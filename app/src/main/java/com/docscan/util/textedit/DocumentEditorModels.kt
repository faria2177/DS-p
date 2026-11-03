package com.docscan.util.textedit

import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import java.util.UUID

enum class EditorElementType {
    TEXT,
    PHOTO,
    QR_BARCODE,
    IMAGE,
    MRZ
}

data class EditorTextElement(
    val id: String,
    val pageIndex: Int,
    val text: String,
    val normalizedRect: RectF,
    val confidence: Float? = null,
    val rotation: Float = 0f,
    val blockIndex: Int = -1,
    val lineIndex: Int = -1,
    val pageWidth: Int,
    val pageHeight: Int,
    val fontSizeSp: Float = 16f,
    val textColor: Color = Color.Black,
    val backgroundColor: Color = Color.White,
    val fontStyle: FontStyleEstimate = FontStyleEstimate(),
    val alignment: String = "LEFT",
    val protectedFactualField: Boolean = false
)

data class EditorImageElement(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: EditorElementType,
    val normalizedRect: RectF,
    val label: String,
    val protected: Boolean = false
)

data class DocumentEditorPageState(
    val page: PageEntity,
    val pageIndex: Int,
    val sourcePath: String,
    val displayWidth: Int = 1,
    val displayHeight: Int = 1,
    val orientationDegrees: Int = 0,
    val ocrDocument: OcrDocument? = null,
    val imageElements: List<EditorImageElement> = emptyList(),
    val isAnalyzing: Boolean = false,
    val analysisComplete: Boolean = false,
    val analysisError: String? = null
)

data class DocumentEditorState(
    val documentId: Long,
    val document: DocumentEntity? = null,
    val pages: List<DocumentEditorPageState> = emptyList(),
    val selectedElementId: String? = null,
    val selectedPageId: Long? = null,
    val hasUnsavedChanges: Boolean = false
)

data class DocumentEditorSnapshot(
    val pageOperations: Map<Long, List<EditTextOperation>>
)

fun OcrDocument.toEditorTextElements(pageIndex: Int): List<EditorTextElement> {
    val output = mutableListOf<EditorTextElement>()

    blocks.forEachIndexed { blockIndex, block ->
        block.lines.forEachIndexed { lineIndex, line ->
            val alignment = inferAlignment(line.normalizedRect)
            if (line.elements.isNotEmpty()) {
                line.elements.forEach { word ->
                    output += EditorTextElement(
                        id = word.id,
                        pageIndex = pageIndex,
                        text = word.text,
                        normalizedRect = RectF(word.normalizedRect),
                        confidence = word.confidence,
                        rotation = line.rotationAngle,
                        blockIndex = blockIndex,
                        lineIndex = lineIndex,
                        pageWidth = imageWidth,
                        pageHeight = imageHeight,
                        fontSizeSp = line.estimatedTextSizeSp,
                        textColor = line.estimatedTextColor,
                        backgroundColor = line.estimatedBackgroundColor,
                        fontStyle = line.estimatedFontStyle,
                        alignment = alignment,
                        protectedFactualField = isProtectedDocumentText(word.text)
                    )
                }
            } else {
                output += EditorTextElement(
                    id = line.id,
                    pageIndex = pageIndex,
                    text = line.text,
                    normalizedRect = RectF(line.normalizedRect),
                    confidence = line.confidence,
                    rotation = line.rotationAngle,
                    blockIndex = blockIndex,
                    lineIndex = lineIndex,
                    pageWidth = imageWidth,
                    pageHeight = imageHeight,
                    fontSizeSp = line.estimatedTextSizeSp,
                    textColor = line.estimatedTextColor,
                    backgroundColor = line.estimatedBackgroundColor,
                    fontStyle = line.estimatedFontStyle,
                    alignment = alignment,
                    protectedFactualField = isProtectedDocumentText(line.text)
                )
            }
        }
    }

    return output
}

fun isProtectedDocumentText(text: String): Boolean {
    val value = text.trim()
    if (value.isBlank()) return false
    val lower = value.lowercase()
    val structuredKeywords = listOf(
        "nid", "passport", "birth", "date of birth", "dob", "phone", "mobile",
        "account", "invoice", "ticket", "pnr", "mrz", "id no", "number",
        "নাম", "জাতীয়", "পরিচয়", "জন্ম", "মোবাইল", "ঠিকানা", "পাসপোর্ট"
    )
    if (structuredKeywords.any { lower.contains(it) }) return true
    if (value.contains("<<<") || value.startsWith("P<") || value.startsWith("I<")) return true

    val compact = value.filterNot { it.isWhitespace() }
    if (compact.length >= 5) {
        val digitRatio = compact.count { it.isDigit() }.toFloat() / compact.length.toFloat()
        if (digitRatio >= 0.45f) return true
        val identifierRatio = compact.count { it.isLetterOrDigit() || it in "-_/<>" }.toFloat() / compact.length.toFloat()
        if (identifierRatio > 0.9f && compact.any { it.isDigit() } && compact.any { it.isLetter() }) return true
    }
    return false
}

private fun inferAlignment(rect: RectF): String {
    val center = (rect.left + rect.right) / 2f
    return when {
        kotlin.math.abs(center - 0.5f) < 0.05f && rect.width() < 0.8f -> "CENTER"
        rect.left > 0.55f -> "RIGHT"
        else -> "LEFT"
    }
}

fun RectF.area(): Float = width().coerceAtLeast(0f) * height().coerceAtLeast(0f)
