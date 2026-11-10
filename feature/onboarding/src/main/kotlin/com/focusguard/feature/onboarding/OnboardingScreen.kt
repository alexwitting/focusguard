package com.focusguard.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.ui.FocusCard
import com.focusguard.core.ui.SectionLabel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check permissions immediately whenever the user returns from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.forceRefresh()
    }

    val vpnLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            // VPN consent result — forceRefresh will fire on the subsequent ON_RESUME.
        }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.NavigateToMain -> onComplete()

                OnboardingEffect.OpenAccessibilitySettings ->
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

                OnboardingEffect.OpenUsageAccessSettings ->
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

                OnboardingEffect.RequestBatteryOptimization ->
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )

                OnboardingEffect.RequestVpnPrepare -> {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent != null) vpnLauncher.launch(prepareIntent)
                    // If null the permission is already granted; ON_RESUME recheck will confirm.
                }

                OnboardingEffect.OpenExactAlarmSettings -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }

                OnboardingEffect.OpenAppDetailsSettings ->
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            StepProgress(currentStep = state.step)

            Spacer(Modifier.height(28.dp))

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                },
                label = "onboarding-step",
                modifier = Modifier.weight(1f),
            ) { step ->
                // Derived from `state` for *this* step, so the outgoing page keeps its own
                // granted flag while it animates away instead of reading the new step's.
                StepContent(
                    step = step,
                    isGranted = state.isGranted(step),
                    hasAttempted = step in state.attemptedSteps,
                    onGrant = viewModel::onGrant,
                    onNext = viewModel::onNext,
                    onSkip = viewModel::onSkip,
                    onOpenAppInfo = viewModel::onOpenAppInfo,
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    isGranted: Boolean,
    hasAttempted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    val meta = stepMeta(step)
    val isPermissionStep = step != OnboardingStep.WELCOME && step != OnboardingStep.DONE

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepIcon(icon = meta.icon, granted = isPermissionStep && isGranted)

            Text(text = meta.title, style = MaterialTheme.typography.displaySmall)

            Text(
                text = meta.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (meta.privacyNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = meta.privacyNote,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (isPermissionStep && !isGranted && meta.guide.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                GuideCard(steps = meta.guide, note = meta.guideNote)
            }

            // Android 13+ greys out a sideloaded app's accessibility switch as a "restricted
            // setting". Tapping it shows a dialog and leaves the service off, which reads to the
            // user as "I enabled it and the app didn't notice". Only the user can lift the
            // restriction, from App info's overflow menu, so explain that once they've tried.
            if (needsRestrictedSettingsHelp(step, hasAttempted, isGranted)) {
                RestrictedSettingHelp(onOpenAppInfo = onOpenAppInfo)
            }
        }

        Spacer(Modifier.height(16.dp))
        StepActions(
            step = step,
            isGranted = isGranted,
            hasAttempted = hasAttempted,
            optional = meta.optional,
            onGrant = onGrant,
            onNext = onNext,
            onSkip = onSkip,
        )
    }
}

private fun needsRestrictedSettingsHelp(
    step: OnboardingStep,
    hasAttempted: Boolean,
    isGranted: Boolean,
): Boolean {
    val restrictedSettingsExist = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return restrictedSettingsExist && step == OnboardingStep.ACCESSIBILITY && hasAttempted && !isGranted
}

@Composable
private fun StepIcon(
    icon: ImageVector,
    granted: Boolean,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape),
            )
        }
    }
}

@Composable
private fun GuideCard(
    steps: List<String>,
    note: String?,
) {
    FocusCard {
        SectionLabel("On the next screen")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { index, text ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(26.dp)
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.surface,
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestrictedSettingHelp(onOpenAppInfo: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Switch greyed out?", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text =
                    "Android blocks this setting for apps installed outside an app store. " +
                        "Open App info, tap ⋮ in the top corner, choose \"Allow restricted " +
                        "settings\", then come back and try again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenAppInfo) {
                Text("Open app info")
            }
        }
    }
}

@Composable
private fun StepActions(
    step: OnboardingStep,
    isGranted: Boolean,
    hasAttempted: Boolean,
    optional: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val primaryModifier = Modifier.fillMaxWidth().height(56.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            step == OnboardingStep.WELCOME ->
                Button(onClick = onNext, modifier = primaryModifier) { Text("Get started") }

            step == OnboardingStep.DONE ->
                Button(onClick = onNext, modifier = primaryModifier) { Text("Start using FocusGuard") }

            isGranted ->
                Button(onClick = onNext, modifier = primaryModifier) { Text("Continue") }

            else -> {
                Button(onClick = onGrant, modifier = primaryModifier) {
                    Text(if (hasAttempted) "Try again" else "Open settings")
                }
                // After a first attempt (or on optional steps) offer a way past, so the user is
                // never stuck on a permission their phone won't grant.
                if (hasAttempted || optional) {
                    TextButton(
                        onClick = if (optional) onSkip else onNext,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Text(if (optional) "Skip for now" else "Continue anyway")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgress(currentStep: OnboardingStep) {
    val allSteps =
        buildList {
            add(OnboardingStep.WELCOME)
            add(OnboardingStep.ACCESSIBILITY)
            add(OnboardingStep.USAGE_ACCESS)
            add(OnboardingStep.BATTERY)
            add(OnboardingStep.VPN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(OnboardingStep.EXACT_ALARM)
            add(OnboardingStep.DONE)
        }
    val currentIndex = allSteps.indexOf(currentStep)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Step ${currentIndex + 1} of ${allSteps.size}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            allSteps.forEachIndexed { index, _ ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    index < currentIndex -> MaterialTheme.colorScheme.onSurface
                                    index == currentIndex -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                )
            }
        }
        Text(
            text = "${currentIndex + 1} of ${allSteps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class StepMeta(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val guide: List<String> = emptyList(),
    val guideNote: String? = null,
    val privacyNote: String? = null,
    val optional: Boolean = false,
)

private val STEP_META: Map<OnboardingStep, StepMeta> =
    mapOf(
        OnboardingStep.WELCOME to
            StepMeta(
                icon = Icons.Default.Shield,
                title = "Take back control",
                description =
                    "FocusGuard blocks distracting apps, websites and short-form feeds. " +
                        "It needs a few permissions to do that — each step explains what one is for.",
            ),
        OnboardingStep.ACCESSIBILITY to
            StepMeta(
                icon = Icons.Default.Accessibility,
                title = "Turn on the accessibility service",
                description =
                    "Lets FocusGuard see which app is open so it can block it the moment it appears.",
                privacyNote = "Never reads what you type",
                guide =
                    listOf(
                        "Open \"Installed apps\" (or \"Downloaded apps\")",
                        "Tap FocusGuard",
                        "Turn the switch on, then tap Allow",
                    ),
            ),
        OnboardingStep.USAGE_ACCESS to
            StepMeta(
                icon = Icons.Default.QueryStats,
                title = "Allow usage access",
                description =
                    "Lets FocusGuard see how long you spend in each app, to enforce daily " +
                        "limits and build your weekly report.",
                privacyNote = "Stays on this phone. Nothing is uploaded.",
                guide =
                    listOf(
                        "Find FocusGuard in the list",
                        "Turn on \"Permit usage access\"",
                        "Press back to return here",
                    ),
                guideNote = "The list can be long — use the search icon if you can't see it.",
            ),
        OnboardingStep.BATTERY to
            StepMeta(
                icon = Icons.Default.BatteryFull,
                title = "Keep FocusGuard running",
                description =
                    "Stops Android putting FocusGuard to sleep. Without this, blocking can " +
                        "quietly stop when the screen is off.",
                guide = listOf("Tap Allow in the dialog that appears"),
                guideNote =
                    "No dialog? Go to Settings › Battery › Battery optimisation, find " +
                        "FocusGuard and choose \"Don't optimise\".",
            ),
        OnboardingStep.VPN to
            StepMeta(
                icon = Icons.Default.VpnKey,
                title = "Block websites",
                description =
                    "Creates a local-only VPN that filters website lookups on this phone. " +
                        "It never connects to an outside server.",
                privacyNote = "No traffic leaves your device",
                guide = listOf("Tap OK in the dialog that appears"),
                guideNote = "Optional — skip it if you only want to block apps.",
                optional = true,
            ),
        OnboardingStep.EXACT_ALARM to
            StepMeta(
                icon = Icons.Default.Schedule,
                title = "Allow exact alarms",
                description =
                    "Lets FocusGuard step in at the exact minute a daily limit runs out.",
                guide =
                    listOf(
                        "Find FocusGuard in the list",
                        "Turn on \"Allow setting alarms and reminders\"",
                    ),
                guideNote = "Optional — skip it if you don't use daily time limits.",
                optional = true,
            ),
        OnboardingStep.DONE to
            StepMeta(
                icon = Icons.Default.Lock,
                title = "You're all set",
                description =
                    "Head to Block to choose what to limit, and set a PIN in Settings so " +
                        "future-you can't undo it on a whim.",
            ),
    )

private fun stepMeta(step: OnboardingStep): StepMeta = STEP_META.getValue(step)
