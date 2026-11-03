package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Accuracy-first NID front/back processing pipeline used by the custom CameraX scanner.
 *
 * Important: Google ML Kit Document Scanner does not expose a headless API that accepts an
 * already-captured Bitmap and returns its document corners. Its automatic crop is tied to the
 * SDK-owned scanner activity. For the app-owned CameraX NID UI we therefore use the project's
 * high-resolution document detector + perspective corrector for the actual 4-corner crop, then
 * run ML Kit on the rectified card for OCR / face / barcode verification.
 *
 * This keeps the user flow seamless: CameraX capture -> automatic crop/perspective correction ->
 * ML Kit verification -> NID preview, without reopening Google's scanner UI.
 */
object NidAutoProcessor {

    data class SideResult(
        val corners: List<Offset>,
        val croppedPreview: Bitmap,
        val ocrText: String,
        val faceDetected: Boolean,
        val barcodeCount: Int,
        val usedFallbackCorners: Boolean
    )

    data class PairResult(
        val front: SideResult,
        val back: SideResult
    )

    suspend fun processPair(
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        frontFallbackCorners: List<Offset>? = null,
        backFallbackCorners: List<Offset>? = null
    ): PairResult = coroutineScope {
        val frontDeferred = async(Dispatchers.Default) {
            processSide(
                bitmap = frontBitmap,
                fallbackCorners = frontFallbackCorners,
                isFront = true
            )
        }
        val backDeferred = async(Dispatchers.Default) {
            processSide(
                bitmap = backBitmap,
                fallbackCorners = backFallbackCorners,
                isFront = false
            )
        }
        PairResult(frontDeferred.await(), backDeferred.await())
    }

    suspend fun processSide(
        bitmap: Bitmap,
        fallbackCorners: List<Offset>? = null,
        isFront: Boolean
    ): SideResult = withContext(Dispatchers.Default) {
        val detectorCorners = runCatching { EdgeDetector.detectDocumentCorners(bitmap) }
            .getOrNull()
            ?.takeIf { isUsableNidQuad(it) }

        val fallback = fallbackCorners
            ?.takeIf { isUsableNidQuad(it) }
            ?: centeredIdCardCorners(bitmap)

        val selected = detectorCorners ?: fallback
        val expanded = expandQuad(selected, 1.018f)

        // Geometry only: no auto-enhance, no hidden filter.
        val cropped = runCatching {
            ImageProcessor.perspectiveCrop(bitmap, expanded)
        }.getOrElse {
            ImageProcessor.perspectiveCrop(bitmap, fallback)
        }

        // ML Kit works best with a landscape ID-card crop. This rotation is only for analysis;
        // the user-visible page remains based on the detected corners so manual crop refinement
        // is still possible from the original capture.
        val analysisBitmap = if (cropped.height > cropped.width) {
            rotateForAnalysis(cropped, 90f)
        } else {
            cropped
        }

        val ocrDeferred = async(Dispatchers.IO) {
            runCatching { TextRecognizerHelper.extractText(analysisBitmap) }.getOrDefault("")
        }
        val faceDeferred = async(Dispatchers.IO) {
            if (isFront) {
                runCatching { PassportFaceAnalyzer.detectPrimaryFace(analysisBitmap) != null }
                    .getOrDefault(false)
            } else {
                false
            }
        }
        val barcodeDeferred = async(Dispatchers.IO) {
            if (!isFront) detectBarcodeCount(analysisBitmap) else 0
        }

        SideResult(
            corners = expanded,
            croppedPreview = analysisBitmap,
            ocrText = ocrDeferred.await(),
            faceDetected = faceDeferred.await(),
            barcodeCount = barcodeDeferred.await(),
            usedFallbackCorners = detectorCorners == null
        )
    }

    private fun isUsableNidQuad(corners: List<Offset>): Boolean {
        if (corners.size != 4) return false
        if (corners.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        if (corners.any { it.x !in -0.03f..1.03f || it.y !in -0.03f..1.03f }) return false

        val area = polygonArea(corners)
        // Reject tiny false detections and near-full-frame detections. The dedicated NID
        // viewfinder normally occupies roughly 30-65% of the frame.
        if (area !in 0.07f..0.82f) return false

        val top = distance(corners[0], corners[1])
        val right = distance(corners[1], corners[2])
        val bottom = distance(corners[2], corners[3])
        val left = distance(corners[3], corners[0])
        val width = (top + bottom) * 0.5f
        val height = (left + right) * 0.5f
        if (width < 0.08f || height < 0.08f) return false

        val ratio = max(width, height) / min(width, height)
        // ID-1 physical ratio is 1.586. Allow perspective/skew but reject A4/full-frame boxes.
        if (ratio !in 1.22f..2.05f) return false

        // Avoid collapsed/self-crossing-like quads by checking diagonals and opposite edges.
        val d1 = distance(corners[0], corners[2])
        val d2 = distance(corners[1], corners[3])
        if (d1 < 0.15f || d2 < 0.15f) return false

        return true
    }

    private fun centeredIdCardCorners(bitmap: Bitmap): List<Offset> {
        val targetRatio = 1.586f
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val xSpan = 0.88f
        val ySpan = (xSpan * imageRatio / targetRatio).coerceIn(0.34f, 0.78f)
        val left = (1f - xSpan) / 2f
        val top = (1f - ySpan) / 2f
        return listOf(
            Offset(left, top),
            Offset(left + xSpan, top),
            Offset(left + xSpan, top + ySpan),
            Offset(left, top + ySpan)
        )
    }

    private fun expandQuad(corners: List<Offset>, factor: Float): List<Offset> {
        if (corners.size != 4) return corners
        val cx = corners.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = corners.sumOf { it.y.toDouble() }.toFloat() / 4f
        return corners.map { p ->
            Offset(
                x = (cx + (p.x - cx) * factor).coerceIn(0f, 1f),
                y = (cy + (p.y - cy) * factor).coerceIn(0f, 1f)
            )
        }
    }

    private fun polygonArea(points: List<Offset>): Float {
        if (points.size != 4) return 0f
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) * 0.5f
    }

    private fun distance(a: Offset, b: Offset): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun rotateForAnalysis(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private suspend fun detectBarcodeCount(bitmap: Bitmap): Int =
        suspendCancellableCoroutine { continuation ->
            val scanner = BarcodeScanning.getClient(BarcodeAnalyzerHelper.scannerOptions)
            val image = InputImage.fromBitmap(bitmap, 0)
            scanner.process(image)
                .addOnSuccessListener { codes ->
                    if (continuation.isActive) continuation.resume(codes.size)
                    scanner.close()
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(0)
                    scanner.close()
                }
        }
}
