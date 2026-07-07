package com.focusguard.core.data.di

import com.focusguard.core.data.repository.AppBlockRuleRepositoryImpl
import com.focusguard.core.data.repository.PinRepositoryImpl
import com.focusguard.core.data.repository.SettingsRepositoryImpl
import com.focusguard.core.data.repository.SystemHealthRepositoryImpl
import com.focusguard.core.data.repository.UsageRepositoryImpl
import com.focusguard.core.data.repository.WebBlockRuleRepositoryImpl
import com.focusguard.core.data.time.SystemClockProvider
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.SystemHealthRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.time.ClockProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindBlockRuleRepository(impl: AppBlockRuleRepositoryImpl): BlockRuleRepository

    @Binds @Singleton
    abstract fun bindWebBlockRuleRepository(impl: WebBlockRuleRepositoryImpl): WebBlockRuleRepository

    @Binds @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindPinRepository(impl: PinRepositoryImpl): PinRepository

    @Binds @Singleton
    abstract fun bindSystemHealthRepository(impl: SystemHealthRepositoryImpl): SystemHealthRepository

    @Binds @Singleton
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider
}
