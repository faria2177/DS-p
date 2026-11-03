package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Mask Processor: handles binary and soft-edge alpha mask transformations,
 * dilation, erosion, Gaussian feathering, and seamless edge blending.
 */
object MaskProcessor {

    /**
     * Dilates a grayscale/alpha mask by radius pixels to guarantee complete coverage of ink edges.
     */
    fun dilateMask(mask: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return mask
        val w = mask.width
        val h = mask.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)

        val srcPixels = IntArray(w * h)
        mask.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outPixels = ByteArray(w * h)

        for (y in 0 until h) {
            val minY = max(0, y - radius)
            val maxY = min(h - 1, y + radius)
            for (x in 0 until w) {
                val minX = max(0, x - radius)
                val maxX = min(w - 1, x + radius)

                var maxAlpha = 0
                for (ny in minY..maxY) {
                    val row = ny * w
                    for (nx in minX..maxX) {
                        val a = Color.alpha(srcPixels[row + nx])
                        if (a > maxAlpha) {
                            maxAlpha = a
                            if (maxAlpha == 255) break
                        }
                    }
                    if (maxAlpha == 255) break
                }
                outPixels[y * w + x] = maxAlpha.toByte()
            }
        }

        // Write back to bitmap
        val argbPixels = IntArray(w * h)
        for (i in argbPixels.indices) {
            val a = outPixels[i].toInt() and 0xFF
            argbPixels[i] = Color.argb(a, 255, 255, 255)
        }
        output.setPixels(argbPixels, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Erodes a mask by radius pixels.
     */
    fun erodeMask(mask: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return mask
        val w = mask.width
        val h = mask.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(w * h)
        mask.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            val minY = max(0, y - radius)
            val maxY = min(h - 1, y + radius)
            for (x in 0 until w) {
                val minX = max(0, x - radius)
                val maxX = min(w - 1, x + radius)

                var minAlpha = 255
                for (ny in minY..maxY) {
                    val row = ny * w
                    for (nx in minX..maxX) {
                        val a = Color.alpha(srcPixels[row + nx])
                        if (a < minAlpha) {
                            minAlpha = a
                            if (minAlpha == 0) break
                        }
                    }
                    if (minAlpha == 0) break
                }
                outPixels[y * w + x] = Color.argb(minAlpha, 255, 255, 255)
            }
        }

        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Applies a separable Gaussian feather to soften mask edges and eliminate sharp transition seams.
     */
    fun featherMask(mask: Bitmap, sigma: Float = 2.5f): Bitmap {
        if (sigma <= 0.1f) return mask
        val w = mask.width
        val h = mask.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val radius = max(1, (sigma * 3).toInt())
        val kernelSize = 2 * radius + 1
        val kernel = FloatArray(kernelSize)
        var kernelSum = 0f

        for (i in -radius..radius) {
            val v = exp(-(i * i) / (2f * sigma * sigma))
            kernel[i + radius] = v
            kernelSum += v
        }
        for (i in 0 until kernelSize) {
            kernel[i] /= kernelSum
        }

        val srcPixels = IntArray(w * h)
        mask.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val alphaBuffer1 = FloatArray(w * h)
        val alphaBuffer2 = FloatArray(w * h)

        // Extract alpha
        for (i in srcPixels.indices) {
            alphaBuffer1[i] = (Color.alpha(srcPixels[i])).toFloat()
        }

        // Horizontal blur pass
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (k in -radius..radius) {
                    val sampleX = (x + k).coerceIn(0, w - 1)
                    sum += alphaBuffer1[rowOffset + sampleX] * kernel[k + radius]
                }
                alphaBuffer2[rowOffset + x] = sum
            }
        }

        // Vertical blur pass
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -radius..radius) {
                    val sampleY = (y + k).coerceIn(0, h - 1)
                    sum += alphaBuffer2[sampleY * w + x] * kernel[k + radius]
                }
                val alpha = sum.toInt().coerceIn(0, 255)
                outPixels[y * w + x] = Color.argb(alpha, 255, 255, 255)
            }
        }

        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Calculates the bounding rectangle of non-zero pixels in a mask.
     */
    fun calculateMaskBounds(mask: Bitmap): Rect? {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (Color.alpha(pixels[row + x]) > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (minX <= maxX && minY <= maxY) {
            Rect(minX, minY, maxX + 1, maxY + 1)
        } else {
            null
        }
    }
}
