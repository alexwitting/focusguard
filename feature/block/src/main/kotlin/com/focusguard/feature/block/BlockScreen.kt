package com.focusguard.feature.block

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.EmptyState
import com.focusguard.core.ui.PinEntryDialog
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockScreen(modifier: Modifier = Modifier) {
    val viewModel: BlockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BlockEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    var strategySheetApp by remember { mutableStateOf<AppWithRule?>(null) }
    var showAddWebDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("Block list") })
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                BlockTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onIntent(BlockIntent.SelectTab(tab)) },
                        text = { Text(tab.label) },
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AnimatedContent(
                    targetState = state.selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(180))
                    },
                    label = "block-tab",
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    when (tab) {
                        BlockTab.APPS ->
                            AppsTab(
                                state = state,
                                onSearchChange = { viewModel.onIntent(BlockIntent.SearchQueryChanged(it)) },
                                onAppClick = { strategySheetApp = it },
                            )
                        BlockTab.WEBSITES ->
                            WebsitesTab(
                                webRules = state.webRules,
                                onAddClick = { showAddWebDialog = true },
                                onDelete = { viewModel.onIntent(BlockIntent.RemoveWebRule(it)) },
                            )
                        BlockTab.CONTENT ->
                            ContentTab(
                                activeFilters = state.activeFilters,
                                onToggle = { filter, enabled ->
                                    viewModel.onIntent(BlockIntent.ToggleContentFilter(filter, enabled))
                                },
                            )
                    }
                }
            }
        }
    }

    strategySheetApp?.let { app ->
        AppStrategySheet(
            app = app,
            onDismiss = { strategySheetApp = null },
            onSetStrategy = { strategy ->
                viewModel.onIntent(BlockIntent.SetAppStrategy(app.packageName, app.label, strategy))
                strategySheetApp = null
            },
            onRemove = {
                viewModel.onIntent(BlockIntent.RemoveAppRule(app.packageName))
                strategySheetApp = null
            },
        )
    }

    if (showAddWebDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddWebDialog = false },
            onConfirm = { domain, blockSubdomains ->
                viewModel.onIntent(BlockIntent.AddWebRule(domain, blockSubdomains))
                showAddWebDialog = false
            },
        )
    }

    if (state.pinRequired) {
        BlockPinDialog(state = state, onIntent = viewModel::onIntent)
    }
}

// ── Apps tab ──────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(
    state: BlockState,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppWithRule) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        val isSearching = state.searchQuery.isNotBlank()

        if (isSearching && state.filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "No results for \"${state.searchQuery}\"")
            }
        } else if (!isSearching && state.apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "No apps found")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (isSearching) {
                    appRows(state.filteredApps, onAppClick, usageByPackage = state.usageByPackage)
                } else {
                    val blocked = state.blockedApps
                    if (blocked.isNotEmpty()) {
                        stickyHeader(key = "header_blocked") {
                            AppSectionHeader("Blocked (${blocked.size})")
                        }
                        appRows(blocked, onAppClick, keySuffix = "_b", usageByPackage = state.usageByPackage)
                    }
                    stickyHeader(key = "header_all") {
                        AppSectionHeader(if (blocked.isEmpty()) "All Apps" else "Other Apps")
                    }
                    val rest = state.unblockedApps
                    if (rest.isEmpty()) {
                        item(key = "empty_rest") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "All apps are blocked",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        appRows(rest, onAppClick, usageByPackage = state.usageByPackage)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.appRows(
    apps: List<AppWithRule>,
    onAppClick: (AppWithRule) -> Unit,
    keySuffix: String = "",
    usageByPackage: Map<String, Int> = emptyMap(),
) {
    items(apps, key = { it.packageName + keySuffix }) { app ->
        AppRow(app = app, usedMinutes = usageByPackage[app.packageName], onClick = { onAppClick(app) })
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
    }
}

@Composable
private fun AppSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AppRow(
    app: AppWithRule,
    usedMinutes: Int?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(app.label) },
        supportingContent = {
            val rule = app.rule
            if (rule != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val strategy = rule.strategy
                    StrategyBadge(strategy = strategy)
                    if (strategy is BlockingStrategy.TimeLimit && usedMinutes != null) {
                        val left = (strategy.dailyLimitMinutes - usedMinutes).coerceAtLeast(0)
                        Text(
                            text = if (left == 0) "Limit reached" else "$left min left",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (left == 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        },
        leadingContent = { AppIconOrAvatar(packageName = app.packageName, label = app.label) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Configure",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DomainAvatar(
    domain: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = domain.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StrategyBadge(strategy: BlockingStrategy) {
    val (text, containerColor) =
        when (strategy) {
            is BlockingStrategy.AlwaysBlock ->
                "Always block" to MaterialTheme.colorScheme.errorContainer
            is BlockingStrategy.ScheduledBlock ->
                "Scheduled" to MaterialTheme.colorScheme.secondaryContainer
            is BlockingStrategy.TimeLimit ->
                "${strategy.dailyLimitMinutes} min/day" to MaterialTheme.colorScheme.tertiaryContainer
        }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ── Strategy bottom sheet ─────────────────────────────────────────────────────

private enum class StrategyOption { NONE, ALWAYS, TIME_LIMIT, SCHEDULED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppStrategySheet(
    app: AppWithRule,
    onDismiss: () -> Unit,
    onSetStrategy: (BlockingStrategy) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialOption =
        when (app.rule?.strategy) {
            is BlockingStrategy.AlwaysBlock -> StrategyOption.ALWAYS
            is BlockingStrategy.ScheduledBlock -> StrategyOption.SCHEDULED
            is BlockingStrategy.TimeLimit -> StrategyOption.TIME_LIMIT
            null -> StrategyOption.NONE
        }
    var selected by remember { mutableStateOf(initialOption) }

    val initialLimit =
        (app.rule?.strategy as? BlockingStrategy.TimeLimit)
            ?.dailyLimitMinutes
            ?.toString() ?: "60"
    var limitText by remember { mutableStateOf(initialLimit) }

    val existingWindow =
        (app.rule?.strategy as? BlockingStrategy.ScheduledBlock)
            ?.windows
            ?.firstOrNull()
    var selectedDays by remember {
        mutableStateOf(
            existingWindow?.days ?: setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
    }
    var startHour by remember { mutableStateOf(existingWindow?.startHour?.toString()?.padStart(2, '0') ?: "09") }
    var startMin by remember { mutableStateOf(existingWindow?.startMinute?.toString()?.padStart(2, '0') ?: "00") }
    var endHour by remember { mutableStateOf(existingWindow?.endHour?.toString()?.padStart(2, '0') ?: "17") }
    var endMin by remember { mutableStateOf(existingWindow?.endMinute?.toString()?.padStart(2, '0') ?: "00") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            StrategyRadioRow(
                label = "No restriction",
                selected = selected == StrategyOption.NONE,
                onClick = { selected = StrategyOption.NONE },
            )
            StrategyRadioRow(
                label = "Always block",
                selected = selected == StrategyOption.ALWAYS,
                onClick = { selected = StrategyOption.ALWAYS },
            )
            StrategyRadioRow(
                label = "Time limit",
                selected = selected == StrategyOption.TIME_LIMIT,
                onClick = { selected = StrategyOption.TIME_LIMIT },
            )
            if (selected == StrategyOption.TIME_LIMIT) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                ) {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) limitText = it },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Minutes") },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("per day", style = MaterialTheme.typography.bodyMedium)
                }
            }

            StrategyRadioRow(
                label = "On schedule (allowed times)",
                selected = selected == StrategyOption.SCHEDULED,
                onClick = { selected = StrategyOption.SCHEDULED },
            )
            if (selected == StrategyOption.SCHEDULED) {
                ScheduleConfigSection(
                    selectedDays = selectedDays,
                    onDaysChanged = { selectedDays = it },
                    startHour = startHour,
                    onStartHourChange = { startHour = it },
                    startMin = startMin,
                    onStartMinChange = { startMin = it },
                    endHour = endHour,
                    onEndHourChange = { endHour = it },
                    endMin = endMin,
                    onEndMinChange = { endMin = it },
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    when (selected) {
                        StrategyOption.NONE -> onRemove()
                        StrategyOption.ALWAYS -> onSetStrategy(BlockingStrategy.AlwaysBlock)
                        StrategyOption.TIME_LIMIT -> {
                            val mins = limitText.toIntOrNull()?.takeIf { it > 0 } ?: return@Button
                            onSetStrategy(BlockingStrategy.TimeLimit(mins))
                        }
                        StrategyOption.SCHEDULED -> {
                            val sh = startHour.toIntOrNull()?.coerceIn(0, 23) ?: return@Button
                            val sm = startMin.toIntOrNull()?.coerceIn(0, 59) ?: return@Button
                            val eh = endHour.toIntOrNull()?.coerceIn(0, 23) ?: return@Button
                            val em = endMin.toIntOrNull()?.coerceIn(0, 59) ?: return@Button
                            val window = TimeWindow(sh, sm, eh, em, selectedDays)
                            onSetStrategy(BlockingStrategy.ScheduledBlock(listOf(window)))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }

            if (app.rule != null) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Remove rule")
                }
            }
        }
    }
}

@Composable
private fun ScheduleConfigSection(
    selectedDays: Set<DayOfWeek>,
    onDaysChanged: (Set<DayOfWeek>) -> Unit,
    startHour: String,
    onStartHourChange: (String) -> Unit,
    startMin: String,
    onStartMinChange: (String) -> Unit,
    endHour: String,
    onEndHourChange: (String) -> Unit,
    endMin: String,
    onEndMinChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 48.dp)) {
        Text(
            "Days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = {
                        val updated =
                            if (day in selectedDays) {
                                if (selectedDays.size > 1) selectedDays - day else selectedDays
                            } else {
                                selectedDays + day
                            }
                        onDaysChanged(updated)
                    },
                    label = { Text(day.name.take(3)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Allowed from",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TimeInputRow(
            hour = startHour,
            minute = startMin,
            onHourChange = onStartHourChange,
            onMinuteChange = onStartMinChange,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TimeInputRow(
            hour = endHour,
            minute = endMin,
            onHourChange = onEndHourChange,
            onMinuteChange = onEndMinChange,
        )
    }
}

@Composable
private fun StrategyRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TimeInputRow(
    hour: String,
    minute: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = hour,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onHourChange(it) },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            label = { Text("HH") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = minute,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onMinuteChange(it) },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            label = { Text("MM") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ── Websites tab ──────────────────────────────────────────────────────────────

@Composable
private fun WebsitesTab(
    webRules: List<WebBlockRule>,
    onAddClick: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (webRules.isEmpty()) {
            EmptyState(
                title = "No websites blocked yet",
                subtitle = "Tap + to add a domain",
                icon = Icons.Default.Search,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(webRules, key = { it.domain }) { rule ->
                    WebRuleRow(rule = rule, onDelete = { onDelete(rule.domain) })
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add website")
        }
    }
}

@Composable
private fun WebRuleRow(
    rule: WebBlockRule,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(rule.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (rule.blockSubdomains) {
                Text(
                    "Including subdomains",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = { DomainAvatar(domain = rule.domain) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove ${rule.domain}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    onConfirm: (domain: String, blockSubdomains: Boolean) -> Unit,
) {
    var domain by rememberSaveable { mutableStateOf("") }
    var blockSubdomains by rememberSaveable { mutableStateOf(true) }
    var domainError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block website") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = {
                        domain = it
                        domainError = false
                    },
                    label = { Text("Domain") },
                    placeholder = { Text("e.g. instagram.com") },
                    singleLine = true,
                    isError = domainError,
                    supportingText =
                        if (domainError) {
                            { Text("Enter a valid domain (e.g. instagram.com)") }
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Block subdomains",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = blockSubdomains, onCheckedChange = { blockSubdomains = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleaned =
                    domain
                        .trim()
                        .lowercase()
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                        .trimEnd('/')
                if (cleaned.isEmpty() || !cleaned.contains('.')) {
                    domainError = true
                } else {
                    onConfirm(cleaned, blockSubdomains)
                }
            }) { Text("Block") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── PIN dialog ────────────────────────────────────────────────────────────────

@Composable
private fun BlockPinDialog(
    state: BlockState,
    onIntent: (BlockIntent) -> Unit,
) {
    PinEntryDialog(
        title = "Enter PIN to continue",
        confirmLabel = "Confirm",
        value = state.pinInput,
        error = state.pinError,
        onDigit = { digit -> onIntent(BlockIntent.PinInputChanged(state.pinInput + digit)) },
        onDelete = {
            if (state.pinInput.isNotEmpty()) {
                onIntent(BlockIntent.PinInputChanged(state.pinInput.dropLast(1)))
            }
        },
        onConfirm = { onIntent(BlockIntent.SubmitPin) },
        onDismiss = { onIntent(BlockIntent.DismissPinDialog) },
    )
}

// ── Content tab ───────────────────────────────────────────────────────────────

private data class FilterInfo(
    val filter: ContentFilter,
    val title: String,
    val subtitle: String,
)

private val CONTENT_FILTERS =
    listOf(
        FilterInfo(ContentFilter.REELS, "Instagram Reels", "Block the Reels feed inside Instagram"),
        FilterInfo(ContentFilter.SHORTS, "YouTube Shorts", "Block the Shorts feed inside YouTube"),
        FilterInfo(ContentFilter.TIKTOK_FYP, "TikTok For You", "Block the TikTok For You page"),
    )

@Composable
private fun ContentTab(
    activeFilters: Set<ContentFilter>,
    onToggle: (ContentFilter, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(CONTENT_FILTERS) { info ->
            ListItem(
                headlineContent = { Text(info.title) },
                supportingContent = {
                    Text(
                        info.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = info.filter in activeFilters,
                        onCheckedChange = { enabled -> onToggle(info.filter, enabled) },
                    )
                },
            )
            HorizontalDivider()
        }
    }
}
