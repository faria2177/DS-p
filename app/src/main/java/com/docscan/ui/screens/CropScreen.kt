package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.data.model.ScanMode
import com.docscan.ui.components.QuadCropView
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.AutoOrientationHelper
import com.docscan.util.EdgeDetector
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CropScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onConfirmCrop: () -> Unit,
    onRetake: (pageIndex: Int) -> Unit
) {
    val capturedPages by viewModel.capturedPages.collectAsState()
    val currentCropIndex by viewModel.currentCropPageIndex.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val isIdCardMode = (scanMode == ScanMode.ID_CARD)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCornerIndex by remember { mutableIntStateOf(0) }
    var showFineTunePad by remember { mutableStateOf(false) }
    var showWarpPreviewDialog by remember { mutableStateOf(false) }
    var warpedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingWarp by remember { mutableStateOf(false) }

    // Spring animation for the 4 corners
    val animTL = remember { Animatable(Offset(0.05f, 0.05f), Offset.VectorConverter) }
    val animTR = remember { Animatable(Offset(0.95f, 0.05f), Offset.VectorConverter) }
    val animBR = remember { Animatable(Offset(0.95f, 0.95f), Offset.VectorConverter) }
    val animBL = remember { Animatable(Offset(0.05f, 0.95f), Offset.VectorConverter) }

    BackHandler {
        onNavigateBack()
    }

    val currentPage = capturedPages.getOrNull(currentCropIndex)

    // Sync animatable corners when page changes, loads, or rotates
    LaunchedEffect(currentCropIndex, currentPage?.originalPath, currentPage?.rotationDegrees) {
        val initialCorners = currentPage?.corners ?: listOf(
            Offset(0.05f, 0.05f),
            Offset(0.95f, 0.05f),
            Offset(0.95f, 0.95f),
            Offset(0.05f, 0.95f)
        )
        if (initialCorners.size == 4) {
            animTL.snapTo(initialCorners[0])
            animTR.snapTo(initialCorners[1])
            animBR.snapTo(initialCorners[2])
            animBL.snapTo(initialCorners[3])
        }
    }

    // Smooth spring animation helper
    fun animateCornersWithSpring(targetCorners: List<Offset>, message: String? = null) {
        if (targetCorners.size != 4) return
        scope.launch {
            launch { animTL.animateTo(targetCorners[0], spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { animTR.animateTo(targetCorners[1], spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { animBR.animateTo(targetCorners[2], spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { animBL.animateTo(targetCorners[3], spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            viewModel.updateCurrentPageCrop(targetCorners)
            if (message != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load and rotate bitmap for current page
    LaunchedEffect(currentCropIndex, currentPage?.originalPath, currentPage?.rotationDegrees) {
        if (currentPage != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val raw = FileUtils.loadBitmap(currentPage.originalPath)
                if (raw != null) {
                    val rotated = ImageProcessor.rotate(raw, currentPage.rotationDegrees)
                    withContext(Dispatchers.Main) {
                        loadedBitmap = rotated
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    // Function to apply ID card aspect ratio (85.6 : 53.98 = ~1.586) centered with spring animation
    fun applyIdCardRatio() {
        val bmp = loadedBitmap ?: return
        val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
        val targetAspect = 1.586f // ID-1 Card Standard

        val normWidth: Float
        val normHeight: Float

        if (imgAspect > targetAspect) {
            normHeight = 0.85f
            normWidth = (normHeight / imgAspect) * targetAspect
        } else {
            normWidth = 0.90f
            normHeight = (normWidth * imgAspect) / targetAspect
        }

        val left = ((1f - normWidth) / 2f).coerceIn(0.02f, 0.45f)
        val right = (left + normWidth).coerceIn(0.55f, 0.98f)
        val top = ((1f - normHeight) / 2f).coerceIn(0.02f, 0.45f)
        val bottom = (top + normHeight).coerceIn(0.55f, 0.98f)

        val idCorners = listOf(
            Offset(left, top),       // TL
            Offset(right, top),      // TR
            Offset(right, bottom),   // BR
            Offset(left, bottom)     // BL
        )
        animateCornersWithSpring(idCorners, "ID Card ratio applied")
    }

    // Auto Crop. Google ML Kit Document Scanner does not expose its detected corner coordinates
    // to third-party apps. When a page came from ML Kit, the returned JPEG is already
    // perspective-corrected/cropped by ML Kit, so the full returned frame IS the ML Kit crop.
    // For non-ML-Kit captures we keep the local edge detector as a safe fallback.
    fun triggerAutoCropWithSpring() {
        val page = currentPage ?: return
        if (page.mlKitProcessed) {
            val mlKitResultCorners = listOf(
                Offset(0f, 0f),
                Offset(1f, 0f),
                Offset(1f, 1f),
                Offset(0f, 1f)
            )
            animateCornersWithSpring(mlKitResultCorners, "ML Kit Auto Crop applied")
            return
        }

        scope.launch(Dispatchers.IO) {
            val bmp = loadedBitmap ?: return@launch
            val detected = EdgeDetector.detectDocumentCorners(bmp)
            // Accuracy pass: sub-pixel refinement is accepted only if it remains close
            // to the detector result. This prevents inner table/text corners from
            // replacing the real outer page boundary.
            val refined = EdgeDetector.refineNormalizedCorners(bmp, detected)
            val maxShift = if (detected.size == 4 && refined.size == 4) {
                (0..3).maxOf { i ->
                    kotlin.math.hypot(
                        (detected[i].x - refined[i].x).toDouble(),
                        (detected[i].y - refined[i].y).toDouble()
                    ).toFloat()
                }
            } else Float.MAX_VALUE
            val best = if (maxShift <= 0.055f) refined else detected
            withContext(Dispatchers.Main) {
                animateCornersWithSpring(best, "Smart border detected & refined")
            }
        }
    }

    // Auto orientation upright detection
    fun triggerAutoOrientation() {
        scope.launch(Dispatchers.IO) {
            val bmp = loadedBitmap ?: return@launch
            val orientationResult = AutoOrientationHelper.detectAndCorrectOrientation(bmp)
            withContext(Dispatchers.Main) {
                if (orientationResult.rotationAppliedDegrees != 0) {
                    when (orientationResult.rotationAppliedDegrees) {
                        90 -> viewModel.rotateCurrentPageRight()
                        270 -> viewModel.rotateCurrentPageLeft()
                        180 -> {
                            viewModel.rotateCurrentPageRight()
                            viewModel.rotateCurrentPageRight()
                        }
                    }
                    Toast.makeText(context, "Rotated ${orientationResult.rotationAppliedDegrees}° to upright position", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Document is already upright", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to snap to edges with sub-pixel corner refiner
    fun triggerSubpixelSnapWithSpring() {
        scope.launch(Dispatchers.IO) {
            val bmp = loadedBitmap ?: return@launch
            val current = listOf(animTL.value, animTR.value, animBR.value, animBL.value)
            val refined = EdgeDetector.refineNormalizedCorners(bmp, current)
            withContext(Dispatchers.Main) {
                animateCornersWithSpring(refined, "Corners magnetically snapped to document edges")
            }
        }
    }

    // Function to apply A4 standard ratio
    fun applyA4Ratio() {
        val bmp = loadedBitmap ?: return
        val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
        val targetAspect = 1f / 1.414f

        val normWidth: Float
        val normHeight: Float

        if (imgAspect > targetAspect) {
            normHeight = 0.90f
            normWidth = (normHeight / imgAspect) * targetAspect
        } else {
            normWidth = 0.90f
            normHeight = (normWidth * imgAspect) / targetAspect
        }

        val left = ((1f - normWidth) / 2f).coerceIn(0.02f, 0.45f)
        val right = (left + normWidth).coerceIn(0.55f, 0.98f)
        val top = ((1f - normHeight) / 2f).coerceIn(0.02f, 0.45f)
        val bottom = (top + normHeight).coerceIn(0.55f, 0.98f)

        val a4Corners = listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(right, bottom),
            Offset(left, bottom)
        )
        animateCornersWithSpring(a4Corners, "A4 Document ratio applied")
    }

    // Generate live perspective warp preview
    fun openWarpPreview() {
        val bmp = loadedBitmap ?: return
        val current = listOf(animTL.value, animTR.value, animBR.value, animBL.value)
        isGeneratingWarp = true
        showWarpPreviewDialog = true
        scope.launch(Dispatchers.IO) {
            val preview = EdgeDetector.generateOpenCvWarpPreview(bmp, current)
            withContext(Dispatchers.Main) {
                warpedPreviewBitmap = preview
                isGeneratingWarp = false
            }
        }
    }

    // Function to nudge selected corner
    fun nudgeSelectedCorner(dx: Float, dy: Float) {
        val current = listOf(animTL.value, animTR.value, animBR.value, animBL.value).toMutableList()
        if (selectedCornerIndex in 0..3) {
            val curr = current[selectedCornerIndex]
            val updated = Offset(
                (curr.x + dx).coerceIn(0f, 1f),
                (curr.y + dy).coerceIn(0f, 1f)
            )
            current[selectedCornerIndex] = updated
            scope.launch {
                when (selectedCornerIndex) {
                    0 -> animTL.snapTo(updated)
                    1 -> animTR.snapTo(updated)
                    2 -> animBR.snapTo(updated)
                    3 -> animBL.snapTo(updated)
                }
                viewModel.updateCurrentPageCrop(current)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeManager.themeColor(Color(0xFF121212), Color(0xFFF8FAFC)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Header Bar
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
                    modifier = Modifier.testTag("crop_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isIdCardMode) "Crop" else "Crop & Adjust",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                )

                Spacer(modifier = Modifier.weight(1f))

                // Toggle Fine-tune D-Pad (normal documents only; NID keeps the clean reference UI)
                if (!isIdCardMode) Surface(
                    color = if (showFineTunePad) Color(0xFF0F766E) else Color(0xFF2D3748),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clickable { showFineTunePad = !showFineTunePad }
                        .padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Fine Tune",
                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Nudge",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                        )
                    }
                }

                if (capturedPages.size > 1 && !isIdCardMode) {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        capturedPages.forEachIndexed { idx, page ->
                            val isSelected = (idx == currentCropIndex)
                            val label = page.label ?: if (isIdCardMode) (if (idx == 0) "Front" else "Back") else "P${idx + 1}"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF00C48C) else Color(0xFF2D3748),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.currentCropPageIndex.value = idx
                                    }
                            ) {
                                Text(
                                    text = if (isIdCardMode) "${idx + 1}. $label" else "Page ${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Preset Sub-bar is hidden for NID so the screen matches the simple reference crop UI.
            if (!isIdCardMode) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ML Kit & OpenCV Subpixel Magnetic Snap
                Surface(
                    color = Color(0xFF1E3A8A).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1A73E8).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { triggerSubpixelSnapWithSpring() }
                        .testTag("btn_crop_snap_opencv")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ML Kit Snap",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // OpenCV & ML Edge Auto Contour Detection
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { triggerAutoCropWithSpring() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF00C48C),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto Edge",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // A4 Document Ratio
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { applyA4Ratio() }
                        .testTag("btn_preset_a4_crop")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "A4 Doc",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // ID Card Standard
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { applyIdCardRatio() }
                        .testTag("btn_preset_id_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ID Card",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Full Frame
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        val fullCorners = listOf(
                            Offset(0f, 0f),
                            Offset(1f, 0f),
                            Offset(1f, 1f),
                            Offset(0f, 1f)
                        )
                        animateCornersWithSpring(fullCorners, "Full frame selected")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Full",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Auto Upright Orientation
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { triggerAutoOrientation() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto Upright",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // OpenCV Live Warp Preview
                Surface(
                    color = Color(0xFF0F766E).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF14B8A6)),
                    modifier = Modifier
                        .clickable { openWarpPreview() }
                        .testTag("btn_crop_warp_preview")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF14B8A6),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Warp Preview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14B8A6)
                        )
                    }
                }
            }

            // 2. Center Image & Interactive Flexible Quad Crop Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF14B8A6),
                        modifier = Modifier.size(48.dp)
                    )
                } else if (loadedBitmap != null && currentPage != null) {
                    val animatedCorners = listOf(
                        animTL.value,
                        animTR.value,
                        animBR.value,
                        animBL.value
                    )

                    QuadCropView(
                        bitmap = loadedBitmap!!,
                        corners = animatedCorners,
                        onCornersChanged = { updatedCorners ->
                            scope.launch {
                                animTL.snapTo(updatedCorners[0])
                                animTR.snapTo(updatedCorners[1])
                                animBR.snapTo(updatedCorners[2])
                                animBL.snapTo(updatedCorners[3])
                            }
                            viewModel.updateCurrentPageCrop(updatedCorners)
                        },
                        selectedCornerIndex = selectedCornerIndex,
                        onCornerSelected = { idx ->
                            selectedCornerIndex = idx
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "No image loaded",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }

                // Page indicator pill (e.g., "1/2" or "2/2")
                if (capturedPages.size > 1) {
                    Surface(
                        color = Color(0xCC2D3748),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (showFineTunePad) 110.dp else 16.dp)
                            .clickable {
                                val nextIdx = (currentCropIndex + 1) % capturedPages.size
                                viewModel.currentCropPageIndex.value = nextIdx
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isIdCardMode) "${currentCropIndex + 1}/${capturedPages.size}" else "Page ${currentCropIndex + 1}/${capturedPages.size} (Tap to switch)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Fine-tune D-Pad floating panel
                if (showFineTunePad && !isIdCardMode) {
                    Surface(
                        color = Color(0xEE1E293B),
                        shape = RoundedCornerShape(16.dp),
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
                                    0 -> "Corner: Top-Left"
                                    1 -> "Corner: Top-Right"
                                    2 -> "Corner: Bottom-Right"
                                    else -> "Corner: Bottom-Left"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14B8A6)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            IconButton(
                                onClick = { nudgeSelectedCorner(0f, -0.0025f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = Color.White)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { nudgeSelectedCorner(-0.0025f, 0f) },
                                    modifier = Modifier.size(32.dp)
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
                                        text = "${selectedCornerIndex + 1}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { nudgeSelectedCorner(0.0025f, 0f) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right", tint = Color.White)
                                }
                            }
                            IconButton(
                                onClick = { nudgeSelectedCorner(0f, 0.0025f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // 3. Bottom Action Bar (Retake, Left, Right, Auto Crop, All, Confirm)
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Retake
                    CropActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Retake",
                        testTag = "btn_crop_retake",
                        onClick = {
                            onRetake(currentCropIndex)
                        }
                    )

                    // Left (Rotate 90° CCW)
                    CropActionButton(
                        icon = Icons.Default.RotateLeft,
                        label = "Left",
                        testTag = "btn_crop_rotate_left",
                        onClick = {
                            viewModel.rotateCurrentPageLeft()
                        }
                    )

                    // Right (Rotate 90° CW)
                    CropActionButton(
                        icon = Icons.Default.RotateRight,
                        label = "Right",
                        testTag = "btn_crop_rotate_right",
                        onClick = {
                            viewModel.rotateCurrentPageRight()
                        }
                    )

                    // Auto Crop
                    CropActionButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "Auto Crop",
                        testTag = "btn_crop_auto",
                        onClick = {
                            triggerAutoCropWithSpring()
                        }
                    )

                    // All
                    CropActionButton(
                        icon = Icons.Default.CropFree,
                        label = "All",
                        testTag = "btn_crop_all",
                        onClick = {
                            val fullCorners = listOf(
                                Offset(0f, 0f),
                                Offset(1f, 0f),
                                Offset(1f, 1f),
                                Offset(0f, 1f)
                            )
                            animateCornersWithSpring(fullCorners, "Full frame selected")
                        }
                    )

                    // Confirm Button (Green/Teal checkmark)
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF00C48C))
                            .clickable {
                                if (isIdCardMode) {
                                    // A card was opened from the two-side NID preview. Confirm only
                                    // this side and return directly to that preview, matching the
                                    // reference flow instead of forcing the user onto the other side.
                                    onConfirmCrop()
                                } else if (currentCropIndex < capturedPages.size - 1) {
                                    viewModel.currentCropPageIndex.value = currentCropIndex + 1
                                    Toast.makeText(context, "Page ${currentCropIndex + 1} saved! Adjust next page (${currentCropIndex + 2}/${capturedPages.size}).", Toast.LENGTH_SHORT).show()
                                } else {
                                    onConfirmCrop()
                                }
                            }
                            .testTag("btn_crop_confirm"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm Crop",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // OpenCV Live Warp Preview Dialog
        if (showWarpPreviewDialog) {
            AlertDialog(
                onDismissRequest = { showWarpPreviewDialog = false },
                containerColor = Color(0xFF1E1E24),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF14B8A6),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OpenCV Rectified Preview",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Live preview of perspective correction using OpenCV bicubic homography",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isGeneratingWarp) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF14B8A6))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Computing OpenCV Warp...", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        } else if (warpedPreviewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = warpedPreviewBitmap!!.asImageBitmap(),
                                    contentDescription = "OpenCV Warped Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                Surface(
                                    color = Color(0xCC0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "${warpedPreviewBitmap!!.width} × ${warpedPreviewBitmap!!.height} px",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else {
                            Text("Preview calculation failed", color = Color.Gray)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showWarpPreviewDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6))
                    ) {
                        Text("Looks Good", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWarpPreviewDialog = false }) {
                        Text("Back to Adjust", color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}

@Composable
private fun CropActionButton(
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
            .padding(horizontal = 6.dp, vertical = 6.dp)
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
