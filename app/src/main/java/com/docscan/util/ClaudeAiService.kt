package com.docscan.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

object ClaudeAiService {
    private const val TAG = "ClaudeAiService"
    private const val BASE_URL = "https://api.anthropic.com/v1/messages"
    private const val DEFAULT_MODEL = "claude-3-5-sonnet-20241022"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isAvailable(context: Context? = null): Boolean {
        val key = AiSettingsManager.getClaudeKey(context).trim()
        return key.isNotBlank() && key != "MY_CLAUDE_API_KEY" && (key.startsWith("sk-ant-") || key.length > 25)
    }

    private fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 85): String {
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val scale = maxDim.toFloat() / max(width, height)
            val newW = (width * scale).toInt().coerceAtLeast(1)
            val newH = (height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Generic caller to Anthropic Messages API
     */
    suspend fun callClaudeApi(
        prompt: String,
        bitmap: Bitmap? = null,
        temperature: Float = 0.2f,
        maxTokens: Int = 4000,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getClaudeKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "Claude API key is empty.")
            return@withContext ""
        }

        try {
            val contentArray = JSONArray()

            // 1. Image Block if provided
            if (bitmap != null) {
                val base64Data = bitmapToBase64(bitmap)
                val sourceObj = JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", base64Data)
                }
                val imageObj = JSONObject().apply {
                    put("type", "image")
                    put("source", sourceObj)
                }
                contentArray.put(imageObj)
            }

            // 2. Text Block
            val textObj = JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            }
            contentArray.put(textObj)

            val messageObj = JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            }

            val messagesArray = JSONArray().apply {
                put(messageObj)
            }

            val requestJson = JSONObject().apply {
                put("model", DEFAULT_MODEL)
                put("max_tokens", maxTokens)
                put("temperature", temperature)
                put("messages", messagesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Claude API HTTP error ${response.code}: $responseBodyString")
                return@withContext ""
            }

            val responseJson = JSONObject(responseBodyString)
            val contentList = responseJson.optJSONArray("content")
            if (contentList != null && contentList.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until contentList.length()) {
                    val item = contentList.getJSONObject(i)
                    if (item.optString("type") == "text") {
                        sb.append(item.optString("text"))
                    }
                }
                return@withContext sb.toString().trim()
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Claude API request: ${e.message}", e)
            ""
        }
    }

    /**
     * AI-Powered Document Corner & Edge Detection
     */
    suspend fun detectDocumentEdgesAi(bitmap: Bitmap, context: Context? = null): List<Offset>? = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext null

        val prompt = """
            Analyze this image which contains a document, paper, ID card, book page, or receipt.
            Find the precise 4 corner points of the primary document in clockwise order starting from the Top-Left corner:
            1. Top-Left corner (x1, y1)
            2. Top-Right corner (x2, y2)
            3. Bottom-Right corner (x3, y3)
            4. Bottom-Left corner (x4, y4)
            All coordinates MUST be normalized floating point values strictly between 0.0 and 1.0 (where (0.0, 0.0) is top-left of image and (1.0, 1.0) is bottom-right of image).
            Respond ONLY with a valid JSON object in this exact format:
            {"x1": 0.05, "y1": 0.08, "x2": 0.95, "y2": 0.07, "x3": 0.93, "y3": 0.94, "x4": 0.06, "y4": 0.95}
        """.trimIndent()

        val responseText = callClaudeApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
        if (responseText.isNotBlank()) {
            try {
                val jsonStr = extractJsonFromResponse(responseText)
                val json = JSONObject(jsonStr)
                val x1 = json.optDouble("x1", 0.05).toFloat().coerceIn(0.01f, 0.99f)
                val y1 = json.optDouble("y1", 0.05).toFloat().coerceIn(0.01f, 0.99f)
                val x2 = json.optDouble("x2", 0.95).toFloat().coerceIn(0.01f, 0.99f)
                val y2 = json.optDouble("y2", 0.05).toFloat().coerceIn(0.01f, 0.99f)
                val x3 = json.optDouble("x3", 0.95).toFloat().coerceIn(0.01f, 0.99f)
                val y3 = json.optDouble("y3", 0.95).toFloat().coerceIn(0.01f, 0.99f)
                val x4 = json.optDouble("x4", 0.05).toFloat().coerceIn(0.01f, 0.99f)
                val y4 = json.optDouble("y4", 0.95).toFloat().coerceIn(0.01f, 0.99f)

                return@withContext listOf(
                    Offset(x1, y1),
                    Offset(x2, y2),
                    Offset(x3, y3),
                    Offset(x4, y4)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Claude corner coordinates: ${e.message}", e)
            }
        }
        null
    }

    /**
     * AI-Powered Multi-language OCR
     */
    suspend fun extractTextAi(bitmap: Bitmap, preferredLanguage: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            Perform highly accurate Optical Character Recognition (OCR) on this document/image.
            - Extract all visible text accurately including English, numbers, symbols, and punctuation.
            - Maintain logical paragraph structure, headings, table lines, and bullet lists.
            - Correct any blurry character ambiguities using contextual knowledge.
            - Return ONLY the exact extracted text without conversational introductions.
        """.trimIndent()

        callClaudeApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
    }

    /**
     * AI-Powered High Quality Translation
     */
    suspend fun translateTextAi(text: String, targetLanguage: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val prompt = """
            Translate the following document text into $targetLanguage with high accuracy, natural fluency, and professional terminology.
            Preserve all numerical figures, dates, proper names, and formatting structure.
            Do not add conversational fluff or introductory explanations. Output ONLY the translated content.

            Document Content:
            $text
        """.trimIndent()

        callClaudeApi(prompt = prompt, temperature = 0.3f, context = context)
    }

    /**
     * AI-Powered Document to Word Converter (Claude's Specialty)
     */
    suspend fun convertToWordAi(bitmap: Bitmap, rawText: String? = null, context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = WordLayoutMarkup.buildPrompt(rawText)
        val result = callClaudeApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
        WordLayoutMarkup.cleanAiOutput(result)
    }

    /**
     * AI-Powered Document to Excel / Table Extractor
     */
    suspend fun convertToExcelAi(
        bitmap: Bitmap,
        rawText: String? = null,
        context: Context? = null,
        structuredLayout: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (structuredLayout) {
            """
            You are a high-precision document-to-Excel vision engine. Reconstruct the real spreadsheet/table structure directly from the page image.
            Preserve Bengali/Bangla and all source-language text exactly; never translate.

            Rules:
            1. Detect each real table separately using visual row/column boundaries and alignment.
            2. Never invent generic Index, Line, Item / Field, Details / Value, OCR Text, or No. columns unless present in the source.
            3. Ignore logos, phone/email/website/QR/footer/address/watermark/decorative text unless inside table cells.
            4. Keep the overall table-related page heading in documentTitle and the immediate heading above each table in table.title.
            5. Preserve exact columns, rows, blank cells, Bengali/Western digits, commas, /-, %, currency, dates, IDs and punctuation.
            6. Keep distinct tables separate even when headers match.
            7. Unreadable value => [UNREADABLE]. Do not guess.
            8. Return ONLY valid JSON, no markdown.

            JSON schema:
            {"documentTitle":"","tables":[{"title":"","headers":[""],"rows":[[""]]}]}
            """.trimIndent()
        } else {
            """
            You are an expert tabular data extraction system.
            Extract only actual table data from this scanned page into CSV.
            Preserve source language including Bengali and all exact number formatting.
            Do not invent Index/Line/Field columns. Ignore letterhead/footer/contact text unless inside a real table.
            Return ONLY raw CSV without markdown.
            """.trimIndent()
        }

        val result = callClaudeApi(prompt = prompt, bitmap = bitmap, temperature = 0.05f, context = context)
        result
            .replace("```json", "")
            .replace("```csv", "")
            .replace("```", "")
            .trim()
    }

    suspend fun generateAiDocumentText(type: String, prompt: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are an AI document assistant for a CamScanner professional app.
            Generate a short, concise, professional text snippet suitable for inserting directly as a text overlay/annotation on a document.
            Type/Category: $type
            User Request/Context: $prompt
            
            Rules:
            1. Keep it brief (1 to 4 lines max) unless specifically asked for a paragraph.
            2. Do NOT enclose in quotation marks or markdown code blocks.
            3. Return only the raw text to be placed onto the document.
        """.trimIndent()

        callClaudeApi(prompt = systemPrompt, temperature = 0.2f, context = context)
    }

    suspend fun rephraseTextAi(originalText: String, action: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) return@withContext ""

        val prompt = """
            You are an AI document text editor.
            Original Text:
            $originalText
            
            Action to perform: $action
            
            Return ONLY the processed text without any explanations, markdown, or quotation marks.
        """.trimIndent()

        callClaudeApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    suspend fun summarizeDocumentAi(text: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val prompt = """
            Analyze the following document text and provide an executive summary:
            1. Document Classification (Type of document e.g. Tax Invoice, Legal Agreement, Medical Report, ID, Receipt)
            2. Key Highlights (3-5 bullet points of main contents)
            3. Important Figures & Dates (Amounts, ID numbers, dates, deadlines)
            4. Parties Involved (Names, Organizations, Issuers)
            5. Recommended Action Items

            Document Text:
            $text
        """.trimIndent()

        callClaudeApi(prompt = prompt, temperature = 0.3f, context = context)
    }

    suspend fun editTextAi(originalText: String, instruction: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) return@withContext ""

        val prompt = """
            You are an expert document editor. Apply the following editing instruction precisely to the text:
            Instruction: $instruction
            
            Original Text:
            $originalText

            Rules:
            1. Return ONLY the edited/corrected text ready to replace the original.
            2. Do not include markdown codeblocks, quotes, or conversational explanations.
        """.trimIndent()

        callClaudeApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getClaudeKey(context)
        if (apiKey.isBlank()) return@withContext ""

        try {
            val systemContext = buildString {
                append("You are the CamScanner AI Document Assistant, an intelligent, helpful document intelligence companion powered by Claude 3.5 Sonnet. ")
                append("You assist users with understanding scanned documents, extracting numbers, dates, summaries, and answering any document-related questions in English or Bengali. ")
                if (!documentContext.isNullOrBlank()) {
                    append("\n\nCurrently Scanned Document Context:\n")
                    append(documentContext)
                }
            }

            val messagesArray = JSONArray()
            for ((sender, msg) in history.takeLast(8)) {
                val role = if (sender.equals("You", ignoreCase = true) || sender.equals("User", ignoreCase = true)) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg)
                })
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val requestJson = JSONObject().apply {
                put("model", DEFAULT_MODEL)
                put("max_tokens", 2000)
                put("system", systemContext)
                put("messages", messagesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext ""

            val responseJson = JSONObject(responseBodyString)
            val contentList = responseJson.optJSONArray("content")
            if (contentList != null && contentList.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until contentList.length()) {
                    val item = contentList.getJSONObject(i)
                    if (item.optString("type") == "text") {
                        sb.append(item.optString("text"))
                    }
                }
                return@withContext sb.toString().trim()
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in Claude chatWithAi: ${e.message}", e)
            ""
        }
    }

    suspend fun smartEraseGuideAi(bitmap: Bitmap, mode: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            Analyze this scanned document image for unwanted artifacts to erase (such as finger shadows, watermarks, stamps, smudges, handwriting notes, or stains).
            Mode: $mode
            Provide a clear summary of artifacts found and recommended inpainting regions.
        """.trimIndent()
        callClaudeApi(prompt = prompt, bitmap = bitmap, temperature = 0.2f, context = context)
    }

    private fun extractJsonFromResponse(text: String): String {
        val trimmed = text.trim()
        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return trimmed.substring(startIndex, endIndex + 1)
        }
        return trimmed
    }
}
