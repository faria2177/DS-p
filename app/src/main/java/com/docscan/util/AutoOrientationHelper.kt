package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Helper to automatically detect orientation of document scans using ML Kit Text Recognition
 * and rotate them to the correct upright position if they were captured sideways or upside down.
 */
object AutoOrientationHelper {
    private const val TAG = "AutoOrientationHelper"
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class OrientationResult(
        val rotatedBitmap: Bitmap,
        val rotationAppliedDegrees: Int, // 0, 90, 180, 270
        val detectedText: String
    )

    /**
     * Analyzes image with ML Kit to detect text angle and orientation.
     * Returns the upright bitmap and the rotation degrees applied.
     */
    suspend fun detectAndCorrectOrientation(source: Bitmap): OrientationResult = withContext(Dispatchers.IO) {
        try {
            // 1. First test current orientation (0 degrees)
            val baseResult = recognize(source, 0)
            val baseText = baseResult.text.trim()

            // If we have distinct text lines, calculate orientation angle from cornerPoints or line angle:
            val lines = baseResult.textBlocks.flatMap { it.lines }
            if (lines.isNotEmpty()) {
                val angles = lines.mapNotNull { line ->
                    val pts = line.cornerPoints
                    if (pts != null && pts.size >= 2) {
                        val dx = (pts[1].x - pts[0].x).toDouble()
                        val dy = (pts[1].y - pts[0].y).toDouble()
                        Math.toDegrees(kotlin.math.atan2(dy, dx)).toFloat()
                    } else {
                        try {
                            line.angle
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (angles.isNotEmpty()) {
                    val avgAngle = angles.average().toFloat()

                    // Check if angle indicates ~90, ~180, ~270 or -90
                    val normalizedAngle = ((avgAngle % 360f) + 360f) % 360f
                    val estimatedRotation = when {
                        normalizedAngle in 45f..135f -> 90
                        normalizedAngle in 135f..225f -> 180
                        normalizedAngle in 225f..315f -> 270
                        else -> 0
                    }

                    if (estimatedRotation != 0) {
                        val rotated = rotateBitmap(source, (360 - estimatedRotation) % 360)
                        val verifyResult = recognize(rotated, 0)
                        if (verifyResult.text.length >= baseText.length * 0.8) {
                            return@withContext OrientationResult(
                                rotatedBitmap = rotated,
                                rotationAppliedDegrees = (360 - estimatedRotation) % 360,
                                detectedText = verifyResult.text.ifBlank { baseText }
                            )
                        }
                    }
                }
            }

            // 2. If base text is very sparse or empty, test 90, 180, 270 degrees to find best upright orientation
            if (baseText.length < 30) {
                var bestRotation = 0
                var maxScore = scoreText(baseResult)
                var bestResult = baseResult

                val testRotations = listOf(90, 180, 270)
                for (deg in testRotations) {
                    val testBmp = rotateBitmap(source, deg)
                    val res = recognize(testBmp, 0)
                    val score = scoreText(res)
                    if (score > maxScore && score >= 15) {
                        maxScore = score
                        bestRotation = deg
                        bestResult = res
                    }
                }

                if (bestRotation != 0) {
                    val finalRotated = rotateBitmap(source, bestRotation)
                    return@withContext OrientationResult(
                        rotatedBitmap = finalRotated,
                        rotationAppliedDegrees = bestRotation,
                        detectedText = bestResult.text.trim()
                    )
                }
            }

            // Already upright
            OrientationResult(
                rotatedBitmap = source,
                rotationAppliedDegrees = 0,
                detectedText = baseText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting orientation: ${e.message}", e)
            OrientationResult(
                rotatedBitmap = source,
                rotationAppliedDegrees = 0,
                detectedText = ""
            )
        }
    }

    private fun scoreText(visionText: Text): Int {
        var score = 0
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val bbox = line.boundingBox ?: continue
                // Prefer wider horizontal lines over tall vertical lines (standard text layout)
                if (bbox.width() > bbox.height()) {
                    score += line.text.length * 2
                } else {
                    score += line.text.length
                }
            }
        }
        return score
    }

    private suspend fun recognize(bitmap: Bitmap, rotationDeg: Int): Text = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, rotationDeg)
        recognizer.process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Recognition failed: ${e.message}")
                continuation.resume(Text("", emptyList<Text.TextBlock>()))
            }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
