package com.docscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.docscan.util.textedit.DocumentCoordinateTransformer
import com.docscan.util.textedit.DocumentTextInpainter
import com.docscan.util.textedit.DocumentTextRenderer
import com.docscan.util.textedit.EditTextOperation
import com.docscan.util.textedit.OcrDocument
import com.docscan.util.textedit.OcrTextBlock
import com.docscan.util.textedit.OcrTextElement
import com.docscan.util.textedit.OcrTextLine
import com.docscan.util.textedit.TextEditGranularity
import com.docscan.util.textedit.TextStyleEstimator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DocumentTextEditorTest {

    @Test
    fun testCoordinateTransformation() {
        val canvasSize = Size(1000f, 1000f)
        val imgWidth = 800
        val imgHeight = 600

        // Test frame calculation
        val frame = DocumentCoordinateTransformer.calculateImageFrame(canvasSize, imgWidth, imgHeight)
        assertEquals(0f, frame.left, 0.01f)
        assertEquals(1000f, frame.right, 0.01f)
        assertTrue(frame.top > 0f)

        // Test screen touch mapping
        val touch = Offset(500f, 500f)
        val normalized = DocumentCoordinateTransformer.screenTouchToNormalized(touch, canvasSize, imgWidth, imgHeight)
        assertNotNull(normalized)
        assertEquals(0.5f, normalized!!.x, 0.05f)
        assertEquals(0.5f, normalized!!.y, 0.05f)
    }

    @Test
    fun testHitTestingHierarchy() {
        val word1 = OcrTextElement(
            text = "Hello",
            pixelRect = Rect(100, 100, 200, 150),
            normalizedRect = RectF(0.1f, 0.1f, 0.2f, 0.15f)
        )
        val word2 = OcrTextElement(
            text = "World",
            pixelRect = Rect(220, 100, 320, 150),
            normalizedRect = RectF(0.22f, 0.1f, 0.32f, 0.15f)
        )
        val line = OcrTextLine(
            text = "Hello World",
            pixelRect = Rect(100, 100, 320, 150),
            normalizedRect = RectF(0.1f, 0.1f, 0.32f, 0.15f),
            elements = listOf(word1, word2)
        )
        val block = OcrTextBlock(
            text = "Hello World",
            pixelRect = Rect(100, 100, 320, 150),
            normalizedRect = RectF(0.1f, 0.1f, 0.32f, 0.15f),
            lines = listOf(line)
        )
        val doc = OcrDocument(
            fullText = "Hello World",
            blocks = listOf(block),
            imageWidth = 1000,
            imageHeight = 1000
        )

        // Test word hit
        val hitWord = DocumentCoordinateTransformer.findHitTextItem(
            normTouch = Offset(0.15f, 0.12f),
            ocrDoc = doc,
            granularity = TextEditGranularity.WORD
        )
        assertTrue(hitWord is OcrTextElement)
        assertEquals("Hello", (hitWord as OcrTextElement).text)

        // Test line hit
        val hitLine = DocumentCoordinateTransformer.findHitTextItem(
            normTouch = Offset(0.25f, 0.12f),
            ocrDoc = doc,
            granularity = TextEditGranularity.LINE
        )
        assertTrue(hitLine is OcrTextLine)
        assertEquals("Hello World", (hitLine as OcrTextLine).text)
    }

    @Test
    fun testTextInpaintingAndRendering() = runBlocking {
        // Create test document bitmap with white paper and black text
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 24f
        }
        canvas.drawText("Invoice Total: $500", 50f, 100f, paint)

        val textRect = RectF(0.1f, 0.2f, 0.8f, 0.3f)
        val inpainted = DocumentTextInpainter.inpaintRegion(bitmap, textRect)
        assertNotNull(inpainted)
        assertEquals(400, inpainted.width)
        assertEquals(400, inpainted.height)

        // Test rendering replacement operation
        val op = EditTextOperation(
            targetId = "target-1",
            originalText = "Invoice Total: $500",
            newText = "Invoice Total: $250",
            originalNormalizedRect = textRect,
            targetNormalizedRect = textRect,
            textColor = Color.Black,
            fontSizeSp = 18f,
            isBold = true
        )

        val renderCanvas = Canvas(inpainted)
        DocumentTextRenderer.renderOperationsOnCanvas(renderCanvas, listOf(op), 400f, 400f)
        assertNotNull(inpainted)
    }

    @Test
    fun testStyleEstimation() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint().apply {
            color = AndroidColor.rgb(20, 30, 40)
            textSize = 20f
        }
        canvas.drawText("Sample Title", 20f, 50f, paint)

        val estimatedStyle = TextStyleEstimator.estimate(bitmap, Rect(10, 20, 180, 80))
        assertNotNull(estimatedStyle.textColor)
        assertNotNull(estimatedStyle.backgroundColor)
    }
}
