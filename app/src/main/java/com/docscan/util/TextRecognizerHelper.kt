package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object TextRecognizerHelper {
    private const val TAG = "TextRecognizerHelper"
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extracts plain text from a Bitmap using ML Kit on-device text recognition.
     */
    suspend fun extractText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val result = visionText.text.trim()
                        continuation.resume(result)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR recognition error: ${e.message}", e)
                        continuation.resume("")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text extraction: ${e.message}", e)
            ""
        }
    }

    data class TextBlockItem(
        val text: String,
        val rect: Rect,
        val lines: List<TextLineItem> = emptyList()
    )

    data class TextLineItem(
        val text: String,
        val rect: Rect
    )

    /**
     * Extracts recognized text blocks with line-level bounding boxes for precise CamScanner in-place editing.
     */
    suspend fun extractTextWithBlocks(bitmap: Bitmap): List<TextBlockItem> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.mapNotNull { block ->
                            val box = block.boundingBox ?: return@mapNotNull null
                            val lineItems = block.lines.mapNotNull { line ->
                                val lineBox = line.boundingBox ?: return@mapNotNull null
                                TextLineItem(line.text, lineBox)
                            }
                            TextBlockItem(
                                text = block.text,
                                rect = box,
                                lines = lineItems
                            )
                        }
                        continuation.resume(blocks)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR recognition error: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during text extraction: ${e.message}", e)
            emptyList()
        }
    }

    data class WordBoxItem(
        val text: String,
        val rect: Rect
    )

    data class LineWithWordsItem(
        val text: String,
        val rect: Rect,
        val words: List<WordBoxItem>
    )

    /**
     * Extracts recognized lines together with their individual word-level bounding boxes.
     * This positional (geometry) data is required to reconstruct the real row/column
     * structure of a table from a photo — plain flattened OCR text loses that layout info.
     */
    suspend fun extractLinesWithWords(bitmap: Bitmap): List<LineWithWordsItem> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val lines = mutableListOf<LineWithWordsItem>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val lineBox = line.boundingBox ?: continue
                                val words = line.elements.mapNotNull { el ->
                                    val box = el.boundingBox ?: return@mapNotNull null
                                    WordBoxItem(el.text, box)
                                }
                                lines.add(LineWithWordsItem(line.text, lineBox, words))
                            }
                        }
                        continuation.resume(lines)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR recognition error: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during line/word extraction: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Extracts line-by-line items directly for fine-grained selection.
     */
    suspend fun extractTextLines(bitmap: Bitmap): List<TextLineItem> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val lines = mutableListOf<TextLineItem>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox ?: continue
                                lines.add(TextLineItem(line.text, box))
                            }
                        }
                        continuation.resume(lines)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR recognition error: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during line extraction: ${e.message}", e)
            emptyList()
        }
    }
}
