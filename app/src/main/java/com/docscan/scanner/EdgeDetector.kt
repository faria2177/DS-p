package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Premium Multi-Channel Document Edge Detector & Classifier.
 * Upgraded with Object Recognition and ML-Kit style confidence scoring.
 */
object EdgeDetector {

    private const val TAG = "EdgeDetector"

    enum class DocumentType {
        A4_DOCUMENT, ID_CARD, BUSINESS_CARD, RECEIPT, UNKNOWN
    }

    data class Quad(
        val tl: Point,
        val tr: Point,
        val br: Point,
        val bl: Point,
        val score: Double,
        val contourRef: MatOfPoint? = null
    ) {
        fun ordered(): Array<Point> = arrayOf(tl, tr, br, bl)
    }

    data class ScaleInfo(
        val workingWidth: Int,
        val workingHeight: Int,
        val scaleX: Double,
        val scaleY: Double,
        val factorToFull: Double
    )

    // PREMIUM UPGRADE: Result class now includes DocumentType and absolute Confidence
    data class Result(
        val quad: Quad,
        val scaleInfo: ScaleInfo,
        val config: EdgeDetectionConfig,
        val documentType: DocumentType,
        val confidence: Double
    ) {
        fun toFullSpace(p: Point): Point =
            Point(p.x * scaleInfo.scaleX, p.y * scaleInfo.scaleY)
    }

    fun orderQuad(pts: Array<Point>): Array<Point> = Corner.orderQuad(pts)

    /**
     * Detects document corners and classifies the document type.
     */
    fun detect(
        source: Bitmap,
        config: EdgeDetectionConfig = EdgeDetectionConfig.Default
    ): Result? {
        val origW = source.width
        val origH = source.height
        if (origW < 10 || origH < 10) return null

        val (wW, wH) = scaleKeepingAspect(origW, origH, config.processingLongSide.toDouble())
        val workingBitmap = if (origW != wW || origH != wH) {
            Bitmap.createScaledBitmap(source, wW, wH, true)
        } else {
            source
        }

        val rgba = Mat()
        try {
            Utils.bitmapToMat(workingBitmap, rgba)
        } finally {
            if (workingBitmap !== source) {
                workingBitmap.recycle()
            }
        }

        val canny = Mat()
        val scharrEdges = Mat()
        val adaptEdges = Mat()
        val fusedEdges = Mat()
        val morphClosed = Mat()

        var processedFrame: ProcessedFrame? = null

        try {
            // 1. Paper Preprocessing
            processedFrame = PaperProcessor.processForEdgeDetection(rgba, config)
            val claheGray = processedFrame.claheGray
            val blurred = processedFrame.blurred

            // 2. Document Pre-recognition
            val recognition = DocumentRecognition.evaluate(claheGray, config)

            // 3. Multi-Channel Edge Extraction
            val medianScalar = Core.mean(blurred)
            val med = medianScalar.`val`[0]
            val lowThresh = (med * config.cannyLowFactor + config.cannyLowBias).coerceIn(15.0, 90.0)
            val highThresh = (med * config.cannyHighFactor + config.cannyLowBias * 2.0).coerceIn(45.0, 210.0)
            Imgproc.Canny(blurred, canny, lowThresh, highThresh)

            if (config.useScharr) {
                val gradX = Mat()
                val gradY = Mat()
                val mag32f = Mat()
                val mag8u = Mat()
                try {
                    Imgproc.Scharr(blurred, gradX, CvType.CV_32F, 1, 0)
                    Imgproc.Scharr(blurred, gradY, CvType.CV_32F, 0, 1)
                    Core.magnitude(gradX, gradY, mag32f)
                    Core.convertScaleAbs(mag32f, mag8u)
                    Imgproc.threshold(mag8u, scharrEdges, 35.0, 255.0, Imgproc.THRESH_BINARY)
                } finally {
                    gradX.release(); gradY.release(); mag32f.release(); mag8u.release()
                }
            }

            if (config.useAdaptiveThreshold) {
                val adaptRaw = Mat()
                try {
                    Imgproc.adaptiveThreshold(
                        blurred, adaptRaw, 255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY_INV, 25, 7.0
                    )
                    val kGrad = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                    Imgproc.morphologyEx(adaptRaw, adaptEdges, Imgproc.MORPH_GRADIENT, kGrad)
                    kGrad.release()
                } finally {
                    adaptRaw.release()
                }
            }

            // 4. Advanced Edge Fusion
            canny.copyTo(fusedEdges)
            if (config.useScharr) Core.bitwise_or(fusedEdges, scharrEdges, fusedEdges)
            if (config.useAdaptiveThreshold) Core.bitwise_or(fusedEdges, adaptEdges, fusedEdges)

            val kClose = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(config.morphCloseSize.toDouble(), config.morphCloseSize.toDouble())
            )
            Imgproc.morphologyEx(
                fusedEdges, morphClosed, Imgproc.MORPH_CLOSE, kClose,
                Point(-1.0, -1.0), config.morphCloseIterations
            )
            kClose.release()

            // 5. Candidate Generation & Scoring
            val candidates = ArrayList<Quad>()
            val frameArea = (wW * wH).toDouble()
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()

            try {
                Imgproc.findContours(
                    morphClosed, contours, hierarchy,
                    Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
                )

                val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }.take(30)

                for (contour in sortedContours) {
                    val area = Imgproc.contourArea(contour)
                    val areaRatio = area / frameArea
                    if (areaRatio < config.minAreaRatio * 0.5) continue

                    val c2f = MatOfPoint2f(*contour.toArray())
                    val peri = Imgproc.arcLength(c2f, true)

                    for (eps in floatArrayOf(0.014f, 0.022f, 0.030f, 0.038f)) {
                        val approx = MatOfPoint2f()
                        try {
                            Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                            if (approx.total() == 4L) {
                                val ordered = Corner.orderQuad(approx.toArray())
                                val refined = Corner.refineCornersByEdgeIntersection(
                                    quad = ordered,
                                    contour = contour.toArray(),
                                    edgeMap = fusedEdges,
                                    pixelSnapSearchRadius = 4
                                )
                                val score = scoreQuad(refined, wW, wH, fusedEdges, area, recognition, config)
                                if (score > 0.35) {
                                    candidates.add(Quad(refined[0], refined[1], refined[2], refined[3], score, contour))
                                }
                            }
                        } finally {
                            approx.release()
                        }
                    }

                    val minRect = Imgproc.minAreaRect(c2f)
                    val boxPts = Array(4) { Point() }
                    minRect.points(boxPts)
                    val orderedBox = Corner.orderQuad(boxPts)
                    val boxScore = scoreQuad(orderedBox, wW, wH, fusedEdges, area, recognition, config) * 1.05
                    if (boxScore > 0.32) {
                        candidates.add(Quad(orderedBox[0], orderedBox[1], orderedBox[2], orderedBox[3], boxScore, contour))
                    }
                    c2f.release()
                }
            } finally {
                hierarchy.release()
                contours.forEach { it.release() }
            }

            if (config.useHoughClustering && candidates.isEmpty()) {
                val houghQuad = detectViaHough(fusedEdges, wW, wH, config)
                if (houghQuad != null) {
                    val hScore = scoreQuad(houghQuad.ordered(), wW, wH, fusedEdges, 0.0, recognition, config)
                    if (hScore > 0.38) {
                        candidates.add(houghQuad.copy(score = hScore))
                    }
                }
            }

            if (candidates.isEmpty()) return null

            val best = candidates.maxByOrNull { it.score } ?: return null

            val scaleInfo = ScaleInfo(
                workingWidth = wW,
                workingHeight = wH,
                scaleX = origW.toDouble() / wW.toDouble(),
                scaleY = origH.toDouble() / wH.toDouble(),
                factorToFull = origW.toDouble() / wW.toDouble()
            )

            // PREMIUM UPGRADE: Classify the final best document
            val docType = classifyDocument(best.ordered(), wW, wH)
            
            // Normalize score to a 0.0 - 1.0 confidence metric
            val finalConfidence = (best.score * 1.2).coerceIn(0.0, 1.0)

            return Result(best, scaleInfo, config, docType, finalConfidence)

        } catch (e: Exception) {
            Log.e(TAG, "Document edge detection failed", e)
            return null
        } finally {
            rgba.release()
            processedFrame?.release()
            canny.release()
            scharrEdges.release()
            adaptEdges.release()
            fusedEdges.release()
            morphClosed.release()
        }
    }

    /**
     * PREMIUM UPGRADE: Object Recognition logic to classify document type.
     */
    private fun classifyDocument(quad: Array<Point>, w: Int, h: Int): DocumentType {
        val topLen = distance(quad[0], quad[1])
        val botLen = distance(quad[3], quad[2])
        val leftLen = distance(quad[0], quad[3])
        val rightLen = distance(quad[1], quad[2])

        val avgW = (topLen + botLen) / 2.0
        val avgH = (leftLen + rightLen) / 2.0
        val aspect = max(avgW, avgH) / min(avgW, avgH).coerceAtLeast(1.0)
        val areaRatio = (avgW * avgH) / (w * h).toDouble()

        return when {
            aspect in 1.35..1.65 && areaRatio < 0.30 -> DocumentType.ID_CARD
            aspect in 1.40..1.75 && areaRatio < 0.15 -> DocumentType.BUSINESS_CARD
            aspect in 1.25..1.55 && areaRatio > 0.35 -> DocumentType.A4_DOCUMENT
            aspect > 1.80 && areaRatio > 0.10 -> DocumentType.RECEIPT
            else -> DocumentType.UNKNOWN
        }
    }

    private fun scoreQuad(
        quad: Array<Point>,
        w: Int,
        h: Int,
        edgeMap: Mat,
        contourArea: Double,
        recognition: DocumentRecognitionResult,
        config: EdgeDetectionConfig
    ): Double {
        val geom = Corner.evaluateGeometry(quad)
        if (config.requireConvex && !geom.isConvex) return 0.0
        if (geom.score < 0.42) return 0.0
        if (geom.perpendicularity < 0.35) return 0.0

        val qArea = calculateQuadArea(quad)
        val frameArea = (w * h).toDouble()
        val areaRatio = qArea / frameArea
        if (areaRatio < config.minAreaRatio || areaRatio > config.maxAreaRatio) return 0.0

        val widthTop = distance(quad[0], quad[1])
        val widthBottom = distance(quad[3], quad[2])
        val heightLeft = distance(quad[0], quad[3])
        val heightRight = distance(quad[1], quad[2])

        val avgW = (widthTop + widthBottom) / 2.0
        val avgH = (heightLeft + heightRight) / 2.0
        if (avgW < 20.0 || avgH < 20.0) return 0.0

        val aspect = avgW / avgH
        if (aspect < config.minAspect || aspect > config.maxAspect) return 0.0

        val edgeSupport = Corner.calculateEdgeSupport(quad, edgeMap, config.cornerSearchRadiusPx)

        val areaScore = when {
            areaRatio in 0.10..0.70 -> 1.0
            areaRatio in 0.06..0.82 -> 0.85
            areaRatio in 0.82..0.94 -> 0.35
            else -> 0.25
        }

        val borderMargin = min(w, h) * 0.03
        var borderHits = 0
        for (p in quad) {
            if (p.x < borderMargin || p.x > w - borderMargin ||
                p.y < borderMargin || p.y > h - borderMargin
            ) borderHits++
        }
        val borderPenalty = when (borderHits) {
            0 -> 1.0; 1 -> 0.92; 2 -> 0.70; 3 -> 0.45; else -> 0.25
        }

        val contourMatchScore = if (contourArea > 0.0) {
            (1.0 - abs(qArea - contourArea) / max(qArea, contourArea)).coerceIn(0.0, 1.0)
        } else {
            0.60
        }

        val docRecScore = recognition.confidence.toDouble()

        val totalScore = (
            0.10 * docRecScore +
            config.weightArea * areaScore +
            config.weightGeometry * geom.score +
            (config.weightEdge + 0.08) * edgeSupport +
            config.weightContour * contourMatchScore
        ) * borderPenalty

        return totalScore.coerceIn(0.0, 1.0)
    }

    private fun detectViaHough(edgeMap: Mat, w: Int, h: Int, config: EdgeDetectionConfig): Quad? {
        val lines = Mat()
        try {
            Imgproc.HoughLinesP(
                edgeMap, lines, config.houghRho, config.houghTheta, config.houghThreshold,
                config.houghMinLineLength.toDouble(), config.houghMaxLineGap.toDouble()
            )

            val count = lines.rows()
            if (count < 4) return null

            val segments = ArrayList<LineSeg>()
            for (i in 0 until count) {
                val data = lines.get(i, 0) ?: continue
                val x1 = data[0]; val y1 = data[1]
                val x2 = data[2]; val y2 = data[3]
                val angle = Math.toDegrees(atan2(y2 - y1, x2 - x1))
                val normAngle = (angle + 180.0) % 180.0
                segments.add(LineSeg(Point(x1, y1), Point(x2, y2), normAngle))
            }

            val groupA = ArrayList<LineSeg>()
            val groupB = ArrayList<LineSeg>()

            val primaryAngle = segments.first().angle
            for (seg in segments) {
                val diff = abs(seg.angle - primaryAngle)
                val diff90 = abs(diff - 90.0)
                if (diff < 25.0 || diff > 155.0) {
                    groupA.add(seg)
                } else if (diff90 < 25.0) {
                    groupB.add(seg)
                }
            }

            if (groupA.size < 2 || groupB.size < 2) return null

            val lineA1 = groupA.minByOrNull { (it.p1.x + it.p1.y + it.p2.x + it.p2.y) / 4.0 } ?: return null
            val lineA2 = groupA.maxByOrNull { (it.p1.x + it.p1.y + it.p2.x + it.p2.y) / 4.0 } ?: return null
            val lineB1 = groupB.minByOrNull { (it.p1.x + it.p1.y + it.p2.x + it.p2.y) / 4.0 } ?: return null
            val lineB2 = groupB.maxByOrNull { (it.p1.x + it.p1.y + it.p2.x + it.p2.y) / 4.0 } ?: return null

            val pTL = intersectLines(lineA1, lineB1) ?: return null
            val pTR = intersectLines(lineA1, lineB2) ?: return null
            val pBR = intersectLines(lineA2, lineB2) ?: return null
            val pBL = intersectLines(lineA2, lineB1) ?: return null

            val ordered = Corner.orderQuad(arrayOf(pTL, pTR, pBR, pBL))
            return Quad(ordered[0], ordered[1], ordered[2], ordered[3], 0.60)
        } catch (e: Exception) {
            return null
        } finally {
            lines.release()
        }
    }

    private data class LineSeg(val p1: Point, val p2: Point, val angle: Double)

    private fun intersectLines(l1: LineSeg, l2: LineSeg): Point? {
        val x1 = l1.p1.x; val y1 = l1.p1.y; val x2 = l1.p2.x; val y2 = l1.p2.y
        val x3 = l2.p1.x; val y3 = l2.p1.y; val x4 = l2.p2.x; val y4 = l2.p2.y

        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-4) return null

        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
        val px = x1 + t * (x2 - x1)
        val py = y1 + t * (y2 - y1)
        return Point(px, py)
    }

    fun calculateQuadArea(pts: Array<Point>): Double {
        val (p1, p2, p3, p4) = pts
        return 0.5 * abs(
            (p1.x * p2.y - p2.x * p1.y) +
            (p2.x * p3.y - p3.x * p2.y) +
            (p3.x * p4.y - p4.x * p3.y) +
            (p4.x * p1.y - p1.x * p4.y)
        )
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun scaleKeepingAspect(origW: Int, origH: Int, maxSide: Double): Pair<Int, Int> {
        val longSide = max(origW, origH).toDouble()
        if (longSide <= maxSide) return origW to origH
        val ratio = maxSide / longSide
        val w = (origW * ratio).toInt().coerceAtLeast(1)
        val h = (origH * ratio).toInt().coerceAtLeast(1)
        return w to h
    }
}
