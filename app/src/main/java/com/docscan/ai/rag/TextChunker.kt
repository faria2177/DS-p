package com.docscan.ai.rag

data class DocumentChunk(
    val chunkId: Int,
    val pageNumber: Int,
    val text: String,
    val wordCount: Int
)

object TextChunker {

    private const val DEFAULT_CHUNK_SIZE = 250 // ~250 words per chunk
    private const val DEFAULT_OVERLAP = 40 // 40 words overlap

    fun chunkPages(
        pages: List<PageTextInfo>,
        targetChunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlapWords: Int = DEFAULT_OVERLAP
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        var globalChunkId = 0

        for (page in pages) {
            val words = page.text.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.isEmpty()) continue

            if (words.size <= targetChunkSize) {
                chunks.add(
                    DocumentChunk(
                        chunkId = globalChunkId++,
                        pageNumber = page.pageNumber,
                        text = words.joinToString(" "),
                        wordCount = words.size
                    )
                )
                continue
            }

            var startIdx = 0
            while (startIdx < words.size) {
                val endIdx = (startIdx + targetChunkSize).coerceAtMost(words.size)
                val chunkWords = words.subList(startIdx, endIdx)
                val chunkText = chunkWords.joinToString(" ")

                chunks.add(
                    DocumentChunk(
                        chunkId = globalChunkId++,
                        pageNumber = page.pageNumber,
                        text = chunkText,
                        wordCount = chunkWords.size
                    )
                )

                if (endIdx >= words.size) break
                startIdx += (targetChunkSize - overlapWords).coerceAtLeast(1)
            }
        }

        return chunks
    }
}
