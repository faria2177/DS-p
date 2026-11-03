package com.docscan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.util.PageSize
import com.docscan.util.PdfExportConfig
import com.docscan.util.PdfSettings

@Composable
fun PdfExportDialog(
    initialTitle: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onExportAndSaveToDevice: ((PdfExportConfig) -> Unit)? = null,
    onExportAndShare: (PdfExportConfig) -> Unit,
    onExportAndOpen: (PdfExportConfig) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var selectedPageSize by remember { mutableStateOf(PdfSettings.pageSize(context)) }
    var includePageNumbers by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Export PDF Document", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Create a high-quality multi-page PDF ($pageCount ${if (pageCount == 1) "page" else "pages"}):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("PDF File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Page Format:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PageSize.values().forEach { size ->
                        FilterChip(
                            selected = selectedPageSize == size,
                            onClick = { selectedPageSize = size },
                            label = { Text(size.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = includePageNumbers,
                        onCheckedChange = { includePageNumbers = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add page numbering (e.g. 1/3)", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Optional PDF Watermark") },
                    placeholder = { Text("e.g. CONFIDENTIAL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onExportAndSaveToDevice != null) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val config = PdfExportConfig(
                                    title = title.ifBlank { initialTitle },
                                    pageSize = selectedPageSize,
                                    includePageNumbers = includePageNumbers,
                                    watermarkText = watermarkText.ifBlank { null },
                                    quality = PdfSettings.quality(context)
                                )
                                onExportAndSaveToDevice(config)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save to Device", maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val config = PdfExportConfig(
                                title = title.ifBlank { initialTitle },
                                pageSize = selectedPageSize,
                                includePageNumbers = includePageNumbers,
                                watermarkText = watermarkText.ifBlank { null },
                                quality = PdfSettings.quality(context)
                            )
                            onExportAndShare(config)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (onExportAndSaveToDevice != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val config = PdfExportConfig(
                                title = title.ifBlank { initialTitle },
                                pageSize = selectedPageSize,
                                includePageNumbers = includePageNumbers,
                                watermarkText = watermarkText.ifBlank { null },
                                quality = PdfSettings.quality(context)
                            )
                            onExportAndOpen(config)
                        }
                    ) {
                        Text("Open Preview")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        },
        dismissButton = null
    )
}
