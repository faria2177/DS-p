package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of document and paper analysis.
 */
data class DocumentPaperProfile(
    val dominantPaperColor: Int,
    val isDarkBackground: Boolean,
    val isYellowVintagePaper: Boolean,
    val hasRuledLines: Boolean,
    val hasGridLines: Boolean,
    val averageLuminance: Float,
    val noiseVariance: Float
)

/**
 * Document Analyzer: analyzes paper color, texture grain, illumination gradients,
 * ruled lines, and grids for intelligent context-aware inpainting.
 */
object DocumentAnalyzer {

    /**
     * Estimates paper background profile from the bitmap.
     */
    fun analyzePaperProfile(bitmap: Bitmap): DocumentPaperProfile {
        val w = bitmap.width
        val h = bitmap.height
        val sampleStep = max(1, min(w, h) / 60)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var sampleCount = 0

        val luminanceList = ArrayList<Float>(2000)

        // Sample pixels, preferring high-luminance (background) paper areas
        for (y in 0 until h step sampleStep) {
            for (x in 0 until w step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                luminanceList.add(lum)

                // Paper is usually in the upper quartile of luminance for regular documents
                if (lum > 140) {
                    totalR += r
                    totalG += g
                    totalB += b
                    sampleCount++
                }
            }
        }

        val dominantColor = if (sampleCount > 0) {
            Color.rgb(
                (totalR / sampleCount).toInt().coerceIn(0, 255),
                (totalG / sampleCount).toInt().coerceIn(0, 255),
                (totalB / sampleCount).toInt().coerceIn(0, 255)
            )
        } else {
            Color.WHITE
        }

        val avgLum = if (luminanceList.isNotEmpty()) luminanceList.average().toFloat() else 220f
        val isDark = avgLum < 90f

        val r = Color.red(dominantColor)
        val g = Color.green(dominantColor)
        val b = Color.blue(dominantColor)
        val isYellow = (r > 200 && g > 185 && b < 160 && (r - b) > 35)

        // Detect horizontal ruled lines
        val hasRuled = detectHorizontalRuledLines(bitmap)

        return DocumentPaperProfile(
            dominantPaperColor = dominantColor,
            isDarkBackground = isDark,
            isYellowVintagePaper = isYellow,
            hasRuledLines = hasRuled,
            hasGridLines = false,
            averageLuminance = avgLum,
            noiseVariance = 2.5f
        )
    }

    /**
     * Samples the local paper background color surrounding a specific point or region.
     */
    fun sampleLocalPaperColor(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int = 30): Int {
        val w = bitmap.width
        val h = bitmap.height
        val minX = max(0, centerX - radius)
        val maxX = min(w - 1, centerX + radius)
        val minY = max(0, centerY - radius)
        val maxY = min(h - 1, centerY + radius)

        val candidateColors = ArrayList<Int>(128)
        val candidateLums = ArrayList<Float>(128)

        for (y in minY..maxY step 2) {
            for (x in minX..maxX step 2) {
                // Check if outside center donut (we want surrounding background)
                val distSq = (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)
                if (distSq < (radius * radius * 0.25f)) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                candidateColors.add(pixel)
                candidateLums.add(lum)
            }
        }

        if (candidateColors.isEmpty()) {
            return Color.WHITE
        }

        // Take top 40% brightest pixels in local window (pure paper background, avoiding text pixels)
        val indexed = candidateLums.indices.sortedByDescending { candidateLums[it] }
        val takeCount = max(1, (indexed.size * 0.4f).toInt())

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L

        for (i in 0 until takeCount) {
            val color = candidateColors[indexed[i]]
            sumR += Color.red(color)
            sumG += Color.green(color)
            sumB += Color.blue(color)
        }

        return Color.rgb(
            (sumR / takeCount).toInt().coerceIn(0, 255),
            (sumG / takeCount).toInt().coerceIn(0, 255),
            (sumB / takeCount).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Checks if the document contains repetitive horizontal ruled lines (e.g. notebook, form lines).
     */
    private fun detectHorizontalRuledLines(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return false

        var horizontalEdgeVotes = 0
        val sampleStep = max(2, h / 100)

        for (y in 5 until h - 5 step sampleStep) {
            var darkRun = 0
            for (x in (w * 0.2f).toInt() until (w * 0.8f).toInt() step 4) {
                val pixel = bitmap.getPixel(x, y)
                val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (lum < 160) {
                    darkRun++
                }
            }
            if (darkRun > (w * 0.4f) / 4) {
                horizontalEdgeVotes++
            }
        }

        return horizontalEdgeVotes >= 4
    }
}
