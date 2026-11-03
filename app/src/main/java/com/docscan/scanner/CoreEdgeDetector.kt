package com.docscan.scanner

// Scanner pipeline revision: 2026-09 — stable page-boundary tuning.

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Document quad detector ported from the `android_edge_detection_core`
 * reference project (the native Android side of the `edge_detection`
 * Flutter plugin — see its `PaperProcessor.kt`: `processPicture` /
 * `findContours` / `getCorners` / `sortPoints`).
 *
 * This is intentionally the simple, original algorithm — fixed-threshold
 * Canny + dilation + external contours + approxPolyDP — kept as a single
 * self-contained pass with no cross-frame state. It is used as a first,
 * proven detection attempt before the app's own multi-channel pipeline
 * (see [com.docscan.util.EdgeDetector]) runs its heavier fallback stages.
 *
 * Point order returned matches this app's convention: TL, TR, BR, BL
 * (same as [Corner.orderQuad]).
 */
object CoreEdgeDetector {

    /**
     * Detects a 4-point document quad in [graySrc] (a single-channel,
     * already-upright Mat — e.g. the app's working-resolution grayscale
     * frame). Returns corners in the SAME pixel coordinate space as
     * [graySrc] (caller normalizes by width/height), or null if no
     * confident 4-point convex candidate is found.
     */
    fun detectQuad(graySrc: Mat): List<Point>? {
        val contours = findContours(graySrc)
        return getCorners(contours, graySrc.size())
    }

    private fun findContours(src: Mat): List<MatOfPoint> {
        val size = Size(src.size().width, src.size().height)
        val prepped = Mat(size, CvType.CV_8UC1)
        val cannedImage = Mat(size, CvType.CV_8UC1)
        val dilated = Mat(size, CvType.CV_8UC1)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))

        return try {
            // src may already be single-channel grayscale (as produced by
            // this app's YUV Y-plane pipeline); convert defensively only
            // when it isn't.
            if (src.channels() == 1) {
                src.copyTo(prepped)
            } else {
                Imgproc.cvtColor(src, prepped, Imgproc.COLOR_BGR2GRAY)
            }

            Imgproc.GaussianBlur(prepped, prepped, Size(5.0, 5.0), 0.0)
            Imgproc.threshold(prepped, prepped, 20.0, 255.0, Imgproc.THRESH_TRIANGLE)
            Imgproc.Canny(prepped, cannedImage, 75.0, 200.0)
            Imgproc.dilate(cannedImage, dilated, kernel)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            try {
                Imgproc.findContours(
                    dilated,
                    contours,
                    hierarchy,
                    Imgproc.RETR_TREE,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )
            } finally {
                hierarchy.release()
            }

            contours
                .filter { c -> Imgproc.contourArea(c) > 100e2 }
                .sortedByDescending { c -> Imgproc.contourArea(c) }
                .take(25)
        } finally {
            prepped.release()
            cannedImage.release()
            dilated.release()
            kernel.release()
        }
    }

    private fun getCorners(contours: List<MatOfPoint>, size: Size): List<Point>? {
        val indexTo = when (contours.size) {
            in 0..5 -> contours.size - 1
            else -> 4
        }
        for (index in 0..contours.size) {
            if (index !in 0..indexTo) return null

            val c2f = MatOfPoint2f(*contours[index].toArray())
            try {
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(c2f, approx, 0.03 * peri, true)
                    val points = approx.toArray().asList()
                    val convex = MatOfPoint()
                    try {
                        approx.convertTo(convex, CvType.CV_32S)
                        if (points.size == 4 && Imgproc.isContourConvex(convex)) {
                            return sortPoints(points)
                        }
                    } finally {
                        convex.release()
                    }
                } finally {
                    approx.release()
                }
            } finally {
                c2f.release()
            }
        }
        return null
    }

    /** TL, TR, BR, BL — same convention as [Corner.orderQuad]. */
    private fun sortPoints(points: List<Point>): List<Point> {
        val p0 = points.minByOrNull { it.x + it.y } ?: return points
        val p1 = points.minByOrNull { it.y - it.x } ?: return points
        val p2 = points.maxByOrNull { it.x + it.y } ?: return points
        val p3 = points.maxByOrNull { it.y - it.x } ?: return points
        return listOf(p0, p1, p2, p3)
    }
}
