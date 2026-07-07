package com.focusguard.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.focusguard.core.ui.PinEntryDialog
import com.focusguard.core.ui.theme.FocusGuardTheme

/**
 * Settings' PIN dialog: computes the title/confirm-label text for the current [PinStep]/
 * [PinAction], then delegates the actual dialog chrome to the shared [PinEntryDialog].
 */
@Composable
internal fun PinDialog(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val title =
        when (state.pinStep) {
            PinStep.VerifyOld ->
                when (state.pendingPinAction) {
                    PinAction.SaveBedtime -> "Confirm PIN to save bedtime"
                    PinAction.ClearBedtime -> "Confirm PIN to clear bedtime"
                    else -> "Enter current PIN"
                }
            PinStep.EnterNew -> if (state.pendingPinAction == PinAction.None) "Set new PIN" else "Enter new PIN"
            PinStep.Hidden -> ""
        }
    val confirmLabel =
        when (state.pinStep) {
            PinStep.VerifyOld ->
                when (state.pendingPinAction) {
                    PinAction.Disable -> "Remove PIN"
                    PinAction.SaveBedtime -> "Save"
                    PinAction.ClearBedtime -> "Clear"
                    else -> "Next"
                }
            PinStep.EnterNew -> "Save"
            PinStep.Hidden -> ""
        }

    PinEntryDialog(
        title = title,
        confirmLabel = confirmLabel,
        value = state.pinInput,
        error = state.pinError,
        onDigit = { digit -> onIntent(SettingsIntent.PinInputChanged(state.pinInput + digit)) },
        onDelete = {
            if (state.pinInput.isNotEmpty()) {
                onIntent(SettingsIntent.PinInputChanged(state.pinInput.dropLast(1)))
            }
        },
        onConfirm = { onIntent(SettingsIntent.SubmitPin) },
        onDismiss = { onIntent(SettingsIntent.DismissPinDialog) },
    )
}

// @Preview requires a no-argument composable — PinDialog itself takes required params, so the
// preview lives here instead, supplying a sample state and the app's real theme.
@Preview(showBackground = true)
@Composable
private fun PinDialogPreview() {
    FocusGuardTheme {
        PinDialog(state = SettingsState(pinStep = PinStep.VerifyOld), onIntent = {})
    }
}
