package com.docscan.ai.rag

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.docscan.data.model.DocumentEntity
import com.docscan.data.repository.DocumentRepository
import com.docscan.util.TextRecognizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PageTextInfo(
    val pageNumber: Int,
    val text: String
)

object DocumentTextExtractor {

    private const val TAG = "DocTextExtractor"

    suspend fun extractAllText(
        context: Context,
        document: DocumentEntity,
        repository: DocumentRepository
    ): List<PageTextInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PageTextInfo>()

        // 1. Check if document already has combined extracted text
        if (!document.extractedText.isNullOrBlank()) {
            val pages = repository.getPagesDirect(document.id)
            if (pages.isNotEmpty()) {
                for (page in pages) {
                    val pageText = if (!page.extractedText.isNullOrBlank()) {
                        page.extractedText
                    } else {
                        // Extract from page image if page-level text is empty
                        extractFromPageImage(page.processedImagePath)
                    }
                    if (pageText.isNotBlank()) {
                        result.add(PageTextInfo(page.pageNumber, pageText))
                    }
                }
            }
            if (result.isNotEmpty()) return@withContext result

            // Single block fallback
            return@withContext listOf(PageTextInfo(1, document.extractedText))
        }

        // 2. Extract from page images directly via ML Kit OCR
        val pages = repository.getPagesDirect(document.id)
        for (page in pages) {
            val pageText = if (!page.extractedText.isNullOrBlank()) {
                page.extractedText
            } else {
                extractFromPageImage(page.processedImagePath)
            }
            if (pageText.isNotBlank()) {
                result.add(PageTextInfo(page.pageNumber, pageText))
            }
        }

        // 3. If extracted text was found, cache it back to document for instant future chats
        if (result.isNotEmpty()) {
            val combined = result.joinToString("\n\n") { "--- Page ${it.pageNumber} ---\n${it.text}" }
            try {
                repository.updateDocument(document.copy(extractedText = combined))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache extracted text: ${e.message}")
            }
        }

        result
    }

    private suspend fun extractFromPageImage(imagePath: String): String {
        return try {
            val file = File(imagePath)
            if (!file.exists() || file.length() == 0L) return ""
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return ""
            val text = TextRecognizerHelper.extractText(bitmap)
            try { bitmap.recycle() } catch (_: Throwable) {}
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error OCR-ing page image $imagePath: ${e.message}")
            ""
        }
    }
}
