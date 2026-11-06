package com.docscan.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.data.model.FilterType
import com.docscan.ui.components.QuadCropView
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CropFilterScreen(
    viewModel: ScannerViewModel,
    targetDocumentId: Long? = null,
    onNavigateBack: () -> Unit,
    onFinishAndOpenDoc: (Long) -> Unit
) {
    val context = LocalContext.current
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val currentCropPageIndex by viewModel.currentCropPageIndex.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

    if (capturedPages.isEmpty() || currentCropPageIndex !in capturedPages.indices) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    val currentPage = capturedPages[currentCropPageIndex]
    var currentBitmap by remember(currentPage.originalPath) {
        mutableStateOf<Bitmap?>(null)
    }
    // Decoding a large camera JPEG must not block the Compose UI thread.
    LaunchedEffect(currentPage.originalPath) {
        currentBitmap = withContext(Dispatchers.IO) {
            FileUtils.loadBitmap(currentPage.originalPath)
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Crop, 1 = Filter & Tone
    var previewFilteredBitmap by remember(currentPage.originalPath) { mutableStateOf<Bitmap?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }

    // The saved document rotates the original *before* perspective cropping.
    // Keep the on-screen result identical, and do expensive image work off the UI thread.
    LaunchedEffect(
        currentBitmap, currentPage.corners, currentPage.filterType,
        currentPage.brightness, currentPage.contrast, currentPage.rotationDegrees, activeTab
    ) {
        if (activeTab != 1) return@LaunchedEffect
        val src = currentBitmap ?: return@LaunchedEffect
        val corners = currentPage.corners.toList()
        val rotation = currentPage.rotationDegrees
        val filter = currentPage.filterType
        val brightness = currentPage.brightness
        val contrast = currentPage.contrast
        // Rapid slider/crop changes cancel the previous preview before starting new work.
        delay(65)
        val filtered = withContext(Dispatchers.Default) {
            val previewSource = src.scaledForScanPreview(1600)
            val rotated = ImageProcessor.rotate(previewSource, rotation)
            val cropped = ImageProcessor.perspectiveCrop(rotated, corners)
            ImageProcessor.applyFilter(cropped, filter, brightness, contrast)
        }
        previewFilteredBitmap = filtered
    }

    val filterOptions = listOf(
        Pair(FilterType.MAGIC_COLOR, "Magic Color"),
        Pair(FilterType.AUTO, "Auto"),
        Pair(FilterType.CLEAR, "Clear"),
        Pair(FilterType.DOCUMENT, "Document"),
        Pair(FilterType.ORIGINAL, "Original"),
        Pair(FilterType.BW, "B&W"),
        Pair(FilterType.GRAYSCALE, "Grayscale"),
        Pair(FilterType.LIGHTEN, "Lighten")
    )

    // Generate filter thumbnails only when the filter tab is open. Large image
    // warps during every crop-handle drag previously caused visible jank.
    var miniThumbnailBase by remember(currentPage.originalPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentBitmap, currentPage.corners, currentPage.rotationDegrees, activeTab) {
        if (activeTab != 1) return@LaunchedEffect
        val src = currentBitmap ?: return@LaunchedEffect
        val corners = currentPage.corners.toList()
        val rotation = currentPage.rotationDegrees
        miniThumbnailBase = withContext(Dispatchers.Default) {
            val rotated = ImageProcessor.rotate(src.scaledForScanPreview(240), rotation)
            val cropped = ImageProcessor.perspectiveCrop(rotated, corners)
            val aspect = cropped.height.toFloat() / cropped.width.coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, 100, (100 * aspect).toInt().coerceIn(70, 140), true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }

                Text(
                    text = if (capturedPages.size > 1) "Page ${currentCropPageIndex + 1} of ${capturedPages.size}" else "Edit Scan",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                IconButton(
                    onClick = { viewModel.rotateCurrentPageRight() }
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
                }
            }

            // Tab Selector: Crop vs Filter
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF14B8A6),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1. Perspective Crop")
                        }
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. Magic Filters")
                        }
                    }
                )
            }

            // Main Preview / Crop Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                currentBitmap?.let { bmp ->
                    if (activeTab == 0) {
                        // Interactive 4-point perspective crop
                        QuadCropView(
                            bitmap = bmp,
                            corners = currentPage.corners,
                            onCornersChanged = { updatedCorners ->
                                viewModel.updateCurrentPageCrop(updatedCorners)
                            }
                        )
                    } else {
                        // Filter Preview
                        previewFilteredBitmap?.let { filteredBmp ->
                            androidx.compose.foundation.Image(
                                bitmap = filteredBmp.asImageBitmap(),
                                contentDescription = "Filtered Scan",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC000000),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Color(0xFF14B8A6), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Enhancing Document...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Bottom Tool Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (activeTab == 0) {
                    // Crop Step Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Reset to full image
                                viewModel.updateCurrentPageCrop(
                                    listOf(
                                        Offset(0.02f, 0.02f),
                                        Offset(0.98f, 0.02f),
                                        Offset(0.98f, 0.98f),
                                        Offset(0.02f, 0.98f)
                                    )
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Full", fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.autoDetectCurrentPageCrop() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Auto", fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.aiDetectCurrentPageCrop() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                            modifier = Modifier.weight(1.4f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("✨ AI Crop", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { activeTab = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1.0f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Next", fontSize = 12.sp, maxLines = 1)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.NavigateNext, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    // --- CAMSCANNER SIGNATURE FILTER SECTION ---
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Section Header + Apply to All button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF00C48C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Enhancement Filters",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (capturedPages.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00C48C).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFF00C48C).copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable {
                                        viewModel.applyFilterToAllPages(currentPage.filterType)
                                        android.widget.Toast.makeText(context, "Applied ${currentPage.filterType.name} to all ${capturedPages.size} pages", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Apply to All",
                                        color = Color(0xFF00C48C),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Visual Filter Carousel (CamScanner Style)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filterOptions) { (fType, label) ->
                                val isSelected = currentPage.filterType == fType
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.updateCurrentPageFilter(fType) }
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 68.dp, height = 68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0F172A))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF00C48C) else Color(0xFF334155),
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val miniBase = miniThumbnailBase
                                        if (miniBase != null) {
                                            val filteredMini = remember(miniBase, fType) {
                                                ImageProcessor.applyFilter(miniBase, fType)
                                            }
                                            androidx.compose.foundation.Image(
                                                bitmap = filteredMini.asImageBitmap(),
                                                contentDescription = label,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (fType == FilterType.MAGIC_COLOR) Icons.Default.AutoAwesome else Icons.Default.Filter,
                                                contentDescription = label,
                                                tint = if (isSelected) Color(0xFF00C48C) else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Magic Color VIP Badge
                                        if (fType == FilterType.MAGIC_COLOR) {
                                            Surface(
                                                shape = RoundedCornerShape(bottomStart = 4.dp),
                                                color = Color(0xFFF59E0B),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    text = "✨ VIP",
                                                    color = Color.Black,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        // Selection Checkmark
                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF00C48C),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .size(16.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.padding(2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(5.dp))

                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF00C48C) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fine-Tuning Adjustments: Brightness & Contrast
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Brightness
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bri ${(currentPage.brightness * 100).toInt()}%",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.width(46.dp)
                                )
                                Slider(
                                    value = currentPage.brightness,
                                    onValueChange = { viewModel.updateCurrentPageAdjustments(it, currentPage.contrast) },
                                    valueRange = -0.5f..0.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00C48C),
                                        activeTrackColor = Color(0xFF00C48C),
                                        inactiveTrackColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Contrast
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Con ${(currentPage.contrast * 10).toInt() / 10f}x",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.width(46.dp)
                                )
                                Slider(
                                    value = currentPage.contrast,
                                    onValueChange = { viewModel.updateCurrentPageAdjustments(currentPage.brightness, it) },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00C48C),
                                        activeTrackColor = Color(0xFF00C48C),
                                        inactiveTrackColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: Back to Crop & Save/Next
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { activeTab = 0 },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("Back to Crop", fontSize = 13.5.sp)
                            }

                            if (currentCropPageIndex < capturedPages.size - 1) {
                                Button(
                                    onClick = {
                                        viewModel.currentCropPageIndex.value = currentCropPageIndex + 1
                                        activeTab = 0
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        "Next Page (${currentCropPageIndex + 2}/${capturedPages.size})",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.NavigateNext, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (targetDocumentId != null) {
                                            viewModel.saveScannedDocument(targetDocumentId = targetDocumentId) { docId ->
                                                onFinishAndOpenDoc(docId)
                                            }
                                        } else {
                                            showFolderDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("btn_save_document")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Document", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Choose Category Dialog on Save
    if (showFolderDialog) {
        var selectedFolder by remember { mutableStateOf("All") }
        val folderList = listOf("All", "Business", "ID Cards", "Receipts", "Personal", "Notes")

        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Save Document To Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folderList.forEach { folderName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolder = folderName }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (selectedFolder == folderName) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                folderName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedFolder == folderName) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFolderDialog = false
                        viewModel.saveScannedDocument(folder = selectedFolder) { docId ->
                            onFinishAndOpenDoc(docId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** A bounded preview keeps native OpenCV filters responsive on mid-range phones. */
private fun Bitmap.scaledForScanPreview(maxDimension: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
}
