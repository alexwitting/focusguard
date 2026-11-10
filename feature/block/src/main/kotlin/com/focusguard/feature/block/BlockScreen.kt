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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.model.WebDomain
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.EmptyState
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.PinEntrySheet
import com.focusguard.core.ui.RowDivider
import com.focusguard.core.ui.ScreenGutter
import com.focusguard.core.ui.ScreenTitle
import com.focusguard.core.ui.SectionLabel
import com.focusguard.core.ui.StatusPill
import com.focusguard.core.ui.describeDays
import com.focusguard.core.ui.formatClock
import com.focusguard.core.ui.formatMinutes
import com.focusguard.core.ui.groupedCardItem

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

    Box(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        BlockContent(
            state = state,
            onIntent = viewModel::onIntent,
            onAppClick = { strategySheetApp = it },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    strategySheetApp?.let { app ->
        AppStrategySheet(
            app = app,
            usedMinutes = state.usageByPackage[app.packageName],
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

    if (state.pinRequired) {
        BlockPinDialog(state = state, onIntent = viewModel::onIntent)
    }
}

/**
 * Title, tabs and the selected tab's list — stateless so it can be previewed or screenshotted
 * with any [BlockState]. Sheets, dialogs and the snackbar stay in [BlockScreen].
 */
@Composable
fun BlockContent(
    state: BlockState,
    onIntent: (BlockIntent) -> Unit,
    onAppClick: (AppWithRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = ScreenGutter, end = ScreenGutter, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle(title = "Block") {
                if (state.isPinEnabled) {
                    StatusPill(
                        text = "PIN protected",
                        icon = Icons.Outlined.Lock,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SegmentedTabs(
                selected = state.selectedTab,
                counts =
                    mapOf(
                        BlockTab.APPS to state.blockedApps.size,
                        BlockTab.WEBSITES to state.webRules.size,
                        BlockTab.CONTENT to state.activeFilters.size,
                    ),
                onSelect = { onIntent(BlockIntent.SelectTab(it)) },
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "block-tab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    BlockTab.APPS ->
                        AppsTab(
                            state = state,
                            onSearchChange = { onIntent(BlockIntent.SearchQueryChanged(it)) },
                            onAppClick = onAppClick,
                        )
                    BlockTab.WEBSITES ->
                        WebsitesTab(
                            webRules = state.webRules,
                            showScanPrompt = state.websitesUnprotected,
                            onEnableScan = { onIntent(BlockIntent.EnableBrowserScan) },
                            onAdd = { domain, subdomains -> onIntent(BlockIntent.AddWebRule(domain, subdomains)) },
                            onDelete = { onIntent(BlockIntent.RemoveWebRule(it)) },
                        )
                    BlockTab.CONTENT ->
                        ContentTab(
                            activeFilters = state.activeFilters,
                            onToggle = { filter, enabled ->
                                onIntent(BlockIntent.ToggleContentFilter(filter, enabled))
                            },
                        )
                }
            }
        }
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────────

@Composable
private fun SegmentedTabs(
    selected: BlockTab,
    counts: Map<BlockTab, Int>,
    onSelect: (BlockTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BlockTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                shadowElevation = if (isSelected) 1.dp else 0.dp,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val count = counts[tab] ?: 0
                    if (count > 0) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Apps tab ──────────────────────────────────────────────────────────────────

@Composable
private fun AppsTab(
    state: BlockState,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppWithRule) -> Unit,
) {
    val isSearching = state.searchQuery.isNotBlank()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, bottom = 24.dp),
    ) {
        item(key = "search") {
            SearchField(query = state.searchQuery, onQueryChange = onSearchChange)
            Spacer(Modifier.height(16.dp))
        }

        when {
            isSearching && state.filteredApps.isEmpty() ->
                item(key = "no_results") { EmptyMessage("No results for \"${state.searchQuery}\"") }

            !isSearching && state.apps.isEmpty() ->
                item(key = "no_apps") { EmptyMessage("No apps found") }

            isSearching -> appGroup(state.filteredApps, state.usageByPackage, onAppClick, keyPrefix = "s_")

            else -> {
                val ruled = state.blockedApps
                if (ruled.isNotEmpty()) {
                    item(key = "h_rules") { GroupLabel("Rules · ${ruled.size}") }
                    appGroup(ruled, state.usageByPackage, onAppClick, keyPrefix = "r_")
                    item(key = "gap") { Spacer(Modifier.height(20.dp)) }
                }
                item(key = "h_other") { GroupLabel(if (ruled.isEmpty()) "All apps" else "Other apps") }
                if (state.unblockedApps.isEmpty()) {
                    item(key = "all_ruled") { EmptyMessage("Every app has a rule") }
                } else {
                    appGroup(state.unblockedApps, state.usageByPackage, onAppClick, keyPrefix = "o_")
                }
            }
        }
    }
}

private fun LazyListScope.appGroup(
    apps: List<AppWithRule>,
    usageByPackage: Map<String, Int>,
    onAppClick: (AppWithRule) -> Unit,
    keyPrefix: String,
) {
    itemsIndexed(apps, key = { _, app -> keyPrefix + app.packageName }) { index, app ->
        Column(Modifier.groupedCardItem(isFirst = index == 0, isLast = index == apps.lastIndex)) {
            if (index > 0) RowDivider(Modifier.padding(start = 70.dp, end = 16.dp))
            AppRow(app = app, usedMinutes = usageByPackage[app.packageName], onClick = { onAppClick(app) })
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

@Composable
private fun GroupLabel(text: String) {
    SectionLabel(text, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        EmptyState(title = text)
    }
}

@Composable
private fun AppRow(
    app: AppWithRule,
    usedMinutes: Int?,
    onClick: () -> Unit,
) {
    val rule = app.rule
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (rule == null) "Add rule" else "Edit rule", onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIconOrAvatar(packageName = app.packageName, label = app.label, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = ruleDetail(rule?.strategy, usedMinutes)
            if (detail != null) {
                Text(
                    detail.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detail.alert) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (rule != null) {
            StrategyChip(rule.strategy)
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private data class RuleDetail(
    val text: String,
    val alert: Boolean = false,
)

private fun ruleDetail(
    strategy: BlockingStrategy?,
    usedMinutes: Int?,
): RuleDetail? =
    when (strategy) {
        null -> usedMinutes?.takeIf { it > 0 }?.let { RuleDetail("${formatMinutes(it)} today") }
        is BlockingStrategy.AlwaysBlock -> RuleDetail("Blocked all day")
        is BlockingStrategy.TimeLimit -> {
            val limit = strategy.dailyLimitMinutes
            when {
                usedMinutes == null -> RuleDetail("${formatMinutes(limit)} / day")
                usedMinutes >= limit -> RuleDetail("Limit reached · ${formatMinutes(limit)} / day", alert = true)
                else -> RuleDetail("${formatMinutes(limit)} / day · ${formatMinutes(limit - usedMinutes)} left")
            }
        }
        is BlockingStrategy.ScheduledBlock -> {
            val first = strategy.windows.firstOrNull()
            if (first == null) {
                RuleDetail("No allowed hours")
            } else {
                val more = if (strategy.windows.size > 1) " +${strategy.windows.size - 1}" else ""
                RuleDetail(
                    "Allowed ${formatClock(first.startHour, first.startMinute)}–" +
                        "${formatClock(first.endHour, first.endMinute)} · ${describeDays(first.days)}$more",
                )
            }
        }
    }

@Composable
private fun StrategyChip(strategy: BlockingStrategy) {
    val (text, container, content) =
        when (strategy) {
            is BlockingStrategy.AlwaysBlock ->
                Triple("Always", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            is BlockingStrategy.TimeLimit ->
                Triple("Limit", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            is BlockingStrategy.ScheduledBlock ->
                Triple("Schedule", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(container)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

// ── Websites tab ──────────────────────────────────────────────────────────────

@Composable
private fun WebsitesTab(
    webRules: List<WebBlockRule>,
    showScanPrompt: Boolean,
    onEnableScan: () -> Unit,
    onAdd: (domain: String, blockSubdomains: Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, bottom = 24.dp),
    ) {
        item(key = "add") {
            AddWebsiteCard(onAdd = onAdd)
            Spacer(Modifier.height(20.dp))
        }
        if (showScanPrompt) {
            item(key = "scan_prompt") {
                BrowserScanPrompt(onEnable = onEnableScan)
                Spacer(Modifier.height(20.dp))
            }
        }
        if (webRules.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    EmptyState(title = "No websites blocked yet", icon = Icons.Outlined.Language)
                }
            }
        } else {
            item(key = "h_sites") { GroupLabel("Blocked sites · ${webRules.size}") }
            itemsIndexed(webRules, key = { _, rule -> rule.domain }) { index, rule ->
                Column(Modifier.groupedCardItem(isFirst = index == 0, isLast = index == webRules.lastIndex)) {
                    if (index > 0) RowDivider(Modifier.padding(start = 64.dp, end = 16.dp))
                    WebRuleRow(rule = rule, onDelete = { onDelete(rule.domain) })
                }
            }
        }
    }
}

/** Shown when sites are listed but neither the browser scan nor the optional VPN is on. */
@Composable
private fun BrowserScanPrompt(onEnable: () -> Unit) {
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
            Icon(Icons.Outlined.Language, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("These sites aren't blocked yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn on the browser scan so FocusGuard can close them in your browser.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onEnable,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
            ) { Text("Turn on") }
        }
    }
}

@Composable
private fun AddWebsiteCard(onAdd: (domain: String, blockSubdomains: Boolean) -> Unit) {
    var domain by rememberSaveable { mutableStateOf("") }
    var blockSubdomains by rememberSaveable { mutableStateOf(true) }
    var domainError by remember { mutableStateOf(false) }

    fun submit() {
        val cleaned = WebDomain.normalize(domain)
        if (cleaned == null) {
            domainError = true
        } else {
            onAdd(cleaned, blockSubdomains)
            domain = ""
        }
    }

    FocusCard {
        Text("Block a website", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = domain,
                onValueChange = {
                    domain = it
                    domainError = false
                },
                placeholder = { Text("e.g. instagram.com") },
                singleLine = true,
                isError = domainError,
                supportingText = if (domainError) ({ Text("Enter a domain like instagram.com") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = ::submit, modifier = Modifier.height(56.dp), shape = MaterialTheme.shapes.medium) {
                Text("Block")
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = blockSubdomains, role = Role.Checkbox) { blockSubdomains = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = blockSubdomains, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text("Include subdomains (m., www., …)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WebRuleRow(
    rule: WebBlockRule,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(rule.domain, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (rule.blockSubdomains) "Includes subdomains" else "This domain only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Remove ${rule.domain}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── PIN dialog ────────────────────────────────────────────────────────────────

@Composable
private fun BlockPinDialog(
    state: BlockState,
    onIntent: (BlockIntent) -> Unit,
) {
    PinEntrySheet(
        title = "Enter your PIN",
        subtitle = "Needed to change your block list",
        value = state.pinInput,
        error = state.pinError,
        onDigit = { digit -> onIntent(BlockIntent.PinInputChanged(state.pinInput + digit)) },
        onDelete = {
            if (state.pinInput.isNotEmpty()) {
                onIntent(BlockIntent.PinInputChanged(state.pinInput.dropLast(1)))
            }
        },
        onSubmit = { onIntent(BlockIntent.SubmitPin) },
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
        FilterInfo(ContentFilter.REELS, "Instagram Reels", "Closes the Reels tab and feed"),
        FilterInfo(ContentFilter.SHORTS, "YouTube Shorts", "Closes Shorts, keeps regular videos"),
        FilterInfo(ContentFilter.TIKTOK_FYP, "TikTok For You", "Closes the For You feed"),
    )

@Composable
private fun ContentTab(
    activeFilters: Set<ContentFilter>,
    onToggle: (ContentFilter, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Keep the app, lose the feed. FocusGuard closes these the moment they open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(CONTENT_FILTERS.size) { index ->
            val info = CONTENT_FILTERS[index]
            val checked = info.filter in activeFilters
            FocusCard(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.large)
                        .toggleable(value = checked, role = Role.Switch) { onToggle(info.filter, it) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.SmartDisplay, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(info.title, style = MaterialTheme.typography.titleMedium)
                        Text(info.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
