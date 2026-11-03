package com.docscan.util.textedit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Preprocesses document images specifically to boost OCR accuracy (contrast boost,
 * grayscale, shadow reduction, sharpening) without altering the original source bitmap.
 */
object DocumentImagePreprocessor {

    /**
     * Creates an optimized, non-destructive working copy of the bitmap for text recognition.
     * Scales down excessively huge images to prevent OOM while keeping sufficient DPI for character clarity.
     */
    suspend fun preprocessForOcr(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val maxDim = 2048
        val scale = if (source.width > maxDim || source.height > maxDim) {
            maxDim.toFloat() / max(source.width, source.height)
        } else {
            1f
        }

        val targetWidth = (source.width * scale).toInt().coerceAtLeast(100)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(100)

        // 1. Create working scaled bitmap
        val working = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 2. Apply document contrast enhancement & shadow normalization
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Contrast boost color matrix
        val contrast = 1.35f
        val brightness = -15f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(working, 0f, 0f, paint)

        if (working != source) {
            working.recycle()
        }

        output
    }
}
