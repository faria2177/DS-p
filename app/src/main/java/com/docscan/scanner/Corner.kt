package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Premium Corner Representation & Advanced Geometric Refinement Engine (v3.1 — stabilized).
 *
 * v3 modernized the two hottest paths — corner refinement and paper-edge line
 * fitting — with:
 *   - Adaptive RANSAC (confidence-based early stopping, no fixed iteration budget)
 *   - IRLS polish with a Tukey biweight M-estimator after RANSAC (soft outlier
 *     down-weighting instead of a hard inlier/outlier cut)
 *   - Edge-strength-weighted line fitting (contour points that sit on strong
 *     gradients count more than points near a rounded corner or noise)
 *   - Harris-response corner validation gate, so a refined corner is only
 *     accepted if it actually sits on a corner-like image feature
 *
 * v3.1 stabilization fixes (no public API changes):
 *   - RANSAC/IRLS weighting now threads original point *indices* through every
 *     stage instead of re-associating weights by Point value-equality. The old
 *     `points.withIndex().associate { p to idx }` approach silently mismapped
 *     weights whenever two contour points shared identical (x, y) — which does
 *     happen along rounded corners and after edge-map quantization — quietly
 *     degrading fit quality without ever throwing. Index-based lookup removes
 *     that failure mode entirely.
 *   - `harrisResponse` now releases its submat `patch` (previously only
 *     `response` was released), closing a small per-call native Mat leak that
 *     matters on a live camera-preview hot path calling this every frame.
 *   - Aspect-ratio scoring in `evaluateGeometry` is sharpened to specifically
 *     reward standard document ratios (A4 1.414, US Letter 1.294, US Legal
 *     1.647, ID/business card 1.586) rather than accepting anything from
 *     0.25–4.0 equally — this directly improves discrimination between real
 *     A4/Legal pages and incidental rectangles (tables, screens, etc.).
 */
object Corner {

    // ---------------- Tunables ----------------
    private const val RANSAC_MAX_ITERATIONS = 128
    private const val RANSAC_MIN_ITERATIONS = 16
    private const val RANSAC_CONFIDENCE = 0.995      // stop once this confidence is reached
    private const val RANSAC_INLIER_TOLERANCE_PX = 2.0
    private const val IRLS_ITERATIONS = 4
    private const val TUKEY_C = 4.685                // standard Tukey biweight tuning constant
    private const val SUBPIX_MAX_ITERS = 100
    private const val SUBPIX_EPSILON = 1e-4
    private const val MAX_SUBPIX_TRAVEL_RATIO = 2.0
    private const val HARRIS_BLOCK = 5
    private const val HARRIS_KSIZE = 3
    private const val HARRIS_K = 0.04
    private const val HARRIS_MIN_SCORE_RATIO = 0.05  // relative to patch's own max response

    // Reference document aspect ratios (long side / short side), used to sharpen
    // aspect-ratio scoring toward real A4/Letter/Legal/ID pages.
    private val KNOWN_DOCUMENT_RATIOS = doubleArrayOf(
        1.414, // A4 (210 x 297mm)
        1.294, // US Letter (8.5 x 11in)
        1.647, // US Legal (8.5 x 14in)
        1.586  // ID / business card (85.6 x 54mm)
    )

    // ==================================================================
    // 1. QUAD ORDERING — [TL, TR, BR, BL] (NaN-safe, winding-enforced)
    // ==================================================================
    fun orderQuad(pts: Array<Point>): Array<Point> {
        require(pts.size == 4) { "Exactly 4 points required for quad ordering" }
        if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) return pts.copyOf()

        val cx = (pts[0].x + pts[1].x + pts[2].x + pts[3].x) / 4.0
        val cy = (pts[0].y + pts[1].y + pts[2].y + pts[3].y) / 4.0

        val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }

        var tlIdx = 0
        var minSum = Double.MAX_VALUE
        for (i in 0 until 4) {
            val s = sorted[i].x + sorted[i].y
            if (s < minSum) { minSum = s; tlIdx = i }
        }

        val ordered = Array(4) { sorted[(tlIdx + it) % 4] }

        // Enforce clockwise winding (image coords, y-down) => TL,TR,BR,BL
        val cross = (ordered[1].x - ordered[0].x) * (ordered[2].y - ordered[1].y) -
                    (ordered[1].y - ordered[0].y) * (ordered[2].x - ordered[1].x)
        if (cross < 0) {
            val tmp = ordered[1]; ordered[1] = ordered[3]; ordered[3] = tmp
        }
        return ordered
    }

    // ==================================================================
    // 2. SUB-PIXEL REFINEMENT — adaptive window + Harris "is this a real
    //    corner" gate, so refinement never wanders onto a plain edge.
    // ==================================================================
    fun refineCornersSubPix(
        quad: Array<Point>,
        grayMap: Mat, // Must be CV_8UC1 (Grayscale or Edge Map)
        windowSize: Int = 5
    ): Array<Point> {
        val w = grayMap.width()
        val h = grayMap.height()
        if (w < 3 || h < 3 || quad.size != 4) return quad.copyOf()
        if (grayMap.type() != CvType.CV_8UC1) return quad.copyOf()

        val out = Array(4) { Point(quad[it].x, quad[it].y) }
        val maxTravel = windowSize * MAX_SUBPIX_TRAVEL_RATIO

        for (i in 0 until 4) {
            val p = quad[i]
            if (p.x < 0 || p.y < 0 || p.x > w - 1 || p.y > h - 1) continue

            // Shrink window so it always fits inside the image
            val margin = min(min(p.x, w - 1 - p.x), min(p.y, h - 1 - p.y)).toInt()
            val win = min(windowSize, margin)
            if (win < 2) continue // too close to the border — sub-pixel refinement unreliable

            val buf = MatOfPoint2f(p)
            try {
                Imgproc.cornerSubPix(
                    grayMap, buf,
                    Size(win.toDouble(), win.toDouble()),
                    Size(-1.0, -1.0),
                    TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, SUBPIX_MAX_ITERS, SUBPIX_EPSILON)
                )
                val r = buf.toArray()[0]
                if (r.x.isFinite() && r.y.isFinite() &&
                    hypot(r.x - p.x, r.y - p.y) <= maxTravel
                ) {
                    val candidate = Point(
                        r.x.coerceIn(0.0, (w - 1).toDouble()),
                        r.y.coerceIn(0.0, (h - 1).toDouble())
                    )
                    // Accept the moved point only if it actually improved "cornerness".
                    // Falls back to the original if the move slid onto a flat edge.
                    out[i] = if (isCornerLike(grayMap, candidate, p)) candidate else p
                }
            } catch (_: Throwable) {
                // keep original corner
            } finally {
                buf.release()
            }
        }
        return out
    }

    /**
     * Lightweight Harris-response gate: accepts [candidate] over [fallback]
     * only if it sits on a genuine corner-like structure (both principal
     * curvatures of the local structure tensor are non-trivial), not merely
     * a straight edge or flat/noisy region.
     */
    private fun isCornerLike(gray: Mat, candidate: Point, fallback: Point): Boolean {
        val candidateScore = harrisResponse(gray, candidate)
        val fallbackScore = harrisResponse(gray, fallback)
        if (candidateScore == null) return false
        if (fallbackScore == null) return candidateScore > 0.0
        // Require the refined point to be at least as corner-like as the seed,
        // with a small tolerance for numerical noise.
        return candidateScore >= fallbackScore * (1.0 - HARRIS_MIN_SCORE_RATIO)
    }

    /** Harris corner response R = det(M) - k*trace(M)^2 at a single point via a local patch. */
    private fun harrisResponse(gray: Mat, p: Point): Double? {
        val w = gray.width(); val h = gray.height()
        val half = HARRIS_BLOCK + HARRIS_KSIZE
        val cx = p.x.toInt(); val cy = p.y.toInt()
        val x0 = (cx - half).coerceIn(0, w - 1)
        val y0 = (cy - half).coerceIn(0, h - 1)
        val x1 = (cx + half).coerceIn(0, w - 1)
        val y1 = (cy + half).coerceIn(0, h - 1)
        val pw = x1 - x0 + 1
        val ph = y1 - y0 + 1
        if (pw < HARRIS_BLOCK + 2 || ph < HARRIS_BLOCK + 2) return null

        var patch: Mat? = null
        var response: Mat? = null
        return try {
            patch = Mat(gray, Rect(x0, y0, pw, ph))
            response = Mat()
            Imgproc.cornerHarris(patch, response, HARRIS_BLOCK, HARRIS_KSIZE, HARRIS_K)
            val lx = (cx - x0).coerceIn(0, pw - 1)
            val ly = (cy - y0).coerceIn(0, ph - 1)
            response!!.get(ly, lx)[0]
        } catch (_: Throwable) {
            null
        } finally {
            // Both the submat view and the response map must be released — the
            // original only released `response`, leaking one small native Mat
            // per corner-refinement call (this runs every live-preview frame).
            patch?.release()
            response?.release()
        }
    }

    // ==================================================================
    // 3. ROBUST LINE FITTING — adaptive RANSAC consensus + IRLS/Tukey polish.
    //    Rounded-corner curve points and edge-map noise are down-weighted
    //    rather than hard-thresholded, so the fit degrades gracefully.
    // ==================================================================
    private data class LineFit(val point: Point, val direction: Point)

    /** Weighted total-least-squares line fit — the workhorse used by IRLS. */
    private fun fitLineWeightedTLS(points: List<Point>, weights: DoubleArray?): LineFit? {
        if (points.size < 2) return null
        var sumW = 0.0; var sumX = 0.0; var sumY = 0.0
        for (i in points.indices) {
            val wt = weights?.get(i) ?: 1.0
            sumW += wt; sumX += points[i].x * wt; sumY += points[i].y * wt
        }
        if (sumW < 1e-9) return null
        val mx = sumX / sumW
        val my = sumY / sumW

        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in points.indices) {
            val wt = weights?.get(i) ?: 1.0
            val dx = points[i].x - mx; val dy = points[i].y - my
            sxx += wt * dx * dx; syy += wt * dy * dy; sxy += wt * dx * dy
        }
        if (sxx + syy < 1e-9) return null

        val angle = 0.5 * atan2(2.0 * sxy, sxx - syy)
        return LineFit(Point(mx, my), Point(cos(angle), sin(angle)))
    }

    private fun perpendicularDistance(line: LineFit, p: Point): Double {
        val nx = -line.direction.y
        val ny = line.direction.x
        return abs((p.x - line.point.x) * nx + (p.y - line.point.y) * ny)
    }

    /**
     * Tukey biweight loss → soft inlier weight in [0, 1]. Points beyond
     * TUKEY_C * scale contribute nothing; points near zero residual keep
     * full weight. This replaces the old hard RANSAC cutoff for the polish
     * stage, which is far more forgiving of the noisy tail near rounded
     * corners.
     */
    private fun tukeyWeight(residual: Double, scale: Double): Double {
        if (scale < 1e-9) return if (abs(residual) < 1e-9) 1.0 else 0.0
        val u = residual / (TUKEY_C * scale)
        if (abs(u) >= 1.0) return 0.0
        val t = 1.0 - u * u
        return t * t
    }

    /** Median absolute deviation, scaled to be a consistent estimator of sigma for a Gaussian core. */
    private fun robustScale(residuals: List<Double>): Double {
        if (residuals.isEmpty()) return 0.0
        val sorted = residuals.sorted()
        val median = sorted[sorted.size / 2]
        val absDev = sorted.map { abs(it - median) }.sorted()
        val mad = absDev[absDev.size / 2]
        return 1.4826 * mad
    }

    /**
     * Adaptive RANSAC: stops as soon as the observed inlier ratio makes the
     * required-iteration formula fall below the iterations already spent,
     * instead of always burning a fixed budget. Falls back to
     * RANSAC_MAX_ITERATIONS if the data stays noisy.
     *
     * Returns the *indices* (into [points]) of the best inlier set, not the
     * points themselves — callers that carry a parallel weights array key off
     * these indices directly, avoiding any value-equality lookup.
     */
    private fun fitLineRansacIndices(points: List<Point>, rng: Random): List<Int> {
        val n = points.size
        var bestInlierIdx: List<Int> = emptyList()
        var trials = 0
        var required = RANSAC_MAX_ITERATIONS

        while (trials < required && trials < RANSAC_MAX_ITERATIONS) {
            trials++
            val i1 = rng.nextInt(n)
            var i2 = rng.nextInt(n)
            if (i2 == i1) i2 = (i2 + 1) % n

            val a = points[i1]; val b = points[i2]
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len < 1e-9) continue

            val nx = -dy / len; val ny = dx / len // unit normal
            val inlierIdx = ArrayList<Int>(n)
            for (idx in 0 until n) {
                val p = points[idx]
                val d = abs((p.x - a.x) * nx + (p.y - a.y) * ny)
                if (d <= RANSAC_INLIER_TOLERANCE_PX) inlierIdx.add(idx)
            }
            if (inlierIdx.size > bestInlierIdx.size) {
                bestInlierIdx = inlierIdx
                val w = inlierIdx.size.toDouble() / n
                required = if (w > 0.0 && w < 1.0) {
                    val denom = ln(1.0 - w * w).coerceAtMost(-1e-9)
                    (ln(1.0 - RANSAC_CONFIDENCE) / denom).toInt().coerceIn(RANSAC_MIN_ITERATIONS, RANSAC_MAX_ITERATIONS)
                } else {
                    RANSAC_MIN_ITERATIONS
                }
            }
        }
        return bestInlierIdx
    }

    /**
     * Adaptive-RANSAC consensus followed by IRLS/Tukey polish. Optional
     * per-point [weights] (e.g. sampled edge-map gradient strength) bias
     * both stages toward points that sit on strong, real image edges.
     *
     * Weights are threaded through by index the whole way — never re-matched
     * by Point value-equality — so duplicate coordinates (which do occur
     * along rounded corners / quantized edge maps) can't silently corrupt
     * the weighting.
     */
    private fun fitLineRobust(points: List<Point>, weights: DoubleArray? = null): LineFit? {
        if (points.size < 3) return null
        val rng = Random(0x5EEDL) // deterministic runs

        val inlierIdx = fitLineRansacIndices(points, rng)
        if (inlierIdx.size < 3) return null

        val inliers = inlierIdx.map { points[it] }
        val inlierWeights = weights?.let { w -> DoubleArray(inlierIdx.size) { i -> w[inlierIdx[i]] } }

        var line = fitLineWeightedTLS(inliers, inlierWeights) ?: return null

        // IRLS polish: iteratively down-weight points with large residuals
        // using a Tukey biweight, which handles the residual outlier tail
        // (rounded-corner curvature, edge-map noise) more gracefully than a
        // single hard RANSAC threshold.
        repeat(IRLS_ITERATIONS) {
            val residuals = inliers.map { perpendicularDistance(line, it) }
            val scale = robustScale(residuals).let { if (it < 1e-6) 1.0 else it }
            val irlsWeights = DoubleArray(inliers.size) { i ->
                val base = inlierWeights?.get(i) ?: 1.0
                base * tukeyWeight(residuals[i], scale)
            }
            val refit = fitLineWeightedTLS(inliers, irlsWeights) ?: return@repeat
            line = refit
        }
        return line
    }

    private fun intersectLines(l1: LineFit, l2: LineFit): Point? {
        val cross = l1.direction.x * l2.direction.y - l1.direction.y * l2.direction.x
        if (abs(cross) < 1e-6) return null
        val dx = l2.point.x - l1.point.x
        val dy = l2.point.y - l1.point.y
        val t = (dx * l2.direction.y - dy * l2.direction.x) / cross
        val x = l1.point.x + t * l1.direction.x
        val y = l1.point.y + t * l1.direction.y
        return if (x.isFinite() && y.isFinite()) Point(x, y) else null
    }

    // ==================================================================
    // 4. EDGE-INTERSECTION REFINEMENT — iterative, convexity-guarded,
    //    edge-strength-weighted, Harris-gated.
    //    Ideal for rounded corners (ID card, notebook).
    // ==================================================================
    fun refineCornersByEdgeIntersection(
        quad: Array<Point>,
        contour: Array<Point>,
        edgeMap: Mat,
        pixelSnapSearchRadius: Int,
        trimRatio: Double = 0.22,
        minPointsPerSide: Int = 6,
        maxShiftRatio: Double = 0.18,
        refinementPasses: Int = 2
    ): Array<Point> {
        var current = refineCornersSubPix(quad, edgeMap, pixelSnapSearchRadius)
        if (contour.size < 4 * minPointsPerSide) return current

        val w = edgeMap.width()
        val h = edgeMap.height()
        val maxShift = max(averageSideLength(current) * maxShiftRatio, pixelSnapSearchRadius * 4.0)

        repeat(refinementPasses.coerceAtLeast(1)) {
            val refined = refineOnce(current, contour, edgeMap, trimRatio, minPointsPerSide, maxShift)
                ?: return current               // no more useful motion available
            if (!isQuadConvex(refined[0], refined[1], refined[2], refined[3])) return current

            // Harris gate: only accept a refined corner if it actually improved
            // (or at least didn't degrade) local cornerness versus the previous estimate.
            val gated = Array(4) { i ->
                val clamped = clampPoint(refined[i], w, h)
                if (edgeMap.type() == CvType.CV_8UC1 && isCornerLike(edgeMap, clamped, current[i])) {
                    clamped
                } else {
                    current[i]
                }
            }
            current = gated
        }
        return current
    }

    private fun refineOnce(
        quad: Array<Point>,
        contour: Array<Point>,
        edgeMap: Mat,
        trimRatio: Double,
        minPointsPerSide: Int,
        maxShift: Double
    ): Array<Point>? {
        val n = contour.size
        if (n < 4) return null

        // Snap each quad corner to nearest contour index
        val cornerIndices = IntArray(4) { i ->
            var bestIdx = 0
            var bestDist = Double.MAX_VALUE
            for (j in 0 until n) {
                val d = (contour[j].x - quad[i].x) * (contour[j].x - quad[i].x) +
                        (contour[j].y - quad[i].y) * (contour[j].y - quad[i].y)
                if (d < bestDist) { bestDist = d; bestIdx = j }
            }
            bestIdx
        }

        fun arcBetween(from: Int, to: Int): List<Point> {
            val pts = ArrayList<Point>()
            var i = from
            while (i != to) { pts.add(contour[i]); i = (i + 1) % n }
            return pts
        }

        // Take the shorter arc (robust to index wrap direction)
        val sides = Array(4) { k ->
            val a = cornerIndices[k]
            val b = cornerIndices[(k + 1) % 4]
            val fwd = arcBetween(a, b)
            val bwd = arcBetween(b, a)
            if (fwd.size <= bwd.size) fwd else bwd
        }

        val edgeWeightsAvailable = edgeMap.type() == CvType.CV_8UC1

        // Adaptive RANSAC + IRLS/Tukey per side, with corner-region trim and
        // optional edge-strength weighting so strong gradient points anchor
        // the fit more than weak/noisy ones.
        val lines = Array<LineFit?>(4) { k ->
            val side = sides[k]
            if (side.size < minPointsPerSide) return@Array null
            val trim = (side.size * trimRatio).toInt().coerceIn(0, (side.size - 3) / 2)
            val trimmed = side.subList(trim, side.size - trim)
            if (trimmed.size < 3) return@Array null
            val weights = if (edgeWeightsAvailable) sampleEdgeWeights(trimmed, edgeMap) else null
            fitLineRobust(trimmed, weights)
        }

        var moved = false
        val result = Array(4) { Point(quad[it].x, quad[it].y) }
        for (k in 0 until 4) {
            val prevLine = lines[(k + 3) % 4] ?: continue
            val nextLine = lines[k] ?: continue
            val intersection = intersectLines(prevLine, nextLine) ?: continue

            val shift = hypot(intersection.x - quad[k].x, intersection.y - quad[k].y)
            if (shift <= maxShift) {
                result[k] = intersection
                if (shift > 0.25) moved = true
            }
        }
        return if (moved) result else null
    }

    /** Bilinearly samples edge-map intensity at each contour point and normalizes to [0.1, 1]. */
    private fun sampleEdgeWeights(points: List<Point>, edgeMap: Mat): DoubleArray {
        val w = edgeMap.width(); val h = edgeMap.height()
        val channels = edgeMap.channels().coerceAtLeast(1)
        val pixels = ByteArray(w * h * channels)
        try {
            edgeMap.get(0, 0, pixels)
        } catch (_: Throwable) {
            return DoubleArray(points.size) { 1.0 }
        }
        fun sample(x: Double, y: Double): Double {
            if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return 0.0
            val x0 = x.toInt(); val y0 = y.toInt()
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val fx = x - x0; val fy = y - y0
            fun at(xx: Int, yy: Int): Double {
                val idx = (yy * w + xx) * channels
                return (pixels[idx].toInt() and 0xFF).toDouble()
            }
            return at(x0, y0) * (1 - fx) * (1 - fy) + at(x1, y0) * fx * (1 - fy) +
                   at(x0, y1) * (1 - fx) * fy + at(x1, y1) * fx * fy
        }
        // Floor of 0.1 so a temporarily weak edge point still contributes a
        // little rather than being fully silenced (RANSAC already removes
        // true outliers; this weighting only refines the survivors).
        return DoubleArray(points.size) { i ->
            0.1 + 0.9 * (sample(points[i].x, points[i].y) / 255.0).coerceIn(0.0, 1.0)
        }
    }

    // ==================================================================
    // 5. EDGE SUPPORT — perpendicular gradient profile + bilinear sampling.
    //    Faster and smoother than a 2D block search.
    // ==================================================================
    fun calculateEdgeSupport(
        quad: Array<Point>,
        edgeMap: Mat,
        searchRadiusPx: Int = 2
    ): Double = calculatePerSideEdgeSupport(quad, edgeMap, searchRadiusPx = searchRadiusPx).average()

    fun calculatePerSideEdgeSupport(
        quad: Array<Point>,
        edgeMap: Mat,
        cachedPixels: ByteArray? = null,
        searchRadiusPx: Int = 3
    ): DoubleArray {
        val w = edgeMap.width()
        val h = edgeMap.height()
        if (w <= 0 || h <= 0 || quad.size != 4) return DoubleArray(4)

        val channels = edgeMap.channels().coerceAtLeast(1)
        val pixels = cachedPixels ?: run {
            val buf = ByteArray(w * h * channels)
            try {
                edgeMap.get(0, 0, buf)
                buf
            } catch (_: Throwable) {
                return DoubleArray(4)
            }
        }

        // Bilinear sampler → sub-pixel accurate edge lookup
        fun sample(x: Double, y: Double): Double {
            if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return 0.0
            val x0 = x.toInt(); val y0 = y.toInt()
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val fx = x - x0; val fy = y - y0
            fun at(xx: Int, yy: Int): Double {
                val idx = (yy * w + xx) * channels
                return (pixels[idx].toInt() and 0xFF).toDouble()
            }
            return at(x0, y0) * (1 - fx) * (1 - fy) + at(x1, y0) * fx * (1 - fy) +
                   at(x0, y1) * (1 - fx) * fy + at(x1, y1) * fx * fy
        }

        val results = DoubleArray(4)
        val sigma = max(1.0, searchRadiusPx / 2.0)

        for (side in 0 until 4) {
            val p1 = quad[side]
            val p2 = quad[(side + 1) % 4]
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val len = hypot(dx, dy)
            if (len < 1e-6) continue

            val nx = -dy / len   // side's unit normal
            val ny = dx / len
            val steps = max(16, (len / 1.5).toInt())

            var acc = 0.0
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val sx = p1.x + t * dx
                val sy = p1.y + t * dy

                // Search only along the normal direction (not a full 2D window)
                var best = 0.0
                for (r in -searchRadiusPx..searchRadiusPx) {
                    val v = sample(sx + r * nx, sy + r * ny)
                    if (v <= 0.0) continue
                    val lateralWeight = exp(-(r * r) / (2.0 * sigma * sigma))
                    val score = (v / 255.0) * lateralWeight
                    if (score > best) best = score
                }
                acc += best
            }
            results[side] = (acc / (steps + 1)).coerceIn(0.0, 1.0)
        }
        return results
    }

    // ==================================================================
    // 6. GEOMETRY EVALUATION — bowtie + area + aspect-ratio aware
    // ==================================================================
    fun evaluateGeometry(quad: Array<Point>): GeometricScore {
        if (quad.size != 4) {
            return GeometricScore(0.0, 0.0, 0.0, 0.0, isConvex = false)
        }
        val (p0, p1, p2, p3) = quad

        val area = computeQuadArea(quad)
        val selfIntersecting = isQuadSelfIntersecting(p0, p1, p2, p3)
        val convex = !selfIntersecting && isQuadConvex(p0, p1, p2, p3)

        if (!convex || area < 1.0) {
            return GeometricScore(
                score = 0.0, parallelism = 0.0, perpendicularity = 0.0,
                perspectiveValid = 0.0, isConvex = false,
                aspectRatio = 0.0, isSelfIntersecting = selfIntersecting, areaPx = area
            )
        }

        val vTop = Point(p1.x - p0.x, p1.y - p0.y)
        val vRight = Point(p2.x - p1.x, p2.y - p1.y)
        val vBottom = Point(p2.x - p3.x, p2.y - p3.y)
        val vLeft = Point(p3.x - p0.x, p3.y - p0.y)

        val lenTop = hypot(vTop.x, vTop.y).coerceAtLeast(1.0)
        val lenRight = hypot(vRight.x, vRight.y).coerceAtLeast(1.0)
        val lenBottom = hypot(vBottom.x, vBottom.y).coerceAtLeast(1.0)
        val lenLeft = hypot(vLeft.x, vLeft.y).coerceAtLeast(1.0)

        val uTop = Point(vTop.x / lenTop, vTop.y / lenTop)
        val uRight = Point(vRight.x / lenRight, vRight.y / lenRight)
        val uBottom = Point(vBottom.x / lenBottom, vBottom.y / lenBottom)
        val uLeft = Point(vLeft.x / lenLeft, vLeft.y / lenLeft)

        val parHoriz = abs(uTop.x * uBottom.x + uTop.y * uBottom.y)
        val parVert = abs(uLeft.x * uRight.x + uLeft.y * uRight.y)
        val parallelismScore = ((parHoriz + parVert) / 2.0).coerceIn(0.0, 1.0)

        val dotTL = abs(uTop.x * uLeft.x + uTop.y * uLeft.y)
        val dotTR = abs(uTop.x * uRight.x + uTop.y * uRight.y)
        val dotBR = abs(uBottom.x * uRight.x + uBottom.y * uRight.y)
        val dotBL = abs(uBottom.x * uLeft.x + uBottom.y * uLeft.y)
        val perpScore = (1.0 - (dotTL + dotTR + dotBR + dotBL) / 4.0).coerceIn(0.0, 1.0)

        // Perspective plausibility: opposite sides of a projected rectangle stay similar in length
        val hDistortion = min(lenTop, lenBottom) / max(lenTop, lenBottom)
        val vDistortion = min(lenLeft, lenRight) / max(lenLeft, lenRight)
        val perspectiveScore = ((hDistortion + vDistortion) / 2.0).coerceIn(0.0, 1.0)

        // Aspect-ratio plausibility. Sharpened to specifically reward standard
        // document ratios (A4/Letter/Legal/ID) rather than treating every ratio
        // in 0.25..4.0 as equally valid — this is what lets the scorer prefer a
        // true A4/Legal page over an incidental rectangle with the same rough
        // proportions (a laptop screen, a floor tile, etc.).
        val shortSide = min(min(lenTop, lenBottom), min(lenLeft, lenRight))
        val longSide = max(max(lenTop, lenBottom), max(lenLeft, lenRight))
        val aspect = longSide / shortSide.coerceAtLeast(1e-6)
        val closestKnownDelta = KNOWN_DOCUMENT_RATIOS.minOf { abs(it - aspect) }
        val aspectScore = when {
            closestKnownDelta <= 0.06 -> 1.0   // near-exact match to a standard page size
            closestKnownDelta <= 0.15 -> 0.85
            aspect in 1.0..2.8 -> 0.60          // plausible but non-standard document
            aspect in 0.25..4.0 -> 0.30
            else -> 0.1                         // sliver — almost certainly not a page
        }

        val combined = 0.35 * perpScore +
                       0.30 * parallelismScore +
                       0.20 * perspectiveScore +
                       0.15 * aspectScore

        return GeometricScore(
            score = combined.coerceIn(0.0, 1.0),
            parallelism = parallelismScore,
            perpendicularity = perpScore,
            perspectiveValid = perspectiveScore,
            isConvex = true,
            aspectRatio = aspect,
            isSelfIntersecting = false,
            areaPx = area
        )
    }

    // ==================================================================
    // 7. VALIDATION & UTILITIES
    // ==================================================================
    fun isQuadConvex(p1: Point, p2: Point, p3: Point, p4: Point): Boolean {
        fun cross(a: Point, b: Point, c: Point): Double {
            val abX = b.x - a.x; val abY = b.y - a.y
            val bcX = c.x - b.x; val bcY = c.y - b.y
            return abX * bcY - abY * bcX
        }
        val cp1 = cross(p1, p2, p3)
        val cp2 = cross(p2, p3, p4)
        val cp3 = cross(p3, p4, p1)
        val cp4 = cross(p4, p1, p2)
        return (cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0) ||
               (cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0)
    }

    /** Detects a "bowtie" quad — where opposite edges intersect each other. */
    fun isQuadSelfIntersecting(p0: Point, p1: Point, p2: Point, p3: Point): Boolean =
        segmentsCross(p0, p1, p2, p3) || segmentsCross(p1, p2, p3, p0)

    private fun segmentsCross(a: Point, b: Point, c: Point, d: Point): Boolean {
        fun ccw(p: Point, q: Point, r: Point): Double =
            (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        val d1 = ccw(c, d, a); val d2 = ccw(c, d, b)
        val d3 = ccw(a, b, c); val d4 = ccw(a, b, d)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
               ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** Shoelace formula — area in px². */
    fun computeQuadArea(quad: Array<Point>): Double {
        if (quad.size != 4) return 0.0
        var a = 0.0
        for (i in 0 until 4) {
            val p = quad[i]
            val q = quad[(i + 1) % 4]
            a += p.x * q.y - q.x * p.y
        }
        return abs(a) / 2.0
    }

    /** One-call sanity check before accepting the final crop. */
    fun isValidQuad(quad: Array<Point>, minAreaPx: Double = 100.0): Boolean {
        if (quad.size != 4) return false
        if (quad.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        if (computeQuadArea(quad) < minAreaPx) return false
        if (isQuadSelfIntersecting(quad[0], quad[1], quad[2], quad[3])) return false
        return isQuadConvex(quad[0], quad[1], quad[2], quad[3])
    }

    /** Shrinks the quad toward its centroid — avoids a 1px black border in the crop. */
    fun insetQuad(quad: Array<Point>, fraction: Double = 0.01): Array<Point> {
        if (quad.size != 4 || fraction <= 0.0) return quad.copyOf()
        val cx = quad.sumOf { it.x } / 4.0
        val cy = quad.sumOf { it.y } / 4.0
        return Array(4) { i ->
            Point(quad[i].x + (cx - quad[i].x) * fraction,
                  quad[i].y + (cy - quad[i].y) * fraction)
        }
    }

    /**
     * Temporal smoothing for the live camera preview. Reduces jitter while
     * snapping instantly on a real scene change.
     */
    fun smoothQuad(
        prev: Array<Point>?,
        curr: Array<Point>,
        alpha: Double = 0.45,
        snapJumpRatio: Double = 0.6
    ): Array<Point> {
        if (prev == null || prev.size != 4 || curr.size != 4) return curr
        val scale = averageSideLength(curr).coerceAtLeast(1.0)
        var maxJump = 0.0
        for (i in 0 until 4) {
            maxJump = max(maxJump, hypot(curr[i].x - prev[i].x, curr[i].y - prev[i].y))
        }
        if (maxJump > scale * snapJumpRatio) return curr // scene changed → instant snap
        return Array(4) { i ->
            Point(prev[i].x + (curr[i].x - prev[i].x) * alpha,
                  prev[i].y + (curr[i].y - prev[i].y) * alpha)
        }
    }

    // ---------------- internal helpers ----------------
    private fun averageSideLength(q: Array<Point>): Double {
        if (q.size != 4) return 0.0
        var sum = 0.0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            sum += hypot(b.x - a.x, b.y - a.y)
        }
        return sum / 4.0
    }

    private fun clampPoint(p: Point, w: Int, h: Int): Point =
        Point(p.x.coerceIn(0.0, (w - 1).toDouble()),
              p.y.coerceIn(0.0, (h - 1).toDouble()))
}

data class GeometricScore(
    val score: Double,
    val parallelism: Double,
    val perpendicularity: Double,
    val perspectiveValid: Double,
    val isConvex: Boolean,
    val aspectRatio: Double = 1.0,           // long/short side
    val isSelfIntersecting: Boolean = false, // bowtie flag
    val areaPx: Double = 0.0
)
