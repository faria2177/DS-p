package com.docscan.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.docscan.data.model.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * High-performance OpenXML Spreadsheet (.xlsx) generator.
 * Creates standard, genuine Microsoft Excel (.xlsx) workbooks with:
 * - Clean multi-sheet and table support
 * - Formatted Header row with Emerald Green (#0F766E) or Excel Green (#00C853) fill and white bold text
 * - Alternating zebra rows (#F8FAFC)
 * - Auto-typed numeric columns with Bengali (০-৯) and Western (0-9) digits support
 * - Automatic dynamic column widths calculated from Unicode text length
 * - Native OpenXML formula support (=SUM(...)) for total rows
 * - Full UTF-8 support for Bengali, English, and multilingual scripts
 * - Sanitized OpenXML compliance to prevent spreadsheet corruptions
 */
object ExcelExporter {

    enum class ExcelExtractionMode {
        AUTO_DETECT,
        TABLE_MODE,
        RAW_TEXT
    }

    data class TableData(
        val sheetName: String = "Table Data",
        val headers: List<String>,
        val rows: List<List<String>>,
        val numericColumns: Set<Int> = emptySet(),
        val includeTotalRow: Boolean = false,
        val boldHeader: Boolean = true,
        val autoType: Boolean = true,
        val autoWidth: Boolean = true
    )


    /** One visual table/section inside a page/sheet. */
    data class StructuredTableSection(
        val title: String = "",
        val headers: List<String>,
        val rows: List<List<String>>
    )

    /**
     * Structured page model for layout-preserving exports.
     * Multiple table sections remain on ONE worksheet, separated by merged section titles.
     */
    data class StructuredSheetData(
        val sheetName: String,
        val documentTitle: String = "",
        val sections: List<StructuredTableSection>
    )

    /**
     * Converts a list of TableData objects into a multi-sheet Microsoft Excel (.xlsx) file.
     */
    suspend fun generateXlsxFromTables(
        context: Context,
        documentTitle: String,
        tables: List<TableData>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val docsDir = FileUtils.getPdfsDir(context)
            val sanitizedTitle = documentTitle.trim()
                .replace(Regex("[^a-zA-Z0-9_\\-\\s\u0980-\u09FF]"), "_")
                .replace(Regex("\\s+"), "_")
                .ifBlank { "Excel_Spreadsheet" }
            
            var candidateFile = File(docsDir, "${sanitizedTitle}.xlsx")
            var counter = 1
            while (candidateFile.exists()) {
                candidateFile = File(docsDir, "${sanitizedTitle}_($counter).xlsx")
                counter++
            }
            val xlsxFile = candidateFile

            val safeTables = if (tables.isEmpty()) {
                listOf(
                    TableData(
                        sheetName = "Sheet 1",
                        headers = listOf("No.", "Extracted Content"),
                        rows = listOf(listOf("1", "Document Processed")),
                        boldHeader = true,
                        autoType = true,
                        autoWidth = true
                    )
                )
            } else tables

            FileOutputStream(xlsxFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. [Content_Types].xml
                    writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(safeTables.size))

                    // 2. _rels/.rels
                    writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())

                    // 3. xl/_rels/workbook.xml.rels
                    writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(safeTables.size))

                    // 4. xl/workbook.xml
                    writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(safeTables))

                    // 5. xl/styles.xml
                    writeZipEntry(zos, "xl/styles.xml", buildStylesXml())

                    // 6. xl/worksheets/sheetN.xml for each table
                    safeTables.forEachIndexed { index, table ->
                        val sheetXml = buildWorksheetXml(table)
                        writeZipEntry(zos, "xl/worksheets/sheet${index + 1}.xml", sheetXml)
                    }
                }
            }

            if (xlsxFile.exists() && xlsxFile.length() > 0) {
                xlsxFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a layout-preserving workbook: page heading -> section heading -> real table -> blank row -> next section.
     * This is used by To Excel so a page with two tables does not become a fake 3-column OCR line list.
     */
    suspend fun generateStructuredXlsx(
        context: Context,
        documentTitle: String,
        sheets: List<StructuredSheetData>
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (sheets.isEmpty() || sheets.all { it.sections.isEmpty() }) return@withContext null

            val docsDir = FileUtils.getPdfsDir(context)
            val sanitizedTitle = documentTitle.trim()
                .replace(Regex("[^a-zA-Z0-9_\\-\\s\u0980-\u09FF]"), "_")
                .replace(Regex("\\s+"), "_")
                .ifBlank { "Excel_Spreadsheet" }

            var candidateFile = File(docsDir, "${sanitizedTitle}.xlsx")
            var counter = 1
            while (candidateFile.exists()) {
                candidateFile = File(docsDir, "${sanitizedTitle}_($counter).xlsx")
                counter++
            }

            FileOutputStream(candidateFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(sheets.size))
                    writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())
                    writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
                    writeZipEntry(zos, "xl/workbook.xml", buildStructuredWorkbookXml(sheets))
                    writeZipEntry(zos, "xl/styles.xml", buildStylesXml())

                    sheets.forEachIndexed { index, sheet ->
                        writeZipEntry(
                            zos,
                            "xl/worksheets/sheet${index + 1}.xml",
                            buildStructuredWorksheetXml(sheet)
                        )
                    }
                }
            }

            candidateFile.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts raw CSV text into a formatted .xlsx Excel file.
     */
    suspend fun generateXlsxFromCsv(
        context: Context,
        documentTitle: String,
        csvContent: String,
        boldHeader: Boolean = true,
        autoType: Boolean = true,
        autoWidth: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return@withContext generateXlsxFromTables(
                context,
                documentTitle,
                listOf(
                    TableData(
                        sheetName = "Sheet 1",
                        headers = listOf("Information"),
                        rows = listOf(listOf("No table data found")),
                        boldHeader = boldHeader,
                        autoType = autoType,
                        autoWidth = autoWidth
                    )
                )
            )
        }

        val parsedRows = lines.map { parseCsvLine(it) }
        val headers = parsedRows.firstOrNull() ?: listOf("Column 1")
        val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

        // Detect numeric columns
        val numericCols = mutableSetOf<Int>()
        if (autoType && dataRows.isNotEmpty()) {
            for (colIdx in headers.indices) {
                val hasValues = dataRows.any { it.getOrNull(colIdx)?.trim()?.isNotEmpty() == true }
                val allNumeric = dataRows.all { row ->
                    val cellVal = row.getOrNull(colIdx)?.trim() ?: ""
                    cellVal.isEmpty() || parseNumericValue(cellVal) != null
                }
                if (allNumeric && hasValues) {
                    numericCols.add(colIdx)
                }
            }
        }

        val tableData = TableData(
            sheetName = "Extracted Data",
            headers = headers,
            rows = dataRows,
            numericColumns = numericCols,
            includeTotalRow = numericCols.isNotEmpty() && dataRows.size > 1,
            boldHeader = boldHeader,
            autoType = autoType,
            autoWidth = autoWidth
        )

        generateXlsxFromTables(context, documentTitle, listOf(tableData))
    }

    /**
     * Converts document pages into Excel sheets with OCR text and tables.
     */
    suspend fun generateXlsxFromPages(
        context: Context,
        documentTitle: String,
        pages: List<PageEntity>,
        extractionMode: ExcelExtractionMode = ExcelExtractionMode.AUTO_DETECT,
        boldHeader: Boolean = true,
        autoType: Boolean = true,
        autoWidth: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        val tables = mutableListOf<TableData>()

        pages.forEachIndexed { index, page ->
            val pageNum = page.pageNumber.takeIf { it > 0 } ?: (index + 1)
            val sheetName = "Page $pageNum"
            val text = page.extractedText ?: ""

            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val rows = mutableListOf<List<String>>()

            when (extractionMode) {
                ExcelExtractionMode.RAW_TEXT -> {
                    lines.forEachIndexed { lineIdx, line ->
                        rows.add(listOf((lineIdx + 1).toString(), line))
                    }
                }
                ExcelExtractionMode.TABLE_MODE -> {
                    lines.forEachIndexed { lineIdx, line ->
                        when {
                            line.contains("\t") -> {
                                rows.add(line.split("\t").map { it.trim() })
                            }
                            line.contains("|") -> {
                                val parts = parsePipeLine(line)
                                val isSeparatorRow = line.all { it == '-' || it == '|' || it == ':' || it.isWhitespace() }
                                if (parts.isNotEmpty() && !isSeparatorRow) {
                                    rows.add(parts)
                                }
                            }
                            line.contains(",") && line.count { it == ',' } >= 2 -> {
                                rows.add(parseCsvLine(line))
                            }
                            line.contains("  ") -> {
                                val cols = line.split(Regex("\\s{2,}")).map { it.trim() }
                                if (cols.size > 1) rows.add(cols) else rows.add(listOf((lineIdx + 1).toString(), line))
                            }
                            else -> {
                                rows.add(listOf((lineIdx + 1).toString(), line))
                            }
                        }
                    }
                }
                ExcelExtractionMode.AUTO_DETECT -> {
                    lines.forEachIndexed { lineIdx, line ->
                        when {
                            line.contains("\t") -> {
                                rows.add(line.split("\t").map { it.trim() })
                            }
                            line.contains("|") -> {
                                val parts = parsePipeLine(line)
                                val isSeparatorRow = line.all { it == '-' || it == '|' || it == ':' || it.isWhitespace() }
                                if (parts.isNotEmpty() && !isSeparatorRow) {
                                    rows.add(parts)
                                }
                            }
                            line.contains(",") && line.count { it == ',' } >= 2 -> {
                                rows.add(parseCsvLine(line))
                            }
                            line.contains(":") && !line.startsWith("http") -> {
                                val parts = line.split(":", limit = 2).map { it.trim() }
                                rows.add(listOf((lineIdx + 1).toString(), parts[0], parts.getOrElse(1) { "" }))
                            }
                            line.contains("  ") -> {
                                val cols = line.split(Regex("\\s{2,}")).map { it.trim() }
                                if (cols.size > 1) rows.add(cols) else rows.add(listOf((lineIdx + 1).toString(), "Text Line", line))
                            }
                            else -> {
                                rows.add(listOf((lineIdx + 1).toString(), "Text Line", line))
                            }
                        }
                    }
                }
            }

            val headers = if (extractionMode == ExcelExtractionMode.RAW_TEXT) {
                listOf("Line", "Recognized Content")
            } else if (rows.isNotEmpty() && rows[0].size > 2) {
                List(rows.maxOf { it.size }) { "Column ${it + 1}" }
            } else if (rows.isNotEmpty() && rows[0].size == 3) {
                listOf("No.", "Property / Field", "Recognized Content")
            } else {
                listOf("No.", "Recognized Content")
            }

            val numericCols = mutableSetOf<Int>()
            if (autoType && rows.isNotEmpty()) {
                val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 1)
                for (colIdx in 0 until colCount) {
                    val allNumeric = rows.all { r ->
                        val v = r.getOrNull(colIdx)?.trim() ?: ""
                        v.isEmpty() || parseNumericValue(v) != null
                    }
                    if (allNumeric && rows.any { it.getOrNull(colIdx)?.trim()?.isNotEmpty() == true }) {
                        numericCols.add(colIdx)
                    }
                }
            }

            tables.add(
                TableData(
                    sheetName = sheetName,
                    headers = headers,
                    rows = rows.ifEmpty { listOf(listOf("1", "No text detected")) },
                    numericColumns = numericCols,
                    includeTotalRow = autoType && numericCols.isNotEmpty() && rows.size > 1,
                    boldHeader = boldHeader,
                    autoType = autoType,
                    autoWidth = autoWidth
                )
            )
        }

        generateXlsxFromTables(context, documentTitle, tables)
    }

    /**
     * Parses numeric strings supporting Bengali digits (০-৯), Western digits (0-9), commas, currency signs.
     */
    fun parseNumericValue(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Avoid converting phone numbers or IDs with leading zeros into raw doubles if length > 5
        if (trimmed.startsWith("0") && trimmed.length > 2 && !trimmed.startsWith("0.")) {
            return null
        }
        if (trimmed.startsWith("০") && trimmed.length > 2 && !trimmed.startsWith("০.")) {
            return null
        }

        // Convert Bengali digits to English
        val bengaliMap = mapOf(
            '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
            '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
        )
        val normalized = trimmed.map { bengaliMap[it] ?: it }.joinToString("")
            .replace("৳", "")
            .replace("Tk.", "", ignoreCase = true)
            .replace("Tk", "", ignoreCase = true)
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("/-", "")
            .replace(",", "")
            .trim()

        return normalized.toDoubleOrNull()
    }

    private fun parsePipeLine(line: String): List<String> {
        val trimmedLine = line.trim()
        var cells = trimmedLine.split("|").map { it.trim() }
        if (trimmedLine.startsWith("|") && cells.isNotEmpty()) cells = cells.drop(1)
        if (trimmedLine.endsWith("|") && cells.isNotEmpty()) cells = cells.dropLast(1)
        return cells
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    fun escapeXml(text: String): String {
        val sb = java.lang.StringBuilder(text.length + 16)
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
                    if (ch.code >= 0x20 && ch.code != 0xFFFF && ch.code != 0xFFFE) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun columnLetter(colIndex: Int): String {
        var n = colIndex
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun buildContentTypesXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
""")
        for (i in 1..sheetCount) {
            sb.append("""  <Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
""")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
""")
        for (i in 1..sheetCount) {
            sb.append("""  <Relationship Id="rIdSheet$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>
""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildWorkbookXml(tables: List<TableData>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
""")
        tables.forEachIndexed { index, table ->
            val sheetId = index + 1
            val safeName = escapeXml(table.sheetName.take(31).ifBlank { "Sheet $sheetId" })
            sb.append("""    <sheet name="$safeName" sheetId="$sheetId" r:id="rIdSheet$sheetId"/>
""")
        }
        sb.append("""  </sheets>
</workbook>""")
        return sb.toString()
    }

    private fun buildStructuredWorkbookXml(sheets: List<StructuredSheetData>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
""")
        sheets.forEachIndexed { index, sheet ->
            val sheetId = index + 1
            val rawName = sheet.sheetName.take(31).ifBlank { "Sheet $sheetId" }
            val safeName = escapeXml(rawName.replace(Regex("[\\[\\]:*?/\\\\]"), "_"))
            sb.append("""    <sheet name="$safeName" sheetId="$sheetId" r:id="rIdSheet$sheetId"/>""").append('\n')
        }
        sb.append("  </sheets>\n</workbook>")
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00"/>
  </numFmts>
  <fonts count="5">
    <font><sz val="11"/><color rgb="FF1E293B"/><name val="Segoe UI"/><family val="2"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Segoe UI"/><family val="2"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F766E"/><name val="Segoe UI"/><family val="2"/></font>
    <font><b/><sz val="14"/><color rgb="FF111827"/><name val="Noto Sans Bengali"/><family val="2"/></font>
    <font><b/><sz val="12"/><color rgb="FF111827"/><name val="Noto Sans Bengali"/><family val="2"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FF64748B"/></left>
      <right style="thin"><color rgb="FF64748B"/></right>
      <top style="thin"><color rgb="FF64748B"/></top>
      <bottom style="thin"><color rgb="FF64748B"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="9">
    <!-- 0 standard old export -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <!-- 1 old emerald header -->
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <!-- 2 zebra -->
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <!-- 3 numeric -->
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 4 total -->
    <xf numFmtId="164" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 5 structured document title -->
    <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <!-- 6 structured section title -->
    <xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <!-- 7 structured dark-blue header -->
    <xf numFmtId="0" fontId="1" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <!-- 8 structured centered body -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildStructuredWorksheetXml(sheet: StructuredSheetData): String {
        val allSections = sheet.sections.filter { it.headers.isNotEmpty() || it.rows.isNotEmpty() }
        val columnCount = allSections.maxOfOrNull { section ->
            maxOf(section.headers.size, section.rows.maxOfOrNull { it.size } ?: 0)
        }?.coerceAtLeast(1) ?: 1

        val sb = StringBuilder()
        val merges = mutableListOf<String>()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
""")

        sb.append("  <cols>\n")
        for (colIdx in 0 until columnCount) {
            val contentLengths = mutableListOf<Int>()
            allSections.forEach { section ->
                contentLengths.add(section.headers.getOrNull(colIdx)?.length ?: 0)
                section.rows.forEach { row -> contentLengths.add(row.getOrNull(colIdx)?.length ?: 0) }
            }
            val maxLen = contentLengths.maxOrNull() ?: 10
            val width = (maxLen + 4).coerceIn(15, if (colIdx == columnCount - 1) 34 else 28)
            sb.append("""    <col min="${colIdx + 1}" max="${colIdx + 1}" width="$width" customWidth="1"/>""").append('\n')
        }
        sb.append("  </cols>\n")
        sb.append("  <sheetData>\n")

        var rowIndex = 1
        val lastCol = columnLetter(columnCount - 1)

        if (sheet.documentTitle.isNotBlank()) {
            sb.append("""    <row r="$rowIndex" ht="28" customHeight="1">""").append('\n')
            sb.append("""      <c r="A$rowIndex" s="5" t="inlineStr"><is><t>${escapeXml(sheet.documentTitle)}</t></is></c>""").append('\n')
            sb.append("    </row>\n")
            if (columnCount > 1) merges.add("A$rowIndex:$lastCol$rowIndex")
            rowIndex += 2
        }

        allSections.forEachIndexed { sectionIndex, section ->
            if (section.title.isNotBlank()) {
                sb.append("""    <row r="$rowIndex" ht="24" customHeight="1">""").append('\n')
                sb.append("""      <c r="A$rowIndex" s="6" t="inlineStr"><is><t>${escapeXml(section.title)}</t></is></c>""").append('\n')
                sb.append("    </row>\n")
                if (columnCount > 1) merges.add("A$rowIndex:$lastCol$rowIndex")
                rowIndex++
            }

            sb.append("""    <row r="$rowIndex" ht="26" customHeight="1">""").append('\n')
            for (colIdx in 0 until columnCount) {
                val ref = "${columnLetter(colIdx)}$rowIndex"
                val value = section.headers.getOrNull(colIdx).orEmpty()
                sb.append("""      <c r="$ref" s="7" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""").append('\n')
            }
            sb.append("    </row>\n")
            rowIndex++

            section.rows.forEach { row ->
                sb.append("""    <row r="$rowIndex" ht="22" customHeight="1">""").append('\n')
                for (colIdx in 0 until columnCount) {
                    val ref = "${columnLetter(colIdx)}$rowIndex"
                    val value = row.getOrNull(colIdx).orEmpty()
                    sb.append("""      <c r="$ref" s="8" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""").append('\n')
                }
                sb.append("    </row>\n")
                rowIndex++
            }

            if (sectionIndex != allSections.lastIndex) rowIndex++
        }

        sb.append("  </sheetData>\n")
        if (merges.isNotEmpty()) {
            sb.append("""  <mergeCells count="${merges.size}">""").append('\n')
            merges.forEach { sb.append("""    <mergeCell ref="$it"/>""").append('\n') }
            sb.append("  </mergeCells>\n")
        }
        sb.append("""  <pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""").append('\n')
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun buildWorksheetXml(table: TableData): String {
        val sb = StringBuilder()
        val columnCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 1)

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
""")

        // Column widths
        sb.append("  <cols>\n")
        for (colIdx in 0 until columnCount) {
            val width = if (table.autoWidth) {
                val headerLen = table.headers.getOrNull(colIdx)?.length ?: 8
                val maxDataLen = table.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
                ((maxOf(headerLen, maxDataLen) + 5).coerceIn(12, 50)).toDouble()
            } else {
                16.0
            }
            sb.append("""    <col min="${colIdx + 1}" max="${colIdx + 1}" width="$width" customWidth="1"/>
""")
        }
        sb.append("  </cols>\n")

        // Sheet Data
        sb.append("  <sheetData>\n")

        var rowIndex = 1

        // 1. Header Row
        val headerStyle = if (table.boldHeader) 1 else 0
        sb.append("""    <row r="$rowIndex" ht="${if (table.boldHeader) 28 else 22}" customHeight="1">
""")
        for (colIdx in 0 until columnCount) {
            val cellRef = "${columnLetter(colIdx)}$rowIndex"
            val text = table.headers.getOrNull(colIdx) ?: "Col ${colIdx + 1}"
            sb.append("""      <c r="$cellRef" s="$headerStyle" t="inlineStr"><is><t>${escapeXml(text)}</t></is></c>
""")
        }
        sb.append("    </row>\n")
        rowIndex++

        // 2. Data Rows
        table.rows.forEachIndexed { dataRowIdx, rowData ->
            val isZebra = dataRowIdx % 2 == 1
            val baseStyle = if (isZebra) 2 else 0

            sb.append("""    <row r="$rowIndex" ht="22" customHeight="1">
""")
            for (colIdx in 0 until columnCount) {
                val cellRef = "${columnLetter(colIdx)}$rowIndex"
                val cellValue = rowData.getOrNull(colIdx)?.trim() ?: ""
                val isNumericCol = table.numericColumns.contains(colIdx)
                val doubleVal = if (table.autoType) parseNumericValue(cellValue) else null

                if (table.autoType && (isNumericCol || doubleVal != null) && doubleVal != null) {
                    sb.append("""      <c r="$cellRef" s="3" t="n"><v>$doubleVal</v></c>
""")
                } else {
                    sb.append("""      <c r="$cellRef" s="$baseStyle" t="inlineStr"><is><t>${escapeXml(cellValue)}</t></is></c>
""")
                }
            }
            sb.append("    </row>\n")
            rowIndex++
        }

        // 3. Optional Total Row with SUM formula
        if (table.includeTotalRow && table.numericColumns.isNotEmpty() && table.rows.isNotEmpty()) {
            val firstDataRow = 2
            val lastDataRow = rowIndex - 1

            sb.append("""    <row r="$rowIndex" ht="24" customHeight="1">
""")
            for (colIdx in 0 until columnCount) {
                val cellRef = "${columnLetter(colIdx)}$rowIndex"
                if (colIdx == 0) {
                    sb.append("""      <c r="$cellRef" s="4" t="inlineStr"><is><t>TOTAL</t></is></c>
""")
                } else if (table.numericColumns.contains(colIdx)) {
                    val colLet = columnLetter(colIdx)
                    val sumFormula = "SUM(${colLet}$firstDataRow:${colLet}$lastDataRow)"
                    sb.append("""      <c r="$cellRef" s="4"><f>$sumFormula</f></c>
""")
                } else {
                    sb.append("""      <c r="$cellRef" s="4" t="inlineStr"><is><t></t></is></c>
""")
                }
            }
            sb.append("    </row>\n")
        }

        sb.append("  </sheetData>\n")
        sb.append("</worksheet>")
        return sb.toString()
    }

    fun shareXlsx(context: Context, xlsxFile: File) {
        if (!xlsxFile.exists()) {
            Toast.makeText(context, "Excel file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, xlsxFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, xlsxFile.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Excel Spreadsheet (.xlsx)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share Excel file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openXlsx(context: Context, xlsxFile: File) {
        if (!xlsxFile.exists()) {
            Toast.makeText(context, "Excel file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, xlsxFile)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open Spreadsheet with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No Excel spreadsheet app found on device", Toast.LENGTH_SHORT).show()
        }
    }
}
