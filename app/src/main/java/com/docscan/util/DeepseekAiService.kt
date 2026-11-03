package com.docscan.util

import android.content.Context
import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit

object DeepseekAiService {
    private const val TAG = "DeepseekAiService"
    private const val BASE_URL = "https://api.deepseek.com/chat/completions"
    private const val DEFAULT_MODEL = "deepseek-chat"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isAvailable(context: Context? = null): Boolean {
        val key = AiSettingsManager.getDeepseekKey(context)
        return key.isNotBlank()
    }

    /**
     * Generic caller to DeepSeek Chat API
     */
    suspend fun callDeepseekApi(
        prompt: String,
        temperature: Float = 0.2f,
        model: String = DEFAULT_MODEL,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getDeepseekKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "DeepSeek API key is empty.")
            return@withContext ""
        }

        try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are DeepSeek AI, a world-class document intelligence engine specializing in precise document transcription, structural formatting, table extraction, and reasoning.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
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
                Log.e(TAG, "DeepSeek API HTTP error ${response.code}: $responseBodyString")
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
            Log.e(TAG, "Error executing DeepSeek API request: ${e.message}", e)
            ""
        }
    }

    /**
     * AI-Powered Multi-language OCR refinement and extraction
     */
    suspend fun extractTextAi(bitmap: Bitmap, preferredLanguage: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        // DeepSeek leverages high quality OCR stream + reasoning to correct any scanned character glitches
        val initialOcr = TextRecognizerHelper.extractText(bitmap)
        if (initialOcr.isBlank() || !isAvailable(context)) return@withContext initialOcr

        val prompt = """
            You are DeepSeek OCR Intelligence.
            Clean up, organize, and reconstruct this OCR scanned document text with 100% precision:
            - Fix any typo errors caused by scan artifacts, low light, or font distortions.
            - Rebuild proper sentences, paragraphs, numbered lists, and key-value sections.
            - Retain all numbers, dates, emails, codes, and currency amounts exactly as shown.
            - Return ONLY the clean extracted text without introductory or markdown conversational noise.

            Scanned OCR Input:
            $initialOcr
        """.trimIndent()

        val refined = callDeepseekApi(prompt = prompt, temperature = 0.1f, context = context)
        if (refined.isNotBlank()) refined else initialOcr
    }

    /**
     * AI-Powered High Quality Translation (DeepSeek's Superior Contextual Translation)
     */
    suspend fun translateTextAi(text: String, targetLanguage: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val prompt = """
            Translate the following document text into $targetLanguage with high contextual accuracy, natural phrasing, and perfect grammatical precision.
            Preserve all numerical figures, dates, proper names, and structural formatting.
            Output ONLY the translated text.

            Source Text:
            $text
        """.trimIndent()

        callDeepseekApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    /**
     * AI-Powered Document to Word Converter
     */
    suspend fun convertToWordAi(bitmap: Bitmap, rawText: String? = null, context: Context? = null): String = withContext(Dispatchers.IO) {
        val textToProcess = rawText ?: TextRecognizerHelper.extractText(bitmap)
        if (textToProcess.isBlank()) return@withContext ""

        val prompt = WordLayoutMarkup.buildPrompt(textToProcess)
        val result = callDeepseekApi(prompt = prompt, temperature = 0.1f, context = context)
        if (result.isNotBlank()) WordLayoutMarkup.cleanAiOutput(result)
        else WordLayoutMarkup.buildRailwayFallback(textToProcess)
    }

    /**
     * AI-Powered Document to Excel / Table Extractor (DeepSeek Deep Table Extraction)
     */
    suspend fun convertToExcelAi(bitmap: Bitmap, rawText: String? = null, context: Context? = null): String = withContext(Dispatchers.IO) {
        val textToProcess = rawText ?: TextRecognizerHelper.extractText(bitmap)
        if (textToProcess.isBlank()) return@withContext ""

        val prompt = """
            You are a precise table-extraction engine. I will provide an image or text containing one or more tables (such as salary slips, invoices, point tables, bills, statements, records). Extract the data with perfect structural accuracy.

            RULES:
            1. Identify every row and column exactly as visually aligned. Do not skip empty cells. Empty cells must be output as "".
            2. Handle merged cells correctly. When a cell is merged across multiple rows or columns, preserve the merged structure in the spreadsheet. If the extraction engine requires a flat representation, repeat the value across the merged range and mark the merged structure internally.
            3. Preserve exact numeric formatting exactly as shown (e.g. decimals, currency symbols Tk., $, commas, percentages, negative values, leading zeros, dates, account numbers, employee IDs). Do NOT round, recalculate, normalize, or modify numbers.
            4. Preserve header rows exactly as written, including capitalization, punctuation, abbreviations, units, text inside parentheses.
            5. If multiple tables exist on a page, identify each table separately or output unified structured CSV/TSV table.
            6. Do not invent, infer, or hallucinate missing information.
            7. If a cell is unreadable, output: "[UNREADABLE]". Never guess an unreadable value.
            8. Preserve the visual row/column relationship of the original document.
            9. Do not accidentally combine separate tables.
            10. Do not move values into neighboring cells simply because the OCR result appears more readable.
            11. Preserve blank rows and blank cells whenever they are structurally meaningful.
            12. Detect table boundaries automatically.
            13. Detect column boundaries based on visual alignment rather than OCR text order alone.
            14. Detect row boundaries based on the document's visual layout.
            15. Output ONLY valid CSV format: comma-separated columns, values containing commas or quotes enclosed in double quotes (""), empty cells as "". No extra markdown formatting or conversational remarks.

            Document Content:
            $textToProcess
        """.trimIndent()

        val result = callDeepseekApi(prompt = prompt, temperature = 0.1f, context = context)
        result.replace("```csv", "").replace("```", "").trim()
    }

    suspend fun generateAiDocumentText(type: String, prompt: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            Generate a short, concise, professional text snippet suitable for inserting directly as a text overlay/annotation on a document.
            Type/Category: $type
            User Request/Context: $prompt
            
            Rules:
            1. Keep it brief (1 to 4 lines max) unless specifically asked for a paragraph.
            2. Do NOT enclose in quotation marks or markdown code blocks.
            3. Return only the raw text to be placed onto the document.
        """.trimIndent()

        callDeepseekApi(prompt = systemPrompt, temperature = 0.2f, context = context)
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

        callDeepseekApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    suspend fun summarizeDocumentAi(text: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val prompt = """
            Analyze the following document text and provide a comprehensive executive summary:
            1. Document Classification (Type of document e.g. Tax Invoice, Legal Agreement, Medical Report, ID, Receipt)
            2. Key Highlights (3-5 bullet points of main contents)
            3. Important Figures & Dates (Amounts, ID numbers, dates, deadlines)
            4. Parties Involved (Names, Organizations, Issuers)
            5. Recommended Action Items

            Document Text:
            $text
        """.trimIndent()

        callDeepseekApi(prompt = prompt, temperature = 0.3f, context = context)
    }

    suspend fun editTextAi(originalText: String, instruction: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) return@withContext ""

        val prompt = """
            You are an expert document editor powered by DeepSeek-V3. Apply the following editing instruction precisely to the text:
            Instruction: $instruction
            
            Original Text:
            $originalText

            Rules:
            1. Return ONLY the edited/corrected text ready to replace the original.
            2. Do not include markdown codeblocks, quotes, or conversational explanations.
        """.trimIndent()

        callDeepseekApi(prompt = prompt, temperature = 0.2f, context = context)
    }

    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getDeepseekKey(context)
        if (apiKey.isBlank()) return@withContext ""

        try {
            val messagesArray = JSONArray()

            val systemContext = buildString {
                append("You are the CamScanner AI Document Assistant, an intelligent, helpful document intelligence companion powered by DeepSeek-V3. ")
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
            if (!response.isSuccessful) {
                Log.e(TAG, "DeepSeek chat error: ${response.code} $responseBodyString")
                return@withContext ""
            }

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                return@withContext message?.optString("content")?.trim() ?: ""
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in DeepSeek chatWithAi: ${e.message}", e)
            ""
        }
    }

    suspend fun smartEraseGuideAi(rawText: String?, mode: String = "auto", context: Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a document restoration expert. The user wants to Smart Erase unwanted elements (mode: $mode) from a document with content:
            ${rawText ?: "General Document"}
            
            Provide concise guidance on what areas (stamps, watermarks, handwritten marginalia, or finger shadows) to remove while preserving core text.
        """.trimIndent()
        callDeepseekApi(prompt = prompt, temperature = 0.2f, context = context)
    }
}
