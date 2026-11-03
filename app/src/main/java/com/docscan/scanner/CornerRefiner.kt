package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
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
 * High-Precision Subpixel Corner Refinement Engine.
 *
 * Refines approximate contour vertices to subpixel accuracy in three layered passes:
 *  A. Perpendicular gradient-point search along each of the two edges meeting at the
 *     corner, followed by a total-least-squares (orthogonal regression) line fit.
 *     This is robust to broken, dashed, or blurry document edges because it uses
 *     every edge sample along the side rather than a single Hough segment.
 *  B. Local ROI HoughLinesP line detection (fallback for when pass A can't collect
 *     enough edge samples, e.g. near the image border or on very low-contrast edges).
 *  C. OpenCV cornerSubPix gradient optimization as a final subpixel polish.
 *
 * ROI size and maximum allowed drift both scale with image resolution so behaviour
 * stays consistent whether the source frame is a 720p preview or a 12MP still.
 */
object CornerRefiner {

    // Fallback bounds used when clamping adaptive ROI sizing
    private const val MIN_ROI_HALF_SIZE = 12
    private const val MAX_ROI_HALF_SIZE = 40

    /**
     * Refines all four corners of the candidate quadrilateral.
     */
    fun refineCorners(
        grayMat: Mat,
        edgeMat: Mat,
        quad: Array<Point>
    ): Array<Point> {
        if (quad.size != 4) return quad

        val refined = Array(4) { Point(quad[it].x, quad[it].y) }
        val imgW = grayMat.width()
        val imgH = grayMat.height()
        val diag = hypot(imgW.toDouble(), imgH.toDouble())

        val roiHalfSize = (diag * 0.025).toInt().coerceIn(MIN_ROI_HALF_SIZE, MAX_ROI_HALF_SIZE)
        val maxDrift = (diag * 0.03).coerceIn(15.0, 60.0)

        for (i in 0 until 4) {
            val curr = quad[i]
            val prev = quad[(i + 3) % 4]
            val next = quad[(i + 1) % 4]

            // Pass A + B: analytic edge-based corner estimate (gradient search -> LSQ,
            // falling back to Hough-in-ROI if not enough edge samples were found)
            val edgeCorner = refineByEdgeIntersection(
                edgeMat = edgeMat,
                corner = curr,
                prevCorner = prev,
                nextCorner = next,
                imgWidth = imgW,
                imgHeight = imgH,
                roiHalfSize = roiHalfSize
            )

            val baseCorner = if (edgeCorner != null && distance(curr, edgeCorner) <= maxDrift) {
                edgeCorner
            } else {
                curr
            }

            // Pass C: subpixel gradient refinement using cornerSubPix
            val subPixCorner = refineWithCornerSubPix(grayMat, baseCorner, imgW, imgH, diag)

            refined[i] = if (subPixCorner != null && distance(curr, subPixCorner) <= maxDrift) {
                subPixCorner
            } else {
                baseCorner
            }
        }

        // Geometric sanity validation: must remain strictly convex and clockwise
        val orderedRefined = ContourDetector.orderQuadClockwise(refined)
        return if (ContourDetector.isValidClockwiseConvex(orderedRefined)) {
            orderedRefined
        } else {
            quad // Fallback to original if refinement deformed geometry
        }
    }

    /**
     * Finds the intersection of the two edges meeting at [corner].
     * Tries the analytic gradient-search + least-squares fit first, and only falls
     * back to a small ROI HoughLinesP search if that doesn't yield enough samples.
     */
    private fun refineByEdgeIntersection(
        edgeMat: Mat,
        corner: Point,
        prevCorner: Point,
        nextCorner: Point,
        imgWidth: Int,
        imgHeight: Int,
        roiHalfSize: Int
    ): Point? {
        // --- Pass A: perpendicular gradient-point search along each expected edge ---
        val pts1 = searchEdgePointsAlongDirection(edgeMat, corner, prevCorner, imgWidth, imgHeight)
        val pts2 = searchEdgePointsAlongDirection(edgeMat, corner, nextCorner, imgWidth, imgHeight)

        if (pts1.size >= 4 && pts2.size >= 4) {
            val line1 = LineCoeffs.fromPointsLeastSquares(pts1)
            val line2 = LineCoeffs.fromPointsLeastSquares(pts2)
            if (line1 != null && line2 != null) {
                val intersection = line1.intersect(line2)
                if (intersection != null) return intersection
            }
        }

        // --- Pass B: fallback to local ROI HoughLinesP ---
        return refineByRoiHough(edgeMat, corner, prevCorner, nextCorner, imgWidth, imgHeight, roiHalfSize)
    }

    /**
     * Walks from [corner] toward [target] and, at each step along that expected edge
     * direction, searches perpendicular to it for the strongest nearby edge pixel.
     * Returns the set of located edge points, which trace the true document edge even
     * when it's dashed, blurry, or partially occluded.
     */
    private fun searchEdgePointsAlongDirection(
        edgeMat: Mat,
        corner: Point,
        target: Point,
        imgWidth: Int,
        imgHeight: Int
    ): List<Point> {
        val points = mutableListOf<Point>()
        val dx = target.x - corner.x
        val dy = target.y - corner.y
        val length = hypot(dx, dy)
        if (length < 6.0) return points

        val ux = dx / length
        val uy = dy / length
        // Perpendicular unit vector
        val px = -uy
        val py = ux

        // Sample along the middle portion of the edge (skip near the corner itself,
        // where the two edges' influence overlaps, and don't walk past ~45% of the
        // side length so we stay clear of the neighboring corner too)
        val maxWalk = min(length * 0.45, 120.0)
        val searchRadius = 5
        var step = 3.0
        while (step < maxWalk) {
            val cx = corner.x + ux * step
            val cy = corner.y + uy * step

            var bestVal = 0.0
            var bestOffset = 0
            for (k in -searchRadius..searchRadius) {
                val sx = (cx + px * k).toInt().coerceIn(0, imgWidth - 1)
                val sy = (cy + py * k).toInt().coerceIn(0, imgHeight - 1)
                val v = edgeMat.get(sy, sx)?.get(0) ?: 0.0
                if (v > bestVal) {
                    bestVal = v
                    bestOffset = k
                }
            }
            if (bestVal > 100.0) {
                points.add(Point(cx + px * bestOffset, cy + py * bestOffset))
            }
            step += 3.0
        }
        return points
    }

    /**
     * Local ROI HoughLinesP-based fallback, used when the analytic edge search above
     * doesn't collect enough points (e.g. corner sits very close to the frame border).
     */
    private fun refineByRoiHough(
        edgeMat: Mat,
        corner: Point,
        prevCorner: Point,
        nextCorner: Point,
        imgWidth: Int,
        imgHeight: Int,
        roiHalfSize: Int
    ): Point? {
        val roiLeft = (corner.x - roiHalfSize).toInt().coerceIn(0, imgWidth - 1)
        val roiTop = (corner.y - roiHalfSize).toInt().coerceIn(0, imgHeight - 1)
        val roiRight = (corner.x + roiHalfSize).toInt().coerceIn(0, imgWidth - 1)
        val roiBottom = (corner.y + roiHalfSize).toInt().coerceIn(0, imgHeight - 1)

        val roiWidth = roiRight - roiLeft
        val roiHeight = roiBottom - roiTop
        if (roiWidth < 8 || roiHeight < 8) return null

        val roiRect = Rect(roiLeft, roiTop, roiWidth, roiHeight)
        val roiEdges = Mat(edgeMat, roiRect)

        try {
            val lines = Mat()
            Imgproc.HoughLinesP(
                roiEdges,
                lines,
                1.0,
                Math.PI / 180.0,
                8, // threshold
                8.0, // minLineLength
                4.0  // maxLineGap
            )

            if (lines.rows() < 2) {
                lines.release()
                return null
            }

            // Expected unit direction vectors of the two incoming edges
            val d1Len = hypot(prevCorner.x - corner.x, prevCorner.y - corner.y).coerceAtLeast(1.0)
            val d1x = (prevCorner.x - corner.x) / d1Len
            val d1y = (prevCorner.y - corner.y) / d1Len

            val d2Len = hypot(nextCorner.x - corner.x, nextCorner.y - corner.y).coerceAtLeast(1.0)
            val d2x = (nextCorner.x - corner.x) / d2Len
            val d2y = (nextCorner.y - corner.y) / d2Len

            var bestLine1: LineCoeffs? = null
            var bestLine2: LineCoeffs? = null
            var bestScore1 = -1.0
            var bestScore2 = -1.0

            for (r in 0 until lines.rows()) {
                val vec = lines.get(r, 0) ?: continue
                val x1 = vec[0] + roiLeft
                val y1 = vec[1] + roiTop
                val x2 = vec[2] + roiLeft
                val y2 = vec[3] + roiTop

                val lineLen = hypot(x2 - x1, y2 - y1)
                if (lineLen < 6.0) continue

                val lx = (x2 - x1) / lineLen
                val ly = (y2 - y1) / lineLen

                // Dot products to find which edge this line segment belongs to
                val dot1 = abs(lx * d1x + ly * d1y)
                val dot2 = abs(lx * d2x + ly * d2y)

                if (dot1 > 0.70 && dot1 > bestScore1) {
                    bestScore1 = dot1
                    bestLine1 = LineCoeffs.fromTwoPoints(Point(x1, y1), Point(x2, y2))
                }
                if (dot2 > 0.70 && dot2 > bestScore2) {
                    bestScore2 = dot2
                    bestLine2 = LineCoeffs.fromTwoPoints(Point(x1, y1), Point(x2, y2))
                }
            }

            lines.release()

            if (bestLine1 != null && bestLine2 != null) {
                return bestLine1.intersect(bestLine2)
            }
        } catch (e: Exception) {
            // ignored
        } finally {
            roiEdges.release()
        }

        return null
    }

    /**
     * Subpixel gradient refinement using OpenCV's cornerSubPix. Window size scales
     * gently with image resolution.
     */
    private fun refineWithCornerSubPix(
        grayMat: Mat,
        corner: Point,
        imgWidth: Int,
        imgHeight: Int,
        diag: Double
    ): Point? {
        val margin = 6.0
        if (corner.x < margin || corner.x > imgWidth - margin ||
            corner.y < margin || corner.y > imgHeight - margin
        ) {
            return null
        }

        try {
            val cornerMat = MatOfPoint2f(Point(corner.x, corner.y))
            val winHalf = (diag * 0.003).coerceIn(3.0, 10.0)
            val winSize = Size(winHalf, winHalf)
            val zeroZone = Size(-1.0, -1.0)
            val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.05)

            Imgproc.cornerSubPix(grayMat, cornerMat, winSize, zeroZone, criteria)
            val refinedPts = cornerMat.toArray()
            cornerMat.release()

            if (refinedPts.isNotEmpty()) {
                val pt = refinedPts[0]
                if (!pt.x.isNaN() && !pt.y.isNaN()) {
                    return Point(
                        pt.x.coerceIn(0.0, imgWidth.toDouble() - 1.0),
                        pt.y.coerceIn(0.0, imgHeight.toDouble() - 1.0)
                    )
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return null
    }

    private data class LineCoeffs(val a: Double, val b: Double, val c: Double) {
        companion object {
            // Line: a*x + b*y + c = 0
            fun fromTwoPoints(p1: Point, p2: Point): LineCoeffs {
                val a = p1.y - p2.y
                val b = p2.x - p1.x
                val c = p1.x * p2.y - p2.x * p1.y
                val norm = hypot(a, b).coerceAtLeast(1e-6)
                return LineCoeffs(a / norm, b / norm, c / norm)
            }

            /**
             * Total-least-squares (orthogonal regression) line fit through a set of
             * points. Unlike an ordinary y=mx+b regression this handles near-vertical
             * lines correctly, which matters for the left/right sides of a document.
             */
            fun fromPointsLeastSquares(points: List<Point>): LineCoeffs? {
                if (points.size < 2) return null
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

                // Principal axis angle of the point scatter (2x2 covariance eigenvector)
                val theta = 0.5 * atan2(2.0 * sxy, sxx - syy)
                val dirX = cos(theta)
                val dirY = sin(theta)

                // Normal vector defines the line: a*x + b*y + c = 0
                val a = -dirY
                val b = dirX
                val c = -(a * meanX + b * meanY)
                val norm = hypot(a, b).coerceAtLeast(1e-6)
                return LineCoeffs(a / norm, b / norm, c / norm)
            }
        }

        fun intersect(other: LineCoeffs): Point? {
            val det = this.a * other.b - this.b * other.a
            if (abs(det) < 1e-4) return null // Parallel lines
            val x = (this.b * other.c - other.b * this.c) / det
            val y = (other.a * this.c - this.a * other.c) / det
            return Point(x, y)
        }
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
