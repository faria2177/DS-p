package com.docscan.ai.manager

import android.content.Context
import android.util.Log
import com.docscan.ai.model.LocalModelInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Connecting(val model: LocalModelInfo) : DownloadState()
    data class Downloading(
        val model: LocalModelInfo,
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : DownloadState()
    data class Paused(
        val model: LocalModelInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    ) : DownloadState()
    data class Verifying(val model: LocalModelInfo) : DownloadState()
    data class Completed(val model: LocalModelInfo, val file: File) : DownloadState()
    data class Failed(val model: LocalModelInfo, val error: String, val canRetry: Boolean = true) : DownloadState()
}

class AIModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "AIModelDownloadManager"
        private const val BUFFER_SIZE = 64 * 1024 // 64KB buffer for high download throughput
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private var isPaused = false

    fun startDownload(model: LocalModelInfo) {
        if (model.id == LocalModelManager.BUILTIN_MODEL.id || model.downloadUrl.isBlank()) {
            enableBuiltInModel()
            return
        }

        if (_downloadState.value is DownloadState.Downloading) {
            Log.w(TAG, "Download already in progress.")
            return
        }

        val targetDir = LocalModelManager.getModelDirectory(context)
        val finalFile = LocalModelManager.getModelFile(context, model)
        val partFile = File(targetDir, "${model.fileName}.part")

        isPaused = false
        _downloadState.value = DownloadState.Connecting(model)

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                executeDownload(model, partFile, finalFile)
            } catch (e: CancellationException) {
                if (isPaused) {
                    val downloaded = partFile.length()
                    val total = model.downloadSizeMB * 1024 * 1024
                    val prog = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    _downloadState.value = DownloadState.Paused(model, downloaded, total, prog)
                } else {
                    _downloadState.value = DownloadState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _downloadState.value = DownloadState.Failed(
                    model = model,
                    error = e.localizedMessage ?: "Download failed. Please check your network connection."
                )
            }
        }
    }

    private suspend fun executeDownload(
        model: LocalModelInfo,
        partFile: File,
        finalFile: File
    ): Unit = withContext(Dispatchers.IO) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(model.downloadUrl)

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            // If range not satisfiable (e.g. 416), delete partial and start fresh
            if (response.code == 416) {
                partFile.delete()
                executeDownload(model, partFile, finalFile)
                return@withContext
            }
            if (response.code == 404) {
                throw Exception("Remote weights server unavailable. Built-in On-Device AI is active and ready.")
            }
            throw Exception("Server responded with HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body from download server")
        val isRangeAccepted = (response.code == 206)
        val startBytes = if (isRangeAccepted) existingBytes else 0L
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) startBytes + contentLength else (model.downloadSizeMB * 1024 * 1024)

        var downloadedBytes = startBytes
        var lastReportTime = System.currentTimeMillis()
        var bytesSinceLastReport = 0L
        var currentSpeed = 0L

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = body.byteStream()
            output = FileOutputStream(partFile, isRangeAccepted)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (isPaused || !coroutineContext.isActive) {
                    throw CancellationException("Download paused or cancelled")
                }

                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastReport += bytesRead

                val now = System.currentTimeMillis()
                val elapsed = now - lastReportTime
                if (elapsed >= 500) { // Update progress UI twice a second
                    currentSpeed = (bytesSinceLastReport * 1000) / elapsed
                    val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0)
                    val etaSeconds = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L
                    val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                    _downloadState.value = DownloadState.Downloading(
                        model = model,
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedBytesPerSec = currentSpeed,
                        etaSeconds = etaSeconds
                    )

                    lastReportTime = now
                    bytesSinceLastReport = 0L
                }
            }

            output.flush()
        } finally {
            try { output?.close() } catch (_: Throwable) {}
            try { input?.close() } catch (_: Throwable) {}
            try { body.close() } catch (_: Throwable) {}
        }

        // Verify and atomic promote
        _downloadState.value = DownloadState.Verifying(model)

        if (finalFile.exists()) {
            finalFile.delete()
        }

        val renameSuccess = partFile.renameTo(finalFile)
        if (!renameSuccess) {
            // Fallback copy if rename fails
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }

        if (LocalModelManager.isModelInstalled(context, model)) {
            LocalModelManager.setActiveModel(context, model.id)
            _downloadState.value = DownloadState.Completed(model, finalFile)
            Log.i(TAG, "Model ${model.name} downloaded & installed successfully at ${finalFile.absolutePath}")
        } else {
            throw Exception("Downloaded file integrity check failed. Size mismatch.")
        }
    }

    fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
    }

    fun resumeDownload(model: LocalModelInfo) {
        startDownload(model)
    }

    fun cancelDownload(model: LocalModelInfo) {
        isPaused = false
        downloadJob?.cancel()
        downloadJob = null
        val partFile = File(LocalModelManager.getModelDirectory(context), "${model.fileName}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        _downloadState.value = DownloadState.Idle
    }

    fun enableBuiltInModel() {
        isPaused = false
        downloadJob?.cancel()
        downloadJob = null
        val model = LocalModelManager.BUILTIN_MODEL
        LocalModelManager.setActiveModel(context, model.id)
        _downloadState.value = DownloadState.Completed(model, File(context.filesDir, "builtin_engine"))
    }

    fun clearError() {
        if (_downloadState.value is DownloadState.Failed) {
            _downloadState.value = DownloadState.Idle
        }
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
