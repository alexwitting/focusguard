package com.focusguard.feature.block.di

import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.block.DeleteAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.DeleteWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.GetAllAppsWithRulesUseCase
import com.focusguard.core.domain.usecase.block.GetContentFiltersUseCase
import com.focusguard.core.domain.usecase.block.GetWebBlockRulesUseCase
import com.focusguard.core.domain.usecase.block.SetContentFilterUseCase
import com.focusguard.core.domain.usecase.block.UpsertAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.UpsertWebBlockRuleUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object BlockUseCaseModule {
    @Provides
    fun provideGetAllAppsWithRules(repo: BlockRuleRepository): GetAllAppsWithRulesUseCase = GetAllAppsWithRulesUseCase(repo)

    @Provides
    fun provideGetWebBlockRules(repo: WebBlockRuleRepository): GetWebBlockRulesUseCase = GetWebBlockRulesUseCase(repo)

    @Provides
    fun provideGetContentFilters(repo: WebBlockRuleRepository): GetContentFiltersUseCase = GetContentFiltersUseCase(repo)

    @Provides
    fun provideUpsertAppBlockRule(
        blockRepo: BlockRuleRepository,
        pinRepo: PinRepository,
        clock: ClockProvider,
    ): UpsertAppBlockRuleUseCase = UpsertAppBlockRuleUseCase(blockRepo, pinRepo, clock)

    @Provides
    fun provideDeleteAppBlockRule(
        blockRepo: BlockRuleRepository,
        pinRepo: PinRepository,
        clock: ClockProvider,
    ): DeleteAppBlockRuleUseCase = DeleteAppBlockRuleUseCase(blockRepo, pinRepo, clock)

    @Provides
    fun provideUpsertWebBlockRule(
        webRepo: WebBlockRuleRepository,
        pinRepo: PinRepository,
        clock: ClockProvider,
    ): UpsertWebBlockRuleUseCase = UpsertWebBlockRuleUseCase(webRepo, pinRepo, clock)

    @Provides
    fun provideDeleteWebBlockRule(
        webRepo: WebBlockRuleRepository,
        pinRepo: PinRepository,
        clock: ClockProvider,
    ): DeleteWebBlockRuleUseCase = DeleteWebBlockRuleUseCase(webRepo, pinRepo, clock)

    @Provides
    fun provideSetContentFilter(
        webRepo: WebBlockRuleRepository,
        pinRepo: PinRepository,
        clock: ClockProvider,
    ): SetContentFilterUseCase = SetContentFilterUseCase(webRepo, pinRepo, clock)
}
