package com.docscan

import androidx.compose.ui.geometry.Offset
import com.docscan.scanner.BlueEdgeDetector
import com.docscan.scanner.ScannerTuning
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression for the diagonal across the two-tone notebook in the camera screenshots. */
class NotebookOuterQuadTest {
    // Approximate live-preview coordinates, normalized to the camera frame.
    private fun at(x: Float, y: Float) = Offset(x / 948f, (y - 380f) / 1108f)

    private val partialCover = listOf(
        at(216f, 890f), at(516f, 709f),
        at(812f, 1170f), at(509f, 1356f)
    )
    private val wholeNotebook = listOf(
        at(171f, 900f), at(509f, 741f),
        at(790f, 1178f), at(228f, 1430f)
    )

    @Before
    fun setUp() = BlueEdgeDetector.PremiumQuadTracker.reset()

    @After
    fun tearDown() = BlueEdgeDetector.PremiumQuadTracker.reset()

    @Test
    fun `rotated notebook polygon area identifies enclosing whole notebook`() {
        assertTrue(
            "The real outside page is 30 percent larger than the false diagonal quad",
            ScannerTuning.isLikelyOuterPageUpgrade(partialCover, wholeNotebook)
        )
        assertFalse(
            "Do not replace a tracked full page with an internal half-cover",
            ScannerTuning.isLikelyOuterPageUpgrade(wholeNotebook, partialCover)
        )
    }

    @Test
    fun `tracker immediately repairs false diagonal when physical outer page appears`() {
        val tracker = BlueEdgeDetector.PremiumQuadTracker
        repeat(6) { tracker.updateAndSmooth(partialCover, 0.88f) }

        val repaired = tracker.updateAndSmooth(wholeNotebook, 0.62f)
        assertTrue(repaired.second)
        assertTrue(
            "The bottom-left corner must return to the spiral binding edge",
            repaired.first!![3].x < 0.35f
        )
        assertTrue(
            "The bottom-left corner must reach the physical notebook bottom",
            repaired.first!![3].y > 0.90f
        )
    }

    @Test
    fun `unrelated enlarged box is not considered an outer-page upgrade`() {
        val elsewhere = listOf(
            Offset(0.68f, 0.04f), Offset(0.99f, 0.04f),
            Offset(0.99f, 0.96f), Offset(0.68f, 0.96f)
        )
        assertFalse(ScannerTuning.isLikelyOuterPageUpgrade(partialCover, elsewhere))
    }
}
