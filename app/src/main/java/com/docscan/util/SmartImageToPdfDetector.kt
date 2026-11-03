package com.docscan.util

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * High-accuracy still-image document edge detector for Image -> PDF imports.
 *
 * It runs the production detector on both the original image and a CLAHE-enhanced
 * luminance variant, scores the candidate quadrilaterals geometrically, then performs
 * sub-pixel refinement against the original pixels. This gives imported phone photos
 * the same "review the four corners" experience as a scanner app instead of blindly
 * using the full image rectangle.
 */
object SmartImageToPdfDetector {

    data class Result(
        val corners: List<Offset>,
        val confidence: Float,
        val autoDetected: Boolean
    )

    fun detect(bitmap: Bitmap): Result {
        if (bitmap.width < 64 || bitmap.height < 64) {
            return Result(defaultCorners(), 0f, false)
        }

        val candidates = mutableListOf<List<Offset>>()

        runCatching { EdgeDetector.detectDocumentCorners(bitmap) }
            .getOrNull()
            ?.takeIf { it.size == 4 }
            ?.let(candidates::add)

        val enhanced = createClaheVariant(bitmap)
        if (enhanced != null) {
            runCatching { EdgeDetector.detectDocumentCorners(enhanced) }
                .getOrNull()
                ?.takeIf { it.size == 4 }
                ?.let(candidates::add)
            if (enhanced !== bitmap) enhanced.recycle()
        }

        runCatching { com.docscan.scanner.BlueEdgeDetector.analyzeStillBitmap(bitmap) }
            .getOrNull()
            ?.takeIf { it.size == 4 }
            ?.let(candidates::add)

        val best = candidates
            .map { normalizeOrder(it) }
            .map { it to score(it) }
            .maxByOrNull { it.second }

        if (best == null || best.second < 0.34f) {
            return Result(defaultCorners(), best?.second ?: 0f, false)
        }

        val refined = runCatching {
            EdgeDetector.refineNormalizedCorners(bitmap, best.first)
        }.getOrElse { best.first }

        val refinedScore = score(refined)
        return Result(
            corners = if (refined.size == 4) refined else best.first,
            confidence = max(best.second, refinedScore).coerceIn(0f, 1f),
            autoDetected = true
        )
    }

    private fun createClaheVariant(bitmap: Bitmap): Bitmap? {
        val rgba = Mat()
        val lab = Mat()
        val channels = ArrayList<Mat>(3)
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, lab, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
            org.opencv.core.Core.split(lab, channels)
            val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
            val outL = Mat()
            clahe.apply(channels[0], outL)
            channels[0].release()
            channels[0] = outL
            org.opencv.core.Core.merge(channels, lab)
            Imgproc.cvtColor(lab, lab, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2RGBA)
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(lab, it)
            }
        } catch (_: Throwable) {
            null
        } finally {
            rgba.release()
            lab.release()
            channels.forEach { runCatching { it.release() } }
        }
    }

    private fun score(c: List<Offset>): Float {
        if (c.size != 4) return 0f
        val p = normalizeOrder(c)
        if (p.any { !it.x.isFinite() || !it.y.isFinite() }) return 0f

        val area = polygonArea(p).coerceIn(0f, 1f)
        if (area < 0.08f) return 0f

        val top = distance(p[0], p[1])
        val right = distance(p[1], p[2])
        val bottom = distance(p[2], p[3])
        val left = distance(p[3], p[0])
        if (min(min(top, bottom), min(left, right)) < 0.08f) return 0f

        val oppositeBalance = 1f - (
            (abs(top - bottom) / max(top, bottom).coerceAtLeast(0.001f) +
                abs(left - right) / max(left, right).coerceAtLeast(0.001f)) / 2f
            ).coerceIn(0f, 1f)

        val convex = isConvex(p)
        if (!convex) return 0f

        val borderMargin = p.minOf { min(min(it.x, 1f - it.x), min(it.y, 1f - it.y)) }
        val edgeUse = when {
            borderMargin < 0.01f -> 0.80f
            borderMargin < 0.04f -> 1.00f
            borderMargin < 0.20f -> 0.92f
            else -> 0.72f
        }

        val areaScore = ((area - 0.08f) / 0.72f).coerceIn(0f, 1f)
        return (areaScore * 0.48f + oppositeBalance * 0.34f + edgeUse * 0.18f).coerceIn(0f, 1f)
    }

    private fun normalizeOrder(points: List<Offset>): List<Offset> {
        if (points.size != 4) return points
        val tl = points.minByOrNull { it.x + it.y } ?: points[0]
        val br = points.maxByOrNull { it.x + it.y } ?: points[2]
        val tr = points.maxByOrNull { it.x - it.y } ?: points[1]
        val bl = points.minByOrNull { it.x - it.y } ?: points[3]
        return listOf(tl, tr, br, bl)
    }

    private fun polygonArea(p: List<Offset>): Float {
        var sum = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) * 0.5f
    }

    private fun isConvex(p: List<Offset>): Boolean {
        var sign = 0f
        for (i in 0..3) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-5f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    private fun defaultCorners() = listOf(
        Offset(0.03f, 0.03f),
        Offset(0.97f, 0.03f),
        Offset(0.97f, 0.97f),
        Offset(0.03f, 0.97f)
    )
}
