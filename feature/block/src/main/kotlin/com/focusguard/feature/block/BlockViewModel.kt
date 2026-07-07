package com.focusguard.feature.block

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.domain.usecase.block.DeleteAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.DeleteWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.GetAllAppsWithRulesUseCase
import com.focusguard.core.domain.usecase.block.GetContentFiltersUseCase
import com.focusguard.core.domain.usecase.block.GetWebBlockRulesUseCase
import com.focusguard.core.domain.usecase.block.SetContentFilterUseCase
import com.focusguard.core.domain.usecase.block.UpsertAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.UpsertWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        private val getAllAppsWithRules: GetAllAppsWithRulesUseCase,
        private val getWebBlockRules: GetWebBlockRulesUseCase,
        private val getContentFilters: GetContentFiltersUseCase,
        private val upsertAppBlockRule: UpsertAppBlockRuleUseCase,
        private val deleteAppBlockRule: DeleteAppBlockRuleUseCase,
        private val upsertWebBlockRule: UpsertWebBlockRuleUseCase,
        private val deleteWebBlockRule: DeleteWebBlockRuleUseCase,
        private val setContentFilter: SetContentFilterUseCase,
        private val verifyPin: VerifyPinUseCase,
        private val getPinEnabled: GetPinEnabledUseCase,
        private val getDailyUsage: GetDailyUsageUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BlockState())
        val state: StateFlow<BlockState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<BlockEffect>()
        val effects: SharedFlow<BlockEffect> = _effects.asSharedFlow()

        // Stored lambda to execute after PIN is verified. Not in state (can't serialize a lambda).
        private var pendingOp: (suspend (PinVerificationToken?) -> Unit)? = null

        init {
            viewModelScope.launch {
                dateTicker()
                    .flatMapLatest { date -> getDailyUsage(date) }
                    .collect { usage ->
                        _state.update {
                            it.copy(usageByPackage = usage.associate { s -> s.packageName to s.totalMinutes })
                        }
                    }
            }
            viewModelScope.launch {
                combine(
                    getAllAppsWithRules(),
                    getWebBlockRules(),
                    getContentFilters(),
                    getPinEnabled(),
                    ::BlockScreenData,
                ).collect { data ->
                    _state.update {
                        it.copy(
                            apps = data.apps,
                            webRules = data.webRules,
                            activeFilters = data.filters,
                            isPinEnabled = data.pinEnabled,
                            isLoading = false,
                        )
                    }
                }
            }
        }

        private data class BlockScreenData(
            val apps: List<AppWithRule>,
            val webRules: List<WebBlockRule>,
            val filters: Set<ContentFilter>,
            val pinEnabled: Boolean,
        )

        // getDailyUsage(LocalDate.now()) captured once at construction would pin usage stats to
        // whatever day the ViewModel happened to be created on — Navigation Compose's
        // ViewModelStore survives backgrounding, so a screen left open across midnight would
        // silently show yesterday's usage forever. This re-checks the date periodically and only
        // re-subscribes to getDailyUsage when it actually changes.
        //
        // flowOn(Dispatchers.Default) is deliberate, not just an optimization: Dispatchers.Main
        // gets swapped for a TestDispatcher in ViewModel unit tests (see MainDispatcherExtension),
        // and this loop's delay() is about real wall-clock date changes, not virtual test time —
        // running it on Dispatchers.Main under a TestDispatcher backed by an unmanaged
        // TestCoroutineScheduler caused the delay to resolve near-instantly in a tight loop,
        // spinning the test at ~100% CPU instead of suspending. Dispatchers.Default is untouched
        // by Dispatchers.setMain(), so delay() there uses genuine timers.
        private fun dateTicker(): Flow<LocalDate> =
            flow {
                while (true) {
                    emit(clockProvider.today())
                    delay(DATE_CHECK_INTERVAL_MS)
                }
            }.flowOn(Dispatchers.Default)
                .distinctUntilChanged()

        fun onIntent(intent: BlockIntent) {
            when (intent) {
                is BlockIntent.SelectTab -> _state.update { it.copy(selectedTab = intent.tab) }
                is BlockIntent.SearchQueryChanged -> _state.update { it.copy(searchQuery = intent.query) }
                is BlockIntent.SetAppStrategy ->
                    withPin { token ->
                        upsertAppBlockRule(
                            AppBlockRule(packageName = intent.packageName, label = intent.label, strategy = intent.strategy),
                            token = token,
                        )
                    }
                is BlockIntent.RemoveAppRule ->
                    withPin { token ->
                        deleteAppBlockRule(intent.packageName, token = token)
                    }
                is BlockIntent.AddWebRule -> addWeb(intent.domain, intent.blockSubdomains)
                is BlockIntent.RemoveWebRule ->
                    withPin { token ->
                        deleteWebBlockRule(intent.domain, token = token)
                    }
                is BlockIntent.ToggleContentFilter ->
                    withPin { token ->
                        setContentFilter(intent.filter, intent.enabled, token = token)
                    }
                is BlockIntent.PinInputChanged ->
                    _state.update { it.copy(pinInput = intent.value.take(6), pinError = null) }
                is BlockIntent.SubmitPin -> handleSubmitPin()
                is BlockIntent.DismissPinDialog -> {
                    pendingOp = null
                    _state.update { it.copy(pinRequired = false, pinInput = "", pinError = null) }
                }
            }
        }

        private fun addWeb(
            domain: String,
            blockSubdomains: Boolean,
        ) {
            val cleaned =
                domain
                    .trim()
                    .lowercase()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .trimEnd('/')
            if (cleaned.isEmpty() || !cleaned.contains('.')) {
                viewModelScope.launch {
                    _effects.emit(BlockEffect.ShowSnackbar("Enter a valid domain (e.g. instagram.com)"))
                }
                return
            }
            withPin { token ->
                upsertWebBlockRule(
                    WebBlockRule(domain = cleaned, blockSubdomains = blockSubdomains),
                    token = token,
                )
            }
        }

        private fun withPin(op: suspend (PinVerificationToken?) -> Unit) {
            if (!_state.value.isPinEnabled) {
                viewModelScope.launch {
                    runCatching { op(null) }.onFailure { e ->
                        Log.e("BlockViewModel", "Operation failed", e)
                        _effects.emit(BlockEffect.ShowSnackbar("Failed: ${e.message}"))
                    }
                }
            } else {
                pendingOp = op
                _state.update { it.copy(pinRequired = true, pinInput = "", pinError = null) }
            }
        }

        private fun handleSubmitPin() {
            val pin = _state.value.pinInput
            if (pin.length != 6) {
                _state.update { it.copy(pinError = "Enter a 6-digit PIN") }
                return
            }
            viewModelScope.launch {
                val token = verifyPin(pin)
                if (token == null) {
                    _state.update { it.copy(pinError = "Incorrect PIN", pinInput = "") }
                } else {
                    val op = pendingOp
                    pendingOp = null
                    _state.update { it.copy(pinRequired = false, pinInput = "", pinError = null) }
                    if (op != null) {
                        runCatching { op(token) }.onFailure { e ->
                            Log.e("BlockViewModel", "Operation failed after PIN", e)
                            _effects.emit(BlockEffect.ShowSnackbar("Failed: ${e.message}"))
                        }
                    }
                }
            }
        }

        private companion object {
            const val DATE_CHECK_INTERVAL_MS = 60_000L
        }
    }
