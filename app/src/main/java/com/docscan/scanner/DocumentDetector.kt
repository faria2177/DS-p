package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * High-Level Document Detector Interface.
 */
interface DocumentDetector {
    fun detectFromMat(rgbaMat: Mat, rotationDegrees: Int = 0): DocumentDetectionResult
    fun detectFromBitmap(bitmap: Bitmap): DocumentDetectionResult
    fun reset()
    fun release()
}

/**
 * Production-ready Document Detector coordinating the boundary detection pipeline,
 * temporal stabilization, tracking state machine, and quality analysis.
 */
class DefaultDocumentDetector(
    val maxProcessingDimension: Int = 500
) : DocumentDetector {

    private val boundaryDetector = DocumentBoundaryDetector()
    private val stabilityAnalyzer = DocumentStabilityAnalyzer()
    private val tracker = DocumentTracker()
    private val qualityAnalyzer = DocumentQualityAnalyzer()

    // Reusable Mat buffers for scaling and rotation
    private val scaledMat = Mat()
    private val rotatedMat = Mat()

    override fun detectFromBitmap(bitmap: Bitmap): DocumentDetectionResult {
        val mat = Mat()
        return try {
            Utils.bitmapToMat(bitmap, mat)
            detectFromMat(mat, rotationDegrees = 0)
        } finally {
            mat.release()
        }
    }

    override fun detectFromMat(rgbaMat: Mat, rotationDegrees: Int): DocumentDetectionResult {
        val origW = rgbaMat.width()
        val origH = rgbaMat.height()
        if (origW < 32 || origH < 32) return DocumentDetectionResult.EMPTY

        // 1. Downscale to processing resolution for speed & noise suppression
        val scale = computeScale(origW, origH, maxProcessingDimension)
        val targetW = (origW * scale).toInt().coerceAtLeast(1)
        val targetH = (origH * scale).toInt().coerceAtLeast(1)

        Imgproc.resize(rgbaMat, scaledMat, Size(targetW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

        // 2. Execute boundary detection pipeline on scaled frame.
        // IMPORTANT: use the non-mutating peek here, never processCorners(...).
        // processCorners(emptyList()) looks harmless but is NOT a read-only
        // peek — an empty list has size != 4, so it hits the "no detection"
        // branch and calls reset(), wiping consecutiveStableFrames and
        // smoothedCorners right before the real processCorners(rawResult.corners)
        // call below runs. That silently forced every single frame down the
        // "first frame ever seen" path: isStable was always false, the EMA
        // smoothing loop never actually ran, and the tracker below could never
        // reach TrackingState.STABLE — matching the "slow/failed alignment"
        // symptom exactly.
        val rawResult = boundaryDetector.detectBoundary(
            scaledMat,
            stabilityScoreBonus = stabilityAnalyzer.currentStabilityScore()
        )

        // 3. Temporal stabilization filter
        val stability = stabilityAnalyzer.processCorners(rawResult.corners)

        // 4. Update Document Tracker (state machine + grace period)
        val stabilizedCorners = if (stability.smoothedCorners.size == 4) stability.smoothedCorners else rawResult.corners
        val detectionWithStability = rawResult.copy(
            corners = stabilizedCorners,
            isStable = stability.isStable
        )

        val tracked = tracker.update(detectionWithStability, stability.isStable)

        // 5. Image Quality & Contextual Guidance Analysis
        val quality = qualityAnalyzer.analyzeQuality(
            grayMat = scaledMat,
            detectionResult = detectionWithStability,
            cameraDisplacement = stability.displacement
        )

        // 6. Rescale corners back to input image coordinate space
        val finalCorners = if (tracked.corners.size == 4) {
            tracked.corners.map { p ->
                Point(
                    (p.x / scale).coerceIn(0.0, origW.toDouble() - 1.0),
                    (p.y / scale).coerceIn(0.0, origH.toDouble() - 1.0)
                )
            }
        } else {
            emptyList()
        }

        return detectionWithStability.copy(
            corners = finalCorners,
            confidence = tracked.confidence,
            isStable = tracked.isStable,
            isValid = tracked.corners.size == 4 && tracked.confidence >= 0.44,
            blurScore = quality.sharpnessVariance,
            isSharp = quality.isSharp,
            guidanceMessage = quality.guidanceMessage,
            stabilityScore = stability.stabilityScore
        )
    }

    override fun reset() {
        stabilityAnalyzer.reset()
        tracker.reset()
    }

    override fun release() {
        boundaryDetector.release()
        scaledMat.release()
        rotatedMat.release()
    }

    private fun computeScale(w: Int, h: Int, maxDim: Int): Double {
        val longSide = max(w, h).toDouble()
        return if (longSide <= maxDim) 1.0 else maxDim.toDouble() / longSide
    }
}
