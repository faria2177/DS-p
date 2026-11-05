package com.docscan

import com.docscan.scanner.ScannerTuning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

/** Regression for a torn white sheet on a shiny, floral printed tablecloth. */
class PaperSurfaceRankingTest {

    @Test
    fun `smooth ruled paper outranks patterned tablecloth interior`() {
        val width = 180
        val height = 180
        val pixels = ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val onPaper = x in 35..145 && y in 30..150
            val luma = if (onPaper) {
                if (y % 17 == 0) 118 else 163
            } else {
                55 + ((x * 73 + y * 97 + x * y * 13) % 151)
            }
            luma.toByte()
        }
        val paperQuad = arrayOf(
            Point(35.0, 30.0), Point(145.0, 30.0),
            Point(145.0, 150.0), Point(35.0, 150.0)
        )
        val patternedQuad = arrayOf(
            Point(3.0, 38.0), Point(29.0, 38.0),
            Point(29.0, 135.0), Point(3.0, 135.0)
        )

        val paper = ScannerTuning.pageSurfaceScore(pixels, width, height, paperQuad)
        val pattern = ScannerTuning.pageSurfaceScore(pixels, width, height, patternedQuad)
        assertTrue("The ruled sheet must be smooth enough for the fallback", paper >= 0.68f)
        assertTrue("A floral texture must not win on raw edge strength", paper > pattern + 0.30f)
    }

    @Test
    fun `physical page outranks a stronger but smaller decorative polygon`() {
        val realSheet = ScannerTuning.outerPagePriority(
            confidence = 0.55f, areaRatio = 0.42f,
            signedPaperContrast = -13.0, surfaceScore = 0.85f
        )
        val decoration = ScannerTuning.outerPagePriority(
            confidence = 0.83f, areaRatio = 0.12f,
            signedPaperContrast = 8.0, surfaceScore = 0.08f
        )
        assertTrue("Interior material evidence must outrank decorative edges", realSheet > decoration)
    }

    @Test
    fun `dark gray paper on brighter cloth can pass faint boundary checks`() {
        assertTrue(
            ScannerTuning.isSoftLowTexturePageBoundary(
                areaRatio = 0.38f, signedPaperContrast = -11.0,
                boundaryContrast = 9.0, supportedSides = 2,
                averageEdgeSupport = 0.07, surfaceScore = 0.89f
            )
        )
        assertFalse(
            ScannerTuning.isSoftLowTexturePageBoundary(
                areaRatio = 0.12f, signedPaperContrast = 15.0,
                boundaryContrast = 17.0, supportedSides = 4,
                averageEdgeSupport = 0.25, surfaceScore = 0.08f
            )
        )
    }
}
