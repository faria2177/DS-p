package com.docscan.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * DocumentLockManager handles secure password hashing, document lock state persistence,
 * and per-session unlock state tracking.
 *
 * Security guarantees:
 * - Passwords are NEVER stored in plain text.
 * - Each document gets a unique 16-byte cryptographically secure random salt.
 * - Passwords are keyed with PBKDF2WithHmacSHA256 (or Salted SHA-256 fallback) with 10,000 iterations.
 * - Unlocks are strictly in-memory session-scoped (cleared on app process restart or manual lock).
 */
object DocumentLockManager {

    private const val PREFS_NAME = "doc_lock_secure_store"
    private const val KEY_LOCKED_IDS = "locked_document_ids"
    private const val PREFIX_HASH = "hash_doc_"
    private const val PREFIX_SALT = "salt_doc_"
    private const val PREFIX_TIMESTAMP = "time_doc_"

    private var prefs: SharedPreferences? = null

    // Set of document IDs that are currently locked
    private val _lockedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val lockedDocIds: StateFlow<Set<Long>> = _lockedDocIds.asStateFlow()

    // In-memory session unlocked document IDs (cleared when app is closed or locked)
    private val _sessionUnlockedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val sessionUnlockedDocIds: StateFlow<Set<Long>> = _sessionUnlockedDocIds.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadLockedIds()
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadLockedIds()
        }
        return prefs!!
    }

    private fun loadLockedIds() {
        val rawSet = prefs?.getStringSet(KEY_LOCKED_IDS, emptySet()) ?: emptySet()
        val parsedIds = rawSet.mapNotNull { it.toLongOrNull() }.toSet()
        _lockedDocIds.value = parsedIds
    }

    /**
     * Checks if a document is locked with a password.
     */
    fun isDocumentLocked(context: Context, docId: Long): Boolean {
        getPrefs(context)
        return _lockedDocIds.value.contains(docId)
    }

    fun isDocumentLocked(docId: Long): Boolean {
        return _lockedDocIds.value.contains(docId)
    }

    /**
     * Checks if a document is unlocked for the current in-memory app session.
     */
    fun isSessionUnlocked(docId: Long): Boolean {
        return _sessionUnlockedDocIds.value.contains(docId)
    }

    /**
     * Returns true if document is locked AND has NOT been unlocked in the current session.
     */
    fun isLockedAndGuarded(context: Context, docId: Long): Boolean {
        return isDocumentLocked(context, docId) && !isSessionUnlocked(docId)
    }

    fun isLockedAndGuarded(docId: Long): Boolean {
        return isDocumentLocked(docId) && !isSessionUnlocked(docId)
    }

    /**
     * Locks a document by generating a salt and deriving a secure PBKDF2 hash.
     */
    fun lockDocument(context: Context, docId: Long, password: CharSequence): Boolean {
        val cleanPass = password.toString().trim()
        if (cleanPass.length < 6) return false

        val p = getPrefs(context)
        try {
            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)

            val hash = hashPassword(cleanPass.toCharArray(), saltBytes)
            val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

            val currentSet = HashSet(p.getStringSet(KEY_LOCKED_IDS, emptySet()) ?: emptySet())
            currentSet.add(docId.toString())

            p.edit()
                .putStringSet(KEY_LOCKED_IDS, currentSet)
                .putString("$PREFIX_SALT$docId", saltBase64)
                .putString("$PREFIX_HASH$docId", hashBase64)
                .putLong("$PREFIX_TIMESTAMP$docId", System.currentTimeMillis())
                .apply()

            loadLockedIds()
            // A newly locked document must become guarded immediately. Keeping it
            // session-unlocked makes the Lock action appear to do nothing until the
            // app restarts, which is both confusing and unsafe.
            _sessionUnlockedDocIds.value = _sessionUnlockedDocIds.value - docId
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Verifies the password. If correct, marks the document as session-unlocked and returns true.
     */
    fun verifyPassword(context: Context, docId: Long, password: CharSequence): Boolean {
        val cleanPass = password.toString().trim()
        if (cleanPass.isEmpty()) return false

        val p = getPrefs(context)
        val saltBase64 = p.getString("$PREFIX_SALT$docId", null) ?: return false
        val storedHashBase64 = p.getString("$PREFIX_HASH$docId", null) ?: return false

        return try {
            val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
            val computedHash = hashPassword(cleanPass.toCharArray(), saltBytes)
            // Compare raw hash bytes in constant time instead of comparing Base64 strings.
            val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            MessageDigest.isEqual(storedHash, computedHash)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun unlockSession(context: Context, docId: Long, password: CharSequence): Boolean {
        if (!verifyPassword(context, docId, password)) return false
        _sessionUnlockedDocIds.value = _sessionUnlockedDocIds.value + docId
        return true
    }

    /**
     * Locks the document in session again (forces password entry next time).
     */
    fun lockSession(docId: Long) {
        _sessionUnlockedDocIds.value = _sessionUnlockedDocIds.value - docId
    }

    /**
     * Completely removes password protection from a document (requires valid password).
     */
    fun removePassword(context: Context, docId: Long, password: CharSequence): Boolean {
        if (!unlockSession(context, docId, password)) {
            return false
        }
        val p = getPrefs(context)
        val currentSet = HashSet(p.getStringSet(KEY_LOCKED_IDS, emptySet()) ?: emptySet())
        currentSet.remove(docId.toString())

        p.edit()
            .putStringSet(KEY_LOCKED_IDS, currentSet)
            .remove("$PREFIX_SALT$docId")
            .remove("$PREFIX_HASH$docId")
            .remove("$PREFIX_TIMESTAMP$docId")
            .apply()

        loadLockedIds()
        _sessionUnlockedDocIds.value = _sessionUnlockedDocIds.value - docId
        return true
    }

    /**
     * Clears all session unlocks (e.g., when logging out or closing sensitive mode).
     */
    fun clearAllSessionUnlocks() {
        _sessionUnlockedDocIds.value = emptySet()
    }

    /**
     * PBKDF2WithHmacSHA256 password hasher with 10,000 iterations.
     * Falls back to multi-round salted SHA-256 if PBKDF2 is unavailable.
     */
    private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        return try {
            val spec = PBEKeySpec(password, salt, 10000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } catch (e: Exception) {
            // Salted SHA-256 fallback with 10,000 rounds
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            var current = md.digest(String(password).toByteArray(Charsets.UTF_8))
            for (i in 0 until 9999) {
                md.reset()
                md.update(salt)
                current = md.digest(current)
            }
            current
        }
    }
}
