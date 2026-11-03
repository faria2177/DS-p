package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Premium Document Scanner Overlay View.
 * Renders smooth real-time quadrilateral boundary lines, animated pulsing corner handles,
 * color-coded stability states (Green = stable, Yellow = stabilizing, Hidden = no document),
 * contextual guidance badge, manual touch-drag corner adjustment with loupe, and auto-capture ring.
 */
@Composable
fun DocumentScannerView(
    corners: List<PointF>, // Viewfinder pixel coordinates: [0] TL, [1] TR, [2] BR, [3] BL
    isStable: Boolean,
    isValid: Boolean,
    confidence: Double,
    guidanceMessage: String,
    autoCaptureProgress: Float = 0f,
    isManualAdjustmentMode: Boolean = false,
    onCornerDragged: ((index: Int, newPos: PointF) -> Unit)? = null,
    onResetCorners: (() -> Unit)? = null,
    onAutoDetectAgain: (() -> Unit)? = null,
    onApplyManualCorners: (() -> Unit)? = null,
    onCancelManualAdjustment: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CornerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Dynamic state colors: Green for stable detection, Yellow/Amber for stabilizing
    val targetBorderColor = when {
        !isValid || corners.size != 4 -> Color.Transparent
        isStable -> Color(0xFF00E676) // Bright CamScanner Green
        else -> Color(0xFFFFD600)     // Amber/Yellow stabilizing
    }

    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "BorderColor")
    val fillColor = borderColor.copy(alpha = if (isStable) 0.16f else 0.10f)

    var activeDragIndex by remember { mutableIntStateOf(-1) }

    Box(modifier = modifier.fillMaxSize()) {

        // 1. QUADRILATERAL BOUNDARY & CORNER HANDLES CANVAS
        if (corners.size == 4 && isValid) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("document_boundary_canvas")
                    .pointerInput(isManualAdjustmentMode, corners) {
                        if (!isManualAdjustmentMode) return@pointerInput

                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val hitRadius = 48.dp.toPx()
                                activeDragIndex = -1
                                for (i in 0 until 4) {
                                    val dist = hypot(corners[i].x - startOffset.x, corners[i].y - startOffset.y)
                                    if (dist <= hitRadius) {
                                        activeDragIndex = i
                                        break
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (activeDragIndex in 0..3) {
                                    val current = corners[activeDragIndex]
                                    val updated = PointF(current.x + dragAmount.x, current.y + dragAmount.y)
                                    onCornerDragged?.invoke(activeDragIndex, updated)
                                }
                            },
                            onDragEnd = { activeDragIndex = -1 },
                            onDragCancel = { activeDragIndex = -1 }
                        )
                    }
            ) {
                val path = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    lineTo(corners[3].x, corners[3].y)
                    close()
                }

                // Shaded interior fill
                drawPath(path = path, color = fillColor, style = Fill)

                // Smooth thick boundary contour
                drawPath(
                    path = path,
                    color = borderColor,
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Render 4 animated corner handles
                for (i in 0 until 4) {
                    val p = corners[i]
                    val isDragged = (i == activeDragIndex)

                    // Outer glowing animated pulse ring
                    val pulseRadius = (if (isDragged) 22.dp else 16.dp).toPx() * (if (isDragged) 1.2f else pulseScale)
                    drawCircle(
                        color = borderColor.copy(alpha = if (isDragged) 0.5f else 0.28f),
                        radius = pulseRadius,
                        center = Offset(p.x, p.y)
                    )

                    // Solid ring border
                    drawCircle(
                        color = borderColor,
                        radius = (if (isDragged) 14.dp else 10.dp).toPx(),
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Inner white dot
                    drawCircle(
                        color = Color.White,
                        radius = (if (isDragged) 6.dp else 4.5.dp).toPx(),
                        center = Offset(p.x, p.y),
                        style = Fill
                    )
                }
            }
        }

        // 2. CONTEXTUAL GUIDANCE BADGE (TOP CENTER)
        if (guidanceMessage.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC111827),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isStable -> Color(0xFF00E676).copy(alpha = 0.6f)
                            isValid -> Color(0xFFFFD600).copy(alpha = 0.5f)
                            else -> Color(0x33FFFFFF)
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (autoCaptureProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { autoCaptureProgress },
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF00E676),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isStable -> Color(0xFF00E676)
                                            isValid -> Color(0xFFFFD600)
                                            else -> Color(0xFFA1A1AA)
                                        }
                                    )
                            )
                        }

                        Text(
                            text = guidanceMessage,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. MANUAL ADJUSTMENT FLOATING ACTION DECK
        AnimatedVisibility(
            visible = isManualAdjustmentMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color(0xF018181B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    IconButton(
                        onClick = { onResetCorners?.invoke() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Reset", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }

                    // Auto Detect Again
                    IconButton(
                        onClick = { onAutoDetectAgain?.invoke() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto", tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                            Text("Auto", fontSize = 10.sp, color = Color(0xFF00E676))
                        }
                    }

                    // Cancel
                    IconButton(
                        onClick = { onCancelManualAdjustment?.invoke() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Text("Cancel", fontSize = 10.sp, color = Color(0xFFEF4444))
                        }
                    }

                    // Apply Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00BFA5),
                        modifier = Modifier.clickable { onApplyManualCorners?.invoke() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
