package com.docscan.util

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Conservative NID cleanup tuned for color identity cards.
 *
 * Goals:
 * - remove uneven light / gray shadow without washing security colors
 * - keep portrait skin tones and pastel guilloche patterns natural
 * - sharpen small Bengali/English text and MRZ/barcode edges
 * - avoid aggressive thresholding or "scanner white" that destroys card details
 *
 * ML Kit remains responsible for document capture/crop. This processor only performs
 * post-capture image cleanup on the already-cropped card bitmap.
 */
object NidCleanProcessor {

    private const val TAG = "NidCleanProcessor"

    fun enhance(source: Bitmap, isFront: Boolean): Bitmap {
        if (source.width < 32 || source.height < 32) return source

        val rgba = Mat()
        val rgb = Mat()
        var output: Mat? = null

        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            val balanced = mildGrayWorld(rgb)

            // Edge-preserving noise cleanup before local contrast/sharpening.
            val denoised = Mat()
            Imgproc.bilateralFilter(
                balanced,
                denoised,
                5,
                if (isFront) 24.0 else 28.0,
                if (isFront) 24.0 else 28.0
            )
            balanced.release()

            val ycrcb = Mat()
            Imgproc.cvtColor(denoised, ycrcb, Imgproc.COLOR_RGB2YCrCb)
            denoised.release()

            val channels = ArrayList<Mat>(3)
            Core.split(ycrcb, channels)
            ycrcb.release()

            val y = channels[0]
            val cr = channels[1]
            val cb = channels[2]

            // Estimate smooth illumination map. A sigma-based blur avoids giant kernels
            // while still following page/card shadows and vignetting.
            val illumination = Mat()
            val sigma = (max(source.width, source.height) / 55.0).coerceIn(18.0, 72.0)
            Imgproc.GaussianBlur(y, illumination, Size(0.0, 0.0), sigma)

            val yFloat = Mat()
            val bgFloat = Mat()
            val normalizedFloat = Mat()
            y.convertTo(yFloat, CvType.CV_32F)
            illumination.convertTo(bgFloat, CvType.CV_32F)
            Core.add(bgFloat, Scalar(2.0), bgFloat)

            val bgMean = Core.mean(illumination).`val`[0].coerceIn(135.0, 205.0)
            Core.divide(yFloat, bgFloat, normalizedFloat, bgMean)

            val normalized = Mat()
            normalizedFloat.convertTo(normalized, CvType.CV_8U)

            val balancedY = Mat()
            Core.addWeighted(
                y,
                if (isFront) 0.30 else 0.20,
                normalized,
                if (isFront) 0.70 else 0.80,
                0.0,
                balancedY
            )

            // Gentle local contrast: enough for small text, not enough to amplify card grain.
            val clahe = Imgproc.createCLAHE(
                if (isFront) 1.25 else 1.45,
                Size(8.0, 8.0)
            )
            val localContrast = Mat()
            clahe.apply(balancedY, localContrast)

            // Keep the card bright, especially on the back side where shadows are common.
            val currentMean = Core.mean(localContrast).`val`[0]
            val targetMean = if (isFront) 166.0 else 176.0
            val lift = (targetMean - currentMean).coerceIn(-3.0, if (isFront) 12.0 else 18.0)
            val lifted = Mat()
            localContrast.convertTo(lifted, -1, 1.0, lift)

            // Small-radius text-aware unsharp mask. Back gets slightly stronger crispness
            // because MRZ/barcode lines benefit from it.
            val blur = Mat()
            Imgproc.GaussianBlur(lifted, blur, Size(0.0, 0.0), if (isFront) 0.75 else 0.65)
            val sharpY = Mat()
            Core.addWeighted(
                lifted,
                if (isFront) 1.28 else 1.38,
                blur,
                if (isFront) -0.28 else -0.38,
                0.0,
                sharpY
            )

            // Preserve chroma/security pattern. Only very slightly pull chroma toward neutral
            // to remove camera/lighting cast without desaturating the genuine card colors.
            val crBalanced = Mat()
            val cbBalanced = Mat()
            cr.convertTo(crBalanced, -1, 0.985, 1.92)
            cb.convertTo(cbBalanced, -1, 0.985, 1.92)

            val merged = Mat()
            Core.merge(listOf(sharpY, crBalanced, cbBalanced), merged)
            val cleanedRgb = Mat()
            Imgproc.cvtColor(merged, cleanedRgb, Imgproc.COLOR_YCrCb2RGB)

            // Selective neutral-background lift: brighten only low-saturation mid/high tones.
            // Dark text is excluded, and colored security printing remains untouched.
            val hsv = Mat()
            Imgproc.cvtColor(cleanedRgb, hsv, Imgproc.COLOR_RGB2HSV)
            val neutralMask = Mat()
            Core.inRange(
                hsv,
                Scalar(0.0, 0.0, if (isFront) 118.0 else 105.0),
                Scalar(180.0, if (isFront) 52.0 else 62.0, 250.0),
                neutralMask
            )
            val gentlyLifted = Mat()
            cleanedRgb.convertTo(
                gentlyLifted,
                -1,
                if (isFront) 1.015 else 1.025,
                if (isFront) 4.0 else 7.0
            )
            val finalRgb = cleanedRgb.clone()
            output = finalRgb
            gentlyLifted.copyTo(finalRgb, neutralMask)

            val outRgba = Mat()
            Imgproc.cvtColor(finalRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
            val result = Bitmap.createBitmap(outRgba.cols(), outRgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, result)

            // Cleanup.
            illumination.release(); yFloat.release(); bgFloat.release(); normalizedFloat.release()
            normalized.release(); balancedY.release(); localContrast.release(); lifted.release()
            blur.release(); sharpY.release(); crBalanced.release(); cbBalanced.release()
            merged.release(); cleanedRgb.release(); hsv.release(); neutralMask.release()
            gentlyLifted.release(); outRgba.release(); channels.forEach { it.release() }
            clahe.collectGarbage()

            result
        } catch (t: Throwable) {
            Log.w(TAG, "NID cleanup failed; preserving original bitmap", t)
            source
        } finally {
            rgba.release()
            rgb.release()
            output?.release()
        }
    }

    /** Mild gray-world correction with tightly clamped channel gain. */
    private fun mildGrayWorld(rgb: Mat): Mat {
        val channels = ArrayList<Mat>(3)
        Core.split(rgb, channels)
        if (channels.size < 3) {
            channels.forEach { it.release() }
            return rgb.clone()
        }

        val means = channels.map { Core.mean(it).`val`[0].coerceAtLeast(1.0) }
        val target = means.average()
        val corrected = ArrayList<Mat>(3)

        for (i in 0..2) {
            val gain = (target / means[i]).coerceIn(0.95, 1.05)
            val channel = Mat()
            channels[i].convertTo(channel, -1, gain, 0.0)
            corrected.add(channel)
        }

        val merged = Mat()
        Core.merge(corrected, merged)
        channels.forEach { it.release() }
        corrected.forEach { it.release() }
        return merged
    }
}
