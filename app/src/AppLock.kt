package com.harpreet.omniai.data

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Optional PIN lock. Only a salted PBKDF2 hash of the PIN is stored. */
class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("omniai_lock", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains("hash")

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) = prefs.edit().putBoolean("biometric", v).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltB64 = prefs.getString("salt", null) ?: return false
        val expected = prefs.getString("hash", null) ?: return false
        val actual = hash(pin, Base64.decode(saltB64, Base64.NO_WRAP))
        return MessageDigest.isEqual(
            Base64.decode(expected, Base64.NO_WRAP), Base64.decode(actual, Base64.NO_WRAP)
        )
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun hash(pin: String, salt: ByteArray): String {
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60000, 256)
        return Base64.encodeToString(SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded, Base64.NO_WRAP)
    }
}
