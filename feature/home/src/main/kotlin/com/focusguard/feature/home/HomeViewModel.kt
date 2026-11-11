package com.focusguard.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.usage.GetBlockedAttemptsUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyAppBreakdownUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyTrendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val getDailyUsage: GetDailyUsageUseCase,
        private val getWeeklyTrend: GetWeeklyTrendUseCase,
        private val getWeeklyAppBreakdown: GetWeeklyAppBreakdownUseCase,
        private val getBlockedAttempts: GetBlockedAttemptsUseCase,
        private val getSystemHealth: GetSystemHealthUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState())
        val state: StateFlow<HomeState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                dateTicker()
                    .flatMapLatest { today ->
                        combine(
                            getDailyUsage(today),
                            getWeeklyTrend(today),
                            getWeeklyAppBreakdown(today),
                            getBlockedAttempts(today),
                        ) { daily, weekly, appBreakdown, attempts ->
                            HomeUsageData(today, daily, weekly, appBreakdown, attempts)
                        }
                    }.catch { e ->
                        // Ends the spinner and shows the error instead of leaving the flow dead.
                        Log.e(TAG, "Failed to load home screen data", e)
                        _state.update { it.copy(isLoading = false, loadError = true) }
                    }.collect { data ->
                        _state.update {
                            it.copy(
                                today = data.today,
                                dailyUsage = data.daily,
                                weeklyTotals = data.weekly,
                                weeklyAppBreakdown = data.appBreakdown,
                                blockedAttempts = data.attempts,
                                isLoading = false,
                                loadError = false,
                            )
                        }
                    }
            }
            viewModelScope.launch {
                getSystemHealth
                    .observeSystemHealth()
                    .catch { e -> Log.e(TAG, "observeSystemHealth failed", e) }
                    .collect { health -> _state.update { it.copy(systemHealth = health) } }
            }
        }

        /**
         * The current date, re-checked every minute so a screen left open past midnight moves to
         * the new day. Later checks run on Dispatchers.Default so delay() uses real time in tests.
         */
        private fun dateTicker(): Flow<LocalDate> =
            flow {
                while (true) {
                    delay(DATE_CHECK_INTERVAL_MS)
                    emit(clockProvider.today())
                }
            }.flowOn(Dispatchers.Default)
                // Emits the first date synchronously, in the collector's context.
                .onStart { emit(clockProvider.today()) }
                .distinctUntilChanged()

        private companion object {
            const val TAG = "HomeViewModel"
            const val DATE_CHECK_INTERVAL_MS = 60_000L
        }
    }

private data class HomeUsageData(
    val today: LocalDate,
    val daily: List<AppUsageSummary>,
    val weekly: List<DailyTotal>,
    val appBreakdown: List<AppUsageSummary>,
    val attempts: Int,
)
