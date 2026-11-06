package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.Bitmap
import android.util.Log
import com.docscan.data.model.FilterType
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Pure tuning shared by the Magic Color pipeline and JVM regression tests. */
internal object MagicPaperTuning {
    // These limits are applied to YCrCb AFTER flattening illumination. The
    // luminance condition protects dark letters; chroma limits protect colored
    // stamps, seals and highlighter marks from being turned gray.
    const val PAPER_MIN_Y = 170.0
    const val PAPER_MIN_CR = 97.0
    const val PAPER_MAX_CR = 157.0
    const val PAPER_MIN_CB = 67.0
    const val PAPER_MAX_CB = 157.0
    const val PAPER_CHROMA_RETAINED = 0.12

    private val inputLevels = intArrayOf(0, 40, 75, 105, 135, 165, 185, 205, 225, 240, 255)
    private val outputLevels = intArrayOf(0, 6, 14, 25, 45, 78, 105, 156, 224, 252, 255)

    fun isPaperLike(y: Int, cr: Int, cb: Int): Boolean =
        y >= PAPER_MIN_Y && cr in PAPER_MIN_CR.toInt()..PAPER_MAX_CR.toInt() &&
            cb in PAPER_MIN_CB.toInt()..PAPER_MAX_CB.toInt()

    fun magicTone(value: Int): Int {
        val level = value.coerceIn(0, 255)
        var segment = 0
        while (segment < inputLevels.lastIndex - 1 && level > inputLevels[segment + 1]) {
            segment++
        }
        val low = inputLevels[segment]
        val high = inputLevels[segment + 1]
        val fraction = (level - low).toDouble() / (high - low)
        return (outputLevels[segment] + fraction * (outputLevels[segment + 1] - outputLevels[segment]))
            .toInt().coerceIn(0, 255)
    }
}

/**
 * Premium OpenCV Image Filter Engine for Professional Document Scanning.
 * Upgraded with ML-Kit style illumination normalization and shadow removal.
 */
object OpenCvFilterProcessor {

    private const val TAG = "OpenCvFilterProcessor"

    fun applyFilter(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0f, 
        contrast: Float = 1f   
    ): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null

        val srcMat = Mat()
        var outMat: Mat? = null
        return try {
            Utils.bitmapToMat(source, srcMat)

            outMat = when (filterType) {
                FilterType.ORIGINAL -> applyOriginal(srcMat, brightness, contrast)
                FilterType.MAGIC_COLOR -> applyPremiumMagicColor(srcMat, brightness, contrast)
                FilterType.BW -> applyUltraBw(srcMat, brightness, contrast)
                FilterType.GRAYSCALE -> applyGrayscale(srcMat, brightness, contrast)
                FilterType.DOCUMENT -> applyDocument(srcMat, brightness, contrast)
                FilterType.CLEAR -> applyClear(srcMat, brightness, contrast)
                FilterType.LIGHTEN -> applyLighten(srcMat, brightness, contrast)
                FilterType.AUTO -> applyAuto(srcMat, brightness, contrast)
            }

            val resultBitmap = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outMat, resultBitmap)
            resultBitmap
        } catch (e: Throwable) {
            Log.e(TAG, "OpenCV filter execution failed for type $filterType", e)
            null
        } finally {
            srcMat.release()
            outMat?.release()
        }
    }

    /**
     * Document-first colour enhancement.  The paper illumination is estimated at a
     * reduced resolution and divided out before a gentle text/paper tone curve.
     * This creates white paper and dark text without binarising stamps, watermarks,
     * signatures or QR codes.  ORIGINAL remains pixel-for-pixel untouched.
     *
     * Keep the background estimate much wider than a character or table line; a
     * small local blur mistakes text for shadows and produces halos around glyphs.
     */
    private fun applyPremiumMagicColor(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val owned = ArrayList<Mat>()
        fun temporary(): Mat = Mat().also { owned.add(it) }

        try {
            val rgb = temporary()
            val yCrCb = temporary()
            Imgproc.cvtColor(srcMat, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, yCrCb, Imgproc.COLOR_RGB2YCrCb)

            val channels = ArrayList<Mat>(3)
            Core.split(yCrCb, channels)
            owned.addAll(channels)
            val luminance = channels[0]

            // Process the shadow map at a maximum edge of 960px: lower memory use,
            // a physically wider estimation window, and stable results at 12MP.
            val scale = minOf(1.0, 960.0 / maxOf(luminance.cols(), luminance.rows()).toDouble())
            val reduced = temporary()
            Imgproc.resize(
                luminance, reduced,
                Size(maxOf(1, (luminance.cols() * scale).toInt()).toDouble(),
                     maxOf(1, (luminance.rows() * scale).toInt()).toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )

            val baseWindow = (minOf(reduced.cols(), reduced.rows()) / 22).coerceIn(15, 51)
            val window = if (baseWindow % 2 == 0) baseWindow + 1 else baseWindow
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(window.toDouble(), window.toDouble())
            )
            owned.add(kernel)
            val brightBackground = temporary()
            Imgproc.morphologyEx(reduced, brightBackground, Imgproc.MORPH_CLOSE, kernel)
            val smoothBackground = temporary()
            // A sigma-only GaussianBlur avoids the even-kernel exception that the
            // previous dynamic (kernelSize + 6) implementation could trigger.
            Imgproc.GaussianBlur(
                brightBackground, smoothBackground, Size(0.0, 0.0),
                maxOf(4.0, window * 0.32)
            )

            val fullBackground = temporary()
            Imgproc.resize(
                smoothBackground, fullBackground,
                Size(luminance.cols().toDouble(), luminance.rows().toDouble()),
                0.0, 0.0, Imgproc.INTER_LINEAR
            )
            // Both inputs are 8-bit.  OpenCV scales the division internally;
            // avoid three full-resolution float Mats (over 140MB at 12MP).
            Core.add(fullBackground, Scalar.all(1.0), fullBackground)
            val normal8 = temporary()
            Core.divide(luminance, fullBackground, normal8, 251.0)

            // Adjustable, continuous contrast curve: background -> white, text ->
            // black, while retaining grey antialiased strokes instead of deleting
            // them with a hard threshold.
            val lutBytes = ByteArray(256) { MagicPaperTuning.magicTone(it).toByte() }
            val toneLut = Mat(1, 256, CvType.CV_8UC1)
            owned.add(toneLut)
            toneLut.put(0, 0, lutBytes)
            val toned = temporary()
            Core.LUT(normal8, toneLut, toned)

            // Very small unsharp mask.  Strong sharpening makes QR modules merge
            // and creates bright rings around type, so use a restrained gain.
            val smallBlur = temporary()
            val sharpened = temporary()
            Imgproc.GaussianBlur(toned, smallBlur, Size(0.0, 0.0), 0.8)
            Core.addWeighted(toned, 1.20, smallBlur, -0.20, 0.0, sharpened)
            val adjustedLum = temporary()
            sharpened.convertTo(
                adjustedLum, CvType.CV_8U,
                contrast.toDouble().coerceIn(0.6, 1.6),
                (brightness * 45.0).coerceIn(-45.0, 45.0)
            )

            // Keep genuine colour in logos, stamps, seals and watermarks.
            // A nearly white Y channel with the ORIGINAL paper's yellow/blue
            // chroma still looks cream or gray. Whiten only bright, low-chroma
            // regions; dark handwriting and saturated ink remain unchanged.
            val cr = temporary()
            val cb = temporary()
            channels[1].convertTo(cr, CvType.CV_8U, 1.04, -5.12)
            channels[2].convertTo(cb, CvType.CV_8U, 1.04, -5.12)

            val paperGateYCrCb = temporary()
            Core.merge(listOf(adjustedLum, cr, cb), paperGateYCrCb)
            val paperMask = temporary()
            Core.inRange(
                paperGateYCrCb,
                Scalar(MagicPaperTuning.PAPER_MIN_Y, MagicPaperTuning.PAPER_MIN_CR,
                    MagicPaperTuning.PAPER_MIN_CB),
                Scalar(255.0, MagicPaperTuning.PAPER_MAX_CR,
                    MagicPaperTuning.PAPER_MAX_CB),
                paperMask
            )
            val neutralizedCr = temporary()
            val neutralizedCb = temporary()
            val retained = MagicPaperTuning.PAPER_CHROMA_RETAINED
            val neutralBias = 128.0 * (1.0 - retained)
            cr.convertTo(neutralizedCr, CvType.CV_8U, retained, neutralBias)
            cb.convertTo(neutralizedCb, CvType.CV_8U, retained, neutralBias)
            neutralizedCr.copyTo(cr, paperMask)
            neutralizedCb.copyTo(cb, paperMask)

            val resultYCrCb = temporary()
            Core.merge(listOf(adjustedLum, cr, cb), resultYCrCb)
            val resultRgb = temporary()
            Imgproc.cvtColor(resultYCrCb, resultRgb, Imgproc.COLOR_YCrCb2RGB)
            val resultRgba = Mat()
            Imgproc.cvtColor(resultRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            return resultRgba
        } finally {
            owned.forEach { it.release() }
        }
    }

    /**
     * PREMIUM UPGRADE: Ultra B&W
     * Perfect text extraction ignoring paper grain and folded shadows.
     */
    private fun applyUltraBw(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val gray = Mat()
        val adjusted = Mat()
        val smooth = Mat()
        val binary = Mat()
        val rgba = Mat()
        try {
            Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGBA2GRAY)
            // Moderate contrast without crushing faint letters or signatures.
            val gain = contrast.toDouble().coerceIn(0.65, 1.65)
            val bias = (brightness * 38.0).coerceIn(-35.0, 35.0)
            gray.convertTo(adjusted, CvType.CV_8U, gain, bias)
            // A small blur removes sensor grain without removing 1–2px thin strokes.
            Imgproc.GaussianBlur(adjusted, smooth, Size(3.0, 3.0), 0.6)
            // Large adaptive windows can turn letters into blobs or erase detail on 12MP photos.
            // Bound the window for comparable behaviour on thumbnails and original pages.
            val longEdge = maxOf(srcMat.width(), srcMat.height())
            val targetBlock = (longEdge / 110).coerceIn(21, 61)
            val block = if (targetBlock % 2 == 0) targetBlock + 1 else targetBlock
            val c = (8.0 - brightness * 5.0).coerceIn(4.0, 13.0)
            Imgproc.adaptiveThreshold(
                smooth, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, block, c
            )
            // Do not close/dilate the binary map: that merges table borders, numerals and
            // adjacent characters, making black-and-white PDFs difficult to read.
            Imgproc.cvtColor(binary, rgba, Imgproc.COLOR_GRAY2RGBA)
            return rgba.clone()
        } finally {
            gray.release(); adjusted.release(); smooth.release(); binary.release(); rgba.release()
        }
    }

    // --- অন্যান্য ফিল্টারগুলো আগের মতোই অপরিবর্তিত থাকবে ---
    // (applyGrayscale, applyDocument, applyClear, applyLighten, applyAuto, applyOriginal)
    
    fun applyGrayscale(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        val clahe = Imgproc.createCLAHE(1.8, Size(8.0, 8.0))
        val claheMat = Mat()
        clahe.apply(grayMat, claheMat)
        val blur = Mat()
        Imgproc.GaussianBlur(claheMat, blur, Size(0.0, 0.0), 1.2)
        val sharp = Mat()
        Core.addWeighted(claheMat, 1.25, blur, -0.25, 0.0, sharp)
        val adjusted = Mat()
        val alpha = (contrast * 1.1f).toDouble()
        val beta = (brightness * 50.0 + 8.0)
        sharp.convertTo(adjusted, -1, alpha, beta)
        val outRgba = Mat()
        Imgproc.cvtColor(adjusted, outRgba, Imgproc.COLOR_GRAY2RGBA)
        grayMat.release(); claheMat.release(); blur.release(); sharp.release(); adjusted.release()
        clahe.collectGarbage()
        return outRgba
    }

    fun applyDocument(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val rgbMat = Mat()
        val ycrcbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgbMat, ycrcbMat, Imgproc.COLOR_RGB2YCrCb)
        val channels = ArrayList<Mat>(3)
        Core.split(ycrcbMat, channels)
        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        val yClahe = Mat()
        clahe.apply(channels[0], yClahe)
        val yBlur = Mat()
        Imgproc.GaussianBlur(yClahe, yBlur, Size(0.0, 0.0), 1.8)
        val ySharp = Mat()
        Core.addWeighted(yClahe, 1.6, yBlur, -0.6, 0.0, ySharp)
        val crMuted = Mat()
        val cbMuted = Mat()
        channels[1].convertTo(crMuted, -1, 0.85, 19.2)
        channels[2].convertTo(cbMuted, -1, 0.85, 19.2)
        val merged = Mat()
        Core.merge(listOf(ySharp, crMuted, cbMuted), merged)
        val outRgb = Mat()
        Imgproc.cvtColor(merged, outRgb, Imgproc.COLOR_YCrCb2RGB)
        val finalRgb = Mat()
        outRgb.convertTo(finalRgb, -1, (contrast * 1.3f).toDouble(), (brightness * 50.0 + 15.0))
        val outRgba = Mat()
        Imgproc.cvtColor(finalRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
        rgbMat.release(); ycrcbMat.release(); channels.forEach { it.release() }
        yClahe.release(); yBlur.release(); ySharp.release(); crMuted.release(); cbMuted.release()
        merged.release(); outRgb.release(); finalRgb.release()
        clahe.collectGarbage()
        return outRgba
    }

    fun applyClear(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val rgbMat = Mat()
        val ycrcbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgbMat, ycrcbMat, Imgproc.COLOR_RGB2YCrCb)
        val channels = ArrayList<Mat>(3)
        Core.split(ycrcbMat, channels)
        val yLift = Mat()
        channels[0].convertTo(yLift, -1, 1.15, 20.0)
        val clahe = Imgproc.createCLAHE(1.5, Size(8.0, 8.0))
        val yClahe = Mat()
        clahe.apply(yLift, yClahe)
        val merged = Mat()
        Core.merge(listOf(yClahe, channels[1], channels[2]), merged)
        val outRgb = Mat()
        Imgproc.cvtColor(merged, outRgb, Imgproc.COLOR_YCrCb2RGB)
        val finalRgb = Mat()
        outRgb.convertTo(finalRgb, -1, (contrast * 1.2f).toDouble(), (brightness * 45.0 + 12.0))
        val outRgba = Mat()
        Imgproc.cvtColor(finalRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
        rgbMat.release(); ycrcbMat.release(); channels.forEach { it.release() }
        yLift.release(); yClahe.release(); merged.release(); outRgb.release(); finalRgb.release()
        clahe.collectGarbage()
        return outRgba
    }

    fun applyLighten(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val rgbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
        val brightRgb = Mat()
        rgbMat.convertTo(brightRgb, -1, (contrast * 1.1f).toDouble(), (brightness * 50.0 + 35.0))
        val outRgba = Mat()
        Imgproc.cvtColor(brightRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
        rgbMat.release(); brightRgb.release()
        return outRgba
    }

    fun applyAuto(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        val rgbMat = Mat()
        val ycrcbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgbMat, ycrcbMat, Imgproc.COLOR_RGB2YCrCb)
        val channels = ArrayList<Mat>(3)
        Core.split(ycrcbMat, channels)
        val clahe = Imgproc.createCLAHE(1.6, Size(8.0, 8.0))
        val yClahe = Mat()
        clahe.apply(channels[0], yClahe)
        val yBlur = Mat()
        Imgproc.GaussianBlur(yClahe, yBlur, Size(0.0, 0.0), 1.2)
        val ySharp = Mat()
        Core.addWeighted(yClahe, 1.3, yBlur, -0.3, 0.0, ySharp)
        val merged = Mat()
        Core.merge(listOf(ySharp, channels[1], channels[2]), merged)
        val outRgb = Mat()
        Imgproc.cvtColor(merged, outRgb, Imgproc.COLOR_YCrCb2RGB)
        val finalRgb = Mat()
        outRgb.convertTo(finalRgb, -1, (contrast * 1.2f).toDouble(), (brightness * 50.0 + 10.0))
        val outRgba = Mat()
        Imgproc.cvtColor(finalRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
        rgbMat.release(); ycrcbMat.release(); channels.forEach { it.release() }
        yClahe.release(); yBlur.release(); ySharp.release(); merged.release(); outRgb.release(); finalRgb.release()
        clahe.collectGarbage()
        return outRgba
    }

    private fun applyOriginal(srcMat: Mat, brightness: Float = 0f, contrast: Float = 1f): Mat {
        if (brightness == 0f && contrast == 1f) return srcMat.clone()
        val adjusted = Mat()
        srcMat.convertTo(adjusted, -1, contrast.toDouble(), (brightness * 100.0))
        return adjusted
    }
}
