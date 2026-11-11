package com.focusguard.core.system.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeUsageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class MidnightResetWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any<Context>()) } returns workManager
        UsagePollerWorker.notified80Packages.add("com.leftover")
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    private var usageRepository: UsageRepository = FakeUsageRepository()
    private val clock = FakeClockProvider().apply { setNow(LocalDateTime.of(2026, 3, 31, 0, 0)) }

    private fun buildWorker(): MidnightResetWorker =
        TestListenableWorkerBuilder<MidnightResetWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = MidnightResetWorker(appContext, workerParameters, usageRepository, clock)
                },
            ).build()

    @Test
    fun `doWork prunes history older than the retention window`() =
        runTest {
            buildWorker().doWork()

            assertEquals(LocalDate.of(2026, 3, 1), (usageRepository as FakeUsageRepository).prunedBefore)
        }

    @Test
    fun `doWork clears the notified-80-percent set and reschedules itself`() =
        runTest {
            val worker = buildWorker()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(emptySet<String>(), UsagePollerWorker.notified80Packages)
            verify(exactly = 1) {
                workManager.enqueueUniqueWork("focusguard_midnight_reset", any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
            }
        }

    @Test
    fun `scheduling at startup keeps an existing reset and aims it at the next midnight`() {
        val request = slot<OneTimeWorkRequest>()

        MidnightResetWorker.scheduleIfNeeded(context)

        verify { workManager.enqueueUniqueWork("focusguard_midnight_reset", ExistingWorkPolicy.KEEP, capture(request)) }
        val delay = request.captured.workSpec.initialDelay
        assertTrue(delay in 1..Duration.ofDays(1).toMillis(), "delay $delay ms")
    }

    @Test
    fun `a failed prune still clears the day's warnings and schedules tomorrow's reset`() =
        runTest {
            mockkStatic(Log::class)
            every { Log.e(any(), any(), any()) } returns 0
            usageRepository = mockk<UsageRepository> { coEvery { pruneBefore(any()) } throws IllegalStateException("disk full") }

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(emptySet<String>(), UsagePollerWorker.notified80Packages)
            verify { workManager.enqueueUniqueWork("focusguard_midnight_reset", ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }
            unmockkStatic(Log::class)
        }
}
