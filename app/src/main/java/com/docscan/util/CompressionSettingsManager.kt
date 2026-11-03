package com.docscan.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted compression preferences and settings.
 */
data class CompressionSettings(
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val isCustom: Boolean = false,
    val customQuality: Int = 80,
    val customMaxDimension: Int = 1920,
    val cleanBackground: Boolean = true,
    val sharpenText: Boolean = true,
    val convertToGrayscale: Boolean = false
) {
    val effectiveQuality: Int
        get() = if (isCustom) customQuality else level.jpegQuality

    val effectiveMaxDimension: Int
        get() = if (isCustom) customMaxDimension else level.maxDimension

    val effectiveCleanBackground: Boolean
        get() = if (isCustom) cleanBackground else level.cleanBackground

    val effectiveSharpenText: Boolean
        get() = if (isCustom) sharpenText else level.sharpenText

    fun toConfig(): CompressionConfig {
        return CompressionConfig(
            level = level,
            customQuality = effectiveQuality,
            customMaxDimension = effectiveMaxDimension,
            cleanBackground = effectiveCleanBackground,
            sharpenText = effectiveSharpenText,
            convertToGrayscale = convertToGrayscale
        )
    }

    companion object {
        fun fromLevel(level: CompressionLevel): CompressionSettings {
            return CompressionSettings(
                level = level,
                isCustom = false,
                customQuality = level.jpegQuality,
                customMaxDimension = level.maxDimension,
                cleanBackground = level.cleanBackground,
                sharpenText = level.sharpenText,
                convertToGrayscale = false
            )
        }
    }
}

object CompressionSettingsManager {
    private const val PREFS_NAME = "docscan_compression_preferences"
    private const val KEY_PRESET_LEVEL = "key_preset_level"
    private const val KEY_IS_CUSTOM = "key_is_custom"
    private const val KEY_CUSTOM_QUALITY = "key_custom_quality"
    private const val KEY_CUSTOM_MAX_DIMENSION = "key_custom_max_dimension"
    private const val KEY_CLEAN_BG = "key_clean_background"
    private const val KEY_SHARPEN = "key_sharpen_text"
    private const val KEY_GRAYSCALE = "key_grayscale"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadSettings(context: Context): CompressionSettings {
        val prefs = getPrefs(context)
        val levelName = prefs.getString(KEY_PRESET_LEVEL, CompressionLevel.MEDIUM.name)
        val level = try {
            CompressionLevel.valueOf(levelName ?: CompressionLevel.MEDIUM.name)
        } catch (_: Exception) {
            CompressionLevel.MEDIUM
        }

        val isCustom = prefs.getBoolean(KEY_IS_CUSTOM, false)
        val customQuality = prefs.getInt(KEY_CUSTOM_QUALITY, level.jpegQuality)
        val customMaxDim = prefs.getInt(KEY_CUSTOM_MAX_DIMENSION, level.maxDimension)
        val cleanBg = prefs.getBoolean(KEY_CLEAN_BG, true)
        val sharpen = prefs.getBoolean(KEY_SHARPEN, true)
        val grayscale = prefs.getBoolean(KEY_GRAYSCALE, false)

        return CompressionSettings(
            level = level,
            isCustom = isCustom,
            customQuality = customQuality,
            customMaxDimension = customMaxDim,
            cleanBackground = cleanBg,
            sharpenText = sharpen,
            convertToGrayscale = grayscale
        )
    }

    fun saveSettings(context: Context, settings: CompressionSettings) {
        getPrefs(context).edit()
            .putString(KEY_PRESET_LEVEL, settings.level.name)
            .putBoolean(KEY_IS_CUSTOM, settings.isCustom)
            .putInt(KEY_CUSTOM_QUALITY, settings.customQuality)
            .putInt(KEY_CUSTOM_MAX_DIMENSION, settings.customMaxDimension)
            .putBoolean(KEY_CLEAN_BG, settings.cleanBackground)
            .putBoolean(KEY_SHARPEN, settings.sharpenText)
            .putBoolean(KEY_GRAYSCALE, settings.convertToGrayscale)
            .apply()
    }
}
