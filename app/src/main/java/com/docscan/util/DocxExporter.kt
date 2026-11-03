package com.docscan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.core.content.FileProvider
import com.docscan.data.model.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Professional OpenXML (.docx) Word Document Generator & Exporter.
 * Generates genuine Microsoft Word documents (.docx) with:
 * - High-accuracy document structure parsing (Headings, Paragraphs, Lists, Key-Value pairs, Tables)
 * - Seamless mixed document support (prose, headings, and tables coexisting in exact reading order)
 * - Inline markdown styling (bold, italic, code) converted to native Word character runs (<w:r>)
 * - Structured tables with styled header rows, zebra row striping, borders, and cell margins
 * - Preserved document layouts, aspect ratios, and section margins
 * - Embedded high-resolution images with exact aspect ratio scaling
 * - Full UTF-8 support for Bengali (বাংলা), English, and multilingual scripts
 * - Sanitized OpenXML compliance to prevent XML corruptions or parsing errors
 */
object DocxExporter {

    data class DocxExportConfig(
        val title: String = "Document",
        val layoutMode: String = "Exact Layout",      // "Exact Layout", "Editable Layout", "Text Focused"
        val ocrLanguage: String = "Auto Detect",      // "Auto Detect", "Bengali", "English"
        val imageHandling: String = "Preserve Images",// "Preserve Images", "Extract Images", "Ignore Images"
        val tableHandling: String = "Detect & Rebuild",// "Detect & Rebuild", "Preserve as Image", "Raw Text"
        val quality: String = "High",                 // "Standard", "High", "Maximum"
        val embedPageImages: Boolean = true,
        val includeExtractedTables: Boolean = true,
        val includeFormattedText: Boolean = true,
        val preservePageSize: Boolean = true,
        val preserveMargins: Boolean = true,
        val includePageNumbers: Boolean = true,
        val fontFamily: String = "Segoe UI"
    )

    private data class EmbeddedImageInfo(
        val pageIndex: Int,
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val role: String = "page"
    ) {
        val relId: String
            get() = when (role) {
                "rail_logo" -> "rIdRailLogo_${pageIndex + 1}"
                "rail_qr" -> "rIdRailQr_${pageIndex + 1}"
                else -> "rIdImg_${pageIndex + 1}"
            }
        val fileName: String
            get() = when (role) {
                "rail_logo" -> "rail_logo_p${pageIndex + 1}.jpeg"
                "rail_qr" -> "rail_qr_p${pageIndex + 1}.jpeg"
                else -> "image_p${pageIndex + 1}.jpeg"
            }
    }

    /**
     * Generates a genuine .docx file from a list of PageEntity objects.
     */
    suspend fun generateDocx(
        context: Context,
        documentTitle: String,
        pages: List<PageEntity>,
        config: DocxExportConfig = DocxExportConfig(title = documentTitle)
    ): File? = withContext(Dispatchers.IO) {
        try {
            val docsDir = FileUtils.getPdfsDir(context)
            val sanitizedTitle = documentTitle.trim()
                .replace(Regex("[^a-zA-Z0-9_\\-\\s\u0980-\u09FF]"), "_")
                .replace(Regex("\\s+"), "_")
                .ifBlank { "Word_Document" }
            
            // Ensure unique filename
            var candidateFile = File(docsDir, "${sanitizedTitle}.docx")
            var counter = 1
            while (candidateFile.exists()) {
                candidateFile = File(docsDir, "${sanitizedTitle}_($counter).docx")
                counter++
            }
            val docxFile = candidateFile

            // Collect valid page images for embedding if enabled
            val shouldEmbedImages = config.imageHandling != "Ignore Images" && (config.embedPageImages || config.layoutMode == "Exact Layout")
            val embeddedImages = mutableListOf<EmbeddedImageInfo>()

            if (shouldEmbedImages) {
                val compressionQuality = when (config.quality) {
                    "Maximum" -> 92
                    "Standard" -> 75
                    else -> 85 // High
                }
                pages.forEachIndexed { index, page ->
                    val imgFile = File(page.processedImagePath).takeIf { it.exists() }
                        ?: File(page.originalImagePath).takeIf { it.exists() }
                    if (imgFile != null) {
                        try {
                            val imgInfo = compressImageWithDimensions(imgFile, compressionQuality)
                            if (imgInfo != null && imgInfo.bytes.isNotEmpty()) {
                                embeddedImages.add(
                                    EmbeddedImageInfo(
                                        pageIndex = index,
                                        bytes = imgInfo.bytes,
                                        width = imgInfo.width,
                                        height = imgInfo.height,
                                        role = "page"
                                    )
                                )
                            }

                            // For Bangladesh Railway style tickets preserve the non-text header artwork
                            // (seal/logo and Shohoz/QR block) as two small images while rebuilding the
                            // rest of the page as native editable Word text/tables.
                            if (WordLayoutMarkup.isBangladeshRailwayTicket(page.extractedText)) {
                                cropRelativeImage(imgFile, 0.06f, 0.045f, 0.17f, 0.125f, compressionQuality)?.let { crop ->
                                    embeddedImages.add(
                                        EmbeddedImageInfo(index, crop.bytes, crop.width, crop.height, "rail_logo")
                                    )
                                }
                                cropRelativeImage(imgFile, 0.79f, 0.04f, 0.95f, 0.205f, compressionQuality)?.let { crop ->
                                    embeddedImages.add(
                                        EmbeddedImageInfo(index, crop.bytes, crop.width, crop.height, "rail_qr")
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            val pageImages = embeddedImages.filter { it.role == "page" }
            val railLogoImages = embeddedImages.filter { it.role == "rail_logo" }
            val railQrImages = embeddedImages.filter { it.role == "rail_qr" }

            val imageRelMap = pageImages.associate { it.pageIndex to it.relId }
            val imageDimMap = pageImages.associate { it.pageIndex to Pair(it.width, it.height) }
            val railLogoRelMap = railLogoImages.associate { it.pageIndex to it.relId }
            val railLogoDimMap = railLogoImages.associate { it.pageIndex to Pair(it.width, it.height) }
            val railQrRelMap = railQrImages.associate { it.pageIndex to it.relId }
            val railQrDimMap = railQrImages.associate { it.pageIndex to Pair(it.width, it.height) }

            val docXmlContent = buildDocumentXml(
                documentTitle, pages, config,
                imageRelMap, imageDimMap,
                railLogoRelMap, railLogoDimMap,
                railQrRelMap, railQrDimMap
            )
            val contentTypesXml = buildContentTypesXml(embeddedImages.isNotEmpty())
            val relsXml = buildRelsXml()
            val docRelsXml = buildDocumentRelsXml(embeddedImages)
            val stylesXml = buildStylesXml(config.fontFamily)
            val corePropsXml = buildCorePropsXml(documentTitle)
            val appPropsXml = buildAppPropsXml(pages.size)

            FileOutputStream(docxFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. [Content_Types].xml
                    writeZipEntry(zos, "[Content_Types].xml", contentTypesXml)

                    // 2. _rels/.rels
                    writeZipEntry(zos, "_rels/.rels", relsXml)

                    // 3. docProps/core.xml
                    writeZipEntry(zos, "docProps/core.xml", corePropsXml)

                    // 4. docProps/app.xml
                    writeZipEntry(zos, "docProps/app.xml", appPropsXml)

                    // 5. word/_rels/document.xml.rels
                    writeZipEntry(zos, "word/_rels/document.xml.rels", docRelsXml)

                    // 6. word/styles.xml
                    writeZipEntry(zos, "word/styles.xml", stylesXml)

                    // 7. Embedded page/header images
                    embeddedImages.forEach { item ->
                        writeZipBytes(zos, "word/media/${item.fileName}", item.bytes)
                    }

                    // 8. word/document.xml
                    writeZipEntry(zos, "word/document.xml", docXmlContent)
                }
            }

            if (docxFile.exists() && docxFile.length() > 0) {
                docxFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class CompressedImageResult(
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )

    private fun compressImageWithDimensions(file: File, quality: Int = 85): CompressedImageResult? {
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val stream = ByteArrayOutputStream()
            val maxDim = 2048
            val (scaledBmp, outW, outH) = if (bmp.width > maxDim || bmp.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
                val targetW = (bmp.width * ratio).toInt()
                val targetH = (bmp.height * ratio).toInt()
                val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                Triple(scaled, targetW, targetH)
            } else {
                Triple(bmp, bmp.width, bmp.height)
            }
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            CompressedImageResult(stream.toByteArray(), outW, outH)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cropRelativeImage(
        file: File,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        quality: Int = 90
    ): CompressedImageResult? {
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val x = (bmp.width * left).toInt().coerceIn(0, bmp.width - 1)
            val y = (bmp.height * top).toInt().coerceIn(0, bmp.height - 1)
            val r = (bmp.width * right).toInt().coerceIn(x + 1, bmp.width)
            val b = (bmp.height * bottom).toInt().coerceIn(y + 1, bmp.height)
            val crop = Bitmap.createBitmap(bmp, x, y, r - x, b - y)
            val stream = ByteArrayOutputStream()
            crop.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            CompressedImageResult(stream.toByteArray(), crop.width, crop.height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun writeZipBytes(zos: ZipOutputStream, entryName: String, bytes: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    /**
     * Sanitizes strings for XML 1.0 compliance, escaping entities and removing illegal control codes.
     */
    fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (i in 0 until text.length) {
            val ch = text[i]
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\t', '\n', '\r' -> sb.append(ch)
                else -> {
                    // Filter out illegal XML 1.0 control characters
                    if (ch.code >= 0x20 && ch.code != 0xFFFF && ch.code != 0xFFFE) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun buildContentTypesXml(hasImages: Boolean): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
""")
        if (hasImages) {
            sb.append("""  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
""")
        }
        sb.append("""  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>""")
        return sb.toString()
    }

    private fun buildRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""
    }

    private fun buildCorePropsXml(title: String): String {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                   xmlns:dc="http://purl.org/dc/elements/1.1/"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${escapeXml(title)}</dc:title>
  <dc:creator>CamScanner AI Studio</dc:creator>
  <cp:lastModifiedBy>CamScanner AI Studio</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$nowIso</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$nowIso</dcterms:modified>
</cp:coreProperties>"""
    }

    private fun buildAppPropsXml(pageCount: Int): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
            xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>CamScanner AI Studio</Application>
  <DocSecurity>0</DocSecurity>
  <Lines>1</Lines>
  <Paragraphs>1</Paragraphs>
  <Pages>$pageCount</Pages>
  <Company>AI Studio</Company>
  <AppVersion>16.0000</AppVersion>
</Properties>"""
    }

    private fun buildDocumentRelsXml(images: List<EmbeddedImageInfo>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
""")
        images.forEach { item ->
            sb.append("""  <Relationship Id="${item.relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${item.fileName}"/>
""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildStylesXml(fontFamily: String): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="$fontFamily" w:hAnsi="$fontFamily" w:eastAsia="$fontFamily" w:cs="Kalpurush, SolaimanLipi, Vrinda, $fontFamily"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
        <w:color w:val="000000"/>
        <w:lang w:val="en-US" w:bidi="bn-BD"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="34"/>
      <w:color w:val="000000"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="26"/>
      <w:color w:val="111827"/>
    </w:rPr>
  </w:style>
</w:styles>"""
    }

    /**
     * Builds OpenXML <w:r> runs from text containing inline markdown (e.g. **bold**, *italic*, ==highlight==).
     */
    fun buildInlineRunsXml(
        text: String,
        font: String,
        defaultColor: String = "000000",
        defaultSize: String = "22",
        baseBold: Boolean = false,
        baseItalic: Boolean = false,
        baseHighlight: Boolean = false
    ): String {
        if (text.isEmpty()) return ""

        val sb = StringBuilder()

        // 1. Split by ==highlight== markers
        val highlightSegments = text.split(Regex("(?<===)|(?===)"))
        var inHighlight = false

        for (hSeg in highlightSegments) {
            if (hSeg == "==") {
                inHighlight = !inHighlight
                continue
            }
            if (hSeg.isEmpty()) continue

            // 2. Split by markdown bold markers `**` or `__`
            val boldSegments = hSeg.split(Regex("(?<=\\*\\*)|(?=\\*\\*)|(?<=__)|(?=__)"))
            var inBold = false

            for (seg in boldSegments) {
                if (seg == "**" || seg == "__") {
                    inBold = !inBold
                    continue
                }
                if (seg.isEmpty()) continue

                // 3. Split by markdown italic markers `*` or `_` (single)
                val italicSegments = seg.split(Regex("(?<=[^*_][*_])|(?=[*_][^*_])"))
                var inItalic = false

                for (subSeg in italicSegments) {
                    var cleanSubSeg = subSeg
                    var isItalicRun = inItalic

                    if (cleanSubSeg.startsWith("*") && cleanSubSeg.endsWith("*") && cleanSubSeg.length > 2) {
                        cleanSubSeg = cleanSubSeg.substring(1, cleanSubSeg.length - 1)
                        isItalicRun = true
                    } else if (cleanSubSeg.startsWith("_") && cleanSubSeg.endsWith("_") && cleanSubSeg.length > 2) {
                        cleanSubSeg = cleanSubSeg.substring(1, cleanSubSeg.length - 1)
                        isItalicRun = true
                    }

                    if (cleanSubSeg.isEmpty()) continue

                    val isRunBold = baseBold || inBold
                    val isRunItalic = baseItalic || isItalicRun
                    val isRunHighlight = baseHighlight || inHighlight

                    sb.append("      <w:r>\n")
                    sb.append("        <w:rPr>\n")
                    sb.append("          <w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:eastAsia=\"$font\" w:cs=\"Kalpurush, SolaimanLipi, Vrinda, $font\"/>\n")
                    if (isRunBold) sb.append("          <w:b/>\n")
                    if (isRunItalic) sb.append("          <w:i/>\n")
                    if (isRunHighlight) sb.append("          <w:highlight w:val=\"yellow\"/>\n")
                    sb.append("          <w:sz w:val=\"$defaultSize\"/>\n")
                    sb.append("          <w:szCs w:val=\"$defaultSize\"/>\n")
                    sb.append("          <w:color w:val=\"$defaultColor\"/>\n")
                    sb.append("        </w:rPr>\n")
                    sb.append("        <w:t xml:space=\"preserve\">${escapeXml(cleanSubSeg)}</w:t>\n")
                    sb.append("      </w:r>\n")
                }
            }
        }

        return sb.toString()
    }

    sealed class ParsedDocElement {
        data class Letterhead(val leftLines: List<String>, val rightLines: List<String>) : ParsedDocElement()
        data class RefAndDate(val ref: String, val date: String) : ParsedDocElement()
        data class RecipientBlock(val lines: List<String>) : ParsedDocElement()
        data class LetterSubject(val text: String) : ParsedDocElement()
        data class HighlightedBullet(val text: String, val bulletChar: String = "•") : ParsedDocElement()
        data class SignoffBlock(val signoffLines: List<String>, val stampLines: List<String>) : ParsedDocElement()
        data class Title(val text: String) : ParsedDocElement()
        data class Heading1(val text: String) : ParsedDocElement()
        data class Heading2(val text: String) : ParsedDocElement()
        data class Heading3(val text: String) : ParsedDocElement()
        data class KeyValue(val key: String, val value: String) : ParsedDocElement()
        data class BulletItem(val text: String, val level: Int = 0) : ParsedDocElement()
        data class NumberedItem(val number: String, val text: String) : ParsedDocElement()
        data class Quote(val text: String) : ParsedDocElement()
        object Divider : ParsedDocElement()
        data class Table(val headers: List<String>, val rows: List<List<String>>) : ParsedDocElement()
        data class CenteredTitle(val lines: List<String>) : ParsedDocElement()
        data class SectionBand(val text: String) : ParsedDocElement()
        data class KeyValueTable(val rows: List<List<String>>) : ParsedDocElement()
        data class TwoColumnBlock(val leftLines: List<String>, val rightLines: List<String>, val footer: Boolean = false) : ParsedDocElement()
        data class Banner(val text: String, val kind: String = "green") : ParsedDocElement()
        data class Paragraph(val text: String) : ParsedDocElement()
    }

    private fun parseLayoutMarkupIntoElements(rawText: String): List<ParsedDocElement>? {
        if (!rawText.contains(":::")) return null
        val lines = rawText.lines()
        val elements = mutableListOf<ParsedDocElement>()
        var i = 0

        fun collectUntil(endToken: String): List<String> {
            val out = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().equals(endToken, ignoreCase = true)) {
                out.add(lines[i].trimEnd())
                i++
            }
            if (i < lines.size) i++
            return out
        }

        fun parsePipeRows(blockLines: List<String>): List<List<String>> {
            return blockLines.mapNotNull { raw ->
                val t = raw.trim()
                if (!t.contains("|")) return@mapNotNull null
                if (t.all { it == '-' || it == '|' || it == ':' || it.isWhitespace() }) return@mapNotNull null
                var cells = t.split("|").map { it.trim() }
                if (t.startsWith("|") && cells.isNotEmpty()) cells = cells.drop(1)
                if (t.endsWith("|") && cells.isNotEmpty()) cells = cells.dropLast(1)
                cells.takeIf { it.isNotEmpty() }
            }
        }

        fun addPlainLine(line: String) {
            val t = line.trim()
            if (t.isEmpty()) return
            when {
                t.startsWith("# ") -> elements.add(ParsedDocElement.Title(t.removePrefix("# ").trim()))
                t.startsWith("## ") -> elements.add(ParsedDocElement.Heading1(t.removePrefix("## ").trim()))
                t.startsWith("### ") -> elements.add(ParsedDocElement.Heading2(t.removePrefix("### ").trim()))
                t.startsWith("• ") || t.startsWith("- ") || t.startsWith("* ") -> {
                    elements.add(ParsedDocElement.BulletItem(t.drop(2).trim()))
                }
                else -> elements.add(ParsedDocElement.Paragraph(t))
            }
        }

        while (i < lines.size) {
            val token = lines[i].trim()
            when {
                token.equals(":::title-center", ignoreCase = true) -> {
                    val block = collectUntil(":::endtitle").filter { it.isNotBlank() }
                    if (block.isNotEmpty()) elements.add(ParsedDocElement.CenteredTitle(block))
                }
                token.equals(":::section", ignoreCase = true) -> {
                    val block = collectUntil(":::endsection").filter { it.isNotBlank() }
                    if (block.isNotEmpty()) elements.add(ParsedDocElement.SectionBand(block.joinToString(" ")))
                }
                token.equals(":::keyvalue-table", ignoreCase = true) -> {
                    val block = collectUntil(":::endtable")
                    val rows = parsePipeRows(block).map { r ->
                        when {
                            r.size >= 2 -> listOf(r.first(), r.drop(1).joinToString(" "))
                            r.size == 1 -> listOf(r.first(), "")
                            else -> listOf("", "")
                        }
                    }
                    if (rows.isNotEmpty()) elements.add(ParsedDocElement.KeyValueTable(rows))
                }
                token.equals(":::table", ignoreCase = true) -> {
                    val block = collectUntil(":::endtable")
                    val rows = parsePipeRows(block)
                    if (rows.isNotEmpty()) {
                        val headers = rows.first()
                        elements.add(ParsedDocElement.Table(headers, rows.drop(1)))
                    }
                }
                token.equals(":::columns2", ignoreCase = true) || token.equals(":::footer2", ignoreCase = true) -> {
                    val isFooter = token.equals(":::footer2", ignoreCase = true)
                    val endToken = if (isFooter) ":::endfooter2" else ":::endcolumns2"
                    val left = mutableListOf<String>()
                    val right = mutableListOf<String>()
                    var target = 0
                    i++
                    while (i < lines.size && !lines[i].trim().equals(endToken, ignoreCase = true)) {
                        when (lines[i].trim().lowercase()) {
                            ":::left" -> target = 1
                            ":::right" -> target = 2
                            else -> if (target == 1) left.add(lines[i].trimEnd()) else if (target == 2) right.add(lines[i].trimEnd())
                        }
                        i++
                    }
                    if (i < lines.size) i++
                    if (left.any { it.isNotBlank() } || right.any { it.isNotBlank() }) {
                        elements.add(ParsedDocElement.TwoColumnBlock(left, right, isFooter))
                    }
                }
                token.equals(":::banner-green", ignoreCase = true) -> {
                    val block = collectUntil(":::endbanner").filter { it.isNotBlank() }
                    if (block.isNotEmpty()) elements.add(ParsedDocElement.Banner(block.joinToString(" "), "green"))
                }
                token.equals(":::banner-orange", ignoreCase = true) -> {
                    val block = collectUntil(":::endbanner").filter { it.isNotBlank() }
                    if (block.isNotEmpty()) elements.add(ParsedDocElement.Banner(block.joinToString(" "), "orange"))
                }
                token.startsWith(":::") -> i++ // Unknown marker: ignore safely.
                else -> {
                    addPlainLine(token)
                    i++
                }
            }
        }
        return elements.takeIf { it.isNotEmpty() }
    }

    /**
     * Parses raw extracted page text into an ordered sequence of structured document elements.
     */
    fun parsePageContentIntoElements(rawText: String): List<ParsedDocElement> {
        val trimmedRaw = rawText.trim()
        if (trimmedRaw.isEmpty()) return emptyList()

        parseLayoutMarkupIntoElements(trimmedRaw)?.let { return it }

        // 0. Check if document is an official letter/circular or has custom structured blocks
        if (trimmedRaw.contains(":::letterhead") || trimmedRaw.contains(":::signoff") ||
            trimmedRaw.contains("|| Date:") || OfficialDocumentAnalyzer.isOfficialLetterOrNotice(trimmedRaw)
        ) {
            val structure = OfficialDocumentAnalyzer.parseOfficialLetter(trimmedRaw)
            val letterElements = mutableListOf<ParsedDocElement>()

            if (structure.letterheadLeft.isNotEmpty() || structure.letterheadRight.isNotEmpty()) {
                letterElements.add(ParsedDocElement.Letterhead(structure.letterheadLeft, structure.letterheadRight))
            }
            if (!structure.refText.isNullOrBlank() || !structure.dateText.isNullOrBlank()) {
                letterElements.add(ParsedDocElement.RefAndDate(structure.refText ?: "", structure.dateText ?: ""))
            }
            if (structure.recipientLines.isNotEmpty()) {
                letterElements.add(ParsedDocElement.RecipientBlock(structure.recipientLines))
            }
            if (!structure.subject.isNullOrBlank()) {
                letterElements.add(ParsedDocElement.LetterSubject(structure.subject))
            }
            if (!structure.salutation.isNullOrBlank()) {
                letterElements.add(ParsedDocElement.Paragraph(structure.salutation))
            }
            structure.highlightedBullets.forEach { bullet ->
                letterElements.add(ParsedDocElement.HighlightedBullet(bullet))
            }
            structure.bodyParagraphs.forEach { para ->
                letterElements.add(ParsedDocElement.Paragraph(para))
            }
            if (structure.signoffLines.isNotEmpty() || structure.stampLines.isNotEmpty()) {
                letterElements.add(ParsedDocElement.SignoffBlock(structure.signoffLines, structure.stampLines))
            }

            if (letterElements.isNotEmpty()) {
                return letterElements
            }
        }

        val elements = mutableListOf<ParsedDocElement>()
        val lines = rawText.lines()
        var lineIdx = 0

        while (lineIdx < lines.size) {
            val line = lines[lineIdx]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                lineIdx++
                continue
            }

            // 1. Check for Markdown table blocks (lines starting with | or containing multiple |)
            if (trimmed.startsWith("|") || (trimmed.contains("|") && trimmed.count { it == '|' } >= 2)) {
                val tableLines = mutableListOf<String>()
                while (lineIdx < lines.size) {
                    val currentLine = lines[lineIdx].trim()
                    if (currentLine.contains("|") && !currentLine.startsWith("#")) {
                        tableLines.add(currentLine)
                        lineIdx++
                    } else {
                        break
                    }
                }

                if (tableLines.isNotEmpty()) {
                    val tableRows = mutableListOf<List<String>>()
                    for (tLine in tableLines) {
                        // Skip markdown separator lines like "|---|---:|"
                        if (tLine.all { it == '-' || it == '|' || it == ':' || it.isWhitespace() }) continue
                        var cells = tLine.split("|").map { it.trim() }
                        if (tLine.startsWith("|") && cells.isNotEmpty()) cells = cells.drop(1)
                        if (tLine.endsWith("|") && cells.isNotEmpty()) cells = cells.dropLast(1)
                        if (cells.isNotEmpty()) {
                            tableRows.add(cells)
                        }
                    }

                    if (tableRows.isNotEmpty()) {
                        val headers = tableRows.first()
                        val dataRows = if (tableRows.size > 1) tableRows.subList(1, tableRows.size) else emptyList()
                        val maxCols = maxOf(headers.size, dataRows.maxOfOrNull { it.size } ?: 0)
                        val paddedHeaders = headers + List((maxCols - headers.size).coerceAtLeast(0)) { "Col ${it + headers.size + 1}" }
                        val paddedData = dataRows.map { r -> r + List((maxCols - r.size).coerceAtLeast(0)) { "" } }
                        elements.add(ParsedDocElement.Table(paddedHeaders, paddedData))
                        continue
                    }
                }
            }

            // 2. Check for Tab-separated table blocks
            if (trimmed.contains("\t") && trimmed.count { it == '\t' } >= 1) {
                val tsvLines = mutableListOf<String>()
                while (lineIdx < lines.size) {
                    val currentLine = lines[lineIdx].trim()
                    if (currentLine.contains("\t")) {
                        tsvLines.add(currentLine)
                        lineIdx++
                    } else {
                        break
                    }
                }
                if (tsvLines.size >= 2) {
                    val tableRows = tsvLines.map { it.split("\t").map { cell -> cell.trim() } }
                    val headers = tableRows.first()
                    val dataRows = tableRows.subList(1, tableRows.size)
                    elements.add(ParsedDocElement.Table(headers, dataRows))
                    continue
                }
            }

            // 3. Headings & Titles & Letter markers
            when {
                trimmed.startsWith("# ") -> {
                    elements.add(ParsedDocElement.Title(trimmed.removePrefix("# ").trim()))
                    lineIdx++
                }
                trimmed.startsWith("## ") -> {
                    elements.add(ParsedDocElement.Heading1(trimmed.removePrefix("## ").trim()))
                    lineIdx++
                }
                trimmed.startsWith("### ") -> {
                    elements.add(ParsedDocElement.Heading2(trimmed.removePrefix("### ").trim()))
                    lineIdx++
                }
                trimmed.startsWith("#### ") -> {
                    elements.add(ParsedDocElement.Heading3(trimmed.removePrefix("#### ").trim()))
                    lineIdx++
                }
                trimmed == "---" || trimmed == "===" || trimmed == "***" -> {
                    elements.add(ParsedDocElement.Divider)
                    lineIdx++
                }
                trimmed.startsWith("> ") -> {
                    elements.add(ParsedDocElement.Quote(trimmed.removePrefix("> ").trim()))
                    lineIdx++
                }
                trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("○ ") || trimmed.startsWith("▪ ") -> {
                    val clean = trimmed.removePrefix("• ").removePrefix("- ").removePrefix("* ").removePrefix("○ ").removePrefix("▪ ").trim()
                    if (clean.contains("==")) {
                        elements.add(ParsedDocElement.HighlightedBullet(clean.replace("==", "")))
                    } else {
                        elements.add(ParsedDocElement.BulletItem(clean))
                    }
                    lineIdx++
                }
                Regex("^(\\d+|[a-zA-Z]|[ivxlcdmIVXLCDM]+)[\\.\\)]\\s+.*").matches(trimmed) -> {
                    val match = Regex("^([\\d\\w]+[\\.\\)])\\s+(.*)").find(trimmed)
                    if (match != null) {
                        val numPrefix = match.groupValues[1]
                        val content = match.groupValues[2]
                        elements.add(ParsedDocElement.NumberedItem(numPrefix, content))
                    } else {
                        elements.add(ParsedDocElement.Paragraph(trimmed))
                    }
                    lineIdx++
                }
                // Key-Value pairs: e.g. "Name: John Doe", "Invoice Date: 2026-09-01", "Total: Tk. 5,000"
                trimmed.contains(":") && !trimmed.startsWith("http:") && !trimmed.startsWith("https:") -> {
                    val colonIdx = trimmed.indexOf(':')
                    val keyPart = trimmed.substring(0, colonIdx).trim()
                    val valuePart = trimmed.substring(colonIdx + 1).trim()
                    if (keyPart.length in 2..45 && !keyPart.contains("\n") && valuePart.isNotEmpty()) {
                        elements.add(ParsedDocElement.KeyValue(keyPart, valuePart))
                    } else if (keyPart.length in 2..40 && valuePart.isEmpty()) {
                        elements.add(ParsedDocElement.Heading2(keyPart))
                    } else {
                        elements.add(ParsedDocElement.Paragraph(trimmed))
                    }
                    lineIdx++
                }
                else -> {
                    elements.add(ParsedDocElement.Paragraph(trimmed))
                    lineIdx++
                }
            }
        }

        return elements
    }

    private fun buildDocumentXml(
        title: String,
        pages: List<PageEntity>,
        config: DocxExportConfig,
        imageRelMap: Map<Int, String>,
        imageDimMap: Map<Int, Pair<Int, Int>>,
        railLogoRelMap: Map<Int, String>,
        railLogoDimMap: Map<Int, Pair<Int, Int>>,
        railQrRelMap: Map<Int, String>,
        railQrDimMap: Map<Int, Pair<Int, Int>>
    ): String {
        val sb = StringBuilder()
        val font = config.fontFamily
        val hasRailwayTicket = pages.any { WordLayoutMarkup.isBangladeshRailwayTicket(it.extractedText) }

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
""")

        // Iterate through each page
        pages.forEachIndexed { index, page ->
            val pageText = page.extractedText?.trim()
            val hasText = !pageText.isNullOrBlank()
            val isRailwayPage = WordLayoutMarkup.isBangladeshRailwayTicket(pageText)

            // 1. EMBEDDED PAGE IMAGE:
            // Include image only when requested and when text wasn't extracted, or explicitly configured
            val relId = imageRelMap[index]
            val includeImageThisPage = relId != null &&
                ((config.imageHandling == "Preserve Images" && (!hasText || !config.includeFormattedText)) ||
                 config.layoutMode == "Image Only")

            if (includeImageThisPage && relId != null) {
                val drawingId = index + 1
                val dim = imageDimMap[index] ?: Pair(1080, 1440)
                val imgW = dim.first
                val imgH = dim.second

                // Calculate EMUs preserving aspect ratio (max available width = 5029200 EMUs ~ 5.5 inches)
                val cx = 5029200
                val ratio = if (imgW > 0) imgH.toFloat() / imgW.toFloat() else 1.333f
                val cy = (cx * ratio).toInt().coerceIn(1200000, 6800000)

                sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="100" w:after="200"/>
      </w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/>
            <wp:docPr id="$drawingId" name="PageImage_$drawingId"/>
            <wp:cNvGraphicFramePr>
              <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
            </wp:cNvGraphicFramePr>
            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:nvPicPr>
                    <pic:cNvPr id="$drawingId" name="image_p$drawingId.jpeg"/>
                    <pic:cNvPicPr/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="$relId"/>
                    <a:stretch>
                      <a:fillRect/>
                    </a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="$cx" cy="$cy"/>
                    </a:xfrm>
                    <a:prstGeom prst="rect">
                      <a:avLst/>
                    </a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>
""")
            }

            // Preserve the graphical identity of Railway e-tickets without rasterizing the body.
            if (isRailwayPage) {
                val logoRel = railLogoRelMap[index]
                val qrRel = railQrRelMap[index]
                if (logoRel != null && qrRel != null) {
                    sb.append(buildRailwayHeaderXml(logoRel, qrRel, index, font))
                }
            }

            // 2. STRUCTURED EDITABLE DOCUMENT CONTENT (Headings, Paragraphs, Key-Values, Tables, Lists, Letters)
            if (hasText && config.includeFormattedText) {
                val elements = parsePageContentIntoElements(pageText!!)

                elements.forEach { elem ->
                    when (elem) {
                        is ParsedDocElement.Letterhead -> {
                            sb.append("""
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="9026" w:type="dxa"/>
        <w:tblBorders>
          <w:top w:val="none"/>
          <w:left w:val="none"/>
          <w:bottom w:val="none"/>
          <w:right w:val="none"/>
          <w:insideH w:val="none"/>
          <w:insideV w:val="none"/>
        </w:tblBorders>
        <w:tblCellMar>
          <w:top w:w="40" w:type="dxa"/>
          <w:left w:w="0" w:type="dxa"/>
          <w:bottom w:w="40" w:type="dxa"/>
          <w:right w:w="0" w:type="dxa"/>
        </w:tblCellMar>
      </w:tblPr>
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="5200" w:type="dxa"/>
          </w:tcPr>
${elem.leftLines.mapIndexed { i, l ->
    """    <w:p>
      <w:pPr><w:spacing w:after="${if (i == 0) "40" else "20"}" w:line="240" w:lineRule="auto"/></w:pPr>
${buildInlineRunsXml(l, font, defaultColor = "000000", defaultSize = if (i == 0) "26" else "22", baseBold = true)}
    </w:p>"""
}.joinToString("\n")}
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="3826" w:type="dxa"/>
          </w:tcPr>
${elem.rightLines.mapIndexed { i, l ->
    """    <w:p>
      <w:pPr><w:jc w:val="right"/><w:spacing w:after="${if (i == 0) "40" else "20"}" w:line="220" w:lineRule="auto"/></w:pPr>
${buildInlineRunsXml(l, font, defaultColor = if (i == 0) "000000" else "334155", defaultSize = if (i == 0) "22" else "18", baseBold = (i == 0))}
    </w:p>"""
}.joinToString("\n")}
        </w:tc>
      </w:tr>
    </w:tbl>
""")
                        }
                        is ParsedDocElement.RefAndDate -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:tabs>
          <w:tab w:val="right" w:pos="9026"/>
        </w:tabs>
        <w:spacing w:before="140" w:after="160" w:line="260" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.ref)}</w:t>
      </w:r>
      <w:r><w:tab/></w:r>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.date)}</w:t>
      </w:r>
    </w:p>
""")
                        }
                        is ParsedDocElement.RecipientBlock -> {
                            elem.lines.forEachIndexed { i, l ->
                                sb.append("""
    <w:p>
      <w:pPr><w:spacing w:after="30" w:line="240" w:lineRule="auto"/></w:pPr>
${buildInlineRunsXml(l, font, defaultColor = "000000", defaultSize = "22", baseBold = (i == 0 || i == 1))}
    </w:p>
""")
                            }
                            sb.append("""
    <w:p><w:pPr><w:spacing w:after="80"/></w:pPr></w:p>
""")
                        }
                        is ParsedDocElement.LetterSubject -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="140" w:after="160" w:line="260" w:lineRule="auto"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "000000", defaultSize = "22", baseBold = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.HighlightedBullet -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:ind w:left="360"/>
        <w:spacing w:before="100" w:after="140" w:line="260" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.bulletChar)}  </w:t>
      </w:r>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:highlight w:val="yellow"/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.text)}</w:t>
      </w:r>
    </w:p>
""")
                        }
                        is ParsedDocElement.SignoffBlock -> {
                            sb.append("""
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="9026" w:type="dxa"/>
        <w:tblBorders>
          <w:top w:val="none"/>
          <w:left w:val="none"/>
          <w:bottom w:val="none"/>
          <w:right w:val="none"/>
          <w:insideH w:val="none"/>
          <w:insideV w:val="none"/>
        </w:tblBorders>
      </w:tblPr>
      <w:tr>
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="5200" w:type="dxa"/>
          </w:tcPr>
${elem.signoffLines.mapIndexed { i, l ->
    """    <w:p>
      <w:pPr><w:spacing w:after="${if (i == 0) "120" else "30"}" w:line="240" w:lineRule="auto"/></w:pPr>
${buildInlineRunsXml(l, font, defaultColor = "000000", defaultSize = "22", baseBold = (l.startsWith("(") || l.contains("Manager") || l.contains("Head")))}
    </w:p>"""
}.joinToString("\n")}
        </w:tc>
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="3826" w:type="dxa"/>
            ${if (elem.stampLines.isNotEmpty()) """<w:tcBorders>
              <w:top w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/>
              <w:left w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/>
              <w:bottom w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/>
              <w:right w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/>
            </w:tcBorders>
            <w:shd w:val="clear" w:color="auto" w:fill="F8FAFC"/>
            <w:tcMar>
              <w:top w:w="120" w:type="dxa"/>
              <w:left w:w="140" w:type="dxa"/>
              <w:bottom w:w="120" w:type="dxa"/>
              <w:right w:w="140" w:type="dxa"/>
            </w:tcMar>""" else ""}
          </w:tcPr>
${elem.stampLines.mapIndexed { i, l ->
    """    <w:p>
      <w:pPr><w:jc w:val="center"/><w:spacing w:after="30" w:line="220" w:lineRule="auto"/></w:pPr>
${buildInlineRunsXml(l, font, defaultColor = "1E293B", defaultSize = if (i == 0) "22" else "20", baseBold = (i == 0 || i == 1))}
    </w:p>"""
}.joinToString("\n")}
        </w:tc>
      </w:tr>
    </w:tbl>
""")
                        }
                        is ParsedDocElement.Title -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="240" w:after="100"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "000000", defaultSize = "34", baseBold = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.Heading1 -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="200" w:after="80"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "000000", defaultSize = "28", baseBold = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.Heading2 -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="160" w:after="60"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "111827", defaultSize = "24", baseBold = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.Heading3 -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="120" w:after="50"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "1E293B", defaultSize = "22", baseBold = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.KeyValue -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:after="80" w:line="260" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.key)}: </w:t>
      </w:r>
${buildInlineRunsXml(elem.value, font, defaultColor = "1E293B", defaultSize = "22")}
    </w:p>
""")
                        }
                        is ParsedDocElement.BulletItem -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:ind w:left="360"/>
        <w:spacing w:after="80" w:line="260" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">•  </w:t>
      </w:r>
${buildInlineRunsXml(elem.text, font, defaultColor = "1E293B", defaultSize = "22")}
    </w:p>
""")
                        }
                        is ParsedDocElement.NumberedItem -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:ind w:left="360"/>
        <w:spacing w:after="80" w:line="260" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:b/>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
          <w:color w:val="000000"/>
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(elem.number)} </w:t>
      </w:r>
${buildInlineRunsXml(elem.text, font, defaultColor = "1E293B", defaultSize = "22")}
    </w:p>
""")
                        }
                        is ParsedDocElement.Quote -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:pBdr>
          <w:left w:val="single" w:sz="18" w:space="8" w:color="94A3B8"/>
        </w:pBdr>
        <w:ind w:left="360"/>
        <w:spacing w:before="80" w:after="120" w:line="260" w:lineRule="auto"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "475569", defaultSize = "22", baseItalic = true)}
    </w:p>
""")
                        }
                        is ParsedDocElement.Divider -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:pBdr>
          <w:bottom w:val="single" w:sz="6" w:space="1" w:color="CBD5E1"/>
        </w:pBdr>
        <w:spacing w:before="120" w:after="160"/>
      </w:pPr>
    </w:p>
""")
                        }
                        is ParsedDocElement.CenteredTitle -> {
                            if (!isRailwayPage) sb.append(buildCenteredTitleXml(elem.lines, font))
                        }
                        is ParsedDocElement.SectionBand -> {
                            sb.append(buildSectionBandXml(elem.text, font))
                        }
                        is ParsedDocElement.KeyValueTable -> {
                            sb.append(buildKeyValueTableXml(elem.rows, font))
                        }
                        is ParsedDocElement.TwoColumnBlock -> {
                            sb.append(buildTwoColumnBlockXml(elem.leftLines, elem.rightLines, font, elem.footer))
                        }
                        is ParsedDocElement.Banner -> {
                            sb.append(buildBannerXml(elem.text, font, elem.kind))
                        }
                        is ParsedDocElement.Table -> {
                            sb.append(buildDocxTableFromRows(elem.headers, elem.rows, font))
                        }
                        is ParsedDocElement.Paragraph -> {
                            sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="both"/>
        <w:spacing w:after="140" w:line="260" w:lineRule="auto"/>
      </w:pPr>
${buildInlineRunsXml(elem.text, font, defaultColor = "000000", defaultSize = "22")}
    </w:p>
""")
                        }
                    }
                }
            } else if (!includeImageThisPage) {
                sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:after="120"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
          <w:i/>
          <w:sz w:val="22"/>
          <w:color w:val="94A3B8"/>
        </w:rPr>
        <w:t xml:space="preserve">[Document Page Content]</w:t>
      </w:r>
    </w:p>
""")
            }

            // Page Break between pages (except last)
            if (index < pages.size - 1) {
                sb.append("""
    <w:p>
      <w:r>
        <w:br w:type="page"/>
      </w:r>
    </w:p>
""")
            }
        }

        // Section A4 sizing & margins. Layout-rich official tickets use compact margins
        // and a native Word page border to match the source without rasterizing the page.
        val margin = if (hasRailwayTicket) 460 else 1440
        val pageBorderXml = if (hasRailwayTicket) """
      <w:pgBorders w:offsetFrom="page">
        <w:top w:val="single" w:sz="14" w:space="18" w:color="009B4D"/>
        <w:left w:val="single" w:sz="14" w:space="18" w:color="009B4D"/>
        <w:bottom w:val="single" w:sz="14" w:space="18" w:color="009B4D"/>
        <w:right w:val="single" w:sz="14" w:space="18" w:color="009B4D"/>
      </w:pgBorders>""" else ""
        sb.append("""
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="$margin" w:right="$margin" w:bottom="$margin" w:left="$margin"/>
$pageBorderXml
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }

    private fun buildRailwayHeaderXml(logoRel: String, qrRel: String, pageIndex: Int, font: String): String {
        val logoId = 7000 + pageIndex * 2
        val qrId = logoId + 1
        return """
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="10000" w:type="dxa"/>
        <w:tblBorders><w:top w:val="none"/><w:left w:val="none"/><w:bottom w:val="none"/><w:right w:val="none"/><w:insideH w:val="none"/><w:insideV w:val="none"/></w:tblBorders>
        <w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="0" w:type="dxa"/><w:bottom w:w="30" w:type="dxa"/><w:right w:w="0" w:type="dxa"/></w:tblCellMar>
      </w:tblPr>
      <w:tr>
        <w:tc><w:tcPr><w:tcW w:w="1700" w:type="dxa"/></w:tcPr>
          <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="850000" cy="850000"/><wp:docPr id="$logoId" name="RailwayLogo_${pageIndex + 1}"/>
            <wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>
              <pic:nvPicPr><pic:cNvPr id="$logoId" name="rail_logo.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>
              <pic:blipFill><a:blip r:embed="$logoRel"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
              <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="850000" cy="850000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
            </pic:pic></a:graphicData></a:graphic>
          </wp:inline></w:drawing></w:r></w:p>
        </w:tc>
        <w:tc><w:tcPr><w:tcW w:w="5900" w:type="dxa"/></w:tcPr>
          <w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="80" w:after="10"/></w:pPr>${buildInlineRunsXml("BANGLADESH RAILWAY", font, defaultColor = "1F6FB2", defaultSize = "34", baseBold = true)}</w:p>
          <w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="20"/></w:pPr>${buildInlineRunsXml("বাংলাদেশ রেলওয়ে", font, defaultColor = "1F6FB2", defaultSize = "28")}</w:p>
        </w:tc>
        <w:tc><w:tcPr><w:tcW w:w="2400" w:type="dxa"/></w:tcPr>
          <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="1250000" cy="1450000"/><wp:docPr id="$qrId" name="RailwayQR_${pageIndex + 1}"/>
            <wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>
              <pic:nvPicPr><pic:cNvPr id="$qrId" name="rail_qr.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>
              <pic:blipFill><a:blip r:embed="$qrRel"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
              <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1250000" cy="1450000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
            </pic:pic></a:graphicData></a:graphic>
          </wp:inline></w:drawing></w:r></w:p>
        </w:tc>
      </w:tr>
    </w:tbl>
"""
    }

    private fun buildCenteredTitleXml(lines: List<String>, font: String): String {
        val sb = StringBuilder()
        lines.filter { it.isNotBlank() }.forEachIndexed { index, raw ->
            val text = raw.trim()
            val size = if (index == 0) "32" else "28"
            val color = if (index <= 1) "1F6FB2" else "000000"
            sb.append("""
    <w:p>
      <w:pPr><w:jc w:val="center"/><w:spacing w:before="${if (index == 0) "40" else "0"}" w:after="${if (index == lines.lastIndex) "100" else "20"}"/></w:pPr>
${buildInlineRunsXml(text, font, defaultColor = color, defaultSize = size, baseBold = index == 0)}
    </w:p>
""")
        }
        return sb.toString()
    }

    private fun buildSectionBandXml(text: String, font: String): String {
        return """
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="10000" w:type="dxa"/>
        <w:tblBorders><w:top w:val="none"/><w:left w:val="none"/><w:bottom w:val="none"/><w:right w:val="none"/><w:insideH w:val="none"/><w:insideV w:val="none"/></w:tblBorders>
        <w:tblCellMar><w:top w:w="70" w:type="dxa"/><w:left w:w="120" w:type="dxa"/><w:bottom w:w="70" w:type="dxa"/><w:right w:w="120" w:type="dxa"/></w:tblCellMar>
      </w:tblPr>
      <w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="009B4D"/></w:tcPr>
        <w:p><w:pPr><w:spacing w:after="0"/></w:pPr>${buildInlineRunsXml(text, font, defaultColor = "FFFFFF", defaultSize = "24", baseBold = true)}</w:p>
      </w:tc></w:tr>
    </w:tbl>
"""
    }

    private fun buildKeyValueTableXml(rows: List<List<String>>, font: String): String {
        val sb = StringBuilder()
        sb.append("""
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="10000" w:type="dxa"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:color="B7B7B7"/><w:left w:val="single" w:sz="4" w:color="B7B7B7"/>
          <w:bottom w:val="single" w:sz="4" w:color="B7B7B7"/><w:right w:val="single" w:sz="4" w:color="B7B7B7"/>
          <w:insideH w:val="single" w:sz="4" w:color="D0D0D0"/><w:insideV w:val="single" w:sz="4" w:color="D0D0D0"/>
        </w:tblBorders>
        <w:tblCellMar><w:top w:w="45" w:type="dxa"/><w:left w:w="90" w:type="dxa"/><w:bottom w:w="45" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar>
      </w:tblPr>
""")
        rows.forEach { row ->
            val left = row.getOrNull(0).orEmpty()
            val right = row.getOrNull(1).orEmpty()
            val important = left.contains("Total Fare", true) || left.contains("Mobile Number", true) || left.contains("PNR Number", true) || left.contains("মোট ভাড়া")
            sb.append("""
      <w:tr>
        <w:tc><w:tcPr><w:tcW w:w="5000" w:type="dxa"/></w:tcPr><w:p><w:pPr><w:spacing w:after="0"/></w:pPr>${buildInlineRunsXml(left, font, defaultColor = "111111", defaultSize = "18", baseBold = important)}</w:p></w:tc>
        <w:tc><w:tcPr><w:tcW w:w="5000" w:type="dxa"/></w:tcPr><w:p><w:pPr><w:spacing w:after="0"/></w:pPr>${buildInlineRunsXml(right, font, defaultColor = "111111", defaultSize = "18", baseBold = important)}</w:p></w:tc>
      </w:tr>
""")
        }
        sb.append("    </w:tbl>\n")
        return sb.toString()
    }

    private fun buildTwoColumnBlockXml(leftLines: List<String>, rightLines: List<String>, font: String, footer: Boolean): String {
        fun renderLines(lines: List<String>): String {
            val out = StringBuilder()
            lines.filter { it.isNotBlank() }.forEachIndexed { index, raw ->
                val t = raw.trim()
                val bullet = t.startsWith("• ") || t.startsWith("- ") || t.startsWith("* ")
                val content = if (bullet) t.drop(2).trim() else t
                val bold = index == 0 || content.startsWith("**") || content.endsWith("**")
                out.append("""
          <w:p><w:pPr>${if (bullet) "<w:ind w:left=\"240\"/>" else ""}<w:spacing w:after="${if (footer) "30" else "40"}" w:line="220" w:lineRule="auto"/></w:pPr>
          ${if (bullet) "<w:r><w:rPr><w:b/><w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">•  </w:t></w:r>" else ""}
          ${buildInlineRunsXml(content, font, defaultColor = "111111", defaultSize = if (footer) "18" else "18", baseBold = bold)}</w:p>
""")
            }
            return out.toString()
        }
        return """
    <w:tbl>
      <w:tblPr><w:tblW w:w="10000" w:type="dxa"/><w:tblBorders><w:top w:val="none"/><w:left w:val="none"/><w:bottom w:val="none"/><w:right w:val="none"/><w:insideH w:val="none"/><w:insideV w:val="none"/></w:tblBorders>
      <w:tblCellMar><w:top w:w="70" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="70" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tblCellMar></w:tblPr>
      <w:tr>
        <w:tc><w:tcPr><w:tcW w:w="5000" w:type="dxa"/></w:tcPr>${renderLines(leftLines)}</w:tc>
        <w:tc><w:tcPr><w:tcW w:w="5000" w:type="dxa"/></w:tcPr>${renderLines(rightLines)}</w:tc>
      </w:tr>
    </w:tbl>
"""
    }

    private fun buildBannerXml(text: String, font: String, kind: String): String {
        val green = kind.equals("green", ignoreCase = true)
        val bg = if (green) "E8F5EA" else "FFF0D9"
        val fg = if (green) "E65300" else "C62828"
        return """
    <w:tbl>
      <w:tblPr><w:tblW w:w="10000" w:type="dxa"/><w:tblBorders><w:top w:val="none"/><w:left w:val="none"/><w:bottom w:val="none"/><w:right w:val="none"/><w:insideH w:val="none"/><w:insideV w:val="none"/></w:tblBorders></w:tblPr>
      <w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="$bg"/><w:tcMar><w:top w:w="90" w:type="dxa"/><w:left w:w="100" w:type="dxa"/><w:bottom w:w="90" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tcMar></w:tcPr>
      <w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="0"/></w:pPr>${buildInlineRunsXml(text, font, defaultColor = fg, defaultSize = "18", baseBold = true)}</w:p>
      </w:tc></w:tr>
    </w:tbl>
"""
    }

    private fun buildDocxTableFromRows(
        headers: List<String>,
        rows: List<List<String>>,
        font: String
    ): String {
        val sb = StringBuilder()
        val totalCols = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 1)
        val colWidth = if (totalCols > 0) (9000 / totalCols) else 9000

        sb.append("""
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="9000" w:type="dxa"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
        </w:tblBorders>
        <w:tblCellMar>
          <w:top w:w="140" w:type="dxa"/>
          <w:left w:w="160" w:type="dxa"/>
          <w:bottom w:w="140" w:type="dxa"/>
          <w:right w:w="160" w:type="dxa"/>
        </w:tblCellMar>
      </w:tblPr>
""")

        // Header Row
        sb.append("""
      <w:tr>
        <w:trPr><w:tblHeader/></w:trPr>
""")
        for (colIdx in 0 until totalCols) {
            val headerText = headers.getOrNull(colIdx) ?: "Col ${colIdx + 1}"
            sb.append("""
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="$colWidth" w:type="dxa"/>
            <w:shd w:val="clear" w:color="auto" w:fill="0F766E"/>
          </w:tcPr>
          <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
              <w:rPr>
                <w:rFonts w:ascii="$font" w:hAnsi="$font" w:eastAsia="$font" w:cs="Kalpurush, SolaimanLipi, Vrinda, $font"/>
                <w:b/>
                <w:sz w:val="22"/>
                <w:szCs w:val="22"/>
                <w:color w:val="FFFFFF"/>
              </w:rPr>
              <w:t xml:space="preserve">${escapeXml(headerText)}</w:t>
            </w:r>
          </w:p>
        </w:tc>
""")
        }
        sb.append("      </w:tr>\n")

        // Data Rows
        rows.forEachIndexed { rowIdx, rowData ->
            val isZebra = rowIdx % 2 == 1
            val fillHex = if (isZebra) "F8FAFC" else "FFFFFF"

            sb.append("""
      <w:tr>
""")
            for (colIdx in 0 until totalCols) {
                val cellText = rowData.getOrNull(colIdx) ?: ""
                sb.append("""
        <w:tc>
          <w:tcPr>
            <w:tcW w:w="$colWidth" w:type="dxa"/>
            <w:shd w:val="clear" w:color="auto" w:fill="$fillHex"/>
          </w:tcPr>
          <w:p>
${buildInlineRunsXml(cellText, font, defaultColor = "1E293B", defaultSize = "20")}
          </w:p>
        </w:tc>
""")
            }
            sb.append("      </w:tr>\n")
        }

        sb.append("    </w:tbl>\n")
        return sb.toString()
    }

    /**
     * Share Word Document via standard Android Intent.
     */
    fun shareDocx(context: Context, docxFile: File) {
        if (!docxFile.exists()) {
            Toast.makeText(context, "DOCX file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, docxFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, docxFile.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Word (.docx) Document")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share DOCX: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share Word Document specifically to WhatsApp
     */
    fun shareDocxToWhatsApp(context: Context, docxFile: File) {
        if (!docxFile.exists()) {
            Toast.makeText(context, "DOCX file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, docxFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Here is your Word document: ${docxFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            shareDocx(context, docxFile)
        }
    }

    /**
     * Share Word Document specifically to Gmail
     */
    fun shareDocxToGmail(context: Context, docxFile: File) {
        if (!docxFile.exists()) {
            Toast.makeText(context, "DOCX file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, docxFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                setPackage("com.google.android.gm")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, docxFile.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            shareDocx(context, docxFile)
        }
    }

    /**
     * Open Word Document with installed MS Word, WPS Office, or Docs app.
     */
    fun openDocx(context: Context, docxFile: File) {
        if (!docxFile.exists()) {
            Toast.makeText(context, "DOCX file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, docxFile)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open Word Document with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No Word document viewer found on device", Toast.LENGTH_SHORT).show()
        }
    }
}
