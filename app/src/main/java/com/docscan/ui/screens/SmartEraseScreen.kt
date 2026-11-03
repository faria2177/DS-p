package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.docscan.ui.viewmodel.SmartEraseToolMode
import com.docscan.ui.viewmodel.SmartEraseViewModel
import com.docscan.util.smarterase.ImageCoordinateMapper
import kotlinx.coroutines.launch

private val EraseTeal = Color(0xFF2DBA8D)
private val EraseCanvasBg: Color get() = ThemeManager.themeColor(Color(0xFF14171A), Color(0xFFF8FAFC))
private val EraseToolbarBg: Color get() = ThemeManager.themeColor(Color(0xFF1E2228), Color(0xFFFFFFFF))
private val ErasePanelBg: Color get() = ThemeManager.themeColor(Color(0xFF181B20), Color(0xFFF0F4F8))
private val EraseControlBg: Color get() = ThemeManager.themeColor(Color(0xFF252930), Color(0xFFE2E8F0))
private val EraseInk: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF142033))
private val EraseMutedInk: Color get() = ThemeManager.themeColor(Color(0xFFB9C2CF), Color(0xFF526174))
private val EraseInactiveBg: Color get() = ThemeManager.themeColor(Color(0xFF2A2E35), Color(0xFFDCE5EE))
private val EraseMaskOverlayColor = Color(0x66FF5252) // Semi-transparent red/coral mask
private val EraseCandidateOverlayColor = Color(0x662DBA8D) // Semi-transparent teal candidate

@Composable
fun SmartEraseScreen(
    sourceBitmap: Bitmap,
    onNavigateBack: () -> Unit,
    onApplyResult: (Bitmap) -> Unit,
    viewModel: SmartEraseViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sourceBitmap) {
        viewModel.initialize(sourceBitmap)
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    // Zoom and Pan states
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Live gesture points
    val liveBrushStroke = remember { mutableStateListOf<Offset>() }
    val liveLassoPoints = remember { mutableStateListOf<Offset>() }
    var liveTouchPos by remember { mutableStateOf<Offset?>(null) }
    var liveRectStart by remember { mutableStateOf<Offset?>(null) }
    var liveRectEnd by remember { mutableStateOf<Offset?>(null) }

    val density = LocalDensity.current.density

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EraseCanvasBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar
            SmartEraseTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                isComparing = uiState.isComparing,
                isCompareSliderActive = uiState.isCompareSliderActive,
                onCancel = onNavigateBack,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onReset = {
                    viewModel.reset()
                    zoomScale = 1.0f
                    panOffset = Offset.Zero
                },
                onCompareToggle = { viewModel.setComparing(it) },
                onCompareSliderToggle = { viewModel.toggleCompareSlider() },
                onApply = {
                    scope.launch {
                        val finalBmp = viewModel.applyAndGetFinalBitmap()
                        onApplyResult(finalBmp)
                    }
                }
            )

            // 2. Interactive Document Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .background(Color(0xFF0F1114)),
                contentAlignment = Alignment.Center
            ) {
                val workingBitmap = uiState.workingBitmap
                val originalBitmap = uiState.originalBitmap
                val activeMask = uiState.activeMaskBitmap
                val candidateMask = uiState.candidateMaskBitmap

                if (workingBitmap != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val containerSize = Size(maxWidth.value * density, maxHeight.value * density)
                        val fitFrame = remember(containerSize, workingBitmap.width, workingBitmap.height) {
                            ImageCoordinateMapper.calculateFitFrame(
                                containerSize = containerSize,
                                bitmapWidth = workingBitmap.width,
                                bitmapHeight = workingBitmap.height
                            )
                        }

                        // Canvas for gestures and drawing
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(uiState.isPanZoomMode) {
                                    if (uiState.isPanZoomMode) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            zoomScale = (zoomScale * zoom).coerceIn(1.0f, 15.0f)
                                            if (zoomScale == 1.0f) {
                                                panOffset = Offset.Zero
                                            } else {
                                                panOffset += pan
                                            }
                                        }
                                    }
                                }
                                .pointerInput(uiState.isPanZoomMode) {
                                    if (uiState.isPanZoomMode) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (zoomScale > 1.2f) {
                                                    zoomScale = 1.0f
                                                    panOffset = Offset.Zero
                                                } else {
                                                    zoomScale = 3.5f
                                                }
                                            }
                                        )
                                    }
                                }
                                .pointerInput(uiState.currentToolMode, uiState.isPanZoomMode, zoomScale, panOffset) {
                                    if (!uiState.isPanZoomMode) {
                                        when (uiState.currentToolMode) {
                                            SmartEraseToolMode.SMART_BRUSH -> {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        liveTouchPos = offset
                                                        liveBrushStroke.clear()
                                                        val norm = ImageCoordinateMapper.screenToNormalized(
                                                            offset, fitFrame, zoomScale, panOffset
                                                        )
                                                        liveBrushStroke.add(norm)
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        liveTouchPos = change.position
                                                        val norm = ImageCoordinateMapper.screenToNormalized(
                                                            change.position, fitFrame, zoomScale, panOffset
                                                        )
                                                        liveBrushStroke.add(norm)
                                                    },
                                                    onDragEnd = {
                                                        if (liveBrushStroke.isNotEmpty()) {
                                                            val radiusNorm = (uiState.brushSizeDp * density / fitFrame.width) / 2f
                                                            viewModel.applyBrushStroke(
                                                                pointsNorm = liveBrushStroke.toList(),
                                                                radiusNorm = radiusNorm,
                                                                isSubtract = uiState.isSubtractMode
                                                            )
                                                        }
                                                        liveBrushStroke.clear()
                                                        liveTouchPos = null
                                                    },
                                                    onDragCancel = {
                                                        liveBrushStroke.clear()
                                                        liveTouchPos = null
                                                    }
                                                )
                                            }
                                            SmartEraseToolMode.RECT_ERASE -> {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        liveTouchPos = offset
                                                        liveRectStart = offset
                                                        liveRectEnd = offset
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        liveTouchPos = change.position
                                                        liveRectEnd = change.position
                                                    },
                                                    onDragEnd = {
                                                        val start = liveRectStart
                                                        val end = liveRectEnd
                                                        if (start != null && end != null) {
                                                            val normStart = ImageCoordinateMapper.screenToNormalized(
                                                                start, fitFrame, zoomScale, panOffset
                                                            )
                                                            val normEnd = ImageCoordinateMapper.screenToNormalized(
                                                                end, fitFrame, zoomScale, panOffset
                                                            )
                                                            viewModel.applyRectangle(
                                                                leftNorm = normStart.x,
                                                                topNorm = normStart.y,
                                                                rightNorm = normEnd.x,
                                                                bottomNorm = normEnd.y,
                                                                isSubtract = uiState.isSubtractMode
                                                            )
                                                        }
                                                        liveRectStart = null
                                                        liveRectEnd = null
                                                        liveTouchPos = null
                                                    },
                                                    onDragCancel = {
                                                        liveRectStart = null
                                                        liveRectEnd = null
                                                        liveTouchPos = null
                                                    }
                                                )
                                            }
                                            SmartEraseToolMode.SMART_SELECT -> {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val norm = ImageCoordinateMapper.screenToNormalized(
                                                            offset, fitFrame, zoomScale, panOffset
                                                        )
                                                        viewModel.applySmartSelect(norm.x, norm.y)
                                                    }
                                                )
                                            }
                                            SmartEraseToolMode.LASSO -> {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        liveTouchPos = offset
                                                        liveLassoPoints.clear()
                                                        val norm = ImageCoordinateMapper.screenToNormalized(
                                                            offset, fitFrame, zoomScale, panOffset
                                                        )
                                                        liveLassoPoints.add(norm)
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        liveTouchPos = change.position
                                                        val norm = ImageCoordinateMapper.screenToNormalized(
                                                            change.position, fitFrame, zoomScale, panOffset
                                                        )
                                                        liveLassoPoints.add(norm)
                                                    },
                                                    onDragEnd = {
                                                        if (liveLassoPoints.size >= 3) {
                                                            viewModel.applyLasso(
                                                                pointsNorm = liveLassoPoints.toList(),
                                                                isSubtract = uiState.isSubtractMode
                                                            )
                                                        }
                                                        liveLassoPoints.clear()
                                                        liveTouchPos = null
                                                    },
                                                    onDragCancel = {
                                                        liveLassoPoints.clear()
                                                        liveTouchPos = null
                                                    }
                                                )
                                            }
                                            SmartEraseToolMode.AUTO_DETECT -> {
                                                // Tap to dismiss or refine
                                            }
                                        }
                                    }
                                }
                        ) {
                            // Calculate transformed drawing frame
                            val centerX = fitFrame.left + fitFrame.width / 2f
                            val centerY = fitFrame.top + fitFrame.height / 2f
                            val drawLeft = centerX - (fitFrame.width / 2f) * zoomScale + panOffset.x
                            val drawTop = centerY - (fitFrame.height / 2f) * zoomScale + panOffset.y
                            val drawWidth = fitFrame.width * zoomScale
                            val drawHeight = fitFrame.height * zoomScale

                            val targetDstOffset = IntOffset(drawLeft.toInt(), drawTop.toInt())
                            val targetDstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())

                            // 1. Draw Document Image (or Original if comparing)
                            val displayBmp = if (uiState.isComparing && originalBitmap != null) {
                                originalBitmap
                            } else {
                                workingBitmap
                            }

                            drawImage(
                                image = displayBmp.asImageBitmap(),
                                dstOffset = targetDstOffset,
                                dstSize = targetDstSize
                            )

                            // 2. Split Compare Slider rendering
                            if (uiState.isCompareSliderActive && originalBitmap != null) {
                                val splitX = drawLeft + drawWidth * uiState.compareSliderPos
                                // Clip and draw original bitmap on left portion
                                val origPortionWidth = (originalBitmap.width * uiState.compareSliderPos).toInt()
                                if (origPortionWidth > 0) {
                                    drawImage(
                                        image = originalBitmap.asImageBitmap(),
                                        srcOffset = IntOffset(0, 0),
                                        srcSize = IntSize(origPortionWidth, originalBitmap.height),
                                        dstOffset = targetDstOffset,
                                        dstSize = IntSize((drawWidth * uiState.compareSliderPos).toInt(), drawHeight.toInt())
                                    )
                                }

                                // Draw vertical divider line
                                drawLine(
                                    color = EraseInk,
                                    start = Offset(splitX, drawTop),
                                    end = Offset(splitX, drawTop + drawHeight),
                                    strokeWidth = 3.dp.toPx()
                                )
                                drawCircle(
                                    color = EraseTeal,
                                    radius = 12.dp.toPx(),
                                    center = Offset(splitX, drawTop + drawHeight / 2f)
                                )
                                drawCircle(
                                    color = EraseInk,
                                    radius = 4.dp.toPx(),
                                    center = Offset(splitX, drawTop + drawHeight / 2f)
                                )
                            }

                            // 3. Draw Active Erase Mask Overlay
                            if (activeMask != null && !uiState.isComparing) {
                                drawImage(
                                    image = activeMask.asImageBitmap(),
                                    dstOffset = targetDstOffset,
                                    dstSize = targetDstSize,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        EraseMaskOverlayColor,
                                        androidx.compose.ui.graphics.BlendMode.SrcAtop
                                    )
                                )
                            }

                            // 4. Draw Candidate Handwriting Overlay
                            if (candidateMask != null && !uiState.isComparing) {
                                drawImage(
                                    image = candidateMask.asImageBitmap(),
                                    dstOffset = targetDstOffset,
                                    dstSize = targetDstSize,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        EraseCandidateOverlayColor,
                                        androidx.compose.ui.graphics.BlendMode.SrcAtop
                                    )
                                )
                            }

                            // 5. Draw Live Brush Stroke
                            if (liveBrushStroke.size >= 2) {
                                val strokePath = Path()
                                val first = ImageCoordinateMapper.normalizedToScreen(
                                    liveBrushStroke[0], fitFrame, zoomScale, panOffset
                                )
                                strokePath.moveTo(first.x, first.y)
                                for (i in 1 until liveBrushStroke.size) {
                                    val pt = ImageCoordinateMapper.normalizedToScreen(
                                        liveBrushStroke[i], fitFrame, zoomScale, panOffset
                                    )
                                    strokePath.lineTo(pt.x, pt.y)
                                }

                                val brushRadiusPx = (uiState.brushSizeDp * density * zoomScale)
                                drawPath(
                                    path = strokePath,
                                    color = EraseMaskOverlayColor,
                                    style = Stroke(
                                        width = brushRadiusPx,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }

                            // 6. Draw Live Lasso Loop
                            if (liveLassoPoints.size >= 2) {
                                val lassoPath = Path()
                                val first = ImageCoordinateMapper.normalizedToScreen(
                                    liveLassoPoints[0], fitFrame, zoomScale, panOffset
                                )
                                lassoPath.moveTo(first.x, first.y)
                                for (i in 1 until liveLassoPoints.size) {
                                    val pt = ImageCoordinateMapper.normalizedToScreen(
                                        liveLassoPoints[i], fitFrame, zoomScale, panOffset
                                    )
                                    lassoPath.lineTo(pt.x, pt.y)
                                }

                                drawPath(
                                    path = lassoPath,
                                    color = EraseTeal,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                )
                            }

                            // 7. Draw Live Box Erase Rectangle
                            if (liveRectStart != null && liveRectEnd != null && uiState.currentToolMode == SmartEraseToolMode.RECT_ERASE) {
                                val s = liveRectStart!!
                                val e = liveRectEnd!!
                                val l = kotlin.math.min(s.x, e.x)
                                val t = kotlin.math.min(s.y, e.y)
                                val w = kotlin.math.abs(s.x - e.x)
                                val h = kotlin.math.abs(s.y - e.y)

                                drawRect(
                                    color = if (uiState.isSubtractMode) Color(0x44FF9800) else Color(0x44FF5252),
                                    topLeft = Offset(l, t),
                                    size = androidx.compose.ui.geometry.Size(w, h)
                                )
                                drawRect(
                                    color = if (uiState.isSubtractMode) Color(0xFFFF9800) else Color(0xFFFF5252),
                                    topLeft = Offset(l, t),
                                    size = androidx.compose.ui.geometry.Size(w, h),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                    )
                                )
                            }

                            // 8. Draw Live Brush Cursor Ring
                            if (liveTouchPos != null && uiState.currentToolMode == SmartEraseToolMode.SMART_BRUSH) {
                                val pos = liveTouchPos!!
                                val brushRadiusPx = (uiState.brushSizeDp * density * zoomScale) / 2f

                                drawCircle(
                                    color = if (uiState.isSubtractMode) Color(0x66FF9800) else Color(0x66FF5252),
                                    radius = brushRadiusPx,
                                    center = pos
                                )
                                drawCircle(
                                    color = EraseInk,
                                    radius = brushRadiusPx,
                                    center = pos,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = Color.Black,
                                    radius = 2.dp.toPx(),
                                    center = pos
                                )
                            }
                        }
                    }
                }

                // Precision Loupe / Magnifier Widget on active stroke/drag
                if (liveTouchPos != null && workingBitmap != null && !uiState.isPanZoomMode) {
                    val touchPos = liveTouchPos!!
                    val loupeSizeDp = 100.dp
                    val loupeRadiusPx = (loupeSizeDp.value * density) / 2f
                    // Position loupe offset to top-left or top-right of finger so finger doesn't obscure it
                    val isLeftHalf = touchPos.x < 300 * density
                    val loupeOffset = if (isLeftHalf) {
                        IntOffset((touchPos.x + 30 * density).toInt(), (touchPos.y - 120 * density).coerceAtLeast(10f).toInt())
                    } else {
                        IntOffset((touchPos.x - 130 * density).coerceAtLeast(10f).toInt(), (touchPos.y - 120 * density).coerceAtLeast(10f).toInt())
                    }

                    Box(
                        modifier = Modifier
                            .offset { loupeOffset }
                            .size(loupeSizeDp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .border(2.5.dp, EraseTeal, CircleShape)
                            .background(Color.Black)
                    ) {
                        // Magnified canvas view centering on touch position
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val magZoom = 2.2f
                            // Center magnified image
                            drawImage(
                                image = workingBitmap.asImageBitmap(),
                                dstOffset = IntOffset(
                                    (loupeRadiusPx - (touchPos.x) * magZoom).toInt(),
                                    (loupeRadiusPx - (touchPos.y) * magZoom).toInt()
                                ),
                                dstSize = IntSize(
                                    (workingBitmap.width * magZoom * (zoomScale)).toInt().coerceAtLeast(1),
                                    (workingBitmap.height * magZoom * (zoomScale)).toInt().coerceAtLeast(1)
                                )
                            )
                            // Crosshair in loupe center
                            drawLine(
                                color = EraseTeal,
                                start = Offset(loupeRadiusPx - 10 * density, loupeRadiusPx),
                                end = Offset(loupeRadiusPx + 10 * density, loupeRadiusPx),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = EraseTeal,
                                start = Offset(loupeRadiusPx, loupeRadiusPx - 10 * density),
                                end = Offset(loupeRadiusPx, loupeRadiusPx + 10 * density),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                // Comparing Badge indicator
                if (uiState.isComparing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ORIGINAL DOCUMENT",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Pan/Zoom Mode floating hint
                if (uiState.isPanZoomMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(EraseTeal.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Pan & Zoom Mode (2 fingers to zoom/pan, double tap to reset)",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Processing Indicator
                if (uiState.isProcessing) {
                    Surface(
                        color = Color(0xEE1E2228),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = EraseTeal,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = uiState.processingMessage,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 3. Dynamic Tool Panels & Bottom Bar
            SmartEraseBottomControls(
                uiState = uiState,
                onToolModeChange = { viewModel.setToolMode(it) },
                onBrushSizeChange = { viewModel.setBrushSize(it) },
                onSubtractModeToggle = { viewModel.setSubtractMode(!uiState.isSubtractMode) },
                onToleranceChange = { viewModel.setSmartSelectTolerance(it) },
                onTogglePanZoom = { viewModel.togglePanZoomMode() },
                onAutoDetectHandwriting = { viewModel.autoDetectHandwriting() },
                onAcceptCandidate = {
                    viewModel.acceptCandidateMask()
                    viewModel.executeInpaint()
                },
                onClearCandidate = { viewModel.clearCandidateMask() },
                onClearMask = { viewModel.clearActiveMask() },
                onExecuteInpaint = { viewModel.executeInpaint() }
            )
        }
    }
}

@Composable
private fun SmartEraseTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    isComparing: Boolean,
    isCompareSliderActive: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onCompareToggle: (Boolean) -> Unit,
    onCompareSliderToggle: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(EraseToolbarBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.size(40.dp).testTag("btn_smart_erase_cancel")) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = EraseInk)
            }
            Text(
                "AI Smart Erase", color = EraseInk, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = EraseTeal, contentColor = Color(0xFF10271F)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_smart_erase_apply")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(38.dp).testTag("btn_smart_erase_undo")) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) EraseInk else EraseMutedInk.copy(alpha = 0.5f))
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(38.dp).testTag("btn_smart_erase_redo")) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) EraseInk else EraseMutedInk.copy(alpha = 0.5f))
            }
            IconButton(onClick = onReset, modifier = Modifier.size(38.dp).testTag("btn_smart_erase_reset")) {
                Icon(Icons.Default.RestartAlt, "Reset", tint = EraseMutedInk)
            }
            IconButton(onClick = onCompareSliderToggle, modifier = Modifier.size(38.dp).testTag("btn_smart_erase_compare_slider")) {
                Icon(Icons.Default.Splitscreen, "Compare Slider", tint = if (isCompareSliderActive) EraseTeal else EraseMutedInk)
            }
        }
    }
}

@Composable
private fun SmartEraseBottomControls(
    uiState: com.docscan.ui.viewmodel.SmartEraseUiState,
    onToolModeChange: (SmartEraseToolMode) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onSubtractModeToggle: () -> Unit,
    onToleranceChange: (Float) -> Unit,
    onTogglePanZoom: () -> Unit,
    onAutoDetectHandwriting: () -> Unit,
    onAcceptCandidate: () -> Unit,
    onClearCandidate: () -> Unit,
    onClearMask: () -> Unit,
    onExecuteInpaint: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EraseToolbarBg)
    ) {
        // 1. Tool-Specific Sub-Panel
        when (uiState.currentToolMode) {
            SmartEraseToolMode.SMART_BRUSH -> {
                BrushSubPanel(
                    brushSizeDp = uiState.brushSizeDp,
                    isSubtractMode = uiState.isSubtractMode,
                    hasUnappliedMask = uiState.hasUnappliedMask,
                    onBrushSizeChange = onBrushSizeChange,
                    onSubtractToggle = onSubtractModeToggle,
                    onClearMask = onClearMask,
                    onInpaint = onExecuteInpaint
                )
            }
            SmartEraseToolMode.SMART_SELECT -> {
                SmartSelectSubPanel(
                    tolerance = uiState.smartSelectTolerance,
                    isSubtractMode = uiState.isSubtractMode,
                    hasUnappliedMask = uiState.hasUnappliedMask,
                    onToleranceChange = onToleranceChange,
                    onSubtractToggle = onSubtractModeToggle,
                    onClearMask = onClearMask,
                    onInpaint = onExecuteInpaint
                )
            }
            SmartEraseToolMode.RECT_ERASE -> {
                RectSubPanel(
                    isSubtractMode = uiState.isSubtractMode,
                    hasUnappliedMask = uiState.hasUnappliedMask,
                    onSubtractToggle = onSubtractModeToggle,
                    onClearMask = onClearMask,
                    onInpaint = onExecuteInpaint
                )
            }
            SmartEraseToolMode.LASSO -> {
                LassoSubPanel(
                    hasUnappliedMask = uiState.hasUnappliedMask,
                    onClearMask = onClearMask,
                    onInpaint = onExecuteInpaint
                )
            }
            SmartEraseToolMode.AUTO_DETECT -> {
                AutoDetectSubPanel(
                    hasCandidate = uiState.candidateMaskBitmap != null,
                    onDetect = onAutoDetectHandwriting,
                    onAccept = onAcceptCandidate,
                    onClear = onClearCandidate
                )
            }
        }

        // 2. Primary Mode Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolModeTab(
                label = "Smart Brush",
                icon = Icons.Default.Brush,
                isSelected = uiState.currentToolMode == SmartEraseToolMode.SMART_BRUSH,
                onClick = { onToolModeChange(SmartEraseToolMode.SMART_BRUSH) }
            )

            ToolModeTab(
                label = "Smart Select",
                icon = Icons.Default.AutoFixHigh,
                isSelected = uiState.currentToolMode == SmartEraseToolMode.SMART_SELECT,
                onClick = { onToolModeChange(SmartEraseToolMode.SMART_SELECT) }
            )

            ToolModeTab(
                label = "Box / Rect",
                icon = Icons.Default.CropSquare,
                isSelected = uiState.currentToolMode == SmartEraseToolMode.RECT_ERASE,
                onClick = { onToolModeChange(SmartEraseToolMode.RECT_ERASE) }
            )

            ToolModeTab(
                label = "Lasso",
                icon = Icons.Default.Gesture,
                isSelected = uiState.currentToolMode == SmartEraseToolMode.LASSO,
                onClick = { onToolModeChange(SmartEraseToolMode.LASSO) }
            )

            ToolModeTab(
                label = "Auto Detect",
                icon = Icons.Default.Psychology,
                isSelected = uiState.currentToolMode == SmartEraseToolMode.AUTO_DETECT,
                onClick = { onToolModeChange(SmartEraseToolMode.AUTO_DETECT) }
            )

            // Pan & Zoom Toggle
            ToolModeTab(
                label = "Pan / Zoom",
                icon = Icons.Default.PanTool,
                isSelected = uiState.isPanZoomMode,
                onClick = onTogglePanZoom
            )
        }
    }
}

@Composable
private fun ToolModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isSelected) EraseTeal else EraseInactiveBg,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF14171A) else EraseInk,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) EraseTeal else EraseMutedInk
        )
    }
}

@Composable
private fun BrushSubPanel(
    brushSizeDp: Float,
    isSubtractMode: Boolean,
    hasUnappliedMask: Boolean,
    onBrushSizeChange: (Float) -> Unit,
    onSubtractToggle: () -> Unit,
    onClearMask: () -> Unit,
    onInpaint: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErasePanelBg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Size Presets & Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size: ${brushSizeDp.toInt()}dp",
                color = EraseInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(68.dp)
            )

            Slider(
                value = brushSizeDp,
                onValueChange = onBrushSizeChange,
                valueRange = 8f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = EraseTeal,
                    activeTrackColor = EraseTeal,
                    inactiveTrackColor = Color(0xFF333842)
                ),
                modifier = Modifier.weight(1f)
            )

            // Preset chips: S, M, L, XL
            val presets = listOf(12f to "S", 24f to "M", 40f to "L", 64f to "XL")
            presets.forEach { (size, label) ->
                val isSel = (brushSizeDp - size).let { it >= -3f && it <= 3f }
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(26.dp)
                        .background(
                            if (isSel) EraseTeal else EraseInactiveBg,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onBrushSizeChange(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.Black else EraseInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Add vs Subtract Toggle
            Row(
                modifier = Modifier
                    .background(EraseControlBg, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isSubtractMode) EraseTeal else Color.Transparent)
                        .clickable { if (isSubtractMode) onSubtractToggle() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = if (!isSubtractMode) Color.Black else EraseInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Paint Mask",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isSubtractMode) Color.Black else EraseInk
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSubtractMode) Color(0xFFFF5252) else Color.Transparent)
                        .clickable { if (!isSubtractMode) onSubtractToggle() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Erase",
                            tint = EraseInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Eraser",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EraseInk
                        )
                    }
                }
            }

            // Inpaint Button
            Button(
                onClick = onInpaint,
                enabled = hasUnappliedMask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EraseTeal,
                    contentColor = Color.Black,
                    disabledContainerColor = EraseInactiveBg,
                    disabledContentColor = EraseMutedInk
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Erase Now",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SmartSelectSubPanel(
    tolerance: Float,
    isSubtractMode: Boolean,
    hasUnappliedMask: Boolean,
    onToleranceChange: (Float) -> Unit,
    onSubtractToggle: () -> Unit,
    onClearMask: () -> Unit,
    onInpaint: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErasePanelBg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reach: ${tolerance.toInt()}",
                color = EraseInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(68.dp)
            )

            Slider(
                value = tolerance,
                onValueChange = onToleranceChange,
                valueRange = 15f..75f,
                colors = SliderDefaults.colors(
                    thumbColor = EraseTeal,
                    activeTrackColor = EraseTeal,
                    inactiveTrackColor = Color(0xFF333842)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tap any signature, stamp, or mark to auto-select",
                color = EraseMutedInk,
                fontSize = 12.sp
            )

            Button(
                onClick = onInpaint,
                enabled = hasUnappliedMask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EraseTeal,
                    contentColor = Color.Black,
                    disabledContainerColor = EraseInactiveBg,
                    disabledContentColor = EraseMutedInk
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Erase Selection",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RectSubPanel(
    isSubtractMode: Boolean,
    hasUnappliedMask: Boolean,
    onSubtractToggle: () -> Unit,
    onClearMask: () -> Unit,
    onInpaint: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErasePanelBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Add vs Erase Mode toggle
        Row(
            modifier = Modifier
                .background(EraseControlBg, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!isSubtractMode) EraseTeal else Color.Transparent)
                    .clickable { if (isSubtractMode) onSubtractToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Box",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isSubtractMode) Color.Black else EraseInk
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSubtractMode) Color(0xFFFF5252) else Color.Transparent)
                    .clickable { if (!isSubtractMode) onSubtractToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Subtract",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EraseInk
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Drag diagonally across any area",
            color = EraseMutedInk,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = onInpaint,
            enabled = hasUnappliedMask,
            colors = ButtonDefaults.buttonColors(
                containerColor = EraseTeal,
                contentColor = Color.Black,
                disabledContainerColor = EraseInactiveBg,
                disabledContentColor = EraseMutedInk
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Erase Box",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LassoSubPanel(
    hasUnappliedMask: Boolean,
    onClearMask: () -> Unit,
    onInpaint: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErasePanelBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Draw a loop around unwanted text or stains",
            color = EraseMutedInk,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = onInpaint,
            enabled = hasUnappliedMask,
            colors = ButtonDefaults.buttonColors(
                containerColor = EraseTeal,
                contentColor = Color.Black,
                disabledContainerColor = EraseInactiveBg,
                disabledContentColor = EraseMutedInk
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Erase Lasso Area",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AutoDetectSubPanel(
    hasCandidate: Boolean,
    onDetect: () -> Unit,
    onAccept: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErasePanelBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!hasCandidate) {
            Text(
                text = "AI detects pen strokes, handwriting & pencil notes",
                color = EraseMutedInk,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onDetect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EraseTeal,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Detect Handwriting",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "Handwriting candidate detected! Confirm to erase:",
                color = EraseTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onClear,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF5252))
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
            }

            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EraseTeal,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Erase Handwriting",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
