package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Point
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Temporal Stability Analyzer and Exponential Moving Average Filter.
 * Prevents camera jitter, eliminates jumpiness between frames,
 * and tracks consecutive stable frames required for confidence and auto-capture.
 */
class DocumentStabilityAnalyzer(
    val minimumStableFrames: Int = 3,
    val displacementThreshold: Double = 10.0 // Max average pixel movement to count as stable
) {

    private var smoothedCorners: Array<Point>? = null
    private var previousCorners: Array<Point>? = null
    private var consecutiveStableFrames: Int = 0
    private var lastDisplacement: Double = 0.0
    private var missedFrames: Int = 0

    /**
     * Resets internal tracking state.
     */
    fun reset() {
        smoothedCorners = null
        previousCorners = null
        consecutiveStableFrames = 0
        lastDisplacement = 0.0
        missedFrames = 0
    }

    data class StabilityResult(
        val smoothedCorners: List<Point>,
        val isStable: Boolean,
        val consecutiveStableFrames: Int,
        val displacement: Double,
        val stabilityScore: Double
    )

    /**
     * Processes new raw corners from detection, applies adaptive temporal smoothing,
     * and evaluates stability across consecutive frames.
     */
    fun processCorners(rawCorners: List<Point>): StabilityResult {
        if (rawCorners.size != 4) {
            // Do not throw away a good track because one or two camera frames were blurred.
            missedFrames++
            consecutiveStableFrames = max(0, consecutiveStableFrames - 1)
            val held = smoothedCorners
            if (held != null && missedFrames <= 2) {
                return StabilityResult(
                    smoothedCorners = held.map { Point(it.x, it.y) },
                    isStable = false,
                    consecutiveStableFrames = consecutiveStableFrames,
                    displacement = lastDisplacement,
                    stabilityScore = (consecutiveStableFrames.toDouble() / 6.0).coerceIn(0.0, 1.0)
                )
            }
            reset()
            return StabilityResult(emptyList(), false, 0, 0.0, 0.0)
        }
        missedFrames = 0

        val rawArray = rawCorners.toTypedArray()
        val prev = previousCorners

        if (prev == null || smoothedCorners == null) {
            // First frame initialization
            smoothedCorners = Array(4) { Point(rawArray[it].x, rawArray[it].y) }
            previousCorners = Array(4) { Point(rawArray[it].x, rawArray[it].y) }
            consecutiveStableFrames = 1
            lastDisplacement = 0.0

            return StabilityResult(
                smoothedCorners = rawCorners,
                isStable = false,
                consecutiveStableFrames = 1,
                displacement = 0.0,
                stabilityScore = 0.2
            )
        }

        // Calculate average corner displacement from previous frame
        var totalDist = 0.0
        for (i in 0 until 4) {
            totalDist += hypot(rawArray[i].x - prev[i].x, rawArray[i].y - prev[i].y)
        }
        val avgDisplacement = totalDist / 4.0
        lastDisplacement = avgDisplacement

        // Update stability counter
        if (avgDisplacement <= displacementThreshold) {
            consecutiveStableFrames++
        } else {
            // Reset or decay stability counter on sudden movement
            consecutiveStableFrames = max(0, consecutiveStableFrames - 2)
        }

        // Adaptive smoothing factor:
        // High alpha (fast response) when camera is actively moving;
        // Low alpha (heavy damping) when nearly stationary to cancel hand jitter.
        val alpha = when {
            avgDisplacement > displacementThreshold * 3.0 -> 0.85 // Fast tracking
            avgDisplacement > displacementThreshold * 1.5 -> 0.50
            consecutiveStableFrames >= minimumStableFrames -> 0.18 // Strong jitter reduction
            else -> 0.35
        }

        val currentSmoothed = smoothedCorners!!
        for (i in 0 until 4) {
            currentSmoothed[i].x = alpha * rawArray[i].x + (1.0 - alpha) * currentSmoothed[i].x
            currentSmoothed[i].y = alpha * rawArray[i].y + (1.0 - alpha) * currentSmoothed[i].y
            prev[i].x = rawArray[i].x
            prev[i].y = rawArray[i].y
        }

        val isStable = consecutiveStableFrames >= minimumStableFrames
        val stabilityScore = (consecutiveStableFrames.toDouble() / 6.0).coerceIn(0.0, 1.0)

        return StabilityResult(
            smoothedCorners = currentSmoothed.map { Point(it.x, it.y) },
            isStable = isStable,
            consecutiveStableFrames = consecutiveStableFrames,
            displacement = avgDisplacement,
            stabilityScore = stabilityScore
        )
    }

    fun getConsecutiveStableFrames(): Int = consecutiveStableFrames
    fun isCurrentlyStable(): Boolean = consecutiveStableFrames >= minimumStableFrames

    /**
     * Non-mutating peek at the stability score accumulated as of the *previous*
     * call to [processCorners]. Callers that need a "prior" stability estimate
     * before this frame's corners are known (e.g. to bias detection scoring)
     * must use this instead of calling [processCorners] with a dummy/empty
     * list — [processCorners] treats any non-4-point input as "no detection
     * this frame" and calls [reset]() as a real side effect, which would wipe
     * out [consecutiveStableFrames] and [smoothedCorners] every single frame.
     */
    fun currentStabilityScore(): Double = (consecutiveStableFrames.toDouble() / 6.0).coerceIn(0.0, 1.0)
}
