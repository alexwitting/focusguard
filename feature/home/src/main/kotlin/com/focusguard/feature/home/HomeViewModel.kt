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
                        // Without this, a single upstream failure (e.g. a Room query error) would
                        // silently end this flow forever — the screen would be stuck on its loading
                        // spinner with no further updates and no signal to the user of why.
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
         * The current date, re-checked every minute. Capturing it once at construction pinned
         * the dashboard to the day the ViewModel was created — the ViewModel survives in the
         * back stack, so a Home screen left open past midnight kept showing yesterday. The first
         * date is emitted immediately; later checks run on Dispatchers.Default for the same
         * real-time-vs-test-dispatcher reason as BlockViewModel's dateTicker().
         */
        private fun dateTicker(): Flow<LocalDate> =
            flow {
                while (true) {
                    delay(DATE_CHECK_INTERVAL_MS)
                    emit(clockProvider.today())
                }
            }.flowOn(Dispatchers.Default)
                // onStart runs in the collector's context, so the first date arrives
                // synchronously (merge() routed it through a channel, which made the initial
                // load timing-dependent).
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
