package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.data.model.PageEntity
import com.docscan.util.AutoOrientationHelper
import com.docscan.scanner.PerspectiveCorrector
import com.docscan.ui.components.QuadCropView
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.EdgeDetector
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-featured OpenCV Image Cropping Interface.
 * Allows users to adjust document corners with sub-pixel precision,
 * inspect real-time OpenCV perspective warp previews, apply OpenCV enhancements,
 * and save rectified documents to storage.
 */
@Composable
fun OpenCvCropScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    initialPageIndex: Int = 0,
    initialImageUri: String? = null,
    onNavigateBack: () -> Unit,
    onSavedDocument: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentCorners by remember {
        mutableStateOf(
            listOf(
                Offset(0.05f, 0.05f),
                Offset(0.95f, 0.05f),
                Offset(0.95f, 0.95f),
                Offset(0.05f, 0.95f)
            )
        )
    }

    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Loading image...") }
    var selectedCornerIndex by remember { mutableIntStateOf(-1) }
    var showNudgePad by remember { mutableStateOf(false) }

    // Live OpenCV Warp Preview State
    var isPreviewWarpMode by remember { mutableStateOf(false) }
    var warpedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingWarp by remember { mutableStateOf(false) }
    var selectedEnhancementMode by remember {
        mutableStateOf(PerspectiveCorrector.EnhancementMode.ORIGINAL)
    }

    // Save Dialog State
    var showSaveDialog by remember { mutableStateOf(false) }
    var documentTitle by remember {
        mutableStateOf("Document_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}")
    }
    var documentFolder by remember { mutableStateOf("All") }
    var isSaving by remember { mutableStateOf(false) }
    var savedSuccessDocId by remember { mutableStateOf<Long?>(null) }

    // Photo picker to allow selecting any image from device
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                statusMessage = "Loading chosen image..."
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    loadedBitmap = bmp
                    val detected = withContext(Dispatchers.IO) {
                        EdgeDetector.detectDocumentCorners(bmp)
                    }
                    currentCorners = detected
                    statusMessage = "Document edges auto-detected via OpenCV"
                    Toast.makeText(context, "Image loaded & corners detected with OpenCV", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to load chosen image", Toast.LENGTH_SHORT).show()
                }
                isLoading = false
            }
        }
    }

    BackHandler {
        if (isPreviewWarpMode) {
            isPreviewWarpMode = false
        } else {
            onNavigateBack()
        }
    }

    // Initial load logic
    LaunchedEffect(initialDocId, initialImageUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            var bmp: Bitmap? = null
            if (!initialImageUri.isNullOrBlank()) {
                try {
                    val uri = Uri.parse(initialImageUri)
                    bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    bmp = null
                }
            } else if (initialDocId != null && initialDocId > 0) {
                try {
                    val pages = viewModel.getPagesForDocumentDirect(initialDocId)
                    val targetPage = pages.getOrNull(initialPageIndex) ?: pages.firstOrNull()
                    if (targetPage != null) {
                        val path = targetPage.originalImagePath.ifBlank { targetPage.processedImagePath }
                        bmp = FileUtils.loadBitmap(path)
                    }
                } catch (e: Exception) {
                    bmp = null
                }
            } else {
                // Fallback to latest captured page if present
                val pages = viewModel.capturedPages.value
                val curPage = pages.getOrNull(viewModel.currentCropPageIndex.value) ?: pages.firstOrNull()
                if (curPage != null) {
                    bmp = FileUtils.loadBitmap(curPage.originalPath)
                }
            }

            withContext(Dispatchers.Main) {
                if (bmp != null) {
                    loadedBitmap = bmp
                    scope.launch(Dispatchers.IO) {
                        val detected = EdgeDetector.detectDocumentCorners(bmp)
                        withContext(Dispatchers.Main) {
                            currentCorners = detected
                            statusMessage = "OpenCV Corner Detector active"
                            isLoading = false
                        }
                    }
                } else {
                    statusMessage = "No image selected. Please pick an image."
                    isLoading = false
                }
            }
        }
    }

    // Function to generate OpenCV Warp Preview
    fun updateWarpPreview(mode: PerspectiveCorrector.EnhancementMode = selectedEnhancementMode) {
        val bmp = loadedBitmap ?: return
        if (currentCorners.size != 4) return
        isGeneratingWarp = true
        scope.launch(Dispatchers.IO) {
            val preview = EdgeDetector.generateOpenCvWarpPreview(
                bitmap = bmp,
                corners = currentCorners,
                enhancementMode = mode,
                maxPreviewDimension = 1400
            )
            withContext(Dispatchers.Main) {
                warpedPreviewBitmap = preview
                isGeneratingWarp = false
            }
        }
    }

    // Apply OpenCV Auto-Detection
    fun triggerAutoDetect() {
        val bmp = loadedBitmap ?: return
        isLoading = true
        statusMessage = "Running OpenCV contour detection..."
        scope.launch(Dispatchers.IO) {
            val detected = EdgeDetector.detectDocumentCorners(bmp)
            withContext(Dispatchers.Main) {
                currentCorners = detected
                isLoading = false
                Toast.makeText(context, "OpenCV auto-detected document corners", Toast.LENGTH_SHORT).show()
                if (isPreviewWarpMode) {
                    updateWarpPreview()
                }
            }
        }
    }

    // Apply OpenCV Sub-pixel Corner Snapping
    fun triggerSubpixelSnap() {
        val bmp = loadedBitmap ?: return
        if (currentCorners.size != 4) return
        isLoading = true
        statusMessage = "Refining corners with OpenCV sub-pixel engine..."
        scope.launch(Dispatchers.IO) {
            val refined = EdgeDetector.refineNormalizedCorners(bmp, currentCorners)
            withContext(Dispatchers.Main) {
                currentCorners = refined
                isLoading = false
                Toast.makeText(context, "Corners magnetically snapped to document edges", Toast.LENGTH_SHORT).show()
                if (isPreviewWarpMode) {
                    updateWarpPreview()
                }
            }
        }
    }

    // Function to apply Aspect Ratio Presets
    fun applyPreset(presetType: String) {
        val bmp = loadedBitmap ?: return
        val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()

        when (presetType) {
            "Full" -> {
                currentCorners = listOf(
                    Offset(0f, 0f),
                    Offset(1f, 0f),
                    Offset(1f, 1f),
                    Offset(0f, 1f)
                )
                Toast.makeText(context, "Full frame selected", Toast.LENGTH_SHORT).show()
            }
            "A4" -> {
                val targetAspect = 1f / 1.414f // 0.707
                val normW: Float
                val normH: Float
                if (imgAspect > targetAspect) {
                    normH = 0.90f
                    normW = (normH / imgAspect) * targetAspect
                } else {
                    normW = 0.90f
                    normH = (normW * imgAspect) / targetAspect
                }
                val l = ((1f - normW) / 2f).coerceIn(0.02f, 0.45f)
                val r = (l + normW).coerceIn(0.55f, 0.98f)
                val t = ((1f - normH) / 2f).coerceIn(0.02f, 0.45f)
                val b = (t + normH).coerceIn(0.55f, 0.98f)
                currentCorners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
                Toast.makeText(context, "A4 Document ratio applied", Toast.LENGTH_SHORT).show()
            }
            "ID Card" -> {
                val targetAspect = 1.586f // ID-1 Card Standard
                val normW: Float
                val normH: Float
                if (imgAspect > targetAspect) {
                    normH = 0.85f
                    normW = (normH / imgAspect) * targetAspect
                } else {
                    normW = 0.90f
                    normH = (normW * imgAspect) / targetAspect
                }
                val l = ((1f - normW) / 2f).coerceIn(0.02f, 0.45f)
                val r = (l + normW).coerceIn(0.55f, 0.98f)
                val t = ((1f - normH) / 2f).coerceIn(0.02f, 0.45f)
                val b = (t + normH).coerceIn(0.55f, 0.98f)
                currentCorners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
                Toast.makeText(context, "ID Card ratio applied", Toast.LENGTH_SHORT).show()
            }
            "Letter" -> {
                val targetAspect = 8.5f / 11f // 0.772
                val normW: Float
                val normH: Float
                if (imgAspect > targetAspect) {
                    normH = 0.90f
                    normW = (normH / imgAspect) * targetAspect
                } else {
                    normW = 0.90f
                    normH = (normW * imgAspect) / targetAspect
                }
                val l = ((1f - normW) / 2f).coerceIn(0.02f, 0.45f)
                val r = (l + normW).coerceIn(0.55f, 0.98f)
                val t = ((1f - normH) / 2f).coerceIn(0.02f, 0.45f)
                val b = (t + normH).coerceIn(0.55f, 0.98f)
                currentCorners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
                Toast.makeText(context, "Letter ratio applied", Toast.LENGTH_SHORT).show()
            }
            "Square" -> {
                val minDim = minOf(bmp.width, bmp.height).toFloat()
                val normW = minDim / bmp.width.toFloat() * 0.9f
                val normH = minDim / bmp.height.toFloat() * 0.9f
                val l = ((1f - normW) / 2f).coerceIn(0.02f, 0.45f)
                val r = (l + normW).coerceIn(0.55f, 0.98f)
                val t = ((1f - normH) / 2f).coerceIn(0.02f, 0.45f)
                val b = (t + normH).coerceIn(0.55f, 0.98f)
                currentCorners = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))
                Toast.makeText(context, "1:1 Square ratio applied", Toast.LENGTH_SHORT).show()
            }
        }
        if (isPreviewWarpMode) {
            updateWarpPreview()
        }
    }

    // Nudge selected corner by dx, dy
    fun nudgeSelectedCorner(dx: Float, dy: Float) {
        if (selectedCornerIndex in 0..3) {
            val updated = currentCorners.toMutableList()
            val curr = updated[selectedCornerIndex]
            updated[selectedCornerIndex] = Offset(
                (curr.x + dx).coerceIn(0f, 1f),
                (curr.y + dy).coerceIn(0f, 1f)
            )
            currentCorners = updated
            if (isPreviewWarpMode) {
                updateWarpPreview()
            }
        }
    }

    // Rotate bitmap 90 degrees
    fun rotateImage(degrees: Int) {
        val bmp = loadedBitmap ?: return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val rotated = ImageProcessor.rotate(bmp, degrees)
            val newCorners = EdgeDetector.detectDocumentCorners(rotated)
            withContext(Dispatchers.Main) {
                loadedBitmap = rotated
                currentCorners = newCorners
                isLoading = false
                Toast.makeText(context, "Rotated $degrees°", Toast.LENGTH_SHORT).show()
                if (isPreviewWarpMode) {
                    updateWarpPreview()
                }
            }
        }
    }

    // Auto Upright Orientation
    fun triggerAutoUpright() {
        val bmp = loadedBitmap ?: return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val result = AutoOrientationHelper.detectAndCorrectOrientation(bmp)
            withContext(Dispatchers.Main) {
                if (result.rotationAppliedDegrees != 0) {
                    val rotated = ImageProcessor.rotate(bmp, result.rotationAppliedDegrees)
                    val newCorners = EdgeDetector.detectDocumentCorners(rotated)
                    loadedBitmap = rotated
                    currentCorners = newCorners
                    Toast.makeText(context, "Aligned upright (+${result.rotationAppliedDegrees}°)", Toast.LENGTH_SHORT).show()
                    if (isPreviewWarpMode) {
                        updateWarpPreview()
                    }
                } else {
                    Toast.makeText(context, "Document is already upright", Toast.LENGTH_SHORT).show()
                }
                isLoading = false
            }
        }
    }

    // Save final perspective cropped document
    fun performSaveDocument() {
        val bmp = loadedBitmap ?: return
        if (currentCorners.size != 4) return
        isSaving = true

        scope.launch(Dispatchers.IO) {
            try {
                val cvCorners = currentCorners.map {
                    org.opencv.core.Point((it.x * bmp.width).toDouble(), (it.y * bmp.height).toDouble())
                }

                // 1. Perspective warp via OpenCV
                val warpResult = PerspectiveCorrector.correctPerspective(
                    sourceBitmap = bmp,
                    corners = cvCorners,
                    mode = selectedEnhancementMode
                )

                val warpedBmp = warpResult.bitmap

                // 2. Save image to permanent storage
                val finalPath = FileUtils.saveBitmapToDocStorage(context, warpedBmp, "OPENCV_CROP")

                // 3. Create Page & Document in database
                val page = PageEntity(
                    documentId = 0,
                    pageNumber = 1,
                    originalImagePath = finalPath,
                    processedImagePath = finalPath,
                    filterType = when (selectedEnhancementMode) {
                        PerspectiveCorrector.EnhancementMode.ORIGINAL -> "ORIGINAL"
                        PerspectiveCorrector.EnhancementMode.MAGIC_COLOR -> "MAGIC_COLOR"
                        PerspectiveCorrector.EnhancementMode.SHADOW_REMOVAL -> "LIGHTEN"
                        PerspectiveCorrector.EnhancementMode.CRISP_BW -> "BW"
                        PerspectiveCorrector.EnhancementMode.GRAYSCALE -> "GRAYSCALE"
                    }
                )

                val newDocId = viewModel.saveNewDocument(
                    title = documentTitle.ifBlank { "Scanned Document" },
                    folder = documentFolder,
                    pages = listOf(page)
                )

                withContext(Dispatchers.Main) {
                    isSaving = false
                    showSaveDialog = false
                    savedSuccessDocId = newDocId
                    Toast.makeText(context, "Cropped document saved successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeManager.themeColor(Color(0xFF101012), Color(0xFFF8FAFC)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP APP BAR ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .background(ThemeManager.themeColor(Color(0xFF1A1A1E), Color(0xFFFFFFFF)))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPreviewWarpMode) {
                            isPreviewWarpMode = false
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.testTag("btn_opencv_crop_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPreviewWarpMode) "OpenCV Warp Preview" else "OpenCV Crop & Adjust",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0F766E).copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, Color(0xFF14B8A6))
                        ) {
                            Text(
                                text = "OpenCV",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14B8A6),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isPreviewWarpMode) "Inspecting de-skewed document" else "Drag 4 corners or auto-detect",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Toggle Nudge D-Pad
                IconButton(
                    onClick = { showNudgePad = !showNudgePad },
                    modifier = Modifier.testTag("btn_toggle_nudge_pad")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Fine-Tune D-Pad",
                        tint = if (showNudgePad) Color(0xFF14B8A6) else Color(0xFF94A3B8)
                    )
                }

                // Choose Image from Gallery button
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("btn_pick_image_crop")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Pick Image",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            // ── QUICK PRESET SUB-TOOLBAR ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141417))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Detect (OpenCV Contour detection)
                PresetChip(
                    icon = Icons.Default.AutoAwesome,
                    label = "Auto Detect",
                    tint = Color(0xFF00C48C),
                    testTag = "btn_opencv_auto_detect",
                    onClick = { triggerAutoDetect() }
                )

                // Sub-pixel Edge Snapping (OpenCV cornerSubPix)
                PresetChip(
                    icon = Icons.Default.FitScreen,
                    label = "Snap to Edge",
                    tint = Color(0xFF14B8A6),
                    testTag = "btn_opencv_snap_corners",
                    onClick = { triggerSubpixelSnap() }
                )

                // A4 Document Preset
                PresetChip(
                    icon = Icons.Default.Description,
                    label = "A4",
                    tint = Color(0xFF38BDF8),
                    testTag = "btn_preset_a4",
                    onClick = { applyPreset("A4") }
                )

                // ID Card Preset
                PresetChip(
                    icon = Icons.Default.CreditCard,
                    label = "ID Card",
                    tint = Color(0xFFF59E0B),
                    testTag = "btn_preset_id_card",
                    onClick = { applyPreset("ID Card") }
                )

                // Letter Preset
                PresetChip(
                    icon = Icons.Default.Layers,
                    label = "Letter",
                    tint = Color(0xFFA855F7),
                    testTag = "btn_preset_letter",
                    onClick = { applyPreset("Letter") }
                )

                // Square 1:1 Preset
                PresetChip(
                    icon = Icons.Default.CropFree,
                    label = "1:1",
                    tint = Color(0xFFEC4899),
                    testTag = "btn_preset_square",
                    onClick = { applyPreset("Square") }
                )

                // Full Frame
                PresetChip(
                    icon = Icons.Default.CropFree,
                    label = "Full",
                    tint = Color(0xFF94A3B8),
                    testTag = "btn_preset_full",
                    onClick = { applyPreset("Full") }
                )

                // Auto Upright Orientation
                PresetChip(
                    icon = Icons.Default.ScreenRotation,
                    label = "Upright",
                    tint = Color(0xFF38BDF8),
                    testTag = "btn_preset_upright",
                    onClick = { triggerAutoUpright() }
                )
            }

            // ── MAIN INTERACTIVE WORKSPACE ──────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF00C48C),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = statusMessage,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                    }
                } else if (loadedBitmap != null) {
                    if (isPreviewWarpMode) {
                        // ── WARPED PERSPECTIVE PREVIEW VIEW ──
                        if (isGeneratingWarp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF14B8A6))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Computing OpenCV Bicubic Homography...", color = Color.White, fontSize = 12.sp)
                            }
                        } else if (warpedPreviewBitmap != null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Image view
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = warpedPreviewBitmap!!.asImageBitmap(),
                                        contentDescription = "OpenCV Warped Document Preview",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Fit
                                    )

                                    // Resolution info badge
                                    Surface(
                                        color = Color(0xCC0F172A),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "${warpedPreviewBitmap!!.width} × ${warpedPreviewBitmap!!.height} px",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Enhancement mode selector bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF16161A))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EnhancementChip(
                                        title = "Original",
                                        isSelected = selectedEnhancementMode == PerspectiveCorrector.EnhancementMode.ORIGINAL,
                                        onClick = {
                                            selectedEnhancementMode = PerspectiveCorrector.EnhancementMode.ORIGINAL
                                            updateWarpPreview(PerspectiveCorrector.EnhancementMode.ORIGINAL)
                                        }
                                    )
                                    EnhancementChip(
                                        title = "Magic Color",
                                        isSelected = selectedEnhancementMode == PerspectiveCorrector.EnhancementMode.MAGIC_COLOR,
                                        onClick = {
                                            selectedEnhancementMode = PerspectiveCorrector.EnhancementMode.MAGIC_COLOR
                                            updateWarpPreview(PerspectiveCorrector.EnhancementMode.MAGIC_COLOR)
                                        }
                                    )
                                    EnhancementChip(
                                        title = "Crisp B&W",
                                        isSelected = selectedEnhancementMode == PerspectiveCorrector.EnhancementMode.CRISP_BW,
                                        onClick = {
                                            selectedEnhancementMode = PerspectiveCorrector.EnhancementMode.CRISP_BW
                                            updateWarpPreview(PerspectiveCorrector.EnhancementMode.CRISP_BW)
                                        }
                                    )
                                    EnhancementChip(
                                        title = "Grayscale",
                                        isSelected = selectedEnhancementMode == PerspectiveCorrector.EnhancementMode.GRAYSCALE,
                                        onClick = {
                                            selectedEnhancementMode = PerspectiveCorrector.EnhancementMode.GRAYSCALE
                                            updateWarpPreview(PerspectiveCorrector.EnhancementMode.GRAYSCALE)
                                        }
                                    )
                                }
                            }
                        } else {
                            Text("Preview failed to render", color = Color.Gray)
                        }
                    } else {
                        // ── QUAD CROP ADJUSTMENT VIEW ──
                        QuadCropView(
                            bitmap = loadedBitmap!!,
                            corners = currentCorners,
                            onCornersChanged = { updated ->
                                currentCorners = updated
                            },
                            selectedCornerIndex = selectedCornerIndex,
                            onCornerSelected = { idx ->
                                selectedCornerIndex = idx
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("opencv_quad_crop_view")
                        )

                        // Floating instruction pill
                        Surface(
                            color = Color(0xCC1E293B),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = "Drag corner handles • 2.5x Loupe active",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Floating Fine-tune D-Pad Panel
                    if (showNudgePad && !isPreviewWarpMode) {
                        Surface(
                            color = Color(0xEE1E293B),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = when (selectedCornerIndex) {
                                        0 -> "Top-Left (1)"
                                        1 -> "Top-Right (2)"
                                        2 -> "Bottom-Right (3)"
                                        3 -> "Bottom-Left (4)"
                                        else -> "Corner (Tap 1-4)"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14B8A6)
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                IconButton(
                                    onClick = { nudgeSelectedCorner(0f, -0.004f) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { nudgeSelectedCorner(-0.004f, 0f) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left", tint = Color.White)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFF0F766E), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (selectedCornerIndex in 0..3) "${selectedCornerIndex + 1}" else "•",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = { nudgeSelectedCorner(0.004f, 0f) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White)
                                    }
                                }
                                IconButton(
                                    onClick = { nudgeSelectedCorner(0f, 0.004f) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No image loaded for cropping",
                            color = Color(0xFFE2E8F0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select an image from device gallery or document library to adjust corners with OpenCV.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                            modifier = Modifier.testTag("btn_select_image_empty")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Image to Crop", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── BOTTOM ACTION BAR ───────────────────────────────────────
            Surface(
                color = Color(0xFF1A1A1E),
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
                    // Rotate Left
                    IconButton(
                        onClick = { rotateImage(270) },
                        modifier = Modifier.testTag("btn_crop_rotate_left")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White, modifier = Modifier.size(22.dp))
                            Text("Left", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }

                    // Rotate Right
                    IconButton(
                        onClick = { rotateImage(90) },
                        modifier = Modifier.testTag("btn_crop_rotate_right")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White, modifier = Modifier.size(22.dp))
                            Text("Right", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }

                    // Toggle Live OpenCV Preview
                    OutlinedButton(
                        onClick = {
                            if (isPreviewWarpMode) {
                                isPreviewWarpMode = false
                            } else {
                                isPreviewWarpMode = true
                                updateWarpPreview()
                            }
                        },
                        border = BorderStroke(1.dp, if (isPreviewWarpMode) Color(0xFF14B8A6) else Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPreviewWarpMode) Color(0xFF0F766E).copy(alpha = 0.25f) else Color.Transparent
                        ),
                        modifier = Modifier.testTag("btn_toggle_opencv_preview")
                    ) {
                        Icon(
                            imageVector = if (isPreviewWarpMode) Icons.Default.Tune else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (isPreviewWarpMode) Color(0xFF14B8A6) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPreviewWarpMode) "Adjust Corners" else "OpenCV Preview",
                            color = if (isPreviewWarpMode) Color(0xFF14B8A6) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Confirm & Save Cropped Document Button
                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_save_opencv_crop")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // ── SAVE DOCUMENT DIALOG ─────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            containerColor = Color(0xFF242428),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color(0xFF00C48C),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Cropped Document",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "The document will be perspective-corrected and de-skewed using OpenCV bicubic warping before saving.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = documentTitle,
                        onValueChange = { documentTitle = it },
                        label = { Text("Document Name", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00C48C),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_save_crop_title")
                    )

                    OutlinedTextField(
                        value = documentFolder,
                        onValueChange = { documentFolder = it },
                        label = { Text("Folder / Category", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00C48C),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_save_crop_folder")
                    )

                    if (isSaving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00C48C),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Warping & saving document...", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { performSaveDocument() },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                    modifier = Modifier.testTag("btn_confirm_save_crop")
                ) {
                    Text("Save Document", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // ── SAVE SUCCESS DIALOG ──────────────────────────────────────────
    if (savedSuccessDocId != null) {
        val docId = savedSuccessDocId!!
        AlertDialog(
            onDismissRequest = {
                savedSuccessDocId = null
                onSavedDocument(docId)
            },
            containerColor = Color(0xFF242428),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00C48C),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Document Saved!",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "\"$documentTitle\" has been cropped with OpenCV and saved to your document collection.",
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedSuccessDocId = null
                        onSavedDocument(docId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C))
                ) {
                    Text("Open Document", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savedSuccessDocId = null
                        // Keep on crop screen so user can crop another image
                    }
                ) {
                    Text("Crop Another", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun PresetChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF26262B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
private fun EnhancementChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF00C48C) else Color(0xFF26262B),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00C48C) else Color(0xFF334155)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.Black else Color(0xFFE2E8F0),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
