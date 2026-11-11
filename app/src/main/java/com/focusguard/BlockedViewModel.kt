package com.focusguard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.settings.RequestExtensionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedViewModel
    @Inject
    constructor(
        private val usageRepository: UsageRepository,
        private val settingsRepository: SettingsRepository,
        private val requestExtensionUseCase: RequestExtensionUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BlockedState())
        val state: StateFlow<BlockedState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<BlockedEffect>()
        val effects: SharedFlow<BlockedEffect> = _effects.asSharedFlow()

        /** The block this screen last initialised for — see [initialize]. */
        private var initializedFor: Pair<String, BlockReason>? = null

        /**
         * Records the blocked attempt and works out whether an extension can be offered. Runs
         * once per block, so recomposition and rotation don't count it again.
         */
        fun initialize(
            packageName: String,
            reason: BlockReason,
        ) {
            val key = packageName to reason
            if (initializedFor == key) return
            initializedFor = key
            _state.update { it.copy(canRequestExtension = false) }
            viewModelScope.launch {
                runCatching {
                    usageRepository.recordBlockedAttempt(packageName)

                    if (reason == BlockReason.TIME_LIMIT_REACHED) {
                        val gracePeriodEnabled = settingsRepository.isGracePeriodEnabled().first()
                        if (gracePeriodEnabled) {
                            val taken =
                                settingsRepository
                                    .hasTakenExtensionToday(packageName, clockProvider.today())
                                    .first()
                            _state.update { it.copy(canRequestExtension = !taken) }
                        }
                    }
                }.onFailure { e -> Log.e(TAG, "Failed to initialize blocked screen", e) }
            }
        }

        fun requestExtension(packageName: String) {
            viewModelScope.launch {
                runCatching { requestExtensionUseCase(packageName) }
                    .onSuccess { granted ->
                        if (granted) {
                            _state.update { it.copy(canRequestExtension = false) }
                            _effects.emit(BlockedEffect.ExtensionGranted)
                        }
                    }.onFailure { e -> Log.e(TAG, "Failed to request extension", e) }
            }
        }

        private companion object {
            const val TAG = "BlockedViewModel"
        }
    }
