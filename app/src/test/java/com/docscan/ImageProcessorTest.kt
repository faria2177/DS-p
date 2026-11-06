package com.docscan

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.docscan.util.ImageProcessor
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageProcessorTest {

    @Test
    fun `perspective crop preserves source for missing corners`() {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        assertSame(bitmap, ImageProcessor.perspectiveCrop(bitmap, emptyList()))
        assertSame(
            bitmap,
            ImageProcessor.perspectiveCrop(
                bitmap,
                listOf(Offset.Zero, Offset(1f, 0f), Offset(1f, 1f))
            )
        )
    }

    @Test
    fun `perspective crop preserves source for non-finite corners`() {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        val corners = listOf(
            Offset(Float.NaN, 0f),
            Offset(1f, 0f),
            Offset(1f, 1f),
            Offset(0f, 1f)
        )
        assertSame(bitmap, ImageProcessor.perspectiveCrop(bitmap, corners))
    }
}
