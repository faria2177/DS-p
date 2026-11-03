package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.PointF
import android.graphics.RectF
import org.opencv.core.Point
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Standard Detection Pipeline Stages
 */
enum class DetectionStage {
    CONTOUR_QUAD,
    STRONG_EDGES,
    LINE_INTERSECTION,
    EDGE_BASED_QUAD,
    TEXTURE_CONTRAST_FALLBACK,
    TRACKING_PREVIOUS,
    NO_DETECTION
}

/**
 * High-precision Document Detection Result containing four corners,
 * comprehensive quality scores, geometric metrics, and temporal stability state.
 */
data class DocumentDetectionResult(
    val corners: List<Point>, // Clockwise: [0] Top-Left, [1] Top-Right, [2] Bottom-Right, [3] Bottom-Left
    val confidence: Double,   // Overall document confidence (0.0 .. 1.0)
    val isStable: Boolean,     // True if corner displacement remains under threshold for consecutive frames
    val isValid: Boolean,      // True if quad satisfies strict geometric, convexity, and aspect-ratio checks
    val documentBounds: RectF, // Normalized or pixel bounding box
    val perspectiveScore: Double, // Metric indicating parallelism and rectangularity (0.0 .. 1.0)
    val areaScore: Double = 0.0,
    val edgeScore: Double = 0.0,
    val geometryScore: Double = 0.0,
    val cornerScore: Double = 0.0,
    val aspectRatioScore: Double = 0.0,
    val stabilityScore: Double = 0.0,
    val blurScore: Double = 0.0,
    val isSharp: Boolean = true,
    val guidanceMessage: String = "Searching document...",
    val detectionStage: DetectionStage = DetectionStage.NO_DETECTION,
    val timestamp: Long = System.currentTimeMillis()
) {
    val topLeft: Point get() = if (corners.size == 4) corners[0] else Point(0.0, 0.0)
    val topRight: Point get() = if (corners.size == 4) corners[1] else Point(0.0, 0.0)
    val bottomRight: Point get() = if (corners.size == 4) corners[2] else Point(0.0, 0.0)
    val bottomLeft: Point get() = if (corners.size == 4) corners[3] else Point(0.0, 0.0)

    /**
     * Converts OpenCV Points to Android PointF list
     */
    fun toPointFList(): List<PointF> {
        return corners.map { PointF(it.x.toFloat(), it.y.toFloat()) }
    }

    /**
     * Converts to normalized [0.0 .. 1.0] coordinates relative to frame dimensions
     */
    fun toNormalized(frameWidth: Double, frameHeight: Double): List<PointF> {
        if (frameWidth <= 0 || frameHeight <= 0) return toPointFList()
        return corners.map {
            PointF(
                (it.x / frameWidth).coerceIn(0.0, 1.0).toFloat(),
                (it.y / frameHeight).coerceIn(0.0, 1.0).toFloat()
            )
        }
    }

    /**
     * Computes the dynamic unwarped destination dimensions based on detected edge lengths
     */
    fun calculateDestinationSize(): Pair<Int, Int> {
        if (corners.size != 4) return Pair(0, 0)
        val widthTop = hypot(topRight.x - topLeft.x, topRight.y - topLeft.y)
        val widthBottom = hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y)
        val heightLeft = hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y)
        val heightRight = hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y)

        val destWidth = max(widthTop, widthBottom).toInt().coerceAtLeast(100)
        val destHeight = max(heightLeft, heightRight).toInt().coerceAtLeast(100)
        return Pair(destWidth, destHeight)
    }

    companion object {
        val EMPTY = DocumentDetectionResult(
            corners = emptyList(),
            confidence = 0.0,
            isStable = false,
            isValid = false,
            documentBounds = RectF(0f, 0f, 0f, 0f),
            perspectiveScore = 0.0,
            guidanceMessage = "No document detected",
            detectionStage = DetectionStage.NO_DETECTION
        )
    }
}
