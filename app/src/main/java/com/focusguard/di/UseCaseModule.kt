package com.focusguard.di

import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.settings.RequestExtensionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideRequestExtensionUseCase(
        settingsRepository: SettingsRepository,
        clockProvider: ClockProvider,
    ): RequestExtensionUseCase = RequestExtensionUseCase(settingsRepository, clockProvider)
}
