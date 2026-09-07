package com.voxa.dictation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

sealed class ActivationResult {
    object Success : ActivationResult()
    object InvalidKey : ActivationResult()
    object DeviceLimitReached : ActivationResult()
    object NetworkError : ActivationResult()
}

/**
 * Офлайн-проверка формата ключа (HMAC-секрет — из BuildConfig, не в коде,
 * см. local.properties) + онлайн-регистрация устройства в Firestore,
 * чтобы один ключ работал не больше чем на MAX_DEVICES устройствах.
 * После успешной активации статус сохраняется локально — дальше приложение
 * работает офлайн, интернет нужен только один раз, при активации.
 */
object License {

    private const val SECRET_HEX = BuildConfig.LICENSE_SECRET
    private const val PREFS_NAME = "voxa_license"
    private const val KEY_ACTIVATED = "activated_key"
    private const val MAX_DEVICES = 3

    private const val PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
    private const val API_KEY = BuildConfig.FIREBASE_API_KEY
    private const val BASE_URL =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/licenses"

    private fun secretBytes(): ByteArray =
        SECRET_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun checksum(serial: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes(), "HmacSHA256"))
        val digest = mac.doFinal(serial.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        val value = BigInteger(hex, 16).mod(BigInteger.valueOf(1_000_000))
        return value.toString().padStart(6, '0')
    }

    fun isValid(rawKey: String): Boolean {
        val raw = rawKey.trim().replace(" ", "").replace("-", "")
        if (raw.length != 12 || !raw.all { it.isDigit() }) return false
        val serial = raw.substring(0, 6)
        val sum = raw.substring(6, 12)
        return checksum(serial) == sum
    }

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ACTIVATED, null) ?: return false
        return isValid(saved)
    }

    @Suppress("HardwareIds")
    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

    private fun saveLocal(context: Context, rawKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVATED, rawKey.trim())
            .apply()
    }

    /** Проверяет формат ключа офлайн, затем регистрирует устройство онлайн. Колбэк вызывается в главном потоке. */
    fun activateAsync(context: Context, rawKey: String, callback: (ActivationResult) -> Unit) {
        val key = rawKey.trim().replace(" ", "").replace("-", "")
        if (!isValid(key)) {
            callback(ActivationResult.InvalidKey)
            return
        }
        Thread {
            val result = try {
                registerDevice(key, deviceId(context))
            } catch (e: Exception) {
                ActivationResult.NetworkError
            }
            if (result == ActivationResult.Success) {
                saveLocal(context, key)
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun registerDevice(key: String, deviceId: String): ActivationResult {
        val existing = firestoreGetDeviceIds(key)
        if (existing == null) {
            // документа ещё нет — пробуем стать первым устройством
            if (firestoreCreate(key, listOf(deviceId))) return ActivationResult.Success
            // кто-то мог создать документ параллельно — читаем ещё раз ниже
        }
        val current = existing ?: firestoreGetDeviceIds(key) ?: return ActivationResult.NetworkError
        if (current.contains(deviceId)) return ActivationResult.Success
        if (current.size >= MAX_DEVICES) return ActivationResult.DeviceLimitReached
        val updated = current + deviceId
        return if (firestoreUpdate(key, updated)) ActivationResult.Success else ActivationResult.NetworkError
    }

    private fun firestoreGetDeviceIds(key: String): List<String>? {
        val url = URL("$BASE_URL/$key?key=$API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val fields = JSONObject(body).optJSONObject("fields") ?: return emptyList()
            val arr = fields.optJSONObject("device_ids")?.optJSONObject("arrayValue")
                ?.optJSONArray("values")
            val list = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list.add(arr.getJSONObject(i).getString("stringValue"))
                }
            }
            list
        } finally {
            conn.disconnect()
        }
    }

    private fun deviceIdsBody(ids: List<String>): String {
        val values = JSONArray()
        ids.forEach { values.put(JSONObject().put("stringValue", it)) }
        val body = JSONObject()
            .put("fields", JSONObject().put("device_ids", JSONObject().put("arrayValue", JSONObject().put("values", values))))
        return body.toString()
    }

    private fun firestoreCreate(key: String, ids: List<String>): Boolean {
        val url = URL("$BASE_URL?documentId=$key&key=$API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.outputStream.use { it.write(deviceIdsBody(ids).toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun firestoreUpdate(key: String, ids: List<String>): Boolean {
        // HttpURLConnection не умеет PATCH напрямую — используем стандартный
        // для Google API обходной путь через заголовок X-HTTP-Method-Override.
        val url = URL("$BASE_URL/$key?updateMask.fieldPaths=device_ids&key=$API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.outputStream.use { it.write(deviceIdsBody(ids).toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
