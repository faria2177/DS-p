package com.docscan.ui.remover.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance 8-bit mask processor.
 * Features:
 * - Hole filling: fills unintentional holes inside the subject (e.g., eyes, white shirt, inner bag loops)
 * - Island suppression: removes floating noisy pixels in the background
 * - Precision bounding box calculation for smart Auto-Crop
 */
object MaskProcessor {

    /**
     * Converts a 2D grayscale/alpha byte array into an ARGB_8888 Alpha Mask Bitmap
     */
    fun createAlphaMaskBitmap(alphaBytes: ByteArray, width: Int, height: Int): Bitmap {
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val a = alphaBytes[i].toInt() and 0xFF
            // Grayscale visualization: white = subject, black = background
            pixels[i] = Color.argb(a, a, a, a)
        }
        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return maskBitmap
    }

    /**
     * Extracts alpha byte array (0..255) from an ARGB_8888 mask bitmap
     */
    fun extractAlphaBytes(maskBitmap: Bitmap): ByteArray {
        val width = maskBitmap.width
        val height = maskBitmap.height
        val pixels = IntArray(width * height)
        maskBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bytes = ByteArray(width * height)
        for (i in pixels.indices) {
            bytes[i] = (Color.alpha(pixels[i])).toByte()
        }
        return bytes
    }

    /**
     * Fills holes inside the subject.
     * Algorithm:
     * - Invert mask (background becomes 1, subject becomes 0).
     * - Flood-fill from outer image boundaries (all 4 edges) to find TRUE background.
     * - Any background pixel that was NOT reached from the image border is an internal enclosed hole!
     * - Turn all unreached internal holes into solid subject (alpha = 255).
     */
    fun fillSubjectHoles(maskBytes: ByteArray, width: Int, height: Int): ByteArray {
        val total = width * height
        val isTrueBackground = BooleanArray(total)
        val queue = ArrayDeque<Int>()

        // Seed outer borders into queue
        for (x in 0 until width) {
            // Top border
            val topIdx = x
            if ((maskBytes[topIdx].toInt() and 0xFF) < 128) {
                isTrueBackground[topIdx] = true
                queue.add(topIdx)
            }
            // Bottom border
            val botIdx = (height - 1) * width + x
            if ((maskBytes[botIdx].toInt() and 0xFF) < 128 && !isTrueBackground[botIdx]) {
                isTrueBackground[botIdx] = true
                queue.add(botIdx)
            }
        }
        for (y in 0 until height) {
            // Left border
            val leftIdx = y * width
            if ((maskBytes[leftIdx].toInt() and 0xFF) < 128 && !isTrueBackground[leftIdx]) {
                isTrueBackground[leftIdx] = true
                queue.add(leftIdx)
            }
            // Right border
            val rightIdx = y * width + (width - 1)
            if ((maskBytes[rightIdx].toInt() and 0xFF) < 128 && !isTrueBackground[rightIdx]) {
                isTrueBackground[rightIdx] = true
                queue.add(rightIdx)
            }
        }

        // Flood fill 4-connectivity
        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        while (!queue.isEmpty()) {
            val curr = queue.poll()
            val cx = curr % width
            val cy = curr / width

            for (dir in 0 until 4) {
                val nx = cx + dx[dir]
                val ny = cy + dy[dir]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!isTrueBackground[nIdx] && (maskBytes[nIdx].toInt() and 0xFF) < 128) {
                        isTrueBackground[nIdx] = true
                        queue.add(nIdx)
                    }
                }
            }
        }

        // Fill any enclosed holes
        val result = maskBytes.clone()
        for (i in 0 until total) {
            val alpha = maskBytes[i].toInt() and 0xFF
            if (!isTrueBackground[i] && alpha < 128) {
                // This is an internal hole (shirt/eye/limbs/center) -> preserve subject!
                result[i] = 255.toByte()
            }
        }
        return result
    }

    /**
     * Removes small isolated floating pixel islands in the background (dust / artifacts)
     */
    fun removeSmallNoiseIslands(maskBytes: ByteArray, width: Int, height: Int, minIslandSize: Int = 100): ByteArray {
        val total = width * height
        val visited = BooleanArray(total)
        val result = maskBytes.clone()
        val queue = ArrayDeque<Int>()
        val currentComponent = ArrayList<Int>(minIslandSize * 2)

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        for (i in 0 until total) {
            val alpha = maskBytes[i].toInt() and 0xFF
            if (alpha > 40 && !visited[i]) {
                currentComponent.clear()
                visited[i] = true
                queue.add(i)
                currentComponent.add(i)

                while (!queue.isEmpty()) {
                    val curr = queue.poll()
                    val cx = curr % width
                    val cy = curr / width

                    for (d in 0 until 4) {
                        val nx = cx + dx[d]
                        val ny = cy + dy[d]
                        if (nx in 0 until width && ny in 0 until height) {
                            val nIdx = ny * width + nx
                            if (!visited[nIdx] && (maskBytes[nIdx].toInt() and 0xFF) > 40) {
                                visited[nIdx] = true
                                queue.add(nIdx)
                                currentComponent.add(nIdx)
                            }
                        }
                    }
                }

                if (currentComponent.size < minIslandSize) {
                    // Small disconnected speck -> remove
                    for (idx in currentComponent) {
                        result[idx] = 0
                    }
                }
            }
        }
        return result
    }

    /**
     * Finds the tight bounding box around foreground pixels (alpha > threshold)
     */
    fun computeSubjectBoundingBox(maskBytes: ByteArray, width: Int, height: Int, threshold: Int = 30): Rect {
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var found = false

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val a = maskBytes[rowOffset + x].toInt() and 0xFF
                if (a > threshold) {
                    found = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (found) {
            Rect(minX, minY, maxX + 1, maxY + 1)
        } else {
            Rect(0, 0, width, height)
        }
    }

    /**
     * Calculates padded auto-crop rectangle bounded within original dimensions
     */
    fun computeAutoCropRect(box: Rect, imageWidth: Int, imageHeight: Int, paddingRatio: Float): Rect {
        if (paddingRatio <= 0f) {
            return Rect(
                max(0, box.left),
                max(0, box.top),
                min(imageWidth, box.right),
                min(imageHeight, box.bottom)
            )
        }
        val padX = ((box.width()) * paddingRatio).toInt()
        val padY = ((box.height()) * paddingRatio).toInt()

        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(imageWidth, box.right + padX)
        val bottom = min(imageHeight, box.bottom + padY)

        return Rect(left, top, right, bottom)
    }
}
