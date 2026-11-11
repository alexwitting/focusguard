package com.focusguard.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.ExtensionPolicy
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.ListRow
import com.focusguard.core.ui.RowDivider
import com.focusguard.core.ui.ScreenGutter
import com.focusguard.core.ui.ScreenTitle
import com.focusguard.core.ui.SectionLabel
import com.focusguard.core.ui.describeDays
import com.focusguard.core.ui.formatClock

/** One row of the System section, with the action that fixes it when it isn't healthy. */
private data class HealthItem(
    val label: String,
    val ok: Boolean,
    val okText: String,
    val badText: String,
    val fixLabel: String,
    val issueTitle: String,
    val issueBody: String,
    val onFix: () -> Unit,
    /** Optional extras (the VPN) show as neutral when off and never count as a problem. */
    val optional: Boolean = false,
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Re-check health whenever the user returns from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.forceRefresh()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarState.showSnackbar(effect.message)
            }
        }
    }

    val vpnLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startVpnService(context)
                viewModel.forceRefresh(afterDelayMs = VPN_TOGGLE_REFRESH_DELAY_MS)
            }
        }

    val health =
        buildList {
            add(
                HealthItem(
                    label = "Accessibility service",
                    ok = state.systemHealth.accessibilityServiceActive,
                    okText = "Running",
                    badText = "Off",
                    fixLabel = "Enable",
                    issueTitle = "App blocking is off",
                    issueBody = "Blocked apps will open until the accessibility service is on.",
                    onFix = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                ),
            )
            add(
                HealthItem(
                    label = "Website blocking (VPN)",
                    ok = state.systemHealth.vpnServiceActive,
                    okText = "Running",
                    badText = "Off",
                    fixLabel = "Turn on",
                    issueTitle = "Website blocking (VPN) is off",
                    issueBody = "Optional. The browser scan blocks sites without it.",
                    optional = true,
                    onFix = {
                        val consent = VpnService.prepare(context)
                        if (consent != null) {
                            vpnLauncher.launch(consent)
                        } else {
                            startVpnService(context)
                            viewModel.forceRefresh(afterDelayMs = VPN_TOGGLE_REFRESH_DELAY_MS)
                        }
                    },
                ),
            )
            add(
                HealthItem(
                    label = "Usage access",
                    ok = state.permissions.usageStatsGranted,
                    okText = "Granted",
                    badText = "Not granted",
                    fixLabel = "Fix",
                    issueTitle = "Usage access is off",
                    issueBody = "Daily limits and usage reports need it.",
                    onFix = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                ),
            )
            add(
                HealthItem(
                    label = "Battery optimisation",
                    ok = state.permissions.batteryOptimizationIgnored,
                    okText = "Unrestricted",
                    badText = "Restricted",
                    fixLabel = "Fix",
                    issueTitle = "Android may pause FocusGuard",
                    issueBody = "Blocking can stop while the screen is off.",
                    onFix = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            },
                        )
                    },
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    HealthItem(
                        label = "Exact alarms",
                        ok = state.permissions.exactAlarmAllowed,
                        okText = "Allowed",
                        badText = "Not allowed",
                        fixLabel = "Fix",
                        issueTitle = "Exact alarms are off",
                        issueBody = "Time limits may block a few minutes late.",
                        onFix = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) },
                    ),
                )
            }
        }

    Box(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenTitle(title = "Settings") }

            // Every flag reads false until the first load; don't show the summary before then.
            if (!state.isLoading) {
                item { StatusSummary(issues = health.filterNot { it.ok || it.optional }) }
            }

            item {
                Section("Protection") {
                    PinRow(
                        enabled = state.isPinEnabled,
                        onSetUp = { viewModel.onIntent(SettingsIntent.RequestSetPin) },
                        onChange = { viewModel.onIntent(SettingsIntent.RequestChangePin) },
                        onDisable = { viewModel.onIntent(SettingsIntent.RequestDisablePin) },
                    )
                    RowDivider()
                    val bedtime = state.bedtime
                    ManageRow(
                        icon = Icons.Outlined.Bedtime,
                        title = "Bedtime",
                        subtitle =
                            bedtime?.let {
                                "${formatClock(it.startHour, it.startMinute)} – " +
                                    "${formatClock(it.endHour, it.endMinute)} · ${describeDays(it.days)}"
                            } ?: "Not set",
                        configured = bedtime != null,
                        setUpLabel = "Set up",
                        menu =
                            listOf(
                                "Edit" to { viewModel.onIntent(SettingsIntent.ShowBedtimeDialog) },
                                "Turn off bedtime" to { viewModel.onIntent(SettingsIntent.ClearBedtime) },
                            ),
                        onSetUp = { viewModel.onIntent(SettingsIntent.ShowBedtimeDialog) },
                    )
                    RowDivider()
                    ListRow(
                        title = "Extra ${ExtensionPolicy.MINUTES} minutes",
                        subtitle = "Offer one extension per app per day when a limit runs out",
                        leading = { RowIcon(Icons.Outlined.MoreTime) },
                        trailing = {
                            Switch(
                                checked = state.gracePeriodEnabled,
                                onCheckedChange = { viewModel.onIntent(SettingsIntent.SetGracePeriodEnabled(it)) },
                            )
                        },
                    )
                    RowDivider()
                    ListRow(
                        title = "Browser scan",
                        subtitle = "Checks the address bar in your browser",
                        leading = { RowIcon(Icons.Outlined.TravelExplore) },
                        trailing = {
                            when {
                                state.isEnablingWebContentBlocking ->
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                state.webContentBlockingEnabled ->
                                    Text("On", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                else ->
                                    OutlinedButton(onClick = { viewModel.onIntent(SettingsIntent.EnableWebContentBlocking) }) {
                                        Text("Turn on")
                                    }
                            }
                        },
                    )
                }
            }

            item {
                Section("System") {
                    health.forEachIndexed { index, item ->
                        if (index > 0) RowDivider()
                        HealthRow(item)
                    }
                }
            }
        }
        SnackbarHost(snackbarState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.pinStep != PinStep.Hidden) {
        PinDialog(state = state, onIntent = viewModel::onIntent)
    }

    if (state.showBedtimeDialog) {
        BedtimeDialog(state = state, onIntent = viewModel::onIntent)
    }
}

// ── Summary ───────────────────────────────────────────────────────────────────

@Composable
private fun StatusSummary(issues: List<HealthItem>) {
    val ok = issues.isEmpty()
    val container = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (ok) Icons.Outlined.GppGood else Icons.Outlined.WarningAmber,
                contentDescription = null,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val first = issues.firstOrNull()
                Text(
                    text =
                        when {
                            first == null -> "Everything is running"
                            issues.size == 1 -> first.issueTitle
                            else -> "${issues.size} things need attention"
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        when {
                            first == null -> "Apps and websites are being blocked."
                            issues.size == 1 -> first.issueBody
                            else -> "Start with: ${first.issueTitle.replaceFirstChar { it.lowercase() }}."
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            issues.firstOrNull()?.let { first ->
                Button(
                    onClick = first.onFix,
                    colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
                ) { Text("Fix") }
            }
        }
    }
}

// ── Sections & rows ───────────────────────────────────────────────────────────

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        FocusCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) { content() }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PinRow(
    enabled: Boolean,
    onSetUp: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
) {
    ManageRow(
        icon = Icons.Outlined.Lock,
        title = "PIN lock",
        subtitle = if (enabled) "On · needed to change any rule" else "Require a PIN to change rules",
        configured = enabled,
        setUpLabel = "Set up",
        menu = listOf("Change PIN" to onChange, "Turn off PIN" to onDisable),
        onSetUp = onSetUp,
    )
}

/** A setting that is either unset (shows a "Set up" button) or set (shows a "Manage" menu). */
@Composable
private fun ManageRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    configured: Boolean,
    setUpLabel: String,
    menu: List<Pair<String, () -> Unit>>,
    onSetUp: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { RowIcon(icon) },
        trailing = {
            if (!configured) {
                OutlinedButton(onClick = onSetUp) { Text(setUpLabel) }
            } else {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Manage") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        menu.forEach { (label, action) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun HealthRow(item: HealthItem) {
    val dot: Color =
        when {
            item.ok -> MaterialTheme.colorScheme.secondary
            item.optional -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.tertiary
        }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when {
            item.ok -> Text(item.okText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            // Optional and off: say so plainly and offer to turn it on, without warning styling.
            item.optional -> {
                Text(item.badText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = item.onFix) { Text(item.fixLabel) }
            }
            else -> TextButton(onClick = item.onFix) { Text("${item.badText} · ${item.fixLabel}") }
        }
    }
}

// ── VPN helpers (string-based to avoid cross-module dependency) ───────────────

/** Gives DnsVpnService a moment to finish starting/stopping before we re-check system health. */
private const val VPN_TOGGLE_REFRESH_DELAY_MS = 300L

private fun startVpnService(context: Context) {
    val intent =
        Intent().apply {
            setClassName(context, "com.focusguard.core.system.vpn.DnsVpnService")
        }
    context.startForegroundService(intent)
}
