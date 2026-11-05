package com.docscan

import androidx.compose.ui.geometry.Offset
import com.docscan.scanner.BlueEdgeDetector
import com.docscan.scanner.ScannerTuning
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
