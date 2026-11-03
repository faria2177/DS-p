package com.docscan.ui.remover.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.docscan.ui.remover.model.AutoCropPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern MediaStore Transparent PNG & Composite Image Exporter.
 * Adheres strictly to Android 10-14+ scoped storage without deprecated broad permissions.
 */
object TransparentPngExporter {

    private fun generateFileName(isPng: Boolean): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = if (isPng) "png" else "jpg"
        return "BG_Removed_$timestamp.$ext"
    }

    /**
     * Crops bitmap according to subject bounding box and selected padding
     */
    fun applyAutoCrop(bitmap: Bitmap, subjectBox: Rect, padding: AutoCropPadding): Bitmap {
        if (padding == AutoCropPadding.NONE) return bitmap

        val cropRect = MaskProcessor.computeAutoCropRect(
            box = subjectBox,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            paddingRatio = padding.paddingRatio
        )

        val w = cropRect.width().coerceAtLeast(1)
        val h = cropRect.height().coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, w, h)
    }

    /**
     * Saves true 32-bit Transparent PNG to user's gallery / MediaStore
     */
    suspend fun saveTransparentPng(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val fileName = generateFileName(isPng = true)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocScan")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Saves JPEG image (with background replaced) to MediaStore
     */
    suspend fun saveCompositeJpeg(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val fileName = generateFileName(isPng = false)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocScan")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Shares image via standard Android Share Sheet using FileProvider
     */
    suspend fun shareBitmap(context: Context, bitmap: Bitmap, isPng: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, generateFileName(isPng))
            val stream = FileOutputStream(file)
            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (isPng) 100 else 92
            bitmap.compress(format, quality, stream)
            stream.close()

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (isPng) "image/png" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Cutout Image").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
