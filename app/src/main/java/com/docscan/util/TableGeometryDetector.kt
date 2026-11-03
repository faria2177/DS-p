package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision table reconstruction engine.
 *
 * Strategy:
 * 1) Detect visible table ruling lines with OpenCV.
 * 2) Recover exact row/column boundaries from the grid.
 * 3) Assign ML Kit word boxes to the cell rectangle they visually belong to.
 * 4) Detect multiple independent tables on one page and nearby section titles.
 *
 * This avoids the old failure mode where every OCR line became:
 * Index | Line N | Text.  Letterhead/footer text is ignored unless it is inside a table.
 */
object TableGeometryDetector {

    data class TableExtractionResult(
        val headers: List<String>,
        val rows: List<List<String>>,
        val isHighConfidence: Boolean
    )

    data class StructuredTable(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val bounds: Rect,
        val isHighConfidence: Boolean
    )

    data class StructuredDocument(
        val documentTitle: String,
        val tables: List<StructuredTable>,
        val isHighConfidence: Boolean
    )

    /**
     * Best API for To Excel. Returns every ruled table found on the page.
     */
    suspend fun extractStructuredDocument(bitmap: Bitmap): StructuredDocument? {
        val lines = TextRecognizerHelper.extractLinesWithWords(bitmap)
        if (lines.isEmpty()) return null

        val ruledTables = try {
            detectRuledTables(bitmap, lines)
        } catch (_: Throwable) {
            emptyList()
        }

        if (ruledTables.isNotEmpty()) {
            val first = ruledTables.first()
            val sectionTitleLine = findNearestTitleLineAbove(first.bounds, lines, bitmap.height)
            val documentTitle = findDocumentTitleAbove(first.bounds, sectionTitleLine, lines, bitmap.height)
            return StructuredDocument(
                documentTitle = documentTitle,
                tables = ruledTables,
                isHighConfidence = ruledTables.all { it.isHighConfidence }
            )
        }

        // Fallback for borderless tables / forms.
        val single = extractSpatialTable(bitmap, lines) ?: return null
        return StructuredDocument(
            documentTitle = "",
            tables = listOf(
                StructuredTable(
                    title = "",
                    headers = single.headers,
                    rows = single.rows,
                    bounds = Rect(0, 0, bitmap.width, bitmap.height),
                    isHighConfidence = single.isHighConfidence
                )
            ),
            isHighConfidence = single.isHighConfidence
        )
    }

    /**
     * Backwards-compatible API used by older flows. If multiple tables exist, emit them
     * one after another as CSV blocks instead of flattening the whole page into line items.
     */
    suspend fun detectTableText(bitmap: Bitmap): String? {
        val doc = extractStructuredDocument(bitmap) ?: return null
        if (doc.tables.isEmpty()) return null

        val sb = StringBuilder()
        doc.tables.forEachIndexed { index, table ->
            if (index > 0) sb.append("\n\n")
            if (table.title.isNotBlank()) {
                sb.append(escapeCsv(table.title)).append("\n")
            }
            sb.append(table.headers.joinToString(",") { escapeCsv(it) }).append("\n")
            table.rows.forEach { row ->
                sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
            }
        }
        return sb.toString().trim()
    }

    /** Backwards-compatible single-table API. */
    suspend fun extractStructuredTable(bitmap: Bitmap): TableExtractionResult? {
        val doc = extractStructuredDocument(bitmap) ?: return null
        val table = doc.tables.firstOrNull() ?: return null
        return TableExtractionResult(table.headers, table.rows, table.isHighConfidence)
    }

    private suspend fun detectRuledTables(
        bitmap: Bitmap,
        lines: List<TextRecognizerHelper.LineWithWordsItem>
    ): List<StructuredTable> {
        val src = Mat()
        val gray = Mat()
        val binary = Mat()
        val horizontal = Mat()
        val vertical = Mat()
        val grid = Mat()
        val connected = Mat()

        try {
            Utils.bitmapToMat(bitmap, src)
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            } else if (src.channels() == 3) {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
            } else {
                src.copyTo(gray)
            }

            Imgproc.adaptiveThreshold(
                gray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                35,
                15.0
            )

            val hKernelWidth = max(18, bitmap.width / 20)
            val vKernelHeight = max(18, bitmap.height / 40)
            val hKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(hKernelWidth.toDouble(), 1.0))
            val vKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, vKernelHeight.toDouble()))

            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, hKernel)
            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, vKernel)
            Core.bitwise_or(horizontal, vertical, grid)

            val joinKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(grid, connected, joinKernel)

            hKernel.release()
            vKernel.release()
            joinKernel.release()

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(connected.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            val minWidth = bitmap.width * 0.28
            val minHeight = bitmap.height * 0.025
            val pageArea = bitmap.width.toDouble() * bitmap.height.toDouble()

            val candidateRects = contours.mapNotNull { contour ->
                val r = Imgproc.boundingRect(contour)
                contour.release()
                val area = r.width.toDouble() * r.height.toDouble()
                if (
                    r.width >= minWidth &&
                    r.height >= minHeight &&
                    area >= pageArea * 0.012 &&
                    r.width > r.height * 1.25
                ) {
                    Rect(r.x, r.y, r.x + r.width, r.y + r.height)
                } else null
            }.sortedBy { it.top }

            val deduped = mutableListOf<Rect>()
            candidateRects.forEach { rect ->
                val duplicate = deduped.any { existing -> intersectionOverUnion(existing, rect) > 0.72f }
                if (!duplicate) deduped.add(rect)
            }

            val result = mutableListOf<StructuredTable>()
            for (rect in deduped) {
                val table = reconstructRuledTable(rect, horizontal, vertical, lines, bitmap)
                if (table != null) result.add(table)
            }
            return result
        } finally {
            src.release()
            gray.release()
            binary.release()
            horizontal.release()
            vertical.release()
            grid.release()
            connected.release()
        }
    }

    private fun reconstructRuledTable(
        rect: Rect,
        horizontalMask: Mat,
        verticalMask: Mat,
        lines: List<TextRecognizerHelper.LineWithWordsItem>,
        bitmap: Bitmap
    ): StructuredTable? {
        val width = rect.width().coerceAtLeast(1)
        val height = rect.height().coerceAtLeast(1)
        if (rect.left < 0 || rect.top < 0 || rect.right > bitmap.width || rect.bottom > bitmap.height) return null

        val cvRect = org.opencv.core.Rect(rect.left, rect.top, width, height)
        val hRoi = horizontalMask.submat(cvRect)
        val vRoi = verticalMask.submat(cvRect)

        try {
            val xPositions = detectVerticalBoundaries(vRoi, width, height)
            val yPositions = detectHorizontalBoundaries(hRoi, width, height)

            if (xPositions.size < 3 || yPositions.size < 3) return null
            if (xPositions.size > 20 || yPositions.size > 120) return null

            val colCount = xPositions.size - 1
            val rowCount = yPositions.size - 1
            if (colCount < 2 || rowCount < 2) return null

            val rows = mutableListOf<List<String>>()
            for (r in 0 until rowCount) {
                val row = mutableListOf<String>()
                val top = rect.top + yPositions[r]
                val bottom = rect.top + yPositions[r + 1]
                for (c in 0 until colCount) {
                    val left = rect.left + xPositions[c]
                    val right = rect.left + xPositions[c + 1]
                    row.add(textInsideCell(Rect(left, top, right, bottom), lines))
                }
                rows.add(row)
            }

            val nonEmpty = rows.filter { row -> row.any { it.isNotBlank() } }
            if (nonEmpty.size < 2) return null

            var headers = nonEmpty.first()
            var dataRows = nonEmpty.drop(1)

            // Keep the exact visual column count and replace truly blank header cells with neutral names.
            headers = headers.mapIndexed { index, value ->
                value.trim().ifBlank { "Column ${index + 1}" }
            }

            val title = findNearestTitleLineAbove(rect, lines, bitmap.height)?.text?.trim().orEmpty()
            return StructuredTable(
                title = title,
                headers = headers,
                rows = dataRows,
                bounds = rect,
                isHighConfidence = xPositions.size >= 4 && yPositions.size >= 4
            )
        } finally {
            hRoi.release()
            vRoi.release()
        }
    }

    private fun detectVerticalBoundaries(mask: Mat, width: Int, height: Int): List<Int> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val centers = mutableListOf<Int>()
        val maxLineWidth = max(10, (width * 0.045f).toInt())
        contours.forEach { c ->
            val r = Imgproc.boundingRect(c)
            c.release()
            if (r.height >= height * 0.42 && r.width <= maxLineWidth) {
                centers.add(r.x + r.width / 2)
            }
        }

        val clustered = clusterPositions(centers, max(4, (width * 0.012f).toInt())).toMutableList()
        addBoundaryIfMissing(clustered, 0, max(8, width / 35))
        addBoundaryIfMissing(clustered, width - 1, max(8, width / 35))
        return clustered.sorted()
    }

    private fun detectHorizontalBoundaries(mask: Mat, width: Int, height: Int): List<Int> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val centers = mutableListOf<Int>()
        val maxLineHeight = max(10, (height * 0.14f).toInt())
        contours.forEach { c ->
            val r = Imgproc.boundingRect(c)
            c.release()
            if (r.width >= width * 0.24 && r.height <= maxLineHeight) {
                centers.add(r.y + r.height / 2)
            }
        }

        val clustered = clusterPositions(centers, max(5, (height * 0.055f).toInt())).toMutableList()
        addBoundaryIfMissing(clustered, 0, max(8, height / 20))
        addBoundaryIfMissing(clustered, height - 1, max(8, height / 20))
        return clustered.sorted()
    }

    private fun clusterPositions(values: List<Int>, tolerance: Int): List<Int> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val groups = mutableListOf<MutableList<Int>>()
        for (value in sorted) {
            val group = groups.lastOrNull()
            if (group != null && abs(value - group.average()) <= tolerance) {
                group.add(value)
            } else {
                groups.add(mutableListOf(value))
            }
        }
        return groups.map { it.average().toInt() }
    }

    private fun addBoundaryIfMissing(values: MutableList<Int>, boundary: Int, tolerance: Int) {
        if (values.none { abs(it - boundary) <= tolerance }) values.add(boundary)
    }

    private fun textInsideCell(
        cell: Rect,
        lines: List<TextRecognizerHelper.LineWithWordsItem>
    ): String {
        val words = lines.flatMap { line -> line.words }
            .filter { word ->
                val cx = word.rect.centerX()
                val cy = word.rect.centerY()
                cx >= cell.left && cx <= cell.right && cy >= cell.top && cy <= cell.bottom
            }
            .sortedWith(compareBy<TextRecognizerHelper.WordBoxItem> { it.rect.centerY() }.thenBy { it.rect.left })

        if (words.isEmpty()) return ""

        val avgHeight = words.map { it.rect.height().coerceAtLeast(1) }.average().coerceAtLeast(8.0)
        val lineTolerance = avgHeight * 0.55
        val grouped = mutableListOf<MutableList<TextRecognizerHelper.WordBoxItem>>()

        for (word in words) {
            val last = grouped.lastOrNull()
            val center = last?.map { it.rect.centerY() }?.average()
            if (last != null && center != null && abs(word.rect.centerY() - center) <= lineTolerance) {
                last.add(word)
            } else {
                grouped.add(mutableListOf(word))
            }
        }

        return grouped.joinToString("\n") { lineWords ->
            lineWords.sortedBy { it.rect.left }
                .joinToString(" ") { it.text.trim() }
                .replace(Regex("\\s+"), " ")
                .trim()
        }.trim()
    }

    private fun findNearestTitleLineAbove(
        tableRect: Rect,
        lines: List<TextRecognizerHelper.LineWithWordsItem>,
        imageHeight: Int
    ): TextRecognizerHelper.LineWithWordsItem? {
        val maxGap = max(60, (imageHeight * 0.09f).toInt())
        return lines.asSequence()
            .filter { it.rect.bottom <= tableRect.top + 4 }
            .filter { tableRect.top - it.rect.bottom in 0..maxGap }
            .filter { horizontalOverlapRatio(it.rect, tableRect) >= 0.18f || tableRect.contains(it.rect.centerX(), tableRect.top) }
            .filter { it.text.trim().length in 2..120 }
            .sortedBy { tableRect.top - it.rect.bottom }
            .firstOrNull()
    }

    private fun findDocumentTitleAbove(
        firstTable: Rect,
        sectionTitle: TextRecognizerHelper.LineWithWordsItem?,
        lines: List<TextRecognizerHelper.LineWithWordsItem>,
        imageHeight: Int
    ): String {
        val ceiling = sectionTitle?.rect?.top ?: firstTable.top
        val maxGap = max(90, (imageHeight * 0.16f).toInt())
        val candidate = lines.asSequence()
            .filter { it.rect.bottom <= ceiling }
            .filter { ceiling - it.rect.bottom in 0..maxGap }
            .filter { horizontalOverlapRatio(it.rect, firstTable) >= 0.20f || it.rect.centerX() in firstTable.left..firstTable.right }
            .filter { it.text.trim().length in 4..160 }
            .sortedBy { ceiling - it.rect.bottom }
            .firstOrNull()
        return candidate?.text?.trim().orEmpty()
    }

    private fun horizontalOverlapRatio(a: Rect, b: Rect): Float {
        val overlap = max(0, min(a.right, b.right) - max(a.left, b.left))
        val denom = min(a.width().coerceAtLeast(1), b.width().coerceAtLeast(1))
        return overlap.toFloat() / denom.toFloat()
    }

    private fun intersectionOverUnion(a: Rect, b: Rect): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val w = max(0, right - left)
        val h = max(0, bottom - top)
        val intersection = w.toLong() * h.toLong()
        if (intersection <= 0) return 0f
        val union = a.width().toLong() * a.height().toLong() + b.width().toLong() * b.height().toLong() - intersection
        return if (union <= 0) 0f else intersection.toFloat() / union.toFloat()
    }

    /** Borderless-table fallback using OCR box alignment. */
    private fun extractSpatialTable(
        bitmap: Bitmap,
        lines: List<TextRecognizerHelper.LineWithWordsItem>
    ): TableExtractionResult? {
        val allWords = lines.flatMap { it.words }
        if (allWords.size < 3) return null

        val avgLineHeight = lines.map { it.rect.height() }.filter { it > 0 }.average().takeIf { it > 0 } ?: 20.0
        val sortedLines = lines.sortedBy { it.rect.centerY() }

        val rowGroups = mutableListOf<MutableList<TextRecognizerHelper.LineWithWordsItem>>()
        for (line in sortedLines) {
            val lastRow = rowGroups.lastOrNull()
            val lastCenter = lastRow?.map { it.rect.centerY() }?.average()
            if (lastRow != null && lastCenter != null && abs(line.rect.centerY() - lastCenter) < avgLineHeight * 0.55) {
                lastRow.add(line)
            } else {
                rowGroups.add(mutableListOf(line))
            }
        }
        if (rowGroups.isEmpty()) return null

        val bitmapWidth = bitmap.width.takeIf { it > 0 } ?: 1000
        val gapThreshold = (bitmapWidth * 0.022f).coerceIn(10f, 40f)
        val wordsByLeft = allWords.sortedBy { it.rect.left }

        data class ColumnBand(var left: Int, var right: Int)
        val rawBands = mutableListOf<ColumnBand>()
        for (word in wordsByLeft) {
            val current = rawBands.lastOrNull()
            if (current != null && word.rect.left - current.right < gapThreshold) {
                current.right = max(current.right, word.rect.right)
            } else {
                rawBands.add(ColumnBand(word.rect.left, word.rect.right))
            }
        }

        val bands = mutableListOf<ColumnBand>()
        for (band in rawBands) {
            val last = bands.lastOrNull()
            if (last != null && band.left - last.right < gapThreshold * 0.8f) {
                last.right = max(last.right, band.right)
            } else {
                bands.add(band)
            }
        }

        if (bands.size < 2) return null

        fun bandIndexFor(rect: Rect): Int {
            val cx = rect.centerX()
            var bestIdx = 0
            var bestDist = Int.MAX_VALUE
            bands.forEachIndexed { idx, band ->
                val dist = when {
                    cx < band.left -> band.left - cx
                    cx > band.right -> cx - band.right
                    else -> 0
                }
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = idx
                }
            }
            return bestIdx
        }

        val grid = mutableListOf<MutableList<String>>()
        for (row in rowGroups) {
            val cells = MutableList(bands.size) { "" }
            for (word in row.flatMap { it.words }.sortedBy { it.rect.left }) {
                val col = bandIndexFor(word.rect)
                cells[col] = if (cells[col].isBlank()) word.text.trim() else "${cells[col]} ${word.text.trim()}"
            }
            if (cells.count { it.isNotBlank() } >= 2) grid.add(cells)
        }

        if (grid.size < 2) return null
        val headers = grid.first().mapIndexed { i, v -> v.ifBlank { "Column ${i + 1}" } }
        return TableExtractionResult(headers, grid.drop(1), true)
    }

    private fun escapeCsv(text: String): String {
        val clean = text.replace("\n", " ").trim()
        return if (clean.contains(",") || clean.contains("\"") || clean.contains(";")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else clean
    }
}
