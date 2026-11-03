package com.docscan.util

/**
 * Advanced table structure detection and parsing for PDFs, Scanned Images, and OCR text.
 * Extracts 2D tabular matrices, detects headers, types, key-value maps, and CSV structures.
 */
object PdfTableExtractor {

    data class ExtractedTable(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val isNumericColumn: List<Boolean> = emptyList()
    )

    /**
     * Parses raw OCR text or document text into structured tables.
     */
    fun extractTablesFromText(text: String, defaultTitle: String = "Table"): List<ExtractedTable> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val tables = mutableListOf<ExtractedTable>()

        // 1. Check for Markdown / Pipe-delimited tables
        val pipeLines = lines.filter { it.contains("|") && !it.startsWith("#") }
        if (pipeLines.size >= 2) {
            val tableRows = mutableListOf<List<String>>()
            for (line in pipeLines) {
                // Skip markdown separator rows like "---|---|---" or ":--|--:"
                if (line.all { it == '-' || it == '|' || it == ':' || it.isWhitespace() }) continue
                val trimmedLine = line.trim()
                var cells = trimmedLine.split("|").map { it.trim() }
                // Drop leading/trailing empty artifacts only when the line is actually
                // wrapped in pipes (e.g. "| a | b |") — never drop a genuinely empty cell.
                if (trimmedLine.startsWith("|") && cells.isNotEmpty()) cells = cells.drop(1)
                if (trimmedLine.endsWith("|") && cells.isNotEmpty()) cells = cells.dropLast(1)
                if (cells.isNotEmpty()) {
                    tableRows.add(cells)
                }
            }
            if (tableRows.isNotEmpty()) {
                val headers = tableRows.first()
                val data = if (tableRows.size > 1) tableRows.subList(1, tableRows.size) else emptyList()
                val maxCols = maxOf(headers.size, data.maxOfOrNull { it.size } ?: 0)
                val paddedHeaders = headers + List((maxCols - headers.size).coerceAtLeast(0)) { "Col ${it + headers.size + 1}" }
                val paddedData = data.map { row -> row + List((maxCols - row.size).coerceAtLeast(0)) { "" } }
                tables.add(ExtractedTable(defaultTitle, paddedHeaders, paddedData))
                return tables
            }
        }

        // 2. Check for Tab-separated values (TSV)
        val tsvLines = lines.filter { it.contains("\t") }
        if (tsvLines.size >= 2) {
            val tableRows = tsvLines.map { line -> line.split("\t").map { it.trim() } }
            val headers = tableRows.first()
            val data = if (tableRows.size > 1) tableRows.subList(1, tableRows.size) else emptyList()
            tables.add(ExtractedTable(defaultTitle, headers, data))
            return tables
        }

        // 3. Check for Comma-separated values (CSV)
        val csvLines = lines.filter { it.contains(",") && it.count { c -> c == ',' } >= 2 }
        if (csvLines.size >= 2) {
            val tableRows = csvLines.map { parseCsvLine(it) }
            val headers = tableRows.first()
            val data = if (tableRows.size > 1) tableRows.subList(1, tableRows.size) else emptyList()
            tables.add(ExtractedTable(defaultTitle, headers, data))
            return tables
        }

        return emptyList()
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
}
