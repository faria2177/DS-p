package com.docscan.util

import android.content.Context
import android.content.SharedPreferences
import com.docscan.BuildConfig

enum class AiProviderType(val displayName: String, val brandIcon: String, val desc: String) {
    GEMINI("Google Gemini 3.5", "✨", "High-speed multimodal vision & edge detection"),
    AUTO_ENSEMBLE("Smart AI Ensemble", "⚡", "Intelligently combines Google Gemini with document AI"),
    CLAUDE("Anthropic Claude", "🟣", "Master of document layouts, formatting & Word docs"),
    CHATGPT("OpenAI ChatGPT (GPT-4o)", "🟢", "Multi-modal vision, Excel tables & accurate OCR"),
    GROQ("Groq (Llama / OSS, ultra-fast)", "🟠", "Extremely low-latency chat via Groq's LPU inference"),
    OPENROUTER("OpenRouter (multi-model)", "🌐", "Routes chat to any model available on OpenRouter")
}

object AiSettingsManager {
    private const val PREFS_NAME = "ai_provider_preferences"
    private const val KEY_ACTIVE_PROVIDER = "key_active_ai_provider"
    private const val KEY_CUSTOM_GEMINI_KEY = "custom_gemini_api_key"
    private const val KEY_CUSTOM_CLAUDE_KEY = "custom_claude_api_key"
    private const val KEY_CUSTOM_OPENAI_KEY = "custom_openai_api_key"
    private const val KEY_CUSTOM_DEEPSEEK_KEY = "custom_deepseek_api_key"
    private const val KEY_CUSTOM_GROQ_KEY = "custom_groq_api_key"
    private const val KEY_CUSTOM_OPENROUTER_KEY = "custom_openrouter_api_key"
    private const val KEY_ENABLE_ENSEMBLE_FALLBACK = "key_enable_ensemble_fallback"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveProvider(context: Context): AiProviderType {
        val name = getPrefs(context).getString(KEY_ACTIVE_PROVIDER, AiProviderType.GEMINI.name)
        return try {
            AiProviderType.valueOf(name ?: AiProviderType.GEMINI.name)
        } catch (e: Exception) {
            AiProviderType.GEMINI
        }
    }

    fun setActiveProvider(context: Context, provider: AiProviderType) {
        getPrefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun isEnsembleFallbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_ENSEMBLE_FALLBACK, true)
    }

    fun setEnsembleFallbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_ENSEMBLE_FALLBACK, enabled).apply()
    }

    // --- Key Retrievers (User Custom Key -> BuildConfig Key -> Empty) ---
    fun getGeminiKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_GEMINI_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            if (k != "MY_GEMINI_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomGeminiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_GEMINI_KEY, key.trim()).apply()
    }

    fun getClaudeKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_CLAUDE_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("CLAUDE_API_KEY").get(null) as? String ?: ""
            if (k != "MY_CLAUDE_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomClaudeKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_CLAUDE_KEY, key.trim()).apply()
    }

    fun getOpenAiKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_OPENAI_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("OPENAI_API_KEY").get(null) as? String ?: ""
            if (k != "MY_OPENAI_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomOpenAiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_OPENAI_KEY, key.trim()).apply()
    }

    fun getDeepseekKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_DEEPSEEK_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("DEEPSEEK_API_KEY").get(null) as? String ?: ""
            if (k != "MY_DEEPSEEK_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomDeepseekKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_DEEPSEEK_KEY, key.trim()).apply()
    }

    fun getGroqKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_GROQ_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("GROQ_API_KEY").get(null) as? String ?: ""
            if (k != "MY_GROQ_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomGroqKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_GROQ_KEY, key.trim()).apply()
    }

    fun getOpenRouterKey(context: Context? = null): String {
        context?.let {
            val custom = getPrefs(it).getString(KEY_CUSTOM_OPENROUTER_KEY, "") ?: ""
            if (custom.isNotBlank()) return custom.trim()
        }
        return try {
            val k = BuildConfig::class.java.getField("OPENROUTER_API_KEY").get(null) as? String ?: ""
            if (k != "MY_OPENROUTER_API_KEY") k else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setCustomOpenRouterKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_OPENROUTER_KEY, key.trim()).apply()
    }

    fun getProviderStatusSummary(context: Context? = null): Map<AiProviderType, Boolean> {
        return mapOf(
            AiProviderType.GEMINI to getGeminiKey(context).isNotBlank(),
            AiProviderType.CLAUDE to getClaudeKey(context).isNotBlank(),
            AiProviderType.CHATGPT to getOpenAiKey(context).isNotBlank(),
            AiProviderType.GROQ to getGroqKey(context).isNotBlank(),
            AiProviderType.OPENROUTER to getOpenRouterKey(context).isNotBlank(),
            AiProviderType.AUTO_ENSEMBLE to true
        )
    }
}
