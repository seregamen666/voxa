package com.voxa.dictation

import android.content.Context
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Офлайн-проверка лицензионного ключа: тот же HMAC-секрет, что и в
 * tools/generate_key.py. Ключ — 12 цифр (1234-5678-9012): первые 6 —
 * серийный номер, последние 6 — его подпись. Валидные ключи не хранятся
 * на сервере — если секрет когда-нибудь меняется, поменяйте его и в
 * generate_key.py.
 */
object License {

    private const val SECRET_HEX = "4248cebc92d01a1c8ebc126c265a47e06bd42adb7af923898b9948161b32740e"
    private const val PREFS_NAME = "voxa_license"
    private const val KEY_ACTIVATED = "activated_key"

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

    fun activate(context: Context, rawKey: String): Boolean {
        if (!isValid(rawKey)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVATED, rawKey.trim())
            .apply()
        return true
    }
}
