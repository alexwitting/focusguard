package com.focusguard.feature.settings

import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.model.TimeWindow
import java.time.DayOfWeek

data class SettingsState(
    val systemHealth: SystemHealth = SystemHealth(false, false),
    val permissions: PermissionsHealth = PermissionsHealth(false, false, false, false, false),
    val isPinEnabled: Boolean = false,
    val webContentBlockingEnabled: Boolean = false,
    val isEnablingWebContentBlocking: Boolean = false,
    val bedtime: TimeWindow? = null,
    val isLoading: Boolean = true,
    // PIN dialog
    val pinStep: PinStep = PinStep.Hidden,
    val pendingPinAction: PinAction = PinAction.None,
    val pinInput: String = "",
    val pinError: String? = null,
    val pendingToken: PinVerificationToken? = null,
    // Bedtime dialog
    val showBedtimeDialog: Boolean = false,
    val bedtimeEdit: BedtimeEdit = BedtimeEdit(),
)

enum class PinStep { Hidden, VerifyOld, EnterNew }

enum class PinAction { None, Change, Disable, SaveBedtime, ClearBedtime }

data class BedtimeEdit(
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

sealed interface SettingsIntent {
    // PIN
    data object RequestSetPin : SettingsIntent

    data object RequestChangePin : SettingsIntent

    data object RequestDisablePin : SettingsIntent

    data class PinInputChanged(
        val value: String,
    ) : SettingsIntent

    data object SubmitPin : SettingsIntent

    data object DismissPinDialog : SettingsIntent

    // Bedtime
    data object ShowBedtimeDialog : SettingsIntent

    data object DismissBedtimeDialog : SettingsIntent

    data class BedtimeStartChanged(
        val hour: Int,
        val minute: Int,
    ) : SettingsIntent

    data class BedtimeEndChanged(
        val hour: Int,
        val minute: Int,
    ) : SettingsIntent

    data class BedtimeDayToggled(
        val day: DayOfWeek,
    ) : SettingsIntent

    data object SaveBedtime : SettingsIntent

    data object ClearBedtime : SettingsIntent

    // Website content blocking — enable-only, no corresponding "disable" intent.
    data object EnableWebContentBlocking : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowSnackbar(
        val message: String,
    ) : SettingsEffect
}
