package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * Premium Camera State Enum for UI Feedback.
 */
enum class CameraState {
    MOVING,    // ব্যবহারকারী ক্যামেরা নাড়াচ্ছে
    SETTLING,  // ক্যামেরা থামছে, অটো-ফোকাস বা স্থির হওয়ার অপেক্ষায়
    STABLE     // ক্যামেরা সম্পূর্ণ স্থির, ভারী OpenCV প্রসেসিংয়ের জন্য প্রস্তুত
}

/**
 * ML Kit-style lightweight camera-shake / stillness detector.
 * 
 * Upgraded with:
 * 1. Center-weighted motion analysis
 * 2. Adaptive noise tolerance for low-light conditions
 * 3. State machine for granular UI feedback
 */
class CameraMotionDetector(
    private val gridSize: Int = 16,
    private val baseStillnessThreshold: Int = 7,
    private val requiredStableFrames: Int = 6
) {
    private var prevGrid: IntArray? = null
    private var stableFrameCount = 0
    var currentState: CameraState = CameraState.MOVING
        private set

    /**
     * Feed one frame's luma plane. Returns the current [CameraState].
     */
    fun processFrame(imageProxy: ImageProxy): CameraState {
        val grid = downsampleLuma(imageProxy)
        val prev = prevGrid

        if (prev == null || prev.size != grid.size) {
            prevGrid = grid
            return currentState
        }

        var diffSum = 0L
        var lumaSum = 0L
        var centerDiffSum = 0L

        // Premium Feature: Center Region Weighting
        // ফ্রেমের মাঝখানের (যেখানে ডকুমেন্ট থাকে) নড়াচড়াকে বেশি গুরুত্ব দেওয়া
        val centerStart = gridSize / 4
        val centerEnd = gridSize - centerStart

        for (i in grid.indices) {
            val currentPixel = grid[i]
            val prevPixel = prev[i]
            val diff = abs(currentPixel - prevPixel).toLong()

            diffSum += diff
            lumaSum += currentPixel

            val x = i % gridSize
            val y = i / gridSize
            if (x in centerStart until centerEnd && y in centerStart until centerEnd) {
                centerDiffSum += diff
            }
        }

        val avgLuma = lumaSum / grid.size
        val avgDiff = diffSum / grid.size
        val centerAvgDiff = centerDiffSum / ((centerEnd - centerStart) * (centerEnd - centerStart))

        // Premium Feature: Noise-Adaptive Thresholding
        // অন্ধকারে সেন্সরের নয়েজকে নড়াচড়া হিসেবে ভুল না করার জন্য টলারেন্স বাড়ানো
        val noiseTolerance = if (avgLuma < 50) 4 else if (avgLuma < 100) 2 else 0
        val dynamicThreshold = baseStillnessThreshold + noiseTolerance

        // Strict Motion Check: পুরো ফ্রেম এবং মাঝখানের ফ্রেম উভয়কেই স্থির হতে হবে
        val isStillThisFrame = avgDiff <= dynamicThreshold && centerAvgDiff <= (dynamicThreshold * 0.8).toInt()

        prevGrid = grid

        if (isStillThisFrame) {
            stableFrameCount++
        } else {
            stableFrameCount = 0
        }

        // State Machine Transition
        currentState = when {
            stableFrameCount >= requiredStableFrames -> CameraState.STABLE
            stableFrameCount > 0 -> CameraState.SETTLING
            else -> CameraState.MOVING
        }

        return currentState
    }

    /**
     * Convenience method to match old API if needed.
     */
    fun isStableEnoughForDetection(imageProxy: ImageProxy): Boolean {
        return processFrame(imageProxy) == CameraState.STABLE
    }

    fun reset() {
        prevGrid = null
        stableFrameCount = 0
        currentState = CameraState.MOVING
    }

    private fun downsampleLuma(imageProxy: ImageProxy): IntArray {
        val yPlane = imageProxy.planes[0] //[span_3](start_span)[span_3](end_span)
        val buffer = yPlane.buffer //[span_4](start_span)[span_4](end_span)
        val rowStride = yPlane.rowStride //[span_5](start_span)[span_5](end_span)
        val pixelStride = yPlane.pixelStride //[span_6](start_span)[span_6](end_span)
        val width = imageProxy.width //[span_7](start_span)[span_7](end_span)
        val height = imageProxy.height //[span_8](start_span)[span_8](end_span)

        val grid = IntArray(gridSize * gridSize) //[span_9](start_span)[span_9](end_span)
        val cellW = (width / gridSize).coerceAtLeast(1) //[span_10](start_span)[span_10](end_span)
        val cellH = (height / gridSize).coerceAtLeast(1) //[span_11](start_span)[span_11](end_span)
        val capacity = buffer.capacity() //[span_12](start_span)[span_12](end_span)

        for (gy in 0 until gridSize) { //[span_13](start_span)[span_13](end_span)
            for (gx in 0 until gridSize) { //[span_14](start_span)[span_14](end_span)
                val px = (gx * cellW + cellW / 2).coerceIn(0, width - 1) //[span_15](start_span)[span_15](end_span)
                val py = (gy * cellH + cellH / 2).coerceIn(0, height - 1) //[span_16](start_span)[span_16](end_span)
                val index = py * rowStride + px * pixelStride //[span_17](start_span)[span_17](end_span)
                val value = if (index in 0 until capacity) buffer.get(index).toInt() and 0xFF else 0 //[span_18](start_span)[span_18](end_span)
                grid[gy * gridSize + gx] = value //[span_19](start_span)[span_19](end_span)
            }
        }
        return grid //[span_20](start_span)[span_20](end_span)
    }
}
