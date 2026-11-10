package com.focusguard

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.ExtensionPolicy
import com.focusguard.core.system.service.FocusGuardAccessibilityService
import com.focusguard.core.ui.AppIconOrAvatar
import com.focusguard.core.ui.theme.FocusGuardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        blockTarget = BlockTarget.from(intent)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        enableEdgeToEdge()
        setContent {
            // Always dark: a calm, low-glare stop screen regardless of the system theme.
            FocusGuardTheme(darkTheme = true) {
                val viewModel: BlockedViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                val target = blockTarget
                LaunchedEffect(target) {
                    viewModel.initialize(target.packageName, target.reason)
                }
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            BlockedEffect.ExtensionGranted -> finish()
                        }
                    }
                }

                BlockedContent(
                    packageName = target.packageName,
                    label = target.label,
                    reason = target.reason,
                    canRequestExtension = state.canRequestExtension,
                    onGoHome = ::goHome,
                    onRequestExtension = { viewModel.requestExtension(target.packageName) },
                )
            }
        }
    }

    /**
     * The activity is launched SINGLE_TOP, so a second block arriving while this one is showing
     * (a different app, or a time limit firing) is delivered here instead of to onCreate.
     * Without this the screen kept showing the first app's name and reason.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockTarget = BlockTarget.from(intent)
    }

    private var blockTarget by mutableStateOf(BlockTarget("", "This app", BlockReason.ALWAYS_BLOCKED))

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        finish()
    }
}

/** What this block screen is about, read from the launching intent. */
private data class BlockTarget(
    val packageName: String,
    val label: String,
    val reason: BlockReason,
) {
    companion object {
        fun from(intent: Intent): BlockTarget {
            val pkg = intent.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_PACKAGE).orEmpty()
            val label =
                intent.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL)
                    ?: pkg.ifEmpty { "This app" }
            val reason =
                intent
                    .getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCK_REASON)
                    ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
                    ?: BlockReason.ALWAYS_BLOCKED
            return BlockTarget(pkg, label, reason)
        }
    }
}

private data class BlockedCopy(
    val headline: String,
    val body: String,
    val icon: ImageVector,
)

private fun blockedCopy(
    reason: BlockReason,
    label: String,
): BlockedCopy =
    when (reason) {
        BlockReason.TIME_LIMIT_REACHED ->
            BlockedCopy(
                headline = "That's your time on $label for today",
                body = "You set this limit to protect your time. It resets at midnight.",
                icon = Icons.Outlined.HourglassBottom,
            )
        BlockReason.ALWAYS_BLOCKED ->
            BlockedCopy(
                headline = "$label is blocked",
                body = "You chose to keep this app closed. You can change that in your block list.",
                icon = Icons.Outlined.Block,
            )
        BlockReason.OUTSIDE_ALLOWED_WINDOW ->
            BlockedCopy(
                headline = "$label is closed right now",
                body = "It's outside the hours you allowed for this app.",
                icon = Icons.Outlined.Schedule,
            )
        BlockReason.CONTENT_FILTER ->
            BlockedCopy(
                headline = "This feed is filtered out",
                body = "The rest of $label still works.",
                icon = Icons.Outlined.SmartDisplay,
            )
        BlockReason.WEBSITE_BLOCKED ->
            BlockedCopy(
                headline = "This website is blocked",
                body = "It's on your block list.",
                icon = Icons.Outlined.Language,
            )
        BlockReason.BEDTIME ->
            BlockedCopy(
                headline = "It's bedtime",
                body = "$label is off until your bedtime ends. Get some rest.",
                icon = Icons.Outlined.Bedtime,
            )
    }

@Composable
private fun BlockedContent(
    packageName: String,
    label: String,
    reason: BlockReason,
    canRequestExtension: Boolean,
    onGoHome: () -> Unit,
    onRequestExtension: () -> Unit,
) {
    val copy = blockedCopy(reason, label)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
            ) {
                BlockedBadge(packageName = packageName, label = label, icon = copy.icon)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = copy.headline,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGoHome,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                ) { Text("Go home") }
                if (canRequestExtension) {
                    OutlinedButton(
                        onClick = onRequestExtension,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Ask for ${ExtensionPolicy.MINUTES} more minutes") }
                }
            }
        }
    }
}

@Composable
private fun BlockedBadge(
    packageName: String,
    label: String,
    icon: ImageVector,
) {
    Box(modifier = Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        Box(Modifier.size(124.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        if (packageName.isNotEmpty()) {
            AppIconOrAvatar(packageName = packageName, label = label, size = 80.dp)
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 30.dp, bottom = 30.dp)
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
        }
    }
}
