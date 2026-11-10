package com.focusguard.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.EmptyState
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.LimitProgressBar
import com.focusguard.core.ui.RowDivider
import com.focusguard.core.ui.ScreenGutter
import com.focusguard.core.ui.ScreenTitle
import com.focusguard.core.ui.StatusPill
import com.focusguard.core.ui.formatMinutes
import com.focusguard.core.ui.theme.extendedColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        when {
            state.isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.loadError ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "Couldn't load usage data", subtitle = "Try reopening the app")
                }

            else -> HomeContent(state = state, onOpenSettings = onOpenSettings)
        }
    }
}

/** The loaded dashboard, stateless so it can be previewed or screenshotted with any [HomeState]. */
@Composable
fun HomeContent(
    state: HomeState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ViewModel's clock-based date; LocalDate.now() is only a fallback before the first load.
    val today = state.today ?: LocalDate.now()
    val limitedApps = state.dailyUsage.filter { it.limitMinutes != null }
    val unlimitedApps = state.significantDailyUsage.filter { it.limitMinutes == null }
    val operational = state.systemHealth.isBlockingActive

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ScreenTitle(
                title = "Today",
                overline = today.format(HEADER_DATE),
            ) {
                ProtectionPill(operational = operational, onClick = onOpenSettings)
            }
        }

        if (!operational) {
            item { ServiceWarningCard(onFix = onOpenSettings) }
        }

        item {
            SummaryCard(
                totalMinutes = state.totalMinutesToday,
                weeklyTotals = state.weeklyTotals,
                today = today,
                blockedAttempts = state.blockedAttempts,
                limitedApps = limitedApps,
            )
        }

        item { WeekSection(dailyTotals = state.weeklyTotals, today = today) }

        if (limitedApps.isNotEmpty()) {
            item {
                AppSection(title = "Daily limits") {
                    limitedApps.forEachIndexed { index, summary ->
                        if (index > 0) RowDivider()
                        LimitRow(summary)
                    }
                }
            }
        }

        if (unlimitedApps.isNotEmpty()) {
            item {
                AppSection(title = "Most used today") {
                    unlimitedApps.forEachIndexed { index, summary ->
                        if (index > 0) RowDivider()
                        UsageRow(summary)
                    }
                }
            }
        } else if (limitedApps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    EmptyState(title = "No app usage recorded today yet")
                }
            }
        }

        val weeklyApps = state.significantWeeklyUsage
        if (weeklyApps.isNotEmpty()) {
            item {
                val max = weeklyApps.maxOf { it.totalMinutes }
                AppSection(title = "Top apps this week") {
                    weeklyApps.forEachIndexed { index, summary ->
                        if (index > 0) RowDivider()
                        WeeklyAppRow(summary = summary, maxMinutes = max)
                    }
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun ProtectionPill(
    operational: Boolean,
    onClick: () -> Unit,
) {
    val label = if (operational) "Protected" else "Check setup"
    StatusPill(
        text = label,
        icon = if (operational) Icons.Outlined.GppGood else Icons.Outlined.WarningAmber,
        containerColor =
            if (operational) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor =
            if (operational) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(role = Role.Button, onClickLabel = "Open settings", onClick = onClick),
    )
}

/**
 * Shown while the accessibility service is off — then nothing is being blocked, and
 * reinstalling the app silently turns it off, so this stays visible (not a dismissible
 * Snackbar) until it's back on. The optional VPN never triggers it.
 */
@Composable
private fun ServiceWarningCard(onFix: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("Blocking is off", style = MaterialTheme.typography.titleMedium)
                Text("Turn the accessibility service back on to block apps again.", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onFix,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
            ) { Text("Fix") }
        }
    }
}

// ── Summary ───────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    totalMinutes: Int,
    weeklyTotals: List<DailyTotal>,
    today: LocalDate,
    blockedAttempts: Int,
    limitedApps: List<AppUsageSummary>,
) {
    val previousDays = weeklyTotals.filter { it.date != today && it.totalMinutes > 0 }
    val average = if (previousDays.isEmpty()) null else previousDays.sumOf { it.totalMinutes } / previousDays.size

    FocusCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            "Screen time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatMinutes(totalMinutes), style = MaterialTheme.typography.displayLarge, maxLines = 1)
            if (average != null) AverageDelta(today = totalMinutes, average = average)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = blockedAttempts.toString(),
                label = if (blockedAttempts == 1) "block today" else "blocks today",
                modifier = Modifier.weight(1f),
            )
            if (limitedApps.isNotEmpty()) {
                val reached = limitedApps.count { it.totalMinutes >= (it.limitMinutes ?: Int.MAX_VALUE) }
                StatTile(
                    value = "$reached of ${limitedApps.size}",
                    label = "limits reached",
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatTile(
                    value = average?.let { formatMinutes(it) } ?: "—",
                    label = "daily average",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AverageDelta(
    today: Int,
    average: Int,
) {
    val diff = today - average
    val under = diff <= 0
    val text = if (under) "${formatMinutes(abs(diff))} under avg" else "${formatMinutes(diff)} over avg"
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (under) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    if (under) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Week chart ────────────────────────────────────────────────────────────────

@Composable
private fun WeekSection(
    dailyTotals: List<DailyTotal>,
    today: LocalDate,
) {
    val tracked = dailyTotals.filter { it.totalMinutes > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (tracked.isNotEmpty()) {
                Text(
                    "avg ${formatMinutes(tracked.sumOf { it.totalMinutes } / tracked.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeeklyBarChart(dailyTotals = dailyTotals, today = today)
    }
}

@Composable
private fun WeeklyBarChart(
    dailyTotals: List<DailyTotal>,
    today: LocalDate,
) {
    val maxMinutes = dailyTotals.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1) ?: 1
    val barArea = 96.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(barArea + 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dailyTotals.forEach { total ->
            val isToday = total.date == today
            val fraction = (total.totalMinutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
            val day =
                total.date.dayOfWeek.name
                    .take(1)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${total.date.dayOfWeek.name.lowercase()}: ${formatMinutes(total.totalMinutes)}"
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barArea * fraction)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.chartInactive,
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── App lists ─────────────────────────────────────────────────────────────────

@Composable
private fun AppSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FocusCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun LimitRow(summary: AppUsageSummary) {
    val limit = summary.limitMinutes ?: return
    val fraction = summary.totalMinutes.toFloat() / limit.coerceAtLeast(1)
    val reached = summary.totalMinutes >= limit
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconOrAvatar(packageName = summary.packageName, label = summary.label, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (reached) "Limit reached" else "${formatMinutes(limit - summary.totalMinutes)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LimitProgressBar(fraction = fraction)
        }
    }
}

@Composable
private fun UsageRow(summary: AppUsageSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconOrAvatar(packageName = summary.packageName, label = summary.label, size = 36.dp)
        Text(
            summary.label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMinutes(summary.totalMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyAppRow(
    summary: AppUsageSummary,
    maxMinutes: Int,
) {
    val fraction = (summary.totalMinutes.toFloat() / maxMinutes).coerceIn(0.02f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconOrAvatar(packageName = summary.packageName, label = summary.label, size = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(summary.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.extendedColors.chartInactive),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            formatMinutes(summary.totalMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
