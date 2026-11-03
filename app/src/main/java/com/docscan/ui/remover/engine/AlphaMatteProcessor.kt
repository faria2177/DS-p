package com.docscan.ui.remover.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.docscan.ui.remover.model.BackgroundStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Alpha Matte Processor & Background Compositor.
 * Supports:
 * - True 32-bit transparent PNG bitmap generation
 * - Natural contact shadow preservation and soft blending
 * - Dynamic background replacement (Transparent, Solid colors, Gradients, Blurred original, Custom image)
 */
object AlphaMatteProcessor {

    /**
     * Builds a true transparent ARGB_8888 bitmap from clean RGB pixels and refined alpha bytes
     */
    fun createTransparentBitmap(
        cleanPixels: IntArray,
        alphaBytes: ByteArray,
        width: Int,
        height: Int,
        keepNaturalShadow: Boolean = false,
        originalPixels: IntArray? = null
    ): Bitmap {
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalPixels = IntArray(width * height)

        for (i in finalPixels.indices) {
            val alpha = alphaBytes[i].toInt() and 0xFF
            if (alpha == 0) {
                if (keepNaturalShadow && originalPixels != null) {
                    // Check if original background had a natural shadow (contact shadow)
                    val orig = originalPixels[i]
                    val lum = (0.299 * Color.red(orig) + 0.587 * Color.green(orig) + 0.114 * Color.blue(orig)).toInt()
                    // If area is noticeably darker than standard white/bright background, preserve as subtle shadow
                    if (lum in 15..160) {
                        val shadowAlpha = ((160 - lum) / 160.0f * 70).toInt().coerceIn(0, 90)
                        finalPixels[i] = Color.argb(shadowAlpha, 20, 20, 25)
                    } else {
                        finalPixels[i] = 0
                    }
                } else {
                    finalPixels[i] = 0
                }
            } else {
                val rgb = cleanPixels[i]
                finalPixels[i] = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            }
        }

        resultBitmap.setPixels(finalPixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    /**
     * Composites the transparent subject over a selected background style
     */
    fun compositeOverBackground(
        subjectBitmap: Bitmap,
        style: BackgroundStyle,
        originalBitmap: Bitmap
    ): Bitmap {
        val width = subjectBitmap.width
        val height = subjectBitmap.height

        if (style is BackgroundStyle.Transparent) {
            return subjectBitmap
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (style) {
            is BackgroundStyle.Solid -> {
                val androidColor = android.graphics.Color.argb(
                    (style.color.alpha * 255).toInt(),
                    (style.color.red * 255).toInt(),
                    (style.color.green * 255).toInt(),
                    (style.color.blue * 255).toInt()
                )
                canvas.drawColor(androidColor)
            }
            is BackgroundStyle.Gradient -> {
                val colors = style.colors.map { c ->
                    android.graphics.Color.argb(
                        (c.alpha * 255).toInt(),
                        (c.red * 255).toInt(),
                        (c.green * 255).toInt(),
                        (c.blue * 255).toInt()
                    )
                }.toIntArray()

                paint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    colors, null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
            is BackgroundStyle.Blur -> {
                val blurred = fastBoxBlur(originalBitmap, radius = 24)
                canvas.drawBitmap(blurred, 0f, 0f, paint)
                // Slight dark overlay for depth
                canvas.drawColor(android.graphics.Color.argb(40, 0, 0, 0))
            }
            is BackgroundStyle.CustomPhoto -> {
                // Scale custom photo to fill canvas nicely
                val srcRect = Rect(0, 0, style.bitmap.width, style.bitmap.height)
                val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(style.bitmap, srcRect, dstRect, paint)
            }
            else -> {}
        }

        // Draw foreground subject on top
        canvas.drawBitmap(subjectBitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Fast high-quality Box Blur for background blur without heavy overhead
     */
    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val downscaled = Bitmap.createScaledBitmap(src, max(1, w / 4), max(1, h / 4), true)
        val dw = downscaled.width
        val dh = downscaled.height
        val pixels = IntArray(dw * dh)
        downscaled.getPixels(pixels, 0, dw, 0, 0, dw, dh)

        val rad = radius.coerceIn(2, 20)
        val result = IntArray(dw * dh)

        for (y in 0 until dh) {
            for (x in 0 until dw) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (ky in -rad..rad step 2) {
                    val ny = (y + ky).coerceIn(0, dh - 1)
                    for (kx in -rad..rad step 2) {
                        val nx = (x + kx).coerceIn(0, dw - 1)
                        val p = pixels[ny * dw + nx]
                        r += Color.red(p)
                        g += Color.green(p)
                        b += Color.blue(p)
                        count++
                    }
                }
                result[y * dw + x] = Color.rgb(r / count, g / count, b / count)
            }
        }
        val blurredSmall = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        blurredSmall.setPixels(result, 0, dw, 0, 0, dw, dh)

        val blurredFull = Bitmap.createScaledBitmap(blurredSmall, w, h, true)
        if (downscaled != src) downscaled.recycle()
        blurredSmall.recycle()
        return blurredFull
    }
}
