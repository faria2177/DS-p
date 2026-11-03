package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.RectF
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Production Document Boundary Detection Pipeline.
 * Executes a prioritized multi-stage detection algorithm:
 * 1. Multi-channel Contour Quadrilateral Detection (Grayscale+CLAHE Canny, Adaptive
 *    Threshold, and HSV-Saturation Canny — catching edges that are low-contrast in
 *    luminance but distinct in color, e.g. white paper on a similarly-bright desk)
 * 2. Dominant Line Detection & Analytical Intersection (HoughLinesP), with
 *    resolution-adaptive thresholds and multi-segment least-squares side fitting
 * 3. Morphological Contrast Gradient Fallback
 * 4. Subpixel Corner Refinement (see CornerRefiner)
 * 5. Weighted Multi-Metric Quadrilateral Scoring, including a corner-angle
 *    plausibility check, to reject false positives (tables, laptop bezels, etc.)
 */
class DocumentBoundaryDetector(
    val areaWeight: Double = 0.20,
    val edgeWeight: Double = 0.20,
    val geometryWeight: Double = 0.20,
    val cornerWeight: Double = 0.15,
    val aspectRatioWeight: Double = 0.10,
    val stabilityWeight: Double = 0.15
) {

    // Pre-allocated reusable OpenCV Mats to prevent allocations per camera frame
    private val grayMat = Mat()
    private val claheMat = Mat()
    private val bilateralMat = Mat()
    private val cannyMat = Mat()
    private val adaptMat = Mat()
    private val rgbMat = Mat()
    private val hsvMat = Mat()
    private val satMat = Mat()
    private val satCannyMat = Mat()
    private val fusedEdgeMat = Mat()
    private val morphMat = Mat()
    // 5x5 (was 3x3): a stacked/underlying second sheet often casts a thin shadow
    // just inside the true page edge, breaking it into short dashes in the fused
    // edge map. The wider closing kernel bridges that gap so the true outer edge
    // forms one continuous contour instead of losing to a smaller inner shadow loop.
    private val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
    // CLAHE is expensive to construct; keep one instance for the lifetime of the detector.
    private val claheOperator = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))

    fun release() {
        grayMat.release()
        claheMat.release()
        bilateralMat.release()
        cannyMat.release()
        adaptMat.release()
        rgbMat.release()
        hsvMat.release()
        satMat.release()
        satCannyMat.release()
        fusedEdgeMat.release()
        morphMat.release()
        morphKernel.release()
    }

    data class CandidateScore(
        val corners: Array<Point>,
        val totalConfidence: Double,
        val areaScore: Double,
        val edgeScore: Double,
        val geometryScore: Double,
        val cornerScore: Double,
        val aspectRatioScore: Double,
        val stage: DetectionStage
    )

    /**
     * Executes the multi-stage document detection pipeline on an RGBA Mat.
     */
    fun detectBoundary(
        rgbaMat: Mat,
        stabilityScoreBonus: Double = 0.0
    ): DocumentDetectionResult {
        val width = rgbaMat.width()
        val height = rgbaMat.height()
        if (width < 32 || height < 32) return DocumentDetectionResult.EMPTY

        val totalImageArea = (width * height).toDouble()

        // FAST STAGE: grayscale -> light blur -> CLAHE -> auto-Canny.
        // This is intentionally cheap enough to run on every preview frame.
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(grayMat, bilateralMat, Size(5.0, 5.0), 0.0)
        claheOperator.apply(bilateralMat, claheMat)

        val meanLum = Core.mean(claheMat).`val`[0]
        val lowThresh = (meanLum * 0.50).coerceIn(22.0, 82.0)
        val highThresh = (meanLum * 1.18).coerceIn(58.0, 175.0)
        Imgproc.Canny(claheMat, cannyMat, lowThresh, highThresh, 3, true)
        Imgproc.morphologyEx(cannyMat, morphMat, Imgproc.MORPH_CLOSE, morphKernel)

        val candidates = mutableListOf<CandidateScore>()
        fun collectFromCurrentEdges(stage: DetectionStage, maxCandidates: Int = 8) {
            val quads = ContourDetector.findQuadrilaterals(morphMat, maxCandidates = maxCandidates)
            for (cand in quads) {
                val score = scoreCandidate(
                    cand.corners, grayMat, morphMat, width, height, totalImageArea,
                    stabilityScoreBonus, stage
                )
                if (score != null) candidates.add(score)
            }
        }

        collectFromCurrentEdges(DetectionStage.CONTOUR_QUAD, 8)

        // Only pay for adaptive threshold/color edges when the fast pass is not convincing.
        var topScore = candidates.maxOfOrNull { it.totalConfidence } ?: 0.0
        if (topScore < 0.60) {
            Imgproc.adaptiveThreshold(
                bilateralMat, adaptMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                21, 7.0
            )
            Core.bitwise_or(cannyMat, adaptMat, fusedEdgeMat)

            if (!looksGrayscaleSourced(rgbaMat)) {
                Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)
                Core.extractChannel(hsvMat, satMat, 1)
                val meanSat = Core.mean(satMat).`val`[0]
                if (meanSat > 4.0) {
                    Imgproc.Canny(satMat, satCannyMat, 18.0, 72.0)
                    Core.bitwise_or(fusedEdgeMat, satCannyMat, fusedEdgeMat)
                }
            }

            Imgproc.morphologyEx(fusedEdgeMat, morphMat, Imgproc.MORPH_CLOSE, morphKernel)
            collectFromCurrentEdges(DetectionStage.CONTOUR_QUAD, 8)
            topScore = candidates.maxOfOrNull { it.totalConfidence } ?: 0.0
        }

        // STAGE 2: If no strong candidate (> 0.70) found, attempt Line Intersections
        // via HoughLinesP. Hough is run on a resolution-normalized copy of the edge
        // map: this keeps segment-length/gap thresholds meaningful (they'd otherwise
        // behave very differently on a 720p preview vs a 12MP still) and reduces
        // spurious short segments from high-res texture noise.
        if (topScore < 0.56) {
            val maxDim = max(width, height)
            val houghScale = if (maxDim > 900) 900.0 / maxDim else 1.0

            val houghSrc: Mat
            val houghW: Int
            val houghH: Int
            var scaledMat: Mat? = null
            if (houghScale < 1.0) {
                scaledMat = Mat()
                Imgproc.resize(
                    morphMat,
                    scaledMat,
                    Size(width * houghScale, height * houghScale)
                )
                houghSrc = scaledMat
                houghW = scaledMat.width()
                houghH = scaledMat.height()
            } else {
                houghSrc = morphMat
                houghW = width
                houghH = height
            }

            val lineQuadScaled = detectQuadByLineIntersection(houghSrc, houghW, houghH)
            scaledMat?.release()

            val lineQuad = lineQuadScaled?.map {
                Point(it.x / houghScale, it.y / houghScale)
            }?.toTypedArray()

            if (lineQuad != null) {
                val score = scoreCandidate(
                    lineQuad,
                    grayMat,
                    morphMat,
                    width,
                    height,
                    totalImageArea,
                    stabilityScoreBonus,
                    DetectionStage.LINE_INTERSECTION
                )
                if (score != null) {
                    candidates.add(score)
                }
            }
        }

        // STAGE 3: If still no good candidate, try Morphological Gradient fallback
        val topScoreAfterStage2 = candidates.maxByOrNull { it.totalConfidence }?.totalConfidence ?: 0.0
        if (topScoreAfterStage2 < 0.46) {
            val gradMat = Mat()
            val largeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(claheMat, gradMat, Imgproc.MORPH_GRADIENT, largeKernel)
            val otsuMat = Mat()
            Imgproc.threshold(gradMat, otsuMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            val gradCandidates = ContourDetector.findQuadrilaterals(otsuMat, minAreaRatio = 0.12)
            gradMat.release()
            largeKernel.release()
            otsuMat.release()

            for (cand in gradCandidates) {
                val score = scoreCandidate(
                    cand.corners,
                    grayMat,
                    morphMat,
                    width,
                    height,
                    totalImageArea,
                    stabilityScoreBonus,
                    DetectionStage.TEXTURE_CONTRAST_FALLBACK
                )
                if (score != null) {
                    candidates.add(score)
                }
            }
        }

        if (candidates.isEmpty()) {
            return DocumentDetectionResult.EMPTY
        }

        // Select the winning candidate. When a stacked/underlying second sheet or a
        // lifted-edge shadow creates an inner "clean" contour that ties on confidence
        // with the true, larger page contour, plain max-confidence tends to pick the
        // smaller, safer-looking one and clip real content (a common failure when the
        // document sits on top of another paper). Among candidates within a small
        // confidence band of the top score, prefer the one with the larger covered
        // area, since under-cropping loses content while slight over-capture does not.
        // Confidence wins first. Area is only a tiny tie-breaker; otherwise a large
        // table/background quadrilateral can beat the real page.
        val best = candidates.maxByOrNull { candidate ->
            val areaRatio = ContourDetector.calculatePolygonArea(candidate.corners) / totalImageArea
            // Only use the outer-page preference as a tie-break among credible borders;
            // strong edge/geometry evidence still beats a larger background rectangle.
            candidate.totalConfidence + if (areaRatio in 0.16..0.88 && candidate.edgeScore >= 0.25)
                areaRatio * 0.065 else 0.0
        }!!

        // STAGE 4: Corner Refinement to Subpixel Accuracy
        val proposedCorners = CornerRefiner.refineCorners(
            grayMat = grayMat,
            edgeMat = morphMat,
            quad = best.corners
        )
        // Subpixel adjustment is only beneficial if it follows the SAME page boundary.
        // A strong intersection inside a form may otherwise drag a corner to table lines.
        val maxAllowedDrift = hypot(width.toDouble(), height.toDouble()) * 0.025
        val stableRefinement = proposedCorners.size == 4 &&
            ContourDetector.isValidClockwiseConvex(proposedCorners) &&
            proposedCorners.indices.all { index ->
                hypot(proposedCorners[index].x - best.corners[index].x,
                    proposedCorners[index].y - best.corners[index].y) <= maxAllowedDrift
            }
        val refinedCorners = if (stableRefinement) proposedCorners else best.corners

        // Compute normalized bounding box
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in refinedCorners) {
            val px = p.x.toFloat()
            val py = p.y.toFloat()
            if (px < minX) minX = px
            if (py < minY) minY = py
            if (px > maxX) maxX = px
            if (py > maxY) maxY = py
        }
        val bounds = RectF(minX, minY, maxX, maxY)

        val isValid = best.totalConfidence >= 0.46 && ContourDetector.isValidClockwiseConvex(refinedCorners)

        return DocumentDetectionResult(
            corners = refinedCorners.toList(),
            confidence = best.totalConfidence,
            isStable = false, // Updated by stability analyzer
            isValid = isValid,
            documentBounds = bounds,
            perspectiveScore = best.geometryScore,
            areaScore = best.areaScore,
            edgeScore = best.edgeScore,
            geometryScore = best.geometryScore,
            cornerScore = best.cornerScore,
            aspectRatioScore = best.aspectRatioScore,
            stabilityScore = stabilityScoreBonus,
            detectionStage = best.stage
        )
    }

    /**
     * Scores a candidate quadrilateral across multiple geometric, contrast, and edge support criteria.
     * Rejects false positives (tables, laptop bezels, random rectangles).
     */
    private fun scoreCandidate(
        corners: Array<Point>,
        gray: Mat,
        edgeMat: Mat,
        imgWidth: Int,
        imgHeight: Int,
        totalImageArea: Double,
        stabilityScoreBonus: Double,
        stage: DetectionStage
    ): CandidateScore? {
        if (!ContourDetector.isValidClockwiseConvex(corners)) return null

        val quadArea = ContourDetector.calculatePolygonArea(corners)
        val areaRatio = quadArea / totalImageArea

        // Area Score: optimal document size is 30% - 85% of screen. The floor
        // used to be 0.08 (8%), which is far too permissive for this use case —
        // a document being scanned is expected to dominate the frame, so an
        // 8%-of-frame quad is almost never the real page. In practice that low
        // floor let small, high-contrast *internal* regions (e.g. this form's
        // bold section-header bars, which have stronger edge contrast than the
        // paper-vs-desk boundary under some lighting) pass validation and win
        // when the true full-page contour failed to simplify into a clean quad
        // — producing a confidently-wrong small box instead of an honest "still
        // searching" state. Raising the floor forces the pipeline to keep
        // looking (or report low confidence) rather than lock onto a fragment.
        val areaScore = when {
            areaRatio < ScannerTuning.MIN_LIVE_AREA_RATIO -> return null // Reject fragments smaller than a plausible in-frame document
            areaRatio > ScannerTuning.MAX_LIVE_AREA_RATIO -> 0.15 // Reject outer frame
            areaRatio in 0.30..0.85 -> 1.0
            areaRatio < 0.30 -> (areaRatio / 0.30).coerceIn(0.3, 1.0)
            else -> ((0.98 - areaRatio) / 0.13).coerceIn(0.2, 1.0)
        }

        // Geometry Score: Rectangularity & Parallelism
        val geometryScore = ContourDetector.evaluateGeometry(corners, imgWidth, imgHeight)
        if (geometryScore < 0.28) return null // Reject distorted non-quad shapes

        // Corner Angle Plausibility: real document corners should read close to 90
        // degrees even under moderate camera-angle perspective. Reject shapes whose
        // vertices are far from that (parallelograms/slivers from partial occlusion).
        val angleScore = evaluateCornerAngles(corners)
        if (angleScore < 0.18) return null

        // Edge Strength Score: evaluates gradient presence along the 4 edges
        val edgeScore = calculateEdgeSupport(corners, edgeMat)

        // Corner Sharpness Score
        val cornerScore = evaluateCornerSharpness(corners, gray, imgWidth, imgHeight)

        // Aspect Ratio + real boundary contrast score. A large quadrilateral on the
        // desk must not win unless its four sides coincide with a visible paper edge.
        val aspectRatioScore = maxOf(evaluateAspectRatio(corners), ScannerTuning.paperRatioScore(ScannerTuning.normalizedLongRatio(corners)))
        val boundaryContrast = ScannerTuning.boundaryContrast(gray, corners)
        // Low-contrast paper must still be detectable. Reject only when both the
        // sampled boundary contrast and actual edge support are weak.
        if (boundaryContrast < ScannerTuning.MIN_BOUNDARY_CONTRAST && edgeScore < ScannerTuning.MIN_AVG_EDGE_SUPPORT) return null
        val boundaryScore = (boundaryContrast / 22.0).coerceIn(0.0, 1.0)

        // False Positive Rejection: Check for flat non-document surfaces (e.g. blank table)
        // Documents typically have internal text/content variance
        val internalVariance = computeInternalContentVariance(corners, gray)
        var contentBonus = 1.0
        if (internalVariance < 8.0) {
            // Very flat/featureless: might be a blank table or wall section
            contentBonus = 0.85
        }

        // Angle bonus scales confidence down smoothly for corners that deviate from
        // 90 degrees, without being a hard cutoff on its own (that's handled above).
        val angleBonus = 0.5 + 0.5 * angleScore

        val totalConfidence = (
                areaScore * areaWeight +
                edgeScore * edgeWeight +
                geometryScore * geometryWeight +
                cornerScore * cornerWeight +
                aspectRatioScore * aspectRatioWeight +
                stabilityScoreBonus * stabilityWeight
        ) * contentBonus * angleBonus * (0.82 + 0.18 * boundaryScore)

        return CandidateScore(
            corners = corners,
            totalConfidence = totalConfidence.coerceIn(0.0, 1.0),
            areaScore = areaScore,
            edgeScore = edgeScore,
            geometryScore = geometryScore,
            cornerScore = cornerScore,
            aspectRatioScore = aspectRatioScore,
            stage = stage
        )
    }

    /**
     * Evaluates edge gradient support by sampling points along each of the 4 edges.
     */
    private fun calculateEdgeSupport(quad: Array<Point>, edgeMat: Mat): Double {
        var supportedSamples = 0
        var totalSamples = 0
        val samplesPerEdge = 24

        val w = edgeMat.width()
        val h = edgeMat.height()

        for (i in 0 until 4) {
            val p1 = quad[i]
            val p2 = quad[(i + 1) % 4]

            for (s in 1 until samplesPerEdge) {
                val t = s.toDouble() / samplesPerEdge.toDouble()
                val sx = (p1.x + t * (p2.x - p1.x)).toInt().coerceIn(0, w - 1)
                val sy = (p1.y + t * (p2.y - p1.y)).toInt().coerceIn(0, h - 1)

                // Check 3x3 neighborhood around sample point
                var hasEdge = false
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val nx = (sx + dx).coerceIn(0, w - 1)
                        val ny = (sy + dy).coerceIn(0, h - 1)
                        val v = edgeMat.get(ny, nx)?.get(0) ?: 0.0
                        if (v > 100.0) {
                            hasEdge = true
                            break
                        }
                    }
                    if (hasEdge) break
                }

                if (hasEdge) supportedSamples++
                totalSamples++
            }
        }

        return if (totalSamples > 0) (supportedSamples.toDouble() / totalSamples.toDouble()).coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Evaluates corner sharpness by measuring local contrast around each vertex.
     */
    private fun evaluateCornerSharpness(quad: Array<Point>, gray: Mat, w: Int, h: Int): Double {
        var totalSharpness = 0.0
        val r = 6

        for (p in quad) {
            val x = p.x.toInt().coerceIn(r, w - r - 1)
            val y = p.y.toInt().coerceIn(r, h - r - 1)

            var minLum = 255.0
            var maxLum = 0.0

            for (dy in -r..r step 2) {
                for (dx in -r..r step 2) {
                    val lum = gray.get(y + dy, x + dx)?.get(0) ?: 128.0
                    if (lum < minLum) minLum = lum
                    if (lum > maxLum) maxLum = lum
                }
            }
            val contrast = (maxLum - minLum) / 255.0
            totalSharpness += contrast
        }
        return (totalSharpness / 4.0).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates how close each of the quad's 4 interior angles is to 90 degrees.
     * Real page corners stay near-orthogonal even under moderate perspective tilt;
     * this rejects skewed parallelogram-like false positives (open laptop lids,
     * angled tabletops, etc.) that can otherwise pass area/geometry checks.
     */
    private fun evaluateCornerAngles(quad: Array<Point>): Double {
        var total = 0.0
        for (i in 0 until 4) {
            val prev = quad[(i + 3) % 4]
            val curr = quad[i]
            val next = quad[(i + 1) % 4]

            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y

            val mag1 = hypot(v1x, v1y).coerceAtLeast(1e-6)
            val mag2 = hypot(v2x, v2y).coerceAtLeast(1e-6)
            val cosAngle = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1.0, 1.0)
            val angleDeg = Math.toDegrees(acos(cosAngle))

            val deviation = abs(angleDeg - 90.0)
            val angleQuality = when {
                deviation <= 14.0 -> 1.0
                deviation <= 30.0 -> 0.88
                deviation <= 48.0 -> 0.58
                else -> 0.18
            }
            total += angleQuality
        }
        return (total / 4.0).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates whether the quadrilateral conforms to plausible document aspect ratios.
     * Reference ratios: A4 1.414, US Letter 1.294, US Legal 1.647, ID/Business card
     * 1.586. A close match to any of these scores highest; wider receipt-like ratios
     * still score reasonably; extreme sliver shapes are rejected.
     */
    private fun evaluateAspectRatio(quad: Array<Point>): Double {
        val (tl, tr, br, bl) = quad
        val wTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val wBot = hypot(br.x - bl.x, br.y - bl.y)
        val hLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val hRight = hypot(br.x - tr.x, br.y - tr.y)

        val avgW = (wTop + wBot) / 2.0
        val avgH = (hLeft + hRight) / 2.0
        if (avgW == 0.0 || avgH == 0.0) return 0.0

        val ratio = max(avgW, avgH) / min(avgW, avgH)

        val knownRatios = doubleArrayOf(1.414, 1.294, 1.647, 1.586)
        val closestDelta = knownRatios.minOf { abs(it - ratio) }

        return when {
            closestDelta <= 0.06 -> 1.0
            closestDelta <= 0.15 -> 0.90
            ratio in 1.0..2.8 -> 0.75  // Wide or long receipts, misc documents
            ratio in 2.8..4.0 -> 0.45
            else -> 0.15               // Too needle-like
        }
    }

    /**
     * Computes standard deviation of pixel intensities inside the quad.
     */
    private fun computeInternalContentVariance(quad: Array<Point>, gray: Mat): Double {
        val cx = quad.sumOf { it.x } / 4.0
        val cy = quad.sumOf { it.y } / 4.0

        // Sample 9 points in the central region of the document
        val samples = DoubleArray(9)
        var idx = 0
        for (i in -1..1) {
            for (j in -1..1) {
                val sx = (cx + i * 20.0).toInt().coerceIn(0, gray.width() - 1)
                val sy = (cy + j * 20.0).toInt().coerceIn(0, gray.height() - 1)
                samples[idx++] = gray.get(sy, sx)?.get(0) ?: 128.0
            }
        }
        val mean = samples.average()
        val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size
        return sqrt(variance)
    }

    /**
     * Line-intersection detection fallback:
     * Fits 4 dominant lines (Top, Bottom, Left, Right) from Hough line segments.
     * All segments consistent with a given side (not just the single longest one)
     * are pooled and fit with a total-least-squares line, which is far more stable
     * for dashed or partially-occluded page edges than picking one segment.
     */
    private fun detectQuadByLineIntersection(edgeMat: Mat, w: Int, h: Int): Array<Point>? {
        val lines = Mat()
        try {
            // Hough thresholds scale with image diagonal so behavior is consistent
            // across preview-resolution and full-resolution capture frames.
            val diag = hypot(w.toDouble(), h.toDouble())
            val houghThreshold = max(20, (diag / 40.0).toInt())
            val minLineLength = max(20.0, diag * 0.04)
            val maxLineGap = max(8.0, diag * 0.02)

            Imgproc.HoughLinesP(edgeMat, lines, 1.0, Math.PI / 180.0, houghThreshold, minLineLength, maxLineGap)
            if (lines.rows() < 4) return null

            val topPts = mutableListOf<Point>()
            val bottomPts = mutableListOf<Point>()
            val leftPts = mutableListOf<Point>()
            val rightPts = mutableListOf<Point>()

            var bestTopLine: LineSeg? = null
            var bestBottomLine: LineSeg? = null
            var bestLeftLine: LineSeg? = null
            var bestRightLine: LineSeg? = null

            val cy = h / 2.0
            val cx = w / 2.0

            for (r in 0 until lines.rows()) {
                val vec = lines.get(r, 0) ?: continue
                val p1 = Point(vec[0], vec[1])
                val p2 = Point(vec[2], vec[3])
                val lineLen = hypot(p2.x - p1.x, p2.y - p1.y)

                val angle = Math.toDegrees(atan2(p2.y - p1.y, p2.x - p1.x))
                val normAngle = (angle + 180.0) % 180.0
                val midY = (p1.y + p2.y) / 2.0
                val midX = (p1.x + p2.x) / 2.0

                // Horizontal edges (angles near 0 or 180)
                if (normAngle < 35.0 || normAngle > 145.0) {
                    if (midY < cy) {
                        topPts.add(p1); topPts.add(p2)
                        if (bestTopLine == null || lineLen > bestTopLine.length) bestTopLine = LineSeg(p1, p2, lineLen)
                    } else {
                        bottomPts.add(p1); bottomPts.add(p2)
                        if (bestBottomLine == null || lineLen > bestBottomLine.length) bestBottomLine = LineSeg(p1, p2, lineLen)
                    }
                }
                // Vertical edges (angles near 90)
                else if (normAngle in 55.0..125.0) {
                    if (midX < cx) {
                        leftPts.add(p1); leftPts.add(p2)
                        if (bestLeftLine == null || lineLen > bestLeftLine.length) bestLeftLine = LineSeg(p1, p2, lineLen)
                    } else {
                        rightPts.add(p1); rightPts.add(p2)
                        if (bestRightLine == null || lineLen > bestRightLine.length) bestRightLine = LineSeg(p1, p2, lineLen)
                    }
                }
            }

            // Prefer a multi-segment least-squares fit per side; fall back to the
            // single longest segment for that side if too few points were pooled.
            val topLine = buildLineSegFromPoints(topPts) ?: bestTopLine
            val bottomLine = buildLineSegFromPoints(bottomPts) ?: bestBottomLine
            val leftLine = buildLineSegFromPoints(leftPts) ?: bestLeftLine
            val rightLine = buildLineSegFromPoints(rightPts) ?: bestRightLine

            if (topLine != null && bottomLine != null && leftLine != null && rightLine != null) {
                val tl = intersectLines(topLine, leftLine) ?: return null
                val tr = intersectLines(topLine, rightLine) ?: return null
                val br = intersectLines(bottomLine, rightLine) ?: return null
                val bl = intersectLines(bottomLine, leftLine) ?: return null

                val quad = arrayOf(tl, tr, br, bl)
                val ordered = ContourDetector.orderQuadClockwise(quad)
                if (ContourDetector.isValidClockwiseConvex(ordered)) {
                    return ordered
                }
            }
        } catch (e: Exception) {
            // ignored
        } finally {
            lines.release()
        }
        return null
    }

    /**
     * Fits a total-least-squares (orthogonal regression) line through a pool of
     * points gathered from every Hough segment assigned to one side, then returns
     * two synthetic far-apart points along that fitted direction so the result can
     * be intersected the same way as a plain two-point LineSeg.
     */
    private fun buildLineSegFromPoints(points: List<Point>): LineSeg? {
        if (points.size < 4) return null // need multiple segments' worth of points

        val n = points.size.toDouble()
        val meanX = points.sumOf { it.x } / n
        val meanY = points.sumOf { it.y } / n

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (p in points) {
            val dx = p.x - meanX
            val dy = p.y - meanY
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        if (abs(sxx) < 1e-9 && abs(syy) < 1e-9) return null

        val theta = 0.5 * atan2(2.0 * sxy, sxx - syy)
        val dirX = cos(theta)
        val dirY = sin(theta)

        val half = 2000.0
        val p1 = Point(meanX - dirX * half, meanY - dirY * half)
        val p2 = Point(meanX + dirX * half, meanY + dirY * half)
        return LineSeg(p1, p2, 2 * half)
    }

    /**
     * Cheap check (9 pixel samples, no Mat conversion) for whether [rgba] carries
     * real chroma or was synthesized from a single luminance channel (R=G=B
     * everywhere). Lets the saturation-channel edge stage skip itself on a
     * grayscale-sourced frame without needing to know the caller's context.
     */
    private fun looksGrayscaleSourced(rgba: Mat): Boolean {
        val w = rgba.width()
        val h = rgba.height()
        if (w < 4 || h < 4) return false
        val xs = intArrayOf(w / 4, w / 2, 3 * w / 4)
        val ys = intArrayOf(h / 4, h / 2, 3 * h / 4)
        var maxDiff = 0.0
        for (y in ys) {
            for (x in xs) {
                val px = rgba.get(y, x) ?: continue
                if (px.size >= 3) {
                    val diff = max(max(abs(px[0] - px[1]), abs(px[1] - px[2])), abs(px[0] - px[2]))
                    if (diff > maxDiff) maxDiff = diff
                }
            }
        }
        return maxDiff < 1.5
    }

    private data class LineSeg(val p1: Point, val p2: Point, val length: Double)

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
}
