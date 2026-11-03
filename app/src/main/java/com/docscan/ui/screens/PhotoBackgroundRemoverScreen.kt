package com.docscan.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.docscan.ui.remover.PhotoBackgroundRemoverViewModel
import com.docscan.ui.remover.model.AutoCropPadding
import com.docscan.ui.remover.model.BackgroundStyle
import com.docscan.ui.remover.model.BgRemovalProvider
import com.docscan.ui.remover.model.BgRemovalStage
import com.docscan.ui.remover.model.ManualBrushMode
import com.docscan.ui.theme.rememberAppThemePalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoBackgroundRemoverScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoBackgroundRemoverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val palette = rememberAppThemePalette()
    val isDark = palette.isDark
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bottom sheet state for save options
    var showSaveSheet by remember { mutableStateOf(false) }

    // Pick photo launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPhotoFromUri(it) }
    }

    val customBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val cr = context.contentResolver
                val stream = cr.openInputStream(it)
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    viewModel.setBackgroundStyle(BackgroundStyle.CustomPhoto(bmp))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Handle user messages
    LaunchedEffect(uiState.userMessage, uiState.errorMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopRemoverHeader(
                isDark = isDark,
                hasImage = uiState.originalBitmap != null,
                isProcessed = uiState.transparentBitmap != null,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBack = onNavigateBack,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onReset = { viewModel.reset() },
                onShare = { viewModel.shareResult() },
                onSave = { showSaveSheet = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Canvas / Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.originalBitmap == null) {
                    // Empty State: Select photo card
                    EmptyPhotoPickerCard(
                        isDark = isDark,
                        onPickGallery = { galleryLauncher.launch("image/*") },
                        onPickSample = {
                            // Generate high quality sample portrait bitmap
                            val sample = createSamplePortraitBitmap()
                            viewModel.setOriginalBitmap(sample)
                        }
                    )
                } else {
                    // Active Photo Preview & Editing Area
                    RemoverPreviewCanvas(
                        originalBitmap = uiState.originalBitmap!!,
                        previewBitmap = uiState.previewBitmap,
                        showBeforeAfterSplit = uiState.showBeforeAfterSplit,
                        splitPosition = uiState.splitPosition,
                        onSplitPositionChange = { viewModel.setSplitPosition(it) },
                        manualMode = uiState.manualMode,
                        brushSize = uiState.brushSize,
                        onBrushStroke = { x, y, isErase ->
                            viewModel.applyManualBrushStroke(x, y, isErase)
                        },
                        onBrushStrokeEnd = { viewModel.commitBrushStroke() }
                    )

                    // Processing Overlay
                    if (uiState.isProcessing) {
                        ProcessingProgressOverlay(
                            stage = uiState.stage,
                            isDark = isDark
                        )
                    }
                }
            }

            // Bottom Controls Area
            if (uiState.originalBitmap != null) {
                if (uiState.transparentBitmap == null) {
                    // Pre-processing Controls: Provider & Run Button
                    PreProcessControlsPanel(
                        isDark = isDark,
                        isProcessing = uiState.isProcessing,
                        selectedProvider = uiState.provider,
                        keepNaturalShadow = uiState.keepNaturalShadow,
                        onProviderSelected = { viewModel.setProvider(it) },
                        onToggleShadow = { viewModel.toggleNaturalShadow(it) },
                        onRemoveBackground = { viewModel.removeBackground() }
                    )
                } else {
                    // Post-processing Controls: Tabs (Background, Refine, Touch-up, Crop)
                    PostProcessControlsPanel(
                        isDark = isDark,
                        backgroundStyle = uiState.backgroundStyle,
                        onBackgroundStyleChange = { viewModel.setBackgroundStyle(it) },
                        onPickCustomBgImage = { customBgPickerLauncher.launch("image/*") },
                        keepNaturalShadow = uiState.keepNaturalShadow,
                        onToggleShadow = { viewModel.toggleNaturalShadow(it) },
                        autoCropPadding = uiState.autoCropPadding,
                        onAutoCropChange = { viewModel.setAutoCropPadding(it) },
                        showSplit = uiState.showBeforeAfterSplit,
                        onToggleSplit = { viewModel.toggleBeforeAfterSplit(it) },
                        manualMode = uiState.manualMode,
                        onManualModeChange = { viewModel.setManualMode(it) },
                        brushSize = uiState.brushSize,
                        onBrushSizeChange = { viewModel.setBrushSize(it) }
                    )
                }
            }
        }
    }

    // Save Options Modal Bottom Sheet
    if (showSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false },
            containerColor = if (isDark) Color(0xFF1E293B) else Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            SaveExportSheetContent(
                isDark = isDark,
                isTransparent = uiState.backgroundStyle is BackgroundStyle.Transparent,
                onSavePng = {
                    showSaveSheet = false
                    viewModel.saveResult(isPng = true) {}
                },
                onSaveJpeg = {
                    showSaveSheet = false
                    viewModel.saveResult(isPng = false) {}
                },
                onShare = {
                    showSaveSheet = false
                    viewModel.shareResult()
                }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Header
// -------------------------------------------------------------------------------------------------

@Composable
private fun TopRemoverHeader(
    isDark: Boolean,
    hasImage: Boolean,
    isProcessed: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        color = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isDark) Color.White else Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Photo Background Remover",
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "AI-powered precision background removal",
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isProcessed) {
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) (if (isDark) Color.White else Color(0xFF1E293B)) else Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) (if (isDark) Color.White else Color(0xFF1E293B)) else Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                } else if (hasImage) {
                    IconButton(onClick = onReset) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Empty Picker
// -------------------------------------------------------------------------------------------------

@Composable
private fun EmptyPhotoPickerCard(
    isDark: Boolean,
    onPickGallery: () -> Unit,
    onPickSample: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Select Photo to Remove Background",
            color = if (isDark) Color.White else Color(0xFF0F172A),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sub-pixel hair strand preservation, de-halo edge refinement and transparent PNG export powered by Gemini & Claude AI",
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPickGallery,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select from Gallery", fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Button(
                onClick = onPickSample,
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = if (isDark) Color.White else Color(0xFF334155))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Try Sample", fontWeight = FontWeight.Medium, color = if (isDark) Color.White else Color(0xFF334155))
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Interactive Canvas with Split & Manual Brush
// -------------------------------------------------------------------------------------------------

@Composable
private fun RemoverPreviewCanvas(
    originalBitmap: Bitmap,
    previewBitmap: Bitmap?,
    showBeforeAfterSplit: Boolean,
    splitPosition: Float,
    onSplitPositionChange: (Float) -> Unit,
    manualMode: ManualBrushMode,
    brushSize: Float,
    onBrushStroke: (Float, Float, Boolean) -> Unit,
    onBrushStrokeEnd: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(manualMode) {
                if (manualMode == ManualBrushMode.NONE) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = Offset(offset.x + pan.x, offset.y + pan.y)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        // 1. Checkerboard Pattern for transparency visualization
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tileSize = 24.dp.toPx()
            val cols = (size.width / tileSize).toInt() + 1
            val rows = (size.height / tileSize).toInt() + 1

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val isLight = (r + c) % 2 == 0
                    drawRect(
                        color = if (isLight) Color(0xFFF1F5F9) else Color(0xFFCBD5E1),
                        topLeft = Offset(c * tileSize, r * tileSize),
                        size = Size(tileSize, tileSize)
                    )
                }
            }
        }

        // 2. Render Bitmap Image
        val displayBitmap = previewBitmap ?: originalBitmap

        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(manualMode) {
                    if (manualMode != ManualBrushMode.NONE) {
                        detectDragGestures(
                            onDragEnd = { onBrushStrokeEnd() },
                            onDrag = { change, _ ->
                                change.consume()
                                val touchX = change.position.x
                                val touchY = change.position.y
                                // Convert display coordinates to bitmap pixel space
                                val bmpW = displayBitmap.width.toFloat()
                                val bmpH = displayBitmap.height.toFloat()
                                val viewW = size.width.toFloat()
                                val viewH = size.height.toFloat()

                                val scaleFactor = minOf(viewW / bmpW, viewH / bmpH)
                                val drawnW = bmpW * scaleFactor
                                val drawnH = bmpH * scaleFactor
                                val leftOffset = (viewW - drawnW) / 2f
                                val topOffset = (viewH - drawnH) / 2f

                                val pixelX = (touchX - leftOffset) / scaleFactor
                                val pixelY = (touchY - topOffset) / scaleFactor

                                if (pixelX in 0f..bmpW && pixelY in 0f..bmpH) {
                                    onBrushStroke(pixelX, pixelY, manualMode == ManualBrushMode.ERASE)
                                }
                            }
                        )
                    }
                }
        ) {
            if (showBeforeAfterSplit && previewBitmap != null) {
                // Split Screen Comparison: Original on left, Cutout on right
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Original",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    // Cutout clipped to right fraction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Removed",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            } else {
                androidx.compose.foundation.Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Photo Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Before / After Split Slider Overlay
        if (showBeforeAfterSplit && previewBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = (containerWidth * splitPosition).dp)
                    .background(Color.White)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Progress Overlay
// -------------------------------------------------------------------------------------------------

@Composable
private fun ProcessingProgressOverlay(
    stage: BgRemovalStage,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) Color(0xFF1E293B) else Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    progress = { stage.progress },
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFFE2E8F0),
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stage.title,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Refining hair strands, removing halos & optimizing alpha mask...",
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { stage.progress },
                    color = Color(0xFF8B5CF6),
                    trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Pre-process Controls Panel
// -------------------------------------------------------------------------------------------------

@Composable
private fun PreProcessControlsPanel(
    isDark: Boolean,
    isProcessing: Boolean,
    selectedProvider: BgRemovalProvider,
    keepNaturalShadow: Boolean,
    onProviderSelected: (BgRemovalProvider) -> Unit,
    onToggleShadow: (Boolean) -> Unit,
    onRemoveBackground: () -> Unit
) {
    Surface(
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Provider Selection Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(BgRemovalProvider.GEMINI, BgRemovalProvider.LOCAL_PROCESSING).forEach { p ->
                    val isSelected = p == selectedProvider
                    FilterChip(
                        selected = isSelected,
                        onClick = { onProviderSelected(p) },
                        label = {
                            Text("${p.badge} ${p.displayName}", fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF8B5CF6)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Natural Shadow Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Flare,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Keep Natural Shadow",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                        Text(
                            "Preserve soft contact shadow under subject",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }

                Switch(
                    checked = keepNaturalShadow,
                    onCheckedChange = onToggleShadow,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Remove Background Button
            Button(
                onClick = onRemoveBackground,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("remove_background_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Remove Background",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Post-process Controls Panel with Tabs
// -------------------------------------------------------------------------------------------------

@Composable
private fun PostProcessControlsPanel(
    isDark: Boolean,
    backgroundStyle: BackgroundStyle,
    onBackgroundStyleChange: (BackgroundStyle) -> Unit,
    onPickCustomBgImage: () -> Unit,
    keepNaturalShadow: Boolean,
    onToggleShadow: (Boolean) -> Unit,
    autoCropPadding: AutoCropPadding,
    onAutoCropChange: (AutoCropPadding) -> Unit,
    showSplit: Boolean,
    onToggleSplit: (Boolean) -> Unit,
    manualMode: ManualBrushMode,
    onManualModeChange: (ManualBrushMode) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Background", "Refine & Shadow", "Touch-up", "Auto Crop")

    Surface(
        color = if (isDark) Color(0xFF0F172A) else Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF8B5CF6)
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) Color(0xFF8B5CF6) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                            )
                        }
                    )
                }
            }

            // Tab Contents
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                when (selectedTabIndex) {
                    0 -> BackgroundReplacementTab(
                        isDark = isDark,
                        currentStyle = backgroundStyle,
                        onSelectStyle = onBackgroundStyleChange,
                        onPickCustomPhoto = onPickCustomBgImage
                    )
                    1 -> RefineAndShadowTab(
                        isDark = isDark,
                        keepNaturalShadow = keepNaturalShadow,
                        onToggleShadow = onToggleShadow,
                        showSplit = showSplit,
                        onToggleSplit = onToggleSplit
                    )
                    2 -> ManualTouchUpTab(
                        isDark = isDark,
                        manualMode = manualMode,
                        onManualModeChange = onManualModeChange,
                        brushSize = brushSize,
                        onBrushSizeChange = onBrushSizeChange
                    )
                    3 -> AutoCropTab(
                        isDark = isDark,
                        currentPadding = autoCropPadding,
                        onSelectPadding = onAutoCropChange
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-tabs
// -------------------------------------------------------------------------------------------------

@Composable
private fun BackgroundReplacementTab(
    isDark: Boolean,
    currentStyle: BackgroundStyle,
    onSelectStyle: (BackgroundStyle) -> Unit,
    onPickCustomPhoto: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Transparent
        BgOptionItem(
            name = "Transparent",
            isSelected = currentStyle is BackgroundStyle.Transparent,
            onClick = { onSelectStyle(BackgroundStyle.Transparent) }
        ) {
            // Checkerboard icon preview
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / 2
                drawRect(Color.White, Offset(0f, 0f), Size(s, s))
                drawRect(Color.LightGray, Offset(s, 0f), Size(s, s))
                drawRect(Color.LightGray, Offset(0f, s), Size(s, s))
                drawRect(Color.White, Offset(s, s), Size(s, s))
            }
        }

        // 2. Solid Colors
        val colors = listOf(
            Color.White to "White",
            Color(0xFF0F172A) to "Dark",
            Color(0xFF0D9488) to "Teal",
            Color(0xFF2563EB) to "Royal",
            Color(0xFFEF4444) to "Crimson",
            Color(0xFFFBBF24) to "Amber",
            Color(0xFFFCE7F3) to "Pastel Pink"
        )
        colors.forEach { (c, name) ->
            BgOptionItem(
                name = name,
                isSelected = currentStyle is BackgroundStyle.Solid && currentStyle.color == c,
                onClick = { onSelectStyle(BackgroundStyle.Solid(c, name)) }
            ) {
                Box(modifier = Modifier.fillMaxSize().background(c))
            }
        }

        // 3. Gradients
        val gradients = listOf(
            listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)) to "Sunset",
            listOf(Color(0xFF0284C7), Color(0xFF0D9488)) to "Ocean",
            listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)) to "Indigo"
        )
        gradients.forEach { (gColors, name) ->
            BgOptionItem(
                name = name,
                isSelected = currentStyle is BackgroundStyle.Gradient && currentStyle.name == name,
                onClick = { onSelectStyle(BackgroundStyle.Gradient(gColors, name)) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gColors))
                )
            }
        }

        // 4. Blur Original
        BgOptionItem(
            name = "Blur",
            isSelected = currentStyle is BackgroundStyle.Blur,
            onClick = { onSelectStyle(BackgroundStyle.Blur()) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF64748B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // 5. Custom Image
        BgOptionItem(
            name = "Custom",
            isSelected = currentStyle is BackgroundStyle.CustomPhoto,
            onClick = onPickCustomPhoto
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF475569)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BgOptionItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            border = if (isSelected) BorderStroke(2.5.dp, Color(0xFF8B5CF6)) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
            modifier = Modifier.size(44.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF8B5CF6) else Color.Gray
        )
    }
}

@Composable
private fun RefineAndShadowTab(
    isDark: Boolean,
    keepNaturalShadow: Boolean,
    onToggleShadow: (Boolean) -> Unit,
    showSplit: Boolean,
    onToggleSplit: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Keep Natural Shadow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                Text("Preserves soft contact shadows on the floor/surface", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = keepNaturalShadow,
                onCheckedChange = onToggleShadow,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Before / After Comparison", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0F172A))
                Text("Compare cut edges side-by-side with the original", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = showSplit,
                onCheckedChange = onToggleSplit,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6))
            )
        }
    }
}

@Composable
private fun ManualTouchUpTab(
    isDark: Boolean,
    manualMode: ManualBrushMode,
    onManualModeChange: (ManualBrushMode) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onManualModeChange(if (manualMode == ManualBrushMode.ERASE) ManualBrushMode.NONE else ManualBrushMode.ERASE) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualMode == ManualBrushMode.ERASE) Color(0xFFEF4444) else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = if (manualMode == ManualBrushMode.ERASE) Color.White else Color.Gray)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Erase BG", fontSize = 12.sp, color = if (manualMode == ManualBrushMode.ERASE) Color.White else (if (isDark) Color.White else Color.Black))
            }

            Button(
                onClick = { onManualModeChange(if (manualMode == ManualBrushMode.RESTORE) ManualBrushMode.NONE else ManualBrushMode.RESTORE) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualMode == ManualBrushMode.RESTORE) Color(0xFF10B981) else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.Brush, contentDescription = null, tint = if (manualMode == ManualBrushMode.RESTORE) Color.White else Color.Gray)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore", fontSize = 12.sp, color = if (manualMode == ManualBrushMode.RESTORE) Color.White else (if (isDark) Color.White else Color.Black))
            }
        }

        if (manualMode != ManualBrushMode.NONE) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brush Size: ${brushSize.toInt()}px", fontSize = 12.sp, color = if (isDark) Color.White else Color.Black)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = brushSize,
                    onValueChange = onBrushSizeChange,
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6)),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AutoCropTab(
    isDark: Boolean,
    currentPadding: AutoCropPadding,
    onSelectPadding: (AutoCropPadding) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AutoCropPadding.values().forEach { pad ->
            val isSelected = currentPadding == pad
            Button(
                onClick = { onSelectPadding(pad) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Text(
                    text = pad.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else (if (isDark) Color.White else Color.Black)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-components: Save Sheet Content
// -------------------------------------------------------------------------------------------------

@Composable
private fun SaveExportSheetContent(
    isDark: Boolean,
    isTransparent: Boolean,
    onSavePng: () -> Unit,
    onSaveJpeg: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Export Image",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isTransparent) {
            Button(
                onClick = onSavePng,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Transparent PNG", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = onSaveJpeg,
            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = if (isDark) Color.White else Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save as JPEG", fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color.Black)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onShare,
            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = if (isDark) Color.White else Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Directly", fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color.Black)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sample Bitmap Generator for Quick Testing
// -------------------------------------------------------------------------------------------------

private fun createSamplePortraitBitmap(): Bitmap {
    val w = 600
    val h = 800
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Background gradient (wall/room)
    val bgPaint = Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(android.graphics.Color.rgb(210, 220, 235), android.graphics.Color.rgb(180, 195, 215)),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    // Subject: Portrait silhouette (Head, Hair, Shoulders)
    val skinPaint = Paint().apply { color = android.graphics.Color.rgb(235, 195, 170); isAntiAlias = true }
    val hairPaint = Paint().apply { color = android.graphics.Color.rgb(40, 25, 20); isAntiAlias = true }
    val shirtPaint = Paint().apply { color = android.graphics.Color.rgb(30, 90, 180); isAntiAlias = true }

    // Shoulders / Torso
    canvas.drawRoundRect(120f, 500f, 480f, 850f, 80f, 80f, shirtPaint)
    // Neck
    canvas.drawRect(260f, 420f, 340f, 520f, skinPaint)
    // Head / Face
    canvas.drawOval(200f, 220f, 400f, 470f, skinPaint)
    // Hair
    canvas.drawOval(190f, 180f, 410f, 320f, hairPaint)
    // Eye accents
    val eyePaint = Paint().apply { color = android.graphics.Color.rgb(30, 30, 30); isAntiAlias = true }
    canvas.drawCircle(260f, 340f, 8f, eyePaint)
    canvas.drawCircle(340f, 340f, 8f, eyePaint)

    return bmp
}
