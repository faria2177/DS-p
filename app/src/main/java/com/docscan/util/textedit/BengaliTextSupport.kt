package com.docscan.util.textedit

/**
 * Small script-awareness helper for Bengali/Bangla text.
 *
 * We deliberately use Android's system font fallback instead of bundling another
 * font file, so Bangla rendering works without increasing APK size.
 */
object BengaliTextSupport {
    private const val BENGALI_START = 0x0980
    private const val BENGALI_END = 0x09FF
    private const val BENGALI_EXTENDED_START = 0x1C50
    private const val BENGALI_EXTENDED_END = 0x1C7F

    fun containsBangla(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.any { ch ->
            val cp = ch.code
            cp in BENGALI_START..BENGALI_END ||
                cp in BENGALI_EXTENDED_START..BENGALI_EXTENDED_END
        }
    }

    fun normalizedFamily(text: String?, requested: String?): String {
        return if (containsBangla(text)) {
            "BENGALI"
        } else {
            requested?.uppercase()?.ifBlank { "DEFAULT" } ?: "DEFAULT"
        }
    }
}
