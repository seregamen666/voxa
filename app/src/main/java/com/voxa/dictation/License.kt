package com.voxa.dictation

import android.content.Context
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Офлайн-проверка лицензионного ключа: тот же HMAC-секрет, что и в
 * tools/generate_key.py. Ключ вида VOXA-XXXXXXXX-XXXXXX, последний блок —
 * подпись первого. Валидные ключи не хранятся на сервере — если секрет
 * когда-нибудь меняется, поменяйте его и в generate_key.py.
 */
object License {

    private const val SECRET_HEX = "4248cebc92d01a1c8ebc126c265a47e06bd42adb7af923898b9948161b32740e"
    private const val PREFS_NAME = "voxa_license"
    private const val KEY_ACTIVATED = "activated_key"

    private fun secretBytes(): ByteArray =
        SECRET_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun sign(serial: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes(), "HmacSHA256"))
        val digest = mac.doFinal(serial.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }.take(6)
    }

    fun isValid(rawKey: String): Boolean {
        val parts = rawKey.trim().uppercase().replace(" ", "").split("-")
        if (parts.size != 3 || parts[0] != "VOXA") return false
        val (_, serial, sig) = parts
        return sign(serial) == sig
    }

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ACTIVATED, null) ?: return false
        return isValid(saved)
    }

    fun activate(context: Context, rawKey: String): Boolean {
        if (!isValid(rawKey)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVATED, rawKey.trim().uppercase())
            .apply()
        return true
    }
}
