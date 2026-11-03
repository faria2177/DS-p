package com.docscan.scanner

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Smart, production-grade four point perspective correction engine.
 *
 * Design goals:
 *  - accept imperfect / slightly unordered corner input safely
 *  - refine good corners to sub-pixel precision without allowing them to jump to text/table edges
 *  - reject degenerate/self-crossing quads and fall back safely instead of crashing
 *  - preserve document detail using high quality projective warping
 *  - avoid huge output bitmaps/OOM on extreme perspective selections
 *  - keep ORIGINAL mode truly original (no hidden enhancement)
 *
 * Input corner convention is [TL, TR, BR, BL], but the corrector also re-orders defensively.
 */
object PerspectiveCorrector {

    enum class EnhancementMode {
        ORIGINAL,
        MAGIC_COLOR,
        SHADOW_REMOVAL,
        CRISP_BW,
        GRAYSCALE
    }

    data class WarpResult(
        val bitmap: Bitmap,
        val outputWidth: Int,
        val outputHeight: Int,
        val enhancementMode: EnhancementMode
    )

    private const val MIN_OUTPUT_EDGE = 160
    private const val MAX_OUTPUT_EDGE = 5000
    private const val MAX_OUTPUT_PIXELS = 18_000_000.0
    private const val MIN_QUAD_AREA_RATIO = 0.0025

    /**
     * Perspective-corrects [sourceBitmap] using a true 4-point homography.
     *
     * ORIGINAL mode performs no auto-enhancement. It only rectifies geometry.
     */
    fun correctPerspective(
        sourceBitmap: Bitmap,
        corners: List<Point>,
        mode: EnhancementMode = EnhancementMode.ORIGINAL
    ): WarpResult {
        require(corners.size == 4) { "Requires exactly 4 corners" }
        require(sourceBitmap.width > 1 && sourceBitmap.height > 1) { "Source bitmap is empty" }

        val srcMat = Mat()
        Utils.bitmapToMat(sourceBitmap, srcMat)

        try {
            val imageWidth = sourceBitmap.width
            val imageHeight = sourceBitmap.height

            // 1) Clamp + defensive ordering.
            val ordered = orderCorners(
                corners.map { clampPoint(it, imageWidth, imageHeight) }
            )

            // 2) Sub-pixel refinement. A strict movement guard prevents page corners from
            // snapping onto nearby letters/table intersections.
            val refined = refineCornersSubPixel(srcMat, ordered, imageWidth, imageHeight)

            // 3) Use the best valid quad. Invalid geometry falls back to the last valid
            // ordered quad, then to the whole frame. This avoids crashes/black output.
            val safeCorners = when {
                isValidQuad(refined, imageWidth, imageHeight) -> refined
                isValidQuad(ordered, imageWidth, imageHeight) -> ordered
                else -> fullFrameCorners(imageWidth, imageHeight)
            }

            val (destW, destH) = computeOutputDimensions(
                corners = safeCorners,
                sourceWidth = imageWidth,
                sourceHeight = imageHeight
            )

            val srcPoints = MatOfPoint2f(*safeCorners.toTypedArray())
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((destW - 1).toDouble(), 0.0),
                Point((destW - 1).toDouble(), (destH - 1).toDouble()),
                Point(0.0, (destH - 1).toDouble())
            )

            val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            val warped = Mat()

            // Bicubic gives consistently sharp text and avoids ringing that can appear with
            // aggressive Lanczos upscaling on thin Bengali/Latin glyph strokes.
            Imgproc.warpPerspective(
                srcMat,
                warped,
                transform,
                Size(destW.toDouble(), destH.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE,
                Scalar.all(255.0)
            )

            srcPoints.release()
            dstPoints.release()
            transform.release()

            val enhanced = when (mode) {
                EnhancementMode.ORIGINAL -> warped
                EnhancementMode.MAGIC_COLOR -> applyMagicColor(warped)
                EnhancementMode.SHADOW_REMOVAL -> removeShadows(warped)
                EnhancementMode.CRISP_BW -> applyCrispBw(warped)
                EnhancementMode.GRAYSCALE -> applyGrayscale(warped)
            }

            val outBitmap = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(enhanced, outBitmap)

            if (enhanced !== warped) enhanced.release()
            warped.release()

            return WarpResult(
                bitmap = outBitmap,
                outputWidth = destW,
                outputHeight = destH,
                enhancementMode = mode
            )
        } finally {
            srcMat.release()
        }
    }

    /**
     * Public compatibility method used by the book-page helper.
     */
    fun computeOutputDimensions(corners: List<Point>): Pair<Int, Int> {
        require(corners.size == 4) { "Requires exactly 4 corners" }
        return computeOutputDimensions(corners, 0, 0)
    }

    /**
     * Calculates an output size from the longest opposite edges and then applies memory-safe
     * scaling. Using the longer projected edge preserves detail on the side closest to camera.
     */
    private fun computeOutputDimensions(
        corners: List<Point>,
        sourceWidth: Int,
        sourceHeight: Int
    ): Pair<Int, Int> {
        val ordered = orderCorners(corners)
        val tl = ordered[0]
        val tr = ordered[1]
        val br = ordered[2]
        val bl = ordered[3]

        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)

        var width = max(widthTop, widthBottom).coerceAtLeast(MIN_OUTPUT_EDGE.toDouble())
        var height = max(heightLeft, heightRight).coerceAtLeast(MIN_OUTPUT_EDGE.toDouble())

        // Guard against an accidental almost-line quad creating an absurd aspect ratio.
        val ratio = width / height
        if (ratio !in 0.16..6.25) {
            val averageWidth = ((widthTop + widthBottom) / 2.0).coerceAtLeast(MIN_OUTPUT_EDGE.toDouble())
            val averageHeight = ((heightLeft + heightRight) / 2.0).coerceAtLeast(MIN_OUTPUT_EDGE.toDouble())
            width = averageWidth
            height = averageHeight
        }

        // Never upscale an extreme selection without limit. 1.35x of source max dimension is
        // enough for geometric correction while protecting mobile devices from OOM.
        val sourceLimit = if (sourceWidth > 0 && sourceHeight > 0) {
            max(sourceWidth, sourceHeight) * 1.35
        } else {
            MAX_OUTPUT_EDGE.toDouble()
        }
        val maxEdge = min(MAX_OUTPUT_EDGE.toDouble(), sourceLimit.coerceAtLeast(MIN_OUTPUT_EDGE.toDouble()))

        var scale = 1.0
        val longest = max(width, height)
        if (longest > maxEdge) scale = min(scale, maxEdge / longest)

        val pixels = width * height
        if (pixels * scale * scale > MAX_OUTPUT_PIXELS) {
            scale = min(scale, sqrt(MAX_OUTPUT_PIXELS / pixels))
        }

        val outW = (width * scale).roundToInt().coerceAtLeast(MIN_OUTPUT_EDGE)
        val outH = (height * scale).roundToInt().coerceAtLeast(MIN_OUTPUT_EDGE)
        return outW to outH
    }

    /**
     * Sub-pixel corner refinement for a stable high-quality homography.
     */
    private fun refineCornersSubPixel(
        src: Mat,
        corners: List<Point>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Point> {
        if (src.empty()) return corners

        val gray = Mat()
        val points = MatOfPoint2f(*corners.toTypedArray())
        return try {
            when (src.channels()) {
                4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
                else -> src.copyTo(gray)
            }

            // Light blur suppresses printed character corners while retaining the stronger
            // physical card/page boundary.
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            Imgproc.cornerSubPix(
                gray,
                points,
                Size(5.0, 5.0),
                Size(-1.0, -1.0),
                TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 24, 0.025)
            )

            val candidate = points.toArray().toList()
            val maxShift = min(18.0, min(imageWidth, imageHeight) * 0.015)

            corners.mapIndexed { index, original ->
                val updated = candidate.getOrNull(index) ?: original
                if (
                    updated.x.isFinite() && updated.y.isFinite() &&
                    distance(original, updated) <= maxShift &&
                    updated.x in 0.0..(imageWidth - 1).toDouble() &&
                    updated.y in 0.0..(imageHeight - 1).toDouble()
                ) {
                    updated
                } else {
                    original
                }
            }
        } catch (_: Throwable) {
            corners
        } finally {
            gray.release()
            points.release()
        }
    }

    /**
     * Orders arbitrary corner input clockwise as TL, TR, BR, BL.
     */
    private fun orderCorners(points: List<Point>): List<Point> {
        if (points.size != 4) return points

        val cx = points.sumOf { it.x } / 4.0
        val cy = points.sumOf { it.y } / 4.0
        val aroundCenter = points.sortedBy { atan2(it.y - cy, it.x - cx) }.toMutableList()

        // Rotate list so smallest x+y (top-left in image coordinates) is first.
        val start = aroundCenter.indices.minByOrNull { aroundCenter[it].x + aroundCenter[it].y } ?: 0
        val rotated = (0 until 4).map { aroundCenter[(start + it) % 4] }.toMutableList()

        // Expected image-coordinate winding TL -> TR -> BR -> BL has positive signed area.
        if (signedArea(rotated) < 0.0) {
            val first = rotated.first()
            val reversedTail = rotated.drop(1).reversed()
            return listOf(first) + reversedTail
        }
        return rotated
    }

    private fun isValidQuad(points: List<Point>, width: Int, height: Int): Boolean {
        if (points.size != 4 || width <= 1 || height <= 1) return false
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return false

        val ordered = orderCorners(points)
        val imageArea = width.toDouble() * height.toDouble()
        val area = abs(signedArea(ordered))
        if (area < imageArea * MIN_QUAD_AREA_RATIO) return false

        val minEdge = listOf(
            distance(ordered[0], ordered[1]),
            distance(ordered[1], ordered[2]),
            distance(ordered[2], ordered[3]),
            distance(ordered[3], ordered[0])
        ).minOrNull() ?: 0.0
        if (minEdge < 16.0) return false

        // Convexity: every turn must have the same sign.
        var sign = 0
        for (i in 0..3) {
            val a = ordered[i]
            val b = ordered[(i + 1) % 4]
            val c = ordered[(i + 2) % 4]
            val cross = cross(a, b, c)
            if (abs(cross) < 1e-6) return false
            val current = if (cross > 0) 1 else -1
            if (sign == 0) sign = current else if (sign != current) return false
        }
        return true
    }

    private fun clampPoint(point: Point, width: Int, height: Int): Point {
        return Point(
            point.x.coerceIn(0.0, (width - 1).toDouble()),
            point.y.coerceIn(0.0, (height - 1).toDouble())
        )
    }

    private fun fullFrameCorners(width: Int, height: Int): List<Point> = listOf(
        Point(0.0, 0.0),
        Point((width - 1).toDouble(), 0.0),
        Point((width - 1).toDouble(), (height - 1).toDouble()),
        Point(0.0, (height - 1).toDouble())
    )

    private fun distance(a: Point, b: Point): Double = hypot(b.x - a.x, b.y - a.y)

    private fun cross(a: Point, b: Point, c: Point): Double =
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)

    /** Shoelace signed area / 2. */
    private fun signedArea(points: List<Point>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - a.y * b.x
        }
        return sum / 2.0
    }

    /** Professional color restoration; never called in ORIGINAL mode. */
    private fun applyMagicColor(srcRgba: Mat): Mat {
        val rgb = Mat()
        val lab = Mat()
        Imgproc.cvtColor(srcRgba, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)

        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val clahe = Imgproc.createCLAHE(1.8, Size(8.0, 8.0))
        val lightness = Mat()
        clahe.apply(channels[0], lightness)
        channels[0].release()
        channels[0] = lightness
        Core.merge(channels, lab)

        val restoredRgb = Mat()
        val result = Mat()
        Imgproc.cvtColor(lab, restoredRgb, Imgproc.COLOR_Lab2RGB)
        Imgproc.cvtColor(restoredRgb, result, Imgproc.COLOR_RGB2RGBA)

        // Small, controlled contrast lift; avoids clipping document highlights.
        val boosted = Mat()
        result.convertTo(boosted, -1, 1.045, 3.0)

        rgb.release()
        lab.release()
        restoredRgb.release()
        result.release()
        channels.forEach { it.release() }
        return boosted
    }

    /**
     * Illumination flattening using background division. This preserves letters instead of
     * returning an edge/difference image like a naive absDiff shadow-removal implementation.
     */
    private fun removeShadows(srcRgba: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

        val background = Mat()
        Imgproc.GaussianBlur(gray, background, Size(0.0, 0.0), 21.0)

        // Avoid division by exact zeros.
        Core.add(background, Scalar.all(1.0), background)

        val flattened = Mat()
        Core.divide(gray, background, flattened, 255.0)
        Core.normalize(flattened, flattened, 0.0, 255.0, Core.NORM_MINMAX)

        val result = Mat()
        Imgproc.cvtColor(flattened, result, Imgproc.COLOR_GRAY2RGBA)

        gray.release()
        background.release()
        flattened.release()
        return result
    }

    private fun applyCrispBw(srcRgba: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

        // Gentle denoise protects thin text before adaptive thresholding.
        val denoised = Mat()
        Imgproc.GaussianBlur(gray, denoised, Size(3.0, 3.0), 0.0)

        val bw = Mat()
        Imgproc.adaptiveThreshold(
            denoised,
            bw,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            21,
            9.0
        )

        val result = Mat()
        Imgproc.cvtColor(bw, result, Imgproc.COLOR_GRAY2RGBA)
        gray.release()
        denoised.release()
        bw.release()
        return result
    }

    private fun applyGrayscale(srcRgba: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val result = Mat()
        Imgproc.cvtColor(gray, result, Imgproc.COLOR_GRAY2RGBA)
        gray.release()
        return result
    }

    /**
     * Optional split helper for wide book spreads after geometric correction.
     */
    fun dewarpCurvedPage(
        sourceBitmap: Bitmap,
        corners: List<Point>,
        spineNormalizedX: Double = 0.5
    ): Pair<WarpResult, WarpResult?> {
        val fullResult = correctPerspective(sourceBitmap, corners)
        val w = fullResult.outputWidth
        val h = fullResult.outputHeight

        val isTwoPageBook = (w.toDouble() / h.toDouble()) > 1.25
        if (!isTwoPageBook) return fullResult to null

        val safeSpine = spineNormalizedX.coerceIn(0.2, 0.8)
        val spineX = (fullResult.bitmap.width * safeSpine).roundToInt()
            .coerceIn(1, fullResult.bitmap.width - 1)

        val leftPage = Bitmap.createBitmap(
            fullResult.bitmap,
            0,
            0,
            spineX,
            fullResult.bitmap.height
        )
        val rightPage = Bitmap.createBitmap(
            fullResult.bitmap,
            spineX,
            0,
            fullResult.bitmap.width - spineX,
            fullResult.bitmap.height
        )

        return WarpResult(leftPage, leftPage.width, leftPage.height, fullResult.enhancementMode) to
            WarpResult(rightPage, rightPage.width, rightPage.height, fullResult.enhancementMode)
    }
}
