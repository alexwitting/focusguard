package com.focusguard.feature.home.di

import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.usage.GetBlockedAttemptsUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyAppBreakdownUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyTrendUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object HomeUseCaseModule {
    @Provides
    fun provideGetDailyUsage(repo: UsageRepository): GetDailyUsageUseCase = GetDailyUsageUseCase(repo)

    @Provides
    fun provideGetWeeklyTrend(
        repo: UsageRepository,
        clockProvider: ClockProvider,
    ): GetWeeklyTrendUseCase = GetWeeklyTrendUseCase(repo, clockProvider)

    @Provides
    fun provideGetWeeklyAppBreakdown(
        repo: UsageRepository,
        clockProvider: ClockProvider,
    ): GetWeeklyAppBreakdownUseCase = GetWeeklyAppBreakdownUseCase(repo, clockProvider)

    @Provides
    fun provideGetBlockedAttempts(
        repo: UsageRepository,
        clockProvider: ClockProvider,
    ): GetBlockedAttemptsUseCase = GetBlockedAttemptsUseCase(repo, clockProvider)
}
