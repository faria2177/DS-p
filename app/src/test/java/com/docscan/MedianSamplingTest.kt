package com.docscan

import com.docscan.scanner.BlueEdgeDetector
import org.junit.Assert.assertTrue
import org.junit.Test

class MedianSamplingTest {
    @Test
    fun `odd sized camera frames never exceed the median sampling budget`() {
        val sampleBudget = 100_000
        for ((width, height) in listOf(720 to 711, 1024 to 577, 540 to 720, 720 to 540)) {
            val step = BlueEdgeDetector.medianSampleStep(width, height, sampleBudget)
            val samplesAcross = (width + step - 1) / step
            val samplesDown = (height + step - 1) / step
            assertTrue("Sample budget exceeded for ${width}x${height}", samplesAcross * samplesDown <= sampleBudget)
        }
    }
}
