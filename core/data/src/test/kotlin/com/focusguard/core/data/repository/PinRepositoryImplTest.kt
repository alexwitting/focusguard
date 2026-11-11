package com.focusguard.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.focusguard.core.domain.model.PinLockout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The PIN store on real (Robolectric) SharedPreferences, as EncryptedSharedPreferences presents them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinRepositoryImplTest {
    private val prefs: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("pin_test", Context.MODE_PRIVATE)

    private fun put(
        key: String,
        value: String,
    ) = prefs.edit().putString(key, value).commit()

    private fun stored(key: String): String? = prefs.getString(key, null)

    @Test
    fun `setPin then verifyPin with the same PIN succeeds`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)

            repository.setPin("123456")

            assertTrue(repository.verifyPin("123456"))
        }

    @Test
    fun `verifyPin fails for the wrong PIN`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            repository.setPin("123456")

            assertFalse(repository.verifyPin("000000"))
        }

    @Test
    fun `verifyPin fails when no PIN has been set`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)

            assertFalse(repository.verifyPin("123456"))
        }

    @Test
    fun `setPin marks isPinEnabled true`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)

            repository.setPin("123456")

            assertTrue(repository.isPinEnabled().first())
        }

    @Test
    fun `disablePin clears stored hash and salt and marks isPinEnabled false`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            repository.setPin("123456")

            repository.disablePin()

            assertFalse(repository.isPinEnabled().first())
            assertFalse(repository.verifyPin("123456"))
            assertFalse(prefs.contains("pin_hash"))
            assertFalse(prefs.contains("pin_salt"))
        }

    @Test
    fun `isPinEnabled reflects existing stored state at construction`() =
        runTest {
            put("pin_hash", "existing-hash")

            val repository = PinRepositoryImpl(prefs)

            assertTrue(repository.isPinEnabled().first())
        }

    @Test
    fun `new PINs are stored as a slow PBKDF2 hash, never the raw PIN`() =
        runTest {
            PinRepositoryImpl(prefs).setPin("123456")

            val hash = stored("pin_hash")!!
            assertTrue(hash.startsWith("pbkdf2:120000:"), hash)
            assertFalse(hash.contains("123456"))
        }

    @Test
    fun `lockout state round-trips and is cleared by a new PIN`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            repository.setLockout(PinLockout(failedAttempts = 6, lockedUntilEpochMillis = 123_456))

            assertEquals(PinLockout(6, 123_456), PinRepositoryImpl(prefs).getLockout())

            repository.setPin("654321")
            assertEquals(PinLockout(), repository.getLockout())
        }

    @Test
    fun `corrupt or partial stored values never verify`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            put("pin_hash", "pbkdf2:120000:abc")
            assertFalse(repository.verifyPin("123456"), "salt missing")

            put(
                "pin_salt",
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(16)),
            )
            put("pin_hash", "pbkdf2:notanumber:abc")
            assertFalse(repository.verifyPin("123456"), "bad iteration count")
            put("pin_hash", "pbkdf2:1000")
            assertFalse(repository.verifyPin("123456"), "hash part missing")
            put("pin_hash", "aGFzaHdpdGhub3ByZWZpeA==")
            assertFalse(repository.verifyPin("123456"), "no format prefix")
        }

    @Test
    fun `a corrupt lockout value reads as no lockout rather than crashing`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            put("pin_lockout", "x:y")
            assertEquals(PinLockout(), repository.getLockout())
            put("pin_lockout", "3")
            assertEquals(PinLockout(failedAttempts = 3), repository.getLockout())
        }
}
