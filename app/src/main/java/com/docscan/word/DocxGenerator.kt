package com.docscan.word

import android.content.Context
import android.os.Environment
import com.docscan.util.FileUtils
import com.docscan.util.OfficialDocumentAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Clean, high-performance OpenXML (.docx) Word Document Generator for Android.
 * Fully compatible with Apache POI XWPF specifications without desktop AWT crashes.
 * Converts [DocumentStructure] into standard Microsoft Word (.docx) files.
 */
class DocxGenerator(private val context: Context) {

    /**
     * Generates a .docx file on disk in the app-safe external files directory.
     */
    suspend fun generateDocx(
        documentStructure: DocumentStructure,
        fileName: String = "DocScan_${System.currentTimeMillis()}.docx"
    ): File = withContext(Dispatchers.IO) {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: FileUtils.getPdfsDir(context)
        if (!outputDir.exists()) outputDir.mkdirs()

        val targetFile = File(outputDir, fileName)
        val documentXml = buildDocumentXml(documentStructure)

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // [Content_Types].xml
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())
                
                // _rels/.rels
                writeZipEntry(zos, "_rels/.rels", buildGlobalRelsXml())
                
                // word/_rels/document.xml.rels
                writeZipEntry(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml())
                
                // word/styles.xml
                writeZipEntry(zos, "word/styles.xml", buildStylesXml(documentStructure.defaultFontFamily))
                
                // word/settings.xml
                writeZipEntry(zos, "word/settings.xml", buildSettingsXml())
                
                // word/document.xml
                writeZipEntry(zos, "word/document.xml", documentXml)
            }
        }

        targetFile
    }

    /**
     * Helper to convert raw extracted text or official document layout into a [DocumentStructure].
     */
    fun createStructureFromText(rawText: String, title: String = "Scanned Document"): DocumentStructure {
        val blocks = mutableListOf<DocBlock>()
        val isOfficial = OfficialDocumentAnalyzer.isOfficialLetterOrNotice(rawText)

        if (isOfficial) {
            val letter = OfficialDocumentAnalyzer.parseOfficialLetter(rawText)

            // 1. Dual-Column Letterhead (Borderless Table)
            if (letter.letterheadLeft.isNotEmpty() || letter.letterheadRight.isNotEmpty()) {
                val leftCells = TableCell(
                    paragraphs = letter.letterheadLeft.mapIndexed { i, text ->
                        ParagraphBlock(
                            runs = listOf(TextRun(text = text, bold = true, fontSize = if (i == 0) 15 else 12)),
                            alignment = TextAlign.LEFT,
                            spaceBefore = 0,
                            spaceAfter = 2
                        )
                    }
                )
                val rightCells = TableCell(
                    paragraphs = letter.letterheadRight.mapIndexed { i, text ->
                        ParagraphBlock(
                            runs = listOf(TextRun(text = text, bold = (i == 0), fontSize = if (i == 0) 11 else 10, fontColor = if (i == 0) "000000" else "475569")),
                            alignment = TextAlign.RIGHT,
                            spaceBefore = 0,
                            spaceAfter = 2
                        )
                    }
                )
                blocks.add(
                    TableBlock(
                        rows = listOf(TableRow(cells = listOf(leftCells, rightCells))),
                        showBorders = false,
                        spaceAfter = 8
                    )
                )
            }

            // 2. Reference & Date row (Borderless Table)
            val refText = letter.refText ?: ""
            val dateText = letter.dateText ?: ""
            if (refText.isNotBlank() || dateText.isNotBlank()) {
                val refCell = TableCell(
                    paragraphs = listOf(
                        ParagraphBlock(
                            runs = listOf(TextRun(text = refText, bold = true, fontSize = 11)),
                            alignment = TextAlign.LEFT
                        )
                    )
                )
                val dateCell = TableCell(
                    paragraphs = listOf(
                        ParagraphBlock(
                            runs = listOf(TextRun(text = dateText, bold = true, fontSize = 11)),
                            alignment = TextAlign.RIGHT
                        )
                    )
                )
                blocks.add(
                    TableBlock(
                        rows = listOf(TableRow(cells = listOf(refCell, dateCell))),
                        showBorders = false,
                        spaceBefore = 4,
                        spaceAfter = 8
                    )
                )
            }

            // 3. Recipient Block
            if (letter.recipientLines.isNotEmpty()) {
                letter.recipientLines.forEachIndexed { idx, line ->
                    blocks.add(
                        ParagraphBlock(
                            runs = listOf(TextRun(text = line, bold = (idx == 0 || idx == 1), fontSize = 11)),
                            alignment = TextAlign.LEFT,
                            spaceBefore = 0,
                            spaceAfter = 2
                        )
                    )
                }
            }

            // 4. Subject Line
            val subjectText = letter.subject ?: ""
            if (subjectText.isNotBlank()) {
                blocks.add(
                    ParagraphBlock(
                        runs = listOf(TextRun(text = subjectText, bold = true, underline = true, fontSize = 12)),
                        alignment = TextAlign.LEFT,
                        spaceBefore = 6,
                        spaceAfter = 6
                    )
                )
            }

            // 5. Highlighted bullets
            for (bullet in letter.highlightedBullets) {
                blocks.add(
                    ParagraphBlock(
                        runs = listOf(TextRun(text = bullet, bold = true, highlightColor = "yellow", fontSize = 11)),
                        alignment = TextAlign.LEFT,
                        isBullet = true,
                        spaceBefore = 4,
                        spaceAfter = 4
                    )
                )
            }

            // 6. Body Paragraphs
            for (p in letter.bodyParagraphs) {
                blocks.add(
                    ParagraphBlock(
                        runs = listOf(TextRun(text = p, fontSize = 11)),
                        alignment = TextAlign.JUSTIFY,
                        spaceBefore = 2,
                        spaceAfter = 6
                    )
                )
            }

            // 7. Signoff & Seal Block (Borderless Table)
            if (letter.signoffLines.isNotEmpty() || letter.stampLines.isNotEmpty()) {
                val signoffCell = TableCell(
                    paragraphs = letter.signoffLines.mapIndexed { idx, line ->
                        ParagraphBlock(
                            runs = listOf(TextRun(text = line, bold = (idx > 0 && line.startsWith("(")), fontSize = 11)),
                            alignment = TextAlign.LEFT,
                            spaceBefore = if (idx == 0) 12 else 0,
                            spaceAfter = 2
                        )
                    }
                )
                val stampCell = TableCell(
                    paragraphs = letter.stampLines.map { line ->
                        ParagraphBlock(
                            runs = listOf(TextRun(text = line, bold = true, fontSize = 10, fontColor = "1E293B")),
                            alignment = TextAlign.CENTER,
                            spaceBefore = 0,
                            spaceAfter = 2
                        )
                    },
                    backgroundColorHex = if (letter.stampLines.isNotEmpty()) "F8FAFC" else null
                )
                blocks.add(
                    TableBlock(
                        rows = listOf(TableRow(cells = listOf(signoffCell, stampCell))),
                        showBorders = false,
                        spaceBefore = 12,
                        spaceAfter = 8
                    )
                )
            }
        } else {
            // General multi-line document parsing
            for (line in rawText.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    blocks.add(
                        ParagraphBlock(
                            runs = listOf(TextRun(text = trimmed, fontSize = 11)),
                            alignment = TextAlign.JUSTIFY,
                            spaceBefore = 2,
                            spaceAfter = 4
                        )
                    )
                }
            }
        }

        return DocumentStructure(
            title = title,
            blocks = blocks
        )
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun buildDocumentXml(structure: DocumentStructure): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n")
        sb.append("<w:body>\n")

        for (block in structure.blocks) {
            when (block) {
                is ParagraphBlock -> sb.append(renderParagraphXml(block, structure.defaultFontFamily))
                is TableBlock -> sb.append(renderTableXml(block, structure.defaultFontFamily))
            }
        }

        // Page Margins & Setup (1 pt = 20 dxa)
        val topDxa = (structure.marginTopPt * 20).toInt()
        val bottomDxa = (structure.marginBottomPt * 20).toInt()
        val leftDxa = (structure.marginLeftPt * 20).toInt()
        val rightDxa = (structure.marginRightPt * 20).toInt()

        sb.append("<w:sectPr>\n")
        sb.append("  <w:pgSz w:w=\"11906\" w:h=\"16838\"/>\n") // A4 size in dxa
        sb.append("  <w:pgMar w:top=\"$topDxa\" w:bottom=\"$bottomDxa\" w:left=\"$leftDxa\" w:right=\"$rightDxa\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>\n")
        sb.append("</w:sectPr>\n")
        sb.append("</w:body>\n")
        sb.append("</w:document>")

        return sb.toString()
    }

    private fun renderParagraphXml(block: ParagraphBlock, defaultFont: String): String {
        val sb = StringBuilder()
        sb.append("<w:p>\n")
        sb.append("<w:pPr>\n")

        val jc = when (block.alignment) {
            TextAlign.LEFT -> "left"
            TextAlign.CENTER -> "center"
            TextAlign.RIGHT -> "right"
            TextAlign.JUSTIFY -> "both"
        }
        sb.append("  <w:jc w:val=\"$jc\"/>\n")

        val spaceBeforeDxa = block.spaceBefore * 20
        val spaceAfterDxa = block.spaceAfter * 20
        sb.append("  <w:spacing w:before=\"$spaceBeforeDxa\" w:after=\"$spaceAfterDxa\" w:line=\"276\" w:lineRule=\"auto\"/>\n")
        sb.append("</w:pPr>\n")

        if (block.isBullet) {
            sb.append("<w:r>\n")
            sb.append("<w:rPr><w:rFonts w:ascii=\"$defaultFont\" w:hAnsi=\"$defaultFont\"/><w:sz w:val=\"22\"/><w:b/></w:rPr>\n")
            sb.append("<w:t xml:space=\"preserve\">•  </w:t>\n")
            sb.append("</w:r>\n")
        }

        for (run in block.runs) {
            val halfPt = run.fontSize * 2
            sb.append("<w:r>\n")
            sb.append("<w:rPr>\n")
            sb.append("  <w:rFonts w:ascii=\"$defaultFont\" w:hAnsi=\"$defaultFont\" w:cs=\"Kalpurush\"/>\n")
            sb.append("  <w:sz w:val=\"$halfPt\"/>\n")
            sb.append("  <w:szCs w:val=\"$halfPt\"/>\n")
            if (run.bold) sb.append("  <w:b/><w:bCs/>\n")
            if (run.italic) sb.append("  <w:i/><w:iCs/>\n")
            if (run.underline) sb.append("  <w:u w:val=\"single\"/>\n")
            if (run.fontColor.isNotBlank()) sb.append("  <w:color w:val=\"${run.fontColor}\"/>\n")
            if (!run.highlightColor.isNullOrBlank()) {
                sb.append("  <w:highlight w:val=\"${run.highlightColor}\"/>\n")
            }
            sb.append("</w:rPr>\n")
            sb.append("<w:t xml:space=\"preserve\">${escapeXml(run.text)}</w:t>\n")
            sb.append("</w:r>\n")
        }

        sb.append("</w:p>\n")
        return sb.toString()
    }

    private fun renderTableXml(table: TableBlock, defaultFont: String): String {
        if (table.rows.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("<w:tbl>\n")
        sb.append("<w:tblPr>\n")
        sb.append("  <w:tblW w:w=\"9000\" w:type=\"dxa\"/>\n")
        sb.append("  <w:jc w:val=\"center\"/>\n")

        if (table.showBorders) {
            sb.append("  <w:tblBorders>\n")
            sb.append("    <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>\n")
            sb.append("    <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>\n")
            sb.append("    <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>\n")
            sb.append("    <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>\n")
            sb.append("    <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>\n")
            sb.append("    <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E2E8F0\"/>\n")
            sb.append("  </w:tblBorders>\n")
        } else {
            sb.append("  <w:tblBorders>\n")
            sb.append("    <w:top w:val=\"none\"/>\n")
            sb.append("    <w:left w:val=\"none\"/>\n")
            sb.append("    <w:bottom w:val=\"none\"/>\n")
            sb.append("    <w:right w:val=\"none\"/>\n")
            sb.append("    <w:insideH w:val=\"none\"/>\n")
            sb.append("    <w:insideV w:val=\"none\"/>\n")
            sb.append("  </w:tblBorders>\n")
        }

        sb.append("  <w:tblCellMar>\n")
        sb.append("    <w:top w:w=\"100\" w:type=\"dxa\"/>\n")
        sb.append("    <w:left w:w=\"140\" w:type=\"dxa\"/>\n")
        sb.append("    <w:bottom w:w=\"100\" w:type=\"dxa\"/>\n")
        sb.append("    <w:right w:w=\"140\" w:type=\"dxa\"/>\n")
        sb.append("  </w:tblCellMar>\n")
        sb.append("</w:tblPr>\n")

        for (row in table.rows) {
            sb.append("<w:tr>\n")
            if (row.isHeader) {
                sb.append("<w:trPr><w:tblHeader/></w:trPr>\n")
            }
            for (cell in row.cells) {
                sb.append("<w:tc>\n")
                sb.append("<w:tcPr>\n")
                if (!cell.backgroundColorHex.isNullOrBlank()) {
                    sb.append("  <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${cell.backgroundColorHex}\"/>\n")
                }
                sb.append("</w:tcPr>\n")

                if (cell.paragraphs.isEmpty()) {
                    sb.append("<w:p/>\n")
                } else {
                    for (p in cell.paragraphs) {
                        sb.append(renderParagraphXml(p, defaultFont))
                    }
                }
                sb.append("</w:tc>\n")
            }
            sb.append("</w:tr>\n")
        }

        sb.append("</w:tbl>\n")
        // Spacing after table
        sb.append("<w:p><w:pPr><w:spacing w:after=\"${table.spaceAfter * 20}\"/></w:pPr></w:p>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
        "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
        "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
        "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
        "  <Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>\n" +
        "  <Override PartName=\"/word/settings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\"/>\n" +
        "</Types>"

    private fun buildGlobalRelsXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
        "</Relationships>"

    private fun buildDocumentRelsXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n" +
        "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings\" Target=\"settings.xml\"/>\n" +
        "</Relationships>"

    private fun buildStylesXml(fontFamily: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
        "  <w:docDefaults>\n" +
        "    <w:rPrDefault>\n" +
        "      <w:rPr>\n" +
        "        <w:rFonts w:ascii=\"$fontFamily\" w:hAnsi=\"$fontFamily\" w:cs=\"Kalpurush\"/>\n" +
        "        <w:sz w:val=\"22\"/>\n" +
        "        <w:szCs w:val=\"22\"/>\n" +
        "        <w:color w:val=\"000000\"/>\n" +
        "      </w:rPr>\n" +
        "    </w:rPrDefault>\n" +
        "  </w:docDefaults>\n" +
        "</w:styles>"

    private fun buildSettingsXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
        "  <w:defaultTabStop w:val=\"720\"/>\n" +
        "  <w:characterSpacingControl w:val=\"doNotCompress\"/>\n" +
        "</w:settings>"
}

