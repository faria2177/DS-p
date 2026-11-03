package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/**
 * Text Layout & Rendering Engine for Document Replacement.
 * Renders single or multi-line edited text accurately within original or expanded bounding boxes.
 */
object DocumentTextRenderer {

    /**
     * Renders a list of edit operations onto a Canvas representing the document bitmap.
     */
    fun renderOperationsOnCanvas(
        canvas: Canvas,
        operations: List<EditTextOperation>,
        bitmapWidth: Float,
        bitmapHeight: Float
    ) {
        for (op in operations) {
            if (op.isDeleted || op.newText.isBlank()) continue
            renderSingleOperation(canvas, op, bitmapWidth, bitmapHeight)
        }
    }

    /**
     * Directly renders an edit operation onto a copy of the given bitmap,
     * returning the newly composited Bitmap.
     */
    fun renderOperationOnBitmap(
        source: Bitmap,
        op: EditTextOperation
    ): Bitmap {
        if (op.isDeleted || op.newText.isBlank()) return source
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        renderSingleOperation(canvas, op, output.width.toFloat(), output.height.toFloat())
        return output
    }

    fun renderSingleOperation(
        canvas: Canvas,
        op: EditTextOperation,
        bitmapW: Float,
        bitmapH: Float
    ) {
        val rect = op.targetNormalizedRect
        val left = rect.left * bitmapW
        val top = rect.top * bitmapH
        val right = rect.right * bitmapW
        val bottom = rect.bottom * bitmapH
        val boxWidth = (right - left).coerceAtLeast(10f)
        val boxHeight = (bottom - top).coerceAtLeast(10f)

        val baseTypeface = when (op.fontFamilyType.uppercase()) {
            "SERIF" -> Typeface.create("serif", Typeface.NORMAL)
            "MONO", "MONOSPACE" -> Typeface.create("monospace", Typeface.NORMAL)
            "SANS_CONDENSED", "CONDENSED" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "SANS_MEDIUM", "MEDIUM" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "SANS_LIGHT", "LIGHT" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "BENGALI", "BANGLA" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "SANS", "DEFAULT" -> Typeface.create("sans-serif", Typeface.NORMAL)
            else -> Typeface.create(op.fontFamilyType, Typeface.NORMAL)
        }

        val style = when {
            op.isBold && op.isItalic -> Typeface.BOLD_ITALIC
            op.isBold -> Typeface.BOLD
            op.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val typeface = Typeface.create(baseTypeface, style)

        val textPaint = TextPaint(
            Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
        ).apply {
            color = op.textColor.toArgb()
            this.typeface = typeface
            isUnderlineText = op.isUnderline
        }

        val text = op.newText
        val isSingleLine = !text.contains("\n")

        // Reconstruct the original glyph height from the locally detected style.
        // TextStyleEstimator normalises size against a 400dp page width, so convert it
        // back to source pixels here. Falling back to the OCR box height keeps older
        // cached operations compatible.
        val detectedGlyphHeightPx = (op.fontSizeSp * bitmapW / 400f / 0.86f)
            .coerceIn(boxHeight * 0.42f, boxHeight * 1.02f)
        val heightMatchedPx = fitTextHeight(textPaint, detectedGlyphHeightPx)
        textPaint.textSize = heightMatchedPx
        textPaint.textScaleX = 1f

        if (isSingleLine) {
            // Calibrate horizontal metrics against the ORIGINAL word/line. This preserves
            // the visual width/slant character of the scanned font much better than sizing
            // the replacement text in isolation.
            val original = op.originalText.ifBlank { text }
            val originalMeasured = textPaint.measureText(original).coerceAtLeast(1f)
            val originalFillRatio = if (op.originalText.isNotBlank()) 0.96f else 0.90f
            val originalMetricScale = (boxWidth * originalFillRatio / originalMeasured)
                .coerceIn(0.72f, 1.22f)
            textPaint.textScaleX = originalMetricScale

            val replacementMeasured = textPaint.measureText(text).coerceAtLeast(1f)
            if (replacementMeasured > boxWidth * 0.985f) {
                val requiredScale = (boxWidth * 0.985f) / replacementMeasured
                val nextScaleX = (textPaint.textScaleX * requiredScale).coerceAtLeast(0.70f)
                textPaint.textScaleX = nextScaleX

                val afterScale = replacementMeasured * (nextScaleX / originalMetricScale.coerceAtLeast(0.01f))
                if (afterScale > boxWidth * 0.985f) {
                    textPaint.textSize = (heightMatchedPx * (boxWidth * 0.985f / afterScale))
                        .coerceAtLeast(4f)
                }
            }
        } else {
            textPaint.textSize = fitMultilineTextSize(textPaint, text, boxWidth, boxHeight)
        }

        if (op.backgroundColor != Color.Transparent) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = op.backgroundColor.toArgb()
                this.style = Paint.Style.FILL
            }
            canvas.drawRect(RectF(left, top, right, bottom), bgPaint)
        }

        if (isSingleLine) {
            // A single edited OCR line should never wrap because of a 1px measurement mismatch.
            // Paint draws glyphs at a real baseline, unlike StaticLayout whose line box/padding
            // can shift scanned text and make the replacement appear to use another size.
            val x = when (op.alignment) {
                "CENTER" -> left + boxWidth / 2f
                "RIGHT" -> right
                else -> left
            }
            textPaint.textAlign = when (op.alignment) {
                "CENTER" -> Paint.Align.CENTER
                "RIGHT" -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
            val metrics = textPaint.fontMetrics
            val baseline = top + (boxHeight - (metrics.ascent + metrics.descent)) / 2f
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            // Glyph positioning uses the existing detected OCR rect and its inferred typeface.
            canvas.drawText(text, x, baseline, textPaint)
            canvas.restore()
        } else {
            val align = when (op.alignment) {
                "CENTER" -> Layout.Alignment.ALIGN_CENTER
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val layoutWidth = max(10, boxWidth.toInt())
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
                .setAlignment(align)
                .setLineSpacing(0f, 1.04f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.translate(left, top)
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun fitTextHeight(paint: TextPaint, targetHeight: Float): Float {
        var low = 4f
        var high = max(6f, targetHeight * 2f)
        var best = low
        repeat(16) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val fm = paint.fontMetrics
            val height = fm.descent - fm.ascent
            if (height <= targetHeight) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    private fun fitMultilineTextSize(
        paint: TextPaint,
        text: String,
        boxWidth: Float,
        boxHeight: Float
    ): Float {
        var low = 4f
        var high = boxHeight.coerceAtLeast(low + 1f)
        var best = low
        repeat(15) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            paint.textScaleX = 1f
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, boxWidth.toInt()))
                .setLineSpacing(0f, 1.04f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            if (layout.height <= boxHeight) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }
}
