package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.os.SystemClock

/**
 * Intelligent Auto-Capture Controller.
 * Verifies document confidence, geometric stability, focus sharpness,
 * exposure, and low camera movement before triggering capture.
 * Provides countdown animation progress (0.0 to 1.0) and debouncing.
 */
class AutoCaptureController(
    val minConfidence: Double = 0.70,
    val minStableFrames: Int = 3,
    val countdownDurationMs: Long = 500L,
    val cooldownMs: Long = 2500L
) {

    enum class State {
        IDLE,
        COUNTING_DOWN,
        CAPTURING,
        COOLDOWN
    }

    private var state = State.IDLE
    private var countdownStartTime = 0L
    private var lastCaptureTime = 0L
    private var progress = 0f

    fun reset() {
        state = State.IDLE
        countdownStartTime = 0L
        progress = 0f
    }

    data class AutoCaptureStatus(
        val shouldCaptureNow: Boolean,
        val progress: Float, // 0.0 to 1.0 for UI circular countdown ring
        val isArmed: Boolean
    )

    /**
     * Evaluates whether auto-capture conditions are met and steps the countdown timer.
     */
    fun evaluate(
        detection: DocumentDetectionResult,
        quality: DocumentQualityAnalyzer.QualityReport,
        isEnabled: Boolean
    ): AutoCaptureStatus {
        val now = SystemClock.elapsedRealtime()

        if (!isEnabled) {
            reset()
            return AutoCaptureStatus(false, 0f, false)
        }

        // Cooldown check
        if (state == State.COOLDOWN) {
            if (now - lastCaptureTime < cooldownMs) {
                return AutoCaptureStatus(false, 0f, false)
            } else {
                state = State.IDLE
            }
        }

        // Check if all conditions are satisfied
        val conditionsMet = detection.isValid &&
                detection.confidence >= minConfidence &&
                detection.isStable &&
                quality.isReadyForAutoCapture

        if (conditionsMet) {
            when (state) {
                State.IDLE -> {
                    state = State.COUNTING_DOWN
                    countdownStartTime = now
                    progress = 0f
                }
                State.COUNTING_DOWN -> {
                    val elapsed = now - countdownStartTime
                    progress = (elapsed.toFloat() / countdownDurationMs.toFloat()).coerceIn(0f, 1f)

                    if (elapsed >= countdownDurationMs) {
                        state = State.COOLDOWN
                        lastCaptureTime = now
                        progress = 1f
                        return AutoCaptureStatus(shouldCaptureNow = true, progress = 1f, isArmed = true)
                    }
                }
                else -> {}
            }
        } else {
            // Conditions dropped (e.g. camera jerked or document moved) -> cancel countdown
            if (state == State.COUNTING_DOWN) {
                state = State.IDLE
                progress = 0f
            }
        }

        return AutoCaptureStatus(
            shouldCaptureNow = false,
            progress = progress,
            isArmed = state == State.COUNTING_DOWN
        )
    }

    fun getProgress(): Float = progress
}
