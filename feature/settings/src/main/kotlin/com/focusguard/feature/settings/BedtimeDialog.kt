package com.focusguard.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focusguard.core.ui.DayOfWeekPicker
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.RowDivider
import com.focusguard.core.ui.ScreenGutter
import com.focusguard.core.ui.SectionLabel
import com.focusguard.core.ui.TimeField
import com.focusguard.core.ui.describeDays

/** Bottom sheet for setting or changing bedtime. Saving may go through the PIN sheet first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BedtimeDialog(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    val edit = state.bedtimeEdit

    ModalBottomSheet(
        onDismissRequest = { onIntent(SettingsIntent.DismissBedtimeDialog) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text("Bedtime", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Every app with a rule is blocked during these hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FocusCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        label = "Starts",
                        hour = edit.startHour,
                        minute = edit.startMinute,
                        onTimeChange = { h, m -> onIntent(SettingsIntent.BedtimeStartChanged(h, m)) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = "Ends",
                        hour = edit.endHour,
                        minute = edit.endMinute,
                        onTimeChange = { h, m -> onIntent(SettingsIntent.BedtimeEndChanged(h, m)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        bedtimeSummary(edit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                RowDivider()
                Spacer(Modifier.height(14.dp))
                SectionLabel("Nights")
                Spacer(Modifier.height(10.dp))
                DayOfWeekPicker(
                    selected = edit.days,
                    onChange = { updated ->
                        // The ViewModel toggles one day at a time; send whichever one changed.
                        (updated - edit.days + (edit.days - updated))
                            .firstOrNull()
                            ?.let { onIntent(SettingsIntent.BedtimeDayToggled(it)) }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    describeDays(edit.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { onIntent(SettingsIntent.SaveBedtime) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                ) { Text("Save bedtime") }
                if (state.bedtime != null) {
                    TextButton(
                        onClick = {
                            onIntent(SettingsIntent.DismissBedtimeDialog)
                            onIntent(SettingsIntent.ClearBedtime)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                    ) { Text("Turn off bedtime") }
                }
            }
        }
    }
}
