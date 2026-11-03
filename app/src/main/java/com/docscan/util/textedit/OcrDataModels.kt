package com.docscan.util.textedit

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Granularity level for text detection and interactive selection.
 */
enum class TextEditGranularity(val label: String) {
    BLOCK("Paragraph / Block"),
    LINE("Line by Line"),
    WORD("Individual Word")
}

/**
 * Font style estimations and user customization settings.
 */
data class FontStyleEstimate(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontFamilyType: String = "DEFAULT" // "DEFAULT", "SERIF", "MONO"
)

/**
 * Individual recognized word / element in OCR hierarchy.
 */
data class OcrTextElement(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pixelRect: Rect,
    val normalizedRect: RectF,
    val confidence: Float? = null
)

/**
 * Recognized text line containing words.
 */
data class OcrTextLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pixelRect: Rect,
    val normalizedRect: RectF,
    val elements: List<OcrTextElement> = emptyList(),
    val confidence: Float? = null,
    val rotationAngle: Float = 0f,
    val estimatedTextSizeSp: Float = 16f,
    val estimatedTextColor: Color = Color.Black,
    val estimatedBackgroundColor: Color = Color.White,
    val estimatedFontStyle: FontStyleEstimate = FontStyleEstimate()
)

/**
 * Recognized text block containing multiple lines.
 */
data class OcrTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pixelRect: Rect,
    val normalizedRect: RectF,
    val lines: List<OcrTextLine> = emptyList(),
    val rotationAngle: Float = 0f,
    val estimatedTextSizeSp: Float = 16f,
    val estimatedTextColor: Color = Color.Black,
    val estimatedBackgroundColor: Color = Color.White,
    val estimatedFontStyle: FontStyleEstimate = FontStyleEstimate()
)

/**
 * Complete OCR Document result hierarchy relative to the original source image.
 */
data class OcrDocument(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val imageWidth: Int,
    val imageHeight: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Flattened list of all lines across blocks.
     */
    val allLines: List<OcrTextLine> by lazy {
        blocks.flatMap { it.lines }
    }

    /**
     * Flattened list of all words across all lines.
     */
    val allWords: List<OcrTextElement> by lazy {
        blocks.flatMap { it.lines }.flatMap { it.elements }
    }
}

/**
 * Non-destructive text editing operation representing an edited or erased text region.
 */
data class EditTextOperation(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String, // References OcrTextBlock.id, OcrTextLine.id, or OcrTextElement.id
    val originalText: String,
    val newText: String,
    val originalNormalizedRect: RectF,
    val targetNormalizedRect: RectF = originalNormalizedRect,
    val textColor: Color = Color.Black,
    val fontSizeSp: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontFamilyType: String = "DEFAULT", // "DEFAULT", "SERIF", "MONO"
    val alignment: String = "LEFT", // "LEFT", "CENTER", "RIGHT"
    val backgroundColor: Color = Color.Transparent,
    val autoInpaintBackground: Boolean = true,
    val isDeleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
