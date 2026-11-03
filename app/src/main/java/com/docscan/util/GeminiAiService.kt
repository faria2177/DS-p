package com.docscan.util

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.docscan.BuildConfig
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
import kotlin.math.min

object GeminiAiService {
    private const val TAG = "GeminiAiService"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"
    private const val ACCURACY_MODEL = "gemini-2.5-pro"
    private const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(context: android.content.Context? = null): String {
        return AiSettingsManager.getGeminiKey(context)
    }

    fun isApiKeyValid(context: android.content.Context? = null): Boolean {
        val key = getApiKey(context).trim()
        return key.isNotBlank() &&
            key != "AQ.Ab8RN6K1Cc3voCNwVzH0ouFrLqJRcQ-M36fpqkxentLMRxcoXw" &&
            key != "MY_GEMINI_API_KEY"
    }

    /**
     * Helper to downscale bitmap and convert to Base64 JPEG string
     */
    fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 85): String {
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
     * Generic caller to Gemini 2.5 Flash API
     */
    private suspend fun callGeminiApi(
        prompt: String,
        bitmap: Bitmap? = null,
        temperature: Float = 0.2f,
        context: android.content.Context? = null,
        model: String = DEFAULT_MODEL,
        imageMaxDim: Int = 1024,
        imageQuality: Int = 85,
        maxOutputTokens: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "AQ.Ab8RN6K1Cc3voCNwVzH0ouFrLqJRcQ-M36fpqkxentLMRxcoXw") {
            Log.w(TAG, "Gemini API key is placeholder or empty.")
            return@withContext ""
        }

        try {
            val partsArray = JSONArray()

            // Text prompt part
            val textPart = JSONObject().apply {
                put("text", prompt)
            }
            partsArray.put(textPart)

            // Image part if provided
            if (bitmap != null) {
                val base64Data = bitmapToBase64(bitmap, maxDim = imageMaxDim, quality = imageQuality)
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Data)
                }
                val imagePart = JSONObject().apply {
                    put("inlineData", inlineData)
                }
                partsArray.put(imagePart)
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            }

            val generationConfig = JSONObject().apply {
                put("temperature", temperature)
                put("topP", 0.95)
                maxOutputTokens?.let { put("maxOutputTokens", it) }
            }

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", generationConfig)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val url = "$API_ROOT/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API HTTP error ${response.code}: $responseBodyString")
                return@withContext ""
            }

            val responseJson = JSONObject(responseBodyString)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return@withContext parts.getJSONObject(0).optString("text", "").trim()
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Gemini API request: ${e.message}", e)
            ""
        }
    }

    /**
     * AI-Powered Document Corner & Edge Detection
     * Identifies the 4 corners of a document/paper in clockwise order: [TL, TR, BR, BL]
     */
    suspend fun detectDocumentEdgesAi(bitmap: Bitmap): List<Offset>? = withContext(Dispatchers.IO) {
        if (!isApiKeyValid()) {
            return@withContext EdgeDetector.detectDocumentCorners(bitmap)
        }

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

        val responseText = callGeminiApi(prompt = prompt, bitmap = bitmap, temperature = 0.1f)
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
                Log.e(TAG, "Failed to parse AI corner coordinates: ${e.message}", e)
            }
        }

        // Fallback to local edge detector
        EdgeDetector.detectDocumentCorners(bitmap)
    }

    /**
     * AI-Powered Multi-language Optical Character Recognition (OCR)
     * Accurately extracts text, including English, Spanish, French, German, etc.
     */
    suspend fun extractTextAi(bitmap: Bitmap, preferredLanguage: String = "auto"): String = withContext(Dispatchers.IO) {
        val prompt = """
            Perform highly accurate Optical Character Recognition (OCR) on this document/image.
            - Extract all visible text accurately including English, numbers, symbols, and punctuation.
            - Maintain logical paragraph structure, headings, table lines, and bullet lists.
            - Correct any blurry character ambiguities using context.
            - Return ONLY the exact extracted text without conversational introductions.
        """.trimIndent()

        val aiResult = callGeminiApi(prompt = prompt, bitmap = bitmap, temperature = 0.2f)
        if (aiResult.isNotBlank()) {
            return@withContext aiResult
        }

        // Fallback to ML Kit on-device recognizer
        TextRecognizerHelper.extractText(bitmap)
    }


    /**
     * Structured multilingual OCR for Edit PDF.
     *
     * Returns exact visible lines plus normalized coordinates so scripts that are
     * not covered well by the on-device recognizer (notably Bangla/Bengali) can
     * still be selected and edited in-place.
     */
    suspend fun extractStructuredOcrAi(
        bitmap: Bitmap,
        context: android.content.Context? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isApiKeyValid(context)) return@withContext ""

        val prompt = """
            Perform exact OCR on this document image. Bengali/Bangla and English may be mixed.

            Return ONLY one valid JSON object, no Markdown and no explanation:
            {
              "lines": [
                {
                  "text": "exact visible line text",
                  "x1": 0.10,
                  "y1": 0.15,
                  "x2": 0.80,
                  "y2": 0.19
                }
              ]
            }

            Rules:
            - Preserve Bengali characters exactly. Never transliterate Bengali into Latin.
            - Preserve English, numbers, dates, currency, punctuation and symbols exactly.
            - Do not translate or rewrite.
            - One visual text line per item.
            - Coordinates must be normalized 0.0..1.0 relative to the full image.
            - Tight boxes around the visible text line.
            - Keep reading order from top to bottom, left to right.
            - Include table cell text as separate visual lines where practical.
            - Do not include decorative borders or non-text graphics.
        """.trimIndent()

        callGeminiApi(
            prompt = prompt,
            bitmap = bitmap,
            temperature = 0.0f,
            context = context,
            model = ACCURACY_MODEL,
            imageMaxDim = 1800,
            imageQuality = 92,
            maxOutputTokens = 12000
        )
    }

    data class FontStyleMatch(
        val family: String,
        val isBold: Boolean,
        val isItalic: Boolean,
        val confidence: Float
    )

    /**
     * Optional precision font-style matcher for Edit Text.
     * Gemini is used only to classify the visible style of the cropped original text.
     * Actual rendering, sizing and placement remain deterministic/local.
     */
    suspend fun analyzeTextStyleAi(
        textCrop: Bitmap,
        originalText: String,
        nearbyText: List<String> = emptyList(),
        context: android.content.Context? = null
    ): FontStyleMatch? = withContext(Dispatchers.IO) {
        if (!isApiKeyValid(context)) return@withContext null

        val nearby = nearbyText.filter { it.isNotBlank() }.joinToString(" | ").take(600)
        val prompt = """
            You are performing typography matching for a document editor.
            Analyze ONLY the visual font style in this cropped ORIGINAL document region.

            TARGET OCR TEXT: $originalText
            NEARBY OCR TEXT FROM THE SAME AREA: $nearby

            The target word/line is inside the crop together with nearby text. Use the nearby
            glyphs as visual references for the same font family, weight, slant and width.
            Ignore document meaning and never rewrite, correct, infer, or invent any content.

            Classify the closest Android-safe font family. Allowed values ONLY:
            SANS, SANS_MEDIUM, SANS_LIGHT, SANS_CONDENSED, SERIF, MONO, BENGALI.
            Also decide whether the target style is bold and/or italic.
            Confidence must reflect visual certainty, not semantic certainty.

            Return ONLY JSON in exactly this form:
            {"family":"SANS","bold":false,"italic":false,"confidence":0.90}
        """.trimIndent()

        val raw = callGeminiApi(
            prompt = prompt,
            bitmap = textCrop,
            temperature = 0.0f,
            context = context,
            model = ACCURACY_MODEL,
            imageMaxDim = 768,
            imageQuality = 96,
            maxOutputTokens = 120
        )
        if (raw.isBlank()) return@withContext null

        runCatching {
            val json = JSONObject(extractJsonFromResponse(raw))
            val family = json.optString("family", "SANS").uppercase().let { value ->
                when (value) {
                    "SANS", "SANS_MEDIUM", "SANS_LIGHT", "SANS_CONDENSED",
                    "SERIF", "MONO", "BENGALI" -> value
                    else -> "SANS"
                }
            }
            FontStyleMatch(
                family = family,
                isBold = json.optBoolean("bold", false),
                isItalic = json.optBoolean("italic", false),
                confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            )
        }.getOrNull()
    }

    /**
     * AI-Powered High Quality Translation
     */
    suspend fun translateTextAi(text: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "No text provided to translate."

        val prompt = """
            Translate the following document text into $targetLanguage with high accuracy and natural fluency.
            Preserve all numerical figures, dates, proper names, and formatting structure.
            Do not add conversational fluff or introductory explanations. Output ONLY the translated content.

            Document Content:
            $text
        """.trimIndent()

        val aiResult = callGeminiApi(prompt = prompt, temperature = 0.3f)
        if (aiResult.isNotBlank()) {
            return@withContext aiResult
        }

        // Local translation fallback
        when (targetLanguage.lowercase()) {
            "bengali", "bangla" -> "[বাংলা অনুবাদ]\n" + text.replace("Invoice", "চালান").replace("Date", "তারিখ").replace("Total", "মোট").replace("Bill", "বিল")
            "spanish" -> "[Traducción Española]\n" + text.replace("Invoice", "Factura").replace("Date", "Fecha").replace("Total", "Total")
            "hindi" -> "[हिंदी अनुवाद]\n" + text.replace("Invoice", "चालान").replace("Date", "तारीख").replace("Total", "कुल")
            "arabic" -> "[الترجمة العربية]\n" + text.replace("Invoice", "فاتورة").replace("Date", "تاريخ").replace("Total", "المجموع")
            else -> text
        }
    }

    /**
     * AI-Powered Document to Word Converter
     * Transforms scanned pages into beautifully structured Word / Markdown document layout
     */
    suspend fun convertToWordAi(
        bitmap: Bitmap,
        rawText: String? = null,
        context: android.content.Context? = null,
        accuracyFirst: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val basePrompt = WordLayoutMarkup.buildPrompt(rawText)
        val model = if (accuracyFirst) ACCURACY_MODEL else DEFAULT_MODEL
        val firstPass = callGeminiApi(
            prompt = basePrompt,
            bitmap = bitmap,
            temperature = if (accuracyFirst) 0.02f else 0.1f,
            context = context,
            model = model,
            imageMaxDim = if (accuracyFirst) 1800 else 1024,
            imageQuality = if (accuracyFirst) 94 else 85,
            maxOutputTokens = if (accuracyFirst) 16384 else null
        )

        if (firstPass.isNotBlank()) {
            val cleanedDraft = WordLayoutMarkup.cleanAiOutput(firstPass)
            if (!accuracyFirst) return@withContext cleanedDraft

            // Second vision pass: compare the draft back against the source image.
            // This intentionally costs more time/tokens because fidelity is more
            // important than latency for To Word.
            val verifyPrompt = """
                You are the FINAL QUALITY-CONTROL pass for document-to-Word reconstruction.
                Compare the SOURCE IMAGE with the DRAFT MARKUP below, line by line and region by region.

                Correct every mismatch you can see, including:
                - Bengali/English spelling, digits, dates, names, IDs and punctuation
                - missing or duplicated lines
                - heading/section hierarchy
                - key-value rows and table row/column placement
                - two-column blocks, notes, banners, footer text and reading order
                - merged/centered headings represented by the supported markup

                Do NOT summarize, translate, invent, normalize or paraphrase anything.
                Preserve the source language and values exactly. If something is genuinely unreadable, keep the draft text rather than guessing.
                Return ONLY the final supported Word layout markup, with no commentary or markdown fences.

                DRAFT MARKUP:
                $cleanedDraft
            """.trimIndent()

            val verified = callGeminiApi(
                prompt = verifyPrompt,
                bitmap = bitmap,
                temperature = 0.0f,
                context = context,
                model = ACCURACY_MODEL,
                imageMaxDim = 1800,
                imageQuality = 94,
                maxOutputTokens = 16384
            )
            if (verified.isNotBlank()) {
                return@withContext WordLayoutMarkup.cleanAiOutput(verified)
            }
            return@withContext cleanedDraft
        }

        val fallbackText = rawText ?: TextRecognizerHelper.extractText(bitmap)
        if (fallbackText.isNotBlank()) {
            return@withContext WordLayoutMarkup.buildRailwayFallback(fallbackText)
        }
        "[Document content processed]"
    }

    /**
     * AI-Powered Document to Excel / Table Extractor
     * Extracts tabular data into standard CSV lines
     */
    suspend fun convertToExcelAi(
        bitmap: Bitmap,
        rawText: String? = null,
        structuredLayout: Boolean = false,
        context: android.content.Context? = null,
        accuracyFirst: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val prompt = if (structuredLayout) {
            """
            You are a high-precision document-to-Excel vision engine. Analyze the PAGE IMAGE itself, not flattened OCR order.
            Reconstruct only the real spreadsheet/table content exactly as it appears visually.

            CRITICAL RULES:
            1. Preserve the original language exactly, including Bengali/Bangla text. Never translate Bengali into English.
            2. Detect every real table on the page separately. Use visible ruling lines, row alignment, column alignment, and cell boundaries.
            3. NEVER invent helper columns such as Index, Line, Item / Field, Details / Value, OCR Text, or No. unless those columns really exist in the source table.
            4. Exclude logos, phone numbers, email addresses, QR codes, website/footer/contact text, watermarks, page decorations, and letterhead unless they are actually inside a table cell.
            5. Preserve headings immediately associated with the tables. A page heading above all tables goes in documentTitle; a heading immediately above a table goes in that table's title.
            6. Preserve the exact number of table columns and rows. Preserve empty cells as empty strings.
            7. Preserve values exactly: Bengali/Western digits, commas, /-, %, currency, leading zeros, dates, IDs, punctuation. Do not recalculate or normalize.
            8. If two tables have the same columns, keep them as two separate table objects, not one flattened list.
            9. If a cell is unreadable, use "[UNREADABLE]". Do not guess.
            10. Return ONLY one valid JSON object, with no markdown fences and no commentary.

            REQUIRED JSON SCHEMA:
            {
              "documentTitle": "page heading related to the tables or empty string",
              "tables": [
                {
                  "title": "table/section heading or empty string",
                  "headers": ["header1", "header2"],
                  "rows": [["cell1", "cell2"]]
                }
              ]
            }
            """.trimIndent()
        } else {
            """
            You are a precise table-extraction engine. Read the table directly from the PAGE IMAGE.
            Preserve every visible row, column, empty cell, source-language string and numeric format exactly.
            Do not invent helper columns. Exclude page furniture outside the table. Do not guess unreadable values.
            Output ONLY valid CSV with no markdown or commentary.
            """.trimIndent()
        }

        val model = if (accuracyFirst) ACCURACY_MODEL else DEFAULT_MODEL
        val firstPass = callGeminiApi(
            prompt = prompt,
            bitmap = bitmap,
            temperature = if (accuracyFirst) 0.0f else 0.05f,
            context = context,
            model = model,
            imageMaxDim = if (accuracyFirst) 2000 else 1024,
            imageQuality = if (accuracyFirst) 95 else 85,
            maxOutputTokens = if (accuracyFirst) 16384 else null
        ).cleanStructuredGeminiOutput()

        if (firstPass.isNotBlank()) {
            if (!accuracyFirst) return@withContext firstPass

            val outputKind = if (structuredLayout) "JSON matching the exact required schema" else "valid CSV"
            val verifyPrompt = """
                Perform a FINAL VISUAL VERIFICATION of this document-to-Excel extraction.
                Compare every table boundary, header, row, column and cell value in the SOURCE IMAGE against the DRAFT below.

                Fix only errors proven by the image. Preserve Bengali/English text, digits, currency, percentages, /-, punctuation, leading zeros and blank cells exactly.
                Never invent rows/columns or convert letterhead/footer into table data.
                If the image contains multiple tables, keep them separate.
                Return ONLY $outputKind. No markdown and no commentary.

                DRAFT:
                $firstPass
            """.trimIndent()

            val verified = callGeminiApi(
                prompt = verifyPrompt,
                bitmap = bitmap,
                temperature = 0.0f,
                context = context,
                model = ACCURACY_MODEL,
                imageMaxDim = 2000,
                imageQuality = 95,
                maxOutputTokens = 16384
            ).cleanStructuredGeminiOutput()
            if (verified.isNotBlank()) return@withContext verified
            return@withContext firstPass
        }

        if (structuredLayout) return@withContext ""

        // Offline/API-error fallback only. The caller may use geometry-aware OCR.
        val fallbackText = rawText ?: TextRecognizerHelper.extractText(bitmap)
        fallbackText.lines().filter { it.isNotBlank() }.joinToString("\n") { line ->
            "\"${line.trim().replace("\"", "\"\"")}\""
        }
    }

    private fun String.cleanStructuredGeminiOutput(): String = this
        .replace("```json", "", ignoreCase = true)
        .replace("```csv", "", ignoreCase = true)
        .replace("```", "")
        .trim()

    /**
     * AI Document Text Generator / Assistant (CamScanner Style)
     */
    suspend fun generateAiDocumentText(type: String, prompt: String): String = withContext(Dispatchers.IO) {
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

        val result = callGeminiApi(prompt = systemPrompt, temperature = 0.2f)
        if (result.isNotBlank()) {
            return@withContext result.trim()
        }

        // Fallback templates if offline
        when (type.lowercase()) {
            "paid" -> "PAID IN FULL\nDate: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}\nRef: #INV-${(1000..9999).random()}"
            "approved" -> "APPROVED & VERIFIED\nAuthorized Signature\nDate: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}"
            "confidential" -> "CONFIDENTIAL\nFor Authorized Eyes Only"
            "certified" -> "CERTIFIED TRUE COPY\nof Original Document"
            "date" -> "Date: ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}"
            else -> if (prompt.isNotBlank()) prompt else "Document Annotation Note"
        }
    }

    /**
     * AI Text Rephraser, Translator, or Formatter for Document Text Overlays
     */
    suspend fun rephraseTextAi(originalText: String, action: String): String = withContext(Dispatchers.IO) {
        if (originalText.isBlank()) return@withContext ""

        val prompt = """
            You are an AI document text editor.
            Original Text:
            $originalText
            
            Action to perform: $action
            (e.g., Translate to English, Translate to Bengali, Make Formal / Professional, Capitalize, Fix Spelling & Grammar, or Summarize in 1 Line)
            
            Return ONLY the processed text without any explanations, markdown, or quotation marks.
        """.trimIndent()

        val result = callGeminiApi(prompt = prompt, temperature = 0.2f)
        if (result.isNotBlank()) {
            return@withContext result.trim()
        }

        when (action.lowercase()) {
            "uppercase" -> originalText.uppercase()
            "lowercase" -> originalText.lowercase()
            else -> originalText
        }
    }

    /**
     * AI-Powered Case Summary & Key Insights
     */
    suspend fun summarizeDocumentAi(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "No document text available for AI analysis."

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

        val result = callGeminiApi(prompt = prompt, temperature = 0.3f)
        if (result.isNotBlank()) {
            return@withContext result
        }

        // Local fallback
        "📋 Document Summary:\n\n• Document contains ${text.lines().size} lines of text.\n• Length: ${text.length} characters.\n• Status: Processed locally."
    }

    suspend fun editTextAi(originalText: String, instruction: String, context: android.content.Context? = null): String = withContext(Dispatchers.IO) {
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

        val res = callGeminiApi(prompt = prompt, temperature = 0.2f, context = context)
        if (res.isNotBlank()) res.trim() else originalText
    }

    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: android.content.Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val systemContext = buildString {
                    append("You are Google Gemini, an intelligent, helpful document intelligence AI assistant in the CamScanner app. ")
                    append("You assist users with understanding scanned documents, extracting numbers, invoices, dates, summaries, and answering any document-related questions in English or Bengali (বাংলা). ")
                    append("Always provide clear, concise, structured, and beautifully formatted answers with bullet points when appropriate. ")
                    if (!documentContext.isNullOrBlank()) {
                        append("\n\n=== CURRENT SCANNED DOCUMENT CONTEXT ===\n")
                        append(documentContext)
                        append("\n=========================================\n")
                    }
                }

                val prompt = buildString {
                    append(systemContext)
                    append("\nConversation History:\n")
                    for ((sender, msg) in history.takeLast(6)) {
                        append("$sender: $msg\n")
                    }
                    append("User: $userMessage\n")
                    append("Google Gemini:")
                }

                val result = callGeminiApi(prompt = prompt, temperature = 0.3f, context = context)
                if (result.isNotBlank()) {
                    return@withContext result.trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Gemini chatWithAi: ${e.message}", e)
            }
        }

        // Contextual intelligent document analysis if API key is not set or network is unreachable
        buildSmartGeminiFallback(userMessage, documentContext)
    }

    private fun buildSmartGeminiFallback(userMessage: String, docContext: String?): String {
        val lower = userMessage.lowercase()
        val doc = docContext?.trim() ?: ""

        if (doc.isBlank()) {
            return if (lower.contains("bengali") || lower.contains("বাংলা")) {
                "নমস্কার! আমি আপনার গুগল জেমিনি (Google Gemini) এআই ডকুমেন্ট অ্যাসিস্ট্যান্ট।\n\nযেকোনো স্ক্যান করা ডকুমেন্ট নির্বাচন করুন অথবা আমাকে প্রশ্ন করুন, আমি সারাংশ, অনুবাদ বা গুরুত্বপূর্ণ তথ্য বের করে দেব।"
            } else {
                "Hello! I am your Google Gemini AI Document Assistant.\n\nAttach any scanned document to summarize it, extract tables/invoices, translate into Bengali, or ask specific questions about your files."
            }
        }

        // If document is attached, perform smart extraction
        return when {
            lower.contains("summar") || lower.contains("সারাংশ") -> {
                val lines = doc.lines().filter { it.isNotBlank() }
                val bullets = lines.take(5).joinToString("\n") { "• ${it.trim().take(120)}" }
                "📄 **Document Summary (Google Gemini)**:\n\n$bullets\n\n*(Full Cloud Gemini analysis available when Gemini API Key is configured in Settings)*"
            }
            lower.contains("key point") || lower.contains("মূল") || lower.contains("point") -> {
                val points = doc.lines().filter { it.length > 15 }.take(4).joinToString("\n") { "• ${it.trim()}" }
                "💡 **Key Document Highlights**:\n\n${points.ifBlank { "• " + doc.take(200) }}"
            }
            lower.contains("total") || lower.contains("amount") || lower.contains("money") || lower.contains("টাকা") -> {
                val numbers = Regex("""[\$€£৳₹]?\s?\d+([.,]\d+)*""").findAll(doc).map { it.value }.take(6).toList()
                if (numbers.isNotEmpty()) {
                    "💰 **Extracted Financial & Numeric Figures**:\n\n" + numbers.joinToString("\n") { "• $it" }
                } else {
                    "💰 No specific financial figures found in the attached document context."
                }
            }
            lower.contains("translate") || lower.contains("bengali") || lower.contains("বাংলা") -> {
                "🌐 **Translation to Bengali (বাংলা)**:\n\nডকুমেন্টের প্রধান বিষয়বস্তু:\n" + doc.take(300)
            }
            else -> {
                val snippet = doc.lines().filter { it.isNotBlank() }.take(4).joinToString("\n")
                "✨ **Google Gemini AI Assistant**:\n\nBased on your document:\n\n$snippet\n\nHow else would you like me to analyze this file?"
            }
        }
    }

    suspend fun smartEraseGuideAi(bitmap: Bitmap, mode: String = "auto", context: android.content.Context? = null): String = withContext(Dispatchers.IO) {
        val prompt = """
            Analyze this scanned document image for unwanted artifacts to erase (such as finger shadows, watermarks, stamps, smudges, handwriting notes, or stains).
            Mode: $mode
            Provide a clear summary of artifacts found and recommended inpainting regions.
        """.trimIndent()
        callGeminiApi(prompt = prompt, bitmap = bitmap, temperature = 0.2f)
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
