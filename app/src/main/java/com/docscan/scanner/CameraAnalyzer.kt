package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-Performance Non-blocking CameraX Image Analyzer.
 * Extracts frames using STRATEGY_KEEP_ONLY_LATEST, rate-limits to optimal 15-20 FPS,
 * converts YUV buffers to OpenCV Mat without heap allocations, and evaluates auto-capture.
 */
class CameraAnalyzer(
    private val onDetectionResult: (
        result: DocumentDetectionResult,
        analysisWidth: Int,
        analysisHeight: Int,
        rotationDegrees: Int,
        autoCaptureProgress: Float,
        shouldAutoCapture: Boolean
    ) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = DefaultDocumentDetector(maxProcessingDimension = 500)
    private val autoCaptureController = AutoCaptureController()
    private val qualityAnalyzer = DocumentQualityAnalyzer()

    var isAutoCaptureEnabled: Boolean = false

    // Rate-limiting: ~60ms between detections (~16 FPS) to prevent device overheating
    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 35L
    private val isBusy = AtomicBoolean(false)

    // Reusable Mat buffers
    private var yMat: Mat? = null
    private var rgbaMat: Mat? = null
    private var yBuffer: ByteArray? = null

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()

        // Skip frame if busy or if frame rate limit hasn't elapsed
        if (now - lastAnalyzedTimestamp < frameIntervalMs || !isBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            val width = imageProxy.width
            val height = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Extract Y-plane (luminance/grayscale) directly for lightning-fast analysis
            val planes = imageProxy.planes
            if (planes.isEmpty()) {
                return
            }

            val buffer: ByteBuffer = planes[0].buffer
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride

            // Allocate or reuse direct buffer
            val bufferSize = width * height
            if (yBuffer == null || yBuffer!!.size != bufferSize) {
                yBuffer = ByteArray(bufferSize)
            }

            if (rowStride == width && pixelStride == 1) {
                buffer.rewind()
                buffer.get(yBuffer!!, 0, bufferSize)
            } else {
                // Respect both rowStride and pixelStride. Some devices pad the Y plane.
                // Reading it as a flat array shifts rows and creates false diagonal edges.
                val src = buffer.duplicate()
                val out = yBuffer!!
                var dest = 0
                for (row in 0 until height) {
                    val rowStart = row * rowStride
                    for (col in 0 until width) {
                        val index = rowStart + col * pixelStride
                        out[dest++] = if (index < src.limit()) src.get(index) else 0
                    }
                }
            }

            if (yMat == null || yMat!!.rows() != height || yMat!!.cols() != width) {
                yMat?.release()
                rgbaMat?.release()
                yMat = Mat(height, width, CvType.CV_8UC1)
                rgbaMat = Mat(height, width, CvType.CV_8UC4)
            }

            yMat!!.put(0, 0, yBuffer!!)
            Imgproc.cvtColor(yMat!!, rgbaMat!!, Imgproc.COLOR_GRAY2RGBA)

            // Run Document Boundary Detection
            val result = detector.detectFromMat(rgbaMat!!, rotationDegrees)

            // Evaluate Auto-Capture
            val quality = qualityAnalyzer.analyzeQuality(
                grayMat = yMat!!,
                detectionResult = result,
                cameraDisplacement = 0.0
            )

            val autoCaptureStatus = autoCaptureController.evaluate(
                detection = result,
                quality = quality,
                isEnabled = isAutoCaptureEnabled
            )

            lastAnalyzedTimestamp = now

            // Deliver detection callback
            onDetectionResult(
                result,
                width,
                height,
                rotationDegrees,
                autoCaptureStatus.progress,
                autoCaptureStatus.shouldCaptureNow
            )

        } catch (e: Exception) {
            // Guard against OpenCV or lifecycle exceptions
        } finally {
            imageProxy.close()
            isBusy.set(false)
        }
    }

    fun reset() {
        detector.reset()
        autoCaptureController.reset()
    }

    fun release() {
        detector.release()
        yMat?.release()
        rgbaMat?.release()
        yMat = null
        rgbaMat = null
        yBuffer = null
    }
}
