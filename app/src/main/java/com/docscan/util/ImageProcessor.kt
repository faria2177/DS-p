package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.docscan.data.model.FilterType
import kotlin.math.hypot
import kotlin.math.max

object ImageProcessor {

    /**
     * Perspective warp/crop using 4 corner points (TopLeft, TopRight, BottomRight, BottomLeft)
     * Points are relative (0..1) or absolute pixel coordinates.
     * Uses OpenCV Bicubic Homography warping (DocumentWarper) with fallback to Android Matrix.
     */
    fun perspectiveCrop(
        srcBitmap: Bitmap,
        corners: List<Offset>, // 4 points: TL, TR, BR, BL (0f..1f normalized)
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap {
        // Crop handles and importers may briefly supply incomplete or non-finite
        // coordinates. Preserve the original rather than indexing a missing point.
        if (corners.size != 4 || corners.any { !it.x.isFinite() || !it.y.isFinite() }) {
            return srcBitmap
        }
        if (corners.size == 4) {
            try {
                val cvCorners = corners.map {
                    org.opencv.core.Point((it.x * srcBitmap.width).toDouble(), (it.y * srcBitmap.height).toDouble())
                }
                val warpResult = com.docscan.scanner.PerspectiveCorrector.correctPerspective(
                    sourceBitmap = srcBitmap,
                    corners = cvCorners,
                    mode = com.docscan.scanner.PerspectiveCorrector.EnhancementMode.ORIGINAL
                )
                return warpResult.bitmap
            } catch (e: Throwable) {
                try {
                    return com.docscan.scanner.DocumentWarper.warp(
                        source = srcBitmap,
                        corners = corners,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    )
                } catch (e2: Throwable) {
                    android.util.Log.w("ImageProcessor", "OpenCV warping failed, falling back to Android Matrix", e2)
                }
            }
        }

        val w = srcBitmap.width.toFloat()
        val h = srcBitmap.height.toFloat()

        val tl = PointF(corners[0].x * w, corners[0].y * h)
        val tr = PointF(corners[1].x * w, corners[1].y * h)
        val br = PointF(corners[2].x * w, corners[2].y * h)
        val bl = PointF(corners[3].x * w, corners[3].y * h)

        // Calculate expected width and height
        val topWidth = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val bottomWidth = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val outWidth = if (targetWidth > 0) targetWidth.toFloat() else max(topWidth, bottomWidth).coerceAtLeast(300f)

        val leftHeight = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightHeight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()
        val outHeight = if (targetHeight > 0) targetHeight.toFloat() else max(leftHeight, rightHeight).coerceAtLeast(300f)

        val srcPoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            outWidth, 0f,
            outWidth, outHeight,
            0f, outHeight
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)) {
            return srcBitmap
        }

        val mappedBitmap = Bitmap.createBitmap(outWidth.toInt(), outHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mappedBitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.concat(matrix)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)

        return mappedBitmap
    }

    /**
     * Applies CamScanner-style enhancement filters using native OpenCV processing:
     * - MAGIC_COLOR: High contrast, vibrant text, white background, vivid stamps
     * - BW: Adaptive Gaussian document binarization & speckle noise removal
     * - GRAYSCALE: Smooth 8-bit luminance with CLAHE and unsharp masking
     * - DOCUMENT: Crisp high-contrast text booster
     * - CLEAR: Whitens yellowing/gray cast while preserving authentic color
     * - LIGHTEN: Lifts dark shadows & white-balances background
     * - AUTO: Intelligent levels & sharpness enhancement
     * - ORIGINAL: Natural photo
     */
    fun applyFilter(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0f, // -1f to 1f
        contrast: Float = 1f   // 0.5f to 2f
    ): Bitmap {
        // Preserve true original pixels — no OpenCV round-trip color shift
        if (filterType == FilterType.ORIGINAL && brightness == 0f && contrast == 1f) {
            return source
        }

        // 1. Primary: Native OpenCV high-precision document filter engine
        val cvFiltered = com.docscan.scanner.OpenCvFilterProcessor.applyFilter(
            source = source,
            filterType = filterType,
            brightness = brightness,
            contrast = contrast
        )
        if (cvFiltered != null) {
            return cvFiltered
        }

        // 2. Fallback: Hardware-accelerated ColorMatrix pipeline
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()

        when (filterType) {
            FilterType.ORIGINAL -> {
                // Identity with optional user brightness/contrast
                applyBrightnessContrast(cm, brightness, contrast)
            }
            FilterType.AUTO -> {
                // Auto Enhancement: smart contrast, vivid clarity, balanced background
                val autoContrast = 1.25f * contrast
                val autoBrightness = 20f + (brightness * 50f)
                val scale = autoContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + autoBrightness

                cm.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))

                val satCm = ColorMatrix()
                satCm.setSaturation(1.15f)
                cm.postConcat(satCm)
            }
            FilterType.CLEAR -> {
                // Crystal Clear: removes yellowing/gray cast, whitens document background
                val clearContrast = 1.3f * contrast
                val clearBrightness = 35f + (brightness * 45f)
                val scale = clearContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + clearBrightness

                cm.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))

                val satCm = ColorMatrix()
                satCm.setSaturation(1.2f)
                cm.postConcat(satCm)
            }
            FilterType.DOCUMENT -> {
                // Sharp Document text: high text contrast, clean crisp background
                val docContrast = 1.5f * contrast
                val docBrightness = 25f + (brightness * 40f)
                val scale = docContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + docBrightness

                cm.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))

                val satCm = ColorMatrix()
                satCm.setSaturation(0.9f)
                cm.postConcat(satCm)
            }
            FilterType.MAGIC_COLOR -> {
                // CamScanner Signature Magic Color:
                // 1. Moderate contrast boost
                // 2. High brightness threshold shift to whiten paper background
                // 3. Mild color saturation boost to keep stamps & signatures vivid
                val magicContrast = 1.35f * contrast
                val magicBrightness = 30f + (brightness * 50f)
                val scale = magicContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + magicBrightness

                cm.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))

                // Saturation boost
                val satCm = ColorMatrix()
                satCm.setSaturation(1.3f)
                cm.postConcat(satCm)
            }
            FilterType.BW -> {
                // Fallback grayscale with readable midtones; avoid crushing thin OCR strokes.
                cm.setSaturation(0f)
                val bwContrast = 1.45f * contrast
                val bwBrightness = brightness * 40f
                val scale = bwContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + bwBrightness

                val bwMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(bwMatrix)
            }
            FilterType.GRAYSCALE -> {
                // Balanced grayscale
                cm.setSaturation(0f)
                val grayContrast = 1.15f * contrast
                val grayBrightness = 15f + (brightness * 40f)
                val scale = grayContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + grayBrightness

                val grayMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(grayMatrix)
            }
            FilterType.LIGHTEN -> {
                // Paper brightening & shadow removal
                val lightenContrast = 1.1f * contrast
                val lightenBrightness = 45f + (brightness * 40f)
                val scale = lightenContrast
                val translate = (-0.5f * scale + 0.5f) * 255f + lightenBrightness

                cm.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyBrightnessContrast(cm: ColorMatrix, brightness: Float, contrast: Float) {
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + (brightness * 100f)
        cm.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Draw repeating or single anti-theft / security watermark across the document page
     */
    fun applyWatermark(
        source: Bitmap,
        text: String,
        opacity: Float = 0.35f,
        colorLong: Long = 0xFF888888,
        isTileMode: Boolean = true,
        sizeScale: Float = 1.0f,
        rotationDegrees: Float = -43f,
        posX: Float = 0.5f,
        posY: Float = 0.5f
    ): Bitmap {
        if (text.isBlank()) return source

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val alphaInt = (opacity.coerceIn(0.01f, 1f) * 255).toInt()
        val baseColor = (colorLong.toInt() and 0x00FFFFFF) or (alphaInt shl 24)

        val baseFontSize = (source.width / 18f).coerceAtLeast(18f)
        val calculatedFontSize = (baseFontSize * sizeScale.coerceIn(0.3f, 3.0f)).coerceIn(12f, 160f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColor
            textSize = calculatedFontSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            letterSpacing = 0.05f
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        if (isTileMode) {
            // Diagonal repeating tile grid covering full page
            val stepX = (bounds.width().coerceAtLeast(60) * 1.5f + 100f).coerceAtLeast(140f)
            val stepY = (bounds.height().coerceAtLeast(30) * 2.0f + 120f).coerceAtLeast(120f)

            canvas.save()
            for (y in (-source.height)..(source.height * 2) step stepY.toInt()) {
                for (x in (-source.width)..(source.width * 2) step stepX.toInt()) {
                    canvas.save()
                    canvas.rotate(rotationDegrees, x.toFloat(), y.toFloat())
                    canvas.drawText(text, x.toFloat(), y.toFloat(), paint)
                    canvas.restore()
                }
            }
            canvas.restore()
        } else {
            // Single watermark centered or at specified coordinate
            canvas.save()
            val targetX = source.width * posX.coerceIn(0f, 1f)
            val targetY = source.height * posY.coerceIn(0f, 1f)
            canvas.rotate(rotationDegrees, targetX, targetY)
            canvas.drawText(text, targetX, targetY + bounds.height() / 2f, paint)
            canvas.restore()
        }

        return result
    }

    // Overload for backwards compatibility
    fun applyWatermark(
        source: Bitmap,
        text: String,
        opacity: Float,
        colorLong: Long,
        diagonal: Boolean
    ): Bitmap {
        return applyWatermark(
            source = source,
            text = text,
            opacity = opacity,
            colorLong = colorLong,
            isTileMode = diagonal,
            sizeScale = 1.0f,
            rotationDegrees = if (diagonal) -43f else 0f
        )
    }

    /**
     * Stamping drawn e-signature onto document page
     */
    fun applySignature(
        source: Bitmap,
        signature: Bitmap,
        normalizedX: Float = 0.7f,
        normalizedY: Float = 0.85f,
        normalizedWidth: Float = 0.35f
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val sigWidth = (source.width * normalizedWidth).toInt().coerceAtLeast(100)
        val sigAspect = signature.height.toFloat() / signature.width.toFloat()
        val sigHeight = (sigWidth * sigAspect).toInt()

        val left = (source.width * normalizedX - sigWidth / 2f).coerceIn(0f, (source.width - sigWidth).toFloat())
        val top = (source.height * normalizedY - sigHeight / 2f).coerceIn(0f, (source.height - sigHeight).toFloat())

        val scaledSig = Bitmap.createScaledBitmap(signature, sigWidth, sigHeight, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledSig, left, top, paint)

        return result
    }

    private fun resizeForPrint(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return source
        if (source.width == targetWidth && source.height == targetHeight) return source

        val srcMat = org.opencv.core.Mat()
        val dstMat = org.opencv.core.Mat()
        return try {
            org.opencv.android.Utils.bitmapToMat(source, srcMat)
            val interpolation = if (source.width > targetWidth || source.height > targetHeight) {
                org.opencv.imgproc.Imgproc.INTER_AREA
            } else {
                org.opencv.imgproc.Imgproc.INTER_CUBIC
            }
            org.opencv.imgproc.Imgproc.resize(
                srcMat,
                dstMat,
                org.opencv.core.Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0,
                0.0,
                interpolation
            )
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                org.opencv.android.Utils.matToBitmap(dstMat, it)
            }
        } catch (_: Throwable) {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } finally {
            srcMat.release()
            dstMat.release()
        }
    }

    /**
     * Bangladesh NID / ID-1 two-side export layout.
     *
     * Produces a print-ready A4 portrait sheet matching the reference layout:
     * - true A4 canvas at 300 DPI (2480 x 3508)
     * - physical ID-1 card size (85.60 x 53.98 mm)
     * - front centered in the upper section
     * - back centered below with generous white spacing
     * - no artificial border, label, shadow or background decoration
     *
     * Keeping the card at its real physical size is important when the exported
     * A4 page is printed at 100% scale.
     */
    fun mergeIdCard(front: Bitmap, back: Bitmap): Bitmap {
        // A4 @ 300 DPI: 210 x 297 mm
        val sheetWidth = 2480
        val sheetHeight = 3508

        val merged = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        canvas.drawColor(Color.WHITE)

        // ISO/IEC 7810 ID-1: 85.60 x 53.98 mm.
        // Width relative to A4 = 85.60 / 210 ≈ 40.76%.
        val cardWidth = (sheetWidth * (85.60f / 210f)).toInt().coerceAtLeast(1)
        val cardHeight = (sheetHeight * (53.98f / 297f)).toInt().coerceAtLeast(1)
        val left = (sheetWidth - cardWidth) / 2f

        // Reference-style placement: both sides sit in the upper half of A4,
        // leaving a large clean white area underneath.
        val topFront = sheetHeight * 0.15f
        val verticalGap = sheetHeight * 0.07f
        val topBack = topFront + cardHeight + verticalGap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }

        fun landscape(bitmap: Bitmap): Bitmap {
            return if (bitmap.height > bitmap.width) rotate(bitmap, 90) else bitmap
        }

        fun drawCard(source: Bitmap, top: Float) {
            val oriented = landscape(source)
            val scaled = resizeForPrint(oriented, cardWidth, cardHeight)

            // Render at the exact final pixel size so Canvas does not perform a second
            // bilinear resample. OpenCV INTER_AREA/INTER_CUBIC keeps small NID text and
            // MRZ/barcode strokes cleaner on the final A4 sheet.
            canvas.drawBitmap(scaled, left, top, paint)
            if (scaled !== oriented && !scaled.isRecycled) {
                scaled.recycle()
            }
        }

        drawCard(front, topFront)
        drawCard(back, topBack)

        return merged
    }

    /**
     * Passport Single/Booklet Merger (Centered on single A4 standard sheet)
     */
    fun mergePassport(passport: Bitmap): Bitmap {
        val sheetWidth = 1414
        val sheetHeight = 2000

        val merged = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        canvas.drawColor(Color.WHITE)

        val passWidth = (sheetWidth * 0.82f).toInt()
        val passHeight = (passWidth / 1.42f).toInt()

        val left = (sheetWidth - passWidth) / 2f
        val top = (sheetHeight - passHeight) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scaled = Bitmap.createScaledBitmap(passport, passWidth, passHeight, true)
        val rect = RectF(left, top, left + passWidth, top + passHeight)
        val cornerRadius = 14f

        val cardPath = android.graphics.Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(cardPath)
        canvas.drawBitmap(scaled, left, top, paint)
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        return merged
    }

    /**
     * One-sided ID, passport, or certificate on an A4 portrait sheet.
     * Fit inside the print area without forcing a card-like aspect ratio: certificates
     * and passport data pages must not be stretched or distorted.
     */
    fun mergeSingleCopyOnA4(image: Bitmap, fullPage: Boolean): Bitmap {
        val sheetWidth = 1414
        val sheetHeight = 2000
        val merged = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        canvas.drawColor(Color.WHITE)

        val widthLimit = sheetWidth * if (fullPage) 0.94f else 0.78f
        val heightLimit = sheetHeight * if (fullPage) 0.94f else 0.43f
        val fitScale = minOf(widthLimit / image.width, heightLimit / image.height)
        val fittedWidth = (image.width * fitScale).toInt().coerceAtLeast(1)
        val fittedHeight = (image.height * fitScale).toInt().coerceAtLeast(1)
        val left = (sheetWidth - fittedWidth) / 2f
        val top = (sheetHeight - fittedHeight) / 2f
        val scaled = Bitmap.createScaledBitmap(image, fittedWidth, fittedHeight, true)
        canvas.drawBitmap(scaled, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (scaled !== image) scaled.recycle()
        return merged
    }

    /**
     * Applies mosaic / pixelation to specified normalized regions (0..1)
     */
    fun applyMosaic(source: Bitmap, regions: List<RectF>, pixelBlockSize: Int = 18): Bitmap {
        if (regions.isEmpty()) return source
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val w = source.width
        val h = source.height

        for (region in regions) {
            val left = (region.left * w).toInt().coerceIn(0, w - 1)
            val top = (region.top * h).toInt().coerceIn(0, h - 1)
            val right = (region.right * w).toInt().coerceIn(left + 1, w)
            val bottom = (region.bottom * h).toInt().coerceIn(top + 1, h)

            val regionWidth = right - left
            val regionHeight = bottom - top
            if (regionWidth <= 0 || regionHeight <= 0) continue

            val subBmp = Bitmap.createBitmap(source, left, top, regionWidth, regionHeight)
            val downsampledWidth = (regionWidth / pixelBlockSize).coerceAtLeast(1)
            val downsampledHeight = (regionHeight / pixelBlockSize).coerceAtLeast(1)

            val small = Bitmap.createScaledBitmap(subBmp, downsampledWidth, downsampledHeight, false)
            val pixelated = Bitmap.createScaledBitmap(small, regionWidth, regionHeight, false)

            canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), paint)
        }

        return result
    }

    /**
     * Samples the local document background / paper tone around a specific normalized position (normX, normY).
     * Selects upper quartile of luminance to discard dark printed ink text and capture the real paper texture/color.
     */
    fun sampleLocalPaperColor(
        source: Bitmap,
        normX: Float,
        normY: Float,
        sampleRadiusFraction: Float = 0.04f
    ): Int {
        val w = source.width
        val h = source.height
        val centerX = (normX * w).toInt().coerceIn(0, w - 1)
        val centerY = (normY * h).toInt().coerceIn(0, h - 1)
        val radiusX = (w * sampleRadiusFraction).toInt().coerceIn(6, 60)
        val radiusY = (h * sampleRadiusFraction).toInt().coerceIn(6, 60)

        val minX = (centerX - radiusX).coerceAtLeast(0)
        val maxX = (centerX + radiusX).coerceAtMost(w - 1)
        val minY = (centerY - radiusY).coerceAtLeast(0)
        val maxY = (centerY + radiusY).coerceAtMost(h - 1)

        val sampleLums = ArrayList<Pair<Int, Int>>() // Pair(lum, pixelColor)
        val step = 2

        for (y in minY..maxY step step) {
            for (x in minX..maxX step step) {
                val p = source.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                sampleLums.add(Pair(lum, p))
            }
        }

        if (sampleLums.isEmpty()) return Color.WHITE

        // Sort by luminance and take top 25% brightest samples (pure paper background, ignoring ink)
        sampleLums.sortByDescending { it.first }
        val takeCount = (sampleLums.size * 0.25f).toInt().coerceAtLeast(1)
        var sumR = 0
        var sumG = 0
        var sumB = 0

        for (i in 0 until takeCount) {
            val c = sampleLums[i].second
            sumR += Color.red(c)
            sumG += Color.green(c)
            sumB += Color.blue(c)
        }

        val avgR = (sumR / takeCount).coerceIn(210, 255)
        val avgG = (sumG / takeCount).coerceIn(210, 255)
        val avgB = (sumB / takeCount).coerceIn(210, 255)

        return Color.rgb(avgR, avgG, avgB)
    }

    /**
     * Wipes a rectangular region (like a detected text block) by inpainting it with the local surrounding paper color.
     */
    fun wipeTextRegion(source: Bitmap, rect: RectF): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val sampleColor = sampleLocalPaperColor(
            source = source,
            normX = rect.centerX(),
            normY = rect.centerY(),
            sampleRadiusFraction = 0.05f
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sampleColor
            style = Paint.Style.FILL
        }

        // Expand boundary slightly by 1-2% to cleanly absorb anti-aliased font edges
        val marginX = w * 0.005f
        val marginY = h * 0.003f
        val cleanRect = RectF(
            (rect.left * w - marginX).coerceAtLeast(0f),
            (rect.top * h - marginY).coerceAtLeast(0f),
            (rect.right * w + marginX).coerceAtMost(w),
            (rect.bottom * h + marginY).coerceAtMost(h)
        )

        canvas.drawRect(cleanRect, paint)
        return result
    }

    /**
     * Smart Eraser inpainting: cleans unwanted background regions with sampled paper color
     */
    fun applySmartErase(
        source: Bitmap,
        strokePoints: List<List<Offset>>,
        strokeWidths: List<Float>
    ): Bitmap {
        if (strokePoints.isEmpty()) return source
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val w = source.width.toFloat()
        val h = source.height.toFloat()

        for (i in strokePoints.indices) {
            val points = strokePoints[i]
            if (points.isEmpty()) continue
            val sw = strokeWidths.getOrElse(i) { 30f }
            val first = points.first()

            val sampledColor = sampleLocalPaperColor(
                source = source,
                normX = first.x,
                normY = first.y
            )

            val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = sampledColor
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = sw * (w / 600f).coerceAtLeast(2f)
            }

            val path = android.graphics.Path()
            path.moveTo(first.x * w, first.y * h)
            for (p in points.drop(1)) {
                path.lineTo(p.x * w, p.y * h)
            }
            canvas.drawPath(path, erasePaint)
        }

        return result
    }

    /**
     * AI Auto Clean: Removes dark border shadows, finger smudges, punch holes, and cleans paper borders
     */
    fun autoCleanDocumentArtifacts(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width
        val h = result.height

        // 1. Sample paper background tone from safe inner border (5% in)
        val samplePoints = listOf(
            Pair((w * 0.1f).toInt(), (h * 0.1f).toInt()),
            Pair((w * 0.9f).toInt(), (h * 0.1f).toInt()),
            Pair((w * 0.1f).toInt(), (h * 0.9f).toInt()),
            Pair((w * 0.9f).toInt(), (h * 0.9f).toInt()),
            Pair((w * 0.5f).toInt(), (h * 0.05f).toInt())
        )
        var totalR = 0
        var totalG = 0
        var totalB = 0
        var validSamples = 0

        for ((sx, sy) in samplePoints) {
            if (sx in 0 until w && sy in 0 until h) {
                val pix = source.getPixel(sx, sy)
                val r = Color.red(pix)
                val g = Color.green(pix)
                val b = Color.blue(pix)
                if (r > 160 && g > 160 && b > 160) {
                    totalR += r
                    totalG += g
                    totalB += b
                    validSamples++
                }
            }
        }

        val paperColor = if (validSamples > 0) {
            Color.rgb((totalR / validSamples).coerceIn(240, 255), (totalG / validSamples).coerceIn(240, 255), (totalB / validSamples).coerceIn(240, 255))
        } else {
            Color.WHITE
        }

        val cleanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperColor
            style = Paint.Style.FILL
        }

        // 2. Clean outer extreme border artifacts (shadow edges / scan bed borders, approx 2.5% of edges)
        val borderX = (w * 0.025f).coerceAtLeast(4f)
        val borderY = (h * 0.025f).coerceAtLeast(4f)

        // Top border
        canvas.drawRect(0f, 0f, w.toFloat(), borderY, cleanPaint)
        // Bottom border
        canvas.drawRect(0f, h - borderY, w.toFloat(), h.toFloat(), cleanPaint)
        // Left border
        canvas.drawRect(0f, 0f, borderX, h.toFloat(), cleanPaint)
        // Right border
        canvas.drawRect(w - borderX, 0f, w.toFloat(), h.toFloat(), cleanPaint)

        // 3. Scan corner quadrants for dark finger smudges / thumb shadows (luminance < 110 in outer 10% bounds)
        val cornerMarginX = (w * 0.10f).toInt()
        val cornerMarginY = (h * 0.10f).toInt()

        val cornerBoxes = listOf(
            Rect(0, 0, cornerMarginX, cornerMarginY),
            Rect(w - cornerMarginX, 0, w, cornerMarginY),
            Rect(0, h - cornerMarginY, cornerMarginX, h),
            Rect(w - cornerMarginX, h - cornerMarginY, w, h)
        )

        for (box in cornerBoxes) {
            var darkPixelCount = 0
            val totalBoxPixels = (box.width() * box.height()).coerceAtLeast(1)
            for (x in box.left until box.right step 3) {
                for (y in box.top until box.bottom step 3) {
                    val pix = source.getPixel(x, y)
                    val lum = 0.299 * Color.red(pix) + 0.587 * Color.green(pix) + 0.114 * Color.blue(pix)
                    if (lum < 110) darkPixelCount++
                }
            }
            // If corner has heavy dark thumb smudge or shadow artifact, inpaint it with clean paper
            if (darkPixelCount > (totalBoxPixels / 9) * 0.15) {
                canvas.drawRect(RectF(box), cleanPaint)
            }
        }

        return result
    }
}
