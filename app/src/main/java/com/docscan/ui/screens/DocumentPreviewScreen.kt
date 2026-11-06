package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docscan.data.model.FilterType
import com.docscan.data.model.ScanMode
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import com.docscan.util.NidCleanProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCrop: (pageIndex: Int) -> Unit,
    onRetake: (pageIndex: Int) -> Unit,
    onSaved: (docId: Long) -> Unit
) {
    val capturedPages by viewModel.capturedPages.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val selectedCardType by viewModel.selectedIdCardType.collectAsState()
    val isTwoSidedCardPreview = scanMode == ScanMode.ID_CARD && selectedCardType.isTwoSided
    val docTitle by viewModel.sessionDocumentTitle.collectAsState()
    val isSaving by viewModel.isProcessing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCardIndex by remember { mutableIntStateOf(0) }
    // Cache the preview for each captured page. A regular scan must never allocate a
    // second empty NID slot (which previously displayed an endless spinner).
    var processedPreviews by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var originalPreviews by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isRendering by remember { mutableStateOf(true) }

    // Dialog & Sheet States
    var showRenameDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showCompareModal by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(capturedPages.size) {
        selectedCardIndex = selectedCardIndex.coerceIn(0, (capturedPages.size - 1).coerceAtLeast(0))
    }

    val activeIndex = selectedCardIndex.coerceIn(0, (capturedPages.size - 1).coerceAtLeast(0))

    // Re-render only the current capture session on crop/filter/rotation changes.
    // Keep heavy bitmap work off the main thread and clear the loading state even
    // if an individual page is missing or fails to decode.
    LaunchedEffect(capturedPages, scanMode, selectedCardType, activeIndex) {
        isRendering = capturedPages.isNotEmpty()
        try {
            val renderedPages = withContext(Dispatchers.IO) {
                // Batch scans may contain 100 pages. Decode the selected page only
                // (both sides for NID) to avoid allocating hundreds of bitmaps.
                val rendered = MutableList<Pair<Bitmap?, Bitmap?>>(capturedPages.size) {
                    null to null
                }
                val indices = if (isTwoSidedCardPreview) {
                    capturedPages.indices.take(2)
                } else {
                    listOf(activeIndex).filter { it in capturedPages.indices }
                }
                indices.forEach { index ->
                    val page = capturedPages[index]
                    val original = FileUtils.loadBitmap(
                        page.originalPath,
                        maxDimension = if (scanMode == ScanMode.ID_CARD) 2600 else 2048
                    )
                    rendered[index] = if (original == null) {
                        null to null
                    } else {
                        val processed = try {
                            val rotated = ImageProcessor.rotate(original, page.rotationDegrees)
                            // ML Kit already returns cropped, perspective-corrected JPEGs.
                            val fullFrame = page.mlKitProcessed && page.corners.size == 4 &&
                                kotlin.math.abs(page.corners[0].x) < 0.015f &&
                                kotlin.math.abs(page.corners[0].y) < 0.015f &&
                                kotlin.math.abs(page.corners[2].x - 1f) < 0.015f &&
                                kotlin.math.abs(page.corners[2].y - 1f) < 0.015f
                            val cropped = if (fullFrame) rotated else
                                ImageProcessor.perspectiveCrop(rotated, page.corners)
                            val cleaned = if (isTwoSidedCardPreview) {
                                NidCleanProcessor.enhance(cropped, isFront = index == 0)
                            } else {
                                cropped
                            }
                            val filtered = ImageProcessor.applyFilter(
                                source = cleaned,
                                filterType = page.filterType,
                                brightness = page.brightness,
                                contrast = page.contrast
                            )
                            if (page.watermarkText.isNullOrBlank()) filtered else
                                ImageProcessor.applyWatermark(
                                    source = filtered,
                                    text = page.watermarkText!!,
                                    opacity = page.watermarkOpacity,
                                    colorLong = page.watermarkColor
                                )
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Keep the source page visible instead of showing a spinner forever.
                            original
                        }
                        original to processed
                    }
                }
                rendered
            }
            originalPreviews = renderedPages.map { it.first }
            processedPreviews = renderedPages.map { it.second }
        } finally {
            isRendering = false
        }
    }

    val activeOriginal = originalPreviews.getOrNull(activeIndex)
    val activeProcessed = processedPreviews.getOrNull(activeIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeManager.themeColor(Color(0xFF141414), Color(0xFFF8FAFC)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Header Bar (Screenshot 2: Back Arrow, Document Title + Edit Icon, Help Icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(ThemeManager.themeColor(Color(0xFF1E1E1E), Color(0xFFFFFFFF)))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("preview_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showRenameDialog = true }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = docTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.testTag("btn_preview_help")
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help Guide",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }
            }

            // 2. Sub-header Banner: "ⓘ Tap the image to rotate or retake" (Screenshot 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThemeManager.themeColor(Color(0xFF262626), Color(0xFFF1F5F9)))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap the image to rotate or retake",
                    color = ThemeManager.themeColor(Color(0xFFE2E8F0), Color(0xFF475569)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 3. Standard documents show ONLY the scanned page, fitted to the
            // available screen. The two-card A4 preview is exclusive to NID mode.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (isTwoSidedCardPreview) 16.dp else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isTwoSidedCardPreview) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (capturedPages.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Page ${activeIndex + 1} of ${capturedPages.size}",
                                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = { showCompareModal = true }) {
                                    Icon(Icons.Default.Compare, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Compare")
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isRendering -> CircularProgressIndicator(color = Color(0xFF14B8A6))
                                activeProcessed != null -> Image(
                                    bitmap = activeProcessed.asImageBitmap(),
                                    contentDescription = "Scanned page ${activeIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            viewModel.currentCropPageIndex.value = activeIndex
                                            onNavigateToCrop(activeIndex)
                                        }
                                )
                                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Could not load the scanned page",
                                        color = ThemeManager.themeColor(Color.White, Color(0xFF334155))
                                    )
                                    TextButton(onClick = { onRetake(activeIndex) }) {
                                        Text("Retake page")
                                    }
                                }
                            }
                        }
                        // Only multi-page scans need page selectors. A single scan has
                        // no thumbnails or empty back-side slot below the image.
                        if (capturedPages.size > 1) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(capturedPages.indices.toList()) { index ->
                                    val selected = index == activeIndex
                                    Surface(
                                        modifier = Modifier
                                            .size(width = 56.dp, height = 66.dp)
                                            .clickable { selectedCardIndex = index },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) Color(0xFF14B8A6) else Color(0xFF94A3B8)
                                        ),
                                        color = Color.White
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val thumbnail = processedPreviews.getOrNull(index)
                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail.asImageBitmap(),
                                                    contentDescription = "Page ${index + 1}",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize().padding(3.dp)
                                                )
                                            } else {
                                                Text("${index + 1}", color = Color(0xFF334155))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // The Front/Back sheet is intentionally retained for ID Card.
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.96f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 24.dp)
                                        .background(Color(0xFF334155), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("01", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.clickable { showCompareModal = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Compare, contentDescription = "Compare", tint = Color(0xFF0F766E), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Compare", color = Color(0xFF0F766E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxSize().padding(top = 34.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IdCardSlotItem(
                                    title = "FRONT SIDE",
                                    bitmap = processedPreviews.getOrNull(0),
                                    isSelected = selectedCardIndex == 0,
                                    isRendering = isRendering,
                                    onClick = {
                                        selectedCardIndex = 0
                                        viewModel.currentCropPageIndex.value = 0
                                        onNavigateToCrop(0)
                                    },
                                    onCrop = {
                                        viewModel.currentCropPageIndex.value = 0
                                        onNavigateToCrop(0)
                                    },
                                    onRotateLeft = {
                                        viewModel.currentCropPageIndex.value = 0
                                        viewModel.rotateCurrentPageLeft()
                                    },
                                    onRotateRight = {
                                        viewModel.currentCropPageIndex.value = 0
                                        viewModel.rotateCurrentPageRight()
                                    },
                                    onRetake = { onRetake(0) },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                IdCardSlotItem(
                                    title = "BACK SIDE",
                                    bitmap = processedPreviews.getOrNull(1),
                                    isSelected = selectedCardIndex == 1,
                                    isRendering = isRendering,
                                    onClick = {
                                        selectedCardIndex = 1
                                        viewModel.currentCropPageIndex.value = 1
                                        onNavigateToCrop(1)
                                    },
                                    onCrop = {
                                        viewModel.currentCropPageIndex.value = 1
                                        onNavigateToCrop(1)
                                    },
                                    onRotateLeft = {
                                        viewModel.currentCropPageIndex.value = 1
                                        viewModel.rotateCurrentPageLeft()
                                    },
                                    onRotateRight = {
                                        viewModel.currentCropPageIndex.value = 1
                                        viewModel.rotateCurrentPageRight()
                                    },
                                    onRetake = { onRetake(1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Bottom Toolbar (Screenshot 2: Crop, Filter, Add Watermark, Confirm Button)
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Crop
                    PreviewBottomAction(
                        icon = Icons.Default.Crop,
                        label = "Crop",
                        testTag = "btn_preview_crop",
                        onClick = {
                            viewModel.currentCropPageIndex.value = selectedCardIndex
                            onNavigateToCrop(selectedCardIndex)
                        }
                    )

                    // Filter
                    PreviewBottomAction(
                        icon = Icons.Default.Filter,
                        label = "Filter",
                        testTag = "btn_preview_filter",
                        onClick = {
                            showFilterSheet = true
                        }
                    )

                    // Add Watermark
                    PreviewBottomAction(
                        icon = Icons.Default.BrandingWatermark,
                        label = "Add Watermark",
                        testTag = "btn_preview_watermark",
                        onClick = {
                            showWatermarkDialog = true
                        }
                    )

                    // Confirm Save Button (Green/Teal rounded button with checkmark)
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF00C48C))
                            .clickable {
                                viewModel.saveScannedDocument { docId ->
                                    Toast.makeText(context, "Document saved", Toast.LENGTH_SHORT).show()
                                    onSaved(docId)
                                }
                            }
                            .testTag("btn_preview_save_confirm"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm and Save",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- FILTER SELECTION BOTTOM SHEET (Screenshot 3 style) ---
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = ThemeManager.themeColor(Color(0xFF1E1E1E), Color(0xFFFFFFFF))
            ) {
                FilterSelectionPanel(
                    currentFilter = capturedPages.getOrNull(selectedCardIndex)?.filterType ?: FilterType.MAGIC_COLOR,
                    previewBitmap = activeOriginal,
                    onSelectFilter = { filter ->
                        viewModel.applyFilterToSession(filter, selectedCardIndex)
                    },
                    onApplyAll = { filter ->
                        viewModel.applyFilterToSession(filter, -1)
                        Toast.makeText(context, "Applied ${filter.name} to all pages", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }

        // --- WATERMARK DIALOG ---
        if (showWatermarkDialog) {
            WatermarkEditorDialog(
                initialText = capturedPages.getOrNull(selectedCardIndex)?.watermarkText ?: "",
                initialOpacity = capturedPages.getOrNull(selectedCardIndex)?.watermarkOpacity ?: 0.35f,
                onApply = { text, opacity, colorLong, applyToAll ->
                    if (applyToAll) {
                        viewModel.applyWatermarkToSession(text, opacity, colorLong, -1)
                    } else {
                        viewModel.applyWatermarkToSession(text, opacity, colorLong, selectedCardIndex)
                    }
                    showWatermarkDialog = false
                    Toast.makeText(context, if (text.isNotBlank()) "Watermark applied" else "Watermark removed", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showWatermarkDialog = false }
            )
        }

        // --- BEFORE / AFTER COMPARE MODAL ---
        if (showCompareModal) {
            CompareBeforeAfterModal(
                originalBitmap = activeOriginal,
                processedBitmap = activeProcessed,
                cardLabel = if (isTwoSidedCardPreview) {
                    if (selectedCardIndex == 0) "Front Side" else "Back Side"
                } else "Page ${selectedCardIndex + 1}",
                onDismiss = { showCompareModal = false }
            )
        }

        // --- RENAME DIALOG ---
        if (showRenameDialog) {
            RenameDocDialog(
                currentTitle = docTitle,
                onConfirm = { newTitle ->
                    viewModel.sessionDocumentTitle.value = newTitle
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false }
            )
        }

        // --- HELP GUIDE DIALOG ---
        if (showHelpDialog) {
            IdCardHelpDialog(onDismiss = { showHelpDialog = false })
        }
    }
}

@Composable
private fun IdCardSlotItem(
    title: String,
    bitmap: Bitmap?,
    isSelected: Boolean,
    isRendering: Boolean,
    onClick: () -> Unit,
    onCrop: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isRendering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF14B8A6),
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (bitmap == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Image unavailable", color = Color(0xFF64748B))
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }


        }
    }
}

@Composable
private fun PreviewBottomAction(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFE2E8F0),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FilterSelectionPanel(
    currentFilter: FilterType,
    previewBitmap: Bitmap?,
    onSelectFilter: (FilterType) -> Unit,
    onApplyAll: (FilterType) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = listOf(
        Pair(FilterType.MAGIC_COLOR, "Magic Color"),
        Pair(FilterType.AUTO, "Auto"),
        Pair(FilterType.CLEAR, "Clear"),
        Pair(FilterType.DOCUMENT, "Document"),
        Pair(FilterType.ORIGINAL, "Original"),
        Pair(FilterType.BW, "B&W"),
        Pair(FilterType.GRAYSCALE, "Grayscale"),
        Pair(FilterType.LIGHTEN, "Lighten")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF00C48C),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CamScanner Filters",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF00C48C).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF00C48C).copy(alpha = 0.35f)),
                modifier = Modifier.clickable { onApplyAll(currentFilter) }
            ) {
                Text(
                    text = "Apply to All Pages",
                    color = Color(0xFF00C48C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { (type, label) ->
                val isSelected = currentFilter == type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectFilter(type) }
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00C48C) else Color(0xFF334155),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            val mini = remember(previewBitmap, type) {
                                ImageProcessor.applyFilter(previewBitmap, type)
                            }
                            Image(
                                bitmap = mini.asImageBitmap(),
                                contentDescription = label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (type == FilterType.MAGIC_COLOR) Icons.Default.AutoAwesome else Icons.Default.AutoAwesome,
                                contentDescription = label,
                                tint = if (isSelected) Color(0xFF00C48C) else Color.White
                            )
                        }

                        // Magic Color VIP Badge
                        if (type == FilterType.MAGIC_COLOR) {
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
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF00C48C) else Color(0xFFE2E8F0)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun WatermarkEditorDialog(
    initialText: String,
    initialOpacity: Float,
    onApply: (text: String, opacity: Float, colorLong: Long, applyToAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    var selectedColor by remember { mutableStateOf(0xFF555555L) }
    var applyToAll by remember { mutableStateOf(true) }

    val presets = listOf("CONFIDENTIAL", "FOR OFFICIAL USE ONLY", "COPY", "NID VERIFICATION", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add Watermark", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Security watermark overlay across card pages",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    placeholder = { Text("e.g. FOR OFFICIAL USE ONLY") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Quick Presets:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(3).forEach { preset ->
                        Surface(
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { text = preset }
                        ) {
                            Text(
                                text = preset,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Opacity: ${(opacity * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF374151)
                )
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..0.8f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF14B8A6),
                        activeTrackColor = Color(0xFF14B8A6)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Apply to Front & Back pages", fontSize = 13.sp, color = Color(0xFF374151))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(text, opacity, selectedColor, applyToAll) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C))
            ) {
                Text(text = if (text.isBlank()) "Clear Watermark" else "Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
private fun CompareBeforeAfterModal(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    cardLabel: String,
    onDismiss: () -> Unit
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var isSideBySide by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Compare Quality",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = cardLabel,
                            fontSize = 12.sp,
                            color = Color(0xFF14B8A6)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle between Split Slider & Side by Side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFF2D3748),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                color = if (!isSideBySide) Color(0xFF14B8A6) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { isSideBySide = false }
                            ) {
                                Text(
                                    text = "Split Slider",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isSideBySide) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                color = if (isSideBySide) Color(0xFF14B8A6) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { isSideBySide = true }
                            ) {
                                Text(
                                    text = "Side by Side",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSideBySide) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isSideBySide) {
                    // Split Comparison View
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    splitFraction = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                                }
                            }
                    ) {
                        val widthPx = constraints.maxWidth.toFloat()

                        if (originalBitmap != null) {
                            Image(
                                bitmap = originalBitmap.asImageBitmap(),
                                contentDescription = "Original",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (processedBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(splitFraction)
                                    .clip(RoundedCornerShape(0.dp))
                            ) {
                                Image(
                                    bitmap = processedBitmap.asImageBitmap(),
                                    contentDescription = "Enhanced",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(this@BoxWithConstraints.maxWidth)
                                )
                            }
                        }

                        // Divider Line
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .background(Color(0xFF00C48C))
                                .align(Alignment.TopStart)
                                .padding(start = (widthPx * splitFraction).dp)
                        )

                        // Top Badges
                        Text(
                            text = "ENHANCED",
                            color = Color(0xFF00C48C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        Text(
                            text = "ORIGINAL",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "↔ Drag horizontally to compare before and after",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Side-by-side View
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Original", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            if (originalBitmap != null) {
                                Image(
                                    bitmap = originalBitmap.asImageBitmap(),
                                    contentDescription = "Original",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .border(1.dp, Color(0xFF14B8A6), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Enhanced", fontSize = 11.sp, color = Color(0xFF14B8A6), fontWeight = FontWeight.Bold)
                            if (processedBitmap != null) {
                                Image(
                                    bitmap = processedBitmap.asImageBitmap(),
                                    contentDescription = "Enhanced",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done Comparing", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RenameDocDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename Document", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Document Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.ifBlank { currentTitle }) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IdCardHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFF0F766E))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "ID Card Scanning Tips", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "• Place the ID card on a flat, contrasting background (e.g. dark table for white cards).",
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
                Text(
                    text = "• Align all four corners within the camera boundary.",
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
                Text(
                    text = "• Tap the card to rotate 90° or adjust corners in the Crop Editor.",
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
                Text(
                    text = "• Use 'Compare' to inspect original vs. perspective-corrected quality.",
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text("Got It")
            }
        }
    )
}
