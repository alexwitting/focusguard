package com.focusguard.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.PinAttemptResult
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.usecase.pin.DisablePinUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.SetPinUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.GetGracePeriodEnabledUseCase
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.SetGracePeriodEnabledUseCase
import com.focusguard.core.ui.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val getSystemHealth: GetSystemHealthUseCase,
        private val getBedtime: GetBedtimeUseCase,
        private val setBedtime: SetBedtimeUseCase,
        private val verifyPin: VerifyPinUseCase,
        private val setPin: SetPinUseCase,
        private val disablePin: DisablePinUseCase,
        private val getPinEnabled: GetPinEnabledUseCase,
        private val getWebContentBlockingEnabled: GetWebContentBlockingEnabledUseCase,
        private val enableWebContentBlocking: EnableWebContentBlockingUseCase,
        private val getGracePeriodEnabled: GetGracePeriodEnabledUseCase,
        private val setGracePeriodEnabled: SetGracePeriodEnabledUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsState())
        val state: StateFlow<SettingsState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<SettingsEffect>()
        val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                combine(
                    combine(
                        getSystemHealth.observeSystemHealth(),
                        getSystemHealth.observePermissionsHealth(),
                        getPinEnabled(),
                        getBedtime(),
                        getWebContentBlockingEnabled(),
                        ::SettingsScreenData,
                    ),
                    getGracePeriodEnabled(),
                ) { data, gracePeriod -> data to gracePeriod }
                    // Keeps the screen out of a dead, stale state if loading fails.
                    .catch { e ->
                        Log.e(TAG, "Failed to load settings", e)
                        _state.update { it.copy(isLoading = false) }
                    }.collect { (data, gracePeriod) ->
                        _state.update {
                            it.copy(
                                systemHealth = data.health,
                                permissions = data.permissions,
                                isPinEnabled = data.pinEnabled,
                                bedtime = data.bedtime,
                                webContentBlockingEnabled = data.webContentBlockingEnabled,
                                gracePeriodEnabled = gracePeriod,
                                isLoading = false,
                            )
                        }
                    }
            }
        }

        private data class SettingsScreenData(
            val health: SystemHealth,
            val permissions: PermissionsHealth,
            val pinEnabled: Boolean,
            val bedtime: TimeWindow?,
            val webContentBlockingEnabled: Boolean,
        )

        /**
         * Re-reads health now rather than waiting for the 30-second poll. [afterDelayMs] gives the
         * VPN service time to start or stop after a toggle.
         */
        fun forceRefresh(afterDelayMs: Long = 0) {
            viewModelScope.launch(Dispatchers.IO) {
                if (afterDelayMs > 0) delay(afterDelayMs)
                val health = getSystemHealth.checkSystemHealthNow()
                val permissions = getSystemHealth.checkPermissionsNow()
                _state.update { it.copy(systemHealth = health, permissions = permissions) }
            }
        }

        fun onIntent(intent: SettingsIntent) {
            when (intent) {
                SettingsIntent.RequestSetPin ->
                    _state.update { it.copy(pinStep = PinStep.EnterNew, pendingPinAction = PinAction.None, pinInput = "", pinError = null) }

                SettingsIntent.RequestChangePin ->
                    _state.update { it.copy(pinStep = PinStep.VerifyOld, pendingPinAction = PinAction.Change, pinInput = "", pinError = null) }

                SettingsIntent.RequestDisablePin ->
                    _state.update { it.copy(pinStep = PinStep.VerifyOld, pendingPinAction = PinAction.Disable, pinInput = "", pinError = null) }

                is SettingsIntent.PinInputChanged ->
                    _state.update { it.copy(pinInput = intent.value.take(6), pinError = null) }

                SettingsIntent.SubmitPin -> handleSubmitPin()

                SettingsIntent.DismissPinDialog -> {
                    newPinDraft = null
                    _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pinError = null, pendingToken = null) }
                }

                SettingsIntent.ShowBedtimeDialog -> {
                    val existing = _state.value.bedtime
                    _state.update {
                        it.copy(
                            showBedtimeDialog = true,
                            bedtimeEdit =
                                if (existing != null) {
                                    BedtimeEdit(existing.startHour, existing.startMinute, existing.endHour, existing.endMinute, existing.days)
                                } else {
                                    BedtimeEdit()
                                },
                        )
                    }
                }

                SettingsIntent.DismissBedtimeDialog ->
                    _state.update { it.copy(showBedtimeDialog = false) }

                is SettingsIntent.BedtimeStartChanged ->
                    _state.update { it.copy(bedtimeEdit = it.bedtimeEdit.copy(startHour = intent.hour, startMinute = intent.minute)) }

                is SettingsIntent.BedtimeEndChanged ->
                    _state.update { it.copy(bedtimeEdit = it.bedtimeEdit.copy(endHour = intent.hour, endMinute = intent.minute)) }

                is SettingsIntent.BedtimeDayToggled -> {
                    val current = _state.value.bedtimeEdit.days
                    val updated = if (intent.day in current) current - intent.day else current + intent.day
                    if (updated.isNotEmpty()) {
                        _state.update { it.copy(bedtimeEdit = it.bedtimeEdit.copy(days = updated)) }
                    }
                }

                SettingsIntent.SaveBedtime -> handleSaveBedtime()

                SettingsIntent.ClearBedtime -> handleClearBedtime()

                SettingsIntent.EnableWebContentBlocking -> handleEnableWebContentBlocking()

                is SettingsIntent.SetGracePeriodEnabled -> handleSetGracePeriod(intent.enabled)
            }
        }

        /** The first entry of a new PIN, held only until it's re-typed on [PinStep.ConfirmNew]. */
        private var newPinDraft: String? = null

        /** Pending value for a PIN-gated grace-period change; read back once the PIN checks out. */
        private var pendingGracePeriod: Boolean? = null

        // Ignores repeat submits while a PIN check runs, so one entry can't count twice.
        private var verifyingPin = false

        private fun handleSetGracePeriod(enabled: Boolean) {
            if (_state.value.isPinEnabled) {
                pendingGracePeriod = enabled
                _state.update {
                    it.copy(pinStep = PinStep.VerifyOld, pendingPinAction = PinAction.SetGracePeriod, pinInput = "", pinError = null)
                }
                return
            }
            viewModelScope.launch {
                runCatching { setGracePeriodEnabled(enabled, null) }
                    .onFailure {
                        Log.e(TAG, "Failed to change extension setting", it)
                        emit(SettingsEffect.ShowSnackbar("Couldn't change the extension setting"))
                    }
            }
        }

        private fun handleClearBedtime() {
            if (_state.value.isPinEnabled) {
                _state.update {
                    it.copy(
                        pinStep = PinStep.VerifyOld,
                        pendingPinAction = PinAction.ClearBedtime,
                        pinInput = "",
                        pinError = null,
                    )
                }
            } else {
                viewModelScope.launch {
                    runCatching { setBedtime(null, null) }
                        .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't turn off bedtime")) }
                }
            }
        }

        private fun handleEnableWebContentBlocking() {
            _state.update { it.copy(isEnablingWebContentBlocking = true) }
            viewModelScope.launch {
                runCatching { enableWebContentBlocking() }
                    .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't turn on the browser scan")) }
                _state.update { it.copy(isEnablingWebContentBlocking = false) }
            }
        }

        private fun handleSubmitPin() {
            val s = _state.value
            val pin = s.pinInput
            if (pin.length != 6) {
                _state.update { it.copy(pinError = "Enter a 6-digit PIN") }
                return
            }

            viewModelScope.launch {
                when (s.pinStep) {
                    PinStep.VerifyOld -> {
                        if (verifyingPin) return@launch
                        verifyingPin = true
                        val result =
                            runCatching { verifyPin(pin) }
                                .onFailure { e -> Log.e(TAG, "PIN check failed", e) }
                                .getOrNull()
                        verifyingPin = false
                        val token = (result as? PinAttemptResult.Success)?.token
                        if (result == null) {
                            _state.update { it.copy(pinError = "Couldn't check the PIN. Try again.", pinInput = "") }
                        } else if (token == null) {
                            _state.update { it.copy(pinError = result.errorMessage(), pinInput = "") }
                        } else {
                            runPendingAction(s, token)
                        }
                    }

                    PinStep.EnterNew, PinStep.ConfirmNew -> handleNewPinEntry(s.pinStep, pin)

                    PinStep.Hidden -> Unit
                }
            }
        }

        /** Carries out the change the PIN prompt was opened for, now that [token] is in hand. */
        private suspend fun runPendingAction(
            s: SettingsState,
            token: PinVerificationToken,
        ) {
            when (s.pendingPinAction) {
                PinAction.Disable -> {
                    runCatching { disablePin(token) }
                        .onSuccess { _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pendingToken = null) } }
                        .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't turn off the PIN")) }
                }
                PinAction.Change ->
                    _state.update { it.copy(pinStep = PinStep.EnterNew, pinInput = "", pinError = null, pendingToken = token) }
                PinAction.None ->
                    _state.update { it.copy(pinStep = PinStep.Hidden) }
                PinAction.SaveBedtime -> {
                    val edit = s.bedtimeEdit
                    runCatching {
                        val window = TimeWindow(edit.startHour, edit.startMinute, edit.endHour, edit.endMinute, edit.days)
                        setBedtime(window, token)
                    }.onSuccess { _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pendingToken = null, showBedtimeDialog = false) } }
                        .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't save bedtime")) }
                }
                PinAction.SetGracePeriod -> {
                    val enabled = pendingGracePeriod ?: !s.gracePeriodEnabled
                    pendingGracePeriod = null
                    runCatching { setGracePeriodEnabled(enabled, token) }
                        .onSuccess { _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pendingToken = null) } }
                        .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't change the extension setting")) }
                }
                PinAction.ClearBedtime -> {
                    runCatching { setBedtime(null, token) }
                        .onSuccess { _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pendingToken = null) } }
                        .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't turn off bedtime")) }
                }
            }
        }

        /** First entry of a new PIN, then its confirmation; only a matching pair is saved. */
        private suspend fun handleNewPinEntry(
            step: PinStep,
            pin: String,
        ) {
            if (step == PinStep.EnterNew) {
                newPinDraft = pin
                _state.update { it.copy(pinStep = PinStep.ConfirmNew, pinInput = "", pinError = null) }
                return
            }
            val draft = newPinDraft
            newPinDraft = null
            if (pin != draft) {
                _state.update { it.copy(pinStep = PinStep.EnterNew, pinInput = "", pinError = "PINs didn't match, try again") }
                return
            }
            runCatching { setPin(pin) }
                .onSuccess { _state.update { it.copy(pinStep = PinStep.Hidden, pinInput = "", pendingToken = null) } }
                .onFailure {
                    _state.update { it.copy(pinStep = PinStep.EnterNew, pinInput = "") }
                    emit(SettingsEffect.ShowSnackbar("Couldn't save the PIN"))
                }
        }

        private fun handleSaveBedtime() {
            val edit = _state.value.bedtimeEdit
            if (edit.startHour == edit.endHour && edit.startMinute == edit.endMinute) {
                viewModelScope.launch { emit(SettingsEffect.ShowSnackbar("Bedtime needs different start and end times")) }
                return
            }
            runCatching {
                TimeWindow(edit.startHour, edit.startMinute, edit.endHour, edit.endMinute, edit.days)
            }.onFailure {
                viewModelScope.launch { emit(SettingsEffect.ShowSnackbar("That bedtime isn't valid")) }
                return
            }
            // With a PIN set, the save happens in handleSubmitPin once the PIN is verified.
            if (_state.value.isPinEnabled) {
                _state.update {
                    it.copy(
                        pinStep = PinStep.VerifyOld,
                        pendingPinAction = PinAction.SaveBedtime,
                        pinInput = "",
                        pinError = null,
                    )
                }
                return
            }
            viewModelScope.launch {
                val window = TimeWindow(edit.startHour, edit.startMinute, edit.endHour, edit.endMinute, edit.days)
                runCatching { setBedtime(window, null) }
                    .onSuccess { _state.update { it.copy(showBedtimeDialog = false) } }
                    .onFailure { emit(SettingsEffect.ShowSnackbar("Couldn't save bedtime")) }
            }
        }

        private suspend fun emit(effect: SettingsEffect) = _effects.emit(effect)

        private companion object {
            const val TAG = "SettingsViewModel"
        }
    }
