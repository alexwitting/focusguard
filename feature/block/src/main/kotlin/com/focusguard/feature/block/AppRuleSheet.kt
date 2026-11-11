package com.focusguard.feature.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.DayOfWeekPicker
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.ScreenGutter
import com.focusguard.core.ui.TimeField
import com.focusguard.core.ui.formatMinutes
import java.time.DayOfWeek

// ── Rule sheet ────────────────────────────────────────────────────────────────

private enum class StrategyOption(
    val title: String,
    val description: String,
) {
    ALWAYS("Always block", "Blocked at all times"),
    TIME_LIMIT("Daily time limit", "Blocked once today's limit is used up"),
    SCHEDULED("Allowed hours", "Blocked outside the hours you choose"),
}

private const val LIMIT_STEP_MINUTES = 5
private const val MAX_LIMIT_MINUTES = 12 * 60
private const val DEFAULT_LIMIT_MINUTES = 60
private val LIMIT_PRESETS = listOf(15, 30, 45, 60)
private val WEEKDAYS = DayOfWeek.entries.toSet() - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppStrategySheet(
    app: AppWithRule,
    usedMinutes: Int?,
    onDismiss: () -> Unit,
    onSetStrategy: (BlockingStrategy) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strategy = app.rule?.strategy

    var selected by remember {
        mutableStateOf(
            when (strategy) {
                is BlockingStrategy.AlwaysBlock -> StrategyOption.ALWAYS
                is BlockingStrategy.ScheduledBlock -> StrategyOption.SCHEDULED
                is BlockingStrategy.TimeLimit, null -> StrategyOption.TIME_LIMIT
            },
        )
    }
    var limitMinutes by remember {
        mutableIntStateOf((strategy as? BlockingStrategy.TimeLimit)?.dailyLimitMinutes ?: DEFAULT_LIMIT_MINUTES)
    }
    val window = (strategy as? BlockingStrategy.ScheduledBlock)?.windows?.firstOrNull()
    var days by remember { mutableStateOf(window?.days ?: WEEKDAYS) }
    var startHour by remember { mutableIntStateOf(window?.startHour ?: 9) }
    var startMinute by remember { mutableIntStateOf(window?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(window?.endHour ?: 17) }
    var endMinute by remember { mutableIntStateOf(window?.endMinute ?: 0) }
    val emptyWindow = selected == StrategyOption.SCHEDULED && startHour == endHour && startMinute == endMinute

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIconOrAvatar(packageName = app.packageName, label = app.label, size = 52.dp)
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    if (usedMinutes != null) {
                        Text(
                            "${formatMinutes(usedMinutes)} used today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StrategyOption.entries.forEach { option ->
                    StrategyOptionCard(option = option, selected = option == selected) { selected = option }
                }
            }

            when (selected) {
                StrategyOption.TIME_LIMIT ->
                    LimitStepper(minutes = limitMinutes, onChange = { limitMinutes = it })
                StrategyOption.SCHEDULED ->
                    FocusCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField(
                                label = "Allowed from",
                                hour = startHour,
                                minute = startMinute,
                                onTimeChange = { h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TimeField(
                                label = "Until",
                                hour = endHour,
                                minute = endMinute,
                                onTimeChange = { h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        DayOfWeekPicker(selected = days, onChange = { days = it })
                        if (emptyWindow) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Start and end times must differ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                StrategyOption.ALWAYS -> Unit
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        onSetStrategy(
                            when (selected) {
                                StrategyOption.ALWAYS -> BlockingStrategy.AlwaysBlock
                                StrategyOption.TIME_LIMIT -> BlockingStrategy.TimeLimit(limitMinutes)
                                StrategyOption.SCHEDULED ->
                                    BlockingStrategy.ScheduledBlock(
                                        listOf(TimeWindow(startHour, startMinute, endHour, endMinute, days)),
                                    )
                            },
                        )
                    },
                    enabled = !emptyWindow,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                ) { Text("Save rule") }
                if (app.rule != null) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                    ) { Text("Remove rule") }
                }
            }
        }
    }
}

@Composable
private fun StrategyOptionCard(
    option: StrategyOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border =
            BorderStroke(
                2.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleMedium)
                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LimitStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    FocusCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = { onChange((minutes - LIMIT_STEP_MINUTES).coerceAtLeast(minOf(minutes, LIMIT_STEP_MINUTES))) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Default.Remove, contentDescription = "Less time") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatMinutes(minutes), style = MaterialTheme.typography.displayMedium)
                Text("per day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedIconButton(
                onClick = { onChange((minutes + LIMIT_STEP_MINUTES).coerceAtMost(MAX_LIMIT_MINUTES)) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "More time") }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LIMIT_PRESETS.forEach { preset ->
                val on = preset == minutes
                Text(
                    text = formatMinutes(preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .selectable(selected = on, role = Role.RadioButton) { onChange(preset) }
                            .padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
