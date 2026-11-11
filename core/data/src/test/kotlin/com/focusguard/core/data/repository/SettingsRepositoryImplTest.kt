package com.focusguard.core.data.repository

import com.focusguard.core.data.DataTestEnvironment
import com.focusguard.core.domain.model.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/** Settings in a real DataStore file; extensions in a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryImplTest {
    private val env = DataTestEnvironment()
    private val repository = SettingsRepositoryImpl(env.dataStore, env.db.usageDao())

    @After
    fun tearDown() = env.close()

    @Test
    fun `a fresh install has every setting off and no bedtime`() =
        runTest {
            assertFalse(repository.isGracePeriodEnabled().first())
            assertFalse(repository.isOnboardingComplete().first())
            assertFalse(repository.isWebContentBlockingEnabled().first())
            assertNull(repository.getBedtime().first())
        }

    @Test
    fun `settings are saved and read back`() =
        runTest {
            repository.setGracePeriodEnabled(true)
            repository.setOnboardingComplete()
            repository.enableWebContentBlocking()
            assertTrue(repository.isGracePeriodEnabled().first())
            assertTrue(repository.isOnboardingComplete().first())
            assertTrue(repository.isWebContentBlockingEnabled().first())

            repository.setGracePeriodEnabled(false)
            assertFalse(repository.isGracePeriodEnabled().first())
        }

    @Test
    fun `bedtime round-trips with its days, and clearing it removes it`() =
        runTest {
            val bedtime = TimeWindow(22, 30, 6, 45, setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))

            repository.setBedtime(bedtime)
            assertEquals(bedtime, repository.getBedtime().first())

            repository.setBedtime(null)
            assertNull(repository.getBedtime().first())
        }

    @Test
    fun `an extension counts for that app on that day only`() =
        runTest {
            val day = LocalDate.of(2026, 1, 15)

            repository.recordExtensionTaken("com.app", day)

            assertTrue(repository.hasTakenExtensionToday("com.app", day).first())
            assertFalse(repository.hasTakenExtensionToday("com.app", day.plusDays(1)).first())
            assertFalse(repository.hasTakenExtensionToday("com.other", day).first())
        }
}
