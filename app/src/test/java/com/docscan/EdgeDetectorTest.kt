package com.docscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.docscan.util.EdgeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EdgeDetectorTest {

    @Test
    fun `test detectDocumentCorners with synthetic document rectangle`() {
        // Create a 500x500 dark background bitmap with a bright white centered document
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 30, 30))

        val docPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Document placed from (50, 50) to (450, 450) -> normalized ~0.1 to ~0.9
        canvas.drawRect(50f, 50f, 450f, 450f, docPaint)

        val corners = EdgeDetector.detectDocumentCorners(bitmap)
        assertEquals(4, corners.size)

        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        // Check clockwise ordering and reasonable detected bounds
        assertTrue("Top-Left should be near upper left corner", tl.x <= 0.25f && tl.y <= 0.25f)
        assertTrue("Top-Right should be near upper right corner", tr.x >= 0.75f && tr.y <= 0.25f)
        assertTrue("Bottom-Right should be near lower right corner", br.x >= 0.75f && br.y >= 0.75f)
        assertTrue("Bottom-Left should be near lower left corner", bl.x <= 0.25f && bl.y >= 0.75f)
    }

    @Test
    fun `test detectDocumentCorners with blank canvas returns valid default frame`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val corners = EdgeDetector.detectDocumentCorners(bitmap)
        assertNotNull(corners)
        assertEquals(4, corners.size)
    }
    @Test
    fun `full printed form is preferred over its inner table rectangles`() {
        val bitmap = Bitmap.createBitmap(700, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(44, 49, 49))
        val fill = Paint().apply { color = Color.rgb(244, 245, 240); style = Paint.Style.FILL }
        val lines = Paint().apply {
            color = Color.rgb(50, 70, 63)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(92f, 110f, 608f, 890f, fill)
        canvas.drawRect(92f, 110f, 608f, 890f, lines)
        // Form fields are intentionally sharper/darker than the sheet edge.
        canvas.drawRect(185f, 270f, 515f, 690f, lines)
        for (y in 330..660 step 55) canvas.drawLine(185f, y.toFloat(), 515f, y.toFloat(), lines)

        val corners = EdgeDetector.detectDocumentCorners(bitmap)
        val area = EdgeDetector.calculateQuadArea(corners[0], corners[1], corners[2], corners[3])
        assertTrue("Detected page should contain the full printed form, not a table cell", area > 0.34f)
        assertTrue("Top-left should not be inside form fields", corners[0].x < 0.23f && corners[0].y < 0.23f)
    }

}
