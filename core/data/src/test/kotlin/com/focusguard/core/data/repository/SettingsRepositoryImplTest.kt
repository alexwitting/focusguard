package com.focusguard.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.entity.ExtensionRecordEntity
import com.focusguard.core.domain.model.TimeWindow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * A minimal in-memory [DataStore] fake — real datastore-preferences `edit`/updateData
 * machinery runs against it unmodified, only the backing storage is swapped out.
 */
private class SettingsFakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class SettingsRepositoryImplTest {
    private val usageDao = mockk<UsageDao>()
    private val dataStore = SettingsFakeDataStore()
    private val repository = SettingsRepositoryImpl(dataStore, usageDao)

    @Test
    fun `isGracePeriodEnabled defaults to false`() =
        runTest {
            assertFalse(repository.isGracePeriodEnabled().first())
        }

    @Test
    fun `setGracePeriodEnabled then isGracePeriodEnabled reflects the change`() =
        runTest {
            repository.setGracePeriodEnabled(true)

            assertTrue(repository.isGracePeriodEnabled().first())
        }

    @Test
    fun `getBedtime is null until a bedtime is set`() =
        runTest {
            assertNull(repository.getBedtime().first())
        }

    @Test
    fun `setBedtime then getBedtime round-trips the window`() =
        runTest {
            val window = TimeWindow(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0)

            repository.setBedtime(window)

            assertEquals(window, repository.getBedtime().first())
        }

    @Test
    fun `setBedtime with null clears a previously stored bedtime`() =
        runTest {
            val window = TimeWindow(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0)
            repository.setBedtime(window)

            repository.setBedtime(null)

            assertNull(repository.getBedtime().first())
        }

    @Test
    fun `isSyncEnabled defaults to false`() =
        runTest {
            assertFalse(repository.isSyncEnabled().first())
        }

    @Test
    fun `setSyncEnabled then isSyncEnabled reflects the change`() =
        runTest {
            repository.setSyncEnabled(true)

            assertTrue(repository.isSyncEnabled().first())
        }

    @Test
    fun `isOnboardingComplete defaults to false`() =
        runTest {
            assertFalse(repository.isOnboardingComplete().first())
        }

    @Test
    fun `setOnboardingComplete marks onboarding complete`() =
        runTest {
            repository.setOnboardingComplete()

            assertTrue(repository.isOnboardingComplete().first())
        }

    @Test
    fun `hasTakenExtensionToday delegates to the usage DAO`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            every { usageDao.countExtension("com.app", date.toEpochDay()) } returns flowOf(1)

            assertTrue(repository.hasTakenExtensionToday("com.app", date).first())
        }

    @Test
    fun `recordExtensionTaken inserts an extension record`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            coEvery { usageDao.recordExtension(ExtensionRecordEntity("com.app", date.toEpochDay())) } returns Unit

            repository.recordExtensionTaken("com.app", date)

            coVerify(exactly = 1) { usageDao.recordExtension(ExtensionRecordEntity("com.app", date.toEpochDay())) }
        }
}
