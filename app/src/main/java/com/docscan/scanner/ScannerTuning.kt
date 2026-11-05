package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Mat
import org.opencv.core.Point
import kotlin.math.abs
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

    /** Prefer actual full-sheet contours to smaller, high-contrast form cells. */
    fun outerPagePriority(confidence: Float, areaRatio: Float, signedPaperContrast: Double): Float {
        val area = areaRatio.coerceIn(0f, 1f)
        val areaBonus = (kotlin.math.sqrt(area.toDouble()) * 0.55).toFloat().coerceAtMost(0.46f)
        val innerPenalty = if (area < 0.16f) 0.12f else 0f
        val paperBonus = (signedPaperContrast / 32.0).coerceIn(0.0, 1.0).toFloat() * 0.22f
        return confidence + areaBonus + paperBonus - innerPenalty
    }

    private fun sample(gray: Mat, x: Double, y: Double): Double? {
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until gray.cols() || iy !in 0 until gray.rows()) return null
        return gray.get(iy, ix)?.firstOrNull()
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
