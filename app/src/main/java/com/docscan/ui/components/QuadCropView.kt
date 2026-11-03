package com.docscan.ui.components

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.docscan.util.EdgeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun QuadCropView(
    bitmap: Bitmap,
    corners: List<Offset>, // 4 normalized points: TL (0), TR (1), BR (2), BL (3)
    onCornersChanged: (List<Offset>) -> Unit,
    selectedCornerIndex: Int = -1,
    onCornerSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeHandleIndex by remember { mutableIntStateOf(-1) }
    var currentCorners by remember(corners) { mutableStateOf(corners) }
    var adjustmentRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    val handleRadiusPx = 80f

                    detectTapGestures { touchOffset ->
                        val frame = calculateImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                        var closestIndex = -1
                        var minDistance = Float.MAX_VALUE

                        // Check 4 corners
                        currentCorners.forEachIndexed { index, normOffset ->
                            val screenPoint = Offset(
                                frame.left + normOffset.x * frame.width,
                                frame.top + normOffset.y * frame.height
                            )
                            val distance = hypot(
                                (screenPoint.x - touchOffset.x).toDouble(),
                                (screenPoint.y - touchOffset.y).toDouble()
                            ).toFloat()

                            if (distance < handleRadiusPx * 2.0f && distance < minDistance) {
                                minDistance = distance
                                closestIndex = index
                            }
                        }

                        if (closestIndex != -1) {
                            onCornerSelected(closestIndex)
                        }
                    }
                }
                .pointerInput(bitmap) {
                    val handleRadiusPx = 85f

                    detectDragGestures(
                        onDragStart = { touchOffset ->
                            adjustmentRevision += 1 // invalidates any in-flight magnetic snap from the previous gesture
                            val frame = calculateImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                            var closestIndex = -1
                            var minDistance = Float.MAX_VALUE

                            val pTL = Offset(frame.left + currentCorners[0].x * frame.width, frame.top + currentCorners[0].y * frame.height)
                            val pTR = Offset(frame.left + currentCorners[1].x * frame.width, frame.top + currentCorners[1].y * frame.height)
                            val pBR = Offset(frame.left + currentCorners[2].x * frame.width, frame.top + currentCorners[2].y * frame.height)
                            val pBL = Offset(frame.left + currentCorners[3].x * frame.width, frame.top + currentCorners[3].y * frame.height)

                            // 1. Check 4 corners (0: TL, 1: TR, 2: BR, 3: BL)
                            val cornerPoints = listOf(pTL, pTR, pBR, pBL)
                            cornerPoints.forEachIndexed { index, screenPoint ->
                                val distance = hypot(
                                    (screenPoint.x - touchOffset.x).toDouble(),
                                    (screenPoint.y - touchOffset.y).toDouble()
                                ).toFloat()

                                if (distance < handleRadiusPx && distance < minDistance) {
                                    minDistance = distance
                                    closestIndex = index
                                }
                            }

                            // 2. Check 4 side midpoints (4: Top, 5: Right, 6: Bottom, 7: Left) if no corner was hit directly
                            if (closestIndex == -1) {
                                val midPoints = listOf(
                                    Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f), // 4 Top
                                    Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f), // 5 Right
                                    Offset((pBR.x + pBL.x) / 2f, (pBR.y + pBL.y) / 2f), // 6 Bottom
                                    Offset((pBL.x + pTL.x) / 2f, (pBL.y + pTL.y) / 2f)  // 7 Left
                                )

                                midPoints.forEachIndexed { midIdx, midPoint ->
                                    val distance = hypot(
                                        (midPoint.x - touchOffset.x).toDouble(),
                                        (midPoint.y - touchOffset.y).toDouble()
                                    ).toFloat()
                                    if (distance < handleRadiusPx * 0.9f && distance < minDistance) {
                                        minDistance = distance
                                        closestIndex = midIdx + 4
                                    }
                                }
                            }

                            // 3. Check inside polygon for full body translation (8: Body Move)
                            if (closestIndex == -1 && isPointInPolygon(touchOffset, listOf(pTL, pTR, pBR, pBL))) {
                                closestIndex = 8
                            }

                            activeHandleIndex = closestIndex
                            if (closestIndex in 0..3) {
                                onCornerSelected(closestIndex)
                            }
                        },
                        onDragEnd = {
                            val finishedRevision = adjustmentRevision
                            val snapshot = currentCorners
                            activeHandleIndex = -1
                            onCornersChanged(snapshot)

                            // Magnetic edge snap runs off the UI thread. It is deliberately
                            // bounded: a refinement is accepted only when it stays close to the
                            // user's placement and keeps a valid convex quad.
                            scope.launch {
                                val refined = withContext(Dispatchers.Default) {
                                    EdgeDetector.refineNormalizedCorners(bitmap, snapshot)
                                }
                                val safe = sanitizeQuad(refined, fallback = snapshot)
                                val maxShift = maxCornerShift(snapshot, safe)
                                if (finishedRevision == adjustmentRevision && maxShift <= 0.035f) {
                                    currentCorners = safe
                                    onCornersChanged(safe)
                                }
                            }
                        },
                        onDragCancel = {
                            activeHandleIndex = -1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val frame = calculateImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                            val dxNorm = dragAmount.x / frame.width
                            val dyNorm = dragAmount.y / frame.height
                            val updated = currentCorners.toMutableList()

                            when (activeHandleIndex) {
                                0 -> { // TL
                                    val newX = (updated[0].x + dxNorm).coerceIn(0f, 1f)
                                    val newY = (updated[0].y + dyNorm).coerceIn(0f, 1f)
                                    updated[0] = Offset(newX, newY)
                                }
                                1 -> { // TR
                                    val newX = (updated[1].x + dxNorm).coerceIn(0f, 1f)
                                    val newY = (updated[1].y + dyNorm).coerceIn(0f, 1f)
                                    updated[1] = Offset(newX, newY)
                                }
                                2 -> { // BR
                                    val newX = (updated[2].x + dxNorm).coerceIn(0f, 1f)
                                    val newY = (updated[2].y + dyNorm).coerceIn(0f, 1f)
                                    updated[2] = Offset(newX, newY)
                                }
                                3 -> { // BL
                                    val newX = (updated[3].x + dxNorm).coerceIn(0f, 1f)
                                    val newY = (updated[3].y + dyNorm).coerceIn(0f, 1f)
                                    updated[3] = Offset(newX, newY)
                                }
                                4 -> shiftEdgePerpendicular(updated, 0, 1, dxNorm, dyNorm) // Top
                                5 -> shiftEdgePerpendicular(updated, 1, 2, dxNorm, dyNorm) // Right
                                6 -> shiftEdgePerpendicular(updated, 2, 3, dxNorm, dyNorm) // Bottom
                                7 -> shiftEdgePerpendicular(updated, 3, 0, dxNorm, dyNorm) // Left
                                8 -> { // Body Drag: Move whole crop box
                                    val minX = updated.minOf { it.x }
                                    val maxX = updated.maxOf { it.x }
                                    val minY = updated.minOf { it.y }
                                    val maxY = updated.maxOf { it.y }

                                    val allowedDx = when {
                                        minX + dxNorm < 0f -> -minX
                                        maxX + dxNorm > 1f -> 1f - maxX
                                        else -> dxNorm
                                    }
                                    val allowedDy = when {
                                        minY + dyNorm < 0f -> -minY
                                        maxY + dyNorm > 1f -> 1f - maxY
                                        else -> dyNorm
                                    }

                                    for (i in 0..3) {
                                        updated[i] = Offset(
                                            (updated[i].x + allowedDx).coerceIn(0f, 1f),
                                            (updated[i].y + allowedDy).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }

                            if (activeHandleIndex in 0..8) {
                                val safe = sanitizeQuad(updated, fallback = currentCorners)
                                currentCorners = safe
                                onCornersChanged(safe)
                            }
                        }
                    )
                }
        ) {
            val frame = calculateImageFrame(size, bitmap.width, bitmap.height)

            // 1. Draw source bitmap
            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
                dstSize = IntSize(frame.width.toInt(), frame.height.toInt())
            )

            // Convert normalized points to screen points
            val pTL = Offset(frame.left + currentCorners[0].x * frame.width, frame.top + currentCorners[0].y * frame.height)
            val pTR = Offset(frame.left + currentCorners[1].x * frame.width, frame.top + currentCorners[1].y * frame.height)
            val pBR = Offset(frame.left + currentCorners[2].x * frame.width, frame.top + currentCorners[2].y * frame.height)
            val pBL = Offset(frame.left + currentCorners[3].x * frame.width, frame.top + currentCorners[3].y * frame.height)

            // 2. Draw darkened semi-transparent mask outside crop quad
            val cropPath = Path().apply {
                moveTo(pTL.x, pTL.y)
                lineTo(pTR.x, pTR.y)
                lineTo(pBR.x, pBR.y)
                lineTo(pBL.x, pBL.y)
                close()
            }

            clipPath(cropPath, clipOp = ClipOp.Difference) {
                drawRect(
                    color = Color(0x7A000000),
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height)
                )
            }

            // 3. Draw crop polygon lines (ML Kit Document Scanner Google Blue & subtle grid)
            val cropBorderColor = Color(0xFF1A73E8) // ML Kit Material Blue
            val gridColor = Color(0x991A73E8)

            // Outer crop polygon line
            drawPath(
                path = cropPath,
                color = cropBorderColor,
                style = Stroke(width = 3.5f)
            )

            // Dynamic rule of thirds (3x3 grid guidelines) ONLY when actively dragging a crop handle
            if (activeHandleIndex != -1) {
                drawGridLines(pTL, pTR, pBR, pBL, gridColor)
            }

            // 4. Draw edge midpoint pill handles (Horizontal & Vertical pills)
            val midTop = Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f)
            val midRight = Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f)
            val midBottom = Offset((pBR.x + pBL.x) / 2f, (pBR.y + pBL.y) / 2f)
            val midLeft = Offset((pBL.x + pTL.x) / 2f, (pBL.y + pTL.y) / 2f)

            drawEdgePillHandle(midTop, isHorizontal = true, isActive = activeHandleIndex == 4)
            drawEdgePillHandle(midRight, isHorizontal = false, isActive = activeHandleIndex == 5)
            drawEdgePillHandle(midBottom, isHorizontal = true, isActive = activeHandleIndex == 6)
            drawEdgePillHandle(midLeft, isHorizontal = false, isActive = activeHandleIndex == 7)

            // 5. Draw 4 Corner Handles
            drawCornerHandle(pTL, isActive = activeHandleIndex == 0, isSelected = selectedCornerIndex == 0)
            drawCornerHandle(pTR, isActive = activeHandleIndex == 1, isSelected = selectedCornerIndex == 1)
            drawCornerHandle(pBR, isActive = activeHandleIndex == 2, isSelected = selectedCornerIndex == 2)
            drawCornerHandle(pBL, isActive = activeHandleIndex == 3, isSelected = selectedCornerIndex == 3)

            // 6. Interactive Magnifier Loupe (Zoom Glass) when actively dragging a corner or edge handle
            if (activeHandleIndex in 0..7) {
                val (activePoint, normPoint, label) = when (activeHandleIndex) {
                    0 -> Triple(pTL, currentCorners[0], "Top Left")
                    1 -> Triple(pTR, currentCorners[1], "Top Right")
                    2 -> Triple(pBR, currentCorners[2], "Bottom Right")
                    3 -> Triple(pBL, currentCorners[3], "Bottom Left")
                    4 -> Triple(midTop, Offset((currentCorners[0].x + currentCorners[1].x) / 2f, (currentCorners[0].y + currentCorners[1].y) / 2f), "Top Edge")
                    5 -> Triple(midRight, Offset((currentCorners[1].x + currentCorners[2].x) / 2f, (currentCorners[1].y + currentCorners[2].y) / 2f), "Right Edge")
                    6 -> Triple(midBottom, Offset((currentCorners[2].x + currentCorners[3].x) / 2f, (currentCorners[2].y + currentCorners[3].y) / 2f), "Bottom Edge")
                    else -> Triple(midLeft, Offset((currentCorners[3].x + currentCorners[0].x) / 2f, (currentCorners[3].y + currentCorners[0].y) / 2f), "Left Edge")
                }

                drawMagnifierLoupe(
                    bitmap = bitmap,
                    activePoint = activePoint,
                    normPoint = normPoint,
                    canvasSize = size,
                    cornerLabel = label
                )
            }
        }
    }
}


private fun shiftEdgePerpendicular(
    points: MutableList<Offset>,
    first: Int,
    second: Int,
    dx: Float,
    dy: Float
) {
    val a = points[first]
    val b = points[second]
    val vx = b.x - a.x
    val vy = b.y - a.y
    val length = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(0.0001f)
    val nx = -vy / length
    val ny = vx / length
    val projected = dx * nx + dy * ny
    val moveX = nx * projected
    val moveY = ny * projected
    points[first] = Offset((a.x + moveX).coerceIn(0f, 1f), (a.y + moveY).coerceIn(0f, 1f))
    points[second] = Offset((b.x + moveX).coerceIn(0f, 1f), (b.y + moveY).coerceIn(0f, 1f))
}

/**
 * Keeps the manual crop polygon convex and non-collapsed while still allowing strong perspective.
 * Invalid drag positions simply fall back to the last valid quad instead of letting handles cross.
 */
private fun sanitizeQuad(candidate: List<Offset>, fallback: List<Offset>): List<Offset> {
    if (candidate.size != 4) return fallback
    val clamped = candidate.map { Offset(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
    if (!isValidConvexQuad(clamped)) return fallback
    return clamped
}

private fun isValidConvexQuad(p: List<Offset>): Boolean {
    if (p.size != 4) return false
    val minEdge = 0.035f
    for (i in 0..3) {
        val a = p[i]
        val b = p[(i + 1) % 4]
        if (hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) < minEdge) return false
    }

    var sign = 0
    for (i in 0..3) {
        val a = p[i]
        val b = p[(i + 1) % 4]
        val c = p[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (abs(cross) < 0.0008f) return false
        val currentSign = if (cross > 0f) 1 else -1
        if (sign == 0) sign = currentSign else if (sign != currentSign) return false
    }

    var twiceArea = 0f
    for (i in 0..3) {
        val a = p[i]
        val b = p[(i + 1) % 4]
        twiceArea += a.x * b.y - b.x * a.y
    }
    return abs(twiceArea) * 0.5f >= 0.012f
}

private fun maxCornerShift(a: List<Offset>, b: List<Offset>): Float {
    if (a.size != 4 || b.size != 4) return Float.MAX_VALUE
    return (0..3).maxOf { i ->
        hypot((a[i].x - b[i].x).toDouble(), (a[i].y - b[i].y).toDouble()).toFloat()
    }
}

private fun isPointInPolygon(point: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        if ((poly[i].y > point.y) != (poly[j].y > point.y) &&
            (point.x < (poly[j].x - poly[i].x) * (point.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun calculateImageFrame(canvasSize: Size, imgWidth: Int, imgHeight: Int): Rect {
    val imgRatio = imgWidth.toFloat() / imgHeight.toFloat()
    val canvasRatio = canvasSize.width / canvasSize.height

    return if (imgRatio > canvasRatio) {
        val width = canvasSize.width
        val height = width / imgRatio
        val top = (canvasSize.height - height) / 2f
        Rect(0f, top, width, top + height)
    } else {
        val height = canvasSize.height
        val width = height * imgRatio
        val left = (canvasSize.width - width) / 2f
        Rect(left, 0f, left + width, height)
    }
}

private fun DrawScope.drawCornerHandle(point: Offset, isActive: Boolean, isSelected: Boolean) {
    val radius = if (isActive) 18f else if (isSelected) 15f else 13f

    // Outer glow for active/selected
    if (isActive || isSelected) {
        drawCircle(
            color = Color(0x661A73E8),
            radius = radius + 9f,
            center = point
        )
    }

    // Shadow
    drawCircle(
        color = Color(0x66000000),
        radius = radius + 3f,
        center = point
    )
    // White solid fill
    drawCircle(
        color = Color.White,
        radius = radius,
        center = point
    )
    // Material Google Blue inner dot / border
    drawCircle(
        color = if (isActive || isSelected) Color(0xFF1557B0) else Color(0xFF1A73E8),
        radius = radius,
        center = point,
        style = Stroke(width = if (isActive || isSelected) 5f else 4f)
    )
}

private fun DrawScope.drawEdgePillHandle(point: Offset, isHorizontal: Boolean, isActive: Boolean) {
    val width = if (isHorizontal) 32f else 12f
    val height = if (isHorizontal) 12f else 32f
    val topLeft = Offset(point.x - width / 2f, point.y - height / 2f)

    // Shadow
    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(topLeft.x - 1f, topLeft.y - 1f),
        size = Size(width + 2f, height + 2f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    // White pill body
    drawRoundRect(
        color = Color.White,
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(6f, 6f)
    )
    // Material Blue stroke
    drawRoundRect(
        color = if (isActive) Color(0xFF1557B0) else Color(0xFF1A73E8),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawGridLines(tl: Offset, tr: Offset, br: Offset, bl: Offset, color: Color) {
    for (i in 1..2) {
        val frac = i / 3f
        val topP = Offset(tl.x + (tr.x - tl.x) * frac, tl.y + (tr.y - tl.y) * frac)
        val botP = Offset(bl.x + (br.x - bl.x) * frac, bl.y + (br.y - bl.y) * frac)
        drawLine(color = color, start = topP, end = botP, strokeWidth = 1.5f)

        val leftP = Offset(tl.x + (bl.x - tl.x) * frac, tl.y + (bl.y - tl.y) * frac)
        val rightP = Offset(tr.x + (br.x - tr.x) * frac, tr.y + (br.y - tr.y) * frac)
        drawLine(color = color, start = leftP, end = rightP, strokeWidth = 1.5f)
    }
}

/**
 * Draws a circular magnifying loupe 2.5x zoomed above the touch point
 */
private fun DrawScope.drawMagnifierLoupe(
    bitmap: Bitmap,
    activePoint: Offset,
    normPoint: Offset,
    canvasSize: Size,
    cornerLabel: String
) {
    val loupeRadius = 90f
    // Place loupe above touch point; if too close to top edge, place below
    val loupeCenterY = if (activePoint.y - loupeRadius * 2.2f > 40f) {
        activePoint.y - loupeRadius * 1.8f
    } else {
        activePoint.y + loupeRadius * 1.8f
    }
    val loupeCenterX = activePoint.x.coerceIn(loupeRadius + 20f, canvasSize.width - loupeRadius - 20f)
    val loupeCenter = Offset(loupeCenterX, loupeCenterY)

    // Calculate source rect in bitmap coordinates (zoom window around touch point)
    val cropWindowBmpWidth = (bitmap.width * 0.10f).toInt().coerceIn(24, bitmap.width)
    val cropWindowBmpHeight = (bitmap.height * 0.10f).toInt().coerceIn(24, bitmap.height)

    // Keep a constant zoom window even when the handle sits at an image edge.
    // This prevents the loupe magnification from changing while the user fine-tunes a corner.
    val srcLeft = (normPoint.x * bitmap.width - cropWindowBmpWidth / 2f)
        .toInt()
        .coerceIn(0, (bitmap.width - cropWindowBmpWidth).coerceAtLeast(0))
    val srcTop = (normPoint.y * bitmap.height - cropWindowBmpHeight / 2f)
        .toInt()
        .coerceIn(0, (bitmap.height - cropWindowBmpHeight).coerceAtLeast(0))
    val srcRight = (srcLeft + cropWindowBmpWidth).coerceAtMost(bitmap.width)
    val srcBottom = (srcTop + cropWindowBmpHeight).coerceAtMost(bitmap.height)

    val srcRect = AndroidRect(srcLeft, srcTop, srcRight, srcBottom)
    val dstRect = AndroidRect(
        (loupeCenterX - loupeRadius).toInt(),
        (loupeCenterY - loupeRadius).toInt(),
        (loupeCenterX + loupeRadius).toInt(),
        (loupeCenterY + loupeRadius).toInt()
    )

    // Circular clip path for magnifier
    val loupePath = Path().apply {
        addOval(Rect(loupeCenter, loupeRadius))
    }

    // Draw shadow
    drawCircle(
        color = Color(0x88000000),
        radius = loupeRadius + 6f,
        center = loupeCenter
    )

    // Draw zoomed bitmap inside circle
    clipPath(loupePath) {
        drawIntoCanvas { canvas ->
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
            canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }

        // Draw crosshairs
        drawLine(
            color = Color(0xFF1A73E8),
            start = Offset(loupeCenterX - loupeRadius, loupeCenterY),
            end = Offset(loupeCenterX + loupeRadius, loupeCenterY),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFF1A73E8),
            start = Offset(loupeCenterX, loupeCenterY - loupeRadius),
            end = Offset(loupeCenterX, loupeCenterY + loupeRadius),
            strokeWidth = 2f
        )

        // Center target dot
        drawCircle(
            color = Color(0xFF1A73E8),
            radius = 5f,
            center = loupeCenter
        )
    }

    // Outer border ring for loupe
    drawCircle(
        color = Color.White,
        radius = loupeRadius,
        center = loupeCenter,
        style = Stroke(width = 4f)
    )
    drawCircle(
        color = Color(0xFF1A73E8),
        radius = loupeRadius + 2f,
        center = loupeCenter,
        style = Stroke(width = 2f)
    )
}
