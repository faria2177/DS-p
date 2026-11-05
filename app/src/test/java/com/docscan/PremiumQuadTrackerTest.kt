package com.docscan

import androidx.compose.ui.geometry.Offset
import com.docscan.scanner.BlueEdgeDetector
import com.docscan.scanner.ScannerTuning
import org.opencv.core.Point
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PremiumQuadTrackerTest {

    private val quad = listOf(
        Offset(0.12f, 0.12f),
        Offset(0.88f, 0.12f),
        Offset(0.88f, 0.88f),
        Offset(0.12f, 0.88f)
    )

    @Before
    fun setUp() {
        BlueEdgeDetector.PremiumQuadTracker.reset()
    }

    @After
    fun tearDown() {
        BlueEdgeDetector.PremiumQuadTracker.reset()
    }

    @Test
    fun `new document needs consecutive detection frames`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker

        assertFalse(tracker.updateAndSmooth(quad, 0.9f).second)
        assertTrue(tracker.updateAndSmooth(quad, 0.9f).second)
    }

    @Test
    fun `paper can lock despite one intermittent missing detection frame`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        assertFalse(tracker.updateAndSmooth(quad, 0.52f).second)
        assertFalse(tracker.updateAndSmooth(null, 0f).second)
        assertTrue(tracker.updateAndSmooth(quad, 0.52f).second)
    }

    @Test
    fun `unrelated paper shape cannot complete a pending lock`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        val elsewhere = listOf(
            Offset(0.38f, 0.28f), Offset(0.78f, 0.28f),
            Offset(0.78f, 0.76f), Offset(0.38f, 0.76f)
        )
        assertFalse(tracker.updateAndSmooth(quad, 0.8f).second)
        assertFalse(tracker.updateAndSmooth(null, 0f).second)
        assertFalse(tracker.updateAndSmooth(elsewhere, 0.8f).second)
    }

    @Test
    fun `bright full sheet survives weak borders but inner printed frame does not`() {
        assertTrue(ScannerTuning.isSoftWhitePaperBoundary(0.46f, 15.0, 12.0, 1, 0.085))
        assertFalse(ScannerTuning.isSoftWhitePaperBoundary(0.08f, 0.0, 15.0, 4, 0.30))
        assertFalse(ScannerTuning.isSoftWhitePaperBoundary(0.46f, 0.5, 2.0, 1, 0.08))
    }

    @Test
    fun `stale corners have bounded preview grace and can be reset`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker

        repeat(3) { tracker.updateAndSmooth(quad, 0.9f) }
        val temporaryLoss = tracker.updateAndSmooth(null, 0f)
        assertTrue(temporaryLoss.second)
        assertNotNull(temporaryLoss.first)

        // Grace is bounded; a document must eventually disappear if no
        // fresh camera frame confirms the quad.
        repeat(11) { assertTrue(tracker.updateAndSmooth(null, 0f).second) }
        assertFalse(tracker.updateAndSmooth(null, 0f).second)

        repeat(3) { tracker.updateAndSmooth(quad, 0.9f) }
        tracker.reset()
        assertFalse(tracker.updateAndSmooth(null, 0f).second)
    }

    @Test
    fun `small repeated detection jitter leaves the locked corners steady`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        repeat(4) { tracker.updateAndSmooth(quad, 0.92f) }
        val steady = tracker.updateAndSmooth(quad, 0.92f).first!!

        // Roughly 4-5px at preview scale: larger than the previous threshold,
        // yet too small to justify visible movement of a locked page outline.
        val slightNoise = quad.map { Offset(it.x + 0.0045f, it.y + 0.001f) }
        repeat(6) {
            val result = tracker.updateAndSmooth(slightNoise, 0.91f)
            assertTrue(result.second)
            result.first!!.forEachIndexed { index, corner ->
                org.junit.Assert.assertEquals(steady[index].x, corner.x, 0.0001f)
                org.junit.Assert.assertEquals(steady[index].y, corner.y, 0.0001f)
            }
        }
    }

    @Test
    fun `a brief strong inner contour does not pull the outline off the page`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        repeat(6) { tracker.updateAndSmooth(quad, 0.92f) }
        val inner = listOf(
            Offset(0.29f, 0.30f), Offset(0.71f, 0.30f),
            Offset(0.71f, 0.70f), Offset(0.29f, 0.70f)
        )
        repeat(2) {
            val held = tracker.updateAndSmooth(inner, 0.94f)
            assertTrue(held.second)
            assertTrue("A transient inner form must not replace the page", held.first!![0].x < 0.20f)
        }
        val recovered = tracker.updateAndSmooth(quad, 0.92f)
        assertTrue(recovered.second)
        assertTrue(recovered.first!![0].x < 0.20f)
    }

    @Test
    fun `three real paper borders must not validate an imaginary diagonal`() {
        // Reproduces the four vertices shown in the user's live camera image:
        // the proposed bottom edge cuts across the white paper, missing BR.
        val skewed = arrayOf(
            Point(125.0, 370.0), Point(702.0, 378.0),
            Point(607.0, 787.0), Point(90.0, 1084.0)
        )
        val minimumAngle = ScannerTuning.minInteriorAngleDegrees(skewed)
        assertTrue(minimumAngle < 60.0)
        assertFalse(
            ScannerTuning.hasPhysicalFourSideBoundary(
                doubleArrayOf(0.24, 0.19, 0.008, 0.21),
                doubleArrayOf(22.0, 18.0, 0.5, 20.0),
                minimumAngle,
                0.51f
            )
        )
    }

    @Test
    fun `subtle white page boundary can still pass with a faint fourth side`() {
        val almostRectangular = arrayOf(
            Point(90.0, 80.0), Point(580.0, 85.0),
            Point(590.0, 790.0), Point(80.0, 785.0)
        )
        assertTrue(
            ScannerTuning.hasPhysicalFourSideBoundary(
                doubleArrayOf(0.15, 0.12, 0.02, 0.19),
                doubleArrayOf(15.0, 13.0, 1.0, 17.0),
                ScannerTuning.minInteriorAngleDegrees(almostRectangular),
                0.53f
            )
        )
        assertFalse(
            ScannerTuning.hasPhysicalFourSideBoundary(
                doubleArrayOf(0.15, 0.02, 0.02, 0.19),
                doubleArrayOf(15.0, 1.0, 1.0, 17.0),
                ScannerTuning.minInteriorAngleDegrees(almostRectangular),
                0.53f
            )
        )
    }

    @Test
    fun `isolated corner warp cannot produce a diagonal in steady tracked sheet`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        repeat(5) { tracker.updateAndSmooth(quad, 0.90f) }
        val isolatedWarp = quad.toMutableList().apply {
            this[2] = Offset(0.88f, 0.56f)
        }
        repeat(6) {
            val held = tracker.updateAndSmooth(isolatedWarp, 0.92f)
            assertTrue(held.second)
            assertTrue("BR must stay near the physical bottom right corner", held.first!![2].y > 0.80f)
        }
    }
    @Test
    fun `outer document beats a small high-contrast form cell`() {
        val internalCell = ScannerTuning.outerPagePriority(0.79f, 0.09f, 0.0)
        val fullSheet = ScannerTuning.outerPagePriority(0.57f, 0.51f, 12.0)
        assertTrue("Outer paper boundary should rank ahead of inner form boxes", fullSheet > internalCell)
    }

    @Test
    fun `tracker unlocks inner box when enclosing paper is found`() {
        val inner = listOf(
            Offset(0.26f, 0.30f), Offset(0.74f, 0.30f),
            Offset(0.74f, 0.70f), Offset(0.26f, 0.70f)
        )
        val outer = listOf(
            Offset(0.07f, 0.09f), Offset(0.93f, 0.09f),
            Offset(0.93f, 0.91f), Offset(0.07f, 0.91f)
        )
        repeat(5) { BlueEdgeDetector.PremiumQuadTracker.updateAndSmooth(inner, 0.94f) }
        val upgraded = BlueEdgeDetector.PremiumQuadTracker.updateAndSmooth(outer, 0.58f)
        assertTrue(upgraded.second)
        assertNotNull(upgraded.first)
        assertTrue("Bigger paper should replace stale inner box", upgraded.first!![0].x < 0.15f)
    }

    @Test
    fun `small printed inner form is rejected without physical paper contrast`() {
        assertTrue(ScannerTuning.isLikelyPrintedInnerFrame(0.09f, 0.8, 3.0))
        assertFalse(ScannerTuning.isLikelyPrintedInnerFrame(0.09f, 13.0, 3.0))
        assertFalse(ScannerTuning.isLikelyPrintedInnerFrame(0.09f, -15.0, 23.0))
        assertFalse(ScannerTuning.isLikelyPrintedInnerFrame(0.42f, 0.8, 3.0))
    }

}
