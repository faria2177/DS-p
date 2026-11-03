package com.docscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlin.math.roundToInt

object FileUtils {

    fun getDocumentsDir(context: Context): File {
        val dir = File(context.filesDir, "scanned_documents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPdfsDir(context: Context): File {
        val dir = File(context.filesDir, "exported_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, "temp_scans")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun clearTempFiles(context: Context) {
        try {
            getTempDir(context).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateFileName(prefix: String = "DOC", extension: String = "jpg"): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val random = (1000..9999).random()
        return "${prefix}_${timeStamp}_$random.$extension"
    }

    fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int = 98): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveBitmapToTemp(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "PAGE",
        quality: Int = 98
    ): String {
        val file = File(getTempDir(context), generateFileName(prefix))
        saveBitmapToFile(bitmap, file, quality.coerceIn(1, 100))
        return file.absolutePath
    }

    fun saveBitmapToDocStorage(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "PAGE",
        quality: Int = 98
    ): String {
        val file = File(getDocumentsDir(context), generateFileName(prefix))
        saveBitmapToFile(bitmap, file, quality.coerceIn(1, 100))
        return file.absolutePath
    }

    fun copyUriToTemp(context: Context, uri: Uri): String? {
        return try {
            val bitmaps = loadBitmapsFromUri(context, uri)
            if (bitmaps.isNotEmpty()) {
                saveBitmapToTemp(context, bitmaps[0])
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies an image content Uri byte-for-byte into app temp storage.
     * Use this for ML Kit scanner JPEG results so we do not decode/re-encode the card
     * before the high-quality NID processing pipeline gets the original pixels.
     */
    fun copyImageUriToTempPreservingBytes(
        context: Context,
        uri: Uri,
        prefix: String = "MLKIT"
    ): String? {
        return try {
            val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
            val extension = if (mime.contains("png")) "png" else "jpg"
            val file = File(getTempDir(context), generateFileName(prefix, extension))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            } ?: return null

            file.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads all pages/images from a Uri (supports both image files and multi-page PDF documents).
     */
    fun loadBitmapsFromUri(context: Context, uri: Uri): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val uriStr = uri.toString().lowercase()

            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
                    uriStr.endsWith(".pdf") ||
                    uri.path?.lowercase()?.endsWith(".pdf") == true

            if (isPdf) {
                val pdfBitmaps = renderPdfUriToBitmaps(context, uri)
                if (pdfBitmaps.isNotEmpty()) {
                    return pdfBitmaps
                }
            }

            // Attempt to load standard image stream
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val rawBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (rawBitmap != null) {
                    result.add(rawBitmap)
                    return result
                }
            }

            // If image decoding returned null, attempt fallback PDF rendering
            val fallbackBitmaps = renderPdfUriToBitmaps(context, uri)
            result.addAll(fallbackBitmaps)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackBitmaps = renderPdfUriToBitmaps(context, uri)
                result.addAll(fallbackBitmaps)
            } catch (ignored: Exception) {}
        }
        return result
    }

    /**
     * Renders every page of a PDF document file to high resolution Bitmaps.
     */
    fun renderPdfUriToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var tempPdfFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            tempPdfFile = File(getTempDir(context), generateFileName("IMPORT_PDF", "pdf"))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempPdfFile.exists() && tempPdfFile.length() > 0) {
                pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    // High resolution rendering for crisp text & OCR (scale 2.0x, min width 1080)
                    val width = (page.width * 2).coerceAtLeast(1080)
                    val height = ((page.height.toFloat() / page.width.toFloat()) * width).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE) // PDF backgrounds default transparent
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            try { tempPdfFile?.delete() } catch (ignored: Exception) {}
        }
        return bitmaps
    }

    /**
     * Streams a PDF into persistent high-resolution page images one page at a time.
     * This avoids holding an entire large PDF as Bitmaps in memory and is used by
     * Edit PDF / document import so OCR and high-resolution editing never depend on
     * a low-quality UI preview.
     *
     * The target is approximately 300 DPI (PDF points are 72 DPI), with a conservative
     * per-page pixel cap to avoid OutOfMemory on very large/odd-size pages.
     */
    fun renderPdfUriToStoredPages(
        context: Context,
        uri: Uri,
        prefix: String = "PDF_PAGE",
        targetDpi: Int = 300,
        maxPixelsPerPage: Long = 14_000_000L
    ): List<String> {
        val paths = mutableListOf<String>()
        var tempPdfFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            tempPdfFile = File(getTempDir(context), generateFileName("IMPORT_PDF_STREAM", "pdf"))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output -> input.copyTo(output, 128 * 1024) }
            } ?: return emptyList()

            if (!tempPdfFile.exists() || tempPdfFile.length() <= 0L) return emptyList()
            pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val dpiScale = (targetDpi.coerceIn(144, 360) / 72f).coerceAtLeast(2f)

            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    var width = (page.width * dpiScale).roundToInt().coerceAtLeast(1440)
                    var height = (page.height * (width.toFloat() / page.width.toFloat())).roundToInt().coerceAtLeast(1)
                    val pixels = width.toLong() * height.toLong()
                    if (pixels > maxPixelsPerPage) {
                        val reduction = sqrt(maxPixelsPerPage.toDouble() / pixels.toDouble()).toFloat()
                        width = (width * reduction).roundToInt().coerceAtLeast(1)
                        height = (height * reduction).roundToInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        paths += saveBitmapToDocStorage(context, bitmap, prefix, quality = 100)
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            paths.forEach { path -> runCatching { File(path).delete() } }
            paths.clear()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { tempPdfFile?.delete() } catch (_: Exception) {}
        }
        return paths
    }

    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.equals("application/pdf", ignoreCase = true)) return true
        val name = getFileNameFromUri(context, uri)
        return name.endsWith(".pdf", ignoreCase = true) || uri.toString().substringBefore('?').endsWith(".pdf", ignoreCase = true)
    }

    fun loadBitmap(path: String, maxDimension: Int = 2048): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFormattedFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        }
    }

    fun getStorageUsed(context: Context): String {
        val docDir = getDocumentsDir(context)
        val pdfDir = getPdfsDir(context)
        var totalBytes = 0L
        docDir.listFiles()?.forEach { totalBytes += it.length() }
        pdfDir.listFiles()?.forEach { totalBytes += it.length() }
        return getFormattedFileSize(totalBytes)
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360 == 0f) return source
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun shareImageFiles(context: Context, files: List<File>, title: String) {
        if (files.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            for (file in files) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                uris.add(uri)
            }
            val intent = android.content.Intent().apply {
                action = if (uris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE
                if (uris.size == 1) {
                    putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                    type = "image/jpeg"
                } else {
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    type = "image/jpeg"
                }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share $title"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveImageFileToGallery(context: Context, imagePath: String, title: String): Boolean {
        val file = File(imagePath)
        if (!file.exists()) return false
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return false
        return saveBitmapToGallery(context, bitmap, title) != null
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val fileName = "${title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/DocScanner")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 98, outStream)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                NotificationHelper.showFileSavedNotification(
                    context = context,
                    fileName = fileName,
                    fileUri = uri,
                    filePath = "Pictures/DocScanner/$fileName",
                    mimeType = "image/jpeg",
                    customTitle = "Image Saved to Gallery / ছবি সেভ হয়েছে",
                    customMessage = "$fileName saved to Pictures/DocScanner"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "Imported_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val displayName = cursor.getString(nameIndex)
                            if (!displayName.isNullOrBlank()) {
                                name = displayName
                            }
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                uri.lastPathSegment?.let { name = it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name.substringBeforeLast(".")
    }

    fun savePdfToPublicDownloads(
        context: Context,
        pdfFile: File,
        displayName: String? = null,
        showNotification: Boolean = true
    ): Uri? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        val fileName = if (!displayName.isNullOrBlank()) {
            val base = displayName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
        } else {
            pdfFile.name
        }

        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DocScanner")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        pdfFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    if (showNotification) {
                        NotificationHelper.showFileSavedNotification(
                            context = context,
                            fileName = fileName,
                            fileUri = uri,
                            filePath = "Downloads/DocScanner/$fileName",
                            mimeType = "application/pdf",
                            customTitle = "PDF Saved to Device / পিডিএফ সেভ হয়েছে",
                            customMessage = "$fileName saved to Downloads/DocScanner (${getFormattedFileSize(pdfFile.length())})"
                        )
                    }
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val docScannerDir = File(downloadsDir, "DocScanner")
                if (!docScannerDir.exists()) docScannerDir.mkdirs()
                val destFile = File(docScannerDir, fileName)
                pdfFile.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("application/pdf"), null)
                uri = Uri.fromFile(destFile)
                if (showNotification) {
                    NotificationHelper.showFileSavedNotification(
                        context = context,
                        file = destFile,
                        customTitle = "PDF Saved to Device / পিডিএফ সেভ হয়েছে",
                        customMessage = "${destFile.name} saved to Downloads/DocScanner (${getFormattedFileSize(destFile.length())})"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri
    }

    fun saveFileToPublicDownloads(
        context: Context,
        sourceFile: File,
        displayName: String? = null,
        mimeType: String = "application/octet-stream",
        notificationTitle: String = "File Saved to Device / ফাইল সেভ হয়েছে",
        showNotification: Boolean = true
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null
        val fileName = if (!displayName.isNullOrBlank()) {
            displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        } else {
            sourceFile.name
        }

        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DocScanner")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    if (showNotification) {
                        NotificationHelper.showFileSavedNotification(
                            context = context,
                            fileName = fileName,
                            fileUri = uri,
                            filePath = "Downloads/DocScanner/$fileName",
                            mimeType = mimeType,
                            customTitle = notificationTitle,
                            customMessage = "$fileName saved to Downloads/DocScanner (${getFormattedFileSize(sourceFile.length())})"
                        )
                    }
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val docScannerDir = File(downloadsDir, "DocScanner")
                if (!docScannerDir.exists()) docScannerDir.mkdirs()
                val destFile = File(docScannerDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
                uri = Uri.fromFile(destFile)
                if (showNotification) {
                    NotificationHelper.showFileSavedNotification(
                        context = context,
                        file = destFile,
                        customTitle = notificationTitle,
                        customMessage = "${destFile.name} saved to Downloads/DocScanner (${getFormattedFileSize(destFile.length())})"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*", title: String = "Share File") {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, title).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
