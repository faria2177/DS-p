package com.docscan.util

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Accurate on-device face geometry used by Passport Photo Maker.
 * No image leaves the device. ML Kit is used only to locate the face/eyes so the
 * app can auto-center and scale the portrait to an ICAO-like composition guide.
 */
object PassportFaceAnalyzer {

    data class Result(
        val bounds: Rect,
        val leftEye: PointF?,
        val rightEye: PointF?,
        val headEulerZ: Float,
        val confidenceHint: Float
    )

    private val options by lazy {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.10f)
            .enableTracking()
            .build()
    }

    suspend fun detectPrimaryFace(bitmap: Bitmap): Result? = suspendCancellableCoroutine { cont ->
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val best = faces.maxByOrNull { face -> face.boundingBox.width() * face.boundingBox.height() }
                val result = best?.let { face ->
                    val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                    val faceArea = face.boundingBox.width().toFloat() * face.boundingBox.height().toFloat()
                    val imageArea = bitmap.width.toFloat() * bitmap.height.toFloat()
                    Result(
                        bounds = Rect(face.boundingBox),
                        leftEye = left,
                        rightEye = right,
                        headEulerZ = face.headEulerAngleZ,
                        confidenceHint = (faceArea / imageArea).coerceIn(0f, 1f)
                    )
                }
                if (cont.isActive) cont.resume(result)
                detector.close()
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
                detector.close()
            }
    }
}
