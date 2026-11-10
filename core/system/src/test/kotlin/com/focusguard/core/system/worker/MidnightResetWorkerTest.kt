package com.focusguard.core.system.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeUsageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

    private val usageRepository = FakeUsageRepository()
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

            assertEquals(LocalDate.of(2026, 3, 1), usageRepository.prunedBefore)
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
}
