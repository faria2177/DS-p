package com.docscan.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.InputStream
import java.util.Locale

/**
 * Robust, production-ready file type detector for Doc Scanner.
 * Accurately detects PDF, Word, Excel, and Image files using:
 * 1. ContentResolver MIME type
 * 2. File extension fallback (from displayName or Uri path)
 * 3. File magic byte inspection (e.g. %PDF-, PK Zip headers)
 */
object FileTypeDetector {

    enum class FileType {
        PDF,
        WORD,
        EXCEL,
        IMAGE,
        UNSUPPORTED
    }

    /**
     * Detects the FileType of the given Uri.
     */
    fun detectType(context: Context, uri: Uri?, explicitMime: String? = null): FileType {
        if (uri == null) return FileType.UNSUPPORTED

        // 1. Check explicit MIME type passed via Intent
        if (!explicitMime.isNullOrBlank() && explicitMime != "*/*") {
            val fromExplicit = matchMimeType(explicitMime)
            if (fromExplicit != FileType.UNSUPPORTED) {
                return fromExplicit
            }
        }

        // 2. Check ContentResolver MIME type
        try {
            val resolverMime = context.contentResolver.getType(uri)
            if (!resolverMime.isNullOrBlank() && resolverMime != "*/*") {
                val fromResolver = matchMimeType(resolverMime)
                if (fromResolver != FileType.UNSUPPORTED) {
                    return fromResolver
                }
            }
        } catch (_: Exception) {}

        // 3. Check filename extension from ContentResolver OpenableColumns
        val displayName = getFileName(context, uri)
        val fromName = matchExtension(displayName)
        if (fromName != FileType.UNSUPPORTED) {
            return fromName
        }

        // 4. Check URI path extension
        val path = uri.path ?: uri.toString()
        val fromPath = matchExtension(path)
        if (fromPath != FileType.UNSUPPORTED) {
            return fromPath
        }

        // 5. Deep inspection: Check magic header bytes
        val fromMagic = inspectMagicBytes(context, uri)
        if (fromMagic != FileType.UNSUPPORTED) {
            return fromMagic
        }

        return FileType.UNSUPPORTED
    }

    private fun matchMimeType(mime: String): FileType {
        val lower = mime.lowercase(Locale.ROOT).trim()
        return when {
            // PDF
            lower == "application/pdf" || lower == "application/x-pdf" || lower == "application/acrobat" -> FileType.PDF

            // Word
            lower == "application/msword" ||
            lower == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            lower == "application/vnd.ms-word" ||
            lower == "application/doc" ||
            lower == "application/docx" ||
            lower == "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> FileType.WORD

            // Excel
            lower == "application/vnd.ms-excel" ||
            lower == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            lower == "application/vnd.ms-excel.sheet.macroenabled.12" ||
            lower == "application/vnd.openxmlformats-officedocument.spreadsheetml.template" ||
            lower == "text/csv" ||
            lower == "text/comma-separated-values" ||
            lower == "application/csv" ||
            lower == "application/x-csv" ||
            lower == "application/excel" ||
            lower == "application/x-excel" ||
            lower == "application/x-msexcel" -> FileType.EXCEL

            // Images
            lower.startsWith("image/") -> FileType.IMAGE

            else -> FileType.UNSUPPORTED
        }
    }

    private fun matchExtension(filename: String): FileType {
        val lower = filename.lowercase(Locale.ROOT)
        val ext = lower.substringAfterLast(".", "")
        if (ext.isBlank()) return FileType.UNSUPPORTED

        return when (ext) {
            "pdf" -> FileType.PDF
            "doc", "docx", "dot", "dotx", "docm" -> FileType.WORD
            "xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "csv", "tsv" -> FileType.EXCEL
            "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "gif" -> FileType.IMAGE
            else -> FileType.UNSUPPORTED
        }
    }

    /**
     * Inspects the first few bytes of the file stream to detect true format
     * even if MIME type is missing or extension is disguised.
     */
    private fun inspectMagicBytes(context: Context, uri: Uri): FileType {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8)
                val read = stream.read(buffer, 0, buffer.size)
                if (read >= 4) {
                    // %PDF (0x25 0x50 0x44 0x46)
                    if (buffer[0] == 0x25.toByte() && buffer[1] == 0x50.toByte() &&
                        buffer[2] == 0x44.toByte() && buffer[3] == 0x46.toByte()
                    ) {
                        return@use FileType.PDF
                    }

                    // Zip Header PK.. (0x50 0x4B 0x03 0x04) - DOCX or XLSX
                    if (buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte() &&
                        buffer[2] == 0x03.toByte() && buffer[3] == 0x04.toByte()
                    ) {
                        // Inspect zip contents or check filename
                        val name = getFileName(context, uri).lowercase(Locale.ROOT)
                        return@use if (name.contains("sheet") || name.contains("excel") || name.endsWith(".xlsx")) {
                            FileType.EXCEL
                        } else {
                            FileType.WORD
                        }
                    }

                    // OLE2 Compound Document (0xD0 0xCF 0x11 0xE0) - legacy .doc or .xls
                    if (buffer[0] == 0xD0.toByte() && buffer[1] == 0xCF.toByte() &&
                        buffer[2] == 0x11.toByte() && buffer[3] == 0xE0.toByte()
                    ) {
                        val name = getFileName(context, uri).lowercase(Locale.ROOT)
                        return@use if (name.endsWith(".xls")) FileType.EXCEL else FileType.WORD
                    }
                }
                FileType.UNSUPPORTED
            } ?: FileType.UNSUPPORTED
        } catch (_: Exception) {
            FileType.UNSUPPORTED
        }
    }

    /**
     * Extracts a clean, sanitized display name for a Uri without path traversal characters.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex) ?: ""
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result.isBlank()) {
            result = uri.lastPathSegment ?: ""
            if (result.contains("/")) {
                result = result.substringAfterLast("/")
            }
        }
        // Sanitize to prevent path traversal
        val sanitized = result.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return if (sanitized.isNotBlank()) sanitized else "Document_${System.currentTimeMillis()}"
    }

    /**
     * Safe query for file size in bytes. Returns -1 if unknown.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            return cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return -1L
    }
}
