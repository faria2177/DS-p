package com.docscan.util.textedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.docscan.util.GeminiAiService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Clean OCR Engine abstraction interface to allow pluggable text recognition engines.
 */
interface TextRecognitionEngine {
    suspend fun recognizeText(bitmap: Bitmap): OcrDocument
}

/**
 * Production-ready Google ML Kit on-device Text Recognition implementation.
 * Extracts full document hierarchy (Blocks -> Lines -> Words) with accurate bounding box
 * coordinates normalized relative to original bitmap dimensions.
 */
class MlKitTextRecognitionEngine : TextRecognitionEngine {

    companion object {
        private const val TAG = "MlKitTextRecognition"
    }

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(bitmap: Bitmap): OcrDocument = withContext(Dispatchers.Default) {
        val origWidth = bitmap.width
        val origHeight = bitmap.height

        // 1. Run Preprocessing on a temporary working copy to enhance faint text
        val preprocessed = try {
            DocumentImagePreprocessor.preprocessForOcr(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Preprocessing failed, falling back to original bitmap: ${e.message}")
            bitmap
        }

        val prepWidth = preprocessed.width
        val prepHeight = preprocessed.height
        val scaleX = origWidth.toFloat() / prepWidth.toFloat()
        val scaleY = origHeight.toFloat() / prepHeight.toFloat()

        try {
            val inputImage = InputImage.fromBitmap(preprocessed, 0)

            val visionText = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        continuation.resume(result)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit OCR failed: ${e.message}", e)
                        continuation.resume(null)
                    }
            }

            if (preprocessed != bitmap) {
                preprocessed.recycle()
            }

            if (visionText == null || visionText.textBlocks.isEmpty()) {
                return@withContext OcrDocument(
                    fullText = "",
                    blocks = emptyList(),
                    imageWidth = origWidth,
                    imageHeight = origHeight
                )
            }

            // Map OCR elements back to original image coordinate space
            val parsedBlocks = mutableListOf<OcrTextBlock>()

            for (block in visionText.textBlocks) {
                val blockBox = block.boundingBox ?: continue

                val origBlockPixel = Rect(
                    (blockBox.left * scaleX).toInt().coerceIn(0, origWidth),
                    (blockBox.top * scaleY).toInt().coerceIn(0, origHeight),
                    (blockBox.right * scaleX).toInt().coerceIn(0, origWidth),
                    (blockBox.bottom * scaleY).toInt().coerceIn(0, origHeight)
                )

                val blockNormRect = RectF(
                    origBlockPixel.left.toFloat() / origWidth.toFloat(),
                    origBlockPixel.top.toFloat() / origHeight.toFloat(),
                    origBlockPixel.right.toFloat() / origWidth.toFloat(),
                    origBlockPixel.bottom.toFloat() / origHeight.toFloat()
                )

                // Style estimation for the block
                val blockStyle = TextStyleEstimator.estimate(bitmap, origBlockPixel, recognizedText = block.text)

                val parsedLines = mutableListOf<OcrTextLine>()

                for (line in block.lines) {
                    val lineBox = line.boundingBox ?: continue

                    val origLinePixel = Rect(
                        (lineBox.left * scaleX).toInt().coerceIn(0, origWidth),
                        (lineBox.top * scaleY).toInt().coerceIn(0, origHeight),
                        (lineBox.right * scaleX).toInt().coerceIn(0, origWidth),
                        (lineBox.bottom * scaleY).toInt().coerceIn(0, origHeight)
                    )

                    val lineNormRect = RectF(
                        origLinePixel.left.toFloat() / origWidth.toFloat(),
                        origLinePixel.top.toFloat() / origHeight.toFloat(),
                        origLinePixel.right.toFloat() / origWidth.toFloat(),
                        origLinePixel.bottom.toFloat() / origHeight.toFloat()
                    )

                    val lineStyle = TextStyleEstimator.estimate(bitmap, origLinePixel, recognizedText = line.text)

                    val parsedWords = mutableListOf<OcrTextElement>()
                    for (element in line.elements) {
                        val elemBox = element.boundingBox ?: continue
                        val origElemPixel = Rect(
                            (elemBox.left * scaleX).toInt().coerceIn(0, origWidth),
                            (elemBox.top * scaleY).toInt().coerceIn(0, origHeight),
                            (elemBox.right * scaleX).toInt().coerceIn(0, origWidth),
                            (elemBox.bottom * scaleY).toInt().coerceIn(0, origHeight)
                        )
                        val elemNormRect = RectF(
                            origElemPixel.left.toFloat() / origWidth.toFloat(),
                            origElemPixel.top.toFloat() / origHeight.toFloat(),
                            origElemPixel.right.toFloat() / origWidth.toFloat(),
                            origElemPixel.bottom.toFloat() / origHeight.toFloat()
                        )
                        parsedWords.add(
                            OcrTextElement(
                                text = element.text,
                                pixelRect = origElemPixel,
                                normalizedRect = elemNormRect,
                                confidence = element.confidence
                            )
                        )
                    }

                    parsedLines.add(
                        OcrTextLine(
                            text = line.text,
                            pixelRect = origLinePixel,
                            normalizedRect = lineNormRect,
                            elements = parsedWords,
                            confidence = line.confidence,
                            rotationAngle = line.angle,
                            estimatedTextSizeSp = lineStyle.fontSizeSp,
                            estimatedTextColor = lineStyle.textColor,
                            estimatedBackgroundColor = lineStyle.backgroundColor,
                            estimatedFontStyle = lineStyle.fontStyle
                        )
                    )
                }

                parsedBlocks.add(
                    OcrTextBlock(
                        text = block.text,
                        pixelRect = origBlockPixel,
                        normalizedRect = blockNormRect,
                        lines = parsedLines,
                        rotationAngle = 0f,
                        estimatedTextSizeSp = blockStyle.fontSizeSp,
                        estimatedTextColor = blockStyle.textColor,
                        estimatedBackgroundColor = blockStyle.backgroundColor,
                        estimatedFontStyle = blockStyle.fontStyle
                    )
                )
            }

            OcrDocument(
                fullText = visionText.text,
                blocks = parsedBlocks,
                imageWidth = origWidth,
                imageHeight = origHeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during OCR parsing: ${e.message}", e)
            OcrDocument(
                fullText = "",
                blocks = emptyList(),
                imageWidth = origWidth,
                imageHeight = origHeight
            )
        }
    }
}


/**
 * Edit-PDF OCR pipeline:
 * 1) ML Kit on-device OCR remains the fast/default engine.
 * 2) When a Gemini key is configured, a structured multilingual OCR pass is
 *    merged in for Bengali/Bangla and for visual text lines ML Kit missed.
 *
 * This keeps document scanning itself on ML Kit while adding accurate selectable
 * Bangla text without bundling a large OCR model into the APK.
 */
class HybridTextRecognitionEngine(
    context: Context
) : TextRecognitionEngine {
    private val appContext = context.applicationContext
    private val mlKit = MlKitTextRecognitionEngine()

    override suspend fun recognizeText(bitmap: Bitmap): OcrDocument {
        val mlDocument = mlKit.recognizeText(bitmap)
        if (!GeminiAiService.isApiKeyValid(appContext)) {
            return mlDocument
        }

        val raw = try {
            GeminiAiService.extractStructuredOcrAi(bitmap, appContext)
        } catch (_: Exception) {
            ""
        }
        if (raw.isBlank()) return mlDocument

        val aiDocument = parseStructuredOcr(raw, bitmap) ?: return mlDocument
        if (aiDocument.blocks.isEmpty()) return mlDocument

        val mergedBlocks = mlDocument.blocks.toMutableList()
        val existingRects = mlDocument.allLines.map { it.normalizedRect }

        for (aiBlock in aiDocument.blocks) {
            val aiLine = aiBlock.lines.firstOrNull()
            val rect = aiLine?.normalizedRect ?: aiBlock.normalizedRect
            val text = aiLine?.text ?: aiBlock.text

            val isBangla = BengaliTextSupport.containsBangla(text)
            val overlapsKnown = existingRects.any { intersectionOverUnion(it, rect) >= 0.45f }

            // Always retain Bangla lines; for other scripts only add visual lines
            // that the local recognizer did not already cover.
            if (isBangla || !overlapsKnown) {
                mergedBlocks += aiBlock
            }
        }

        val sorted = mergedBlocks.sortedWith(
            compareBy<OcrTextBlock> { it.normalizedRect.top }
                .thenBy { it.normalizedRect.left }
        )
        val fullText = sorted.joinToString("\n") { it.text }.trim()

        return OcrDocument(
            fullText = fullText,
            blocks = sorted,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private fun parseStructuredOcr(raw: String, bitmap: Bitmap): OcrDocument? {
        return try {
            val jsonText = raw
                .replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            val start = jsonText.indexOf('{')
            val end = jsonText.lastIndexOf('}')
            if (start < 0 || end <= start) return null

            val root = JSONObject(jsonText.substring(start, end + 1))
            val lines = root.optJSONArray("lines") ?: return null
            val blocks = mutableListOf<OcrTextBlock>()

            for (i in 0 until lines.length()) {
                val item = lines.optJSONObject(i) ?: continue
                val text = item.optString("text", "").trim()
                if (text.isBlank()) continue

                var x1 = item.optDouble("x1", 0.0).toFloat().coerceIn(0f, 1f)
                var y1 = item.optDouble("y1", 0.0).toFloat().coerceIn(0f, 1f)
                var x2 = item.optDouble("x2", 1.0).toFloat().coerceIn(0f, 1f)
                var y2 = item.optDouble("y2", 1.0).toFloat().coerceIn(0f, 1f)
                if (x2 < x1) {
                    val tmp = x1
                    x1 = x2
                    x2 = tmp
                }
                if (y2 < y1) {
                    val tmp = y1
                    y1 = y2
                    y2 = tmp
                }
                if (x2 - x1 < 0.002f || y2 - y1 < 0.002f) continue

                val norm = RectF(x1, y1, x2, y2)
                val pixel = Rect(
                    (x1 * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
                    (y1 * bitmap.height).toInt().coerceIn(0, bitmap.height - 1),
                    (x2 * bitmap.width).toInt().coerceIn(1, bitmap.width),
                    (y2 * bitmap.height).toInt().coerceIn(1, bitmap.height)
                )
                if (pixel.right <= pixel.left || pixel.bottom <= pixel.top) continue

                val style = TextStyleEstimator.estimate(
                    bitmap = bitmap,
                    pixelRect = pixel,
                    recognizedText = text
                )
                val line = OcrTextLine(
                    text = text,
                    pixelRect = pixel,
                    normalizedRect = RectF(norm),
                    elements = emptyList(),
                    estimatedTextSizeSp = style.fontSizeSp,
                    estimatedTextColor = style.textColor,
                    estimatedBackgroundColor = style.backgroundColor,
                    estimatedFontStyle = style.fontStyle
                )
                blocks += OcrTextBlock(
                    text = text,
                    pixelRect = pixel,
                    normalizedRect = RectF(norm),
                    lines = listOf(line),
                    estimatedTextSizeSp = style.fontSizeSp,
                    estimatedTextColor = style.textColor,
                    estimatedBackgroundColor = style.backgroundColor,
                    estimatedFontStyle = style.fontStyle
                )
            }

            OcrDocument(
                fullText = blocks.joinToString("\n") { it.text },
                blocks = blocks,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            Log.w("HybridTextRecognition", "Structured OCR parse failed: ${e.message}")
            null
        }
    }

    private fun intersectionOverUnion(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val iw = (right - left).coerceAtLeast(0f)
        val ih = (bottom - top).coerceAtLeast(0f)
        val inter = iw * ih
        if (inter <= 0f) return 0f
        val areaA = (a.width() * a.height()).coerceAtLeast(0f)
        val areaB = (b.width() * b.height()).coerceAtLeast(0f)
        val union = areaA + areaB - inter
        return if (union > 0f) inter / union else 0f
    }
}
