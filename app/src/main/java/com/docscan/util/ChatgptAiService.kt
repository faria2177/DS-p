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

object ChatgptAiService {
    private const val TAG = "ChatgptAiService"
    private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
    private const val DEFAULT_MODEL = "gpt-4o"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isAvailable(context: Context? = null): Boolean {
        val key = AiSettingsManager.getOpenAiKey(context)
        return key.isNotBlank()
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
     * Generic caller to OpenAI Chat Completions API
     */
    suspend fun callOpenAiApi(
        prompt: String,
        bitmap: Bitmap? = null,
        temperature: Float = 0.2f,
        model: String = DEFAULT_MODEL,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getOpenAiKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenAI API key is empty.")
            return@withContext ""
        }

        try {
            val contentArray = JSONArray()

            // 1. Text Prompt
            val textObj = JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            }
            contentArray.put(textObj)

            // 2. Image Part (if provided)
            if (bitmap != null) {
                val base64Data = bitmapToBase64(bitmap)
                val imageUrlObj = JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64Data")
                }
                val imageContentObj = JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", imageUrlObj)
                }
                contentArray.put(imageContentObj)
            }

            val userMessage = JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            }

            val messagesArray = JSONArray().apply {
                put(userMessage)
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", temperature)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI API HTTP error ${response.code}: $responseBodyString")
                return@withContext ""
            }

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                return@withContext message?.optString("content", "")?.trim() ?: ""
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error executing OpenAI API request: ${e.message}", e)
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

        val responseText = callOpenAiApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
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
                Log.e(TAG, "Failed to parse OpenAI corner coordinates: ${e.message}", e)
            }
        }
        null
    }

    /**
     * AI-Powered Multi-language Optical Character Recognition (OCR)
     */
    suspend fun extractTextAi(bitmap: Bitmap, preferredLanguage: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            Perform highly accurate Optical Character Recognition (OCR) on this document/image.
            - Extract all visible text accurately including English, numbers, symbols, and punctuation.
            - Maintain logical paragraph structure, headings, table lines, and bullet lists.
            - Return ONLY the exact extracted text without conversational introductions.
        """.trimIndent()

        callOpenAiApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
    }

    /**
     * AI-Powered High Quality Translation
     */
    suspend fun translateTextAi(text: String, targetLanguage: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val prompt = """
            Translate the following document text into $targetLanguage with high accuracy and natural fluency.
            Preserve all numerical figures, dates, proper names, and formatting structure.
            Do not add conversational fluff or introductory explanations. Output ONLY the translated content.

            Document Content:
            $text
        """.trimIndent()

        callOpenAiApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    /**
     * AI-Powered Document to Word Converter
     */
    suspend fun convertToWordAi(bitmap: Bitmap, rawText: String? = null, context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = WordLayoutMarkup.buildPrompt(rawText)
        val result = callOpenAiApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f, context = context)
        WordLayoutMarkup.cleanAiOutput(result)
    }

    /**
     * AI-Powered Document to Excel / Table Extractor (ChatGPT's Specialty)
     */
    suspend fun convertToExcelAi(
        bitmap: Bitmap,
        rawText: String? = null,
        context: Context? = null,
        structuredLayout: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (structuredLayout) {
            """
            You are a high-precision document-to-Excel vision engine. Reconstruct the actual table layout from the page image.
            Preserve Bengali/Bangla and every other source language exactly. Do not translate.

            Rules:
            - Detect every real table separately from visible rows, columns, ruling lines, alignment and cell boundaries.
            - Do NOT invent Index, Line, Item / Field, Details / Value, OCR Text, No., or helper columns unless they exist in the source.
            - Exclude letterhead logos, contact numbers, email, QR codes, footer/address, watermarks and decorative text unless they are inside table cells.
            - Keep a page/table heading in documentTitle and each immediate section/table heading in table.title.
            - Preserve exact row count, column count, empty cells, Bengali/Western digits, commas, /-, percentages, currency, dates and IDs.
            - Never combine distinct tables just because they share headers.
            - Unreadable cell => [UNREADABLE], never guess.
            - Return ONLY valid JSON, no markdown fences.

            JSON schema:
            {"documentTitle":"","tables":[{"title":"","headers":[""],"rows":[[""]]}]}
            """.trimIndent()
        } else {
            """
            You are an expert tabular data extraction system powered by GPT-4o.
            Analyze the scanned document image and extract only real tabular data into CSV.
            Preserve source language including Bengali and preserve exact numeric formatting.
            Do not invent Index/Line/Field columns. Exclude letterhead/footer/contact text unless it is inside a table.
            Return ONLY raw CSV text without markdown blocks.
            """.trimIndent()
        }

        val result = callOpenAiApi(prompt = prompt, bitmap = bitmap, temperature = 0.05f, context = context)
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

        callOpenAiApi(prompt = systemPrompt, temperature = 0.2f, context = context)
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

        callOpenAiApi(prompt = prompt, temperature = 0.2f, context = context)
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

        callOpenAiApi(prompt = prompt, temperature = 0.3f, context = context)
    }

    suspend fun editTextAi(originalText: String, instruction: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) return@withContext ""

        val prompt = """
            You are an expert document editor powered by GPT-4o. Apply the following editing instruction precisely to the text:
            Instruction: $instruction
            
            Original Text:
            $originalText

            Rules:
            1. Return ONLY the edited/corrected text ready to replace the original.
            2. Do not include markdown codeblocks, quotes, or conversational explanations.
        """.trimIndent()

        callOpenAiApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getOpenAiKey(context)
        if (apiKey.isBlank()) return@withContext ""

        try {
            val messagesArray = JSONArray()

            val systemContext = buildString {
                append("You are the CamScanner AI Document Assistant, an intelligent, helpful document intelligence companion powered by ChatGPT (GPT-4o). ")
                append("You assist users with understanding scanned documents, extracting numbers, dates, summaries, and answering any document-related questions in English or Bengali. ")
                if (!documentContext.isNullOrBlank()) {
                    append("\n\nCurrently Scanned Document Context:\n")
                    append(documentContext)
                }
            }

            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemContext)
            })

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
                put("temperature", 0.3)
                put("max_tokens", 2000)
                put("messages", messagesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext ""

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                return@withContext message?.optString("content")?.trim() ?: ""
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in ChatGPT chatWithAi: ${e.message}", e)
            ""
        }
    }

    suspend fun smartEraseGuideAi(bitmap: Bitmap, mode: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            Analyze this scanned document image for unwanted artifacts to erase (such as finger shadows, watermarks, stamps, smudges, handwriting notes, or stains).
            Mode: $mode
            Provide a clear summary of artifacts found and recommended inpainting regions.
        """.trimIndent()
        callOpenAiApi(prompt = prompt, bitmap = bitmap, temperature = 0.2f, context = context)
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
