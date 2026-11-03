package com.docscan.ui.remover.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-quality on-device foreground segmentation.
 *
 * Pipeline:
 * 1) estimate a subject ROI from border/background contrast when AI did not provide one,
 * 2) seed GrabCut with definite-background, probable-background, probable-foreground and
 *    definite-foreground regions (more stable than rectangle-only GrabCut),
 * 3) run 5 GrabCut iterations on a bounded working resolution,
 * 4) upscale the mask with filtered interpolation to avoid stair-step edges,
 * 5) fall back to an adaptive soft saliency mask if OpenCV is unavailable.
 */
object LocalSegmentationEngine {
    private const val TAG = "LocalSegEngine"

    fun segmentForeground(
        bitmap: Bitmap,
        subjectRect: Rect? = null
    ): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return ByteArray(max(1, width * height)) { 0xFF.toByte() }

        val robustRect = sanitizeRect(
            subjectRect ?: estimateSubjectRect(bitmap),
            width,
            height
        )

        try {
            val maskBytes = runOpenCvGrabCut(bitmap, robustRect)
            if (maskBytes != null && isValidMask(maskBytes, width, height)) {
                return maskBytes
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Seeded OpenCV GrabCut failed; using adaptive saliency: ${e.message}")
        }

        return runPureKotlinSaliencySegmentation(bitmap, robustRect)
    }

    /**
     * Estimates the main subject rectangle by learning the image-border colour distribution.
     * This is intentionally conservative: it supplies a guide to GrabCut, not a hard crop.
     */
    fun estimateSubjectRect(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 20 || height < 20) {
            return Rect(1, 1, max(2, width - 1), max(2, height - 1))
        }

        val maxDim = 420
        val scale = min(1f, maxDim.toFloat() / max(width, height).toFloat())
        val sw = max(20, (width * scale).toInt())
        val sh = max(20, (height * scale).toInt())
        val small = if (scale < 0.999f) Bitmap.createScaledBitmap(bitmap, sw, sh, true) else bitmap

        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        val border = max(2, (min(sw, sh) * 0.035f).toInt())
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0L
        var sumSq = 0.0

        fun sample(x: Int, y: Int) {
            val p = pixels[y * sw + x]
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            sumR += r; sumG += g; sumB += b
            sumSq += (r * r + g * g + b * b).toDouble()
            count++
        }

        for (y in 0 until sh) {
            for (x in 0 until border) sample(x, y)
            for (x in sw - border until sw) sample(x, y)
        }
        for (x in border until sw - border) {
            for (y in 0 until border) sample(x, y)
            for (y in sh - border until sh) sample(x, y)
        }

        val bgR = (sumR / count.coerceAtLeast(1)).toInt()
        val bgG = (sumG / count.coerceAtLeast(1)).toInt()
        val bgB = (sumB / count.coerceAtLeast(1)).toInt()
        val meanSq = sumSq / count.coerceAtLeast(1).toDouble()
        val meanEnergy = (bgR * bgR + bgG * bgG + bgB * bgB).toDouble()
        val borderStd = sqrt(max(0.0, meanSq - meanEnergy))
        val threshold = (38.0 + borderStd * 0.45).coerceIn(38.0, 105.0)

        var minX = sw; var minY = sh; var maxX = -1; var maxY = -1
        var salient = 0
        val step = if (max(sw, sh) > 300) 2 else 1
        for (y in border until sh - border step step) {
            val row = y * sw
            for (x in border until sw - border step step) {
                val p = pixels[row + x]
                val dr = Color.red(p) - bgR
                val dg = Color.green(p) - bgG
                val db = Color.blue(p) - bgB
                val dist = sqrt((dr * dr + dg * dg + db * db).toDouble())
                if (dist >= threshold) {
                    minX = min(minX, x); minY = min(minY, y)
                    maxX = max(maxX, x); maxY = max(maxY, y)
                    salient++
                }
            }
        }

        val fallback = Rect(
            (width * 0.07f).toInt(),
            (height * 0.05f).toInt(),
            (width * 0.93f).toInt(),
            (height * 0.96f).toInt()
        )

        if (salient < (sw * sh) / 250 || maxX <= minX || maxY <= minY) {
            if (small !== bitmap) small.recycle()
            return fallback
        }

        val inv = 1f / scale
        var left = (minX * inv).toInt()
        var top = (minY * inv).toInt()
        var right = ((maxX + 1) * inv).toInt()
        var bottom = ((maxY + 1) * inv).toInt()

        // Generous padding keeps hair, fingers and soft shadows inside the probable FG region.
        val padX = max((right - left) * 7 / 100, width / 80)
        val padY = max((bottom - top) * 7 / 100, height / 80)
        left = max(1, left - padX)
        top = max(1, top - padY)
        right = min(width - 1, right + padX)
        bottom = min(height - 1, bottom + padY)

        if (small !== bitmap) small.recycle()
        return sanitizeRect(Rect(left, top, right, bottom), width, height)
    }

    private fun runOpenCvGrabCut(bitmap: Bitmap, subjectRect: Rect): ByteArray? {
        val width = bitmap.width
        val height = bitmap.height

        val maxDim = 900
        val scale = if (width > maxDim || height > maxDim) {
            maxDim.toFloat() / max(width, height).toFloat()
        } else 1f

        val procW = max(16, (width * scale).toInt())
        val procH = max(16, (height * scale).toInt())
        val workingBmp = if (scale < 0.999f) Bitmap.createScaledBitmap(bitmap, procW, procH, true) else bitmap

        val rgba = Mat()
        val rgb = Mat()
        Utils.bitmapToMat(workingBmp, rgba)
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()

        val l = (subjectRect.left * scale).toInt().coerceIn(1, procW - 3)
        val t = (subjectRect.top * scale).toInt().coerceIn(1, procH - 3)
        val r = (subjectRect.right * scale).toInt().coerceIn(l + 2, procW - 1)
        val b = (subjectRect.bottom * scale).toInt().coerceIn(t + 2, procH - 1)

        // GC mask values: 0 BGD, 1 FGD, 2 PR_BGD, 3 PR_FGD.
        val init = ByteArray(procW * procH) { Imgproc.GC_PR_BGD.toByte() }
        val hardBorderX = max(2, (procW * 0.02f).toInt())
        val hardBorderY = max(2, (procH * 0.02f).toInt())
        for (y in 0 until procH) {
            val row = y * procW
            for (x in 0 until procW) {
                val idx = row + x
                if (x < hardBorderX || x >= procW - hardBorderX || y < hardBorderY || y >= procH - hardBorderY) {
                    init[idx] = Imgproc.GC_BGD.toByte()
                } else if (x in l until r && y in t until b) {
                    init[idx] = Imgproc.GC_PR_FGD.toByte()
                }
            }
        }

        // A smaller central core provides a strong foreground seed without clipping edges.
        val coreL = l + (r - l) * 30 / 100
        val coreR = r - (r - l) * 30 / 100
        val coreT = t + (b - t) * 28 / 100
        val coreB = b - (b - t) * 25 / 100
        if (coreR > coreL && coreB > coreT) {
            for (y in coreT until coreB) {
                val row = y * procW
                for (x in coreL until coreR) init[row + x] = Imgproc.GC_FGD.toByte()
            }
        }

        val mask = Mat(procH, procW, CvType.CV_8UC1)
        mask.put(0, 0, init)
        val bgdModel = Mat()
        val fgdModel = Mat()

        Imgproc.grabCut(
            rgb,
            mask,
            org.opencv.core.Rect(),
            bgdModel,
            fgdModel,
            5,
            Imgproc.GC_INIT_WITH_MASK
        )

        val labels = ByteArray(procW * procH)
        mask.get(0, 0, labels)
        rgb.release(); mask.release(); bgdModel.release(); fgdModel.release()
        if (workingBmp !== bitmap) workingBmp.recycle()

        val alpha = ByteArray(labels.size)
        for (i in labels.indices) {
            val v = labels[i].toInt() and 0xFF
            alpha[i] = if (v == Imgproc.GC_FGD || v == Imgproc.GC_PR_FGD) 0xFF.toByte() else 0
        }

        return if (scale < 0.999f) upscaleMaskSmooth(alpha, procW, procH, width, height) else alpha
    }

    private fun upscaleMaskSmooth(lowResMask: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
        val pixels = IntArray(srcW * srcH)
        for (i in pixels.indices) {
            val a = lowResMask[i].toInt() and 0xFF
            pixels[i] = Color.argb(255, a, a, a)
        }
        val small = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
        small.setPixels(pixels, 0, srcW, 0, 0, srcW, srcH)
        val large = Bitmap.createScaledBitmap(small, dstW, dstH, true)
        val largePixels = IntArray(dstW * dstH)
        large.getPixels(largePixels, 0, dstW, 0, 0, dstW, dstH)
        val out = ByteArray(dstW * dstH)
        for (i in out.indices) out[i] = Color.red(largePixels[i]).toByte()
        small.recycle(); large.recycle()
        return out
    }

    private fun runPureKotlinSaliencySegmentation(bitmap: Bitmap, subjectRect: Rect): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val mask = ByteArray(width * height)

        // Estimate background from many border samples instead of just four corner pixels.
        val band = max(1, min(width, height) / 50)
        var br = 0L; var bg = 0L; var bb = 0L; var n = 0L
        fun add(p: Int) { br += Color.red(p); bg += Color.green(p); bb += Color.blue(p); n++ }
        val stride = max(1, min(width, height) / 220)
        for (x in 0 until width step stride) {
            for (y in 0 until band) add(pixels[y * width + x])
            for (y in height - band until height) add(pixels[y * width + x])
        }
        for (y in band until height - band step stride) {
            for (x in 0 until band) add(pixels[y * width + x])
            for (x in width - band until width) add(pixels[y * width + x])
        }
        val bgR = (br / n.coerceAtLeast(1)).toInt()
        val bgG = (bg / n.coerceAtLeast(1)).toInt()
        val bgB = (bb / n.coerceAtLeast(1)).toInt()

        val softStart = 28.0
        val hardFg = 82.0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (x !in subjectRect.left until subjectRect.right || y !in subjectRect.top until subjectRect.bottom) {
                    mask[idx] = 0
                    continue
                }
                val p = pixels[idx]
                val dr = Color.red(p) - bgR
                val dg = Color.green(p) - bgG
                val db = Color.blue(p) - bgB
                val diff = sqrt((dr * dr + dg * dg + db * db).toDouble())
                val a = when {
                    diff <= softStart -> 0
                    diff >= hardFg -> 255
                    else -> (((diff - softStart) / (hardFg - softStart)) * 255.0).toInt()
                }
                mask[idx] = a.coerceIn(0, 255).toByte()
            }
        }
        return mask
    }

    private fun isValidMask(maskBytes: ByteArray, width: Int, height: Int): Boolean {
        var fgCount = 0
        var edgeCount = 0
        val total = width * height
        val stride = max(1, total / 12000)
        var sampled = 0
        var i = 0
        while (i < total) {
            val a = maskBytes[i].toInt() and 0xFF
            if (a > 180) fgCount++ else if (a in 20..180) edgeCount++
            sampled++
            i += stride
        }
        val fgRatio = fgCount.toFloat() / sampled.coerceAtLeast(1)
        val edgeRatio = edgeCount.toFloat() / sampled.coerceAtLeast(1)
        return fgRatio in 0.015f..0.94f && edgeRatio < 0.55f
    }

    private fun sanitizeRect(rect: Rect, width: Int, height: Int): Rect {
        val minW = max(4, width / 10)
        val minH = max(4, height / 10)
        var left = rect.left.coerceIn(1, max(1, width - 3))
        var top = rect.top.coerceIn(1, max(1, height - 3))
        var right = rect.right.coerceIn(left + 2, max(left + 2, width - 1))
        var bottom = rect.bottom.coerceIn(top + 2, max(top + 2, height - 1))
        if (right - left < minW) {
            val cx = (left + right) / 2
            left = max(1, cx - minW / 2); right = min(width - 1, left + minW)
        }
        if (bottom - top < minH) {
            val cy = (top + bottom) / 2
            top = max(1, cy - minH / 2); bottom = min(height - 1, top + minH)
        }
        return Rect(left, top, right, bottom)
    }
}
