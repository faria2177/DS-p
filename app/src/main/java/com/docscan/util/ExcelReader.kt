package com.docscan.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * High-performance standalone OpenXML Spreadsheet (.xlsx) Parser & Reader.
 * Reads workbook sheets, shared strings, cell values, formulas, numbers, and headers
 * without requiring Microsoft Excel or third-party bloated libraries.
 */
object ExcelReader {

    data class ParsedWorkbook(
        val title: String,
        val sheets: List<ParsedSheet>
    )

    data class ParsedSheet(
        val name: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val rowCount: Int,
        val columnCount: Int
    )

    /**
     * Reads and parses an XLSX or CSV file from an Android File object.
     */
    suspend fun readXlsxFile(context: Context, xlsxFile: File): ParsedWorkbook = withContext(Dispatchers.IO) {
        try {
            if (xlsxFile.name.endsWith(".csv", ignoreCase = true) || xlsxFile.name.endsWith(".tsv", ignoreCase = true)) {
                xlsxFile.inputStream().use { stream ->
                    parseCsvStream(stream, xlsxFile.nameWithoutExtension)
                }
            } else {
                xlsxFile.inputStream().use { stream ->
                    parseXlsxStream(stream, xlsxFile.nameWithoutExtension)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedWorkbook(
                title = xlsxFile.nameWithoutExtension,
                sheets = listOf(
                    ParsedSheet(
                        name = "Error",
                        headers = listOf("Message"),
                        rows = listOf(listOf("Could not read Excel file: ${e.localizedMessage}")),
                        rowCount = 1,
                        columnCount = 1
                    )
                )
            )
        }
    }

    /**
     * Reads and parses an XLSX or CSV file from an Android content Uri.
     */
    suspend fun readXlsxUri(context: Context, uri: Uri, title: String = "Spreadsheet"): ParsedWorkbook = withContext(Dispatchers.IO) {
        try {
            val isCsv = title.endsWith(".csv", ignoreCase = true) ||
                    uri.toString().endsWith(".csv", ignoreCase = true) ||
                    (context.contentResolver.getType(uri)?.contains("csv", ignoreCase = true) == true)
            if (isCsv) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    parseCsvStream(stream, title.removeSuffix(".csv"))
                } ?: ParsedWorkbook(title, emptyList())
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    parseXlsxStream(stream, title)
                } ?: ParsedWorkbook(title, emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedWorkbook(
                title = title,
                sheets = listOf(
                    ParsedSheet(
                        name = "Error",
                        headers = listOf("Message"),
                        rows = listOf(listOf("Could not read Excel file: ${e.localizedMessage}")),
                        rowCount = 1,
                        columnCount = 1
                    )
                )
            )
        }
    }

    private fun parseXlsxStream(inputStream: InputStream, title: String): ParsedWorkbook {
        val sheetXmls = mutableMapOf<String, String>()
        val sheetNames = mutableListOf<String>()
        var sharedStringsXml: String? = null
        var workbookXml: String? = null

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/workbook.xml" -> {
                        workbookXml = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    name == "xl/sharedStrings.xml" -> {
                        sharedStringsXml = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        val sheetXml = zis.bufferedReader(Charsets.UTF_8).readText()
                        sheetXmls[name] = sheetXml
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Parse shared strings table
        val sharedStrings = parseSharedStrings(sharedStringsXml)

        // Parse sheet names from workbook.xml
        if (workbookXml != null) {
            sheetNames.addAll(parseSheetNames(workbookXml!!))
        }

        val parsedSheets = mutableListOf<ParsedSheet>()
        val sortedSheetKeys = sheetXmls.keys.sorted()

        sortedSheetKeys.forEachIndexed { index, key ->
            val xml = sheetXmls[key] ?: return@forEachIndexed
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
            val sheet = parseWorksheetXml(xml, sharedStrings, sheetName)
            parsedSheets.add(sheet)
        }

        if (parsedSheets.isEmpty()) {
            parsedSheets.add(
                ParsedSheet(
                    name = "Sheet 1",
                    headers = listOf("Info"),
                    rows = listOf(listOf("Empty spreadsheet")),
                    rowCount = 0,
                    columnCount = 0
                )
            )
        }

        return ParsedWorkbook(title = title, sheets = parsedSheets)
    }

    private fun parseSharedStrings(xml: String?): List<String> {
        if (xml.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inSi = false
            var currentStr = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "si") {
                            inSi = true
                            currentStr = StringBuilder()
                        } else if (tag == "t" && inSi) {
                            val text = parser.nextText()
                            currentStr.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "si") {
                            inSi = false
                            list.add(currentStr.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseSheetNames(xml: String): List<String> {
        val names = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    if (!name.isNullOrBlank()) {
                        names.add(name)
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return names
    }

    private fun parseWorksheetXml(
        xml: String,
        sharedStrings: List<String>,
        sheetName: String
    ): ParsedSheet {
        val allRows = mutableListOf<MutableList<String>>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentRow = mutableListOf<String>()
            var currentCellType = ""
            var currentCellValue = ""
            var currentCellRef = ""
            var lastColIdx = -1

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "row" -> {
                                currentRow = mutableListOf()
                                lastColIdx = -1
                            }
                            "c" -> {
                                currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                currentCellValue = ""
                            }
                            "v" -> {
                                currentCellValue = parser.nextText()
                            }
                            "t" -> {
                                currentCellValue = parser.nextText()
                            }
                            "is" -> {
                                // inline string
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "c" -> {
                                val colIdx = columnLetterToIndex(currentCellRef)
                                // Fill empty skipped columns if any
                                if (colIdx > lastColIdx + 1) {
                                    for (i in (lastColIdx + 1) until colIdx) {
                                        currentRow.add("")
                                    }
                                }
                                lastColIdx = colIdx

                                val cellText = when (currentCellType) {
                                    "s" -> {
                                        val strIdx = currentCellValue.toIntOrNull()
                                        if (strIdx != null && strIdx in sharedStrings.indices) {
                                            sharedStrings[strIdx]
                                        } else {
                                            currentCellValue
                                        }
                                    }
                                    "inlineStr", "str" -> currentCellValue
                                    else -> currentCellValue
                                }
                                currentRow.add(cellText)
                            }
                            "row" -> {
                                if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                                    allRows.add(currentRow)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (allRows.isEmpty()) {
            return ParsedSheet(
                name = sheetName,
                headers = listOf("Column 1"),
                rows = emptyList(),
                rowCount = 0,
                columnCount = 1
            )
        }

        val rawHeaders = allRows.first()
        val dataRows = if (allRows.size > 1) allRows.subList(1, allRows.size) else emptyList()
        val maxCols = maxOf(rawHeaders.size, dataRows.maxOfOrNull { it.size } ?: 1)

        val paddedHeaders = rawHeaders + List((maxCols - rawHeaders.size).coerceAtLeast(0)) { "Col ${it + rawHeaders.size + 1}" }
        val paddedRows = dataRows.map { r -> r + List((maxCols - r.size).coerceAtLeast(0)) { "" } }

        return ParsedSheet(
            name = sheetName,
            headers = paddedHeaders,
            rows = paddedRows,
            rowCount = paddedRows.size,
            columnCount = maxCols
        )
    }

    private fun columnLetterToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var result = 0
        for (c in letters) {
            result = result * 26 + (c - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun parseCsvStream(inputStream: InputStream, title: String): ParsedWorkbook {
        val allRows = mutableListOf<List<String>>()
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8))
        reader.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val row = parseCsvLine(line)
                allRows.add(row)
            }
        }
        if (allRows.isEmpty()) {
            return ParsedWorkbook(
                title = title,
                sheets = listOf(
                    ParsedSheet(
                        name = "Sheet 1",
                        headers = listOf("Data"),
                        rows = listOf(listOf("Empty CSV file")),
                        rowCount = 0,
                        columnCount = 1
                    )
                )
            )
        }
        val headers = allRows.first()
        val dataRows = if (allRows.size > 1) allRows.subList(1, allRows.size) else emptyList()
        val maxCols = maxOf(headers.size, dataRows.maxOfOrNull { it.size } ?: 1)
        val paddedHeaders = headers + List((maxCols - headers.size).coerceAtLeast(0)) { "Col ${it + headers.size + 1}" }
        val paddedRows = dataRows.map { r -> r + List((maxCols - r.size).coerceAtLeast(0)) { "" } }
        return ParsedWorkbook(
            title = title,
            sheets = listOf(
                ParsedSheet(
                    name = "Data",
                    headers = paddedHeaders,
                    rows = paddedRows,
                    rowCount = paddedRows.size,
                    columnCount = maxCols
                )
            )
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
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
                (c == ',' || c == '\t' || c == ';') && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }
}
