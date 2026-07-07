package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.repository.SystemHealthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetSystemHealthUseCaseTest {
    private val repository = mockk<SystemHealthRepository>()
    private val useCase = GetSystemHealthUseCase(repository)

    private val systemHealth = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false)
    private val permissionsHealth =
        PermissionsHealth(
            accessibilityEnabled = true,
            vpnPrepared = false,
            usageStatsGranted = true,
            batteryOptimizationIgnored = false,
            exactAlarmAllowed = true,
        )

    @Test
    fun `observeSystemHealth delegates to the repository`() =
        runTest {
            every { repository.observeSystemHealth() } returns flowOf(systemHealth)

            assertEquals(systemHealth, useCase.observeSystemHealth().first())
        }

    @Test
    fun `observePermissionsHealth delegates to the repository`() =
        runTest {
            every { repository.observePermissionsHealth() } returns flowOf(permissionsHealth)

            assertEquals(permissionsHealth, useCase.observePermissionsHealth().first())
        }

    @Test
    fun `checkSystemHealthNow delegates to the repository`() =
        runTest {
            coEvery { repository.checkSystemHealthNow() } returns systemHealth

            assertEquals(systemHealth, useCase.checkSystemHealthNow())
        }

    @Test
    fun `checkPermissionsNow delegates to the repository`() =
        runTest {
            coEvery { repository.checkPermissionsNow() } returns permissionsHealth

            assertEquals(permissionsHealth, useCase.checkPermissionsNow())
        }
}
