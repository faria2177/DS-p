package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Heuristics-based text styling estimator. Analyzes pixel distributions in and around
 * bounding boxes to estimate background paper color, ink text color, font size, and stroke weight.
 */
object TextStyleEstimator {

    data class EstimatedStyle(
        val textColor: Color,
        val backgroundColor: Color,
        val fontSizeSp: Float,
        val fontStyle: FontStyleEstimate
    )

    /**
     * Estimates text styling parameters directly from the source bitmap and detected bounding box.
     */
    fun estimate(
        bitmap: Bitmap,
        pixelRect: Rect,
        documentRefWidth: Int = bitmap.width,
        recognizedText: String? = null
    ): EstimatedStyle {
        val w = bitmap.width
        val h = bitmap.height

        val left = pixelRect.left.coerceIn(0, w - 1)
        val top = pixelRect.top.coerceIn(0, h - 1)
        val right = pixelRect.right.coerceIn(left + 1, w)
        val bottom = pixelRect.bottom.coerceIn(top + 1, h)

        val boxW = right - left
        val boxH = bottom - top

        // 1. Sample Background Color from perimeter ring (outside box to avoid text ink)
        val perimeterColors = mutableListOf<Int>()
        val marginX = (boxW * 0.15f).toInt().coerceIn(2, 20)
        val marginY = (boxH * 0.25f).toInt().coerceIn(2, 20)

        val sampleTop = (top - marginY).coerceIn(0, h - 1)
        val sampleBottom = (bottom + marginY).coerceIn(0, h - 1)
        val sampleLeft = (left - marginX).coerceIn(0, w - 1)
        val sampleRight = (right + marginX).coerceIn(0, w - 1)

        val stepX = max(1, (sampleRight - sampleLeft) / 16)
        val stepY = max(1, (sampleBottom - sampleTop) / 8)

        // Top and bottom edges
        for (x in sampleLeft..sampleRight step stepX) {
            perimeterColors.add(bitmap.getPixel(x, sampleTop))
            perimeterColors.add(bitmap.getPixel(x, sampleBottom))
        }
        // Left and right edges
        for (y in sampleTop..sampleBottom step stepY) {
            perimeterColors.add(bitmap.getPixel(sampleLeft, y))
            perimeterColors.add(bitmap.getPixel(sampleRight, y))
        }

        // Calculate average background (ignoring outliers)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var bgCount = 0
        for (pixel in perimeterColors) {
            val r = AndroidColor.red(pixel)
            val g = AndroidColor.green(pixel)
            val b = AndroidColor.blue(pixel)
            // Filter out accidentally sampled dark ink
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            if (lum > 80) {
                sumR += r
                sumG += g
                sumB += b
                bgCount++
            }
        }

        val estimatedBg = if (bgCount > 0) {
            Color(
                (sumR / bgCount).toInt().coerceIn(0, 255),
                (sumG / bgCount).toInt().coerceIn(0, 255),
                (sumB / bgCount).toInt().coerceIn(0, 255)
            )
        } else {
            Color.White
        }

        // 2. Sample Text Ink Color (darkest pixels inside bounding box)
        val insidePixels = mutableListOf<Int>()
        val inStepX = max(1, boxW / 24)
        val inStepY = max(1, boxH / 12)

        for (x in left until right step inStepX) {
            for (y in top until bottom step inStepY) {
                insidePixels.add(bitmap.getPixel(x, y))
            }
        }

        // Sort pixels by luminance to find ink cluster
        val sortedByLuminance = insidePixels.sortedBy { pixel ->
            val r = AndroidColor.red(pixel)
            val g = AndroidColor.green(pixel)
            val b = AndroidColor.blue(pixel)
            0.299 * r + 0.587 * g + 0.114 * b
        }

        // Take the darkest 20% pixels
        val inkSampleCount = max(1, (sortedByLuminance.size * 0.2f).toInt())
        var inkR = 0L
        var inkG = 0L
        var inkB = 0L
        for (i in 0 until inkSampleCount) {
            val p = sortedByLuminance[i]
            inkR += AndroidColor.red(p)
            inkG += AndroidColor.green(p)
            inkB += AndroidColor.blue(p)
        }

        val avgInkR = (inkR / inkSampleCount).toInt().coerceIn(0, 255)
        val avgInkG = (inkG / inkSampleCount).toInt().coerceIn(0, 255)
        val avgInkB = (inkB / inkSampleCount).toInt().coerceIn(0, 255)

        val estimatedText = Color(avgInkR, avgInkG, avgInkB)

        // 3. Build a foreground/ink mask relative to the locally estimated paper color.
        // Using color distance instead of a fixed black threshold keeps coloured headings
        // and light-weight fonts detectable.
        val bgR = (estimatedBg.red * 255f).toInt()
        val bgG = (estimatedBg.green * 255f).toInt()
        val bgB = (estimatedBg.blue * 255f).toInt()
        fun isInk(pixel: Int): Boolean {
            val dr = AndroidColor.red(pixel) - bgR
            val dg = AndroidColor.green(pixel) - bgG
            val db = AndroidColor.blue(pixel) - bgB
            val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())
            val lum = 0.299 * AndroidColor.red(pixel) + 0.587 * AndroidColor.green(pixel) + 0.114 * AndroidColor.blue(pixel)
            val bgLum = 0.299 * bgR + 0.587 * bgG + 0.114 * bgB
            return distance > 44.0 && abs(lum - bgLum) > 28.0
        }

        // Find the actual glyph-height inside the OCR rectangle. This is more stable than
        // blindly using the whole OCR box (which may contain ascender/descender padding).
        val rowInkCounts = IntArray(boxH.coerceAtLeast(1))
        val colStep = max(1, boxW / 80)
        for (yy in top until bottom) {
            var count = 0
            for (xx in left until right step colStep) {
                if (isInk(bitmap.getPixel(xx, yy))) count++
            }
            rowInkCounts[yy - top] = count
        }
        val minUsefulRowInk = max(1, (boxW / colStep * 0.03f).toInt())
        val firstInkRow = rowInkCounts.indexOfFirst { it >= minUsefulRowInk }.takeIf { it >= 0 } ?: 0
        val lastInkRow = rowInkCounts.indexOfLast { it >= minUsefulRowInk }.takeIf { it >= 0 } ?: (boxH - 1)
        val glyphHeightPx = (lastInkRow - firstInkRow + 1).coerceAtLeast(1)

        // 4. Estimate Font Size (SP) relative to a 400dp document viewport.
        val docScale = 400f / documentRefWidth.coerceAtLeast(100).toFloat()
        val estimatedSizeSp = (glyphHeightPx * docScale * 0.86f).coerceIn(7f, 72f)

        // 5. Weight detection from foreground density.
        var inkSamples = 0
        var totalSamples = 0
        val sampleStepX = max(1, boxW / 60)
        val sampleStepY = max(1, boxH / 24)
        for (yy in top until bottom step sampleStepY) {
            for (xx in left until right step sampleStepX) {
                totalSamples++
                if (isInk(bitmap.getPixel(xx, yy))) inkSamples++
            }
        }
        val inkDensity = if (totalSamples > 0) inkSamples.toFloat() / totalSamples else 0f
        val isBold = inkDensity > 0.24f

        // 6. Italic/slant estimate. Compare foreground centroids in upper vs lower
        // glyph halves. A consistent horizontal shift is a useful approximation without
        // adding a heavyweight font-classification model.
        fun halfCentroid(yStart: Int, yEnd: Int): Float? {
            var weightedX = 0L
            var count = 0L
            val yy0 = yStart.coerceIn(top, bottom - 1)
            val yy1 = yEnd.coerceIn(yy0 + 1, bottom)
            for (yy in yy0 until yy1 step max(1, boxH / 24)) {
                for (xx in left until right step max(1, boxW / 60)) {
                    if (isInk(bitmap.getPixel(xx, yy))) {
                        weightedX += xx.toLong()
                        count++
                    }
                }
            }
            return if (count > 0) weightedX.toFloat() / count else null
        }
        val midY = top + boxH / 2
        val upperCentroid = halfCentroid(top + firstInkRow, midY)
        val lowerCentroid = halfCentroid(midY, top + lastInkRow + 1)
        val slantRatio = if (upperCentroid != null && lowerCentroid != null) {
            abs(upperCentroid - lowerCentroid) / boxH.coerceAtLeast(1).toFloat()
        } else 0f
        val isItalic = slantRatio > 0.12f

        // 7. Underline estimate: look for a strong ink row near, but not exactly on, the
        // lower edge. Ignoring the final 5% helps avoid mistaking table borders for underline.
        val underlineStart = (boxH * 0.68f).toInt().coerceIn(0, boxH - 1)
        val underlineEnd = (boxH * 0.94f).toInt().coerceIn(underlineStart + 1, boxH)
        val sampledColumns = max(1, boxW / colStep)
        val isUnderline = (underlineStart until underlineEnd).any { row ->
            rowInkCounts.getOrElse(row) { 0 }.toFloat() / sampledColumns > 0.58f
        }

        // 8. Closest built-in font family classification. Exact embedded PDF font names
        // are not available after rasterization, so we choose the closest safe Android family.
        val compactText = recognizedText.orEmpty().filterNot { it.isWhitespace() }
        val codeLikeRatio = if (compactText.isNotEmpty()) {
            compactText.count { it.isDigit() || it in "-_/.:#<>" }.toFloat() / compactText.length
        } else 0f
        val topBottomInk = if (rowInkCounts.isNotEmpty()) {
            val edgeBand = max(1, (rowInkCounts.size * 0.20f).toInt())
            val edge = rowInkCounts.take(edgeBand).sum() + rowInkCounts.takeLast(edgeBand).sum()
            val middle = rowInkCounts.drop(edgeBand).dropLast(edgeBand).sum().coerceAtLeast(1)
            edge.toFloat() / middle
        } else 0f
        val avgCharWidthRatio = if (compactText.isNotEmpty() && glyphHeightPx > 0) {
            boxW.toFloat() / compactText.length.toFloat() / glyphHeightPx.toFloat()
        } else 0.55f

        val family = when {
            BengaliTextSupport.containsBangla(compactText) -> "BENGALI"
            compactText.length >= 6 && codeLikeRatio >= 0.72f -> "MONO"
            compactText.length >= 3 && avgCharWidthRatio < 0.42f -> "SANS_CONDENSED"
            compactText.length >= 3 && topBottomInk > 0.98f && !isBold -> "SERIF"
            isBold -> "SANS_MEDIUM"
            inkDensity < 0.115f -> "SANS_LIGHT"
            else -> "SANS"
        }

        return EstimatedStyle(
            textColor = estimatedText,
            backgroundColor = estimatedBg,
            fontSizeSp = estimatedSizeSp,
            fontStyle = FontStyleEstimate(
                isBold = isBold,
                isItalic = isItalic,
                isUnderline = isUnderline,
                fontFamilyType = family
            )
        )
    }
}
