package com.docscan.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.docscan.data.model.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class CompressionLevel(
    val title: String,
    val subtitle: String,
    val maxDimension: Int,
    val jpegQuality: Int,
    val cleanBackground: Boolean = true,
    val sharpenText: Boolean = true
) {
    LOW(
        title = "Best Quality",
        subtitle = "Near-original detail, loss-minimized compression",
        maxDimension = 3200,
        jpegQuality = 100,
        cleanBackground = false,
        sharpenText = false
    ),
    MEDIUM(
        title = "Medium",
        subtitle = "Smaller size with high visual fidelity",
        maxDimension = 2800,
        jpegQuality = 96,
        cleanBackground = false,
        sharpenText = true
    ),
    HIGH(
        title = "High",
        subtitle = "Stronger size reduction while protecting text/detail",
        maxDimension = 2400,
        jpegQuality = 92,
        cleanBackground = false,
        sharpenText = true
    );

    fun toConfig(): CompressionConfig = CompressionConfig(level = this)
}

data class CompressionConfig(
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val customQuality: Int? = null,
    val customMaxDimension: Int? = null,
    val cleanBackground: Boolean = true,
    val sharpenText: Boolean = true,
    val convertToGrayscale: Boolean = false
) {
    val effectiveQuality: Int get() = customQuality ?: level.jpegQuality
    val effectiveMaxDimension: Int get() = customMaxDimension ?: level.maxDimension
}

data class CompressedPdfResult(
    val file: File,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val formattedSize: String,
    val pageCount: Int,
    val previewBitmaps: List<Bitmap> = emptyList(),
    val originalBitmaps: List<Bitmap> = emptyList(),
    val reductionPercentage: Int = 0,
    val qualityLevelName: String = "Medium"
)

object PdfCompressor {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1fMB", mb)
        } else {
            String.format(Locale.US, "%d KB", kb.toLong().coerceAtLeast(1L))
        }
    }

    fun estimateOutputSize(originalBytes: Long, level: CompressionLevel): Long {
        if (originalBytes <= 0L) return 0L
        val ratio = when (level) {
            CompressionLevel.LOW -> 0.82
            CompressionLevel.MEDIUM -> 0.62
            CompressionLevel.HIGH -> 0.46
        }
        return (originalBytes * ratio).toLong().coerceAtLeast(48L * 1024L)
    }

    /**
     * Compresses an existing document's pages according to the selected compression level.
     */
    suspend fun compressDocument(
        context: Context,
        documentTitle: String,
        pages: List<PageEntity>,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CompressedPdfResult? = compressDocument(
        context = context,
        documentTitle = documentTitle,
        pages = pages,
        config = level.toConfig(),
        onProgress = onProgress
    )

    /**
     * Compresses an existing document's pages with a full CompressionConfig.
     */
    suspend fun compressDocument(
        context: Context,
        documentTitle: String,
        pages: List<PageEntity>,
        config: CompressionConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CompressedPdfResult? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val pdfDir = FileUtils.getPdfsDir(context)
        val sanitizedTitle = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Document" }
        val outputFile = getUniqueOutputFile(pdfDir, sanitizedTitle)

        val pdfDocument = PdfDocument()
        val previewBitmaps = mutableListOf<Bitmap>()
        val originalBitmaps = mutableListOf<Bitmap>()

        try {
            var originalTotalBytes = 0L
            val totalPages = pages.size

            onProgress(0.1f, "Preparing document pages...")

            pages.forEachIndexed { index, pageEntity ->
                val stepFraction = 0.1f + (0.7f * (index.toFloat() / totalPages.coerceAtLeast(1)))
                onProgress(stepFraction, "Optimizing page ${index + 1} of $totalPages...")

                val imagePath = pageEntity.processedImagePath.ifBlank { pageEntity.originalImagePath }
                val srcFile = File(imagePath)
                if (srcFile.exists()) {
                    originalTotalBytes += srcFile.length()
                }

                // Load source bitmap
                val rawBitmap = FileUtils.loadBitmap(imagePath, maxDimension = 3200)
                    ?: return@forEachIndexed

                // Save an uncompressed reference preview bitmap for side-by-side quality comparison
                val origPreview = Bitmap.createScaledBitmap(
                    rawBitmap,
                    minOf(rawBitmap.width, 1600),
                    minOf(rawBitmap.height, 1600),
                    true
                )
                originalBitmaps.add(origPreview)

                // Process and scale according to compression config with smart text & background optimization
                val compressedBmp = compressAndScaleBitmap(rawBitmap, config)
                rawBitmap.recycle()

                // Adaptive page dimensions matching image orientation (Portrait vs Landscape)
                val isLandscape = compressedBmp.width > compressedBmp.height
                val pageWidth = if (isLandscape) 842 else 595
                val pageHeight = if (isLandscape) 595 else 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                val margin = 16f
                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val bmpRatio = compressedBmp.width.toFloat() / compressedBmp.height.toFloat()
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
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(compressedBmp, null, destRect, paint)

                pdfDocument.finishPage(page)
                compressedBmp.recycle()
            }

            onProgress(0.85f, "Writing compressed PDF...")

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            onProgress(0.95f, "Generating preview...")

            // Render preview bitmaps from the newly generated compressed PDF
            val renderedPreviews = renderPdfFileToBitmaps(outputFile)
            previewBitmaps.addAll(renderedPreviews)

            val finalLength = outputFile.length()
            val formatted = formatFileSize(finalLength)
            val effectiveOriginalBytes = originalTotalBytes.coerceAtLeast(finalLength)
            val reduction = if (effectiveOriginalBytes > finalLength && effectiveOriginalBytes > 0) {
                (((effectiveOriginalBytes - finalLength).toDouble() / effectiveOriginalBytes.toDouble()) * 100).toInt()
            } else {
                0
            }

            onProgress(1.0f, "Completed")

            CompressedPdfResult(
                file = outputFile,
                originalSizeBytes = effectiveOriginalBytes,
                compressedSizeBytes = finalLength,
                formattedSize = formatted,
                pageCount = previewBitmaps.size.coerceAtLeast(totalPages),
                previewBitmaps = previewBitmaps,
                originalBitmaps = originalBitmaps,
                reductionPercentage = reduction,
                qualityLevelName = config.level.title
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    /**
     * Compresses an arbitrary list of Bitmaps (e.g. from an imported PDF/image).
     */
    suspend fun compressBitmaps(
        context: Context,
        documentTitle: String,
        bitmaps: List<Bitmap>,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CompressedPdfResult? = compressBitmaps(
        context = context,
        documentTitle = documentTitle,
        bitmaps = bitmaps,
        config = level.toConfig(),
        onProgress = onProgress
    )

    suspend fun compressBitmaps(
        context: Context,
        documentTitle: String,
        bitmaps: List<Bitmap>,
        config: CompressionConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CompressedPdfResult? = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext null

        val pdfDir = FileUtils.getPdfsDir(context)
        val sanitizedTitle = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Document" }
        val outputFile = getUniqueOutputFile(pdfDir, sanitizedTitle)

        val pdfDocument = PdfDocument()
        val previewBitmaps = mutableListOf<Bitmap>()
        val originalBitmaps = mutableListOf<Bitmap>()

        try {
            val totalPages = bitmaps.size
            var estOriginalBytes = 0L

            onProgress(0.1f, "Preparing pages...")

            bitmaps.forEachIndexed { index, rawBmp ->
                estOriginalBytes += (rawBmp.byteCount.toLong() / 3L) // estimate raw size
                val stepFraction = 0.1f + (0.7f * (index.toFloat() / totalPages.coerceAtLeast(1)))
                onProgress(stepFraction, "Optimizing page ${index + 1} of $totalPages...")

                val origPreview = Bitmap.createScaledBitmap(
                    rawBmp,
                    minOf(rawBmp.width, 1600),
                    minOf(rawBmp.height, 1600),
                    true
                )
                originalBitmaps.add(origPreview)

                val compressedBmp = compressAndScaleBitmap(rawBmp, config)

                // Adaptive page dimensions matching image orientation (Portrait vs Landscape)
                val isLandscape = compressedBmp.width > compressedBmp.height
                val pageWidth = if (isLandscape) 842 else 595
                val pageHeight = if (isLandscape) 595 else 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                val margin = 16f
                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val bmpRatio = compressedBmp.width.toFloat() / compressedBmp.height.toFloat()
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
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(compressedBmp, null, destRect, paint)

                pdfDocument.finishPage(page)
                compressedBmp.recycle()
            }

            onProgress(0.85f, "Writing compressed PDF...")

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            onProgress(0.95f, "Generating preview...")

            val renderedPreviews = renderPdfFileToBitmaps(outputFile)
            previewBitmaps.addAll(renderedPreviews)

            val finalLength = outputFile.length()
            val formatted = formatFileSize(finalLength)
            val effectiveOriginalBytes = estOriginalBytes.coerceAtLeast(finalLength)
            val reduction = if (effectiveOriginalBytes > finalLength && effectiveOriginalBytes > 0) {
                (((effectiveOriginalBytes - finalLength).toDouble() / effectiveOriginalBytes.toDouble()) * 100).toInt()
            } else {
                0
            }

            onProgress(1.0f, "Completed")

            CompressedPdfResult(
                file = outputFile,
                originalSizeBytes = effectiveOriginalBytes,
                compressedSizeBytes = finalLength,
                formattedSize = formatted,
                pageCount = previewBitmaps.size.coerceAtLeast(totalPages),
                previewBitmaps = previewBitmaps,
                originalBitmaps = originalBitmaps,
                reductionPercentage = reduction,
                qualityLevelName = config.level.title
            )
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun compressAndScaleBitmap(src: Bitmap, level: CompressionLevel): Bitmap {
        return compressAndScaleBitmap(src, level.toConfig())
    }

    private fun compressAndScaleBitmap(src: Bitmap, config: CompressionConfig): Bitmap {
        val maxDim = config.effectiveMaxDimension
        val w = src.width
        val h = src.height

        // 1. High-Quality Bilinear Scaling preserving aspect ratio
        val scaled = if (w > maxDim || h > maxDim) {
            val scale = maxDim.toFloat() / maxOf(w, h)
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, targetW, targetH, true)
        } else {
            src.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 2. Grayscale conversion if requested
        val processedColor = if (config.convertToGrayscale) {
            convertBitmapToGrayscale(scaled).also {
                if (scaled != src) scaled.recycle()
            }
        } else {
            scaled
        }

        // 3. Smart Background Whitening (Purges noisy paper texture, shadows & dust to achieve dramatic compression without losing ink/content)
        val cleaned = if (config.cleanBackground) {
            cleanDocumentBackground(processedColor).also {
                if (processedColor != src) processedColor.recycle()
            }
        } else {
            processedColor
        }

        // 4. Text Edge Sharpening (Preserves micro-contrast on font contours, Bengali characters, signatures & numbers)
        val sharpened = if (config.sharpenText) {
            sharpenDocumentEdges(cleaned).also {
                if (cleaned != src) cleaned.recycle()
            }
        } else {
            cleaned
        }

        // 5. Keep the working bitmap lossless. Re-encoding every page to JPEG here
        // was the main source of blur, ringing and broken small/Bengali text. Size
        // reduction now comes from controlled down-scaling and PDF encoding, while
        // preserving the pixels that remain after scaling.
        return sharpened
    }

    private fun convertBitmapToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Cleans near-white background noise (paper shadows, grey tints, grain) into clean crisp white.
     * This dramatically improves JPEG compression ratio by eliminating high-frequency noise
     * from the paper background, while leaving all text, stamps, signatures, and graphics untouched.
     */
    private fun cleanDocumentBackground(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Fast luminance approximation: Y = 0.299R + 0.587G + 0.114B
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            if (lum >= 236) {
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                // Only whiten if saturation is low (i.e. paper background, not light colored ink/stamps)
                if (maxC - minC < 25) {
                    pixels[i] = (c and 0xFF000000.toInt()) or 0x00FFFFFF
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Lightweight unsharp mask edge enhancement to preserve sharp text character strokes
     * and prevent downsampling blurriness on document fonts.
     */
    private fun sharpenDocumentEdges(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val srcPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val dstPixels = IntArray(width * height)

        for (y in 1 until height - 1) {
            val yOffset = y * width
            val yTop = (y - 1) * width
            val yBottom = (y + 1) * width

            for (x in 1 until width - 1) {
                val idx = yOffset + x
                val c = srcPixels[idx]
                val a = (c shr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // If it's pure white, no need to sharpen
                if (r >= 252 && g >= 252 && b >= 252) {
                    dstPixels[idx] = c
                    continue
                }

                // Surrounding 4-neighbor pixels
                val cUp = srcPixels[yTop + x]
                val cDown = srcPixels[yBottom + x]
                val cLeft = srcPixels[yOffset + x - 1]
                val cRight = srcPixels[yOffset + x + 1]

                val rNeighbors = (((cUp shr 16) and 0xFF) + ((cDown shr 16) and 0xFF) +
                                  ((cLeft shr 16) and 0xFF) + ((cRight shr 16) and 0xFF)) shr 2
                val gNeighbors = (((cUp shr 8) and 0xFF) + ((cDown shr 8) and 0xFF) +
                                  ((cLeft shr 8) and 0xFF) + ((cRight shr 8) and 0xFF)) shr 2
                val bNeighbors = ((cUp and 0xFF) + (cDown and 0xFF) +
                                  (cLeft and 0xFF) + (cRight and 0xFF)) shr 2

                // Subtle sharpening formula: output = 1.35 * center - 0.35 * neighbors
                val sharpR = (r + (r - rNeighbors) * 0.35f).toInt().coerceIn(0, 255)
                val sharpG = (g + (g - gNeighbors) * 0.35f).toInt().coerceIn(0, 255)
                val sharpB = (b + (b - bNeighbors) * 0.35f).toInt().coerceIn(0, 255)

                dstPixels[idx] = (a shl 24) or (sharpR shl 16) or (sharpG shl 8) or sharpB
            }
        }

        // Copy borders
        System.arraycopy(srcPixels, 0, dstPixels, 0, width)
        System.arraycopy(srcPixels, (height - 1) * width, dstPixels, (height - 1) * width, width)
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]
            dstPixels[y * width + width - 1] = srcPixels[y * width + width - 1]
        }

        val output = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun renderPdfFileToBitmaps(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val width = (page.width * 2).coerceAtLeast(1080)
                val height = ((page.height.toFloat() / page.width.toFloat()) * width).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
        return bitmaps
    }

    private fun getUniqueOutputFile(dir: File, baseName: String): File {
        var candidate = File(dir, "${baseName}_compressed.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_compressed_$counter.pdf")
            counter++
        }
        return candidate
    }

    /**
     * Native Android share via Sharesheet and FileProvider.
     */
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

            val chooser = Intent.createChooser(shareIntent, "Share Compressed PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves the compressed PDF to external device Downloads/DocScanner storage.
     */
    fun savePdfToDevice(context: Context, pdfFile: File): File? {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocScanner")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        pdfFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    Toast.makeText(context, "Saved to Downloads/DocScanner/${pdfFile.name}", Toast.LENGTH_LONG).show()
                    NotificationHelper.showFileSavedNotification(
                        context = context,
                        fileName = pdfFile.name,
                        fileUri = uri,
                        filePath = "Downloads/DocScanner/${pdfFile.name}",
                        mimeType = "application/pdf",
                        customTitle = "Compressed PDF Saved / পিডিএফ সেভ হয়েছে",
                        customMessage = "${pdfFile.name} saved to Downloads/DocScanner"
                    )
                    pdfFile
                } else {
                    fallbackDirectSave(context, pdfFile)
                }
            } else {
                fallbackDirectSave(context, pdfFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackDirectSave(context, pdfFile)
        }
    }

    private fun fallbackDirectSave(context: Context, pdfFile: File): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val docScannerDir = File(downloadsDir, "DocScanner")
            if (!docScannerDir.exists()) docScannerDir.mkdirs()
            val destFile = File(docScannerDir, pdfFile.name)
            pdfFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("application/pdf"), null)
            Toast.makeText(context, "Saved to Downloads/DocScanner/${pdfFile.name}", Toast.LENGTH_LONG).show()
            NotificationHelper.showFileSavedNotification(
                context = context,
                file = destFile,
                customTitle = "Compressed PDF Saved / পিডিএফ সেভ হয়েছে",
                customMessage = "${destFile.name} saved to Downloads/DocScanner"
            )
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Saved in app documents: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
            pdfFile
        }
    }

    /**
     * Opens PDF in external/system viewer app.
     */
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
}
