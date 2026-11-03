package com.docscan.data.repository

import androidx.room.withTransaction
import com.docscan.data.db.AppDatabase
import com.docscan.data.db.DocumentDao
import com.docscan.data.db.PageDao
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val database: AppDatabase,
    private val documentDao: DocumentDao = database.documentDao(),
    private val pageDao: PageDao = database.pageDao()
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val starredDocuments: Flow<List<DocumentEntity>> = documentDao.getStarredDocuments()
    val allFolders: Flow<List<String>> = documentDao.getAllFolders()

    fun getDocumentsByFolder(folder: String): Flow<List<DocumentEntity>> {
        return if (folder == "All") {
            documentDao.getAllDocuments()
        } else if (folder == "Starred") {
            documentDao.getStarredDocuments()
        } else {
            documentDao.getDocumentsByFolder(folder)
        }
    }

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return documentDao.searchDocuments(query)
    }

    fun getDocumentFlow(id: Long): Flow<DocumentEntity?> = documentDao.getDocumentFlowById(id)

    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getDocumentById(id)

    suspend fun saveNewDocument(
        title: String,
        folder: String,
        pages: List<PageEntity>
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val aggregatedText = pages.mapNotNull { it.extractedText }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank { null }

        val doc = DocumentEntity(
            title = title,
            folder = folder,
            pageCount = pages.size,
            thumbnailPath = pages.firstOrNull()?.processedImagePath ?: "",
            extractedText = aggregatedText,
            createdAt = now,
            updatedAt = now
        )
        val docId = documentDao.insertDocument(doc)
        val pagesWithDocId = pages.mapIndexed { index, page ->
            page.copy(documentId = docId, pageNumber = index + 1)
        }
        pageDao.insertPages(pagesWithDocId)
        docId
    }

    suspend fun updateDocument(doc: DocumentEntity) {
        documentDao.updateDocument(doc.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun safelyDeleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deletePageFiles(page: PageEntity) {
        safelyDeleteFile(page.originalImagePath)
        safelyDeleteFile(page.processedImagePath)
        safelyDeleteFile(page.signatureImagePath)
    }

    suspend fun deleteDocument(id: Long) {
        val doc = documentDao.getDocumentById(id)
        val pages = pageDao.getPagesForDocumentDirect(id)
        pages.forEach { page ->
            deletePageFiles(page)
        }
        doc?.pdfPath?.let { safelyDeleteFile(it) }

        database.withTransaction {
            pageDao.deletePagesForDocument(id)
            documentDao.deleteDocumentById(id)
        }
    }

    suspend fun deleteDocuments(ids: List<Long>) {
        ids.forEach { id ->
            val doc = documentDao.getDocumentById(id)
            val pages = pageDao.getPagesForDocumentDirect(id)
            pages.forEach { page ->
                deletePageFiles(page)
            }
            doc?.pdfPath?.let { safelyDeleteFile(it) }
        }

        database.withTransaction {
            ids.forEach { id ->
                pageDao.deletePagesForDocument(id)
            }
            documentDao.deleteDocumentsByIds(ids)
        }
    }

    fun getPagesForDocument(documentId: Long): Flow<List<PageEntity>> =
        pageDao.getPagesForDocument(documentId)

    suspend fun getPagesDirect(documentId: Long): List<PageEntity> =
        pageDao.getPagesForDocumentDirect(documentId)

    suspend fun getPageById(id: Long): PageEntity? = pageDao.getPageById(id)

    suspend fun updatePage(page: PageEntity) {
        database.withTransaction {
            pageDao.updatePage(page)
            // Refresh document extracted text & thumbnail
            val allPages = pageDao.getPagesForDocumentDirect(page.documentId)
            val aggregatedText = allPages.mapNotNull { it.extractedText }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .ifBlank { null }
            val doc = documentDao.getDocumentById(page.documentId)
            doc?.let {
                val thumb = if (page.pageNumber == 1) page.processedImagePath else it.thumbnailPath
                documentDao.updateDocument(
                    it.copy(
                        thumbnailPath = thumb,
                        extractedText = aggregatedText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun addPagesToDocument(documentId: Long, newPages: List<PageEntity>) {
        database.withTransaction {
            val currentPages = pageDao.getPagesForDocumentDirect(documentId)
            val startNumber = currentPages.size + 1
            val pagesToInsert = newPages.mapIndexed { index, p ->
                p.copy(documentId = documentId, pageNumber = startNumber + index)
            }
            pageDao.insertPages(pagesToInsert)
            val allUpdated = pageDao.getPagesForDocumentDirect(documentId)
            val aggregatedText = allUpdated.mapNotNull { it.extractedText }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .ifBlank { null }
            val doc = documentDao.getDocumentById(documentId)
            doc?.let {
                val thumb = if (it.thumbnailPath.isEmpty() && allUpdated.isNotEmpty()) {
                    allUpdated.first().processedImagePath
                } else it.thumbnailPath
                documentDao.updateDocument(
                    it.copy(
                        pageCount = allUpdated.size,
                        thumbnailPath = thumb,
                        extractedText = aggregatedText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun deletePage(page: PageEntity) {
        deletePageFiles(page)
        database.withTransaction {
            pageDao.deletePageById(page.id)
            val remaining = pageDao.getPagesForDocumentDirect(page.documentId)
            if (remaining.isEmpty()) {
                documentDao.deleteDocumentById(page.documentId)
            } else {
                // Re-sequence page numbers
                val updated = remaining.mapIndexed { index, p ->
                    p.copy(pageNumber = index + 1)
                }
                pageDao.insertPages(updated)
                val aggregatedText = updated.mapNotNull { it.extractedText }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { null }
                val doc = documentDao.getDocumentById(page.documentId)
                doc?.let {
                    documentDao.updateDocument(
                        it.copy(
                            pageCount = updated.size,
                            thumbnailPath = updated.first().processedImagePath,
                            extractedText = aggregatedText,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    suspend fun deletePages(pagesToDelete: List<PageEntity>) {
        if (pagesToDelete.isEmpty()) return
        val docId = pagesToDelete.first().documentId
        pagesToDelete.forEach { page ->
            deletePageFiles(page)
        }

        database.withTransaction {
            pagesToDelete.forEach { page ->
                pageDao.deletePageById(page.id)
            }
            val remaining = pageDao.getPagesForDocumentDirect(docId)
            if (remaining.isEmpty()) {
                val doc = documentDao.getDocumentById(docId)
                doc?.pdfPath?.let { safelyDeleteFile(it) }
                documentDao.deleteDocumentById(docId)
            } else {
                val updated = remaining.mapIndexed { index, p ->
                    p.copy(pageNumber = index + 1)
                }
                pageDao.insertPages(updated)
                val aggregatedText = updated.mapNotNull { it.extractedText }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { null }
                val doc = documentDao.getDocumentById(docId)
                doc?.let {
                    documentDao.updateDocument(
                        it.copy(
                            pageCount = updated.size,
                            thumbnailPath = updated.first().processedImagePath,
                            extractedText = aggregatedText,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    suspend fun extractPagesToNewDocument(
        title: String,
        folder: String,
        pages: List<PageEntity>,
        context: android.content.Context
    ): Long {
        val newPageEntities = pages.mapIndexed { index, p ->
            val origBmp = com.docscan.util.FileUtils.loadBitmap(p.processedImagePath)
            val newPath = if (origBmp != null) {
                com.docscan.util.FileUtils.saveBitmapToDocStorage(context, origBmp, "EXTRACT")
            } else {
                p.processedImagePath
            }
            p.copy(
                id = 0,
                pageNumber = index + 1,
                originalImagePath = newPath,
                processedImagePath = newPath
            )
        }
        return saveNewDocument(title, folder, newPageEntities)
    }

    suspend fun mergeDocumentsIntoNew(
        newTitle: String,
        folder: String,
        sourceDocIds: List<Long>,
        context: android.content.Context
    ): Long {
        val allPages = mutableListOf<PageEntity>()
        for (docId in sourceDocIds) {
            val pages = pageDao.getPagesForDocumentDirect(docId)
            for (p in pages) {
                val origBmp = com.docscan.util.FileUtils.loadBitmap(p.processedImagePath)
                val newPath = if (origBmp != null) {
                    com.docscan.util.FileUtils.saveBitmapToDocStorage(context, origBmp, "MERGE")
                } else {
                    p.processedImagePath
                }
                allPages.add(
                    p.copy(
                        id = 0,
                        originalImagePath = newPath,
                        processedImagePath = newPath
                    )
                )
            }
        }
        return saveNewDocument(newTitle, folder, allPages)
    }

    suspend fun updatePages(pages: List<PageEntity>) {
        database.withTransaction {
            pageDao.insertPages(pages)
        }
    }

    suspend fun reorderPages(documentId: Long, reorderedPages: List<PageEntity>) {
        database.withTransaction {
            val updated = reorderedPages.mapIndexed { index, p ->
                p.copy(pageNumber = index + 1)
            }
            pageDao.insertPages(updated)
            val doc = documentDao.getDocumentById(documentId)
            doc?.let {
                documentDao.updateDocument(
                    it.copy(
                        thumbnailPath = updated.firstOrNull()?.processedImagePath ?: it.thumbnailPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
