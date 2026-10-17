package com.smsspam.detector.data

import android.content.Context
import java.security.MessageDigest

/**
 * App-lock credentials: an optional app PIN and/or the device credential
 * (fingerprint, face or the system PIN). The PIN is stored only as a salted
 * SHA-256 hash.
 */
class AppLockStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the app should be locked on launch. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** True when the device credential (fingerprint/face/system PIN) may be used. */
    var deviceCredential: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_CREDENTIAL, true)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_CREDENTIAL, value).apply()

    /** True when an app PIN has been set. */
    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    /** True when [pin] matches the stored PIN hash. */
    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "$SALT$pin"
        return digest.digest(salted.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "spam_guard_app_lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_DEVICE_CREDENTIAL = "device_credential"
        const val KEY_PIN_HASH = "pin_hash"
        const val SALT = "spam-guard-lock:"
    }
}
