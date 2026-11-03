package com.docscan.util

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local app PIN; PBKDF2 hash, never persist the PIN itself. Migrates old plain PINs. */
object AppPinSecurity {
    private const val PREFERENCES = "app_settings_prefs"
    private const val ENABLED = "sec_folder_password"
    private const val LEGACY = "sec_pin_code"
    private const val SALT = "sec_pin_salt"
    private const val HASH = "sec_pin_hash"
    private const val ITERATIONS = 120_000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(ENABLED, false)

    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun verify(ctx: Context, pin: String): Boolean {
        if (!isEnabled(ctx) || pin.length != 4 || !pin.all(Char::isDigit)) return false
        val savedHash = prefs(ctx).getString(HASH, null)
        val savedSalt = prefs(ctx).getString(SALT, null)
        if (savedHash != null && savedSalt != null) {
            return try {
                MessageDigest.isEqual(
                    Base64.decode(savedHash, Base64.NO_WRAP),
                    digest(pin, Base64.decode(savedSalt, Base64.NO_WRAP))
                )
            } catch (_: Exception) { false }
        }
        // Previous app builds persisted an unencrypted PIN. Migrate after first correct entry.
        val legacyPin = prefs(ctx).getString(LEGACY, null)
        if (legacyPin != null && legacyPin == pin) {
            setPin(ctx, pin)
            return true
        }
        return false
    }

    fun setPin(ctx: Context, pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(digest(pin, salt), Base64.NO_WRAP))
            .remove(LEGACY).putBoolean(ENABLED, true).apply()
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().putBoolean(ENABLED, false).remove(HASH)
            .remove(SALT).remove(LEGACY).apply()
    }
}
