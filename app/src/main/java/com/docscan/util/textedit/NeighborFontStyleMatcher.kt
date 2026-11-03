package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Local visual font matcher for Edit Text.
 *
 * The selected OCR item is treated as the primary source of truth. Nearby OCR items on the
 * same row/line are used as supporting samples so a noisy crop does not suddenly switch font
 * family/weight. All measurements are done against the original raster page, not the viewport.
 */
object NeighborFontStyleMatcher {

    data class Match(
        val fontFamilyType: String,
        val fontSizeSp: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val textColor: Color,
        val confidence: Float,
        val neighborIds: Set<String>
    )

    fun findNeighbors(
        target: EditorTextElement,
        all: List<EditorTextElement>,
        maxCount: Int = 8
    ): List<EditorTextElement> {
        val t = target.normalizedRect
        val tcx = (t.left + t.right) * 0.5f
        val tcy = (t.top + t.bottom) * 0.5f
        val th = t.height().coerceAtLeast(0.004f)

        return all.asSequence()
            .filter { it.id != target.id }
            .map { candidate ->
                val r = candidate.normalizedRect
                val ccx = (r.left + r.right) * 0.5f
                val ccy = (r.top + r.bottom) * 0.5f
                val verticalOverlap = overlap1d(t.top, t.bottom, r.top, r.bottom) /
                    minOf(th, r.height().coerceAtLeast(0.004f))
                val sameOcrLine = target.blockIndex >= 0 && target.lineIndex >= 0 &&
                    target.blockIndex == candidate.blockIndex && target.lineIndex == candidate.lineIndex
                val nearBaseline = abs(tcy - ccy) <= maxOf(th, r.height()) * 1.35f
                val horizontalGap = when {
                    r.right < t.left -> t.left - r.right
                    r.left > t.right -> r.left - t.right
                    else -> 0f
                }
                val distance = hypot((tcx - ccx).toDouble(), (tcy - ccy).toDouble()).toFloat()
                val eligible = sameOcrLine ||
                    (verticalOverlap >= 0.35f && horizontalGap <= 0.22f) ||
                    (nearBaseline && distance <= 0.32f)
                if (!eligible) null else {
                    val score = (if (sameOcrLine) 3f else 0f) +
                        verticalOverlap * 2f +
                        (1f - (horizontalGap / 0.22f).coerceIn(0f, 1f)) +
                        (1f - (distance / 0.32f).coerceIn(0f, 1f))
                    candidate to score
                }
            }
            .filterNotNull()
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { it.first }
            .toList()
    }

    fun analyze(
        bitmap: Bitmap,
        target: EditorTextElement,
        neighbors: List<EditorTextElement>
    ): Match {
        val targetEstimate = estimateFor(bitmap, target)
        val samples = buildList {
            add(Triple(target, targetEstimate, 4.5f))
            neighbors.forEach { n ->
                val sameLine = target.blockIndex >= 0 && target.lineIndex >= 0 &&
                    target.blockIndex == n.blockIndex && target.lineIndex == n.lineIndex
                add(Triple(n, estimateFor(bitmap, n), if (sameLine) 1.6f else 0.8f))
            }
        }

        val familyScores = linkedMapOf<String, Float>()
        var boldScore = 0f
        var italicScore = 0f
        var underlineScore = 0f
        var totalWeight = 0f
        var sizeWeighted = 0f
        var colorR = 0f
        var colorG = 0f
        var colorB = 0f

        samples.forEach { (_, style, weight) ->
            val family = normalizeFamily(style.fontStyle.fontFamilyType)
            familyScores[family] = (familyScores[family] ?: 0f) + weight
            if (style.fontStyle.isBold) boldScore += weight
            if (style.fontStyle.isItalic) italicScore += weight
            if (style.fontStyle.isUnderline) underlineScore += weight
            sizeWeighted += style.fontSizeSp * weight
            val argb = style.textColor.toArgbCompat()
            colorR += AndroidColor.red(argb) * weight
            colorG += AndroidColor.green(argb) * weight
            colorB += AndroidColor.blue(argb) * weight
            totalWeight += weight
        }

        val family = familyScores.maxByOrNull { it.value }?.key
            ?: normalizeFamily(targetEstimate.fontStyle.fontFamilyType)
        val size = if (totalWeight > 0f) sizeWeighted / totalWeight else target.fontSizeSp
        val r = if (totalWeight > 0f) (colorR / totalWeight).toInt().coerceIn(0, 255) else 0
        val g = if (totalWeight > 0f) (colorG / totalWeight).toInt().coerceIn(0, 255) else 0
        val b = if (totalWeight > 0f) (colorB / totalWeight).toInt().coerceIn(0, 255) else 0

        // Keep target crop dominant: neighbours should stabilise the classification, not replace it.
        val dominantFamilyWeight = familyScores[family] ?: 0f
        val confidence = if (totalWeight <= 0f) 0.5f else (dominantFamilyWeight / totalWeight).coerceIn(0f, 1f)

        return Match(
            fontFamilyType = family,
            fontSizeSp = size.coerceIn(5f, 96f),
            isBold = boldScore >= totalWeight * 0.46f,
            isItalic = italicScore >= totalWeight * 0.46f,
            isUnderline = underlineScore >= totalWeight * 0.52f,
            textColor = Color(r, g, b),
            confidence = confidence,
            neighborIds = neighbors.mapTo(linkedSetOf()) { it.id }
        )
    }

    fun contextRect(target: EditorTextElement, neighbors: List<EditorTextElement>): RectF {
        var left = target.normalizedRect.left
        var top = target.normalizedRect.top
        var right = target.normalizedRect.right
        var bottom = target.normalizedRect.bottom
        neighbors.forEach {
            left = minOf(left, it.normalizedRect.left)
            top = minOf(top, it.normalizedRect.top)
            right = maxOf(right, it.normalizedRect.right)
            bottom = maxOf(bottom, it.normalizedRect.bottom)
        }
        val width = (right - left).coerceAtLeast(target.normalizedRect.width())
        val height = (bottom - top).coerceAtLeast(target.normalizedRect.height())
        val padX = maxOf(0.02f, width * 0.10f)
        val padY = maxOf(0.015f, height * 0.45f)
        return RectF(
            (left - padX).coerceAtLeast(0f),
            (top - padY).coerceAtLeast(0f),
            (right + padX).coerceAtMost(1f),
            (bottom + padY).coerceAtMost(1f)
        )
    }

    private fun estimateFor(bitmap: Bitmap, element: EditorTextElement): TextStyleEstimator.EstimatedStyle {
        val r = element.normalizedRect
        val px = Rect(
            (r.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
            (r.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1),
            (r.right * bitmap.width).toInt().coerceIn(1, bitmap.width),
            (r.bottom * bitmap.height).toInt().coerceIn(1, bitmap.height)
        )
        if (px.right <= px.left) px.right = (px.left + 1).coerceAtMost(bitmap.width)
        if (px.bottom <= px.top) px.bottom = (px.top + 1).coerceAtMost(bitmap.height)
        return TextStyleEstimator.estimate(
            bitmap = bitmap,
            pixelRect = px,
            documentRefWidth = bitmap.width,
            recognizedText = element.text
        )
    }

    private fun overlap1d(a0: Float, a1: Float, b0: Float, b1: Float): Float =
        (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0f)

    private fun normalizeFamily(value: String): String = when (value.uppercase()) {
        "DEFAULT" -> "SANS"
        "MONOSPACE" -> "MONO"
        "CONDENSED" -> "SANS_CONDENSED"
        "MEDIUM" -> "SANS_MEDIUM"
        "LIGHT" -> "SANS_LIGHT"
        "BANGLA" -> "BENGALI"
        else -> value.uppercase()
    }

    private fun Color.toArgbCompat(): Int {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        val r = (red * 255f).toInt().coerceIn(0, 255)
        val g = (green * 255f).toInt().coerceIn(0, 255)
        val b = (blue * 255f).toInt().coerceIn(0, 255)
        return AndroidColor.argb(a, r, g, b)
    }
}
