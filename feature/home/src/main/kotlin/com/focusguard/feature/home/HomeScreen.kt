package com.focusguard.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.EmptyState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Status-bar inset only — bottom inset is already handled by the outer Scaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Overview") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.loadError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "Couldn't load usage data", subtitle = "Try reopening the app")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (!state.systemHealth.isFullyOperational) {
                item {
                    ServiceWarningBanner(
                        health = state.systemHealth,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            item {
                SummaryCard(
                    totalMinutes = state.totalMinutesToday,
                    blockedAttempts = state.blockedAttempts,
                )
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    title = "This week",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                WeeklyChartCard(
                    dailyTotals = state.weeklyTotals,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                )
            }

            if (state.weeklyAppBreakdown.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(
                        title = "Top apps this week",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                val maxWeeklyMinutes = state.weeklyAppBreakdown.first().totalMinutes
                items(state.weeklyAppBreakdown, key = { "weekly_${it.packageName}" }) { summary ->
                    TopAppRow(summary = summary, maxMinutes = maxWeeklyMinutes)
                }
            }

            if (state.significantDailyUsage.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(
                        title = "Today's usage",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }

                items(state.significantDailyUsage, key = { it.packageName }) { summary ->
                    AppUsageRow(summary = summary)
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                }
            } else {
                item {
                    Spacer(Modifier.height(40.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(title = "No app usage recorded today yet")
                    }
                }
            }
        }
    }
}

// ── Service warning banner ────────────────────────────────────────────────────

/**
 * Reinstalling the app resets both the accessibility service and the VPN's OS-level consent, so
 * this stays visible (not a dismissible Snackbar) until [health] reports both active again —
 * otherwise blocking silently does nothing and the user has no indication why.
 */
@Composable
private fun ServiceWarningBanner(
    health: SystemHealth,
    modifier: Modifier = Modifier,
) {
    val disabled =
        buildList {
            if (!health.accessibilityServiceActive) add("Accessibility service")
            if (!health.vpnServiceActive) add("VPN")
        }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Blocking may not be working",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "${disabled.joinToString(" and ")} disabled — re-enable in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ── Summary card ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    totalMinutes: Int,
    blockedAttempts: Int,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric(
                value = formatMinutes(totalMinutes),
                label = "Screen time",
            )
            VerticalDivider(
                modifier = Modifier.height(44.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            SummaryMetric(
                value = blockedAttempts.toString(),
                label = "Blocked today",
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Weekly bar chart card ─────────────────────────────────────────────────────

@Composable
private fun WeeklyChartCard(
    dailyTotals: List<DailyTotal>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        WeeklyBarChart(
            dailyTotals = dailyTotals,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun WeeklyBarChart(
    dailyTotals: List<DailyTotal>,
    modifier: Modifier = Modifier,
) {
    val maxMinutes = dailyTotals.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1) ?: 1
    val today = LocalDate.now()
    val chartHeight = 120.dp

    Row(
        modifier = modifier.height(chartHeight + 36.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        dailyTotals.forEach { total ->
            val fraction = (total.totalMinutes.toFloat() / maxMinutes).coerceIn(0.01f, 1f)
            val isToday = total.date == today
            val barColor =
                if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            val labelColor =
                if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f),
            ) {
                if (total.totalMinutes > 0) {
                    Text(
                        text = formatMinutesShort(total.totalMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier =
                        Modifier
                            .width(24.dp)
                            .height(chartHeight * fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        total.date.dayOfWeek.name
                            .take(1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                )
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

// ── App usage row ─────────────────────────────────────────────────────────────

@Composable
private fun AppUsageRow(summary: AppUsageSummary) {
    val limitMinutes = summary.limitMinutes
    val isOverLimit = limitMinutes != null && summary.totalMinutes >= limitMinutes
    val progress = limitMinutes?.let { (summary.totalMinutes.toFloat() / it).coerceIn(0f, 1f) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconOrAvatar(packageName = summary.packageName, label = summary.label, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (limitMinutes != null) {
                    Text(
                        text = "Limit: ${formatMinutes(limitMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isOverLimit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatMinutes(summary.totalMinutes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color =
                    if (isOverLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                color =
                    if (isOverLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

// ── Top app row ───────────────────────────────────────────────────────────────

@Composable
private fun TopAppRow(
    summary: AppUsageSummary,
    maxMinutes: Int,
) {
    val fraction = (summary.totalMinutes.toFloat() / maxMinutes.coerceAtLeast(1)).coerceIn(0.02f, 1f)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconOrAvatar(packageName = summary.packageName, label = summary.label, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMinutes(summary.totalMinutes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun formatMinutesShort(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h" else "${m}m"
}
