package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import android.graphics.ImageFormat
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import kotlin.math.*

/* ============================================================
 *  TRACKER STATE
 * ============================================================ */

enum class TrackerState {
    SEARCHING,
    DETECTED,
    STABLE,
    LOST
}

/* ============================================================
 *  RESULT
 * ============================================================ */

data class TrackedQuadState(
    val corners: List<Offset>,
    val state: TrackerState,
    val isStable: Boolean,
    val stableFramesCount: Int,
    val confidence: Float,
    val detectionConfidence: Float = confidence,
    val frameAspectRatio: Float = 3f / 4f,
    val movement: Float = 0f
)

/* ============================================================
 *  DOCUMENT DETECTION RESULT
 * ============================================================ */

data class QuadTrackerDetectionResult(
    val corners: List<Offset>,
    val confidence: Float,
    val areaRatio: Float,
    val rectangularity: Float,
    val angleScore: Float,
    val edgeScore: Float
)

/* ============================================================
 *  OBJECT RESULT
 *
 *  This is geometry-based object recognition.
 *  Semantic object recognition requires ML Kit/TFLite.
 * ============================================================ */

data class DetectedObject(
    val bounds: RectFNormalized,
    val confidence: Float,
    val type: ObjectType
)

enum class ObjectType {
    DOCUMENT,
    RECTANGLE,
    UNKNOWN
}

data class RectFNormalized(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/* ============================================================
 *  ONE EURO FILTER
 * ============================================================ */

private class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.5f,
    private val dCutoff: Float = 1.0f
) {

    private var initialized = false
    private var xPrev = 0f
    private var dxPrev = 0f
    private var previousTime = 0L

    private fun alpha(
        cutoff: Float,
        dt: Float
    ): Float {

        val safeDt =
            dt.coerceIn(
                1f / 120f,
                0.10f
            )

        val safeCutoff =
            cutoff.coerceIn(
                0.05f,
                60f
            )

        val tau =
            1f /
                (2f * Math.PI.toFloat() *
                    safeCutoff)

        return (
            1f /
                (1f + tau / safeDt)
            ).coerceIn(
                0f,
                1f
            )
    }

    fun filter(
        value: Float,
        timestampNanos: Long
    ): Float {

        if (!value.isFinite()) {
            return xPrev
        }

        if (!initialized) {

            initialized = true
            xPrev = value
            dxPrev = 0f
            previousTime =
                timestampNanos

            return value
        }

        val rawDt =
            (
                timestampNanos -
                    previousTime
                ).coerceAtLeast(1L) /
                1_000_000_000f

        val dt =
            rawDt.coerceIn(
                1f / 120f,
                0.10f
            )

        previousTime =
            timestampNanos

        val dx =
            (value - xPrev) / dt

        val derivativeAlpha =
            alpha(
                dCutoff,
                dt
            )

        val dxHat =
            dxPrev +
                derivativeAlpha *
                (dx - dxPrev)

        val cutoff =
            minCutoff +
                beta * abs(dxHat)

        val valueAlpha =
            alpha(
                cutoff,
                dt
            )

        val result =
            xPrev +
                valueAlpha *
                (value - xPrev)

        xPrev = result
        dxPrev = dxHat

        return result
    }

    fun reset() {

        initialized = false
        xPrev = 0f
        dxPrev = 0f
        previousTime = 0L
    }
}

/* ============================================================
 *  ADVANCED QUAD TRACKER
 * ============================================================ */

class QuadTracker(
    private val historySize: Int = 8,

    private val stabilityVarianceThreshold:
        Float = 0.009f,

    private val deadbandThreshold:
        Float = 0.0035f,

    private val maxDisplacementPerFrame:
        Float = 0.045f,

    private val minStableFrames:
        Int = 5,

    private val lostGraceFrames:
        Int = 7,

    private val lockThreshold:
        Float = 0.66f,

    private val keepThreshold:
        Float = 0.46f,

    private val filterMinCutoff:
        Float = 1.1f,

    private val filterBeta:
        Float = 0.55f
) {

    private val history =
        mutableListOf<List<Offset>>()

    private var currentCorners =
        defaultCorners()

    private var lastAcceptedCandidate:
        List<Offset>? = null

    private var lockedStableCorners:
        List<Offset>? = null

    private var stableFrameCount = 0

    private var lostFrameCount = 0

    private var currentState =
        TrackerState.SEARCHING

    private var lastConfidence = 0f

    private var previousMovement = 0f

    private val filtersX =
        Array(4) {
            OneEuroFilter(
                filterMinCutoff,
                filterBeta
            )
        }

    private val filtersY =
        Array(4) {
            OneEuroFilter(
                filterMinCutoff,
                filterBeta
            )
        }

    /* --------------------------------------------------------
     * PROCESS FRAME
     * -------------------------------------------------------- */

    fun processFrame(
        candidateCorners: List<Offset>,
        isDocumentDetected: Boolean,
        candidateConfidence: Float,
        frameAspectRatio: Float = 3f / 4f
    ): TrackedQuadState {

        val validCandidate =
            isValidQuad(candidateCorners) &&
                candidateConfidence.isFinite() &&
                candidateConfidence >= 0f

        val normalizedCandidate =
            if (validCandidate) {
                normalizeQuad(
                    candidateCorners
                )
            } else {
                null
            }

        val continuity =
            if (
                normalizedCandidate != null &&
                lastAcceptedCandidate != null
            ) {
                quadDistance(
                    normalizedCandidate,
                    lastAcceptedCandidate!!
                )
            } else {
                0f
            }

        val continuityLimit =
            max(
                0.18f,
                maxDisplacementPerFrame * 3f
            )

        val continuityAccepted =
            normalizedCandidate != null &&
                (
                    lastAcceptedCandidate == null ||
                        currentState ==
                        TrackerState.SEARCHING ||
                        continuity <=
                        continuityLimit
                    )

        val detectorAccepted =
            normalizedCandidate != null &&
                continuityAccepted &&
                (
                    (
                        currentState ==
                            TrackerState.SEARCHING &&
                            candidateConfidence >=
                            lockThreshold
                        ) ||
                        (
                            currentState !=
                                TrackerState.SEARCHING &&
                            candidateConfidence >=
                            keepThreshold
                        )
                    ) &&
                isDocumentDetected

        val target =
            if (detectorAccepted) {
                normalizedCandidate!!
            } else {
                currentCorners
            }

        val now =
            System.nanoTime()

        val result =
            ArrayList<Offset>(4)

        var movement = 0f

        for (i in 0 until 4) {

            val old =
                currentCorners[i]

            val targetPoint =
                target[i]

            val dx =
                targetPoint.x - old.x

            val dy =
                targetPoint.y - old.y

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                ).toFloat()

            movement += distance

            if (
                detectorAccepted &&
                distance <
                deadbandThreshold
            ) {

                filtersX[i].filter(
                    old.x,
                    now
                )

                filtersY[i].filter(
                    old.y,
                    now
                )

                result.add(old)
                continue
            }

            val isLargeOutlier =
                detectorAccepted &&
                    distance > 0.30f &&
                    stableFrameCount >=
                    minStableFrames

            val input =
                if (isLargeOutlier) {
                    old
                } else {
                    targetPoint
                }

            val filteredX =
                filtersX[i].filter(
                    input.x,
                    now
                )

            val filteredY =
                filtersY[i].filter(
                    input.y,
                    now
                )

            val adaptiveStep =
                when {
                    currentState ==
                        TrackerState.SEARCHING ->
                        maxDisplacementPerFrame *
                            1.5f

                    movement > 0.10f ->
                        maxDisplacementPerFrame *
                            1.5f

                    else ->
                        maxDisplacementPerFrame
                }

            val finalX =
                old.x +
                    (
                        filteredX -
                            old.x
                        ).coerceIn(
                            -adaptiveStep,
                            adaptiveStep
                        )

            val finalY =
                old.y +
                    (
                        filteredY -
                            old.y
                        ).coerceIn(
                            -adaptiveStep,
                            adaptiveStep
                        )

            result.add(
                Offset(
                    finalX.coerceIn(
                        0.005f,
                        0.995f
                    ),
                    finalY.coerceIn(
                        0.005f,
                        0.995f
                    )
                )
            )
        }

        previousMovement =
            movement / 4f

        currentCorners =
            enforceQuadShape(
                result,
                currentCorners
            )

        if (detectorAccepted) {

            lastAcceptedCandidate =
                currentCorners

            lastConfidence =
                candidateConfidence
                    .coerceIn(
                        0f,
                        1f
                    )

            lostFrameCount = 0

        } else {

            lastConfidence *= 0.92f

            lostFrameCount++
        }

        history.add(
            currentCorners
        )

        if (history.size >
            historySize
        ) {
            history.removeAt(0)
        }

        val stability =
            calculateStability()

        if (
            detectorAccepted &&
            stability <
            stabilityVarianceThreshold
        ) {

            stableFrameCount++

            if (
                stableFrameCount >=
                minStableFrames
            ) {

                lockedStableCorners =
                    currentCorners
            }

        } else {

            stableFrameCount =
                (
                    stableFrameCount - 1
                    ).coerceAtLeast(0)

            if (
                previousMovement >
                0.08f
            ) {
                lockedStableCorners = null
            }
        }

        val stable =
            detectorAccepted &&
                history.size >= 3 &&
                stableFrameCount >=
                minStableFrames &&
                stability <
                stabilityVarianceThreshold

        currentState =
            when {

                stable ->
                    TrackerState.STABLE

                detectorAccepted ->
                    TrackerState.DETECTED

                lostFrameCount <
                    lostGraceFrames &&
                    !isDefaultCorners(
                        currentCorners
                    ) ->
                    TrackerState.DETECTED

                lostFrameCount <
                    lostGraceFrames + 3 ->
                    TrackerState.LOST

                else -> {

                    lockedStableCorners = null

                    TrackerState.SEARCHING
                }
            }

        val output =
            if (
                currentState ==
                    TrackerState.STABLE &&
                lockedStableCorners != null
            ) {
                lockedStableCorners!!
            } else {
                currentCorners
            }

        return TrackedQuadState(
            corners = output,
            state = currentState,
            isStable = stable,
            stableFramesCount =
                stableFrameCount,
            confidence =
                lastConfidence,
            detectionConfidence =
                candidateConfidence
                    .coerceIn(
                        0f,
                        1f
                    ),
            frameAspectRatio =
                frameAspectRatio,
            movement =
                previousMovement
        )
    }

    /* --------------------------------------------------------
     * STABILITY
     * -------------------------------------------------------- */

    private fun calculateStability():
        Float {

        if (history.size < 3) {
            return Float.MAX_VALUE
        }

        val latest =
            history.last()

        val recent =
            history.takeLast(
                min(
                    history.size,
                    historySize / 2
                ).coerceAtLeast(2)
            )

        var maximum = 0f

        for (frame in recent) {

            for (i in 0 until 4) {

                val distance =
                    hypot(
                        (
                            latest[i].x -
                                frame[i].x
                            ).toDouble(),

                        (
                            latest[i].y -
                                frame[i].y
                            ).toDouble()
                    ).toFloat()

                maximum =
                    max(
                        maximum,
                        distance
                    )
            }
        }

        return maximum
    }

    /* --------------------------------------------------------
     * QUAD VALIDATION
     * -------------------------------------------------------- */

    private fun isValidQuad(
        points: List<Offset>
    ): Boolean {

        if (points.size != 4) {
            return false
        }

        return points.all {
            it.x.isFinite() &&
                it.y.isFinite()
        }
    }

    private fun normalizeQuad(
        points: List<Offset>
    ): List<Offset> {

        return points
            .take(4)
            .map {
                Offset(
                    it.x.coerceIn(
                        0.005f,
                        0.995f
                    ),
                    it.y.coerceIn(
                        0.005f,
                        0.995f
                    )
                )
            }
    }

    private fun quadDistance(
        a: List<Offset>,
        b: List<Offset>
    ): Float {

        if (
            a.size != 4 ||
            b.size != 4
        ) {
            return Float.MAX_VALUE
        }

        var sum = 0f

        for (i in 0 until 4) {

            sum +=
                hypot(
                    (
                        a[i].x -
                            b[i].x
                        ).toDouble(),

                    (
                        a[i].y -
                            b[i].y
                        ).toDouble()
                ).toFloat()
        }

        return sum / 4f
    }

    private fun enforceQuadShape(
        points: List<Offset>,
        fallback: List<Offset>
    ): List<Offset> {

        if (points.size != 4) {
            return fallback
        }

        val area =
            polygonArea(points)

        if (area < 0.008f) {
            return fallback
        }

        if (
            points.any {
                !it.x.isFinite() ||
                    !it.y.isFinite()
            }
        ) {
            return fallback
        }

        return points
    }

    private fun polygonArea(
        points: List<Offset>
    ): Float {

        var area = 0f

        for (i in 0 until 4) {

            val p1 =
                points[i]

            val p2 =
                points[
                    (i + 1) % 4
                ]

            area +=
                p1.x * p2.y -
                p2.x * p1.y
        }

        return abs(area) * 0.5f
    }

    private fun isDefaultCorners(
        points: List<Offset>
    ): Boolean {

        val defaults =
            defaultCorners()

        if (points.size != 4) {
            return false
        }

        return points.indices.all { i ->

            abs(
                points[i].x -
                    defaults[i].x
            ) < 0.0001f &&

            abs(
                points[i].y -
                    defaults[i].y
            ) < 0.0001f
        }
    }

    fun reset() {

        history.clear()

        currentCorners =
            defaultCorners()

        lastAcceptedCandidate = null

        lockedStableCorners = null

        stableFrameCount = 0

        lostFrameCount = 0

        currentState =
            TrackerState.SEARCHING

        lastConfidence = 0f

        previousMovement = 0f

        for (i in 0 until 4) {

            filtersX[i].reset()
            filtersY[i].reset()
        }
    }

    companion object {

        fun defaultCorners():
            List<Offset> {

            return listOf(

                Offset(
                    0.08f,
                    0.08f
                ),

                Offset(
                    0.92f,
                    0.08f
                ),

                Offset(
                    0.92f,
                    0.92f
                ),

                Offset(
                    0.08f,
                    0.92f
                )
            )
        }
    }
}

/* ============================================================
 *  OPEN CV DOCUMENT DETECTOR
 * ============================================================ */

class OpenCvDocumentDetector(
    private val minAreaRatio:
        Double = ScannerTuning.MIN_LIVE_AREA_RATIO,

    private val maxAreaRatio:
        Double = ScannerTuning.MAX_LIVE_AREA_RATIO
) {

    private val clahe: CLAHE =
        Imgproc.createCLAHE(
            2.0,
            Size(8.0, 8.0)
        )

    fun detect(
        rgba: Mat
    ): QuadTrackerDetectionResult? {

        if (rgba.empty()) {
            return null
        }

        val gray =
            Mat()

        val enhanced =
            Mat()

        val blurred =
            Mat()

        val edges =
            Mat()

        val threshold =
            Mat()

        val combined =
            Mat()

        val hierarchy =
            Mat()

        try {

            Imgproc.cvtColor(
                rgba,
                gray,
                Imgproc.COLOR_RGBA2GRAY
            )

            clahe.apply(
                gray,
                enhanced
            )

            Imgproc.GaussianBlur(
                enhanced,
                blurred,
                Size(5.0, 5.0),
                0.0
            )

            /*
             * Edge detector.
             */

            Imgproc.Canny(
                blurred,
                edges,
                45.0,
                150.0,
                3,
                true
            )

            /*
             * Adaptive threshold helps under
             * uneven illumination.
             */

            Imgproc.adaptiveThreshold(
                blurred,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                7.0
            )

            Core.bitwise_not(
                threshold,
                threshold
            )

            /*
             * Combine edge + threshold.
             */

            Core.bitwise_or(
                edges,
                threshold,
                combined
            )

            val kernel =
                Imgproc.getStructuringElement(
                    Imgproc.MORPH_RECT,
                    Size(5.0, 5.0)
                )

            Imgproc.morphologyEx(
                combined,
                combined,
                Imgproc.MORPH_CLOSE,
                kernel
            )

            Imgproc.dilate(
                combined,
                combined,
                kernel,
                Point(-1.0, -1.0),
                1
            )

            val contours =
                ArrayList<MatOfPoint>()

            Imgproc.findContours(
                combined,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea =
                rgba.width().toDouble() *
                    rgba.height().toDouble()

            var best:
                QuadTrackerDetectionResult? =
                null

            /*
             * Largest contours first.
             */

            val sortedContours =
                contours.sortedByDescending {
                    abs(
                        Imgproc.contourArea(
                            it
                        )
                    )
                }.take(40)

            for (
                contour in
                sortedContours
            ) {

                val area =
                    abs(
                        Imgproc.contourArea(
                            contour
                        )
                    )

                val areaRatio =
                    area / imageArea

                if (
                    areaRatio <
                    minAreaRatio ||
                    areaRatio >
                    maxAreaRatio
                ) {
                    continue
                }

                val contour2f =
                    MatOfPoint2f(
                        *contour.toArray()
                    )

                val perimeter =
                    Imgproc.arcLength(
                        contour2f,
                        true
                    )

                val approx =
                    MatOfPoint2f()

                Imgproc.approxPolyDP(
                    contour2f,
                    approx,
                    perimeter * 0.018,
                    true
                )

                if (
                    approx.total() != 4L
                ) {
                    contour2f.release()
                    approx.release()
                    continue
                }

                val points =
                    orderCorners(
                        approx.toArray()
                    )

                if (
                    !isConvex(points)
                ) {
                    contour2f.release()
                    approx.release()
                    continue
                }

                if (
                    !validAngles(points)
                ) {
                    contour2f.release()
                    approx.release()
                    continue
                }

                val quadArea =
                    polygonArea(
                        points
                    )

                if (
                    quadArea <=
                    imageArea *
                    minAreaRatio
                ) {
                    contour2f.release()
                    approx.release()
                    continue
                }

                val rectangularity =
                    (
                        quadArea /
                            max(
                                area,
                                1.0
                            )
                        ).coerceIn(
                            0.0,
                            1.0
                        )

                val angleScore =
                    calculateAngleScore(
                        points
                    )

                val edgeScore =
                    calculateEdgeScore(
                        edges,
                        points
                    )

                val borderScore =
                    calculateBorderScore(
                        points,
                        rgba.width(),
                        rgba.height()
                    )

                val sizeScore =
                    (
                        (
                            areaRatio -
                                minAreaRatio
                            ) /
                            (
                                maxAreaRatio -
                                    minAreaRatio
                                )
                        ).coerceIn(
                            0.0,
                            1.0
                        )

                /*
                 * Premium confidence fusion.
                 */

                val confidence =
                    (
                        sizeScore *
                            0.20 +

                        rectangularity *
                            0.20 +

                        angleScore *
                            0.25 +

                        edgeScore *
                            0.25 +

                        borderScore *
                            0.10
                        ).coerceIn(
                            0.0,
                            1.0
                        )

                val candidate =
                    QuadTrackerDetectionResult(
                        corners =
                            points.map {
                                Offset(
                                    (
                                        it.x /
                                            rgba.width()
                                        ).toFloat(),

                                    (
                                        it.y /
                                            rgba.height()
                                        ).toFloat()
                                )
                            },

                        confidence =
                            confidence.toFloat(),

                        areaRatio =
                            areaRatio.toFloat(),

                        rectangularity =
                            rectangularity.toFloat(),

                        angleScore =
                            angleScore.toFloat(),

                        edgeScore =
                            edgeScore.toFloat()
                    )

                if (
                    best == null ||
                    candidate.confidence >
                    best!!.confidence
                ) {
                    best = candidate
                }

                contour2f.release()
                approx.release()
            }

            return best

        } finally {

            gray.release()
            enhanced.release()
            blurred.release()
            edges.release()
            threshold.release()
            combined.release()
            hierarchy.release()
        }
    }

    /* --------------------------------------------------------
     * CORNER ORDER
     * TL -> TR -> BR -> BL
     * -------------------------------------------------------- */

    private fun orderCorners(
        points: Array<Point>
    ): Array<Point> {

        if (points.size != 4) {
            return points
        }

        val centerX =
            points.map { it.x }
                .average()

        val centerY =
            points.map { it.y }
                .average()

        val sorted =
            points.sortedBy {
                atan2(
                    it.y - centerY,
                    it.x - centerX
                )
            }

        /*
         * Find top-left using minimum x+y.
         */

        val tlIndex =
            sorted.indices.minBy {
                sorted[it].x +
                    sorted[it].y
            }

        val rotated =
            List(4) { index ->
                sorted[
                    (tlIndex + index) %
                        4
                ]
            }

        /*
         * Ensure clockwise order.
         */

        val area =
            polygonArea(
                rotated.toTypedArray()
            )

        return if (area > 0) {

            arrayOf(
                rotated[0],
                rotated[1],
                rotated[2],
                rotated[3]
            )

        } else {

            arrayOf(
                rotated[0],
                rotated[3],
                rotated[2],
                rotated[1]
            )
        }
    }

    /* --------------------------------------------------------
     * CONVEX
     * -------------------------------------------------------- */

    private fun isConvex(
        points: Array<Point>
    ): Boolean {

        if (points.size != 4) {
            return false
        }

        var positive = false
        var negative = false

        for (i in 0 until 4) {

            val a =
                points[i]

            val b =
                points[
                    (i + 1) % 4
                ]

            val c =
                points[
                    (i + 2) % 4
                ]

            val cross =
                (
                    b.x - a.x
                    ) *
                    (
                        c.y - b.y
                        ) -

                    (
                        b.y - a.y
                        ) *
                    (
                        c.x - b.x
                        )

            if (cross > 0) {
                positive = true
            }

            if (cross < 0) {
                negative = true
            }

            if (
                positive &&
                negative
            ) {
                return false
            }
        }

        return true
    }

    /* --------------------------------------------------------
     * ANGLES
     * -------------------------------------------------------- */

    private fun validAngles(
        points: Array<Point>
    ): Boolean {

        for (i in 0 until 4) {

            val angle =
                angleBetween(
                    points[
                        (i + 3) % 4
                    ],

                    points[i],

                    points[
                        (i + 1) % 4
                    ]
                )

            if (
                angle < 35.0 ||
                angle > 145.0
            ) {
                return false
            }
        }

        return true
    }

    private fun angleBetween(
        a: Point,
        b: Point,
        c: Point
    ): Double {

        val v1x =
            a.x - b.x

        val v1y =
            a.y - b.y

        val v2x =
            c.x - b.x

        val v2y =
            c.y - b.y

        val dot =
            v1x * v2x +
                v1y * v2y

        val len1 =
            hypot(
                v1x,
                v1y
            )

        val len2 =
            hypot(
                v2x,
                v2y
            )

        if (
            len1 < 0.001 ||
            len2 < 0.001
        ) {
            return 0.0
        }

        return Math.toDegrees(
            acos(
                (
                    dot /
                        (
                            len1 *
                                len2
                            )
                    ).coerceIn(
                        -1.0,
                        1.0
                    )
            )
        )
    }

    private fun calculateAngleScore(
        points: Array<Point>
    ): Double {

        var score = 0.0

        for (i in 0 until 4) {

            val angle =
                angleBetween(
                    points[
                        (i + 3) % 4
                    ],

                    points[i],

                    points[
                        (i + 1) % 4
                    ]
                )

            val error =
                abs(
                    angle - 90.0
                )

            score +=
                (
                    1.0 -
                        error / 90.0
                    ).coerceIn(
                        0.0,
                        1.0
                    )
        }

        return score / 4.0
    }

    /* --------------------------------------------------------
     * EDGE SUPPORT
     * -------------------------------------------------------- */

    private fun calculateEdgeScore(
        edgeImage: Mat,
        points: Array<Point>
    ): Double {

        if (edgeImage.empty()) {
            return 0.0
        }

        var score = 0.0
        var samples = 0

        for (i in 0 until 4) {

            val start =
                points[i]

            val end =
                points[
                    (i + 1) % 4
                ]

            for (s in 0..20) {

                val t =
                    s / 20.0

                val x =
                    (
                        start.x +
                            (
                                end.x -
                                    start.x
                                ) * t
                        ).roundToInt()

                val y =
                    (
                        start.y +
                            (
                                end.y -
                                    start.y
                                ) * t
                        ).roundToInt()

                if (
                    x >= 1 &&
                    y >= 1 &&
                    x < edgeImage.cols() - 1 &&
                    y < edgeImage.rows() - 1
                ) {

                    val value =
                        edgeImage.get(
                            y,
                            x
                        )?.firstOrNull()
                            ?: 0.0

                    if (value > 0) {
                        score++
                    }

                    samples++
                }
            }
        }

        if (samples == 0) {
            return 0.0
        }

        return (
            score / samples
            ).coerceIn(
                0.0,
                1.0
            )
    }

    /* --------------------------------------------------------
     * BORDER SCORE
     * -------------------------------------------------------- */

    private fun calculateBorderScore(
        points: Array<Point>,
        width: Int,
        height: Int
    ): Double {

        val marginX =
            width * 0.015

        val marginY =
            height * 0.015

        var score = 0.0

        for (p in points) {

            val dx =
                min(
                    p.x,
                    width - p.x
                )

            val dy =
                min(
                    p.y,
                    height - p.y
                )

            if (
                dx > marginX &&
                dy > marginY
            ) {
                score += 1.0
            }
        }

        return score / 4.0
    }

    private fun polygonArea(
        points: Array<Point>
    ): Double {

        var area = 0.0

        for (i in points.indices) {

            val p1 =
                points[i]

            val p2 =
                points[
                    (i + 1) %
                        points.size
                ]

            area +=
                p1.x * p2.y -
                p2.x * p1.y
        }

        return abs(area) * 0.5
    }
}

/* ============================================================
 *  OPEN CV GEOMETRIC OBJECT RECOGNIZER
 * ============================================================ */

class OpenCvObjectRecognizer {

    fun detect(
        rgba: Mat,
        document: QuadTrackerDetectionResult?
    ): List<DetectedObject> {

        if (rgba.empty()) {
            return emptyList()
        }

        val gray =
            Mat()

        val edges =
            Mat()

        val hierarchy =
            Mat()

        try {

            Imgproc.cvtColor(
                rgba,
                gray,
                Imgproc.COLOR_RGBA2GRAY
            )

            Imgproc.GaussianBlur(
                gray,
                gray,
                Size(5.0, 5.0),
                0.0
            )

            Imgproc.Canny(
                gray,
                edges,
                60.0,
                160.0
            )

            val contours =
                ArrayList<MatOfPoint>()

            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val results =
                mutableListOf<DetectedObject>()

            for (contour in contours) {

                val area =
                    abs(
                        Imgproc.contourArea(
                            contour
                        )
                    )

                val imageArea =
                    rgba.width()
                        .toDouble() *
                        rgba.height()
                        .toDouble()

                val ratio =
                    area / imageArea

                if (
                    ratio < 0.02 ||
                    ratio > 0.90
                ) {
                    continue
                }

                val rect =
                    Imgproc.boundingRect(
                        contour
                    )

                val normalized =
                    RectFNormalized(

                        left =
                            rect.x.toFloat() /
                                rgba.width(),

                        top =
                            rect.y.toFloat() /
                                rgba.height(),

                        right =
                            (
                                rect.x +
                                    rect.width
                                ).toFloat() /
                                rgba.width(),

                        bottom =
                            (
                                rect.y +
                                    rect.height
                                ).toFloat() /
                                rgba.height()
                    )

                val confidence =
                    (
                        ratio * 2.0
                    ).coerceIn(
                        0.25,
                        0.95
                    ).toFloat()

                /*
                 * Ignore the detected document itself.
                 */

                if (
                    document != null &&
                    overlapsDocument(
                        normalized,
                        document.corners
                    )
                ) {
                    continue
                }

                results.add(
                    DetectedObject(
                        bounds = normalized,
                        confidence = confidence,
                        type =
                            ObjectType.RECTANGLE
                    )
                )
            }

            return results
                .sortedByDescending {
                    it.confidence
                }
                .take(10)

        } finally {

            gray.release()
            edges.release()
            hierarchy.release()
        }
    }

    private fun overlapsDocument(
        rect: RectFNormalized,
        corners: List<Offset>
    ): Boolean {

        if (corners.size != 4) {
            return false
        }

        val left =
            corners.minOf {
                it.x
            }

        val right =
            corners.maxOf {
                it.x
            }

        val top =
            corners.minOf {
                it.y
            }

        val bottom =
            corners.maxOf {
                it.y
            }

        val centerX =
            (
                rect.left +
                    rect.right
                ) * 0.5f

        val centerY =
            (
                rect.top +
                    rect.bottom
                ) * 0.5f

        return centerX in left..right &&
            centerY in top..bottom
    }
}

/* ============================================================
 *  CAMERA X + OPEN CV ANALYZER
 * ============================================================ */

class CameraXDocumentAnalyzer(
    private val detector:
        OpenCvDocumentDetector,

    private val tracker:
        QuadTracker,

    private val objectRecognizer:
        OpenCvObjectRecognizer =
        OpenCvObjectRecognizer(),

    private val onResult:
        (
            TrackedQuadState,
            List<DetectedObject>
        ) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(
        image: ImageProxy
    ) {

        var rgba: Mat? = null

        try {

            rgba =
                imageProxyToRgbaMat(
                    image
                )

            if (
                rgba.empty()
            ) {
                return
            }

            /*
             * CameraX rotation.
             */

            rgba =
                rotateMat(
                    rgba,
                    image.imageInfo.rotationDegrees
                )

            /*
             * Document detection.
             */

            val detection =
                detector.detect(
                    rgba
                )

            /*
             * Temporal tracking.
             */

            val tracked =
                tracker.processFrame(

                    candidateCorners =
                        detection?.corners
                            ?: QuadTracker
                                .defaultCorners(),

                    isDocumentDetected =
                        detection != null,

                    candidateConfidence =
                        detection?.confidence
                            ?: 0f,

                    frameAspectRatio =
                        rgba.width()
                            .toFloat() /
                        rgba.height()
                            .toFloat()
                )

            /*
             * Object detection.
             */

            val objects =
                objectRecognizer.detect(
                    rgba,
                    detection
                )

            onResult(
                tracked,
                objects
            )

        } catch (
            exception: Exception
        ) {

            /*
             * Never crash the camera analyzer.
             */

            exception.printStackTrace()

        } finally {

            rgba?.release()

            image.close()
        }
    }

    /* --------------------------------------------------------
     * IMAGE PROXY -> RGBA MAT
     * -------------------------------------------------------- */

    private fun imageProxyToRgbaMat(
        image: ImageProxy
    ): Mat {

        val width =
            image.width

        val height =
            image.height

        val nv21 =
            yuv420ToNv21(
                image
            )

        val yuv =
            Mat(
                height +
                    height / 2,
                width,
                CvType.CV_8UC1
            )

        yuv.put(
            0,
            0,
            nv21
        )

        val rgba =
            Mat()

        Imgproc.cvtColor(
            yuv,
            rgba,
            Imgproc.COLOR_YUV2RGBA_NV21
        )

        yuv.release()

        return rgba
    }

    /* --------------------------------------------------------
     * YUV 420 -> NV21
     * -------------------------------------------------------- */

    private fun yuv420ToNv21(
        image: ImageProxy
    ): ByteArray {

        val width =
            image.width

        val height =
            image.height

        val yPlane =
            image.planes[0]

        val uPlane =
            image.planes[1]

        val vPlane =
            image.planes[2]

        val ySize =
            width * height

        val uvSize =
            width * height / 2

        val output =
            ByteArray(
                ySize + uvSize
            )

        var outputIndex = 0

        /*
         * Y plane.
         */

        val yBuffer =
            yPlane.buffer

        val yRowStride =
            yPlane.rowStride

        val yPixelStride =
            yPlane.pixelStride

        val row =
            ByteArray(
                yRowStride
            )

        for (y in 0 until height) {

            yBuffer.position(
                y * yRowStride
            )

            yBuffer.get(
                row,
                0,
                min(
                    yRowStride,
                    yBuffer.remaining()
                )
            )

            for (x in 0 until width) {

                val index =
                    x * yPixelStride

                output[
                    outputIndex++
                ] =
                    row[
                        index.coerceIn(
                            0,
                            row.lastIndex
                        )
                    ]
            }
        }

        /*
         * UV planes.
         *
         * NV21 requires V then U.
         */

        val uBuffer =
            uPlane.buffer

        val vBuffer =
            vPlane.buffer

        val uRowStride =
            uPlane.rowStride

        val vRowStride =
            vPlane.rowStride

        val uPixelStride =
            uPlane.pixelStride

        val vPixelStride =
            vPlane.pixelStride

        val uvHeight =
            height / 2

        val uvWidth =
            width / 2

        for (y in 0 until uvHeight) {

            val uRowStart =
                y * uRowStride

            val vRowStart =
                y * vRowStride

            for (x in 0 until uvWidth) {

                val uIndex =
                    uRowStart +
                        x * uPixelStride

                val vIndex =
                    vRowStart +
                        x * vPixelStride

                if (
                    vIndex <
                    vBuffer.limit() &&
                    uIndex <
                    uBuffer.limit()
                ) {

                    output[
                        outputIndex++
                    ] =
                        vBuffer.get(
                            vIndex
                        )

                    output[
                        outputIndex++
                    ] =
                        uBuffer.get(
                            uIndex
                        )
                }
            }
        }

        return output
    }

    /* --------------------------------------------------------
     * ROTATION
     * -------------------------------------------------------- */

    private fun rotateMat(
        source: Mat,
        degrees: Int
    ): Mat {

        if (
            degrees % 360 == 0
        ) {
            return source
        }

        val result =
            Mat()

        when (
            degrees % 360
        ) {

            90 -> {

                Core.rotate(
                    source,
                    result,
                    Core.ROTATE_90_CLOCKWISE
                )

                source.release()
            }

            180 -> {

                Core.rotate(
                    source,
                    result,
                    Core.ROTATE_180
                )

                source.release()
            }

            270 -> {

                Core.rotate(
                    source,
                    result,
                    Core.ROTATE_90_COUNTERCLOCKWISE
                )

                source.release()
            }

            else -> {
                return source
            }
        }

        return result
    }
}

/* ============================================================
 *  CAMERA FACTORY
 * ============================================================ */

object ScannerCamera {

    fun createAnalyzer(
        tracker: QuadTracker =
            QuadTracker(),

        detector:
            OpenCvDocumentDetector =
            OpenCvDocumentDetector(),

        onResult:
            (
                TrackedQuadState,
                List<DetectedObject>
            ) -> Unit
    ): ImageAnalysis.Analyzer {

        return CameraXDocumentAnalyzer(
            detector = detector,
            tracker = tracker,
            onResult = onResult
        )
    }
}

/* ============================================================
 *  OPEN CV INITIALIZATION
 * ============================================================ */

object OpenCvManager {

    fun initialize(): Boolean {

        return try {

            OpenCVLoader.initLocal()

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()

            false
        }
    }
}