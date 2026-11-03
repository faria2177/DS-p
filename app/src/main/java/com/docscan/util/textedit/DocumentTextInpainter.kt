package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min

/**
 * Non-destructive document text removal.
 *
 * The primary path creates a text-stroke mask only inside the selected OCR rectangles,
 * removes long table/border lines from that mask, and uses OpenCV Telea inpainting to
 * reconstruct local paper/security texture. This is substantially safer than painting a
 * flat rectangle over the entire OCR box and preserves table rules, logos and nearby patterns.
 * A conservative sampled-paper fallback is retained for devices where OpenCV fails.
 */
object DocumentTextInpainter {

    suspend fun inpaintRegions(
        source: Bitmap,
        regions: List<RectF>
    ): Bitmap = withContext(Dispatchers.Default) {
        if (regions.isEmpty()) return@withContext source
        try {
            inpaintWithOpenCv(source, regions)
        } catch (_: Throwable) {
            fallbackInpaint(source, regions)
        }
    }

    suspend fun inpaintRegion(
        source: Bitmap,
        normRect: RectF
    ): Bitmap = inpaintRegions(source, listOf(normRect))

    private fun inpaintWithOpenCv(source: Bitmap, regions: List<RectF>): Bitmap {
        val rgba = Mat()
        Utils.bitmapToMat(source, rgba)
        val width = rgba.cols().coerceAtLeast(1)
        val height = rgba.rows().coerceAtLeast(1)
        val mask = Mat(height, width, CvType.CV_8UC1, Scalar(0.0))

        regions.forEach { normalized ->
            val left = (normalized.left * width).toInt().coerceIn(0, width - 1)
            val top = (normalized.top * height).toInt().coerceIn(0, height - 1)
            val right = (normalized.right * width).toInt().coerceIn(left + 1, width)
            val bottom = (normalized.bottom * height).toInt().coerceIn(top + 1, height)

            // Tight OCR boxes are expanded only slightly to include anti-aliased glyph edges.
            val padX = max(1, ((right - left) * 0.025f).toInt())
            val padY = max(1, ((bottom - top) * 0.08f).toInt())
            val x0 = (left - padX).coerceAtLeast(0)
            val y0 = (top - padY).coerceAtLeast(0)
            val x1 = (right + padX).coerceAtMost(width)
            val y1 = (bottom + padY).coerceAtMost(height)
            val roiRect = Rect(x0, y0, (x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1))

            val roi = rgba.submat(roiRect)
            val gray = Mat()
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY)

            val strokeMask = Mat()
            val minDim = min(roiRect.width, roiRect.height)
            if (minDim >= 5) {
                val maxOdd = if (minDim % 2 == 0) minDim - 1 else minDim
                var blockSize = (minDim / 2).coerceAtLeast(3)
                if (blockSize % 2 == 0) blockSize += 1
                blockSize = blockSize.coerceAtMost(min(61, maxOdd)).coerceAtLeast(3)
                Imgproc.adaptiveThreshold(
                    gray,
                    strokeMask,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    blockSize,
                    9.0
                )
            } else {
                Imgproc.threshold(gray, strokeMask, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
            }

            // Preserve structural table/border lines. A line spanning a large fraction of the
            // selected box is much more likely to be a table rule than part of a character.
            val horizontal = Mat()
            val vertical = Mat()
            val hKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(max(9, roiRect.width / 3).toDouble(), 1.0)
            )
            val vKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(1.0, max(9, roiRect.height / 2).toDouble())
            )
            Imgproc.morphologyEx(strokeMask, horizontal, Imgproc.MORPH_OPEN, hKernel)
            Imgproc.morphologyEx(strokeMask, vertical, Imgproc.MORPH_OPEN, vKernel)
            val structuralLines = Mat()
            Core.bitwise_or(horizontal, vertical, structuralLines)
            Core.subtract(strokeMask, structuralLines, strokeMask)

            // Slight dilation removes the whole glyph including anti-aliased fringe without
            // growing far enough to damage nearby security graphics.
            val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.dilate(strokeMask, strokeMask, dilateKernel)

            val target = mask.submat(roiRect)
            Core.max(target, strokeMask, target)

            target.release()
            dilateKernel.release()
            structuralLines.release()
            horizontal.release()
            vertical.release()
            hKernel.release()
            vKernel.release()
            strokeMask.release()
            gray.release()
            roi.release()
        }

        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        val restored = Mat()
        Photo.inpaint(rgb, mask, restored, 3.0, Photo.INPAINT_TELEA)
        val restoredRgba = Mat()
        Imgproc.cvtColor(restored, restoredRgba, Imgproc.COLOR_RGB2RGBA)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(restoredRgba, output)

        restoredRgba.release()
        restored.release()
        rgb.release()
        mask.release()
        rgba.release()
        return output
    }

    private fun fallbackInpaint(source: Bitmap, regions: List<RectF>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()
        regions.forEach { inpaintSingleRegionFallback(output, canvas, it, w, h) }
        return output
    }

    private fun inpaintSingleRegionFallback(
        bitmap: Bitmap,
        canvas: Canvas,
        normRect: RectF,
        bitmapW: Float,
        bitmapH: Float
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val left = (normRect.left * bitmapW).toInt().coerceIn(0, w - 1)
        val top = (normRect.top * bitmapH).toInt().coerceIn(0, h - 1)
        val right = (normRect.right * bitmapW).toInt().coerceIn(left + 1, w)
        val bottom = (normRect.bottom * bitmapH).toInt().coerceIn(top + 1, h)
        val boxW = right - left
        val boxH = bottom - top
        val sampleOffset = (min(boxW, boxH) * 0.2f).toInt().coerceIn(2, 16)
        val topSampleY = (top - sampleOffset).coerceIn(0, h - 1)
        val bottomSampleY = (bottom + sampleOffset).coerceIn(0, h - 1)
        val leftSampleX = (left - sampleOffset).coerceIn(0, w - 1)
        val rightSampleX = (right + sampleOffset).coerceIn(0, w - 1)
        val stepX = max(1, boxW / 12)
        val stepY = max(1, boxH / 6)
        val samples = mutableListOf<Int>()
        for (x in left until right step stepX) {
            samples += bitmap.getPixel(x, topSampleY)
            samples += bitmap.getPixel(x, bottomSampleY)
        }
        for (y in top until bottom step stepY) {
            samples += bitmap.getPixel(leftSampleX, y)
            samples += bitmap.getPixel(rightSampleX, y)
        }
        val light = samples.sortedByDescending {
            0.299 * AndroidColor.red(it) + 0.587 * AndroidColor.green(it) + 0.114 * AndroidColor.blue(it)
        }
        val count = max(1, (light.size * 0.75f).toInt())
        var r = 0L; var g = 0L; var b = 0L
        light.take(count).forEach { p -> r += AndroidColor.red(p); g += AndroidColor.green(p); b += AndroidColor.blue(p) }
        val paper = AndroidColor.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper; style = Paint.Style.FILL }
        val padX = (bitmapW * 0.0015f).coerceAtLeast(1f)
        val padY = (bitmapH * 0.0015f).coerceAtLeast(1f)
        canvas.drawRect(
            RectF(
                (left - padX).coerceAtLeast(0f),
                (top - padY).coerceAtLeast(0f),
                (right + padX).coerceAtMost(bitmapW),
                (bottom + padY).coerceAtMost(bitmapH)
            ),
            paint
        )
    }
}
