package com.focusguard.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.focusguard.core.ui.theme.FocusGuardTheme

/**
 * The shared "enter your PIN to continue" dialog — title, [PinPad] keypad, Confirm/Cancel
 * buttons — used by every PIN-gated screen in the app so they don't each maintain their own
 * copy of this wiring.
 */
@Composable
fun PinEntryDialog(
    title: String,
    confirmLabel: String,
    value: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = value.length == 6,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PinPad(value = value, error = error, onDigit = onDigit, onDelete = onDelete) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PinEntryDialogPreview() {
    FocusGuardTheme {
        PinEntryDialog(
            title = "Enter PIN to continue",
            confirmLabel = "Confirm",
            value = "123",
            error = null,
            onDigit = {},
            onDelete = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PinEntryDialogWithErrorPreview() {
    FocusGuardTheme {
        PinEntryDialog(
            title = "Enter PIN to continue",
            confirmLabel = "Confirm",
            value = "12",
            error = "Incorrect PIN",
            onDigit = {},
            onDelete = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
