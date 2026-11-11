package com.focusguard.core.data.repository

import android.content.SharedPreferences
import android.util.Base64
import com.focusguard.core.domain.model.PinLockout
import com.focusguard.core.domain.repository.PinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/**
 * Stores the PIN as a salted PBKDF2-HMAC-SHA256 hash, as `pbkdf2:<iterations>:<base64>`, in
 * encrypted [prefs]. The slow hash matters because a 6-digit PIN has only a million values.
 * A stored value in any other format never verifies.
 */
class PinRepositoryImpl
    @Inject
    constructor(
        private val prefs: SharedPreferences,
    ) : PinRepository {
        private val _isPinEnabled = MutableStateFlow(prefs.contains(KEY_HASH))

        override fun isPinEnabled(): Flow<Boolean> = _isPinEnabled.asStateFlow()

        override suspend fun setPin(rawPin: String) {
            storePbkdf2(rawPin)
            prefs.edit().remove(KEY_LOCKOUT).apply()
            _isPinEnabled.value = true
        }

        override suspend fun verifyPin(rawPin: String): Boolean {
            val stored = prefs.getString(KEY_HASH, null) ?: return false
            val salt = Base64.decode(prefs.getString(KEY_SALT, null) ?: return false, Base64.NO_WRAP)

            if (!stored.startsWith(PBKDF2_PREFIX)) return false

            val parts = stored.split(':')
            val iterations = parts.getOrNull(1)?.toIntOrNull() ?: return false
            val expected = parts.getOrNull(2) ?: return false
            return constantTimeEquals(pbkdf2(rawPin, salt, iterations), expected)
        }

        override suspend fun disablePin() {
            prefs
                .edit()
                .remove(KEY_HASH)
                .remove(KEY_SALT)
                .remove(KEY_LOCKOUT)
                .apply()
            _isPinEnabled.value = false
        }

        // Stored as "failedAttempts:lockedUntilMillis" in the same encrypted prefs.
        override suspend fun getLockout(): PinLockout {
            val parts = prefs.getString(KEY_LOCKOUT, null)?.split(':') ?: return PinLockout()
            return PinLockout(
                failedAttempts = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                lockedUntilEpochMillis = parts.getOrNull(1)?.toLongOrNull() ?: 0,
            )
        }

        override suspend fun setLockout(lockout: PinLockout) {
            if (lockout == PinLockout()) {
                prefs.edit().remove(KEY_LOCKOUT).apply()
            } else {
                prefs.edit().putString(KEY_LOCKOUT, "${lockout.failedAttempts}:${lockout.lockedUntilEpochMillis}").apply()
            }
        }

        private suspend fun storePbkdf2(rawPin: String) {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val hash = pbkdf2(rawPin, salt, ITERATIONS)
            prefs
                .edit()
                .putString(KEY_HASH, "$PBKDF2_PREFIX$ITERATIONS:$hash")
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .apply()
        }

        /** Runs on Dispatchers.Default; too slow for the main thread. */
        private suspend fun pbkdf2(
            rawPin: String,
            salt: ByteArray,
            iterations: Int,
        ): String =
            withContext(Dispatchers.Default) {
                val spec = PBEKeySpec(rawPin.toCharArray(), salt, iterations, KEY_BITS)
                try {
                    val bytes = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } finally {
                    spec.clearPassword()
                }
            }

        private fun constantTimeEquals(
            a: String,
            b: String,
        ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

        companion object {
            private const val KEY_HASH = "pin_hash"
            private const val KEY_SALT = "pin_salt"
            private const val KEY_LOCKOUT = "pin_lockout"
            private const val PBKDF2_PREFIX = "pbkdf2:"
            private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
            private const val ITERATIONS = 120_000
            private const val KEY_BITS = 256
            private const val SALT_BYTES = 16
        }
    }
