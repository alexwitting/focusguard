package com.focusguard.feature.onboarding

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.SystemHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val systemHealthRepository: SystemHealthRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OnboardingState())
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        // MutableSharedFlow + asSharedFlow(), matching Block/SettingsViewModel's effect convention.
        private val _effects = MutableSharedFlow<OnboardingEffect>()
        val effects: SharedFlow<OnboardingEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                systemHealthRepository
                    .observePermissionsHealth()
                    .catch { e -> Log.e(TAG, "observePermissionsHealth failed", e) }
                    .collect { health ->
                        _state.update { it.copy(health = health) }
                    }
            }
        }

        /** Called by the screen on every ON_RESUME — bypasses the 30-second poll. */
        fun forceRefresh() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { systemHealthRepository.checkPermissionsNow() }
                    .onSuccess { health -> _state.update { it.copy(health = health) } }
                    .onFailure { e -> Log.e(TAG, "checkPermissionsNow failed", e) }
            }
        }

        fun onGrant() {
            val step = _state.value.step
            _state.update { it.copy(attemptedSteps = it.attemptedSteps + step) }
            val effect =
                when (step) {
                    OnboardingStep.ACCESSIBILITY -> OnboardingEffect.OpenAccessibilitySettings
                    OnboardingStep.USAGE_ACCESS -> OnboardingEffect.OpenUsageAccessSettings
                    OnboardingStep.BATTERY -> OnboardingEffect.RequestBatteryOptimization
                    OnboardingStep.VPN -> OnboardingEffect.RequestVpnPrepare
                    OnboardingStep.EXACT_ALARM -> OnboardingEffect.OpenExactAlarmSettings
                    else -> return
                }
            viewModelScope.launch { _effects.emit(effect) }
        }

        fun onNext() {
            val current = _state.value.step
            if (current == OnboardingStep.DONE) {
                complete()
                return
            }
            _state.update { it.copy(step = nextStep(current)) }
        }

        fun onSkip() = onNext()

        private fun complete() {
            viewModelScope.launch {
                settingsRepository.setOnboardingComplete()
                _effects.emit(OnboardingEffect.NavigateToMain)
            }
        }

        private fun nextStep(current: OnboardingStep): OnboardingStep =
            when (current) {
                OnboardingStep.WELCOME -> OnboardingStep.ACCESSIBILITY
                OnboardingStep.ACCESSIBILITY -> OnboardingStep.USAGE_ACCESS
                OnboardingStep.USAGE_ACCESS -> OnboardingStep.BATTERY
                OnboardingStep.BATTERY -> OnboardingStep.VPN
                OnboardingStep.VPN ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        OnboardingStep.EXACT_ALARM
                    } else {
                        OnboardingStep.DONE
                    }
                OnboardingStep.EXACT_ALARM -> OnboardingStep.DONE
                OnboardingStep.DONE -> OnboardingStep.DONE
            }

        fun isCurrentStepGranted(): Boolean {
            val health = _state.value.health
            return when (_state.value.step) {
                OnboardingStep.ACCESSIBILITY -> health.accessibilityEnabled
                OnboardingStep.USAGE_ACCESS -> health.usageStatsGranted
                OnboardingStep.BATTERY -> health.batteryOptimizationIgnored
                OnboardingStep.VPN -> health.vpnPrepared
                OnboardingStep.EXACT_ALARM -> health.exactAlarmAllowed
                OnboardingStep.WELCOME, OnboardingStep.DONE -> true
            }
        }

        private companion object {
            const val TAG = "OnboardingViewModel"
        }
    }
