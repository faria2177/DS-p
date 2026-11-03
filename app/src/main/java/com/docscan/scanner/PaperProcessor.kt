package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Premium Image Preprocessing & Illumination Normalization Engine.
 * Prepares images for highly accurate, ML-Kit style edge detection.
 */
object PaperProcessor {

    /**
     * Preprocesses a scaled color Mat into an enhanced grayscale Mat
     * optimized for shadow-resistant multi-channel edge detection.
     */
    fun processForEdgeDetection(
        rgbaMat: Mat,
        config: EdgeDetectionConfig = EdgeDetectionConfig.Default
    ): ProcessedFrame {
        val gray = Mat()
        val shadowRemoved = Mat()
        val claheGray = Mat()
        val blurred = Mat()

        // 1. Grayscale Conversion
        Imgproc.cvtColor(rgbaMat, gray, Imgproc.COLOR_RGBA2GRAY)

        // 2. PREMIUM UPGRADE: Morphological Shadow Eraser
        // এটি কাগজের ওপর পড়া কড়া ছায়াকে দূর করে, যাতে এজ ডিটেক্টর ছায়াকে বর্ডার না ভাবে।
        val background = Mat()
        val kSize = max(11, (max(rgbaMat.cols(), rgbaMat.rows()) / 40))
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kSize.toDouble(), kSize.toDouble()))

        Imgproc.morphologyEx(gray, background, Imgproc.MORPH_DILATE, kernel)
        Imgproc.GaussianBlur(background, background, Size(kSize.toDouble() + 4, kSize.toDouble() + 4), 0.0)

        val grayFloat = Mat()
        val bgFloat = Mat()
        val normalizedFloat = Mat()

        gray.convertTo(grayFloat, CvType.CV_32F)
        background.convertTo(bgFloat, CvType.CV_32F)
        Core.add(bgFloat, Scalar(1.0), bgFloat) // Prevent division by zero
        Core.divide(grayFloat, bgFloat, normalizedFloat, 255.0)
        normalizedFloat.convertTo(shadowRemoved, CvType.CV_8U)

        // 3. Dynamic Contrast Enhancement (CLAHE)
        val clahe = Imgproc.createCLAHE(
            config.claheClipLimit + 0.5, 
            Size(config.claheTileSize.toDouble(), config.claheTileSize.toDouble())
        )
        clahe.apply(shadowRemoved, claheGray)
        clahe.collectGarbage()

        // 4. PREMIUM UPGRADE: Edge-Preserving Noise Reduction
        // সাধারণ Gaussian Blur এর বদলে Bilateral Filter, যা প্রান্ত (Edge) ব্লার না করেই কাগজের টেক্সচার মসৃণ করে।
        Imgproc.bilateralFilter(claheGray, blurred, 5, 45.0, 45.0)

        // মেমরি ক্লিনআপ (টেম্পোরারি ম্যাটগুলো)
        background.release()
        kernel.release()
        grayFloat.release()
        bgFloat.release()
        normalizedFloat.release()
        shadowRemoved.release()

        return ProcessedFrame(
            rawGray = gray,
            claheGray = claheGray,
            blurred = blurred
        )
    }
}

/**
 * Container holding intermediate Mats. Call [release] in a finally block to free native memory.
 */
data class ProcessedFrame(
    val rawGray: Mat,
    val claheGray: Mat,
    val blurred: Mat
) {
    fun release() {
        rawGray.release()
        claheGray.release()
        blurred.release()
    }
}
