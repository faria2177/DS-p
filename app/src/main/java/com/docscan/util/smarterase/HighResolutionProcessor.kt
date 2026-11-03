package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-Resolution Inpainting Processor:
 * Ensures the preview operates fast on an optimized preview bitmap, while the final output
 * is processed at full original capture resolution without any quality degradation.
 */
object HighResolutionProcessor {

    /**
     * Reconstructs the full-resolution original image using the normalized edit mask.
     */
    suspend fun processFullResolution(
        highResOriginal: Bitmap,
        previewMask: Bitmap,
        config: InpaintConfig = InpaintConfig()
    ): Bitmap = withContext(Dispatchers.Default) {
        val origW = highResOriginal.width
        val origH = highResOriginal.height

        // 1. Scale mask up to exact full high-resolution bitmap dimensions
        val highResMask = Bitmap.createScaledBitmap(previewMask, origW, origH, true)

        // 2. Perform intelligent document-aware inpainting on full high-res
        val engine = InpaintingEngineFactory.createEngine(config.mode)
        val reconstructed = engine.reconstruct(highResOriginal, highResMask, config)

        if (!highResMask.isRecycled && highResMask != previewMask) {
            highResMask.recycle()
        }

        return@withContext reconstructed
    }
}
