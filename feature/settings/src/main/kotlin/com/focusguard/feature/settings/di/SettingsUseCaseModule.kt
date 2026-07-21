package com.focusguard.feature.settings.di

import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.usecase.pin.DisablePinUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.SetPinUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
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
    ): SetBedtimeUseCase = SetBedtimeUseCase(settings, pin)

    @Provides
    fun provideVerifyPin(repo: PinRepository): VerifyPinUseCase = VerifyPinUseCase(repo)

    @Provides
    fun provideGetPinEnabled(repo: PinRepository): GetPinEnabledUseCase = GetPinEnabledUseCase(repo)

    @Provides
    fun provideSetPin(repo: PinRepository): SetPinUseCase = SetPinUseCase(repo)

    @Provides
    fun provideDisablePin(repo: PinRepository): DisablePinUseCase = DisablePinUseCase(repo)

    @Provides
    fun provideGetWebContentBlockingEnabled(repo: SettingsRepository): GetWebContentBlockingEnabledUseCase = GetWebContentBlockingEnabledUseCase(repo)

    @Provides
    fun provideEnableWebContentBlocking(repo: SettingsRepository): EnableWebContentBlockingUseCase = EnableWebContentBlockingUseCase(repo)
}
