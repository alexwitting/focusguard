package com.focusguard.feature.settings

import androidx.compose.runtime.Composable
import com.focusguard.core.ui.PinEntrySheet

/**
 * Settings' PIN prompt: picks the title and explanation for the current [PinStep] and
 * [PinAction], then shows the shared [PinEntrySheet].
 */
@Composable
internal fun PinDialog(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val (title, subtitle) =
        when (state.pinStep) {
            PinStep.VerifyOld ->
                "Enter your PIN" to
                    when (state.pendingPinAction) {
                        PinAction.Change -> "Then you can choose a new one"
                        PinAction.Disable -> "To turn off PIN lock"
                        PinAction.SaveBedtime -> "To save your bedtime"
                        PinAction.ClearBedtime -> "To turn off bedtime"
                        PinAction.SetGracePeriod -> "To change the time-limit extension"
                        PinAction.None -> "Needed to change your rules"
                    }
            PinStep.EnterNew -> "Choose a PIN" to "6 digits. You'll need it to change any rule."
            PinStep.ConfirmNew -> "Enter it again" to "Just to make sure"
            PinStep.Hidden -> "" to ""
        }

    PinEntrySheet(
        title = title,
        subtitle = subtitle,
        value = state.pinInput,
        error = state.pinError,
        onDigit = { digit -> onIntent(SettingsIntent.PinInputChanged(state.pinInput + digit)) },
        onDelete = {
            if (state.pinInput.isNotEmpty()) {
                onIntent(SettingsIntent.PinInputChanged(state.pinInput.dropLast(1)))
            }
        },
        onSubmit = { onIntent(SettingsIntent.SubmitPin) },
        onDismiss = { onIntent(SettingsIntent.DismissPinDialog) },
    )
}
