package com.docscan.util

import android.content.Context

/** Persisted defaults used for new PDF export dialogs and automatic PDF generation. */
object PdfSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    fun pageSize(context: Context): PageSize = when (prefs(context).getString("doc_page_size", "A4 (Standard)")) {
        "US Letter" -> PageSize.LETTER
        "Legal" -> PageSize.LEGAL
        "Auto / Original" -> PageSize.AUTO
        else -> PageSize.A4
    }

    fun quality(context: Context): Int = when (prefs(context).getString("doc_pdf_quality", "High (Recommended)")) {
        "Original HD (Highest quality)" -> 100
        "Standard (Balanced size)" -> 75
        "Compressed (Small file size)" -> 55
        else -> 90
    }

    fun withDefaults(context: Context, title: String) = PdfExportConfig(
        title = title, pageSize = pageSize(context), quality = quality(context)
    )
}
