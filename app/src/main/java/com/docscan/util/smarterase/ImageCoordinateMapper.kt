package com.docscan.util.smarterase

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision coordinate mapper for zoom, pan, and touch interaction
 * between screen viewport, working preview bitmap, and full-resolution source bitmap.
 */
object ImageCoordinateMapper {

    /**
     * Calculates the fitted aspect-ratio frame of the bitmap within the canvas container.
     */
    fun calculateFitFrame(
        containerSize: Size,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Rect {
        if (containerSize.width <= 0 || containerSize.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return Rect.Zero
        }

        val containerAspect = containerSize.width / containerSize.height
        val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()

        val fitWidth: Float
        val fitHeight: Float

        if (bitmapAspect > containerAspect) {
            fitWidth = containerSize.width
            fitHeight = fitWidth / bitmapAspect
        } else {
            fitHeight = containerSize.height
            fitWidth = fitHeight * bitmapAspect
        }

        val left = (containerSize.width - fitWidth) / 2f
        val top = (containerSize.height - fitHeight) / 2f

        return Rect(left, top, left + fitWidth, top + fitHeight)
    }

    /**
     * Maps screen touch coordinate to normalized [0, 1] bitmap coordinates,
     * taking into account current zoom scale and pan offset.
     */
    fun screenToNormalized(
        touchPoint: Offset,
        fitFrame: Rect,
        zoomScale: Float,
        panOffset: Offset
    ): Offset {
        val centerX = fitFrame.left + fitFrame.width / 2f
        val centerY = fitFrame.top + fitFrame.height / 2f

        // Reverse pan
        val unpannedX = touchPoint.x - panOffset.x
        val unpannedY = touchPoint.y - panOffset.y

        // Reverse zoom from center
        val unscaledX = centerX + (unpannedX - centerX) / zoomScale
        val unscaledY = centerY + (unpannedY - centerY) / zoomScale

        // Normalize within fit frame
        val normX = ((unscaledX - fitFrame.left) / fitFrame.width).coerceIn(0f, 1f)
        val normY = ((unscaledY - fitFrame.top) / fitFrame.height).coerceIn(0f, 1f)

        return Offset(normX, normY)
    }

    /**
     * Maps normalized [0, 1] coordinate back to screen canvas coordinate.
     */
    fun normalizedToScreen(
        normalizedPoint: Offset,
        fitFrame: Rect,
        zoomScale: Float,
        panOffset: Offset
    ): Offset {
        val centerX = fitFrame.left + fitFrame.width / 2f
        val centerY = fitFrame.top + fitFrame.height / 2f

        val unscaledX = fitFrame.left + normalizedPoint.x * fitFrame.width
        val unscaledY = fitFrame.top + normalizedPoint.y * fitFrame.height

        val scaledX = centerX + (unscaledX - centerX) * zoomScale
        val scaledY = centerY + (unscaledY - centerY) * zoomScale

        return Offset(scaledX + panOffset.x, scaledY + panOffset.y)
    }

    /**
     * Calculates the brush radius on screen given a normalized brush radius and zoom scale.
     */
    fun calculateScreenBrushRadius(
        brushSizeDp: Float,
        fitFrame: Rect,
        zoomScale: Float,
        density: Float
    ): Float {
        val basePx = brushSizeDp * density
        return basePx * zoomScale
    }
}
