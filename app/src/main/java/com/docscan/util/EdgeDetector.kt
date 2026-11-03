package com.docscan.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.docscan.scanner.Corner
import com.docscan.scanner.CoreEdgeDetector
import com.docscan.scanner.DocumentRecognition
import com.docscan.scanner.EdgeDetectionConfig
import com.docscan.scanner.QuadTracker
import com.docscan.scanner.TrackerState
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class DetectionState {
    IDLE,
    SEARCHING_DOCUMENT,
    DOCUMENT_DETECTED,
    DOCUMENT_STABLE,
    CAPTURING,
    PROCESSING
}

/**
 * Stable real-time document edge detector.
 *
 * Active pipeline:
 *
 * ImageProxy
 *   -> Y plane
 *   -> rotation
 *   -> downscale
 *   -> CLAHE
 *   -> Gaussian blur
 *   -> adaptive Canny
 *   -> contour detection
 *   -> multi-epsilon quad detection
 *   -> conditional Scharr fallback
 *   -> conditional adaptive threshold fallback
 *   -> geometry validation
 *   -> edge support scoring
 *   -> confidence scoring
 */
object EdgeDetector {

    private const val TAG = "EdgeDetector"

    private const val TARGET_SAMPLE_W = 360
    private const val TARGET_SAMPLE_H = 480

    // QuadTracker (scanner package) applies its own dual-hysteresis on top of
    // this: it requires >= 0.42 confidence to *lock onto* a fresh quad while
    // searching, but only >= 0.28 to *keep* one it is already tracking. That
    // second, more generous threshold can only ever matter if real corners +
    // their true confidence make it out of this function in the first place —
    // so the gate here must sit at the lower (keep) bound, not the lock bound,
    // or low-confidence dips always get discarded before QuadTracker ever
    // sees them, making the tracker's "keep" leniency dead code in practice.
    private const val MIN_CONFIDENCE = 0.28f

    private val EPSILON_VALUES = floatArrayOf(
        0.012f,
        0.018f,
        0.024f,
        0.030f,
        0.038f
    )

    private data class InternalDetection(
        val corners: List<Offset>,
        val confidence: Float
    )

    /**
     * Main CameraX entry point.
     */
    fun analyzeImageProxy(imageProxy: ImageProxy): DetectionResult {

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val uprightAspect =
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    imageProxy.height.toFloat() /
                        imageProxy.width.toFloat()
                } else {
                    imageProxy.width.toFloat() /
                        imageProxy.height.toFloat()
                }

            if (
                imageProxy.format == ImageFormat.YUV_420_888 &&
                imageProxy.planes.isNotEmpty()
            ) {

                val yPlane = imageProxy.planes[0]

                val result = detectFromYuvMat(
                    yBuffer = yPlane.buffer,
                    srcW = imageProxy.width,
                    srcH = imageProxy.height,
                    rowStride = yPlane.rowStride,
                    pixelStride = yPlane.pixelStride,
                    rotationDegrees = rotationDegrees
                )

                if (
                    result != null &&
                    result.confidence >= MIN_CONFIDENCE &&
                    isRealDocumentDetected(result.corners, uprightAspect)
                ) {
                    return DetectionResult(
                        corners = result.corners,
                        isDocumentDetected = true,
                        frameAspectRatio = uprightAspect,
                        confidence = result.confidence
                    )
                }

                return DetectionResult(
                    corners = defaultCorners(),
                    isDocumentDetected = false,
                    frameAspectRatio = uprightAspect,
                    confidence = result?.confidence ?: 0f
                )
            }

            return analyzeBitmapFallback(
                imageProxy = imageProxy,
                rotationDegrees = rotationDegrees,
                uprightAspect = uprightAspect
            )

        } catch (error: Throwable) {

            Log.e(
                TAG,
                "Edge detection failed",
                error
            )

            return DetectionResult(
                corners = defaultCorners(),
                isDocumentDetected = false,
                confidence = 0f
            )

        } finally {

            try {
                imageProxy.close()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Fallback for non-YUV camera output.
     */
    private fun analyzeBitmapFallback(
        imageProxy: ImageProxy,
        rotationDegrees: Int,
        uprightAspect: Float
    ): DetectionResult {

        var rawBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null

        try {

            rawBitmap = imageProxy.toBitmap()

            rotatedBitmap =
                if (rotationDegrees != 0) {

                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }

                    Bitmap.createBitmap(
                        rawBitmap,
                        0,
                        0,
                        rawBitmap.width,
                        rawBitmap.height,
                        matrix,
                        true
                    )

                } else {
                    rawBitmap
                }

            val corners = detectDocumentCorners(rotatedBitmap)

            val detected = isRealDocumentDetected(corners, uprightAspect)

            return DetectionResult(
                corners = corners,
                isDocumentDetected = detected,
                frameAspectRatio = uprightAspect,
                confidence = if (detected) 0.65f else 0f
            )

        } finally {

            if (
                rotatedBitmap != null &&
                rotatedBitmap !== rawBitmap &&
                !rotatedBitmap.isRecycled
            ) {
                rotatedBitmap.recycle()
            }

            if (
                rawBitmap != null &&
                !rawBitmap.isRecycled
            ) {
                rawBitmap.recycle()
            }
        }
    }

    /**
     * Main OpenCV live detection path.
     */
    private fun detectFromYuvMat(
        yBuffer: ByteBuffer,
        srcW: Int,
        srcH: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): InternalDetection? {

        var rawMat: Mat? = null
        var rotatedMat: Mat? = null
        var scaledMat: Mat? = null
        var claheGray: Mat? = null
        var blurred: Mat? = null
        var cannyEdges: Mat? = null
        var scharrEdges: Mat? = null
        var adaptiveEdges: Mat? = null
        var fusedEdges: Mat? = null
        var morphEdges: Mat? = null

        try {

            val yBytes = copyYPlane(
                buffer = yBuffer,
                width = srcW,
                height = srcH,
                rowStride = rowStride,
                pixelStride = pixelStride
            )

            rawMat = Mat(
                srcH,
                srcW,
                CvType.CV_8UC1
            )

            rawMat.put(
                0,
                0,
                yBytes
            )

            rotatedMat = Mat()

            when (rotationDegrees) {

                90 -> Core.rotate(
                    rawMat,
                    rotatedMat,
                    Core.ROTATE_90_CLOCKWISE
                )

                180 -> Core.rotate(
                    rawMat,
                    rotatedMat,
                    Core.ROTATE_180
                )

                270 -> Core.rotate(
                    rawMat,
                    rotatedMat,
                    Core.ROTATE_90_COUNTERCLOCKWISE
                )

                else -> rawMat.copyTo(rotatedMat)
            }

            val config =
                EdgeDetectionConfig.Default

            val rotatedWidth =
                rotatedMat.width()

            val rotatedHeight =
                rotatedMat.height()

            val targetLongSide =
                config.processingLongSide
                    .coerceIn(600, 760)

            val scale =
                targetLongSide.toDouble() /
                    max(
                        rotatedWidth,
                        rotatedHeight
                    ).toDouble()

            val workingWidth =
                max(
                    1,
                    (rotatedWidth * scale).toInt()
                )

            val workingHeight =
                max(
                    1,
                    (rotatedHeight * scale).toInt()
                )

            scaledMat = Mat()

            Imgproc.resize(
                rotatedMat,
                scaledMat,
                Size(
                    workingWidth.toDouble(),
                    workingHeight.toDouble()
                ),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )

            // --- Ported reference edge-detection core (android_edge_detection_core) ---
            // Try the simple, proven contour-based detector (fixed-threshold
            // Canny + dilation + external contours + approxPolyDP) first. It
            // has no cross-frame state and no multi-channel fusion to get
            // out of sync, so it's a fast sanity-checked first attempt.
            // The heavier multi-channel pipeline below only runs when this
            // can't find a confident 4-point convex quad on its own.
            val coreQuad = CoreEdgeDetector.detectQuad(scaledMat)
            if (coreQuad != null) {
                val normalizedCore = coreQuad.map { p ->
                    Offset(
                        (p.x / workingWidth).toFloat().coerceIn(0f, 1f),
                        (p.y / workingHeight).toFloat().coerceIn(0f, 1f)
                    )
                }
                if (isRealDocumentDetected(normalizedCore, workingWidth.toFloat() / workingHeight.toFloat())) {
                    return InternalDetection(corners = normalizedCore, confidence = 0.72f)
                }
            }

            claheGray = Mat()

            val clahe =
                Imgproc.createCLAHE(
                    config.claheClipLimit,
                    Size(
                        config.claheTileSize.toDouble(),
                        config.claheTileSize.toDouble()
                    )
                )

            try {

                clahe.apply(
                    scaledMat,
                    claheGray
                )

            } finally {

                clahe.collectGarbage()
            }

            blurred = Mat()

            Imgproc.GaussianBlur(
                claheGray,
                blurred,
                Size(5.0, 5.0),
                1.0
            )

            val recognition =
                DocumentRecognition.evaluate(
                    claheGray,
                    config
                )

            val median =
                calculateMedianIntensity(
                    blurred
                )

            val lowerThreshold =
                (
                    median *
                        config.cannyLowFactor +
                        config.cannyLowBias
                )
                    .coerceIn(
                        12.0,
                        100.0
                    )

            val upperThreshold =
                max(
                    lowerThreshold + 25.0,
                    median *
                        config.cannyHighFactor +
                        config.cannyLowBias
                )
                    .coerceIn(
                        45.0,
                        220.0
                    )

            cannyEdges = Mat()

            Imgproc.Canny(
                blurred,
                cannyEdges,
                lowerThreshold,
                upperThreshold,
                3,
                true
            )

            fusedEdges = Mat()

            cannyEdges.copyTo(
                fusedEdges
            )

            morphEdges = closeEdges(
                input = fusedEdges,
                config = config
            )

            var best =
                findBestQuadrilateral(
                    edgeMap = morphEdges,
                    frameWidth = workingWidth,
                    frameHeight = workingHeight,
                    recognitionConfidence =
                        recognition.confidence.toFloat(),
                    config = config
                )

            /*
             * Only run expensive fallback stages
             * when primary Canny contour detection
             * is weak.
             */
            if (
                best == null ||
                best.confidence < 0.56f
            ) {

                scharrEdges = createScharrEdges(
                    blurred = blurred,
                    median = median
                )

                val combined =
                    Mat()

                try {

                    Core.bitwise_or(
                        fusedEdges,
                        scharrEdges,
                        combined
                    )

                    val refined =
                        closeEdges(
                            input = combined,
                            config = config
                        )

                    try {

                        val candidate =
                            findBestQuadrilateral(
                                edgeMap = refined,
                                frameWidth = workingWidth,
                                frameHeight = workingHeight,
                                recognitionConfidence =
                                    recognition.confidence.toFloat(),
                                config = config
                            )

                        best =
                            selectBetterCandidate(
                                best,
                                candidate
                            )

                    } finally {

                        refined.release()
                    }

                } finally {

                    combined.release()
                }
            }

            if (
                best == null ||
                best.confidence < 0.50f
            ) {

                adaptiveEdges =
                    createAdaptiveEdges(
                        blurred
                    )

                val combined =
                    Mat()

                try {

                    Core.bitwise_or(
                        fusedEdges,
                        adaptiveEdges,
                        combined
                    )

                    val refined =
                        closeEdges(
                            input = combined,
                            config = config
                        )

                    try {

                        val candidate =
                            findBestQuadrilateral(
                                edgeMap = refined,
                                frameWidth = workingWidth,
                                frameHeight = workingHeight,
                                recognitionConfidence =
                                    recognition.confidence.toFloat(),
                                config = config
                            )

                        best =
                            selectBetterCandidate(
                                best,
                                candidate
                            )

                    } finally {

                        refined.release()
                    }

                } finally {

                    combined.release()
                }
            }

            return best

        } finally {

            rawMat?.release()
            rotatedMat?.release()
            scaledMat?.release()
            claheGray?.release()
            blurred?.release()
            cannyEdges?.release()
            scharrEdges?.release()
            adaptiveEdges?.release()
            fusedEdges?.release()
            morphEdges?.release()
        }
    }

    /**
     * Copies the luminance plane while respecting
     * rowStride and pixelStride.
     */
    private fun copyYPlane(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {

        val output =
            ByteArray(width * height)

        val limit =
            buffer.limit()

        var index = 0

        for (y in 0 until height) {

            val rowStart =
                y * rowStride

            for (x in 0 until width) {

                val position =
                    rowStart +
                        x * pixelStride

                output[index++] =
                    if (
                        position >= 0 &&
                        position < limit
                    ) {
                        buffer.get(position)
                    } else {
                        0
                    }
            }
        }

        return output
    }

    /**
     * Robust histogram median.
     */
    private fun calculateMedianIntensity(
        image: Mat
    ): Double {

        val histogram =
            IntArray(256)

        val buffer =
            ByteArray(
                image.rows() *
                    image.cols()
            )

        image.get(
            0,
            0,
            buffer
        )

        for (value in buffer) {

            histogram[
                value.toInt() and 0xFF
            ]++
        }

        val middle =
            buffer.size / 2

        var total = 0

        for (i in histogram.indices) {

            total += histogram[i]

            if (total >= middle) {
                return i.toDouble()
            }
        }

        return 128.0
    }

    private fun createScharrEdges(
        blurred: Mat,
        median: Double
    ): Mat {

        val gradX =
            Mat()

        val gradY =
            Mat()

        val magnitude =
            Mat()

        val output =
            Mat()

        try {

            Imgproc.Scharr(
                blurred,
                gradX,
                CvType.CV_32F,
                1,
                0
            )

            Imgproc.Scharr(
                blurred,
                gradY,
                CvType.CV_32F,
                0,
                1
            )

            Core.magnitude(
                gradX,
                gradY,
                magnitude
            )

            Core.convertScaleAbs(
                magnitude,
                output
            )

            val threshold =
                max(
                    22.0,
                    median * 0.22
                )

            Imgproc.threshold(
                output,
                output,
                threshold,
                255.0,
                Imgproc.THRESH_BINARY
            )

            return output

        } finally {

            gradX.release()
            gradY.release()
            magnitude.release()
        }
    }

    private fun createAdaptiveEdges(
        blurred: Mat
    ): Mat {

        val threshold =
            Mat()

        val output =
            Mat()

        var kernel: Mat? = null

        try {

            Imgproc.adaptiveThreshold(
                blurred,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                25,
                7.0
            )

            kernel =
                Imgproc.getStructuringElement(
                    Imgproc.MORPH_RECT,
                    Size(3.0, 3.0)
                )

            Imgproc.morphologyEx(
                threshold,
                output,
                Imgproc.MORPH_GRADIENT,
                kernel
            )

            return output

        } finally {

            threshold.release()
            kernel?.release()
        }
    }

    private fun closeEdges(
        input: Mat,
        config: EdgeDetectionConfig
    ): Mat {

        val output =
            Mat()

        val kernelSize =
            if (
                config.morphCloseSize >= 7
            ) {
                5
            } else {
                3
            }

        val kernel =
            Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(
                    kernelSize.toDouble(),
                    kernelSize.toDouble()
                )
            )

        try {

            Imgproc.morphologyEx(
                input,
                output,
                Imgproc.MORPH_CLOSE,
                kernel,
                Point(-1.0, -1.0),
                min(
                    config.morphCloseIterations,
                    2
                )
            )

            return output

        } finally {

            kernel.release()
        }
    }

    private fun findBestQuadrilateral(
        edgeMap: Mat,
        frameWidth: Int,
        frameHeight: Int,
        recognitionConfidence: Float,
        config: EdgeDetectionConfig
    ): InternalDetection? {

        val contours =
            ArrayList<MatOfPoint>()

        val hierarchy =
            Mat()

        try {

            Imgproc.findContours(
                edgeMap,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            if (contours.isEmpty()) {
                return null
            }

            val frameArea =
                frameWidth.toDouble() *
                    frameHeight.toDouble()

            val sorted =
                contours
                    .sortedByDescending {
                        Imgproc.contourArea(it)
                    }
                    .take(15)

            var best:
                InternalDetection? =
                null

            for (contour in sorted) {

                val contourArea =
                    Imgproc.contourArea(
                        contour
                    )

                val contourAreaRatio =
                    (
                        contourArea /
                            frameArea
                    ).toFloat()

                if (
                    contourAreaRatio <
                        0.035f
                ) {
                    continue
                }

                val contour2f =
                    MatOfPoint2f(
                        *contour.toArray()
                    )

                try {

                    val perimeter =
                        Imgproc.arcLength(
                            contour2f,
                            true
                        )

                    for (
                        epsilonRatio
                        in EPSILON_VALUES
                    ) {

                        val approx =
                            MatOfPoint2f()

                        try {

                            Imgproc.approxPolyDP(
                                contour2f,
                                approx,
                                epsilonRatio *
                                    perimeter,
                                true
                            )

                            if (
                                approx.total() != 4L
                            ) {
                                continue
                            }

                            val ordered =
                                Corner.orderQuad(
                                    approx.toArray()
                                )

                            if (
                                ordered.size != 4
                            ) {
                                continue
                            }

                            val refined =
                                Corner.refineCornersByEdgeIntersection(
                                    quad = ordered,
                                    contour = contour.toArray(),
                                    edgeMap = edgeMap,
                                    pixelSnapSearchRadius =
                                        config.cornerSearchRadiusPx
                                )

                            val normalized =
                                refined.map {
                                    Offset(
                                        (
                                            it.x /
                                                frameWidth
                                        )
                                            .toFloat()
                                            .coerceIn(
                                                0f,
                                                1f
                                            ),

                                        (
                                            it.y /
                                                frameHeight
                                        )
                                            .toFloat()
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                    )
                                }

                            if (
                                !isRealDocumentDetected(
                                    normalized,
                                    frameWidth.toFloat() / frameHeight.toFloat()
                                )
                            ) {
                                continue
                            }

                            val area =
                                calculateQuadArea(
                                    normalized[0],
                                    normalized[1],
                                    normalized[2],
                                    normalized[3]
                                )

                            if (
                                area !in
                                    0.045f..0.96f
                            ) {
                                continue
                            }

                            val geometry =
                                Corner.evaluateGeometry(
                                    refined
                                )

                            if (
                                !geometry.isConvex
                            ) {
                                continue
                            }

                            val edgeSupport =
                                Corner.calculateEdgeSupport(
                                    refined,
                                    edgeMap,
                                    searchRadiusPx = 3
                                )
                                    .toFloat()
                                    .coerceIn(
                                        0f,
                                        1f
                                    )

                            val areaScore =
                                calculateAreaScore(
                                    area
                                )

                            val geometryScore =
                                geometry.score
                                    .toFloat()
                                    .coerceIn(
                                        0f,
                                        1f
                                    )

                            val confidence =
                                (
                                    0.34f *
                                        geometryScore +

                                        0.34f *
                                        edgeSupport +

                                        0.17f *
                                        areaScore +

                                        0.15f *
                                        recognitionConfidence
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                )
                                    .coerceIn(
                                        0f,
                                        1f
                                    )

                            if (
                                confidence <
                                    0.28f
                            ) {
                                continue
                            }

                            val candidate =
                                InternalDetection(
                                    corners =
                                        normalized,
                                    confidence =
                                        confidence
                                )

                            best =
                                selectBetterCandidate(
                                    best,
                                    candidate
                                )

                        } finally {

                            approx.release()
                        }
                    }

                } finally {

                    contour2f.release()
                }
            }

            return best

        } finally {

            hierarchy.release()

            contours.forEach {
                it.release()
            }
        }
    }

    private fun calculateAreaScore(
        area: Float
    ): Float {

        /*
         * Prefer a reasonably large document,
         * but do not force it to fill the screen.
         */

        return when {

            area < 0.08f ->
                area / 0.08f

            area <= 0.72f ->
                1f

            area <= 0.92f ->
                1f -
                    (
                        (area - 0.72f) /
                            0.20f
                    ) * 0.25f

            else ->
                0.70f
        }
            .coerceIn(
                0f,
                1f
            )
    }

    private fun selectBetterCandidate(
        first: InternalDetection?,
        second: InternalDetection?
    ): InternalDetection? {

        if (first == null) {
            return second
        }

        if (second == null) {
            return first
        }

        return if (
            second.confidence >
            first.confidence
        ) {
            second
        } else {
            first
        }
    }

    /**
     * Fixed ID card frame.
     */
    fun idCardFrameCorners():
        List<Offset> {

        val left = 0.08f
        val right = 0.92f
        val top = 0.22f
        val bottom = 0.72f

        return listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(right, bottom),
            Offset(left, bottom)
        )
    }

    /**
     * Still image detection.
     *
     * Existing advanced scanner detector is used
     * for high-resolution still images.
     */
    /**
     * High-accuracy still-image / gallery / PDF-page corner detection.
     *
     * Designed to correctly find INNER documents (e.g. NID card on a white
     * sheet) rather than locking onto the outer paper edge.
     *
     * Pipeline:
     *  1. Production multi-channel detector (RETR_LIST + border-penalised scoring)
     *  2. Dedicated still pass: dual-threshold Canny + RETR_LIST + edge-energy ranking
     *  3. BlueEdgeDetector / CoreEdgeDetector fallbacks
     *  4. Full-frame heuristic only when the page truly fills the image
     */
    fun detectDocumentCorners(
        bitmap: Bitmap
    ): List<Offset> {

        if (bitmap.width < 10 || bitmap.height < 10) {
            return defaultCorners()
        }

        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        // ── Strategy 0: Advanced Multi-Stage DocumentDetector (Subpixel + Intelligent Scoring)
        try {
            val detector = com.docscan.scanner.DefaultDocumentDetector()
            val result = detector.detectFromBitmap(bitmap)
            detector.release()
            if (result.isValid && result.corners.size == 4 && result.confidence >= 0.55) {
                val corners = result.corners.map { p ->
                    Offset(
                        (p.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                        (p.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                    )
                }
                if (isRealDocumentDetected(corners, aspect) && !isNearFullFrame(corners) && !isNearDefault(corners)) {
                    Log.d(TAG, "Still detect: strategy=DocumentDetector confidence=${result.confidence}")
                    return corners
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image DocumentDetector failed", error)
        }

        // ── Strategy 1: dedicated inner-document pass (gallery / card photos)
        // Runs FIRST because production path often latches onto fabric texture
        // or outer white sheets; this pass uses brightness + smoothness scoring.
        try {
            val inner = detectInnerDocument(bitmap)
            if (inner != null && isRealDocumentDetected(inner, aspect) && !isNearDefault(inner)) {
                Log.d(TAG, "Still detect: strategy=inner")
                return inner
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image inner detector failed", error)
        }

        // ── Strategy 2: production multi-channel ──────────────────────────
        try {
            val result = com.docscan.scanner.EdgeDetector.detect(bitmap)
            if (result != null) {
                val q = result.quad
                val corners = listOf(
                    result.toFullSpace(q.tl),
                    result.toFullSpace(q.tr),
                    result.toFullSpace(q.br),
                    result.toFullSpace(q.bl)
                ).map { p ->
                    Offset(
                        (p.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                        (p.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                    )
                }
                if (isRealDocumentDetected(corners, aspect) && !isNearFullFrame(corners) && !isNearDefault(corners)) {
                    Log.d(TAG, "Still detect: strategy=production score=${q.score}")
                    return corners
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image production detector failed", error)
        }

        // ── Strategy 3: BlueEdgeDetector ──────────────────────────────────
        try {
            val blueCorners = com.docscan.scanner.BlueEdgeDetector.analyzeStillBitmap(bitmap)
            if (
                blueCorners.size == 4 &&
                isRealDocumentDetected(blueCorners, aspect) &&
                !isNearDefault(blueCorners)
            ) {
                Log.d(TAG, "Still detect: strategy=blueEdge")
                return blueCorners
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image BlueEdgeDetector failed", error)
        }

        // ── Strategy 4: CoreEdgeDetector + CLAHE ──────────────────────────
        try {
            val coreCorners = detectViaCorePipeline(bitmap)
            if (coreCorners != null && isRealDocumentDetected(coreCorners, aspect)) {
                Log.d(TAG, "Still detect: strategy=core")
                return coreCorners
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image CoreEdgeDetector failed", error)
        }

        // ── Strategy 5: already-cropped full page ─────────────────────────
        try {
            if (looksLikeFullFrameDocument(bitmap)) {
                Log.d(TAG, "Still detect: strategy=fullFrameHeuristic")
                return listOf(
                    Offset(0.02f, 0.02f),
                    Offset(0.98f, 0.02f),
                    Offset(0.98f, 0.98f),
                    Offset(0.02f, 0.98f)
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Still-image full-frame heuristic failed", error)
        }

        Log.d(TAG, "Still detect: all strategies missed → defaultCorners")
        return defaultCorners()
    }

    private fun isNearFullFrame(corners: List<Offset>): Boolean {
        if (corners.size != 4) return false
        val area = calculateQuadArea(corners[0], corners[1], corners[2], corners[3])
        return area > 0.82f
    }

    private fun isNearDefault(corners: List<Offset>): Boolean {
        val def = defaultCorners()
        if (corners.size != 4 || def.size != 4) return false
        var total = 0f
        for (i in 0 until 4) {
            val dx = corners[i].x - def[i].x
            val dy = corners[i].y - def[i].y
            total += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return total < 0.08f
    }

    /**
     * Refines user-adjusted normalized corners (0..1) to sub-pixel accuracy using OpenCV
     * cornerSubPix and Canny edge analysis around each corner.
     */
    fun refineNormalizedCorners(
        bitmap: Bitmap,
        corners: List<Offset>
    ): List<Offset> {
        if (corners.size != 4 || bitmap.width < 16 || bitmap.height < 16) return corners
        val rgbaMat = Mat()
        val grayMat = Mat()
        val edgeMat = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Canny(grayMat, edgeMat, 50.0, 150.0)

            val cvPoints = corners.map {
                Point((it.x * bitmap.width).toDouble(), (it.y * bitmap.height).toDouble())
            }.toTypedArray()

            val refined = com.docscan.scanner.CornerRefiner.refineCorners(grayMat, edgeMat, cvPoints)
            refined.map { p ->
                Offset(
                    (p.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                    (p.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "refineNormalizedCorners failed, using original corners", e)
            corners
        } finally {
            rgbaMat.release()
            grayMat.release()
            edgeMat.release()
        }
    }

    /**
     * Generates a perspective-rectified bitmap preview using OpenCV warpPerspective.
     */
    fun generateOpenCvWarpPreview(
        bitmap: Bitmap,
        corners: List<Offset>,
        enhancementMode: com.docscan.scanner.PerspectiveCorrector.EnhancementMode = com.docscan.scanner.PerspectiveCorrector.EnhancementMode.ORIGINAL,
        maxPreviewDimension: Int = 1200
    ): Bitmap? {
        if (corners.size != 4 || bitmap.width < 16 || bitmap.height < 16) return null
        return try {
            val cvCorners = corners.map {
                Point((it.x * bitmap.width).toDouble(), (it.y * bitmap.height).toDouble())
            }
            val warpResult = com.docscan.scanner.PerspectiveCorrector.correctPerspective(
                sourceBitmap = bitmap,
                corners = cvCorners,
                mode = enhancementMode
            )
            val result = warpResult.bitmap
            if (result.width > maxPreviewDimension || result.height > maxPreviewDimension) {
                val scale = maxPreviewDimension.toFloat() / max(result.width, result.height).toFloat()
                val targetW = (result.width * scale).toInt().coerceAtLeast(1)
                val targetH = (result.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(result, targetW, targetH, true)
            } else {
                result
            }
        } catch (e: Throwable) {
            Log.w(TAG, "generateOpenCvWarpPreview failed", e)
            null
        }
    }

    /**
     * Dedicated still-image pass optimised for documents/cards on plain
     * backgrounds (the common gallery-import case).
     *
     * Key differences from the live pipeline:
     *  - Dual Canny thresholds (low + high) to catch soft and sharp edges
     *  - RETR_LIST so nested card contours are not discarded
     *  - Ranking by edge-energy density, NOT by contour area
     *  - Strong penalty for quads glued to the image border
     */
    private fun detectInnerDocument(bitmap: Bitmap): List<Offset>? {
        val rgba = org.opencv.core.Mat()
        val gray = org.opencv.core.Mat()
        val claheMat = org.opencv.core.Mat()
        val blur = org.opencv.core.Mat()
        val edges = org.opencv.core.Mat()
        val closed = org.opencv.core.Mat()
        val hierarchy = org.opencv.core.Mat()

        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
            org.opencv.imgproc.Imgproc.cvtColor(
                rgba, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY
            )

            val longSide = maxOf(bitmap.width, bitmap.height)
            val target = 1000.0
            val scale = if (longSide > target) target / longSide else 1.0
            val wW = maxOf(1, (bitmap.width * scale).toInt())
            val wH = maxOf(1, (bitmap.height * scale).toInt())

            val workGray = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.resize(
                gray, workGray, org.opencv.core.Size(wW.toDouble(), wH.toDouble()),
                0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_AREA
            )

            val clahe = org.opencv.imgproc.Imgproc.createCLAHE(2.0, org.opencv.core.Size(8.0, 8.0))
            clahe.apply(workGray, claheMat)
            clahe.collectGarbage()
            workGray.release()

            // Denoise fabric / noise but keep strong boundaries
            val bilateral = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.bilateralFilter(claheMat, bilateral, 7, 50.0, 50.0)
            org.opencv.imgproc.Imgproc.GaussianBlur(
                bilateral, blur, org.opencv.core.Size(5.0, 5.0), 1.0
            )
            bilateral.release()

            // Morphological gradient — strong on outer borders, weak on flat interiors
            val grad = org.opencv.core.Mat()
            val kGrad = org.opencv.imgproc.Imgproc.getStructuringElement(
                org.opencv.imgproc.Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0)
            )
            org.opencv.imgproc.Imgproc.morphologyEx(
                blur, grad, org.opencv.imgproc.Imgproc.MORPH_GRADIENT, kGrad
            )
            kGrad.release()

            // Dual Canny
            val cannyLo = org.opencv.core.Mat()
            val cannyHi = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.Canny(blur, cannyLo, 25.0, 80.0, 3, true)
            org.opencv.imgproc.Imgproc.Canny(blur, cannyHi, 50.0, 150.0, 3, true)
            org.opencv.core.Core.bitwise_or(cannyLo, cannyHi, edges)
            org.opencv.core.Core.bitwise_or(edges, grad, edges)
            cannyLo.release(); cannyHi.release(); grad.release()

            // Close gaps along card/page borders
            val kClose = org.opencv.imgproc.Imgproc.getStructuringElement(
                org.opencv.imgproc.Imgproc.MORPH_RECT, org.opencv.core.Size(7.0, 7.0)
            )
            org.opencv.imgproc.Imgproc.morphologyEx(
                edges, closed, org.opencv.imgproc.Imgproc.MORPH_CLOSE, kClose
            )
            org.opencv.imgproc.Imgproc.dilate(closed, closed, kClose)
            kClose.release()

            val contours = ArrayList<org.opencv.core.MatOfPoint>()
            org.opencv.imgproc.Imgproc.findContours(
                closed, contours, hierarchy,
                org.opencv.imgproc.Imgproc.RETR_LIST,
                org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE
            )

            val frameArea = (wW * wH).toDouble()
            var bestPts: Array<org.opencv.core.Point>? = null
            var bestScore = -1.0

            val epsilons = floatArrayOf(0.015f, 0.022f, 0.030f, 0.040f, 0.055f)

            fun dist(a: org.opencv.core.Point, b: org.opencv.core.Point): Double {
                val dx = a.x - b.x; val dy = a.y - b.y
                return kotlin.math.sqrt(dx * dx + dy * dy)
            }

            fun interiorStats(ordered: Array<org.opencv.core.Point>): Pair<Double, Double> {
                val xs = ordered.map { it.x.toInt().coerceIn(0, wW - 1) }
                val ys = ordered.map { it.y.toInt().coerceIn(0, wH - 1) }
                val minX = xs.minOrNull()!!; val maxX = xs.maxOrNull()!!
                val minY = ys.minOrNull()!!; val maxY = ys.maxOrNull()!!
                if (maxX - minX < 4 || maxY - minY < 4) return 0.0 to 999.0
                val roi = org.opencv.core.Rect(minX, minY, maxX - minX, maxY - minY)
                val patch = org.opencv.core.Mat(claheMat, roi)
                val mean = org.opencv.core.MatOfDouble()
                val std = org.opencv.core.MatOfDouble()
                org.opencv.core.Core.meanStdDev(patch, mean, std)
                val m = mean.get(0, 0)[0]
                val s = std.get(0, 0)[0]
                mean.release(); std.release()
                return m to s
            }

            fun evaluateCandidate(ordered: Array<org.opencv.core.Point>): Double {
                val geom = com.docscan.scanner.Corner.evaluateGeometry(ordered)
                if (!geom.isConvex || geom.score < 0.48) return -1.0
                if (geom.perpendicularity < 0.40) return -1.0
                if (geom.parallelism < 0.40) return -1.0

                val avgW = (dist(ordered[0], ordered[1]) + dist(ordered[3], ordered[2])) / 2.0
                val avgH = (dist(ordered[0], ordered[3]) + dist(ordered[1], ordered[2])) / 2.0
                if (avgW < 40 || avgH < 40) return -1.0

                val top = dist(ordered[0], ordered[1])
                val bot = dist(ordered[3], ordered[2])
                val left = dist(ordered[0], ordered[3])
                val right = dist(ordered[1], ordered[2])
                val hRatio = kotlin.math.min(top, bot) / kotlin.math.max(top, bot).coerceAtLeast(1.0)
                val vRatio = kotlin.math.min(left, right) / kotlin.math.max(left, right).coerceAtLeast(1.0)
                if (hRatio < 0.70 || vRatio < 0.70) return -1.0

                val physAspect = avgW / avgH
                if (physAspect < 0.28 || physAspect > 4.0) return -1.0

                val qArea = kotlin.math.abs(
                    ordered[0].x * ordered[1].y - ordered[1].x * ordered[0].y +
                    ordered[1].x * ordered[2].y - ordered[2].x * ordered[1].y +
                    ordered[2].x * ordered[3].y - ordered[3].x * ordered[2].y +
                    ordered[3].x * ordered[0].y - ordered[0].x * ordered[3].y
                ) * 0.5
                val areaRatio = qArea / frameArea
                if (areaRatio < 0.08 || areaRatio > 0.95) return -1.0

                val edgeSupport = com.docscan.scanner.Corner.calculateEdgeSupport(
                    ordered, closed, 4
                )
                if (edgeSupport < 0.22) return -1.0

                val (meanLum, stdLum) = interiorStats(ordered)

                // CRITICAL: blank white boxes inside cards (signature pads, empty
                // fields) are ultra-bright + ultra-smooth + medium-small.
                // Real cards/pages have more texture (photo, text, logos).
                val isBlankInnerBox =
                    areaRatio < 0.40 && meanLum > 175 && stdLum < 22
                if (isBlankInnerBox) return -1.0

                // Partial page slices (e.g. left half of a bank statement) —
                // reject if one side is glued to the image border while the
                // opposite side floats deep inside with weak edge support.
                val margin = minOf(wW, wH) * 0.025
                var hits = 0
                for (p in ordered) {
                    if (p.x < margin || p.x > wW - margin ||
                        p.y < margin || p.y > wH - margin
                    ) hits++
                }

                // Prefer OUTER documents: larger area wins among similar scores
                val areaPref = when {
                    areaRatio in 0.25..0.85 -> 1.15   // full card / full page
                    areaRatio in 0.15..0.25 -> 0.95
                    areaRatio in 0.08..0.15 -> 0.55   // small inner regions
                    else -> 0.35
                }

                val brightnessScore = when {
                    meanLum >= 100 -> 0.85
                    meanLum >= 70 -> 0.65
                    else -> 0.35
                }
                // Some interior texture is GOOD (text/photo) — not pure blank
                val textureScore = when {
                    stdLum in 15.0..55.0 -> 1.0   // text / photo content
                    stdLum in 8.0..15.0 -> 0.70
                    stdLum < 8.0 -> 0.25          // nearly blank
                    else -> 0.55                  // busy but ok
                }

                val borderPen = when (hits) {
                    0 -> 1.0
                    1 -> 0.95
                    2 -> 0.80
                    3 -> 0.55
                    else -> 0.35
                }

                val cardBonus = when {
                    physAspect in 1.45..1.72 -> 0.14
                    physAspect in 0.58..0.70 -> 0.14
                    physAspect in 1.20..1.90 -> 0.06
                    // A4-ish portrait pages
                    physAspect in 0.65..0.85 -> 0.08
                    else -> 0.0
                }

                // Edge support is the primary signal for true outer borders
                return (
                    0.30 * edgeSupport +
                    0.22 * geom.score +
                    0.20 * areaPref +
                    0.12 * textureScore +
                    0.08 * brightnessScore +
                    0.05 * hRatio +
                    cardBonus
                ) * borderPen
            }

            // Process largest contours first but score all fairly
            for (contour in contours.sortedByDescending {
                org.opencv.imgproc.Imgproc.contourArea(it)
            }.take(60)) {
                val cArea = org.opencv.imgproc.Imgproc.contourArea(contour)
                if (cArea / frameArea < 0.06) continue

                val c2f = org.opencv.core.MatOfPoint2f(*contour.toArray())
                try {
                    val peri = org.opencv.imgproc.Imgproc.arcLength(c2f, true)
                    for (eps in epsilons) {
                        val approx = org.opencv.core.MatOfPoint2f()
                        try {
                            org.opencv.imgproc.Imgproc.approxPolyDP(
                                c2f, approx, eps * peri, true
                            )
                            if (approx.total() != 4L) continue
                            val ordered = com.docscan.scanner.Corner.orderQuad(approx.toArray())
                            val refined = try {
                                com.docscan.scanner.Corner.refineCornersByEdgeIntersection(
                                    ordered, contour.toArray(), closed, 5
                                )
                            } catch (_: Throwable) {
                                ordered
                            }
                            val finalPts = com.docscan.scanner.Corner.orderQuad(refined)
                            val score = evaluateCandidate(finalPts)
                            if (score > bestScore) {
                                bestScore = score
                                bestPts = finalPts
                            }
                        } finally {
                            approx.release()
                        }
                    }

                    // minAreaRect — true rectangle fit (best for cards + pages)
                    val minRect = org.opencv.imgproc.Imgproc.minAreaRect(c2f)
                    val boxPts = Array(4) { org.opencv.core.Point() }
                    minRect.points(boxPts)
                    val orderedBox = com.docscan.scanner.Corner.orderQuad(boxPts)
                    val boxScore = evaluateCandidate(orderedBox)
                    val boosted = if (boxScore > 0) boxScore * 1.10 else -1.0
                    if (boosted > bestScore) {
                        bestScore = boosted
                        bestPts = orderedBox
                    }
                } finally {
                    c2f.release()
                }
            }
            contours.forEach { it.release() }

            if (bestPts == null || bestScore < 0.38) return null

            return bestPts.map {
                Offset(
                    (it.x / wW.toDouble()).toFloat().coerceIn(0f, 1f),
                    (it.y / wH.toDouble()).toFloat().coerceIn(0f, 1f)
                )
            }
        } finally {
            rgba.release(); gray.release(); claheMat.release()
            blur.release(); edges.release(); closed.release()
            hierarchy.release()
        }
    }

    private fun detectViaCorePipeline(bitmap: Bitmap): List<Offset>? {
        val rgba = org.opencv.core.Mat()
        val gray = org.opencv.core.Mat()
        val claheMat = org.opencv.core.Mat()
        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
            org.opencv.imgproc.Imgproc.cvtColor(rgba, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
            val clahe = org.opencv.imgproc.Imgproc.createCLAHE(3.0, org.opencv.core.Size(8.0, 8.0))
            clahe.apply(gray, claheMat)
            clahe.collectGarbage()

            val pts = com.docscan.scanner.CoreEdgeDetector.detectQuad(claheMat) ?: return null
            val ordered = com.docscan.scanner.Corner.orderQuad(pts.toTypedArray())
            return ordered.map {
                Offset(
                    (it.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                    (it.y / bitmap.height).toFloat().coerceIn(0f, 1f)
                )
            }
        } finally {
            rgba.release(); gray.release(); claheMat.release()
        }
    }

    private fun looksLikeFullFrameDocument(bitmap: Bitmap): Boolean {
        val sampleW = 64
        val sampleH = 64
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        try {
            val pixels = IntArray(sampleW * sampleH)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            var bright = 0
            var borderBright = 0
            var borderCount = 0
            val border = 4
            for (y in 0 until sampleH) {
                for (x in 0 until sampleW) {
                    val c = pixels[y * sampleW + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val lum = (r * 30 + g * 59 + b * 11) / 100
                    if (lum > 200) bright++
                    val onBorder = x < border || x >= sampleW - border || y < border || y >= sampleH - border
                    if (onBorder) {
                        borderCount++
                        if (lum > 180) borderBright++
                    }
                }
            }
            val brightRatio = bright.toFloat() / pixels.size
            val borderBrightRatio = if (borderCount > 0) borderBright.toFloat() / borderCount else 0f
            return brightRatio > 0.55f && borderBrightRatio > 0.50f
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun isRealDocumentDetected(
        corners: List<Offset>,
        frameAspect: Float
    ): Boolean {

        if (
            corners.size != 4
        ) {
            return false
        }

        if (
            corners.any {
                !it.x.isFinite() ||
                !it.y.isFinite()
            }
        ) {
            return false
        }

        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        val area =
            calculateQuadArea(
                tl,
                tr,
                br,
                bl
            )

        if (
            area < 0.035f ||
            area > 0.985f
        ) {
            return false
        }

        if (
            !isValidConvex(
                tl,
                tr,
                br,
                bl
            )
        ) {
            return false
        }

        val top =
            distance(
                tl,
                tr
            )

        val right =
            distance(
                tr,
                br
            )

        val bottom =
            distance(
                bl,
                br
            )

        val left =
            distance(
                tl,
                bl
            )

        if (
            min(
                min(top, right),
                min(bottom, left)
            ) < 0.05f
        ) {
            return false
        }

        // Aspect-ratio sanity check (the still-image detector in
        // scanner/EdgeDetector.kt already enforces this via config.minAspect/
        // maxAspect — the live camera path was missing it, which let long thin
        // quads from table edges, floor tiles, bookshelves, etc. pass as a
        // "document" as long as they were convex and no side was under 5%).
        //
        // corners here are normalized independently per axis (x / frameWidth,
        // y / frameHeight), so a physically square document does NOT produce
        // equal normalized width/height unless the frame itself is square.
        // Multiplying by frameAspect (frameWidth / frameHeight) converts the
        // normalized-space ratio back to a true physical aspect ratio before
        // comparing it against config bounds that were written in real terms.
        val avgWidthNorm = (top + bottom) / 2f
        val avgHeightNorm = (left + right) / 2f

        if (avgWidthNorm < 0.001f || avgHeightNorm < 0.001f) {
            return false
        }

        val physicalAspect = (avgWidthNorm / avgHeightNorm) * frameAspect
        val config = EdgeDetectionConfig.Default

        if (
            physicalAspect < config.minAspect.toFloat() ||
            physicalAspect > config.maxAspect.toFloat()
        ) {
            return false
        }

        return true
    }

    private fun distance(
        a: Offset,
        b: Offset
    ): Float {

        val dx =
            a.x - b.x

        val dy =
            a.y - b.y

        return sqrt(
            dx * dx +
                dy * dy
        )
    }

    // Single source of truth lives in QuadTracker (scanner package) — this
    // used to be a second, separately hand-typed copy of the same four
    // Offsets. Two independent literals for the same "no document" box meant
    // an edit to one without the other would silently break the
    // `currentCorners != defaultCorners()` comparisons in QuadTracker.
    fun defaultCorners():
        List<Offset> =
        QuadTracker.defaultCorners()

    fun calculateQuadArea(
        p1: Offset,
        p2: Offset,
        p3: Offset,
        p4: Offset
    ): Float {

        return abs(
            p1.x * p2.y -
                p2.x * p1.y +

                p2.x * p3.y -
                p3.x * p2.y +

                p3.x * p4.y -
                p4.x * p3.y +

                p4.x * p1.y -
                p1.x * p4.y
        ) * 0.5f
    }

    private fun isValidConvex(
        p1: Offset,
        p2: Offset,
        p3: Offset,
        p4: Offset
    ): Boolean {

        val points =
            listOf(
                p1,
                p2,
                p3,
                p4
            )

        var sign = 0

        for (i in points.indices) {

            val a =
                points[i]

            val b =
                points[
                    (i + 1) %
                        points.size
                ]

            val c =
                points[
                    (i + 2) %
                        points.size
                ]

            val cross =
                (
                    b.x - a.x
                ) *
                    (
                        c.y - b.y
                    ) -
                    (
                        b.y - a.y
                    ) *
                        (
                            c.x - b.x
                        )

            if (
                abs(cross) <
                0.00001f
            ) {
                return false
            }

            val currentSign =
                if (cross > 0f) {
                    1
                } else {
                    -1
                }

            if (
                sign == 0
            ) {
                sign =
                    currentSign
            } else if (
                sign !=
                currentSign
            ) {
                return false
            }
        }

        return true
    }
}

data class DetectionResult(
    val corners: List<Offset>,
    val isDocumentDetected: Boolean,
    val frameAspectRatio: Float = 3f / 4f,
    val confidence: Float = 0.5f
)

/**
 * Adapter between live detector and QuadTracker.
 */
class CornerSmoother {

    private val tracker =
        QuadTracker()

    fun processFrame(
        detected: DetectionResult
    ): SmoothedFrameState {

        val tracked =
            tracker.processFrame(
                candidateCorners =
                    detected.corners,
                isDocumentDetected =
                    detected.isDocumentDetected,
                candidateConfidence =
                    detected.confidence,
                frameAspectRatio =
                    detected.frameAspectRatio
            )

        return SmoothedFrameState(
            corners =
                tracked.corners,

            state =
                when (
                    tracked.state
                ) {
                    TrackerState.SEARCHING ->
                        DetectionState.SEARCHING_DOCUMENT

                    TrackerState.DETECTED ->
                        DetectionState.DOCUMENT_DETECTED

                    TrackerState.STABLE ->
                        DetectionState.DOCUMENT_STABLE

                    TrackerState.LOST ->
                        DetectionState.SEARCHING_DOCUMENT
                },

            frameAspectRatio =
                tracked.frameAspectRatio
        )
    }

    fun reset() {
        tracker.reset()
    }
}

data class SmoothedFrameState(
    val corners: List<Offset>,
    val state: DetectionState,
    val frameAspectRatio: Float
)