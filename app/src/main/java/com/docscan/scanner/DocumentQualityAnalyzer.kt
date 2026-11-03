package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Image Quality and Camera Environment Analyzer.
 * Measures focus sharpness using Laplacian variance, evaluates exposure/illumination,
 * checks document bounding constraints, and generates real-time contextual user guidance.
 */
class DocumentQualityAnalyzer(
    val minSharpnessThreshold: Double = 52.0, // Laplacian variance threshold for sharp text
    val optimalAreaMin: Double = 0.16,
    val optimalAreaMax: Double = 0.94
) {

    data class QualityReport(
        val sharpnessVariance: Double,
        val isSharp: Boolean,
        val meanLuminance: Double,
        val isWellExposed: Boolean,
        val isTooDark: Boolean,
        val isOverexposed: Boolean,
        val guidanceMessage: String,
        val isReadyForAutoCapture: Boolean
    )

    /**
     * Evaluates image sharpness and exposure, returning comprehensive quality report.
     */
    fun analyzeQuality(
        grayMat: Mat,
        detectionResult: DocumentDetectionResult?,
        cameraDisplacement: Double = 0.0
    ): QualityReport {
        // 1. Sharpness / Focus Analysis via Laplacian Variance
        val laplacian = Mat()
        val meanMat = MatOfDouble()
        val stdDevMat = MatOfDouble()

        var variance = 0.0
        try {
            Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, meanMat, stdDevMat)
            val stdDev = stdDevMat.get(0, 0)?.get(0) ?: 0.0
            variance = stdDev * stdDev
        } catch (e: Exception) {
            variance = 50.0
        } finally {
            laplacian.release()
            meanMat.release()
            stdDevMat.release()
        }

        val isSharp = variance >= minSharpnessThreshold

        // 2. Exposure Analysis
        val meanScalar = Core.mean(grayMat)
        val meanLum = meanScalar.`val`[0]
        val isTooDark = meanLum < 38.0
        val isOverexposed = meanLum > 230.0
        val isWellExposed = !isTooDark && !isOverexposed

        // 3. Contextual Guidance Generation
        val guidance = when {
            detectionResult == null || !detectionResult.isValid || detectionResult.confidence < 0.35 -> {
                "No document detected"
            }
            isTooDark -> {
                "Improve lighting"
            }
            isOverexposed -> {
                "Reduce glare / lighting"
            }
            cameraDisplacement > 14.0 || (!isSharp && variance < 40.0) -> {
                "Hold steady"
            }
            detectionResult.areaScore < optimalAreaMin -> {
                "Move closer"
            }
            detectionResult.areaScore > optimalAreaMax -> {
                "Move farther away"
            }
            !detectionResult.isStable -> {
                "Document detected"
            }
            isSharp && isWellExposed && detectionResult.isStable && detectionResult.confidence >= 0.75 -> {
                "Ready to scan"
            }
            else -> {
                "Hold steady"
            }
        }

        val readyForAutoCapture = isSharp &&
                isWellExposed &&
                cameraDisplacement <= 8.0 &&
                (detectionResult?.isStable == true) &&
                (detectionResult?.confidence ?: 0.0) >= 0.82 &&
                (detectionResult?.areaScore ?: 0.0) >= optimalAreaMin

        return QualityReport(
            sharpnessVariance = variance,
            isSharp = isSharp,
            meanLuminance = meanLum,
            isWellExposed = isWellExposed,
            isTooDark = isTooDark,
            isOverexposed = isOverexposed,
            guidanceMessage = guidance,
            isReadyForAutoCapture = readyForAutoCapture
        )
    }
}
