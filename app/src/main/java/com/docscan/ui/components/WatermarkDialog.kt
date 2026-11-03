package com.docscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onApplyWatermark: (text: String, opacity: Float, colorLong: Long) -> Unit
) {
    var text by remember { mutableStateOf(initialText.ifEmpty { "CONFIDENTIAL" }) }
    var opacity by remember { mutableFloatStateOf(0.35f) }
    var selectedColor by remember { mutableStateOf(Color(0xFF64748B)) }

    val presetTexts = listOf(
        "CONFIDENTIAL",
        "ORIGINAL COPY",
        "FOR VERIFICATION ONLY",
        "SAMPLE ONLY",
        "COPY DO NOT DUPLICATE",
        "OFFICIAL DOCUMENT"
    )

    val colorChoices = listOf(
        Color(0xFF64748B), // Slate Gray
        Color(0xFFDC2626), // Red
        Color(0xFF2563EB), // Blue
        Color(0xFF059669)  // Emerald
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add Anti-theft Watermark", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Protect your scanned document with a custom anti-copy watermark:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetTexts.forEach { preset ->
                        FilterChip(
                            selected = text == preset,
                            onClick = { text = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Color:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorChoices.forEach { col ->
                            Surface(
                                shape = CircleShape,
                                color = col,
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(
                                        width = if (selectedColor == col) 2.5.dp else 1.dp,
                                        color = if (selectedColor == col) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    ),
                                onClick = { selectedColor = col }
                            ) {}
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Opacity Slider
                Text("Watermark Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..0.8f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onApplyWatermark(text.trim(), opacity, selectedColor.toArgb().toLong())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply Watermark")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
