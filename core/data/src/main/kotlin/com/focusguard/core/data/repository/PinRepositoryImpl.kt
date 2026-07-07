package com.focusguard.core.data.repository

import android.content.SharedPreferences
import android.util.Base64
import com.focusguard.core.domain.repository.PinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

class PinRepositoryImpl
    @Inject
    constructor(
        private val prefs: SharedPreferences,
    ) : PinRepository {
        private val _isPinEnabled = MutableStateFlow(prefs.contains(KEY_HASH))

        override fun isPinEnabled(): Flow<Boolean> = _isPinEnabled.asStateFlow()

        override suspend fun setPin(rawPin: String) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs
                .edit()
                .putString(KEY_HASH, hashPin(rawPin, salt))
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .apply()
            _isPinEnabled.value = true
        }

        override suspend fun verifyPin(rawPin: String): Boolean {
            val storedHash = prefs.getString(KEY_HASH, null) ?: return false
            val saltBytes =
                Base64.decode(
                    prefs.getString(KEY_SALT, null) ?: return false,
                    Base64.NO_WRAP,
                )
            return hashPin(rawPin, saltBytes) == storedHash
        }

        override suspend fun disablePin() {
            prefs
                .edit()
                .remove(KEY_HASH)
                .remove(KEY_SALT)
                .apply()
            _isPinEnabled.value = false
        }

        private fun hashPin(
            rawPin: String,
            salt: ByteArray,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            return Base64.encodeToString(
                digest.digest(rawPin.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
        }

        companion object {
            private const val KEY_HASH = "pin_hash"
            private const val KEY_SALT = "pin_salt"
        }
    }
