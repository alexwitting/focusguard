package com.focusguard.core.data.di

import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.SystemHealthRepository
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Use cases consumed by more than one feature module live here rather than in any single
 * feature's own UseCaseModule, so no feature's DI wiring silently depends on another's.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideGetSystemHealth(repo: SystemHealthRepository): GetSystemHealthUseCase = GetSystemHealthUseCase(repo)

    // Shared by Settings (the browser-scan row) and Block (the "sites aren't blocked" prompt).
    @Provides
    fun provideGetWebContentBlockingEnabled(repo: SettingsRepository): GetWebContentBlockingEnabledUseCase = GetWebContentBlockingEnabledUseCase(repo)

    @Provides
    fun provideEnableWebContentBlocking(repo: SettingsRepository): EnableWebContentBlockingUseCase = EnableWebContentBlockingUseCase(repo)
}
