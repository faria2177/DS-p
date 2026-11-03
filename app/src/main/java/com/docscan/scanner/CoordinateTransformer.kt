package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.Matrix
import android.graphics.PointF
import org.opencv.core.Point
import kotlin.math.max

/**
 * High-Precision Coordinate Transformer across Camera Analysis,
 * Compose/Android Viewfinder Preview, and High-Resolution ImageCapture spaces.
 * Supports 0°, 90°, 180°, and 270° sensor rotations with FILL_CENTER scaling.
 */
object CoordinateTransformer {

    /**
     * Maps a coordinate from raw Analysis Frame space (with sensor rotation)
     * to a canonical normalized [0.0 .. 1.0, 0.0 .. 1.0] upright space.
     */
    fun analysisToNormalized(
        pt: Point,
        analysisWidth: Int,
        analysisHeight: Int,
        rotationDegrees: Int
    ): PointF {
        val w = analysisWidth.toFloat().coerceAtLeast(1f)
        val h = analysisHeight.toFloat().coerceAtLeast(1f)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()

        return when ((rotationDegrees % 360 + 360) % 360) {
            90 -> {
                // (x, y) in analysis buffer -> rotated 90 deg clockwise
                // normalized X = y / h, normalized Y = (w - x) / w
                PointF((y / h).coerceIn(0f, 1f), ((w - x) / w).coerceIn(0f, 1f))
            }
            180 -> {
                PointF(((w - x) / w).coerceIn(0f, 1f), ((h - y) / h).coerceIn(0f, 1f))
            }
            270 -> {
                PointF(((h - y) / h).coerceIn(0f, 1f), (x / w).coerceIn(0f, 1f))
            }
            else -> { // 0
                PointF((x / w).coerceIn(0f, 1f), (y / h).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Maps a normalized upright coordinate [0..1] to the Compose/Android Viewfinder Preview,
     * accounting for PreviewView FILL_CENTER center-crop scaling.
     */
    fun normalizedToPreview(
        normPt: PointF,
        previewWidth: Float,
        previewHeight: Float,
        sensorAspect: Float // Aspect ratio of upright camera image (e.g. 4/3 or 3/4)
    ): PointF {
        if (previewWidth <= 0f || previewHeight <= 0f) return PointF(0f, 0f)

        val viewAspect = previewWidth / previewHeight
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (viewAspect > sensorAspect) {
            // Viewfinder is wider than camera frame: scales by width, crops top & bottom
            scale = previewWidth
            val scaledHeight = previewWidth / sensorAspect
            offsetX = 0f
            offsetY = (previewHeight - scaledHeight) / 2f
            return PointF(
                normPt.x * scale + offsetX,
                normPt.y * scaledHeight + offsetY
            )
        } else {
            // Viewfinder is taller than camera frame: scales by height, crops sides
            scale = previewHeight
            val scaledWidth = previewHeight * sensorAspect
            offsetX = (previewWidth - scaledWidth) / 2f
            offsetY = 0f
            return PointF(
                normPt.x * scaledWidth + offsetX,
                normPt.y * scale + offsetY
            )
        }
    }

    /**
     * Converts a list of OpenCV analysis points directly to Preview coordinates.
     */
    fun transformQuadToPreview(
        analysisCorners: List<Point>,
        analysisWidth: Int,
        analysisHeight: Int,
        rotationDegrees: Int,
        previewWidth: Float,
        previewHeight: Float
    ): List<PointF> {
        if (analysisCorners.isEmpty()) return emptyList()

        // Compute upright sensor aspect ratio
        val uprightSensorWidth = if (rotationDegrees == 90 || rotationDegrees == 270) analysisHeight else analysisWidth
        val uprightSensorHeight = if (rotationDegrees == 90 || rotationDegrees == 270) analysisWidth else analysisHeight
        val sensorAspect = uprightSensorWidth.toFloat() / uprightSensorHeight.toFloat()

        return analysisCorners.map { pt ->
            val norm = analysisToNormalized(pt, analysisWidth, analysisHeight, rotationDegrees)
            normalizedToPreview(norm, previewWidth, previewHeight, sensorAspect)
        }
    }

    /**
     * Maps normalized coordinates to full-resolution capture image space (OpenCV Point)
     */
    fun normalizedToCapture(
        normPt: PointF,
        captureWidth: Int,
        captureHeight: Int
    ): Point {
        val cx = (normPt.x * captureWidth).toDouble().coerceIn(0.0, captureWidth.toDouble() - 1.0)
        val cy = (normPt.y * captureHeight).toDouble().coerceIn(0.0, captureHeight.toDouble() - 1.0)
        return Point(cx, cy)
    }

    /**
     * Converts analysis corners to full-resolution capture space
     */
    fun transformQuadToCapture(
        analysisCorners: List<Point>,
        analysisWidth: Int,
        analysisHeight: Int,
        rotationDegrees: Int,
        captureWidth: Int,
        captureHeight: Int
    ): List<Point> {
        return analysisCorners.map { pt ->
            val norm = analysisToNormalized(pt, analysisWidth, analysisHeight, rotationDegrees)
            normalizedToCapture(norm, captureWidth, captureHeight)
        }
    }

    /**
     * Inverts a preview coordinate back to normalized space [0..1]
     */
    fun previewToNormalized(
        previewPt: PointF,
        previewWidth: Float,
        previewHeight: Float,
        sensorAspect: Float
    ): PointF {
        val viewAspect = previewWidth / previewHeight
        return if (viewAspect > sensorAspect) {
            val scaledHeight = previewWidth / sensorAspect
            val offsetY = (previewHeight - scaledHeight) / 2f
            PointF(
                (previewPt.x / previewWidth).coerceIn(0f, 1f),
                ((previewPt.y - offsetY) / scaledHeight).coerceIn(0f, 1f)
            )
        } else {
            val scaledWidth = previewHeight * sensorAspect
            val offsetX = (previewWidth - scaledWidth) / 2f
            PointF(
                ((previewPt.x - offsetX) / scaledWidth).coerceIn(0f, 1f),
                (previewPt.y / previewHeight).coerceIn(0f, 1f)
            )
        }
    }
}
