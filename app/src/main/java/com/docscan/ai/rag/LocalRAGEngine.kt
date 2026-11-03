package com.docscan.ai.rag

import kotlin.math.ln

object LocalRAGEngine {

    private val STOP_WORDS = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "in", "for", "to", "of", "with",
        "this", "that", "these", "those", "are", "was", "were", "be", "been", "being", "have", "has",
        "had", "do", "does", "did", "but", "by", "from", "as", "if", "then", "so", "than", "too",
        "very", "can", "will", "just", "should", "now", "it", "its", "what", "where", "who", "how",
        "কী", "কি", "এই", "ওই", "এবং", "বা", "কিন্তু", "করে", "করা", "হল", "হলো", "থেকে", "দিয়ে"
    )

    data class ScoredChunk(
        val chunk: DocumentChunk,
        val score: Double
    )

    fun findRelevantChunks(
        chunks: List<DocumentChunk>,
        userQuery: String,
        topK: Int = 4
    ): List<DocumentChunk> {
        if (chunks.isEmpty()) return emptyList()
        // If document is small enough to fit within context, return all chunks
        if (chunks.size <= topK) return chunks

        val queryTokens = tokenize(userQuery)
        if (queryTokens.isEmpty()) {
            return chunks.take(topK)
        }

        val totalDocs = chunks.size.toDouble()

        // 1. Calculate Document Frequency (DF) for each query token
        val docFrequency = mutableMapOf<String, Int>()
        for (token in queryTokens) {
            val count = chunks.count { chunk ->
                chunk.text.contains(token, ignoreCase = true)
            }
            docFrequency[token] = count
        }

        // 2. Score each chunk using BM25-like scoring
        val scoredChunks = chunks.map { chunk ->
            val chunkTokens = tokenize(chunk.text)
            val chunkLength = chunkTokens.size.toDouble()
            var score = 0.0

            for (qToken in queryTokens) {
                val df = docFrequency[qToken] ?: 0
                if (df == 0) continue

                // Inverse Document Frequency (IDF)
                val idf = ln((totalDocs - df + 0.5) / (df + 0.5) + 1.0)

                // Term Frequency (TF) in current chunk
                val tf = chunkTokens.count { it == qToken || it.startsWith(qToken) || qToken.startsWith(it) }
                if (tf > 0) {
                    val tfScore = (tf * 2.2) / (tf + 1.2 * (0.25 + 0.75 * (chunkLength / 200.0)))
                    score += idf * tfScore
                }
            }

            // Small boost for earlier pages (often contains summary/executive info)
            val pageDecay = 1.0 / (1.0 + 0.05 * (chunk.pageNumber - 1))
            score *= pageDecay

            ScoredChunk(chunk, score)
        }

        val topChunks = scoredChunks
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .map { it.chunk }
            .take(topK)

        return if (topChunks.isNotEmpty()) topChunks else chunks.take(topK)
    }

    fun buildRAGContext(
        documentTitle: String,
        chunks: List<DocumentChunk>
    ): String {
        if (chunks.isEmpty()) return ""
        return buildString {
            append("Document Context: \"$documentTitle\"\n")
            append("The following are relevant excerpts retrieved directly from this document:\n\n")
            chunks.forEachIndexed { index, chunk ->
                append("[Excerpt ${index + 1} - Page ${chunk.pageNumber}]\n")
                append(chunk.text.trim())
                append("\n\n")
            }
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace("[^a-zA-Z0-9\\u0980-\\u09FF\\s]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }
    }
}
