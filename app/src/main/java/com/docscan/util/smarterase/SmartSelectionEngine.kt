package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import androidx.compose.ui.geometry.Offset
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Smart Selection Engine: implements intelligent tap-to-select object segmentation (Mode B)
 * and freehand lasso polygon selection (Mode C).
 */
object SmartSelectionEngine {

    /**
     * Mode B: Magic Wand / Smart Object Segmentation.
     * Given a tap point (normX, normY), detects the boundaries of the unwanted object
     * (signature, pen stroke, stamp, handwriting word, stain) using adaptive color distance,
     * gradient thresholding, and connected-component flood-fill.
     */
    fun segmentObjectAtPoint(
        bitmap: Bitmap,
        tapXNorm: Float,
        tapYNorm: Float,
        tolerance: Float = 35f,
        maxRadiusNorm: Float = 0.25f
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val startX = (tapXNorm * w).toInt().coerceIn(0, w - 1)
        val startY = (tapYNorm * h).toInt().coerceIn(0, h - 1)

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val seedColor = bitmap.getPixel(startX, startY)
        val seedR = Color.red(seedColor)
        val seedG = Color.green(seedColor)
        val seedB = Color.blue(seedColor)
        val seedLum = 0.299f * seedR + 0.587f * seedG + 0.114f * seedB

        val maxRadiusPx = (max(w, h) * maxRadiusNorm).toInt()
        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Point>(2048)

        // Add 3x3 seed neighborhood
        for (dy in -2..2) {
            for (dx in -2..2) {
                val nx = (startX + dx).coerceIn(0, w - 1)
                val ny = (startY + dy).coerceIn(0, h - 1)
                queue.add(Point(nx, ny))
                visited[ny * w + nx] = true
            }
        }

        val maskPixels = ByteArray(w * h)
        var hitCount = 0

        while (queue.isNotEmpty() && hitCount < (w * h * 0.35f)) {
            val pt = queue.removeFirst()
            val x = pt.x
            val y = pt.y

            val distFromSeed = sqrt(((x - startX) * (x - startX) + (y - startY) * (y - startY)).toFloat())
            if (distFromSeed > maxRadiusPx) continue

            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Color distance
            val colorDist = sqrt(
                ((r - seedR) * (r - seedR) + (g - seedG) * (g - seedG) + (b - seedB) * (b - seedB)).toFloat()
            )

            // Ink detection: If seed is dark ink (lum < 160), match nearby dark ink pixels
            val isMatch = if (seedLum < 160) {
                (lum < 175) && (colorDist < tolerance * 2.2f || (lum < seedLum + 45))
            } else {
                colorDist < tolerance
            }

            if (isMatch) {
                maskPixels[y * w + x] = 255.toByte()
                hitCount++

                // Explore 4 neighbors
                val neighbors = arrayOf(
                    Point(x + 1, y),
                    Point(x - 1, y),
                    Point(x, y + 1),
                    Point(x, y - 1)
                )

                for (n in neighbors) {
                    if (n.x in 0 until w && n.y in 0 until h) {
                        val idx = n.y * w + n.x
                        if (!visited[idx]) {
                            visited[idx] = true
                            queue.add(n)
                        }
                    }
                }
            }
        }

        // Convert byte mask to bitmap
        val argbPixels = IntArray(w * h)
        for (i in argbPixels.indices) {
            val a = maskPixels[i].toInt() and 0xFF
            argbPixels[i] = Color.argb(a, 255, 255, 255)
        }
        mask.setPixels(argbPixels, 0, w, 0, 0, w, h)

        // Dilate slightly to ensure full ink coverage
        return MaskProcessor.dilateMask(mask, 3)
    }

    /**
     * Mode C: Lasso Selection.
     * Takes normalized freehand trace points, creates a closed polygon mask, and feathers edges.
     */
    fun createLassoMask(
        width: Int,
        height: Int,
        pointsNorm: List<Offset>,
        featherSigma: Float = 2.0f
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (pointsNorm.size < 3) return mask

        val canvas = Canvas(mask)
        val path = Path()
        val p0 = pointsNorm[0]
        path.moveTo(p0.x * width, p0.y * height)

        for (i in 1 until pointsNorm.size) {
            val pt = pointsNorm[i]
            path.lineTo(pt.x * width, pt.y * height)
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        canvas.drawPath(path, paint)

        return if (featherSigma > 0.5f) {
            MaskProcessor.featherMask(mask, featherSigma)
        } else {
            mask
        }
    }

    /**
     * Mode: Rectangle / Box Selection.
     * Takes normalized box coordinates and creates a crisp or feathered rectangular mask.
     */
    fun createRectangleMask(
        width: Int,
        height: Int,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        featherSigma: Float = 1.5f
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val l = (min(leftNorm, rightNorm) * width).toInt().coerceIn(0, width - 1)
        val t = (min(topNorm, bottomNorm) * height).toInt().coerceIn(0, height - 1)
        val r = (max(leftNorm, rightNorm) * width).toInt().coerceIn(l + 1, width)
        val b = (max(topNorm, bottomNorm) * height).toInt().coerceIn(t + 1, height)

        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)

        return if (featherSigma > 0.5f) {
            MaskProcessor.featherMask(mask, featherSigma)
        } else {
            mask
        }
    }
}
