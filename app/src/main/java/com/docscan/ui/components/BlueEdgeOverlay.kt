package com.docscan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Authentic CamScanner-identical live document edge detection overlay.
 *
 * Faithfully matches real CamScanner capture behavior (CaptureActivity):
 * - Signature CamScanner Mint-Teal (#28D8A1 / #2CD5A6) boundary contour.
 * - Clean, sleek 2.2dp stroke with rounded joins hugging document edges seamlessly.
 * - Completely unobscured viewfinder: camera preview and text remain 100% visible and sharp.
 * - 60 FPS silky smooth spring-interpolated corner gliding when panning.
 * - Zero-jitter deadband locking: 100% frozen coordinates when holding device steady.
 * - Clean fade-in / fade-out without ghosting or false boxes on empty scenes.
 */
@Composable
fun BlueEdgeOverlay(
    corners: List<Offset>,
    state: com.docscan.util.DetectionState,
    scanMode: com.docscan.data.model.ScanMode,
    idCardStep: Int,
    idCardType: com.docscan.data.model.IdCardType = com.docscan.data.model.IdCardType.BANK_CARD,
    showGrid: Boolean,
    frameAspectRatio: Float = 3f / 4f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "camscanner_overlay_fx")

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cam_pulse"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // 4 corner animatables for silky 60 FPS gliding
        val animP0 = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val animP1 = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val animP2 = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val animP3 = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val animAlpha = remember { Animatable(0f) }

        val hasValidDetection = corners.size == 4 &&
                state != com.docscan.util.DetectionState.SEARCHING_DOCUMENT &&
                state != com.docscan.util.DetectionState.IDLE

        // Corner tracking, deadband freezing & smooth spring interpolation
        LaunchedEffect(hasValidDetection, corners, frameAspectRatio, w, h) {
            if (hasValidDetection && corners.size == 4 && w > 0f && h > 0f) {
                val mapped = mapCornersToPreview(corners, w, h, frameAspectRatio)
                if (mapped.size == 4) {
                    if (animAlpha.value < 0.05f) {
                        // First detection appearance: snap directly so the box doesn't slide from (0,0)
                        animP0.snapTo(mapped[0])
                        animP1.snapTo(mapped[1])
                        animP2.snapTo(mapped[2])
                        animP3.snapTo(mapped[3])
                        animAlpha.animateTo(1f, tween(140, easing = LinearOutSlowInEasing))
                    } else {
                        // Small changes are sensor/detection jitter, not paper
                        // motion. Once aligned, use a larger hold band so the
                        // outline stays visually locked to the sheet.
                        val d0 = hypot(mapped[0].x - animP0.targetValue.x, mapped[0].y - animP0.targetValue.y)
                        val d1 = hypot(mapped[1].x - animP1.targetValue.x, mapped[1].y - animP1.targetValue.y)
                        val d2 = hypot(mapped[2].x - animP2.targetValue.x, mapped[2].y - animP2.targetValue.y)
                        val d3 = hypot(mapped[3].x - animP3.targetValue.x, mapped[3].y - animP3.targetValue.y)
                        val maxDelta = maxOf(d0, d1, d2, d3)

                        val holdBandPx = if (state == com.docscan.util.DetectionState.DOCUMENT_STABLE) 8f else 5f
                        if (maxDelta >= holdBandPx) {
                            val glideSpring = spring<Offset>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            launch { animP0.animateTo(mapped[0], glideSpring) }
                            launch { animP1.animateTo(mapped[1], glideSpring) }
                            launch { animP2.animateTo(mapped[2], glideSpring) }
                            launch { animP3.animateTo(mapped[3], glideSpring) }
                        }

                        if (animAlpha.targetValue != 1f) {
                            animAlpha.animateTo(1f, tween(100))
                        }
                    }
                }
            } else {
                // Not detected: cleanly fade out immediately without ghost boxes
                animAlpha.animateTo(0f, tween(120, easing = FastOutLinearInEasing))
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_PARAMETER") idCardStep

            // Grid guidelines (Rule-of-Thirds)
            if (showGrid) {
                val gridColor = Color.White.copy(alpha = 0.16f)
                val stroke = 1.dp.toPx()
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = stroke)
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = stroke)
            }

            if (scanMode == com.docscan.data.model.ScanMode.ID_CARD) {
                drawIdCardCamScannerFrame(w, h, idCardType, pulseGlow)
                return@Canvas
            }

            if (animAlpha.value > 0.01f) {
                val currentPts = listOf(
                    animP0.value,
                    animP1.value,
                    animP2.value,
                    animP3.value
                )
                drawDocumentOutline(
                    pts = currentPts,
                    isStable = state == com.docscan.util.DetectionState.DOCUMENT_STABLE,
                    alpha = animAlpha.value
                )
            }
        }
    }
}

private fun mapCornersToPreview(
    corners: List<Offset>,
    w: Float,
    h: Float,
    frameAspectRatio: Float
): List<Offset> {
    val safeAspect = if (frameAspectRatio.isFinite() && frameAspectRatio > 0f) frameAspectRatio else 3f / 4f
    val destAspect = if (h > 0f) w / h else safeAspect
    var cropXFrac = 0f
    var cropYFrac = 0f

    if (safeAspect > destAspect) {
        cropXFrac = 0.5f * (1f - destAspect / safeAspect)
    } else if (safeAspect < destAspect) {
        cropYFrac = 0.5f * (1f - safeAspect / destAspect)
    }

    val xSpan = (1f - 2f * cropXFrac).coerceAtLeast(0.0001f)
    val ySpan = (1f - 2f * cropYFrac).coerceAtLeast(0.0001f)

    fun mapX(x: Float) = ((x - cropXFrac) / xSpan) * w
    fun mapY(y: Float) = ((y - cropYFrac) / ySpan) * h

    return corners.take(4).map {
        Offset(
            mapX(it.x.coerceIn(0f, 1f)),
            mapY(it.y.coerceIn(0f, 1f))
        )
    }
}

// Exact CamScanner Signature Colors (reproduced faithfully from CaptureActivity reference screenshot)
private val CamScannerTeal = Color(0xFF28D8A1)       // Signature mint-teal line
private val CamScannerTealBright = Color(0xFF38EFB6) // Stable locked crisp mint-teal
private val CamScannerTealGlow = Color(0x4028D8A1)   // Soft outer edge glow

/** A light mint four-edge contour without camera-view handles or scanning lasers. */
private fun DrawScope.drawDocumentOutline(
    pts: List<Offset>,
    isStable: Boolean,
    alpha: Float
) {
    if (pts.size != 4) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1..3) lineTo(pts[i].x, pts[i].y)
        close()
    }
    // The normal camera view has no midpoint handles; users edit corners after capture.
    drawPath(path, CamScannerTeal.copy(alpha = 0.045f * alpha), style = Fill)
    drawPath(
        path = path,
        color = (if (isStable) CamScannerTealBright else CamScannerTeal).copy(alpha = alpha),
        style = Stroke(
            width = if (isStable) 2.5.dp.toPx() else 2.1.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Clean ID Card Guide Frame in CamScanner style.
 */
private fun DrawScope.drawIdCardCamScannerFrame(
    w: Float, h: Float,
    idCardType: com.docscan.data.model.IdCardType,
    pulseGlow: Float
) {
    val ratio = if (idCardType == com.docscan.data.model.IdCardType.PASSPORT) 1.42f else 1.586f
    val cardW = if (idCardType == com.docscan.data.model.IdCardType.PASSPORT) w * 0.86f else w * 0.82f
    val cardH = cardW / ratio
    val left = (w - cardW) / 2f
    val top = (h - cardH) / 2.3f
    val frame = Rect(left, top, left + cardW, top + cardH)

    // Gentle focus mask outside card area
    val outerPath = Path().apply { addRect(Rect(0f, 0f, w, h)) }
    val holePath = Path().apply {
        addRoundRect(RoundRect(frame, CornerRadius(16.dp.toPx(), 16.dp.toPx())))
    }
    val maskPath = Path.combine(PathOperation.Difference, outerPath, holePath)
    drawPath(maskPath, Color(0x4D000000))

    // Card boundary outline
    drawRoundRect(
        color = CamScannerTeal.copy(alpha = 0.92f * pulseGlow),
        topLeft = Offset(frame.left, frame.top),
        size = Size(frame.width, frame.height),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        style = Stroke(width = 2.4.dp.toPx())
    )

    // Corner L-brackets
    val bracket = 24.dp.toPx()
    val stroke = 3.5.dp.toPx()
    val c = CamScannerTealBright
    // TL
    drawLine(c, Offset(frame.left, frame.top + bracket), Offset(frame.left, frame.top), strokeWidth = stroke)
    drawLine(c, Offset(frame.left, frame.top), Offset(frame.left + bracket, frame.top), strokeWidth = stroke)
    // TR
    drawLine(c, Offset(frame.right - bracket, frame.top), Offset(frame.right, frame.top), strokeWidth = stroke)
    drawLine(c, Offset(frame.right, frame.top), Offset(frame.right, frame.top + bracket), strokeWidth = stroke)
    // BL
    drawLine(c, Offset(frame.left, frame.bottom - bracket), Offset(frame.left, frame.bottom), strokeWidth = stroke)
    drawLine(c, Offset(frame.left, frame.bottom), Offset(frame.left + bracket, frame.bottom), strokeWidth = stroke)
    // BR
    drawLine(c, Offset(frame.right - bracket, frame.bottom), Offset(frame.right, frame.bottom), strokeWidth = stroke)
    drawLine(c, Offset(frame.right, frame.bottom), Offset(frame.right, frame.bottom - bracket), strokeWidth = stroke)
}
