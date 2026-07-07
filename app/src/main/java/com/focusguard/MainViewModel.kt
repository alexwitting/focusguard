package com.focusguard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.navigation.HomeRoute
import com.focusguard.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        // null = still loading from DataStore; Any = resolved start destination
        val startDestination =
            settingsRepository
                .isOnboardingComplete()
                .map<Boolean, Any?> { complete -> if (complete) HomeRoute else OnboardingRoute }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)
    }
