package com.focusguard.core.data.repository

import android.content.SharedPreferences
import android.util.Base64
import com.focusguard.core.domain.model.PinLockout
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [PinRepositoryImpl] calls `android.util.Base64` directly, which is an unmocked Android stub
 * under a plain JVM unit test (throws on any method call) — `mockkStatic` swaps it for a
 * behaviorally equivalent `java.util.Base64` delegate for the duration of each test.
 */
class PinRepositoryImplTest {
    private val stored = mutableMapOf<String, String>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64
                .getEncoder()
                .encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64
                .getDecoder()
                .decode(firstArg<String>())
        }

        every { prefs.edit() } returns editor
        every { editor.apply() } returns Unit
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { editor.putString(capture(keySlot), capture(valueSlot)) } answers {
            stored[keySlot.captured] = valueSlot.captured
            editor
        }
        every { editor.remove(any()) } answers {
            stored.remove(firstArg())
            editor
        }
        every { prefs.getString(any(), null) } answers { stored[firstArg()] }
        every { prefs.contains(any()) } answers { stored.containsKey(firstArg()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

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
            verify(exactly = 1) { editor.remove("pin_hash") }
            verify(exactly = 1) { editor.remove("pin_salt") }
        }

    @Test
    fun `isPinEnabled reflects existing stored state at construction`() =
        runTest {
            stored["pin_hash"] = "existing-hash"

            val repository = PinRepositoryImpl(prefs)

            assertTrue(repository.isPinEnabled().first())
        }

    @Test
    fun `new PINs are stored as a slow PBKDF2 hash, never the raw PIN`() =
        runTest {
            PinRepositoryImpl(prefs).setPin("123456")

            val hash = stored.getValue("pin_hash")
            assertTrue(hash.startsWith("pbkdf2:120000:"), hash)
            assertFalse(hash.contains("123456"))
        }

    @Test
    fun `a legacy SHA-256 hash still verifies and is upgraded to PBKDF2 on the next correct entry`() =
        runTest {
            val salt = ByteArray(16) { it.toByte() }
            val legacy =
                java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest
                        .getInstance("SHA-256")
                        .apply { update(salt) }
                        .digest("123456".toByteArray()),
                )
            stored["pin_hash"] = legacy
            stored["pin_salt"] =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(salt)
            val repository = PinRepositoryImpl(prefs)

            assertFalse(repository.verifyPin("000000"))
            assertEquals(legacy, stored["pin_hash"], "a wrong PIN must not touch the stored hash")

            assertTrue(repository.verifyPin("123456"))
            assertTrue(stored.getValue("pin_hash").startsWith("pbkdf2:"))
            assertTrue(repository.verifyPin("123456"))
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
            stored["pin_hash"] = "pbkdf2:120000:abc"
            assertFalse(repository.verifyPin("123456"), "salt missing")

            stored["pin_salt"] =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(ByteArray(16))
            stored["pin_hash"] = "pbkdf2:notanumber:abc"
            assertFalse(repository.verifyPin("123456"), "bad iteration count")
            stored["pin_hash"] = "pbkdf2:1000"
            assertFalse(repository.verifyPin("123456"), "hash part missing")
        }

    @Test
    fun `a corrupt lockout value reads as no lockout rather than crashing`() =
        runTest {
            val repository = PinRepositoryImpl(prefs)
            stored["pin_lockout"] = "x:y"
            assertEquals(PinLockout(), repository.getLockout())
            stored["pin_lockout"] = "3"
            assertEquals(PinLockout(failedAttempts = 3), repository.getLockout())
        }
}
