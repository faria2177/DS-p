package com.docscan.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Central router for handling all incoming external file opening intents
 * (e.g. File Manager -> Open With -> Doc Scanner, or Share To -> Doc Scanner).
 *
 * Provides:
 * - Intent extraction (ACTION_VIEW and ACTION_SEND)
 * - Persistable and temporary URI permission handling
 * - Scoped storage copy into secure local app cache
 * - Security validations (path traversal, size limits, format checks)
 * - Routing to PDF Viewer, Word Reader, Excel Reader, or Unsupported Dialog
 */
object ExternalFileRouter {

    private const val TAG = "ExternalFileRouter"
    private const val MAX_ALLOWED_FILE_SIZE_BYTES = 200L * 1024L * 1024L // 200MB limit

    sealed class RouteDestination {
        data class OpenPdf(val uri: Uri, val cachedFilePath: String, val title: String) : RouteDestination()
        data class OpenWord(val uri: Uri, val cachedFilePath: String, val title: String) : RouteDestination()
        data class OpenExcel(val uri: Uri, val cachedFilePath: String, val title: String) : RouteDestination()
        data class OpenImage(val uri: Uri, val cachedFilePath: String, val title: String) : RouteDestination()
        data class Unsupported(val fileName: String, val mimeType: String?) : RouteDestination()
        data class Error(val message: String) : RouteDestination()
    }

    private val _incomingRoute = MutableStateFlow<RouteDestination?>(null)
    val incomingRoute: StateFlow<RouteDestination?> = _incomingRoute.asStateFlow()

    /**
     * Call this when the destination has been navigated to, so recomposition does not repeat it.
     */
    fun consumeRoute() {
        _incomingRoute.value = null
    }

    /**
     * Processes an incoming Intent from MainActivity.onCreate or MainActivity.onNewIntent.
     */
    suspend fun processIntent(context: Context, intent: Intent?): RouteDestination? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null

        val action = intent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) {
            return@withContext null
        }

        // 1. Extract Target Uri
        val targetUri: Uri? = extractUriFromIntent(intent)
        if (targetUri == null) {
            Log.w(TAG, "No valid URI found in incoming intent: $intent")
            return@withContext null
        }

        // 2. Handle URI Permissions
        grantUriPermissionSafely(context, intent, targetUri)

        // 3. Security & Validation Checks
        val fileName = FileTypeDetector.getFileName(context, targetUri)
        if (isMaliciousPath(fileName) || isMaliciousUri(targetUri)) {
            val dest = RouteDestination.Error("Security error: Invalid or malicious file path.")
            _incomingRoute.value = dest
            return@withContext dest
        }

        val fileSize = FileTypeDetector.getFileSize(context, targetUri)
        if (fileSize > MAX_ALLOWED_FILE_SIZE_BYTES) {
            val dest = RouteDestination.Error("File is too large (${fileSize / (1024 * 1024)}MB). Maximum allowed size is 200MB.")
            _incomingRoute.value = dest
            return@withContext dest
        }

        // 4. Detect File Type
        val detectedType = FileTypeDetector.detectType(context, targetUri, intent.type)
        if (detectedType == FileTypeDetector.FileType.UNSUPPORTED) {
            val dest = RouteDestination.Unsupported(fileName, intent.type)
            _incomingRoute.value = dest
            return@withContext dest
        }

        // 5. Create a secure local cache copy
        // This ensures scoped storage compliance across all Android versions (10 to 16)
        // and guarantees file access even if the external app immediately revokes temporary URI grants.
        val cachedFile = copyToSecureCache(context, targetUri, fileName)
        val finalFilePath = cachedFile?.absolutePath ?: ""
        val title = fileName.substringBeforeLast(".").ifBlank { "External Document" }

        val destination = when (detectedType) {
            FileTypeDetector.FileType.PDF -> RouteDestination.OpenPdf(targetUri, finalFilePath, title)
            FileTypeDetector.FileType.WORD -> RouteDestination.OpenWord(targetUri, finalFilePath, title)
            FileTypeDetector.FileType.EXCEL -> RouteDestination.OpenExcel(targetUri, finalFilePath, title)
            FileTypeDetector.FileType.IMAGE -> RouteDestination.OpenImage(targetUri, finalFilePath, title)
            FileTypeDetector.FileType.UNSUPPORTED -> RouteDestination.Unsupported(fileName, intent.type)
        }

        _incomingRoute.value = destination
        destination
    }

    private fun extractUriFromIntent(intent: Intent): Uri? {
        // Direct intent.data (Standard for ACTION_VIEW)
        if (intent.data != null) {
            return intent.data
        }

        // ClipData (Modern Android ACTION_VIEW and ACTION_SEND)
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val itemUri = clipData.getItemAt(0).uri
            if (itemUri != null) return itemUri
        }

        // EXTRA_STREAM (Standard for ACTION_SEND)
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            try {
                @Suppress("DEPRECATION")
                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (streamUri != null) return streamUri
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read EXTRA_STREAM: ${e.message}")
            }
        }

        return null
    }

    /**
     * Safely attempts to take persistable URI permission if available,
     * without crashing if the content provider does not support persistable grants.
     */
    private fun grantUriPermissionSafely(context: Context, intent: Intent, uri: Uri) {
        if (uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val takeFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.d(TAG, "Successfully took persistable URI permission for $uri")
                }
            } catch (e: SecurityException) {
                // Provider did not grant persistable permission; temporary access is granted via intent flags
                Log.d(TAG, "Persistable permission not supported by provider: ${e.message}. Using temporary access.")
            } catch (e: Exception) {
                Log.w(TAG, "Error while attempting to take URI permission: ${e.message}")
            }
        }
    }

    private fun isMaliciousPath(filename: String): Boolean {
        return filename.contains("../") || filename.contains("..\\") || filename.contains("\u0000")
    }

    private fun isMaliciousUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return path.contains("../") || path.contains("..\\") || path.contains("%2e%2e")
    }

    /**
     * Copies the content from the external URI into the app's cacheDir/external_incoming/
     * so it can be reliably read by PDF Renderers, Zip Readers, and database stores.
     */
    private fun copyToSecureCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val incomingDir = File(context.cacheDir, "external_incoming")
            if (!incomingDir.exists()) {
                incomingDir.mkdirs()
            }

            // Sanitize file name
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(incomingDir, "${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy external URI to cache: ${e.message}", e)
            null
        }
    }
}
