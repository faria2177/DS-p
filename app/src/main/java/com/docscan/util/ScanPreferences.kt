package com.docscan.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads the settings that the Settings tab actually persists. */
object ScanPreferences {
    private fun prefs(context: Context) =
        context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    fun autoAdjustBorders(context: Context): Boolean = prefs(context).getBoolean("scan_auto_adjust_borders", true)
    fun reviewAfterScan(context: Context): Boolean = prefs(context).getBoolean("scan_adjust_after_each_scan", false)
    fun saveToGallery(context: Context): Boolean = prefs(context).getBoolean("scan_save_to_gallery", false)
    fun shutterSound(context: Context): Boolean = prefs(context).getBoolean("scan_shutter_sound", true)
    fun enableBackgroundOcr(context: Context): Boolean = prefs(context).getBoolean("ocr_local_extraction", true)

    fun newDocumentTitle(context: Context): String {
        val configured = prefs(context).getString("doc_default_file_name", "DocScanner_")
            ?.trim().orEmpty().ifBlank { "DocScanner_" }
        val safePrefix = configured.replace(Regex("""[\\/:*?"<>|]"""), "_").replace('\n', '_').replace('\r', '_').take(70)
        return safePrefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    /** Publish a COPY; app's private originals remain unchanged. */
    fun copyImageToGallery(context: Context, path: String): Uri? {
        if (!saveToGallery(context)) return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Docscanner_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Docscanner")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) error("JPEG write failed")
                } ?: error("Could not write gallery image")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } catch (_: Exception) {
            return null
        } finally {
            bitmap.recycle()
        }
    }
}
