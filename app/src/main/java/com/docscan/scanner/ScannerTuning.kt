package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import androidx.compose.ui.geometry.Offset
import org.opencv.core.Mat
import org.opencv.core.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Shared production tuning for the live scanner pipeline. */
object ScannerTuning {
    const val A4_RATIO = 1.41421356237
    const val LETTER_RATIO = 1.29411764706
    const val LEGAL_RATIO = 1.64705882353
    const val ID_CARD_RATIO = 1.5857725
    const val RECEIPT_RATIO = 2.20

    const val MIN_LIVE_AREA_RATIO = 0.035
    const val MAX_LIVE_AREA_RATIO = 0.985
    const val MIN_SIDE_EDGE_SUPPORT = 0.08
    const val MIN_AVG_EDGE_SUPPORT = 0.15
    const val MIN_BOUNDARY_CONTRAST = 2.5

    /**
     * An outer quad can genuinely enclose a smaller, false inner-cover quad
     * even if one extrapolated inner corner lies just outside the new bounding
     * rectangle. Axis-aligned bounding-box area is misleading for rotated
     * pages: compare real polygon area and require the old center to be inside.
     */
    fun isLikelyOuterPageUpgrade(previous: List<Offset>, candidate: List<Offset>): Boolean {
        if (previous.size != 4 || candidate.size != 4 ||
            (previous + candidate).any { !it.x.isFinite() || !it.y.isFinite() }) return false

        fun area(points: List<Offset>): Double {
            var sum = 0.0
            for (i in 0..3) {
                val a = points[i]
                val b = points[(i + 1) % 4]
                sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
            }
            return abs(sum) * 0.5
        }

        val previousArea = area(previous)
        val candidateArea = area(candidate)
        if (previousArea < 0.005 || candidateArea < previousArea * 1.20) return false

        val centerX = previous.sumOf { it.x.toDouble() } / 4.0
        val centerY = previous.sumOf { it.y.toDouble() } / 4.0
        var winding = 0
        for (i in 0..3) {
            val a = candidate[i]
            val b = candidate[(i + 1) % 4]
            val cross = (b.x - a.x) * (centerY - a.y) -
                (b.y - a.y) * (centerX - a.x)
            if (abs(cross) < 1e-7) continue
            val sign = if (cross > 0) 1 else -1
            if (winding != 0 && winding != sign) return false
            winding = sign
        }
        return winding != 0
    }

    fun normalizedLongRatio(q: Array<Point>): Double {
        if (q.size != 4) return 0.0
        val top = distance(q[0], q[1])
        val bottom = distance(q[3], q[2])
        val left = distance(q[0], q[3])
        val right = distance(q[1], q[2])
        val w = (top + bottom) * 0.5
        val h = (left + right) * 0.5
        if (w <= 1.0 || h <= 1.0) return 0.0
        return max(w, h) / min(w, h)
    }

    fun paperRatioScore(ratio: Double): Double {
        if (!ratio.isFinite() || ratio <= 0.0) return 0.0
        fun closeness(target: Double, tolerance: Double): Double =
            (1.0 - abs(ratio - target) / tolerance).coerceIn(0.0, 1.0)
        return maxOf(
            closeness(A4_RATIO, 0.32),
            closeness(LETTER_RATIO, 0.28),
            closeness(LEGAL_RATIO, 0.35),
            closeness(ID_CARD_RATIO, 0.28),
            closeness(RECEIPT_RATIO, 1.20)
        )
    }

    /**
     * Samples just inside and outside each detected side. A true paper boundary normally
     * produces a luminance change here; large background polygons usually do not.
     */
    fun boundaryContrast(gray: Mat, q: Array<Point>): Double {
        if (gray.empty() || q.size != 4) return 0.0
        val cx = q.sumOf { it.x } / 4.0
        val cy = q.sumOf { it.y } / 4.0
        val step = (min(gray.width(), gray.height()) * 0.012).coerceIn(3.0, 10.0)
        var total = 0.0
        var count = 0
        for (i in 0..3) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val mx = (a.x + b.x) * 0.5
            val my = (a.y + b.y) * 0.5
            var vx = cx - mx
            var vy = cy - my
            val len = hypot(vx, vy).coerceAtLeast(1.0)
            vx /= len; vy /= len
            for (t in doubleArrayOf(0.25, 0.5, 0.75)) {
                val sx = a.x + (b.x - a.x) * t
                val sy = a.y + (b.y - a.y) * t
                val inside = sample(gray, sx + vx * step, sy + vy * step)
                val outside = sample(gray, sx - vx * step, sy - vy * step)
                if (inside != null && outside != null) {
                    total += abs(inside - outside)
                    count++
                }
            }
        }
        return if (count == 0) 0.0 else total / count
    }

    /**
     * Signed brightness difference across a quadrilateral: positive when its
     * interior is brighter than the background (white paper on a desk).
     * Ruled tables inside a white sheet tend to score near zero because both
     * sides of the thin printed line are the same paper color.
     */
    fun paperBoundaryPolarity(gray: Mat, q: Array<Point>): Double {
        if (gray.empty() || q.size != 4) return 0.0
        val cx = q.sumOf { it.x } / 4.0
        val cy = q.sumOf { it.y } / 4.0
        val step = (min(gray.width(), gray.height()) * 0.018).coerceIn(4.0, 14.0)
        var sum = 0.0
        var count = 0
        for (i in q.indices) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val mx = (a.x + b.x) * 0.5
            val my = (a.y + b.y) * 0.5
            val length = hypot(cx - mx, cy - my).coerceAtLeast(1.0)
            val nx = (cx - mx) / length
            val ny = (cy - my) / length
            for (t in doubleArrayOf(0.25, 0.50, 0.75)) {
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                val inside = sample(gray, px + nx * step, py + ny * step)
                val outside = sample(gray, px - nx * step, py - ny * step)
                if (inside != null && outside != null) {
                    sum += inside - outside
                    count++
                }
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    /**
     * Signed luminance evidence for EACH side separately. An average over all
     * four sides can hide a completely imaginary diagonal cutting across the
     * blank middle of a sheet (three real borders still score highly).
     */
    fun sidePaperBoundaryPolarity(gray: Mat, q: Array<Point>): DoubleArray {
        val result = DoubleArray(4)
        if (gray.empty() || q.size != 4) return result
        val cx = q.sumOf { it.x } / 4.0
        val cy = q.sumOf { it.y } / 4.0
        val step = (min(gray.width(), gray.height()) * 0.018).coerceIn(4.0, 14.0)
        for (i in 0..3) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val mx = (a.x + b.x) / 2.0
            val my = (a.y + b.y) / 2.0
            val length = hypot(cx - mx, cy - my).coerceAtLeast(1.0)
            val nx = (cx - mx) / length
            val ny = (cy - my) / length
            val differences = mutableListOf<Double>()
            for (t in doubleArrayOf(0.20, 0.35, 0.50, 0.65, 0.80)) {
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                val inside = sample(gray, px + nx * step, py + ny * step)
                val outside = sample(gray, px - nx * step, py - ny * step)
                if (inside != null && outside != null) differences.add(inside - outside)
            }
            // The median is resistant to a word, pen or shadow crossing one sample.
            result[i] = differences.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        }
        return result
    }

    /** Minimum interior angle; rules out severely pinched quadrilaterals. */
    fun minInteriorAngleDegrees(q: Array<Point>): Double {
        if (q.size != 4) return 0.0
        var minimum = 180.0
        for (i in 0..3) {
            val p = q[i]
            val prev = q[(i + 3) % 4]
            val next = q[(i + 1) % 4]
            val ax = prev.x - p.x
            val ay = prev.y - p.y
            val bx = next.x - p.x
            val by = next.y - p.y
            val cross = abs(ax * by - ay * bx)
            val dot = ax * bx + ay * by
            minimum = min(minimum, Math.toDegrees(atan2(cross, dot)))
        }
        return minimum
    }

    /**
     * A proposed page must have a physical boundary on each side (or three
     * strong sides with a convincing almost-rectangular fourth). This prevents
     * the scanner from drawing a diagonal across a blank white paper.
     */
    fun hasPhysicalFourSideBoundary(
        edgeSupport: DoubleArray,
        signedSideContrast: DoubleArray,
        minimumAngleDegrees: Double,
        areaRatio: Float
    ): Boolean {
        if (edgeSupport.size != 4 || signedSideContrast.size != 4 ||
            minimumAngleDegrees < 48.0 || !minimumAngleDegrees.isFinite()) return false
        var strongSides = 0
        var faintSides = 0
        for (i in 0..3) {
            val edge = edgeSupport[i]
            val contrast = abs(signedSideContrast[i])
            if (edge >= 0.065 || contrast >= 6.0) strongSides++
            if (edge >= 0.028 || contrast >= 3.0) faintSides++
        }
        return when {
            strongSides == 4 -> true
            strongSides >= 3 && faintSides == 4 && minimumAngleDegrees >= 59.0 -> true
            // One glare-obscured side is reasonable only for a well-aligned
            // large sheet. Never allow a skewed diagonal with no real edge.
            strongSides >= 3 && minimumAngleDegrees >= 78.0 && areaRatio >= 0.20f -> true
            // White-on-white paper can have four faint but genuine borders.
            // Preserve detection when evidence exists on EVERY side.
            faintSides == 4 && minimumAngleDegrees >= 80.0 && areaRatio >= 0.25f -> true
            else -> false
        }
    }

    /**
     * A small rectangle with equally bright paper on both sides is usually a
     * printed table or form field, not a physical sheet boundary.
     */
    fun isLikelyPrintedInnerFrame(
        areaRatio: Float,
        signedPaperContrast: Double,
        absoluteBoundaryContrast: Double
    ): Boolean =
        areaRatio < 0.14f && signedPaperContrast < 6.0 &&
            absoluteBoundaryContrast < 16.0

    /** A large, brighter paper region can have faint Canny edges in soft light. */
    fun isSoftWhitePaperBoundary(
        areaRatio: Float,
        signedPaperContrast: Double,
        absoluteBoundaryContrast: Double,
        supportedSides: Int,
        averageEdgeSupport: Double
    ): Boolean = areaRatio >= 0.20f &&
        signedPaperContrast >= 7.0 &&
        absoluteBoundaryContrast >= 6.0 &&
        supportedSides >= 1 && averageEdgeSupport >= 0.055

    /**
     * A sheet on patterned cloth has a relatively quiet interior, while the
     * cloth's glitter and printed decoration produce many short gradients.
     * Evaluate a quadrilateral's INTERIOR instead of looking only at its four
     * lines. Raw grayscale pixels avoid hundreds of per-candidate JNI calls.
     * This is a soft ranking prior, not a hard ban on printed documents.
     */
    fun pageSurfaceScore(
        grayPixels: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        quad: Array<Point>
    ): Float {
        if (quad.size != 4 || imageWidth < 12 || imageHeight < 12 ||
            grayPixels.size < imageWidth * imageHeight) return 0f

        fun gradient(x: Double, y: Double): Double? {
            val px = x.toInt()
            val py = y.toInt()
            if (px < 2 || px >= imageWidth - 2 ||
                py < 2 || py >= imageHeight - 2) return null
            fun luma(xx: Int, yy: Int): Int =
                grayPixels[yy * imageWidth + xx].toInt() and 0xFF
            val horizontal = abs(luma(px + 2, py) - luma(px - 2, py))
            val vertical = abs(luma(px, py + 2) - luma(px, py - 2))
            return (horizontal + vertical) / 2.0
        }

        fun median(values: List<Double>): Double =
            if (values.isEmpty()) 0.0 else values.sorted()[values.size / 2]

        val interiorGradients = ArrayList<Double>(49)
        // Bilinear interpolation remains inside a rotated/perspective page.
        for (vIndex in 1..7) {
            val v = vIndex / 8.0
            for (uIndex in 1..7) {
                val u = uIndex / 8.0
                val topX = quad[0].x * (1.0 - u) + quad[1].x * u
                val topY = quad[0].y * (1.0 - u) + quad[1].y * u
                val bottomX = quad[3].x * (1.0 - u) + quad[2].x * u
                val bottomY = quad[3].y * (1.0 - u) + quad[2].y * u
                gradient(topX * (1.0 - v) + bottomX * v,
                    topY * (1.0 - v) + bottomY * v)?.let(interiorGradients::add)
            }
        }
        if (interiorGradients.size < 24) return 0f

        val cx = quad.sumOf { it.x } / 4.0
        val cy = quad.sumOf { it.y } / 4.0
        val exteriorGradients = ArrayList<Double>(16)
        val outsideStep = (min(imageWidth, imageHeight) * 0.025).coerceIn(7.0, 16.0)
        for (i in 0..3) {
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            val midpointX = (a.x + b.x) / 2.0
            val midpointY = (a.y + b.y) / 2.0
            val dx = midpointX - cx
            val dy = midpointY - cy
            val d = hypot(dx, dy).coerceAtLeast(1.0)
            val nx = dx / d
            val ny = dy / d
            for (t in doubleArrayOf(0.2, 0.4, 0.6, 0.8)) {
                val x = a.x + (b.x - a.x) * t + nx * outsideStep
                val y = a.y + (b.y - a.y) * t + ny * outsideStep
                gradient(x, y)?.let(exteriorGradients::add)
            }
        }

        val insideTexture = median(interiorGradients)
        val outsideTexture = if (exteriorGradients.size >= 4) {
            median(exteriorGradients)
        } else insideTexture
        val smoothPage = ((26.0 - insideTexture) / 23.0).coerceIn(0.0, 1.0)
        val materialSeparation = ((outsideTexture - insideTexture) / 22.0)
            .coerceIn(0.0, 1.0)
        return (0.78 * smoothPage + 0.22 * materialSeparation).toFloat()
    }

    /** Faint gray paper on brighter cloth must not be rejected for dark polarity. */
    fun isSoftLowTexturePageBoundary(
        areaRatio: Float,
        signedPaperContrast: Double,
        boundaryContrast: Double,
        supportedSides: Int,
        averageEdgeSupport: Double,
        surfaceScore: Float
    ): Boolean = areaRatio >= 0.15f && surfaceScore >= 0.68f &&
        abs(signedPaperContrast) >= 4.0 && boundaryContrast >= 4.0 &&
        supportedSides >= 1 && averageEdgeSupport >= 0.045

    /** Prefer actual full-sheet contours to smaller, high-contrast form cells. */
    fun outerPagePriority(
        confidence: Float,
        areaRatio: Float,
        signedPaperContrast: Double,
        surfaceScore: Float = 0f
    ): Float {
        val area = areaRatio.coerceIn(0f, 1f)
        val areaBonus = (kotlin.math.sqrt(area.toDouble()) * 0.55).toFloat().coerceAtMost(0.46f)
        val innerPenalty = if (area < 0.16f) 0.12f else 0f
        val materialContrast = if (surfaceScore >= 0.68f) {
            abs(signedPaperContrast)
        } else signedPaperContrast.coerceAtLeast(0.0)
        val paperBonus = (materialContrast / 32.0).coerceIn(0.0, 1.0).toFloat() * 0.22f
        // A smooth physical sheet should outrank a high-contrast decorative
        // polygon, even when the decoration has stronger Canny edges.
        val surfaceWeight = if (area >= 0.14f) 0.55f else 0.28f
        return confidence + areaBonus + paperBonus - innerPenalty +
            surfaceWeight * surfaceScore.coerceIn(0f, 1f)
    }

    private fun sample(gray: Mat, x: Double, y: Double): Double? {
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until gray.cols() || iy !in 0 until gray.rows()) return null
        return gray.get(iy, ix)?.firstOrNull()
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
