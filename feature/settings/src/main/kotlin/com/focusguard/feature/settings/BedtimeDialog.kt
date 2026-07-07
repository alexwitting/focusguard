package com.focusguard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BedtimeDialog(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val edit = state.bedtimeEdit

    val startPickerState =
        rememberTimePickerState(
            initialHour = edit.startHour,
            initialMinute = edit.startMinute,
            is24Hour = true,
        )
    val endPickerState =
        rememberTimePickerState(
            initialHour = edit.endHour,
            initialMinute = edit.endMinute,
            is24Hour = true,
        )

    AlertDialog(
        onDismissRequest = { onIntent(SettingsIntent.DismissBedtimeDialog) },
        title = { Text("Bedtime") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Start time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeInput(state = startPickerState)

                Spacer(Modifier.height(4.dp))

                Text(
                    "End time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeInput(state = endPickerState)

                Spacer(Modifier.height(4.dp))

                Text(
                    "Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in edit.days,
                            onClick = { onIntent(SettingsIntent.BedtimeDayToggled(day)) },
                            label = { Text(day.name.take(2)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Push picker values into ViewModel state before saving
                onIntent(SettingsIntent.BedtimeStartChanged(startPickerState.hour, startPickerState.minute))
                onIntent(SettingsIntent.BedtimeEndChanged(endPickerState.hour, endPickerState.minute))
                onIntent(SettingsIntent.SaveBedtime)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SettingsIntent.DismissBedtimeDialog) }) { Text("Cancel") }
        },
    )
}
