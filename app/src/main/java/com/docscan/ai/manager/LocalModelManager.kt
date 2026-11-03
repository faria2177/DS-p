package com.docscan.ai.manager

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.docscan.ai.model.DeviceTier
import com.docscan.ai.model.DeviceSpec
import com.docscan.ai.model.LocalModelInfo
import java.io.File

object LocalModelManager {

    private const val TAG = "LocalModelManager"
    private const val MODEL_DIR_NAME = "ai_models"
    private const val PREFS_NAME = "ai_model_prefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"

    val BUILTIN_MODEL = LocalModelInfo(
        id = "builtin-ondevice-docai",
        name = "DocScan Smart On-Device AI",
        description = "Instant offline AI engine. Document Q&A, summaries, invoice math, and OCR chat with 0 MB download required.",
        parameterSize = "Built-in",
        quantization = "Instant (Zero-Download)",
        downloadSizeMB = 0,
        minRamRequiredGB = 1.0f,
        minStorageRequiredMB = 0,
        fileName = "builtin_engine",
        downloadUrl = "",
        tier = DeviceTier.LOW,
        isRecommended = true
    )

    // Curated catalog of high-efficiency on-device models
    val SUPPORTED_MODELS: List<LocalModelInfo> = listOf(
        BUILTIN_MODEL,
        LocalModelInfo(
            id = "qwen2-0.5b-instruct-int4",
            name = "Qwen2 0.5B Compact",
            description = "Ultra-fast, light on RAM. Optimized for rapid document Q&A and instant offline queries.",
            parameterSize = "0.5B",
            quantization = "4-bit (int4)",
            downloadSizeMB = 385,
            minRamRequiredGB = 1.8f,
            minStorageRequiredMB = 700,
            fileName = "qwen2_0_5b_instruct_int4.bin",
            downloadUrl = "https://storage.googleapis.com/docscan-ai-models/qwen2_0_5b_instruct_int4.bin",
            tier = DeviceTier.LOW
        ),
        LocalModelInfo(
            id = "gemma-2b-it-int4",
            name = "Gemma 2B Instruct",
            description = "Google DeepMind's flagship on-device model. Superior reasoning, translation, and summaries.",
            parameterSize = "2.0B",
            quantization = "4-bit (int4)",
            downloadSizeMB = 1290,
            minRamRequiredGB = 3.5f,
            minStorageRequiredMB = 2200,
            fileName = "gemma_2b_it_int4.bin",
            downloadUrl = "https://storage.googleapis.com/docscan-ai-models/gemma_2b_it_int4.bin",
            tier = DeviceTier.MID
        ),
        LocalModelInfo(
            id = "tinyllama-1.1b-chat-int4",
            name = "TinyLlama 1.1B Chat",
            description = "Balanced compact conversational engine with low thermal footprint.",
            parameterSize = "1.1B",
            quantization = "4-bit (int4)",
            downloadSizeMB = 620,
            minRamRequiredGB = 2.4f,
            minStorageRequiredMB = 1200,
            fileName = "tinyllama_1_1b_chat_int4.bin",
            downloadUrl = "https://storage.googleapis.com/docscan-ai-models/tinyllama_1_1b_chat_int4.bin",
            tier = DeviceTier.LOW
        )
    )

    fun getDeviceSpec(context: Context): DeviceSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamGB = (memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
        val availRamGB = (memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()

        val stat = StatFs(context.filesDir.absolutePath)
        val freeStorageMB = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

        val tier = when {
            totalRamGB >= 7.0f && freeStorageMB >= 4000 -> DeviceTier.HIGH
            totalRamGB >= 3.5f && freeStorageMB >= 2000 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }

        val canRun = freeStorageMB >= 800 && totalRamGB >= 1.5f
        val reason = if (!canRun) {
            if (freeStorageMB < 800) "Insufficient device storage (at least 800 MB required)"
            else "Device RAM is below the 1.5 GB minimum threshold for on-device inference"
        } else null

        return DeviceSpec(
            totalRamGB = totalRamGB,
            availableRamGB = availRamGB,
            freeStorageMB = freeStorageMB,
            androidVersion = Build.VERSION.SDK_INT,
            tier = tier,
            canRunLocalAI = canRun,
            unsupportedReason = reason
        )
    }

    fun getRecommendedModel(context: Context): LocalModelInfo {
        return BUILTIN_MODEL
    }

    fun getModelDirectory(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, modelInfo: LocalModelInfo): File {
        return File(getModelDirectory(context), modelInfo.fileName)
    }

    fun isModelInstalled(context: Context, modelInfo: LocalModelInfo): Boolean {
        if (modelInfo.id == BUILTIN_MODEL.id) return true
        val file = getModelFile(context, modelInfo)
        // File must exist, be non-empty, and be at least 80% of expected download size
        if (!file.exists() || !file.canRead()) return false
        val minExpectedBytes = (modelInfo.downloadSizeMB * 1024 * 1024 * 0.8).toLong()
        return file.length() >= minExpectedBytes
    }

    fun getActiveInstalledModel(context: Context): LocalModelInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SELECTED_MODEL_ID, null)

        if (!savedId.isNullOrBlank()) {
            val savedModel = SUPPORTED_MODELS.firstOrNull { it.id == savedId }
            if (savedModel != null && isModelInstalled(context, savedModel)) {
                return savedModel
            }
        }

        // Return first installed model in order of quality
        for (model in SUPPORTED_MODELS) {
            if (isModelInstalled(context, model)) {
                return model
            }
        }
        return BUILTIN_MODEL
    }

    fun setActiveModel(context: Context, modelId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_MODEL_ID, modelId)
            .apply()
    }

    fun validateModelIntegrity(context: Context, modelInfo: LocalModelInfo): Boolean {
        val file = getModelFile(context, modelInfo)
        if (!file.exists()) return false
        val minExpectedBytes = (modelInfo.downloadSizeMB * 1024 * 1024 * 0.85).toLong()
        return file.length() >= minExpectedBytes
    }

    fun deleteModel(context: Context, modelInfo: LocalModelInfo): Boolean {
        return try {
            val file = getModelFile(context, modelInfo)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted model ${modelInfo.name}: $deleted")
                deleted
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model ${modelInfo.name}: ${e.message}")
            false
        }
    }
}
