package com.focusguard.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** PINs are always this many digits. */
const val PIN_LENGTH = 6

private val KEY_SIZE = 76.dp

// null = empty cell, "del" = backspace.
private val PIN_PAD_ROWS: List<List<String?>> =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(null, "0", "del"),
    )

/**
 * Six-dot indicator + large round keypad, shared by every PIN entry in the app. [onComplete]
 * fires as soon as the sixth digit is typed, so there's no separate confirm button. When
 * [error] appears the dots shake and turn clay.
 */
@Composable
fun PinPad(
    value: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 360
                    -12f at 60
                    12f at 120
                    -8f at 180
                    8f at 240
                    -4f at 300
                },
            )
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(
            filled = value.length,
            isError = error != null,
            modifier = Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) },
        )
        Spacer(Modifier.height(10.dp))
        // Reserved height so the keypad doesn't jump when a message appears.
        Box(
            Modifier
                .height(20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            if (error != null) {
                Text(error, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PIN_PAD_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { key ->
                        when (key) {
                            null -> Spacer(Modifier.size(KEY_SIZE))
                            "del" ->
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDelete()
                                    },
                                    enabled = value.isNotEmpty(),
                                    modifier = Modifier.size(KEY_SIZE),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete", modifier = Modifier.size(26.dp))
                                }
                            else ->
                                DigitKey(digit = key) {
                                    if (value.length < PIN_LENGTH) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDigit(key[0])
                                        if (value.length + 1 == PIN_LENGTH) onComplete()
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDots(
    filled: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isError) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.semantics { contentDescription = "$filled of $PIN_LENGTH digits entered" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(PIN_LENGTH) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (on) color else MaterialTheme.colorScheme.background, CircleShape)
                    .border(2.dp, if (on || isError) color else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@Composable
private fun DigitKey(
    digit: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.offset { IntOffset(0, (-1).dp.toPx().roundToInt()) })
        }
    }
}
