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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(
                currentStep = state.step,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(48.dp))

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                },
                label = "onboarding-step",
                modifier = Modifier.weight(1f),
            ) { step ->
                StepContent(
                    step = step,
                    isGranted = viewModel.isCurrentStepGranted(),
                    hasAttempted = step in state.attemptedSteps,
                    onGrant = viewModel::onGrant,
                    onNext = viewModel::onNext,
                    onSkip = viewModel::onSkip,
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
) {
    val meta = stepMeta(step)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon with optional granted badge
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (isGranted && step != OnboardingStep.WELCOME && step != OnboardingStep.DONE) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = meta.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = meta.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // How-to guide shown on permission steps once the user has tapped Grant,
        // or always shown if they haven't tapped yet (so they know what to expect).
        if (meta.guide.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp),
                        ).padding(16.dp),
            ) {
                Text(
                    text = "How to enable",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = meta.guide,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        when (step) {
            OnboardingStep.WELCOME -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Get started")
                }
            }

            OnboardingStep.DONE -> {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Start using FocusGuard")
                }
            }

            else -> {
                if (isGranted) {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                } else {
                    Button(
                        onClick = onGrant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hasAttempted) "Try again" else "Grant access")
                    }

                    // After a first attempt, offer a bypass so the user is never stuck.
                    if (hasAttempted) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue anyway")
                        }
                    }

                    if (meta.optional) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                            Text("Skip for now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(
    currentStep: OnboardingStep,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        allSteps.forEachIndexed { index, _ ->
            val active = index == currentIndex
            val done = index < currentIndex
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .background(
                            color =
                                when {
                                    active -> MaterialTheme.colorScheme.primary
                                    done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

private data class StepMeta(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val guide: String = "",
    val optional: Boolean = false,
)

@Composable
private fun stepMeta(step: OnboardingStep): StepMeta =
    when (step) {
        OnboardingStep.WELCOME ->
            StepMeta(
                icon = Icons.Default.Shield,
                title = "Take back control",
                description =
                    "FocusGuard blocks distracting apps, websites, and short-form content. " +
                        "We need a few permissions to keep you on track — here's exactly what each one does and how to enable it.",
            )

        OnboardingStep.ACCESSIBILITY ->
            StepMeta(
                icon = Icons.Default.Accessibility,
                title = "Accessibility service",
                description =
                    "Detects which app is in the foreground so FocusGuard can enforce your " +
                        "block rules in real time. No keyboard input or screen content is ever read.",
                guide =
                    "1. On the next screen, scroll down to find \"Installed apps\".\n" +
                        "2. Tap \"FocusGuard\" in the list.\n" +
                        "3. Enable the toggle at the top.\n" +
                        "4. Tap \"Allow\" in the confirmation dialog.",
            )

        OnboardingStep.USAGE_ACCESS ->
            StepMeta(
                icon = Icons.Default.QueryStats,
                title = "Usage access",
                description =
                    "Lets FocusGuard see how much time you spend in each app, so it can " +
                        "enforce daily time limits and show accurate usage reports.",
                guide =
                    "1. On the next screen, find \"FocusGuard\" in the app list.\n" +
                        "2. Tap it.\n" +
                        "3. Enable \"Permit usage access\".\n\n" +
                        "Tip: on some phones the list is long — scroll or use the search icon.",
            )

        OnboardingStep.BATTERY ->
            StepMeta(
                icon = Icons.Default.BatteryFull,
                title = "Battery optimisation",
                description =
                    "Prevents Android from sleeping FocusGuard's background services. " +
                        "Without this, blocking may silently stop working when the screen turns off.",
                guide =
                    "A system dialog will appear — tap \"Allow\".\n\n" +
                        "If no dialog appears, go to:\n" +
                        "Settings → Battery → Battery optimisation\n" +
                        "Find FocusGuard and select \"Don't optimise\".",
            )

        OnboardingStep.VPN ->
            StepMeta(
                icon = Icons.Default.VpnKey,
                title = "Website blocking (optional)",
                description =
                    "Creates a local-only VPN to intercept DNS queries and block websites " +
                        "and short-form content CDNs. No traffic leaves your device — it never connects " +
                        "to an external server.",
                guide =
                    "A system dialog will appear — tap \"OK\" to allow the local VPN.\n\n" +
                        "You can revoke this at any time from Settings → VPN.\n" +
                        "Skip this step if you only want app blocking.",
                optional = true,
            )

        OnboardingStep.EXACT_ALARM ->
            StepMeta(
                icon = Icons.Default.Schedule,
                title = "Exact alarms",
                description =
                    "Required on Android 12 and above so FocusGuard can fire time-limit " +
                        "alerts at the precise second your daily allowance runs out.",
                guide =
                    "1. On the next screen, find \"FocusGuard\" in the app list.\n" +
                        "2. Tap it.\n" +
                        "3. Enable \"Allow setting alarms and reminders\".\n\n" +
                        "Skip this if you don't use daily time limits.",
                optional = true,
            )

        OnboardingStep.DONE ->
            StepMeta(
                icon = Icons.Default.Lock,
                title = "You're all set!",
                description =
                    "FocusGuard is ready. Head to Block to choose which apps to limit, " +
                        "or check Settings to adjust daily time limits and set a PIN.",
            )
    }
