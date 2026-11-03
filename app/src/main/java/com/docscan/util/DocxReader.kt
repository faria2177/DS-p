package com.docscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Advanced, standalone OpenXML Word (.docx) Document Parser and Reader.
 * Extracts paragraphs, headings, rich text runs, tables, and embedded images
 * from any standard .docx file without requiring external office apps.
 */
object DocxReader {

    data class ParsedDocx(
        val title: String,
        val elements: List<DocxElement>,
        val wordCount: Int,
        val paragraphCount: Int,
        val tableCount: Int,
        val imageCount: Int
    )

    sealed class DocxElement {
        data class Heading(val text: String, val level: Int) : DocxElement()
        data class Paragraph(val runs: List<TextRun>, val alignment: String = "left") : DocxElement()
        data class Table(val headers: List<String>, val rows: List<List<String>>) : DocxElement()
        data class ImageItem(val bitmap: Bitmap, val name: String) : DocxElement()
        data object Divider : DocxElement()
    }

    data class TextRun(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val colorHex: String? = null,
        val fontSizePt: Float = 11f
    )

    /**
     * Reads and parses a DOCX file from an Android File object.
     */
    suspend fun readDocxFile(context: Context, docxFile: File): ParsedDocx = withContext(Dispatchers.IO) {
        try {
            docxFile.inputStream().use { stream ->
                parseDocxStream(stream, docxFile.nameWithoutExtension)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedDocx(
                title = docxFile.nameWithoutExtension,
                elements = listOf(DocxElement.Paragraph(listOf(TextRun("Error reading document: ${e.localizedMessage}")))),
                wordCount = 0,
                paragraphCount = 0,
                tableCount = 0,
                imageCount = 0
            )
        }
    }

    /**
     * Reads and parses a DOCX file from an Android content Uri.
     */
    suspend fun readDocxUri(context: Context, uri: Uri, title: String = "Word Document"): ParsedDocx = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseDocxStream(stream, title)
            } ?: ParsedDocx(title, emptyList(), 0, 0, 0, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedDocx(
                title = title,
                elements = listOf(DocxElement.Paragraph(listOf(TextRun("Error reading document: ${e.localizedMessage}")))),
                wordCount = 0,
                paragraphCount = 0,
                tableCount = 0,
                imageCount = 0
            )
        }
    }

    private fun parseDocxStream(inputStream: InputStream, title: String): ParsedDocx {
        var documentXml: String? = null
        val mediaImages = mutableMapOf<String, Bitmap>()

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "word/document.xml" -> {
                        documentXml = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    name.startsWith("word/media/") -> {
                        val bytes = zis.readBytes()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            val imgName = name.substringAfterLast("/")
                            mediaImages[imgName] = bmp
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (documentXml.isNullOrBlank()) {
            return ParsedDocx(
                title = title,
                elements = listOf(DocxElement.Paragraph(listOf(TextRun("Empty or invalid Word (.docx) document.")))),
                wordCount = 0,
                paragraphCount = 0,
                tableCount = 0,
                imageCount = 0
            )
        }

        return parseDocumentXml(documentXml!!, mediaImages, title)
    }

    private fun parseDocumentXml(
        xmlContent: String,
        mediaImages: Map<String, Bitmap>,
        title: String
    ): ParsedDocx {
        val elements = mutableListOf<DocxElement>()
        var totalWords = 0
        var paragraphCount = 0
        var tableCount = 0

        // Add embedded images at the top if any
        mediaImages.values.forEach { bmp ->
            elements.add(DocxElement.ImageItem(bmp, "Embedded Image"))
        }

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var inParagraph = false
            var inTable = false
            var inTableRow = false
            var inTableCell = false
            var inRun = false
            var inRunProps = false

            var isBold = false
            var isItalic = false
            var isUnderline = false
            var runColor: String? = null
            var runFontSize = 11f
            var pAlignment = "left"
            var isHeading = 0

            val currentRuns = mutableListOf<TextRun>()
            val currentTableHeaders = mutableListOf<String>()
            val currentTableRows = mutableListOf<MutableList<String>>()
            var currentRowCells = mutableListOf<String>()
            var currentCellText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                tableCount++
                                currentTableHeaders.clear()
                                currentTableRows.clear()
                            }
                            "tr" -> {
                                inTableRow = true
                                currentRowCells = mutableListOf()
                            }
                            "tc" -> {
                                inTableCell = true
                                currentCellText = StringBuilder()
                            }
                            "p" -> {
                                inParagraph = true
                                currentRuns.clear()
                                pAlignment = "left"
                                isHeading = 0
                            }
                            "pStyle" -> {
                                val styleVal = parser.getAttributeValue(null, "val") ?: ""
                                if (styleVal.contains("Heading1", true) || styleVal == "1") isHeading = 1
                                else if (styleVal.contains("Heading2", true) || styleVal == "2") isHeading = 2
                                else if (styleVal.contains("Heading3", true) || styleVal == "3") isHeading = 3
                            }
                            "jc" -> {
                                val alignVal = parser.getAttributeValue(null, "val") ?: "left"
                                pAlignment = alignVal
                            }
                            "r" -> {
                                inRun = true
                                isBold = false
                                isItalic = false
                                isUnderline = false
                                runColor = null
                                runFontSize = 11f
                            }
                            "rPr" -> inRunProps = true
                            "b" -> if (inRun) isBold = true
                            "i" -> if (inRun) isItalic = true
                            "u" -> if (inRun) isUnderline = true
                            "color" -> if (inRun) runColor = parser.getAttributeValue(null, "val")
                            "sz" -> {
                                val szVal = parser.getAttributeValue(null, "val")?.toIntOrNull()
                                if (szVal != null) runFontSize = (szVal / 2f).coerceIn(8f, 36f)
                            }
                            "t" -> {
                                val text = parser.nextText()
                                if (text.isNotEmpty()) {
                                    if (inTableCell) {
                                        currentCellText.append(text).append(" ")
                                    } else if (inParagraph) {
                                        currentRuns.add(
                                            TextRun(
                                                text = text,
                                                isBold = isBold,
                                                isItalic = isItalic,
                                                isUnderline = isUnderline,
                                                colorHex = runColor,
                                                fontSizePt = runFontSize
                                            )
                                        )
                                        totalWords += text.split(Regex("\\s+")).count { it.isNotBlank() }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "r" -> inRun = false
                            "p" -> {
                                inParagraph = false
                                if (!inTableCell && currentRuns.isNotEmpty()) {
                                    val fullText = currentRuns.joinToString("") { it.text }.trim()
                                    if (fullText.isNotEmpty()) {
                                        paragraphCount++
                                        if (isHeading > 0 || fullText.startsWith("#")) {
                                            elements.add(DocxElement.Heading(fullText.removePrefix("#").trim(), if (isHeading > 0) isHeading else 1))
                                        } else {
                                            elements.add(DocxElement.Paragraph(currentRuns.toList(), pAlignment))
                                        }
                                    }
                                }
                            }
                            "tc" -> {
                                inTableCell = false
                                currentRowCells.add(currentCellText.toString().trim())
                            }
                            "tr" -> {
                                inTableRow = false
                                if (currentRowCells.isNotEmpty()) {
                                    if (currentTableHeaders.isEmpty() && currentTableRows.isEmpty()) {
                                        currentTableHeaders.addAll(currentRowCells)
                                    } else {
                                        currentTableRows.add(currentRowCells)
                                    }
                                }
                            }
                            "tbl" -> {
                                inTable = false
                                val headers = if (currentTableHeaders.isNotEmpty()) currentTableHeaders.toList() else listOf("Col 1")
                                elements.add(DocxElement.Table(headers, currentTableRows.map { it.toList() }))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback plain regex extractor if xml pull parser encounters complex nested namespaces
            val plainText = xmlContent.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            if (plainText.isNotEmpty() && elements.isEmpty()) {
                elements.add(DocxElement.Paragraph(listOf(TextRun(plainText))))
            }
        }

        return ParsedDocx(
            title = title,
            elements = elements,
            wordCount = totalWords,
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = mediaImages.size
        )
    }
}
