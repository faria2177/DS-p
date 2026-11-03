package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.Point
import kotlin.math.hypot
import kotlin.math.max

/**
 * State machine and lightweight temporal tracker for document boundaries.
 * Implements grace period recovery to eliminate visual flicker and jump rejection.
 */
class DocumentTracker(
    val maxGraceFrames: Int = 3,
    val maxJumpThreshold: Double = 110.0 // Reject instantaneous anomalous jumps
) {

    enum class TrackingState {
        NO_DOCUMENT,
        DETECTING,
        TRACKING,
        STABLE,
        LOST,
        REDETECTING
    }

    data class TrackedDocument(
        val corners: List<Point>,
        val state: TrackingState,
        val confidence: Double,
        val isStable: Boolean,
        val isRecoveredFromGrace: Boolean = false
    )

    private var state: TrackingState = TrackingState.NO_DOCUMENT
    private var trackedCorners: List<Point>? = null
    private var previousCorners: List<Point>? = null
    private var lostFrameCount: Int = 0
    private var trackingConfidence: Double = 0.0

    fun reset() {
        state = TrackingState.NO_DOCUMENT
        trackedCorners = null
        previousCorners = null
        lostFrameCount = 0
        trackingConfidence = 0.0
    }

    /**
     * Updates the tracker with the latest frame's detection result.
     */
    fun update(
        detection: DocumentDetectionResult,
        isTemporallyStable: Boolean
    ): TrackedDocument {
        val hasValidNewDetection = detection.isValid && detection.corners.size == 4 && detection.confidence >= 0.44

        if (hasValidNewDetection) {
            val newCorners = detection.corners

            // Jump rejection check: if previously stable, reject a single-frame sudden massive jump
            if ((state == TrackingState.STABLE || state == TrackingState.TRACKING) && trackedCorners != null) {
                val dist = averageDistance(trackedCorners!!, newCorners)
                if (dist > maxJumpThreshold && detection.confidence < 0.90) {
                    // Possible spurious false positive (e.g. table edge or background glitch)
                    lostFrameCount++
                    if (lostFrameCount <= 2) {
                        return TrackedDocument(
                            corners = trackedCorners!!,
                            state = TrackingState.TRACKING,
                            confidence = trackingConfidence * 0.90,
                            isStable = false,
                            isRecoveredFromGrace = true
                        )
                    }
                }
            }

            // Normal valid update
            previousCorners = trackedCorners
            trackedCorners = newCorners
            lostFrameCount = 0
            trackingConfidence = detection.confidence

            state = if (isTemporallyStable && detection.confidence >= 0.66) {
                TrackingState.STABLE
            } else {
                TrackingState.TRACKING
            }

            return TrackedDocument(
                corners = newCorners,
                state = state,
                confidence = detection.confidence,
                isStable = isTemporallyStable
            )

        } else {
            // Detection was missing or invalid this frame
            if (trackedCorners != null && (state == TrackingState.STABLE || state == TrackingState.TRACKING || state == TrackingState.LOST)) {
                lostFrameCount++
                if (lostFrameCount <= maxGraceFrames) {
                    state = TrackingState.LOST
                    // Decay confidence during grace period
                    trackingConfidence *= 0.85

                    return TrackedDocument(
                        corners = trackedCorners!!,
                        state = TrackingState.LOST,
                        confidence = trackingConfidence,
                        isStable = false,
                        isRecoveredFromGrace = true
                    )
                } else {
                    // Grace period expired
                    state = TrackingState.REDETECTING
                    trackedCorners = null
                    previousCorners = null
                    lostFrameCount = 0
                    trackingConfidence = 0.0

                    return TrackedDocument(
                        corners = emptyList(),
                        state = TrackingState.NO_DOCUMENT,
                        confidence = 0.0,
                        isStable = false
                    )
                }
            } else {
                state = TrackingState.NO_DOCUMENT
                return TrackedDocument(
                    corners = emptyList(),
                    state = TrackingState.NO_DOCUMENT,
                    confidence = 0.0,
                    isStable = false
                )
            }
        }
    }

    private fun averageDistance(c1: List<Point>, c2: List<Point>): Double {
        if (c1.size != 4 || c2.size != 4) return 0.0
        var total = 0.0
        for (i in 0 until 4) {
            total += hypot(c1[i].x - c2[i].x, c1[i].y - c2[i].y)
        }
        return total / 4.0
    }

    fun getCurrentState(): TrackingState = state
}
