package com.docscan.ai.model

enum class DeviceTier(val displayName: String) {
    LOW("Entry-Level (Efficient)"),
    MID("Standard (Balanced)"),
    HIGH("High Performance")
}

data class LocalModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val parameterSize: String,
    val quantization: String,
    val downloadSizeMB: Long,
    val minRamRequiredGB: Float,
    val minStorageRequiredMB: Long,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String = "",
    val tier: DeviceTier = DeviceTier.MID,
    val isRecommended: Boolean = false
)

data class DeviceSpec(
    val totalRamGB: Float,
    val availableRamGB: Float,
    val freeStorageMB: Long,
    val androidVersion: Int,
    val tier: DeviceTier,
    val canRunLocalAI: Boolean,
    val unsupportedReason: String? = null
)
