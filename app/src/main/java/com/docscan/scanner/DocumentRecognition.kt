package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result data class for DocumentRecognition analysis.
 */
data class DocumentRecognitionResult(
    val isDocument: Boolean,
    val confidence: Float,
    val region: Rect? = null,
    val meanLuminance: Double = 0.0,
    val contrastRatio: Double = 0.0,
    val dominantContourArea: Double = 0.0
)

/**
 * High-speed Document Presence & Foreground Quality Analyzer.
 *
 * Answers the essential pre-filtering question:
 * "Is there a plausible document / screen / rectangular paper in this camera frame?"
 *
 * Responsibilities:
 * 1. Fast foreground/background separation and luminance dynamics
 * 2. Area coverage and rectangular candidate scoring
 * 3. Contrast, lighting, and shadow level evaluation
 * 4. Overall document presence confidence calculation (0.0 .. 1.0)
 */
object DocumentRecognition {

    private const val TAG = "DocumentRecognition"

    /**
     * Evaluates whether a scaled grayscale Mat contains a document candidate.
     */
    fun evaluate(
        grayMat: Mat,
        config: EdgeDetectionConfig = EdgeDetectionConfig.Default
    ): DocumentRecognitionResult {
        val w = grayMat.width()
        val h = grayMat.height()
        val totalArea = (w * h).toDouble()

        if (w < 30 || h < 30 || totalArea < 900.0) {
            return DocumentRecognitionResult(isDocument = false, confidence = 0f)
        }

        val meanScalar = Core.mean(grayMat)
        val meanLum = meanScalar.`val`[0]

        // MinMax luminance for dynamic contrast range
        val minMax = Core.minMaxLoc(grayMat)
        val dynamicRange = (minMax.maxVal - minMax.minVal).coerceAtLeast(1.0)
        val contrastScore = (dynamicRange / 255.0).toFloat().coerceIn(0f, 1f)

        // Downsampled rough binary threshold to detect bounding blob
        val binary = Mat()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Imgproc.threshold(
                grayMat,
                binary,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )

            // Morphological opening to detach tiny text characters from outer boundary
            val kOpen = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kOpen)
            kOpen.release()

            Imgproc.findContours(
                binary,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestArea = 0.0
            var bestRect: Rect? = null
            var bestRectangularity = 0f

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < totalArea * 0.04) continue

                val rect = Imgproc.boundingRect(contour)
                val rectArea = (rect.width * rect.height).toDouble()
                val rectangularity = if (rectArea > 0) (area / rectArea).toFloat() else 0f

                if (area > bestArea) {
                    bestArea = area
                    bestRect = rect
                    bestRectangularity = rectangularity
                }
            }

            val areaFraction = (bestArea / totalArea).toFloat().coerceIn(0f, 1f)

            // Plausibility scoring
            val areaScore = when {
                areaFraction in 0.08f..0.94f -> 1.0f
                areaFraction in 0.04f..0.98f -> 0.7f
                else -> 0.2f
            }

            val shapeScore = bestRectangularity.coerceIn(0f, 1f)
            val lightingScore = if (meanLum in 25.0..240.0) 1.0f else 0.5f

            // Modern scoring: prioritise area + rectangularity the way ML Kit /
            // Adobe Scan do — contrast is secondary so low-light paper still passes.
            val confidence = (
                0.38f * areaScore +
                0.32f * shapeScore +
                0.18f * contrastScore +
                0.12f * lightingScore
            ).coerceIn(0f, 1f)
            val isDoc = confidence >= 0.38f && areaFraction >= 0.04f

            return DocumentRecognitionResult(
                isDocument = isDoc,
                confidence = confidence,
                region = bestRect,
                meanLuminance = meanLum,
                contrastRatio = dynamicRange / 255.0,
                dominantContourArea = bestArea
            )
        } catch (e: Exception) {
            return DocumentRecognitionResult(isDocument = false, confidence = 0f)
        } finally {
            binary.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }
}
