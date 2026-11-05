package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// --- Document Type & Result ---
enum class DocumentType {
    A4_DOCUMENT, ID_CARD, BUSINESS_CARD, RECEIPT, BOOK_PAGE, UNKNOWN
}

data class PremiumDetectionResult(
    val corners: List<Offset>,
    val isDocumentDetected: Boolean,
    val frameAspectRatio: Float,
    val confidence: Float,
    val documentType: DocumentType
)

/**
 * ML Kit & CamScanner grade high-performance real-time document edge detector.
 * Features:
 *  - Multi-pass edge extraction (CLAHE Canny + Adaptive Gaussian Threshold + Otsu Binarization + Convex Hull)
 *  - High-res sub-pixel corner refinement
 *  - Rock-solid jitter-free temporal tracking (One-Euro filter + hysteresis + grace period)
 *  - Dynamic document classification (A4, ID card, Receipt, Book, Business card)
 */
object BlueEdgeDetector {

    private const val TAG = "BlueEdgeDetector"

    private const val TARGET_LONG_SIDE = 720
    private const val MIN_LONG_SIDE = 360

    private val EPSILON_VALUES = floatArrayOf(0.012f, 0.018f, 0.025f, 0.035f, 0.048f)

    /**
     * Temporal Tracker with One-Euro filtering, grace persistence, and deadband smoothing.
     * Guarantees butter-smooth, stable corners without flickering.
     */
    object PremiumQuadTracker {
        private var lastAcceptedCorners: List<Offset>? = null
        private var smoothedCorners: List<Offset>? = null
        private var trackCount = 0
        private var lostGraceFrames = 0
        private var consecutiveValidHits = 0
        private var acquisitionMissFrames = 0
        private var pendingCorners: List<Offset>? = null
        private var pendingRelocation: List<Offset>? = null
        private var pendingRelocationHits = 0
        private const val REQUIRED_HITS_TO_OPEN = 2 // Quick lock within 2 frames
        private const val MAX_ACQUISITION_GAP = 2 // A faint border may vanish on alternating frames
        private const val MAX_GRACE_FRAMES = 12      // Bridge short focus / motion blur without box flicker
        private const val DEADBAND_THRESHOLD = 0.006f // Ignore sub-pixel/noisy corner shifts at rest

        // One-Euro Filter State per corner (4 corners x 2 axes = 8 filters)
        private val xPrev = FloatArray(4)
        private val dxPrev = FloatArray(4)
        private val yPrev = FloatArray(4)
        private val dyPrev = FloatArray(4)
        private val initialized = BooleanArray(4)
        private var lastTimestampNanos = 0L

        @Synchronized
        fun reset() {
            lastAcceptedCorners = null
            smoothedCorners = null
            trackCount = 0
            lostGraceFrames = 0
            consecutiveValidHits = 0
            acquisitionMissFrames = 0
            pendingCorners = null
            pendingRelocation = null
            pendingRelocationHits = 0
            for (i in 0..3) {
                initialized[i] = false
                xPrev[i] = 0f
                dxPrev[i] = 0f
                yPrev[i] = 0f
                dyPrev[i] = 0f
            }
            lastTimestampNanos = 0L
        }

        @Synchronized
        fun updateAndSmooth(newCorners: List<Offset>?, confidence: Float): Pair<List<Offset>?, Boolean> {
            val now = System.nanoTime()

            val minConfidenceToAccept = if (trackCount > 0) 0.36f else 0.40f
            if (newCorners == null || confidence < minConfidenceToAccept || newCorners.size != 4) {
                pendingRelocation = null
                pendingRelocationHits = 0
                // A genuine paper border can be detected only every other
                // analysis frame on textured desks or under moving shadows.
                // Keep the *pending* shape briefly, without displaying it,
                // so the next agreeing frame can open the box.
                if (trackCount == 0 && pendingCorners != null &&
                    acquisitionMissFrames < MAX_ACQUISITION_GAP) {
                    acquisitionMissFrames++
                    return Pair(null, false)
                }
                // If we recently tracked a valid quad, allow grace period for brief motion blur or hand adjustments
                if (smoothedCorners != null && lostGraceFrames < MAX_GRACE_FRAMES && trackCount >= 2) {
                    lostGraceFrames++
                    return Pair(smoothedCorners, true)
                }
                reset()
                return Pair(null, false)
            }

            // A candidate was detected in this frame
            if (trackCount == 0) {
                val pending = pendingCorners
                val isSamePaper = pending != null &&
                    alignToPreviousCorners(pending, newCorners).zip(pending).all { (a, b) ->
                        hypot(a.x - b.x, a.y - b.y) < 0.16f
                    }
                consecutiveValidHits = if (isSamePaper) consecutiveValidHits + 1 else 1
                pendingCorners = newCorners
                acquisitionMissFrames = 0
                if (consecutiveValidHits < REQUIRED_HITS_TO_OPEN) {
                    return Pair(null, false)
                }
            }
            pendingCorners = null
            consecutiveValidHits = 0

            lostGraceFrames = 0
            val currentLast = lastAcceptedCorners

            // Align new corners to previous corners to eliminate 90-degree index rotation flips
            val alignedCorners = if (currentLast != null && currentLast.size == 4) {
                alignToPreviousCorners(currentLast, newCorners)
            } else {
                newCorners
            }

            // Upgrade an inner printed form to its enclosing sheet immediately.
            // But a sudden jump to an inner box or unrelated object must be
            // confirmed over several frames: accepting one high-confidence
            // spurious contour caused the live outline to flicker wildly.
            if (currentLast != null && trackCount >= 4 &&
                isDrasticJump(currentLast, alignedCorners)) {
                val outerPageUpgrade = isOutwardExpansion(currentLast, alignedCorners)
                if (outerPageUpgrade) {
                    for (i in initialized.indices) initialized[i] = false
                } else {
                    val previousProposal = pendingRelocation
                    val matchesProposal = previousProposal != null &&
                        alignToPreviousCorners(previousProposal, alignedCorners)
                            .zip(previousProposal).all { (a, b) ->
                                hypot(a.x - b.x, a.y - b.y) < 0.075f
                            }
                    pendingRelocationHits = if (matchesProposal) pendingRelocationHits + 1 else 1
                    pendingRelocation = alignedCorners
                    val requiredHits = if (confidence >= 0.65f) 3 else 4
                    if (pendingRelocationHits < requiredHits) {
                        return Pair(smoothedCorners, true)
                    }
                    // This is a real moved document, not a one-frame glitch.
                    for (i in initialized.indices) initialized[i] = false
                }
            }
            pendingRelocation = null
            pendingRelocationHits = 0

            // Deadband stillness check: If camera is steady, completely freeze coordinates (zero micro-jitter)
            val currentSmooth = smoothedCorners
            if (currentSmooth != null && currentSmooth.size == 4 && trackCount >= 2) {
                var maxDist = 0f
                for (i in 0..3) {
                    val dx = alignedCorners[i].x - currentSmooth[i].x
                    val dy = alignedCorners[i].y - currentSmooth[i].y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist > maxDist) maxDist = dist
                }
                if (maxDist < DEADBAND_THRESHOLD) {
                    lastAcceptedCorners = alignedCorners
                    lastTimestampNanos = now
                    // A steady frame is still a valid hit. Without incrementing,
                    // the tracker never arms its larger-page upgrade after settling.
                    trackCount = (trackCount + 1).coerceAtMost(1000)
                    return Pair(currentSmooth, true)
                }
            }

            // Apply One-Euro filter on each corner with tuned smoothing parameters
            val filteredList = ArrayList<Offset>(4)
            for (i in 0..3) {
                val rawX = alignedCorners[i].x.coerceIn(0.001f, 0.999f)
                val rawY = alignedCorners[i].y.coerceIn(0.001f, 0.999f)

                if (!initialized[i]) {
                    initialized[i] = true
                    xPrev[i] = rawX
                    dxPrev[i] = 0f
                    yPrev[i] = rawY
                    dyPrev[i] = 0f
                    filteredList.add(Offset(rawX, rawY))
                } else {
                    val dt = if (lastTimestampNanos > 0L) {
                        ((now - lastTimestampNanos).coerceAtLeast(1L) / 1_000_000_000f).coerceIn(1f / 120f, 0.1f)
                    } else 1f / 30f

                    // Filter X with lower cutoff for buttery-smooth stabilization
                    val fx = filterCoordinate(i, rawX, xPrev[i], dxPrev[i], dt, isX = true)
                    // Filter Y with lower cutoff for buttery-smooth stabilization
                    val fy = filterCoordinate(i, rawY, yPrev[i], dyPrev[i], dt, isX = false)

                    filteredList.add(Offset(fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f)))
                }
            }

            lastTimestampNanos = now
            lastAcceptedCorners = alignedCorners
            smoothedCorners = filteredList
            trackCount++

            return Pair(filteredList, true)
        }

        /**
         * Aligns new corners with previous corners cyclically to ensure corner 0 matches closest corner 0,
         * avoiding catastrophic rotational index flips.
         */
        private fun alignToPreviousCorners(previous: List<Offset>, current: List<Offset>): List<Offset> {
            var bestShift = 0
            var minTotalDist = Float.MAX_VALUE
            for (shift in 0..3) {
                var totalDist = 0f
                for (i in 0..3) {
                    val p = previous[i]
                    val c = current[(i + shift) % 4]
                    val dx = p.x - c.x
                    val dy = p.y - c.y
                    totalDist += dx * dx + dy * dy
                }
                if (totalDist < minTotalDist) {
                    minTotalDist = totalDist
                    bestShift = shift
                }
            }
            return List(4) { i -> current[(i + bestShift) % 4] }
        }

        private fun filterCoordinate(
            index: Int,
            rawVal: Float,
            prevVal: Float,
            prevDVal: Float,
            dt: Float,
            isX: Boolean
        ): Float {
            // Tuned for high stability and zero jitter at rest, with responsive tracking during panning
            // Steadier at rest; the derivative term still lets the frame follow
            // a deliberate camera move instead of leaving the outline behind.
            val minCutoff = 0.30f
            val beta = 0.25f
            val dCutoff = 1.0f

            fun getAlpha(cutoff: Float, deltaT: Float): Float {
                val tau = 1f / (2f * Math.PI.toFloat() * cutoff.coerceIn(0.05f, 50f))
                return (1f / (1f + tau / deltaT)).coerceIn(0f, 1f)
            }

            val dVal = (rawVal - prevVal) / dt
            val dAlpha = getAlpha(dCutoff, dt)
            val dHat = prevDVal + dAlpha * (dVal - prevDVal)

            val cutoff = minCutoff + beta * abs(dHat)
            val vAlpha = getAlpha(cutoff, dt)
            val filtered = prevVal + vAlpha * (rawVal - prevVal)

            if (isX) {
                xPrev[index] = filtered
                dxPrev[index] = dHat
            } else {
                yPrev[index] = filtered
                dyPrev[index] = dHat
            }
            return filtered
        }

        private fun isDrasticJump(old: List<Offset>, new: List<Offset>): Boolean {
            // Check rigid translation (camera panning) vs shape deformation
            val dxs = FloatArray(4) { i -> new[i].x - old[i].x }
            val dys = FloatArray(4) { i -> new[i].y - old[i].y }
            val avgDx = dxs.average().toFloat()
            val avgDy = dys.average().toFloat()

            var maxDeformation = 0f
            for (i in 0..3) {
                val residualX = dxs[i] - avgDx
                val residualY = dys[i] - avgDy
                val def = sqrt(residualX * residualX + residualY * residualY)
                if (def > maxDeformation) maxDeformation = def
            }
            val maxDist = (0..3).maxOf { sqrt(dxs[it] * dxs[it] + dys[it] * dys[it]) }
            return maxDist > 0.25f || maxDeformation > 0.12f
        }

        private fun isOutwardExpansion(previous: List<Offset>, candidate: List<Offset>): Boolean {
            if (previous.size != 4 || candidate.size != 4) return false
            val oldLeft = previous.minOf { it.x }
            val oldRight = previous.maxOf { it.x }
            val oldTop = previous.minOf { it.y }
            val oldBottom = previous.maxOf { it.y }
            val newLeft = candidate.minOf { it.x }
            val newRight = candidate.maxOf { it.x }
            val newTop = candidate.minOf { it.y }
            val newBottom = candidate.maxOf { it.y }
            val previousArea = (oldRight - oldLeft) * (oldBottom - oldTop)
            val candidateArea = (newRight - newLeft) * (newBottom - newTop)
            val containsPrevious = oldLeft >= newLeft - 0.025f &&
                oldRight <= newRight + 0.025f &&
                oldTop >= newTop - 0.025f &&
                oldBottom <= newBottom + 0.025f
            return containsPrevious && previousArea > 0.001f &&
                candidateArea > previousArea * 1.45f
        }

        fun defaultCorners(): List<Offset> = listOf(
            Offset(0.08f, 0.12f), Offset(0.92f, 0.12f),
            Offset(0.92f, 0.88f), Offset(0.08f, 0.88f)
        )
    }

    fun idCardFrameCorners(): List<Offset> = listOf(
        Offset(0.08f, 0.24f),
        Offset(0.92f, 0.24f),
        Offset(0.92f, 0.72f),
        Offset(0.08f, 0.72f)
    )

    /**
     * CameraX entry point with multi-strategy detection, sub-pixel accuracy, and temporal tracking.
     */
    fun analyzeImageProxy(imageProxy: ImageProxy): PremiumDetectionResult {
        val rotatedDegrees = imageProxy.imageInfo.rotationDegrees

        val isQuarterTurn = rotatedDegrees == 90 || rotatedDegrees == 270
        val uprightWidth = if (isQuarterTurn) imageProxy.height else imageProxy.width
        val uprightHeight = if (isQuarterTurn) imageProxy.width else imageProxy.height
        val uprightAspect = uprightWidth.toFloat() / uprightHeight.coerceAtLeast(1)

        val detection = try {
            if (imageProxy.format == ImageFormat.YUV_420_888 && imageProxy.planes.isNotEmpty()) {
                detectFromYuv(
                    yBuffer = imageProxy.planes[0].buffer,
                    srcW = imageProxy.width,
                    srcH = imageProxy.height,
                    rowStride = imageProxy.planes[0].rowStride,
                    pixelStride = imageProxy.planes[0].pixelStride,
                    rotationDegrees = rotatedDegrees
                )
            } else {
                detectFromBitmap(imageProxy)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Live detection failed", t)
            null
        }

        val (smoothedCorners, isDetected) = PremiumQuadTracker.updateAndSmooth(
            detection?.corners,
            detection?.confidence ?: 0f
        )

        if (isDetected && smoothedCorners != null && smoothedCorners.size == 4) {
            // Detection points are normalized *after* rotating the YUV frame.
            // Classify against the upright dimensions, not the sensor dimensions.
            val rawPts = smoothedCorners.map {
                Point((it.x * uprightWidth).toDouble(), (it.y * uprightHeight).toDouble())
            }.toTypedArray()

            val docType = classifyDocumentType(rawPts, uprightWidth, uprightHeight)

            return PremiumDetectionResult(
                corners = smoothedCorners,
                isDocumentDetected = true,
                frameAspectRatio = uprightAspect,
                // The tracker may briefly retain old corners during a missing frame.
                // Never present that grace-period outline as a fresh confident capture.
                confidence = detection?.confidence ?: 0f,
                documentType = docType
            )
        }

        // When no real document is detected on screen, return empty corners so no box is drawn!
        return PremiumDetectionResult(
            corners = emptyList(),
            isDocumentDetected = false,
            frameAspectRatio = uprightAspect,
            confidence = 0f,
            documentType = DocumentType.UNKNOWN
        )
    }

    /**
     * Classifies document type by aspect ratio and relative area.
     */
    private fun classifyDocumentType(orderedPts: Array<Point>, w: Int, h: Int): DocumentType {
        val topLen = dist(orderedPts[0], orderedPts[1])
        val botLen = dist(orderedPts[3], orderedPts[2])
        val leftLen = dist(orderedPts[0], orderedPts[3])
        val rightLen = dist(orderedPts[1], orderedPts[2])

        val avgW = (topLen + botLen) / 2.0
        val avgH = (leftLen + rightLen) / 2.0
        val aspect = max(avgW, avgH) / min(avgW, avgH).coerceAtLeast(1.0)
        val areaRatio = (avgW * avgH) / (w * h).toDouble()

        return when {
            aspect in 1.35..1.65 && areaRatio < 0.28 -> DocumentType.ID_CARD
            aspect in 1.40..1.80 && areaRatio < 0.16 -> DocumentType.BUSINESS_CARD
            aspect in 1.20..1.72 && areaRatio > 0.28 -> DocumentType.A4_DOCUMENT
            aspect > 1.85 -> DocumentType.RECEIPT
            aspect in 1.10..1.35 && areaRatio > 0.40 -> DocumentType.BOOK_PAGE
            else -> DocumentType.UNKNOWN
        }
    }

    /**
     * High-precision still image corner detection.
     */
    fun analyzeStillBitmap(bitmap: Bitmap): List<Offset> {
        if (bitmap.width < 20 || bitmap.height < 20) {
            return PremiumQuadTracker.defaultCorners()
        }

        val rgba = Mat()
        val gray = Mat()
        val scaled = Mat()

        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val maxDim = max(bitmap.width, bitmap.height)
            val scale = if (maxDim > 1024) 1024.0 / maxDim else 1.0
            val targetW = max(1, (bitmap.width * scale).toInt())
            val targetH = max(1, (bitmap.height * scale).toInt())

            Imgproc.resize(
                gray, scaled, Size(targetW.toDouble(), targetH.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )

            val detectedQuad = multiPassDocumentDetection(
                grayMat = scaled,
                wW = targetW,
                wH = targetH
            )

            if (detectedQuad != null) {
                return detectedQuad.corners
            }

            return PremiumQuadTracker.defaultCorners()
        } catch (t: Throwable) {
            Log.w(TAG, "Still image detection failed", t)
            return PremiumQuadTracker.defaultCorners()
        } finally {
            rgba.release()
            gray.release()
            scaled.release()
        }
    }

    // --------------------------- Internal Multi-Pass Engine --------------------------- //

    private data class InternalResult(val corners: List<Offset>, val confidence: Float)

    private data class InternalCandidate(
        val rawPts: Array<Point>,
        val areaNorm: Float,
        val confidence: Float,
        val signedPaperContrast: Double
    )

    private fun detectFromYuv(
        yBuffer: ByteBuffer,
        srcW: Int,
        srcH: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): InternalResult? {
        var rawMat: Mat? = null
        var rotatedMat: Mat? = null
        var scaledMat: Mat? = null

        try {
            rawMat = Mat(srcH, srcW, CvType.CV_8UC1)
            rawMat.put(0, 0, copyYPlane(yBuffer, srcW, srcH, rowStride, pixelStride))

            rotatedMat = Mat()
            when (rotationDegrees) {
                90 -> Core.rotate(rawMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(rawMat, rotatedMat, Core.ROTATE_180)
                270 -> Core.rotate(rawMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> rawMat.copyTo(rotatedMat)
            }

            val rotatedW = rotatedMat.width()
            val rotatedH = rotatedMat.height()
            val longSide = max(rotatedW, rotatedH).coerceAtLeast(1)
            val scale = (TARGET_LONG_SIDE.toDouble() / longSide)
                .coerceAtMost(1.0)
                .coerceAtLeast(MIN_LONG_SIDE.toDouble() / longSide)
            val wW = max(1, (rotatedW * scale).toInt())
            val wH = max(1, (rotatedH * scale).toInt())

            scaledMat = Mat()
            Imgproc.resize(
                rotatedMat, scaledMat, Size(wW.toDouble(), wH.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )

            return multiPassDocumentDetection(scaledMat, wW, wH)
        } finally {
            rawMat?.release()
            rotatedMat?.release()
            scaledMat?.release()
        }
    }

    /**
     * CamScanner-Grade Multi-Pass Document Detection Algorithm:
     *  0. CLAHE Dynamic Contrast Equalization (boosts faint paper borders in shadow/glare)
     *  1. Macro-Luminance Segmentation (Gaussian Blur 21x21 + Otsu + Morph Close) to eliminate internal text/tables
     *  2. Inverted Macro-Luminance (handles dark passports / cards on light tables)
     *  3. Large-Window Adaptive Gaussian Thresholding on CLAHE
     *  4. Multi-Scale Canny with Morphological Closing & Dilation
     *  5. Scharr First-Order Edge Energy Field + Otsu binarization
     *  6. Orientation-Invariant Hough Line Fitting & Analytical 4-Line Intersection
     *  7. Morphological Gradient + Otsu Binarization (CamScanner physical edge extraction)
     *  8. Thumb/Finger Occlusion Recovery (5-8 vertex Dominant Quad Simplification)
     *  9. Enclosure Filtering & Sub-pixel Corner Refinement
     */
    private fun multiPassDocumentDetection(
        grayMat: Mat,
        wW: Int,
        wH: Int
    ): InternalResult? {
        val frameArea = (wW * wH).toDouble()
        val candidateList = ArrayList<InternalCandidate>()

        // 0. Equalize local contrast via CLAHE to amplify subtle document edges
        val claheMat = Mat()
        val claheOperator = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
        claheOperator.apply(grayMat, claheMat)

        // 1. Generate clean Canny edge map for edge support testing and line fitting
        val cannyEdgeMat = Mat()
        val median = computeMedian(claheMat)
        val lowThresh = (median * 0.40).coerceIn(20.0, 80.0)
        val highThresh = max(lowThresh + 28.0, median * 1.15).coerceIn(65.0, 180.0)
        Imgproc.Canny(claheMat, cannyEdgeMat, lowThresh, highThresh, 3, true)
        val cannyPixels = ByteArray(wW * wH * cannyEdgeMat.channels().coerceAtLeast(1))
        try {
            cannyEdgeMat.get(0, 0, cannyPixels)
        } catch (_: Throwable) {}

        fun testAndRecordCandidate(ordered: Array<Point>, passWeight: Float = 1.0f) {
            // Check 1: Must NOT touch the camera sensor boundaries (margin 0.8% of sensor size)
            val marginX = wW * 0.008
            val marginY = wH * 0.008
            for (p in ordered) {
                if (p.x < marginX || p.x > wW - marginX || p.y < marginY || p.y > wH - marginY) {
                    return // Rejected: touches camera sensor edge!
                }
            }

            // Check 2: Convexity & basic geometry
            val geom = Corner.evaluateGeometry(ordered)
            if (!geom.isConvex || geom.score < 0.32) return

            // Check 3: Area must be between 3.5% and 98.5% of viewfinder
            val qArea = computeQuadAreaNorm(ordered, wW, wH)
            if (qArea < ScannerTuning.MIN_LIVE_AREA_RATIO.toFloat() || qArea > ScannerTuning.MAX_LIVE_AREA_RATIO.toFloat()) return

            // Check 4: Aspect ratio (supports standard paper, cards, and receipts up to 4.5)
            val top = dist(ordered[0], ordered[1])
            val bot = dist(ordered[3], ordered[2])
            val left = dist(ordered[0], ordered[3])
            val right = dist(ordered[1], ordered[2])
            val avgWidth = (top + bot) / 2.0
            val avgHeight = (left + right) / 2.0
            val aspect = max(avgWidth, avgHeight) / min(avgWidth, avgHeight).coerceAtLeast(1.0)
            if (aspect > 4.5) return

            // Check 5: Opposing sides must not have wildly disparate lengths
            val hRatio = min(top, bot) / max(top, bot).coerceAtLeast(1.0)
            val vRatio = min(left, right) / max(left, right).coerceAtLeast(1.0)
            if (hRatio < 0.40 || vRatio < 0.40) return

            // Check 6: Real physical edge support on ALL 4 sides
            val perSide = Corner.calculatePerSideEdgeSupport(ordered, cannyEdgeMat, cachedPixels = cannyPixels, searchRadiusPx = 4)
            val minSideSupport = perSide.minOrNull() ?: 0.0
            val avgSideSupport = perSide.average()

            // CamScanner resilience: Every side must have real contrast, but allow single-edge glare if 3 sides are strongly supported
            val sidesAboveMin = perSide.count { it >= 0.06 }
            val boundaryContrast = ScannerTuning.boundaryContrast(grayMat, ordered)
            val paperContrast = ScannerTuning.paperBoundaryPolarity(grayMat, ordered)
            if (ScannerTuning.isLikelyPrintedInnerFrame(qArea, paperContrast, boundaryContrast)) {
                return
            }
            val isEdgeSupported = (minSideSupport >= ScannerTuning.MIN_SIDE_EDGE_SUPPORT &&
                    avgSideSupport >= ScannerTuning.MIN_AVG_EDGE_SUPPORT) ||
                    (sidesAboveMin >= 3 && avgSideSupport >= 0.16) ||
                    (avgSideSupport >= 0.22) ||
                    // Full pages can have two faint borders against white paper.
                    (qArea >= 0.24f && sidesAboveMin >= 2 &&
                     avgSideSupport >= 0.10 && paperContrast >= 3.0) ||
                    // White sheets on a light wooden table are frequently
                    // low-Canny-edge despite their real brightness boundary.
                    ScannerTuning.isSoftWhitePaperBoundary(
                        qArea, paperContrast, boundaryContrast,
                        sidesAboveMin, avgSideSupport
                    )
            if (!isEdgeSupported) return

            // Thin printed form boxes lack a consistently brighter interior.
            if (boundaryContrast < ScannerTuning.MIN_BOUNDARY_CONTRAST &&
                avgSideSupport < 0.24 && paperContrast < 3.0) return

            val geoScore = geom.score.toFloat().coerceIn(0f, 1f)
            val areaScore = when {
                qArea in 0.20f..0.85f -> 1.0f
                qArea in 0.10f..0.20f -> 0.85f
                else -> 0.60f
            }
            val paperPrior = ScannerTuning.paperRatioScore(aspect).toFloat()
            val aspectScore = when {
                paperPrior >= 0.80f -> 1.0f
                aspect in 1.10..2.60 -> 0.85f
                else -> 0.60f
            }
            val boundaryScore = (boundaryContrast / 25.0).toFloat().coerceIn(0f, 1f)

            val confidence = ((
                0.24f * geoScore +
                0.32f * avgSideSupport.toFloat().coerceIn(0f, 1f) +
                0.15f * areaScore +
                0.14f * aspectScore +
                0.15f * boundaryScore
            ).coerceIn(0f, 1f) * passWeight +
                (paperContrast / 35.0).coerceIn(0.0, 1.0).toFloat() * 0.12f
            ).coerceIn(0f, 1f)

            if (confidence >= 0.40f) {
                candidateList.add(InternalCandidate(ordered, qArea, confidence, paperContrast))
            }
        }

        try {
            // --- PASS 0: CLAHE Local Adaptive Thresholding (Block size 25) ---
            val adaptMat0 = Mat()
            val kAdapt0 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            try {
                Imgproc.adaptiveThreshold(
                    claheMat, adaptMat0, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 25, 4.0
                )
                Imgproc.morphologyEx(adaptMat0, adaptMat0, Imgproc.MORPH_CLOSE, kAdapt0)
                extractContoursAndQuads(adaptMat0, frameArea) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.12f)
                }
            } finally {
                adaptMat0.release()
                kAdapt0.release()
            }

            // --- PASS 1: Macro-Luminance Gaussian Blur (21x21) + Otsu Binarization ---
            // Large Gaussian blur completely smooths out fine printed text and internal tables,
            // leaving the whole white page as a single uniform bright shape against the darker table.
            val macroBlur = Mat()
            val macroOtsu = Mat()
            val kMacroClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
            try {
                Imgproc.GaussianBlur(grayMat, macroBlur, Size(21.0, 21.0), 0.0)
                Imgproc.threshold(macroBlur, macroOtsu, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                Imgproc.morphologyEx(macroOtsu, macroOtsu, Imgproc.MORPH_CLOSE, kMacroClose)

                extractContoursAndQuads(macroOtsu, frameArea, retrievalMode = Imgproc.RETR_LIST) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.15f)
                }
            } finally {
                macroBlur.release()
                macroOtsu.release()
                kMacroClose.release()
            }

            // --- PASS 2: Inverted Macro-Luminance (Dark documents / Passports on light desk) ---
            val macroBlurInv = Mat()
            val macroOtsuInv = Mat()
            val kMacroCloseInv = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
            try {
                Imgproc.GaussianBlur(grayMat, macroBlurInv, Size(21.0, 21.0), 0.0)
                Imgproc.threshold(macroBlurInv, macroOtsuInv, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
                Imgproc.morphologyEx(macroOtsuInv, macroOtsuInv, Imgproc.MORPH_CLOSE, kMacroCloseInv)

                extractContoursAndQuads(macroOtsuInv, frameArea, retrievalMode = Imgproc.RETR_LIST) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.10f)
                }
            } finally {
                macroBlurInv.release()
                macroOtsuInv.release()
                kMacroCloseInv.release()
            }

            // --- PASS 3: Large-Window Adaptive Gaussian Thresholding on CLAHE ---
            val adaptMat = Mat()
            val kAdaptClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(11.0, 11.0))
            try {
                Imgproc.adaptiveThreshold(
                    claheMat, adaptMat, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 51, 6.0
                )
                Imgproc.morphologyEx(adaptMat, adaptMat, Imgproc.MORPH_CLOSE, kAdaptClose)

                extractContoursAndQuads(adaptMat, frameArea) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.05f)
                }
            } finally {
                adaptMat.release()
                kAdaptClose.release()
            }

            // --- PASS 4: Multi-Scale Canny with Morphological Closing & Dilation ---
            val morphCanny = Mat()
            val kCannyClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(11.0, 11.0))
            val kDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            try {
                Imgproc.morphologyEx(cannyEdgeMat, morphCanny, Imgproc.MORPH_CLOSE, kCannyClose)
                Imgproc.dilate(morphCanny, morphCanny, kDilate)

                extractContoursAndQuads(morphCanny, frameArea) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.00f)
                }
            } finally {
                morphCanny.release()
                kCannyClose.release()
                kDilate.release()
            }

            // --- PASS 5: Scharr Edge Energy Filter (CamScanner Boundary Energy Magnitude) ---
            val gradX = Mat()
            val gradY = Mat()
            val absGradX = Mat()
            val absGradY = Mat()
            val gradMag = Mat()
            val gradThresh = Mat()
            val kScharrClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            try {
                Imgproc.Scharr(claheMat, gradX, CvType.CV_16S, 1, 0)
                Imgproc.Scharr(claheMat, gradY, CvType.CV_16S, 0, 1)
                Core.convertScaleAbs(gradX, absGradX)
                Core.convertScaleAbs(gradY, absGradY)
                Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, gradMag)
                Imgproc.threshold(gradMag, gradThresh, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                Imgproc.morphologyEx(gradThresh, gradThresh, Imgproc.MORPH_CLOSE, kScharrClose)
                extractContoursAndQuads(gradThresh, frameArea) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.10f)
                }
            } catch (_: Throwable) {
            } finally {
                gradX.release(); gradY.release(); absGradX.release(); absGradY.release()
                gradMag.release(); gradThresh.release(); kScharrClose.release()
            }

            // --- PASS 6: Dominant Hough Line Intersection (Orientation-Invariant) ---
            detectQuadByHoughLines(cannyEdgeMat, wW, wH)?.let { houghQuad ->
                testAndRecordCandidate(houghQuad, passWeight = 1.12f)
            }

            // --- PASS 7: Morphological Gradient + Otsu Binarization (CamScanner physical edge extraction) ---
            val morphGrad = Mat()
            val morphGradThresh = Mat()
            val kGrad = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            try {
                Imgproc.morphologyEx(claheMat, morphGrad, Imgproc.MORPH_GRADIENT, kGrad)
                Imgproc.threshold(morphGrad, morphGradThresh, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                extractContoursAndQuads(morphGradThresh, frameArea) { quad ->
                    testAndRecordCandidate(quad, passWeight = 1.16f)
                }
            } finally {
                morphGrad.release()
                morphGradThresh.release()
                kGrad.release()
            }
        } finally {
            claheMat.release()
            cannyEdgeMat.release()
        }

        if (candidateList.isEmpty()) return null

        // --- ENCLOSURE FILTERING ---
        // Discard any candidate whose centroid is enclosed within a significantly larger candidate.
        // This eliminates any internal tables or text frames inside the paper.
        val outerCandidates = candidateList.filter { candA ->
            val isChildOfLarger = candidateList.any { candB ->
                if (candB.areaNorm > candA.areaNorm * 1.25f) {
                    val centerA = Point(
                        (candA.rawPts[0].x + candA.rawPts[1].x + candA.rawPts[2].x + candA.rawPts[3].x) / 4.0,
                        (candA.rawPts[0].y + candA.rawPts[1].y + candA.rawPts[2].y + candA.rawPts[3].y) / 4.0
                    )
                    val polyB = MatOfPoint2f(*candB.rawPts)
                    val test = try {
                        Imgproc.pointPolygonTest(polyB, centerA, false)
                    } catch (_: Throwable) {
                        -1.0
                    } finally {
                        polyB.release()
                    }
                    test >= 0.0 // Center of candA is strictly inside candB
                } else {
                    false
                }
            }
            !isChildOfLarger
        }

        val pool = if (outerCandidates.isNotEmpty()) outerCandidates else candidateList
        // CamScanner priority: prefer outer boundary candidate with highest combined confidence + area prior
        val best = pool.maxByOrNull {
            ScannerTuning.outerPagePriority(it.confidence, it.areaNorm, it.signedPaperContrast)
        } ?: return null

        if (best.confidence < 0.40f) return null

        // Sub-pixel refine corners on Canny edge map for razor sharp alignment
        val edgeMap = Mat()
        Imgproc.Canny(grayMat, edgeMap, 35.0, 115.0)
        val refinedPts = try {
            CornerRefiner.refineCorners(grayMat = grayMat, edgeMat = edgeMap, quad = best.rawPts)
        } catch (_: Throwable) {
            best.rawPts
        } finally {
            edgeMap.release()
        }

        val normCorners = refinedPts.map {
            Offset(
                (it.x / wW.toDouble()).toFloat().coerceIn(0f, 1f),
                (it.y / wH.toDouble()).toFloat().coerceIn(0f, 1f)
            )
        }

        return InternalResult(normCorners, best.confidence)
    }

    /**
     * Fits dominant lines from Hough line segments with full 360-degree rotation tolerance
     * and analytically calculates their 4 intersection points.
     */
    private fun detectQuadByHoughLines(edgeMat: Mat, w: Int, h: Int): Array<Point>? {
        val lines = Mat()
        try {
            Imgproc.HoughLinesP(edgeMat, lines, 1.0, Math.PI / 180.0, 30, 32.0, 15.0)
            if (lines.rows() < 4) return null

            val segments = ArrayList<LineSeg>()
            for (r in 0 until lines.rows()) {
                val vec = lines.get(r, 0) ?: continue
                val p1 = Point(vec[0], vec[1])
                val p2 = Point(vec[2], vec[3])
                val lineLen = hypot(p2.x - p1.x, p2.y - p1.y)
                if (lineLen >= 30.0) {
                    segments.add(LineSeg(p1, p2, lineLen))
                }
            }
            if (segments.size < 4) return null

            val angles = segments.map { seg ->
                val ang = Math.toDegrees(atan2(seg.p2.y - seg.p1.y, seg.p2.x - seg.p1.x))
                (ang + 180.0) % 180.0
            }

            // Find dominant angle by weighted histogram over 18 10-degree bins
            var bestPrimaryAngle = 0.0
            var maxWeight = 0.0
            for (bin in 0 until 18) {
                val centerAngle = bin * 10.0
                var currentWeight = 0.0
                for (i in segments.indices) {
                    val diff = abs(angles[i] - centerAngle).let { if (it > 90.0) 180.0 - it else it }
                    if (diff < 18.0) {
                        currentWeight += segments[i].length
                    }
                }
                if (currentWeight > maxWeight) {
                    maxWeight = currentWeight
                    bestPrimaryAngle = centerAngle
                }
            }

            val familyA = ArrayList<LineSeg>()
            val familyB = ArrayList<LineSeg>()
            for (i in segments.indices) {
                val diffA = abs(angles[i] - bestPrimaryAngle).let { if (it > 90.0) 180.0 - it else it }
                if (diffA < 25.0) {
                    familyA.add(segments[i])
                } else {
                    val perpAngle = (bestPrimaryAngle + 90.0) % 180.0
                    val diffB = abs(angles[i] - perpAngle).let { if (it > 90.0) 180.0 - it else it }
                    if (diffB < 25.0) {
                        familyB.add(segments[i])
                    }
                }
            }

            if (familyA.size < 2 || familyB.size < 2) return null

            val cx = w / 2.0
            val cy = h / 2.0

            // Project Family A midpoints along normal to Family A
            val radA = Math.toRadians(bestPrimaryAngle + 90.0)
            val normAx = cos(radA); val normAy = sin(radA)
            val cProjA = cx * normAx + cy * normAy

            var lineA1: LineSeg? = null
            var lineA2: LineSeg? = null
            var minProjA = Double.MAX_VALUE
            var maxProjA = -Double.MAX_VALUE

            for (seg in familyA) {
                val mx = (seg.p1.x + seg.p2.x) / 2.0
                val my = (seg.p1.y + seg.p2.y) / 2.0
                val proj = mx * normAx + my * normAy
                if (proj < cProjA && proj < minProjA) {
                    minProjA = proj
                    lineA1 = seg
                } else if (proj >= cProjA && proj > maxProjA) {
                    maxProjA = proj
                    lineA2 = seg
                }
            }

            // Project Family B midpoints along normal to Family B
            val radB = Math.toRadians(bestPrimaryAngle)
            val normBx = cos(radB); val normBy = sin(radB)
            val cProjB = cx * normBx + cy * normBy

            var lineB1: LineSeg? = null
            var lineB2: LineSeg? = null
            var minProjB = Double.MAX_VALUE
            var maxProjB = -Double.MAX_VALUE

            for (seg in familyB) {
                val mx = (seg.p1.x + seg.p2.x) / 2.0
                val my = (seg.p1.y + seg.p2.y) / 2.0
                val proj = mx * normBx + my * normBy
                if (proj < cProjB && proj < minProjB) {
                    minProjB = proj
                    lineB1 = seg
                } else if (proj >= cProjB && proj > maxProjB) {
                    maxProjB = proj
                    lineB2 = seg
                }
            }

            if (lineA1 != null && lineA2 != null && lineB1 != null && lineB2 != null) {
                val p0 = intersectLineSegments(lineA1, lineB1) ?: return null
                val p1 = intersectLineSegments(lineA1, lineB2) ?: return null
                val p2 = intersectLineSegments(lineA2, lineB2) ?: return null
                val p3 = intersectLineSegments(lineA2, lineB1) ?: return null

                val quad = arrayOf(p0, p1, p2, p3)
                val ordered = Corner.orderQuad(quad)
                val geom = Corner.evaluateGeometry(ordered)
                if (geom.isConvex) {
                    return ordered
                }
            }
        } catch (_: Throwable) {
        } finally {
            lines.release()
        }
        return null
    }

    private data class LineSeg(val p1: Point, val p2: Point, val length: Double)

    private fun intersectLineSegments(l1: LineSeg, l2: LineSeg): Point? {
        val x1 = l1.p1.x; val y1 = l1.p1.y; val x2 = l1.p2.x; val y2 = l1.p2.y
        val x3 = l2.p1.x; val y3 = l2.p1.y; val x4 = l2.p2.x; val y4 = l2.p2.y

        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-4) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        val px = x1 + t * (x2 - x1)
        val py = y1 + t * (y2 - y1)
        return Point(px, py)
    }

    /**
     * Extracts contours and evaluates 4-corner candidates using multi-epsilon poly approximation,
     * thumb/finger occlusion recovery via dominant quad simplification, and convex hull bounding.
     * The macro-luminance passes can request RETR_LIST to preserve a sheet
     * boundary when its background touches the image border. Other passes
     * retain RETR_EXTERNAL to avoid unnecessary inner-text work each frame.
     */
    private fun extractContoursAndQuads(
        edgeOrBinaryMat: Mat,
        frameArea: Double,
        retrievalMode: Int = Imgproc.RETR_EXTERNAL,
        onCandidateFound: (Array<Point>) -> Unit
    ) {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                edgeOrBinaryMat, contours, hierarchy,
                retrievalMode, Imgproc.CHAIN_APPROX_SIMPLE
            )
            if (contours.isEmpty()) return

            val filtered = contours
                .filter { Imgproc.contourArea(it) / frameArea >= 0.030 }
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(14)

            for (contour in filtered) {
                // OpenCV's toArray() allocates and copies the *entire* contour.
                // Calling it once per hull vertex made large document outlines
                // quadratic in work and could starve the next preview frame.
                val contourPoints = contour.toArray()
                val c2f = MatOfPoint2f(*contourPoints)
                try {
                    val peri = Imgproc.arcLength(c2f, true)

                    // 1. Direct Polygon Approximation across multiple epsilons with thumb recovery
                    for (eps in EPSILON_VALUES) {
                        val approx = MatOfPoint2f()
                        try {
                            Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                            val pts = approx.toArray()
                            if (pts.size == 4) {
                                val ordered = Corner.orderQuad(pts)
                                onCandidateFound(ordered)
                            } else if (pts.size in 5..8) {
                                // Thumb / finger occlusion recovery: extract 4 dominant vertices
                                ContourDetector.extractDominantQuad(pts)?.let { dominantQuad ->
                                    val ordered = Corner.orderQuad(dominantQuad)
                                    onCandidateFound(ordered)
                                }
                            }
                        } finally {
                            approx.release()
                        }
                    }

                    // 2. Convex Hull 4-point extraction (essential for paper edges & folded corners)
                    val hullIndices = MatOfInt()
                    try {
                        Imgproc.convexHull(contour, hullIndices)
                        val hullPoints = Array(hullIndices.rows()) { idx ->
                            val ptIndex = hullIndices.get(idx, 0)[0].toInt()
                            contourPoints[ptIndex]
                        }
                        val hull2f = MatOfPoint2f(*hullPoints)
                        try {
                            val hullPeri = Imgproc.arcLength(hull2f, true)
                            for (eps in floatArrayOf(0.012f, 0.018f, 0.026f, 0.036f, 0.048f, 0.062f)) {
                                val hullApprox = MatOfPoint2f()
                                try {
                                    Imgproc.approxPolyDP(hull2f, hullApprox, eps * hullPeri, true)
                                    val hPts = hullApprox.toArray()
                                    if (hPts.size == 4) {
                                        val ordered = Corner.orderQuad(hPts)
                                        onCandidateFound(ordered)
                                    } else if (hPts.size in 5..8) {
                                        ContourDetector.extractDominantQuad(hPts)?.let { dominantQuad ->
                                            val ordered = Corner.orderQuad(dominantQuad)
                                            onCandidateFound(ordered)
                                        }
                                    }
                                } finally {
                                    hullApprox.release()
                                }
                            }
                        } finally {
                            hull2f.release()
                        }
                    } finally {
                        hullIndices.release()
                    }

                    // 3. MinAreaRect (Rotated Rect) candidate for solid rectangular document bounding
                    if (Imgproc.contourArea(contour) / frameArea >= 0.045) {
                        val rotRect = Imgproc.minAreaRect(c2f)
                        val boxPts = Array(4) { Point() }
                        rotRect.points(boxPts)
                        val orderedBox = Corner.orderQuad(boxPts)
                        onCandidateFound(orderedBox)
                    }
                } finally {
                    c2f.release()
                }
            }
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun detectFromBitmap(imageProxy: ImageProxy): InternalResult? {
        val bitmap = try {
            imageProxy.toBitmap()
        } catch (_: Throwable) {
            return null
        }
        val rotated = if (imageProxy.imageInfo.rotationDegrees != 0) {
            val m = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            val r = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (r !== bitmap) bitmap.recycle()
            r
        } else bitmap

        val rgba = Mat()
        val gray = Mat()
        val scaled = Mat()

        try {
            org.opencv.android.Utils.bitmapToMat(rotated, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val maxDim = max(rotated.width, rotated.height)
            val scale = (TARGET_LONG_SIDE.toDouble() / maxDim).coerceAtMost(1.0)
            val wW = max(1, (rotated.width * scale).toInt())
            val wH = max(1, (rotated.height * scale).toInt())

            Imgproc.resize(gray, scaled, Size(wW.toDouble(), wH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

            return multiPassDocumentDetection(scaled, wW, wH)
        } finally {
            rgba.release()
            gray.release()
            scaled.release()
            if (!rotated.isRecycled) rotated.recycle()
        }
    }

    private fun copyYPlane(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        val out = ByteArray(width * height)
        val limit = buffer.limit()
        var idx = 0
        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                val pos = rowStart + x * pixelStride
                out[idx++] = if (pos in 0 until limit) buffer.get(pos) else 0
            }
        }
        return out
    }

    private fun computeMedian(mat: Mat): Double {
        val hist = IntArray(256)
        val maxSamples = 100_000
        val totalPixels = mat.rows() * mat.cols()
        if (totalPixels <= 0) return 128.0

        // ceil() keeps the number of samples bounded, including odd frame
        // dimensions. The old floor-sized sample array could overflow when
        // (width or height) % step != 0, aborting the entire live detection.
        val step = medianSampleStep(mat.cols(), mat.rows(), maxSamples)
        val rowPixels = ByteArray(mat.cols())
        var sampleCount = 0
        for (y in 0 until mat.rows() step step) {
            mat.get(y, 0, rowPixels)
            for (x in 0 until mat.cols() step step) {
                hist[rowPixels[x].toInt() and 0xFF]++
                sampleCount++
            }
        }

        val mid = (sampleCount + 1) / 2
        var total = 0
        for (i in hist.indices) {
            total += hist[i]
            if (total >= mid) return i.toDouble()
        }
        return 128.0
    }

    /** Sampling density independent of frame parity or camera sensor resolution. */
    internal fun medianSampleStep(width: Int, height: Int, maxSamples: Int = 100_000): Int {
        if (width <= 0 || height <= 0) return 1
        val pixels = width.toDouble() * height.toDouble()
        return ceil(sqrt(pixels / maxSamples.coerceAtLeast(1))).toInt().coerceAtLeast(1)
    }

    private fun dist(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun computeQuadAreaNorm(q: Array<Point>, w: Int, h: Int): Float {
        val (p1, p2, p3, p4) = q
        return abs(
            p1.x * p2.y - p2.x * p1.y +
            p2.x * p3.y - p3.x * p2.y +
            p3.x * p4.y - p4.x * p3.y +
            p4.x * p1.y - p1.x * p4.y
        ).toFloat() * 0.5f / (w * h).toFloat()
    }
}
