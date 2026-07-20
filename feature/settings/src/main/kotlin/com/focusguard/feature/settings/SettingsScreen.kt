package com.focusguard.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Re-check accessibility/VPN/permission health immediately whenever the user returns
    // from system settings, instead of waiting on the repository's 30-second poll.
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

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SectionCard(title = "Services") {
                    ServiceRow(
                        label = "Accessibility service",
                        active = state.systemHealth.accessibilityServiceActive,
                        actionLabel = if (state.systemHealth.accessibilityServiceActive) null else "Enable",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    ServiceRow(
                        label = "Website blocking (VPN)",
                        active = state.systemHealth.vpnServiceActive,
                        actionLabel = if (state.systemHealth.vpnServiceActive) null else "Start",
                        onAction = {
                            val consent = VpnService.prepare(context)
                            if (consent != null) {
                                vpnLauncher.launch(consent)
                            } else {
                                startVpnService(context)
                                viewModel.forceRefresh(afterDelayMs = VPN_TOGGLE_REFRESH_DELAY_MS)
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    ServiceRow(
                        label = "Website blocking (browser scan)",
                        active = state.webContentBlockingEnabled,
                        actionLabel = if (state.webContentBlockingEnabled) null else "Enable",
                        loading = state.isEnablingWebContentBlocking,
                        onAction = { viewModel.onIntent(SettingsIntent.EnableWebContentBlocking) },
                    )
                }
            }

            item {
                SectionCard(title = "Permissions") {
                    PermissionRow(
                        label = "Usage access",
                        granted = state.permissions.usageStatsGranted,
                        onFix = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    PermissionRow(
                        label = "Battery optimization exempt",
                        granted = state.permissions.batteryOptimizationIgnored,
                        onFix = {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            context.startActivity(intent)
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        PermissionRow(
                            label = "Exact alarms",
                            granted = state.permissions.exactAlarmAllowed,
                            onFix = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            },
                        )
                    }
                }
            }

            item {
                SectionCard(title = "PIN lock") {
                    if (!state.isPinEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("PIN protection", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { viewModel.onIntent(SettingsIntent.RequestSetPin) }) {
                                Text("Enable")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column {
                                Text("PIN protection", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "6-digit PIN active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.onIntent(SettingsIntent.RequestChangePin) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Change PIN") }
                                OutlinedButton(
                                    onClick = { viewModel.onIntent(SettingsIntent.RequestDisablePin) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Disable PIN") }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Bedtime") {
                    val bedtime = state.bedtime
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column {
                            Text("Block during bedtime", style = MaterialTheme.typography.bodyMedium)
                            if (bedtime != null) {
                                Text(
                                    text =
                                        "%02d:%02d – %02d:%02d".format(
                                            bedtime.startHour,
                                            bedtime.startMinute,
                                            bedtime.endHour,
                                            bedtime.endMinute,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    "Not configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.onIntent(SettingsIntent.ShowBedtimeDialog) },
                                modifier = Modifier.weight(1f),
                            ) { Text(if (bedtime != null) "Edit" else "Set bedtime") }
                            if (bedtime != null) {
                                OutlinedButton(
                                    onClick = { viewModel.onIntent(SettingsIntent.ClearBedtime) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Clear") }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (state.pinStep != PinStep.Hidden) {
        PinDialog(state = state, onIntent = viewModel::onIntent)
    }

    if (state.showBedtimeDialog) {
        BedtimeDialog(state = state, onIntent = viewModel::onIntent)
    }
}

// ── Section card ──────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

// ── Service row ───────────────────────────────────────────────────────────────

@Composable
private fun ServiceRow(
    label: String,
    active: Boolean,
    actionLabel: String?,
    loading: Boolean = false,
    onAction: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (active) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            loading -> {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            actionLabel != null -> {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

// ── Permission row ────────────────────────────────────────────────────────────

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (!granted) {
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

// ── VPN helpers (string-based to avoid cross-module dependency) ───────────────

/** Gives DnsVpnService a moment to finish starting/stopping before we re-check system health. */
private const val VPN_TOGGLE_REFRESH_DELAY_MS = 300L

private fun startVpnService(context: android.content.Context) {
    val intent =
        Intent().apply {
            setClassName(context, "com.focusguard.core.system.vpn.DnsVpnService")
        }
    context.startForegroundService(intent)
}
