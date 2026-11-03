package com.docscan.word

/**
 * Text alignment options matching OpenXML / Word formatting standards.
 */
enum class TextAlign {
    LEFT, CENTER, RIGHT, JUSTIFY
}

/**
 * A styled text fragment within a paragraph.
 */
data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSize: Int = 11, // In points (pt)
    val fontColor: String = "000000", // Hex color code
    val highlightColor: String? = null // e.g. "yellow", "cyan"
)

/**
 * Common interface for all document structural elements.
 */
sealed interface DocBlock

/**
 * Paragraph element supporting multi-run styling, alignment, and bulleting.
 */
data class ParagraphBlock(
    val runs: List<TextRun>,
    val alignment: TextAlign = TextAlign.LEFT,
    val isBullet: Boolean = false,
    val isNumbered: Boolean = false,
    val spaceBefore: Int = 0,
    val spaceAfter: Int = 6
) : DocBlock

/**
 * Table element supporting borders, background shading, and borderless layout grids.
 */
data class TableCell(
    val paragraphs: List<ParagraphBlock>,
    val widthPercentage: Float = 0f,
    val backgroundColorHex: String? = null
)

data class TableRow(
    val cells: List<TableCell>,
    val isHeader: Boolean = false
)

data class TableBlock(
    val rows: List<TableRow>,
    val showBorders: Boolean = true,
    val spaceBefore: Int = 4,
    val spaceAfter: Int = 8
) : DocBlock

/**
 * Top-level document container holding metadata and blocks.
 */
data class DocumentStructure(
    val title: String = "Scanned Document",
    val defaultFontFamily: String = "Times New Roman",
    val marginTopPt: Float = 72f,    // 1 inch = 72 points
    val marginBottomPt: Float = 72f,
    val marginLeftPt: Float = 72f,
    val marginRightPt: Float = 72f,
    val blocks: List<DocBlock> = emptyList()
)
