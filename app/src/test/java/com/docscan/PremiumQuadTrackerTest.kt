package com.docscan

import androidx.compose.ui.geometry.Offset
import com.docscan.scanner.BlueEdgeDetector
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
        repeat(7) { assertTrue(tracker.updateAndSmooth(null, 0f).second) }
        assertFalse(tracker.updateAndSmooth(null, 0f).second)

        repeat(3) { tracker.updateAndSmooth(quad, 0.9f) }
        tracker.reset()
        assertFalse(tracker.updateAndSmooth(null, 0f).second)
    }
}
