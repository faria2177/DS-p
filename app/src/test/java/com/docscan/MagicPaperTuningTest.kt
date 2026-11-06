package com.docscan

import com.docscan.scanner.MagicPaperTuning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagicPaperTuningTest {
    @Test
    fun `warm notebook paper is neutralized but colored seals survive`() {
        assertTrue(MagicPaperTuning.isPaperLike(y = 239, cr = 133, cb = 87))
        assertTrue(MagicPaperTuning.isPaperLike(y = 210, cr = 128, cb = 128))
        assertFalse("Dark lettering must be left intact", MagicPaperTuning.isPaperLike(52, 130, 122))
        assertFalse("Red seals must retain their ink", MagicPaperTuning.isPaperLike(197, 205, 91))
        assertFalse("Blue logos must retain their ink", MagicPaperTuning.isPaperLike(194, 109, 202))
    }

    @Test
    fun `magic tone makes paper white without deleting faint strokes`() {
        var previous = -1
        for (lum in 0..255) {
            val adjusted = MagicPaperTuning.magicTone(lum)
            assertTrue("Tone curve must stay monotonic at $lum", adjusted >= previous)
            previous = adjusted
        }
        assertTrue("White background should approach white", MagicPaperTuning.magicTone(240) >= 250)
        assertTrue("Black printed line should remain dark", MagicPaperTuning.magicTone(80) < 25)
        assertTrue("Midtone pencil marks should still be visible", MagicPaperTuning.magicTone(185) in 80..135)
    }
}
