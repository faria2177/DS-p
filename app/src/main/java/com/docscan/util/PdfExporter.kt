package com.docscan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.docscan.data.model.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

data class PdfExportConfig(
    val title: String = "Document",
    val pageSize: PageSize = PageSize.A4,
    val includePageNumbers: Boolean = true,
    val watermarkText: String? = null,
    val quality: Int = 85
)

enum class PageSize(val widthPt: Int, val heightPt: Int, val displayName: String) {
    A4(595, 842, "A4 (Standard)"),
    LETTER(612, 792, "US Letter"),
    LEGAL(612, 1008, "US Legal"),
    A5(420, 595, "A5 (Pocket)"),
    COMPACT(500, 700, "Compact"),
    AUTO(0, 0, "Fit to Image Size")
}

enum class TextToPdfTheme(
    val title: String,
    val bgColor: Int,
    val textColor: Int,
    val titleColor: Int,
    val accentColor: Int,
    val borderColor: Int
) {
    CLASSIC("Classic White", Color.WHITE, Color.rgb(30, 30, 30), Color.rgb(15, 23, 42), Color.rgb(0, 122, 255), Color.rgb(226, 232, 240)),
    WARM_CREAM("Warm Cream", Color.rgb(252, 248, 238), Color.rgb(45, 38, 30), Color.rgb(120, 60, 20), Color.rgb(180, 83, 9), Color.rgb(230, 220, 200)),
    CLEAN_SLATE("Clean Slate", Color.rgb(245, 247, 250), Color.rgb(33, 43, 54), Color.rgb(15, 23, 42), Color.rgb(14, 165, 233), Color.rgb(203, 213, 225)),
    EXECUTIVE_NAVY("Executive Navy", Color.rgb(248, 250, 252), Color.rgb(15, 23, 42), Color.rgb(30, 58, 138), Color.rgb(37, 99, 235), Color.rgb(203, 213, 225)),
    MODERN_DARK("Modern Dark", Color.rgb(30, 32, 38), Color.rgb(240, 240, 245), Color.rgb(255, 255, 255), Color.rgb(45, 186, 141), Color.rgb(60, 64, 75))
}

data class TextToPdfOptions(
    val pageSize: PageSize = PageSize.A4,
    val isLandscape: Boolean = false,
    val fontSize: Float = 13f,
    val lineSpacingMultiplier: Float = 1.35f,
    val fontFamily: String = "SansSerif", // "SansSerif", "Serif", "Monospace"
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textAlign: String = "Left", // "Left", "Center", "Right"
    val theme: TextToPdfTheme = TextToPdfTheme.CLASSIC,
    val margin: Float = 40f, // 24f Compact, 40f Standard, 56f Wide
    val includeHeader: Boolean = true,
    val headerText: String = "",
    val includeFooter: Boolean = true,
    val includePageNumbers: Boolean = true,
    val includeDate: Boolean = true,
    val customFooterText: String = "",
    val watermarkText: String? = null,
    val showPageBorder: Boolean = false,
    val author: String = ""
)

object PdfExporter {

    suspend fun generatePdf(
        context: Context,
        documentTitle: String,
        pages: List<PageEntity>,
        config: PdfExportConfig = PdfExportConfig(title = documentTitle)
    ): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val effectiveConfig = if (config == PdfExportConfig(title = documentTitle)) {
            PdfSettings.withDefaults(context, documentTitle)
        } else config

        // NID two-side scans are stored as one pre-composed A4 image. Preserve
        // that layout exactly during PDF export: no extra margins, footer or
        // page number, and always use an A4 page.
        val isNidComposite = pages.size == 1 && (
            pages.first().notes == "NID_COMPOSITE_A4" ||
                documentTitle.startsWith("NID Card", ignoreCase = true) ||
                documentTitle.startsWith("ID Card", ignoreCase = true)
            )

        val pdfDocument = PdfDocument()
        val pdfDir = FileUtils.getPdfsDir(context)
        val sanitizedTitle = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val pdfFile = File(pdfDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        try {
            var pagesRendered = 0
            val totalPages = pages.size

            pages.forEachIndexed { index, pageEntity ->
                val originalBitmap = FileUtils.loadBitmap(pageEntity.processedImagePath) ?: return@forEachIndexed
                // Lower quality presets must meaningfully reduce embedded bitmap pixels.
                val maxDimension = when (effectiveConfig.quality.coerceIn(1, 100)) {
                    in 96..100 -> Int.MAX_VALUE
                    in 85..95 -> 2600
                    in 65..84 -> 1700
                    else -> 1050
                }
                val originalMax = maxOf(originalBitmap.width, originalBitmap.height)
                val bitmap = if (!isNidComposite && originalMax > maxDimension) {
                    val ratio = maxDimension.toFloat() / originalMax
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * ratio).toInt().coerceAtLeast(1),
                        (originalBitmap.height * ratio).toInt().coerceAtLeast(1),
                        true
                    ).also { originalBitmap.recycle() }
                } else originalBitmap

                try {
                    val (pageWidth, pageHeight) = if (isNidComposite) {
                        Pair(PageSize.A4.widthPt, PageSize.A4.heightPt)
                    } else {
                        when (effectiveConfig.pageSize) {
                            PageSize.A4 -> Pair(PageSize.A4.widthPt, PageSize.A4.heightPt)
                            PageSize.LETTER -> Pair(PageSize.LETTER.widthPt, PageSize.LETTER.heightPt)
                            PageSize.LEGAL -> Pair(PageSize.LEGAL.widthPt, PageSize.LEGAL.heightPt)
                            PageSize.A5 -> Pair(PageSize.A5.widthPt, PageSize.A5.heightPt)
                            PageSize.COMPACT -> Pair(PageSize.COMPACT.widthPt, PageSize.COMPACT.heightPt)
                            PageSize.AUTO -> Pair(bitmap.width, bitmap.height)
                        }
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Background
                    canvas.drawColor(Color.WHITE)

                    val margin = when {
                        isNidComposite -> 0f
                        effectiveConfig.pageSize == PageSize.AUTO -> 0f
                        else -> 24f
                    }
                    val footerSpace = when {
                        isNidComposite -> 0f
                        effectiveConfig.includePageNumbers && effectiveConfig.pageSize != PageSize.AUTO -> 32f
                        else -> margin
                    }

                    val availableWidth = pageWidth - (margin * 2)
                    val availableHeight = pageHeight - margin - footerSpace

                    val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val availRatio = availableWidth / availableHeight

                    val drawWidth: Float
                    val drawHeight: Float

                    if (bmpRatio > availRatio) {
                        drawWidth = availableWidth
                        drawHeight = availableWidth / bmpRatio
                    } else {
                        drawHeight = availableHeight
                        drawWidth = availableHeight * bmpRatio
                    }

                    val left = margin + (availableWidth - drawWidth) / 2f
                    val top = margin + (availableHeight - drawHeight) / 2f

                    val destRect = RectF(left, top, left + drawWidth, top + drawHeight)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, null, destRect, paint)

                    // Optional PDF-level watermark
                    effectiveConfig.watermarkText?.let { wmText ->
                        if (wmText.isNotBlank()) {
                            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0x33444444
                                textSize = (pageWidth / 18f).coerceIn(18f, 48f)
                                textAlign = Paint.Align.CENTER
                                isFakeBoldText = true
                            }
                            canvas.save()
                            canvas.rotate(-35f, pageWidth / 2f, pageHeight / 2f)
                            canvas.drawText(wmText, pageWidth / 2f, pageHeight / 2f, wmPaint)
                            canvas.restore()
                        }
                    }

                    // Page Number footer
                    if (!isNidComposite && effectiveConfig.includePageNumbers && effectiveConfig.pageSize != PageSize.AUTO) {
                        val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.DKGRAY
                            textSize = 10f
                            textAlign = Paint.Align.CENTER
                        }
                        val footerY = pageHeight - 12f
                        canvas.drawText("${index + 1} / $totalPages", pageWidth / 2f, footerY, pageNumPaint)
                    }

                    pdfDocument.finishPage(page)
                    pagesRendered++
                } finally {
                    bitmap.recycle()
                }
            }

            if (pagesRendered == 0) {
                pdfDocument.close()
                return@withContext null
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun sharePdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Document PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open PDF with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer app found on device", Toast.LENGTH_SHORT).show()
        }
    }

    fun printPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
            if (printManager != null) {
                val printAdapter = object : android.print.PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: android.print.PrintAttributes?,
                        newAttributes: android.print.PrintAttributes?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        try {
                            java.io.FileInputStream(pdfFile).use { input ->
                                java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
                printManager.print(pdfFile.nameWithoutExtension, printAdapter, android.print.PrintAttributes.Builder().build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Printing error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareImages(context: Context, imagePaths: List<String>, title: String) {
        val files = imagePaths.map { File(it) }.filter { it.exists() }
        if (files.isEmpty()) {
            Toast.makeText(context, "No images found to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val uriList = ArrayList<Uri>()

            files.forEach { file ->
                uriList.add(FileProvider.getUriForFile(context, authority, file))
            }

            val shareIntent = if (uriList.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uriList.first())
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(shareIntent, "Share Scanned Pages")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share images: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun generatePdfFromText(
        context: Context,
        title: String,
        textContent: String,
        options: TextToPdfOptions = TextToPdfOptions()
    ): File? = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pdfDir = FileUtils.getPdfsDir(context)
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Text_Document" }
        val pdfFile = File(pdfDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        try {
            val baseWidth = if (options.pageSize.widthPt > 0) options.pageSize.widthPt else 595
            val baseHeight = if (options.pageSize.heightPt > 0) options.pageSize.heightPt else 842

            val pageWidth = if (options.isLandscape) maxOf(baseWidth, baseHeight) else minOf(baseWidth, baseHeight)
            val pageHeight = if (options.isLandscape) minOf(baseWidth, baseHeight) else maxOf(baseWidth, baseHeight)

            val margin = options.margin
            val contentWidth = pageWidth - (margin * 2)

            val baseTypeface = when (options.fontFamily) {
                "Serif" -> Typeface.SERIF
                "Monospace" -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
            val styleFlag = when {
                options.isBold && options.isItalic -> Typeface.BOLD_ITALIC
                options.isBold -> Typeface.BOLD
                options.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val regularTypeface = Typeface.create(baseTypeface, styleFlag)
            val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)

            val theme = options.theme
            val baseFontSize = options.fontSize
            val lineSpacing = baseFontSize * options.lineSpacingMultiplier

            val paintAlign = when (options.textAlign) {
                "Center" -> Paint.Align.CENTER
                "Right" -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }

            // Paints
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = baseFontSize
                typeface = regularTypeface
                textAlign = paintAlign
            }

            val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.titleColor
                textSize = baseFontSize * 1.5f
                typeface = boldTypeface
                textAlign = paintAlign
            }

            val h2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.titleColor
                textSize = baseFontSize * 1.25f
                typeface = boldTypeface
                textAlign = paintAlign
            }

            val h3Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentColor
                textSize = baseFontSize * 1.1f
                typeface = boldTypeface
                textAlign = paintAlign
            }

            val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = baseFontSize
                typeface = regularTypeface
            }

            val headerFooterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                alpha = 160
                textSize = 9f
                typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            }

            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.borderColor
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.borderColor
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }

            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                alpha = 32
                textSize = 48f
                typeface = boldTypeface
                textAlign = Paint.Align.CENTER
            }

            // Parse document elements
            data class RenderItem(
                val text: String,
                val type: String, // "H1", "H2", "H3", "BODY", "BULLET", "DIVIDER", "SPACER"
                val height: Float,
                val indent: Float = 0f
            )

            val renderItems = mutableListOf<RenderItem>()
            val rawLines = textContent.split("\n")

            for (rawLine in rawLines) {
                val trimmed = rawLine.trim()
                when {
                    trimmed.isEmpty() -> {
                        renderItems.add(RenderItem("", "SPACER", lineSpacing * 0.7f))
                    }
                    trimmed.startsWith("---") || trimmed.startsWith("===") || trimmed.startsWith("___") -> {
                        renderItems.add(RenderItem("", "DIVIDER", lineSpacing * 1.2f))
                    }
                    trimmed.startsWith("# ") -> {
                        val text = trimmed.substring(2).trim()
                        renderItems.add(RenderItem(text, "H1", lineSpacing * 1.8f))
                    }
                    trimmed.startsWith("## ") -> {
                        val text = trimmed.substring(3).trim()
                        renderItems.add(RenderItem(text, "H2", lineSpacing * 1.5f))
                    }
                    trimmed.startsWith("### ") -> {
                        val text = trimmed.substring(4).trim()
                        renderItems.add(RenderItem(text, "H3", lineSpacing * 1.3f))
                    }
                    trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("+ ") -> {
                        val bulletContent = trimmed.substring(2).trim()
                        // Word wrap bullet content
                        val words = bulletContent.split(" ")
                        var curLine = StringBuilder()
                        val maxW = contentWidth - 16f
                        var isFirst = true
                        for (w in words) {
                            val test = if (curLine.isEmpty()) w else "$curLine $w"
                            if (bodyPaint.measureText(test) <= maxW) {
                                curLine = StringBuilder(test)
                            } else {
                                if (curLine.isNotEmpty()) {
                                    renderItems.add(RenderItem(if (isFirst) "•  $curLine" else "    $curLine", "BULLET", lineSpacing, if (isFirst) 0f else 14f))
                                    isFirst = false
                                }
                                curLine = StringBuilder(w)
                            }
                        }
                        if (curLine.isNotEmpty()) {
                            renderItems.add(RenderItem(if (isFirst) "•  $curLine" else "    $curLine", "BULLET", lineSpacing, if (isFirst) 0f else 14f))
                        }
                    }
                    else -> {
                        // Regular wrapped text
                        val words = rawLine.split(" ")
                        var curLine = StringBuilder()
                        for (w in words) {
                            val test = if (curLine.isEmpty()) w else "$curLine $w"
                            if (bodyPaint.measureText(test) <= contentWidth) {
                                curLine = StringBuilder(test)
                            } else {
                                if (curLine.isNotEmpty()) {
                                    renderItems.add(RenderItem(curLine.toString(), "BODY", lineSpacing))
                                }
                                curLine = StringBuilder(w)
                            }
                        }
                        if (curLine.isNotEmpty()) {
                            renderItems.add(RenderItem(curLine.toString(), "BODY", lineSpacing))
                        }
                    }
                }
            }

            val topHeaderSpace = if (options.includeHeader) 40f else 0f
            val bottomFooterSpace = if (options.includeFooter) 35f else 0f
            val usableHeight = pageHeight - (margin * 2) - topHeaderSpace - bottomFooterSpace

            // Calculate total pages
            var estimatedPages = 1
            var pageCurrentH = 0f
            for (item in renderItems) {
                if (pageCurrentH + item.height > usableHeight) {
                    estimatedPages++
                    pageCurrentH = item.height
                } else {
                    pageCurrentH += item.height
                }
            }

            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            var itemIdx = 0
            var currentPage = 1

            while (itemIdx < renderItems.size || (renderItems.isEmpty() && currentPage == 1)) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), currentPage).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Background
                canvas.drawColor(theme.bgColor)

                // Optional Decorative Page Border
                if (options.showPageBorder) {
                    val borderInset = 16f
                    canvas.drawRoundRect(
                        RectF(borderInset, borderInset, pageWidth - borderInset, pageHeight - borderInset),
                        6f, 6f, borderPaint
                    )
                }

                // Watermark
                if (!options.watermarkText.isNullOrBlank()) {
                    canvas.save()
                    canvas.translate(pageWidth / 2f, pageHeight / 2f)
                    canvas.rotate(-40f)
                    canvas.drawText(options.watermarkText.uppercase(Locale.getDefault()), 0f, 0f, watermarkPaint)
                    canvas.restore()
                }

                // Header
                if (options.includeHeader) {
                    val headerY = margin - 10f
                    val headerTitle = if (options.headerText.isNotBlank()) options.headerText else title
                    canvas.drawText(headerTitle, margin, headerY, headerFooterPaint)
                    if (options.includeDate) {
                        val dateWidth = headerFooterPaint.measureText(dateStr)
                        canvas.drawText(dateStr, pageWidth - margin - dateWidth, headerY, headerFooterPaint)
                    }
                    canvas.drawLine(margin, margin - 4f, pageWidth - margin, margin - 4f, dividerPaint)
                }

                var curY = margin + topHeaderSpace + 14f

                // Render page items
                while (itemIdx < renderItems.size) {
                    val item = renderItems[itemIdx]
                    if (curY + item.height > pageHeight - margin - bottomFooterSpace && curY > margin + topHeaderSpace + 20f) {
                        break // Move to next page
                    }

                    val drawX = when (options.textAlign) {
                        "Center" -> pageWidth / 2f
                        "Right" -> pageWidth - margin
                        else -> margin + item.indent
                    }

                    when (item.type) {
                        "H1" -> {
                            canvas.drawText(item.text, drawX, curY + (item.height * 0.7f), h1Paint)
                        }
                        "H2" -> {
                            canvas.drawText(item.text, drawX, curY + (item.height * 0.7f), h2Paint)
                        }
                        "H3" -> {
                            canvas.drawText(item.text, drawX, curY + (item.height * 0.7f), h3Paint)
                        }
                        "DIVIDER" -> {
                            val divY = curY + (item.height / 2f)
                            canvas.drawLine(margin + 20f, divY, pageWidth - margin - 20f, divY, dividerPaint)
                        }
                        "BULLET" -> {
                            canvas.drawText(item.text, margin + item.indent, curY + (item.height * 0.75f), bulletPaint)
                        }
                        "BODY" -> {
                            canvas.drawText(item.text, drawX, curY + (item.height * 0.75f), bodyPaint)
                        }
                        "SPACER" -> {
                            // nothing to draw
                        }
                    }

                    curY += item.height
                    itemIdx++
                }

                // Footer
                if (options.includeFooter) {
                    val footerY = pageHeight - margin + 18f
                    canvas.drawLine(margin, footerY - 14f, pageWidth - margin, footerY - 14f, dividerPaint)
                    val footerLeft = if (options.author.isNotBlank()) "Author: ${options.author}" else options.customFooterText
                    if (footerLeft.isNotBlank()) {
                        canvas.drawText(footerLeft, margin, footerY, headerFooterPaint)
                    }
                    if (options.includePageNumbers) {
                        val pageStr = "Page $currentPage / $estimatedPages"
                        val pageStrWidth = headerFooterPaint.measureText(pageStr)
                        canvas.drawText(pageStr, pageWidth - margin - pageStrWidth, footerY, headerFooterPaint)
                    }
                }

                pdfDocument.finishPage(page)
                if (itemIdx >= renderItems.size) break
                currentPage++
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    suspend fun generatePdfFromBitmaps(
        context: Context,
        title: String,
        bitmaps: List<Bitmap>
    ): File? = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext null
        val pdfDocument = PdfDocument()
        val pdfDir = FileUtils.getPdfsDir(context)
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Images_Document" }
        val pdfFile = File(pdfDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val pageWidth = 595
                val pageHeight = 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val margin = 20f
                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val availRatio = availableWidth / availableHeight

                val drawWidth: Float
                val drawHeight: Float

                if (bmpRatio > availRatio) {
                    drawWidth = availableWidth
                    drawHeight = availableWidth / bmpRatio
                } else {
                    drawHeight = availableHeight
                    drawWidth = availableHeight * bmpRatio
                }

                val left = margin + (availableWidth - drawWidth) / 2f
                val top = margin + (availableHeight - drawHeight) / 2f

                val destRect = RectF(left, top, left + drawWidth, top + drawHeight)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bitmap, null, destRect, paint)

                pdfDocument.finishPage(page)
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Explicitly saves a PDF file to device public storage (Downloads/DocScanner) and shows notification.
     */
    fun savePdfToDevice(context: Context, pdfFile: File, displayName: String? = null): Uri? {
        return FileUtils.savePdfToPublicDownloads(context, pdfFile, displayName, showNotification = true)
    }
}
