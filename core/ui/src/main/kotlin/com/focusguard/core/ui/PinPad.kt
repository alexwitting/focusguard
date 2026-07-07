package com.focusguard.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Grid definition for the numeric keypad; null = empty spacer, "⌫" = backspace key.
private val PIN_PAD_ROWS: List<List<String?>> =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(null, "0", "⌫"),
    )

/**
 * Six-dot PIN indicator + numeric keypad, shared by every PIN-entry dialog in the app.
 */
@Composable
fun PinPad(
    value: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Six-dot indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            repeat(6) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(
                                color =
                                    if (index < value.length) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                shape = CircleShape,
                            ),
                )
            }
        }

        // Reserved-height error slot — avoids layout shift
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Numeric grid
        PIN_PAD_ROWS.forEach { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    when {
                        key == null ->
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                            )
                        key == "⌫" ->
                            FilledTonalButton(
                                onClick = onDelete,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                enabled = value.isNotEmpty(),
                            ) {
                                Text(key, style = MaterialTheme.typography.titleMedium)
                            }
                        else ->
                            FilledTonalButton(
                                onClick = { if (value.length < 6) onDigit(key[0]) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                enabled = value.length < 6,
                            ) {
                                Text(key, style = MaterialTheme.typography.titleMedium)
                            }
                    }
                }
            }
        }
    }
}
