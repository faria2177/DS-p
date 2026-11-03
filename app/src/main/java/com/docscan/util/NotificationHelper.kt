package com.docscan.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper to handle system notifications when files, PDFs, DOCX, XLSX, or Images
 * are saved, downloaded, or exported to the device.
 */
object NotificationHelper {

    const val CHANNEL_ID = "file_saved_notifications"
    private const val CHANNEL_NAME = "Saved Files & Exports"
    private const val CHANNEL_DESC = "Notifications when documents or files are saved to the device"

    /**
     * Initializes the notification channel on Android 8.0+ (API 26+).
     */
    fun initChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Shows a notification when a File object is saved to the device.
     */
    fun showFileSavedNotification(
        context: Context,
        file: File,
        customTitle: String? = null,
        customMessage: String? = null
    ) {
        if (!file.exists()) return
        val mimeType = getMimeTypeForFile(file)
        val fileUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            null
        }

        val formattedSize = FileUtils.getFormattedFileSize(file.length())
        val title = customTitle ?: "File Saved / ফাইল সেভ হয়েছে"
        val message = customMessage ?: "${file.name} ($formattedSize)"

        showNotification(
            context = context,
            fileName = file.name,
            fileUri = fileUri,
            mimeType = mimeType,
            title = title,
            message = message,
            detailText = "File saved at: ${file.absolutePath}\nSize: $formattedSize"
        )
    }

    /**
     * Shows a notification when a file is saved via Uri or path (e.g., MediaStore Pictures or Downloads).
     */
    fun showFileSavedNotification(
        context: Context,
        fileName: String,
        fileUri: Uri? = null,
        filePath: String? = null,
        mimeType: String? = null,
        customTitle: String? = null,
        customMessage: String? = null
    ) {
        val resolvedMimeType = mimeType ?: getMimeTypeForName(fileName)
        val title = customTitle ?: "File Saved / ফাইল সেভ হয়েছে"
        val message = customMessage ?: fileName

        showNotification(
            context = context,
            fileName = fileName,
            fileUri = fileUri,
            mimeType = resolvedMimeType,
            title = title,
            message = message,
            detailText = if (!filePath.isNullOrBlank()) "Saved to: $filePath" else "Saved successfully to your device"
        )
    }

    /**
     * Internal method to build and dispatch the notification.
     */
    private fun showNotification(
        context: Context,
        fileName: String,
        fileUri: Uri?,
        mimeType: String,
        title: String,
        message: String,
        detailText: String
    ) {
        try {
            initChannel(context)

            // Check notification permission for Android 13+ (API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val notificationId = (System.currentTimeMillis() % 100000).toInt()

            // Open File Intent
            var contentPendingIntent: PendingIntent? = null
            var sharePendingIntent: PendingIntent? = null

            if (fileUri != null) {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contentPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent.createChooser(openIntent, "Open with"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                sharePendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId + 1,
                    Intent.createChooser(shareIntent, "Share file"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // The user-facing notification must show only the file name.
            // Retain the tap-to-open behavior, but no detail line, path, size or actions.
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(fileName)
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)

            contentPendingIntent?.let { builder.setContentIntent(it) }

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMimeTypeForFile(file: File): String {
        return getMimeTypeForName(file.name)
    }

    private fun getMimeTypeForName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "*/*"
        }
    }
}
