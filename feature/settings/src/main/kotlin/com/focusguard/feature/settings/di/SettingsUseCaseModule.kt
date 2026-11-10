package com.focusguard.feature.settings.di

import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.pin.DisablePinUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.SetPinUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.settings.GetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.GetGracePeriodEnabledUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.SetGracePeriodEnabledUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SettingsUseCaseModule {
    @Provides
    fun provideGetBedtime(repo: SettingsRepository): GetBedtimeUseCase = GetBedtimeUseCase(repo)

    @Provides
    fun provideSetBedtime(
        settings: SettingsRepository,
        pin: PinRepository,
        clock: ClockProvider,
    ): SetBedtimeUseCase = SetBedtimeUseCase(settings, pin, clock)

    @Provides
    fun provideVerifyPin(
        repo: PinRepository,
        clock: ClockProvider,
    ): VerifyPinUseCase = VerifyPinUseCase(repo, clock)

    @Provides
    fun provideGetPinEnabled(repo: PinRepository): GetPinEnabledUseCase = GetPinEnabledUseCase(repo)

    @Provides
    fun provideSetPin(repo: PinRepository): SetPinUseCase = SetPinUseCase(repo)

    @Provides
    fun provideDisablePin(
        repo: PinRepository,
        clock: ClockProvider,
    ): DisablePinUseCase = DisablePinUseCase(repo, clock)

    @Provides
    fun provideGetGracePeriodEnabled(repo: SettingsRepository): GetGracePeriodEnabledUseCase = GetGracePeriodEnabledUseCase(repo)

    @Provides
    fun provideSetGracePeriodEnabled(
        settings: SettingsRepository,
        pin: PinRepository,
        clock: ClockProvider,
    ): SetGracePeriodEnabledUseCase = SetGracePeriodEnabledUseCase(settings, pin, clock)
}
