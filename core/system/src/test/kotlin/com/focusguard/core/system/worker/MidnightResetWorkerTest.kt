package com.focusguard.core.system.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
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

    @Test
    fun `doWork clears the notified-80-percent set and reschedules itself`() =
        runTest {
            val worker = TestListenableWorkerBuilder<MidnightResetWorker>(context).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(emptySet<String>(), UsagePollerWorker.notified80Packages)
            verify(exactly = 1) {
                workManager.enqueueUniqueWork("focusguard_midnight_reset", any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
            }
        }
}
