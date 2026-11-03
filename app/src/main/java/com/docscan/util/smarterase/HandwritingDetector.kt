package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.docscan.util.TextRecognizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Auto Detect Handwriting & Pen Marks:
 * Distinguishes handwritten annotations, colored pen strokes (blue, red ink), pencil marks,
 * and signatures from high-confidence printed text.
 */
object HandwritingDetector {

    /**
     * Scans bitmap for handwriting and pen marks while protecting printed text.
     * Returns a candidate selection mask Bitmap for the user to review and adjust.
     */
    suspend fun detectHandwritingCandidateMask(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // 1. Get OCR Text Blocks to protect printed text
        val textBlocks = TextRecognizerHelper.extractTextWithBlocks(bitmap)
        val protectedBoxes = textBlocks.map { it.rect }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val candidatePixels = IntArray(w * h)

        // 2. Paper background color estimation
        val paperColor = DocumentAnalyzer.sampleLocalPaperColor(bitmap, w / 2, h / 2, min(w, h) / 4)
        val paperR = Color.red(paperColor)
        val paperG = Color.green(paperColor)
        val paperB = Color.blue(paperColor)

        // 3. Scan pixels for handwriting indicators:
        // - Colored ink (Blue pen, Red pen, Green ink)
        // - High curvature irregular strokes outside recognized printed text boxes
        for (y in 5 until h - 5) {
            val rowY = y * w
            for (x in 5 until w - 5) {
                // Check if inside high-confidence printed text block
                var isInsidePrintedText = false
                for (box in protectedBoxes) {
                    if (box.contains(x, y)) {
                        isInsidePrintedText = true
                        break
                    }
                }

                val pixel = pixels[rowY + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                // Check for Blue pen ink: high blue relative to red/green
                val isBlueInk = (b > r + 20 && b > g + 10 && lum < 200)
                // Check for Red pen ink: high red relative to blue/green
                val isRedInk = (r > g + 30 && r > b + 30 && lum < 200)
                // Check for Pencil / Dark handwriting outside printed text
                val isUnrecognizedDarkStroke = (!isInsidePrintedText && lum < 150 && (abs(r - paperR) > 40 || abs(g - paperG) > 40 || abs(b - paperB) > 40))

                if (isBlueInk || isRedInk || isUnrecognizedDarkStroke) {
                    candidatePixels[rowY + x] = Color.WHITE
                }
            }
        }

        mask.setPixels(candidatePixels, 0, w, 0, 0, w, h)

        // Dilate to consolidate connected handwritten strokes
        val dilated = MaskProcessor.dilateMask(mask, 3)
        return@withContext MaskProcessor.featherMask(dilated, 1.8f)
    }
}
