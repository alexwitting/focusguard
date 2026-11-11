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

/** Use cases needed by more than one feature module. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideGetSystemHealth(repo: SystemHealthRepository): GetSystemHealthUseCase = GetSystemHealthUseCase(repo)

    // Used by Settings (browser-scan row) and Block ("sites aren't blocked" prompt).
    @Provides
    fun provideGetWebContentBlockingEnabled(repo: SettingsRepository): GetWebContentBlockingEnabledUseCase = GetWebContentBlockingEnabledUseCase(repo)

    @Provides
    fun provideEnableWebContentBlocking(repo: SettingsRepository): EnableWebContentBlockingUseCase = EnableWebContentBlockingUseCase(repo)
}
