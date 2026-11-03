package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DocumentEditorImageAnalyzer {
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    suspend fun analyze(
        bitmap: Bitmap,
        pageIndex: Int,
        ocrDocument: OcrDocument? = null
    ): List<EditorImageElement> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val output = mutableListOf<EditorImageElement>()
        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)

        val barcodes = suspendCancellableCoroutine<List<Barcode>> { continuation ->
            barcodeScanner.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
        barcodes.forEach { barcode ->
            val box = barcode.boundingBox ?: return@forEach
            output += EditorImageElement(
                pageIndex = pageIndex,
                type = EditorElementType.QR_BARCODE,
                normalizedRect = RectF(box.left / w, box.top / h, box.right / w, box.bottom / h),
                label = "QR / Barcode",
                protected = true
            )
        }

        val faces = suspendCancellableCoroutine<List<Face>> { continuation ->
            faceDetector.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
        faces.forEach { face ->
            val box = face.boundingBox
            output += EditorImageElement(
                pageIndex = pageIndex,
                type = EditorElementType.PHOTO,
                normalizedRect = RectF(box.left / w, box.top / h, box.right / w, box.bottom / h),
                label = "Photo / Face",
                protected = false
            )
        }

        ocrDocument?.allLines?.forEach { line ->
            val text = line.text.trim()
            if (text.contains("<<<") || text.startsWith("P<") || text.startsWith("I<")) {
                output += EditorImageElement(
                    pageIndex = pageIndex,
                    type = EditorElementType.MRZ,
                    normalizedRect = RectF(line.normalizedRect),
                    label = "MRZ / Identity data",
                    protected = true
                )
            }
        }

        output.distinctBy { element ->
            "${element.type}:${(element.normalizedRect.left * 100).toInt()}:${(element.normalizedRect.top * 100).toInt()}"
        }
    }
}
