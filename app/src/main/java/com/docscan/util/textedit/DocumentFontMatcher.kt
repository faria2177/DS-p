package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision typography matcher for in-place document text replacement.
 *
 * The selected word is the primary signal, but we also inspect the original
 * OCR line and nearby words on the same baseline. This makes the replacement
 * look much closer to the surrounding document typography than relying on a
 * generic font family from OCR alone.
 */
object DocumentFontMatcher {

    data class TypographyMatch(
        val fontFamilyType: String,
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val fontSizeSp: Float,
        val textColor: Color,
        val backgroundColor: Color,
        val confidence: Float,
        val contextNormalizedRect: RectF
    )

    private data class WeightedStyle(
        val style: TextStyleEstimator.EstimatedStyle,
        val weight: Float
    )

    fun analyze(
        bitmap: Bitmap,
        ocrDocument: OcrDocument?,
        element: EditorTextElement
    ): TypographyMatch {
        val selectedPixelRect = normalizedToPixelRect(element.normalizedRect, bitmap.width, bitmap.height)
        val selectedStyle = TextStyleEstimator.estimate(
            bitmap = bitmap,
            pixelRect = selectedPixelRect,
            documentRefWidth = bitmap.width,
            recognizedText = element.text
        )

        val weighted = mutableListOf(
            WeightedStyle(selectedStyle, 5.0f)
        )

        val line = ocrDocument
            ?.blocks
            ?.getOrNull(element.blockIndex)
            ?.lines
            ?.getOrNull(element.lineIndex)

        if (line != null) {
            // Whole-line typography is useful when the selected word is very short,
            // noisy or underlined. Keep it below the selected word in weight.
            runCatching {
                TextStyleEstimator.estimate(
                    bitmap = bitmap,
                    pixelRect = line.pixelRect,
                    documentRefWidth = bitmap.width,
                    recognizedText = line.text
                )
            }.getOrNull()?.let { weighted += WeightedStyle(it, 2.2f) }

            val selectedCenterX = element.normalizedRect.centerX()
            val selectedHeight = element.normalizedRect.height().coerceAtLeast(0.001f)

            line.elements
                .filter { it.id != element.id && it.text.isNotBlank() }
                .map { word ->
                    val distance = abs(word.normalizedRect.centerX() - selectedCenterX)
                    val heightRatio = word.normalizedRect.height().coerceAtLeast(0.001f) / selectedHeight
                    Triple(word, distance, heightRatio)
                }
                .filter { (_, _, heightRatio) -> heightRatio in 0.62f..1.62f }
                .sortedBy { (_, distance, _) -> distance }
                .take(6)
                .forEachIndexed { index, (word, _, _) ->
                    val estimate = runCatching {
                        TextStyleEstimator.estimate(
                            bitmap = bitmap,
                            pixelRect = word.pixelRect,
                            documentRefWidth = bitmap.width,
                            recognizedText = word.text
                        )
                    }.getOrNull() ?: return@forEachIndexed

                    // Nearest words contribute more than distant words.
                    val weight = when (index) {
                        0 -> 1.8f
                        1 -> 1.5f
                        2 -> 1.2f
                        else -> 0.8f
                    }
                    weighted += WeightedStyle(estimate, weight)
                }
        }

        val familyWeights = linkedMapOf<String, Float>()
        weighted.forEach { item ->
            val family = normalizeFamily(item.style.fontStyle.fontFamilyType)
            familyWeights[family] = (familyWeights[family] ?: 0f) + item.weight
        }
        val bestFamilyEntry = familyWeights.maxByOrNull { it.value }
        val family = bestFamilyEntry?.key ?: normalizeFamily(selectedStyle.fontStyle.fontFamilyType)
        val totalWeight = weighted.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val familyConfidence = ((bestFamilyEntry?.value ?: 0f) / totalWeight).coerceIn(0f, 1f)

        fun weightedBoolean(selector: (TextStyleEstimator.EstimatedStyle) -> Boolean, selectedValue: Boolean): Boolean {
            val yes = weighted.filter { selector(it.style) }.sumOf { it.weight.toDouble() }.toFloat()
            val no = totalWeight - yes
            // For selected-word-specific attributes such as underline/italic, keep
            // the direct selected estimate if consensus is close.
            return when {
                abs(yes - no) / totalWeight < 0.18f -> selectedValue
                else -> yes >= no
            }
        }

        val bold = weightedBoolean({ it.fontStyle.isBold }, selectedStyle.fontStyle.isBold)
        val italic = weightedBoolean({ it.fontStyle.isItalic }, selectedStyle.fontStyle.isItalic)
        val underline = selectedStyle.fontStyle.isUnderline || weightedBoolean(
            { it.fontStyle.isUnderline },
            selectedStyle.fontStyle.isUnderline
        )

        val sizes = weighted.flatMap { item ->
            // Approximate weighting without floating-point list replication explosion.
            List(max(1, (item.weight * 2f).toInt())) { item.style.fontSizeSp }
        }.sorted()
        val medianSize = sizes.getOrElse(sizes.size / 2) { selectedStyle.fontSizeSp }
        val selectedSize = selectedStyle.fontSizeSp
        val fontSize = (selectedSize * 0.68f + medianSize * 0.32f)
            .coerceIn(selectedSize * 0.78f, selectedSize * 1.24f)
            .coerceIn(6f, 72f)

        // Selected word color is the best source of truth. A small neighbor blend
        // stabilizes noisy scans without washing out colored text.
        val neighborColor = weightedColor(weighted)
        val textColor = blendColor(selectedStyle.textColor, neighborColor, 0.18f)
        val backgroundColor = selectedStyle.backgroundColor

        val contextRect = buildContextRect(element, line)

        val sampleBonus = min(0.18f, (weighted.size - 1).coerceAtLeast(0) * 0.025f)
        val confidence = (0.58f + familyConfidence * 0.26f + sampleBonus).coerceIn(0f, 0.98f)

        return TypographyMatch(
            fontFamilyType = family,
            isBold = bold,
            isItalic = italic,
            isUnderline = underline,
            fontSizeSp = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor,
            confidence = confidence,
            contextNormalizedRect = contextRect
        )
    }

    private fun buildContextRect(element: EditorTextElement, line: OcrTextLine?): RectF {
        val source = line?.normalizedRect ?: element.normalizedRect
        val selected = element.normalizedRect

        val widthPad = max(selected.width() * 1.8f, source.width() * 0.08f)
        val heightPad = max(selected.height() * 1.7f, source.height() * 0.45f)

        return RectF(
            (min(source.left, selected.left) - widthPad).coerceIn(0f, 1f),
            (min(source.top, selected.top) - heightPad).coerceIn(0f, 1f),
            (max(source.right, selected.right) + widthPad).coerceIn(0f, 1f),
            (max(source.bottom, selected.bottom) + heightPad).coerceIn(0f, 1f)
        )
    }

    private fun normalizedToPixelRect(rect: RectF, width: Int, height: Int): Rect {
        val left = (rect.left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, max(0, width - 1))
        val top = (rect.top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, max(0, height - 1))
        val right = (rect.right.coerceIn(0f, 1f) * width).toInt().coerceIn(left + 1, width)
        val bottom = (rect.bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun normalizeFamily(value: String): String = when (value.uppercase()) {
        "SERIF" -> "SERIF"
        "MONO", "MONOSPACE" -> "MONO"
        "SANS_CONDENSED", "CONDENSED" -> "SANS_CONDENSED"
        "SANS_MEDIUM", "MEDIUM" -> "SANS_MEDIUM"
        "SANS_LIGHT", "LIGHT" -> "SANS_LIGHT"
        "BENGALI", "BANGLA" -> "BENGALI"
        else -> "SANS"
    }

    private fun weightedColor(items: List<WeightedStyle>): Color {
        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        var total = 0f
        items.forEach { item ->
            val c = item.style.textColor
            r += c.red * item.weight
            g += c.green * item.weight
            b += c.blue * item.weight
            a += c.alpha * item.weight
            total += item.weight
        }
        if (total <= 0f) return Color.Black
        return Color(
            (r / total).coerceIn(0f, 1f),
            (g / total).coerceIn(0f, 1f),
            (b / total).coerceIn(0f, 1f),
            (a / total).coerceIn(0f, 1f)
        )
    }

    private fun blendColor(a: Color, b: Color, ratio: Float): Color {
        val t = ratio.coerceIn(0f, 1f)
        return Color(
            a.red * (1f - t) + b.red * t,
            a.green * (1f - t) + b.green * t,
            a.blue * (1f - t) + b.blue * t,
            a.alpha * (1f - t) + b.alpha * t
        )
    }
}
