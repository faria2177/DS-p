package com.docscan.util.textedit

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Robust Coordinate Transformation System between:
 * Original Bitmap Pixel Coordinates <-> Normalized Coordinates (0..1) <-> Screen/Canvas Coordinates (with Zoom & Pan) <-> Touch Coordinates.
 */
object DocumentCoordinateTransformer {

    /**
     * Calculates the rendered frame rectangle of the bitmap inside the given canvas viewport,
     * maintaining aspect ratio (fitCenter/centerInside).
     */
    fun calculateImageFrame(canvasSize: Size, imgWidth: Int, imgHeight: Int): RectF {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f || imgWidth <= 0 || imgHeight <= 0) {
            return RectF(0f, 0f, max(1f, canvasSize.width), max(1f, canvasSize.height))
        }

        val canvasAspect = canvasSize.width / canvasSize.height
        val imgAspect = imgWidth.toFloat() / imgHeight.toFloat()

        return if (imgAspect > canvasAspect) {
            val drawWidth = canvasSize.width
            val drawHeight = drawWidth / imgAspect
            val top = (canvasSize.height - drawHeight) / 2f
            RectF(0f, top, drawWidth, top + drawHeight)
        } else {
            val drawHeight = canvasSize.height
            val drawWidth = drawHeight * imgAspect
            val left = (canvasSize.width - drawWidth) / 2f
            RectF(left, 0f, left + drawWidth, drawHeight)
        }
    }

    /**
     * Maps a touch point on the screen (considering pan and zoom) back to normalized document coordinates (0..1).
     */
    fun screenTouchToNormalized(
        touchPoint: Offset,
        canvasSize: Size,
        imgWidth: Int,
        imgHeight: Int,
        scale: Float = 1f,
        panOffset: Offset = Offset.Zero
    ): Offset? {
        val baseFrame = calculateImageFrame(canvasSize, imgWidth, imgHeight)

        // Viewport center
        val centerX = canvasSize.width / 2f
        val centerY = canvasSize.height / 2f

        // Reverse zoom and pan transform:
        // touch = center + (basePos - center) * scale + panOffset
        // => basePos = center + (touch - center - panOffset) / scale
        val unscaledX = centerX + (touchPoint.x - centerX - panOffset.x) / scale
        val unscaledY = centerY + (touchPoint.y - centerY - panOffset.y) / scale

        // Check if inside image frame
        if (unscaledX < baseFrame.left || unscaledX > baseFrame.right ||
            unscaledY < baseFrame.top || unscaledY > baseFrame.bottom
        ) {
            // Allow a small generous touch margin around the frame
            val margin = 20f
            if (unscaledX < baseFrame.left - margin || unscaledX > baseFrame.right + margin ||
                unscaledY < baseFrame.top - margin || unscaledY > baseFrame.bottom + margin
            ) {
                return null
            }
        }

        val normX = ((unscaledX - baseFrame.left) / baseFrame.width()).coerceIn(0f, 1f)
        val normY = ((unscaledY - baseFrame.top) / baseFrame.height()).coerceIn(0f, 1f)

        return Offset(normX, normY)
    }

    /**
     * Converts a normalized RectF (0..1) to screen canvas coordinates.
     */
    fun normalizedToCanvasRect(
        normRect: RectF,
        baseFrame: RectF
    ): RectF {
        return RectF(
            baseFrame.left + normRect.left * baseFrame.width(),
            baseFrame.top + normRect.top * baseFrame.height(),
            baseFrame.left + normRect.right * baseFrame.width(),
            baseFrame.top + normRect.bottom * baseFrame.height()
        )
    }

    /**
     * Performs hit-testing to find the most specific text item matching the touched normalized coordinate.
     */
    fun findHitTextItem(
        normTouch: Offset,
        ocrDoc: OcrDocument,
        granularity: TextEditGranularity = TextEditGranularity.LINE
    ): Any? {
        val x = normTouch.x
        val y = normTouch.y

        when (granularity) {
            TextEditGranularity.WORD -> {
                // Search words first
                for (word in ocrDoc.allWords) {
                    if (isPointInsideWithMargin(x, y, word.normalizedRect, margin = 0.005f)) {
                        return word
                    }
                }
            }
            TextEditGranularity.LINE -> {
                // Search lines
                for (line in ocrDoc.allLines) {
                    if (isPointInsideWithMargin(x, y, line.normalizedRect, margin = 0.008f)) {
                        return line
                    }
                }
            }
            TextEditGranularity.BLOCK -> {
                // Search blocks
                for (block in ocrDoc.blocks) {
                    if (isPointInsideWithMargin(x, y, block.normalizedRect, margin = 0.012f)) {
                        return block
                    }
                }
            }
        }

        // Fallback: If nothing was found at the requested granularity, search lines or blocks
        for (line in ocrDoc.allLines) {
            if (isPointInsideWithMargin(x, y, line.normalizedRect, margin = 0.01f)) {
                return line
            }
        }
        for (block in ocrDoc.blocks) {
            if (isPointInsideWithMargin(x, y, block.normalizedRect, margin = 0.015f)) {
                return block
            }
        }

        return null
    }

    private fun isPointInsideWithMargin(x: Float, y: Float, rect: RectF, margin: Float): Boolean {
        return x >= (rect.left - margin) && x <= (rect.right + margin) &&
                y >= (rect.top - margin) && y <= (rect.bottom + margin)
    }
}
