package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent Contour and Quadrilateral Detection Engine.
 * Implements adaptive polygon approximation, convexity checks,
 * clockwise ordering, and structural geometry validation.
 */
object ContourDetector {

    data class QuadCandidate(
        val corners: Array<Point>, // TL, TR, BR, BL
        val area: Double,
        val areaRatio: Double,
        val perimeter: Double,
        val contour: MatOfPoint?,
        val geometryScore: Double,
        val isConvex: Boolean,
        val isFromMinAreaRect: Boolean = false
    ) {
        val topLeft: Point get() = corners[0]
        val topRight: Point get() = corners[1]
        val bottomRight: Point get() = corners[2]
        val bottomLeft: Point get() = corners[3]
    }

    /**
     * Extracts top quadrilateral candidates from a binary or edge image.
     */
    fun findQuadrilaterals(
        binaryMat: Mat,
        minAreaRatio: Double = ScannerTuning.MIN_LIVE_AREA_RATIO,
        maxAreaRatio: Double = ScannerTuning.MAX_LIVE_AREA_RATIO,
        maxCandidates: Int = 10
    ): List<QuadCandidate> {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            if (contours.isEmpty()) return emptyList()

            val imageArea = (binaryMat.width() * binaryMat.height()).toDouble()
            val minArea = imageArea * minAreaRatio
            val maxArea = imageArea * maxAreaRatio

            // Sort contours by area descending
            val sortedContours = contours
                .map { c -> Pair(c, Imgproc.contourArea(c)) }
                .filter { it.second in minArea..maxArea }
                .sortedByDescending { it.second }
                .take(10)

            val candidates = mutableListOf<QuadCandidate>()

            for ((contour, area) in sortedContours) {
                val c2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(c2f, true)

                // Multi-scale epsilon approximation (from tight to loose)
                var approxQuad: Array<Point>? = null
                val epsilons = doubleArrayOf(0.012, 0.018, 0.025, 0.035, 0.048, 0.062)

                for (epsFactor in epsilons) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(c2f, approx, epsFactor * peri, true)
                    val pts = approx.toArray()

                    if (pts.size == 4) {
                        approxQuad = pts
                        break
                    } else if (pts.size in 5..8 && approxQuad == null) {
                        // Attempt to extract 4 dominant vertices from near-quad
                        approxQuad = extractDominantQuad(pts)
                    }
                }

                // If polygon approximation failed, check convex hull
                if (approxQuad == null) {
                    val hullIndices = org.opencv.core.MatOfInt()
                    try {
                        Imgproc.convexHull(contour, hullIndices)
                        val contourArray = contour.toArray()
                        val indicesArray = hullIndices.toArray()
                        val hullPoints = indicesArray.map { contourArray[it] }.toTypedArray()
                        val hull2f = MatOfPoint2f(*hullPoints)
                        val hullPeri = Imgproc.arcLength(hull2f, true)
                        val hullApprox = MatOfPoint2f()
                        Imgproc.approxPolyDP(hull2f, hullApprox, 0.025 * hullPeri, true)
                        if (hullApprox.toArray().size == 4) {
                            approxQuad = hullApprox.toArray()
                        }
                        hull2f.release()
                        hullApprox.release()
                    } catch (e: Exception) {
                        // ignore hull approximation failure
                    } finally {
                        hullIndices.release()
                    }
                }

                if (approxQuad != null && approxQuad.size == 4) {
                    val ordered = orderQuadClockwise(approxQuad)
                    if (isValidClockwiseConvex(ordered)) {
                        val quadArea = calculatePolygonArea(ordered)
                        val ratio = quadArea / imageArea
                        if (ratio in minAreaRatio..maxAreaRatio) {
                            val geomScore = evaluateGeometry(ordered, binaryMat.width(), binaryMat.height())
                            candidates.add(
                                QuadCandidate(
                                    corners = ordered,
                                    area = quadArea,
                                    areaRatio = ratio,
                                    perimeter = peri,
                                    contour = contour,
                                    geometryScore = geomScore,
                                    isConvex = true
                                )
                            )
                        }
                    }
                } else {
                    // Fallback to MinAreaRect if high area
                    val rotRect = Imgproc.minAreaRect(c2f)
                    val boxPts = Array(4) { Point() }
                    rotRect.points(boxPts)
                    val orderedBox = orderQuadClockwise(boxPts)
                    val boxArea = rotRect.size.width * rotRect.size.height
                    val ratio = boxArea / imageArea
                    if (ratio in (minAreaRatio * 1.2)..maxAreaRatio && area / boxArea.coerceAtLeast(1.0) > 0.72) {
                        val geomScore = evaluateGeometry(orderedBox, binaryMat.width(), binaryMat.height()) * 0.75
                        candidates.add(
                            QuadCandidate(
                                corners = orderedBox,
                                area = boxArea,
                                areaRatio = ratio,
                                perimeter = peri,
                                contour = contour,
                                geometryScore = geomScore,
                                isConvex = true,
                                isFromMinAreaRect = true
                            )
                        )
                    }
                }

                if (candidates.size >= maxCandidates) break
            }

            return candidates.sortedByDescending { candidate ->
                val paperPrior = ScannerTuning.paperRatioScore(ScannerTuning.normalizedLongRatio(candidate.corners))
                candidate.geometryScore * 0.55 + candidate.areaRatio * 0.30 + paperPrior * 0.15
            }

        } catch (e: Exception) {
            return emptyList()
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * Orders 4 points into canonical [TL, TR, BR, BL] order based on centroid and trigonometry.
     */
    fun orderQuadClockwise(pts: Array<Point>): Array<Point> {
        require(pts.size == 4) { "Requires exactly 4 points" }

        val cx = pts.sumOf { it.x } / 4.0
        val cy = pts.sumOf { it.y } / 4.0

        // Sort by polar angle around centroid
        val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }

        // Find top-left candidate: minimum (x + y)
        var tlIndex = 0
        var minSum = Double.MAX_VALUE
        for (i in sorted.indices) {
            val s = sorted[i].x + sorted[i].y
            if (s < minSum) {
                minSum = s
                tlIndex = i
            }
        }

        val ordered = Array(4) { Point() }
        for (i in 0 until 4) {
            ordered[i] = sorted[(tlIndex + i) % 4]
        }

        // Validate clockwise orientation using cross product
        val cross = (ordered[1].x - ordered[0].x) * (ordered[2].y - ordered[1].y) -
                (ordered[1].y - ordered[0].y) * (ordered[2].x - ordered[1].x)
        if (cross < 0) {
            // Counter-clockwise: swap TR and BL to enforce clockwise
            val temp = ordered[1]
            ordered[1] = ordered[3]
            ordered[3] = temp
        }

        return ordered
    }

    /**
     * Verifies strict convexity and clockwise vertex ordering via 4 consecutive cross products.
     */
    fun isValidClockwiseConvex(quad: Array<Point>): Boolean {
        if (quad.size != 4) return false
        val (tl, tr, br, bl) = quad

        // Check cross products of consecutive edges
        val c1 = crossProduct(tl, tr, br)
        val c2 = crossProduct(tr, br, bl)
        val c3 = crossProduct(br, bl, tl)
        val c4 = crossProduct(bl, tl, tr)

        // All cross products must be strictly positive for clockwise convex polygon
        if (c1 <= 0 || c2 <= 0 || c3 <= 0 || c4 <= 0) return false

        // Check minimum corner angles (reject collapsed or degenerate polygons)
        val angles = calculateCornerAngles(quad)
        for (angle in angles) {
            if (angle < 34.0 || angle > 146.0) return false
        }

        // Check edge lengths are non-trivial
        val wTop = distance(tl, tr)
        val wBottom = distance(bl, br)
        val hLeft = distance(tl, bl)
        val hRight = distance(tr, br)

        if (wTop < 20 || wBottom < 20 || hLeft < 20 || hRight < 20) return false

        return true
    }

    /**
     * Cross product of vectors (B - A) x (C - B)
     */
    fun crossProduct(a: Point, b: Point, c: Point): Double {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val bcX = c.x - b.x
        val bcY = c.y - b.y
        return abX * bcY - abY * bcX
    }

    /**
     * Calculates the interior angles (in degrees) of the four vertices.
     */
    fun calculateCornerAngles(quad: Array<Point>): DoubleArray {
        val angles = DoubleArray(4)
        for (i in 0 until 4) {
            val pPrev = quad[(i + 3) % 4]
            val pCurr = quad[i]
            val pNext = quad[(i + 1) % 4]

            val v1x = pPrev.x - pCurr.x
            val v1y = pPrev.y - pCurr.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = hypot(v1x, v1y)
            val mag2 = hypot(v2x, v2y)

            if (mag1 * mag2 == 0.0) {
                angles[i] = 0.0
            } else {
                val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
                angles[i] = Math.toDegrees(Math.acos(cosTheta))
            }
        }
        return angles
    }

    /**
     * Evaluates geometric rectangularity and parallelism score [0.0 .. 1.0].
     */
    fun evaluateGeometry(quad: Array<Point>, imgWidth: Int, imgHeight: Int): Double {
        val angles = calculateCornerAngles(quad)
        var angleScore = 0.0
        for (a in angles) {
            val diff = abs(a - 90.0)
            angleScore += (1.0 - (diff / 45.0)).coerceIn(0.0, 1.0)
        }
        angleScore /= 4.0

        val (tl, tr, br, bl) = quad
        val topLen = distance(tl, tr)
        val botLen = distance(bl, br)
        val leftLen = distance(tl, bl)
        val rightLen = distance(tr, br)

        val widthRatio = min(topLen, botLen) / max(topLen, botLen).coerceAtLeast(1.0)
        val heightRatio = min(leftLen, rightLen) / max(leftLen, rightLen).coerceAtLeast(1.0)
        val parallelismScore = (widthRatio + heightRatio) / 2.0

        // Border margin check: documents right against image boundary get penalty
        val borderMargin = 6.0
        var borderPenalty = 1.0
        for (p in quad) {
            if (p.x < borderMargin || p.x > imgWidth - borderMargin ||
                p.y < borderMargin || p.y > imgHeight - borderMargin
            ) {
                borderPenalty *= 0.85
            }
        }

        return (angleScore * 0.5 + parallelismScore * 0.5) * borderPenalty
    }

    /**
     * Extracts 4 dominant corners from an approximated polygon with 5-8 vertices
     */
    fun extractDominantQuad(pts: Array<Point>): Array<Point>? {
        if (pts.size < 4) return null
        // Select 4 vertices that maximize polygon area
        var bestQuad: Array<Point>? = null
        var maxArea = 0.0

        val n = pts.size
        for (i in 0 until n - 3) {
            for (j in i + 1 until n - 2) {
                for (k in j + 1 until n - 1) {
                    for (l in k + 1 until n) {
                        val candidate = arrayOf(pts[i], pts[j], pts[k], pts[l])
                        val ordered = orderQuadClockwise(candidate)
                        if (isValidClockwiseConvex(ordered)) {
                            val area = calculatePolygonArea(ordered)
                            if (area > maxArea) {
                                maxArea = area
                                bestQuad = ordered
                            }
                        }
                    }
                }
            }
        }
        return bestQuad
    }

    fun calculatePolygonArea(pts: Array<Point>): Double {
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
