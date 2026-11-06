package com.docscan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FilterType {
    ORIGINAL,
    AUTO,
    CLEAR,
    DOCUMENT,
    GRAYSCALE,
    BW,
    MAGIC_COLOR,
    LIGHTEN
}

enum class ScanMode {
    SINGLE,
    BATCH,
    ID_CARD
}

enum class ScannerFeatureMode(val label: String) {
    SCAN("Scan"),
    ID_CARDS("ID Card"),
    TRANSLATE("Translate")
}

enum class IdCardType(
    val title: String,
    val subtitle: String,
    val isTwoSided: Boolean
) {
    GENERAL("General", "Single card copy on A4 paper", false),
    ID_CARD("ID Card", "National ID card front & back", true),
    DRIVER_LICENSE("Driver License", "Driving license card front & back", true),
    PASSPORT("Passport", "Passport data page on A4", false),
    BANK_CARD("Bank Card", "Credit / Debit card front & back", true),
    CERTIFICATE("Certificate", "Certificate copy on an A4 sheet", false)
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val folder: String = "All",
    val tags: String = "",
    val isStarred: Boolean = false,
    val pageCount: Int = 1,
    val thumbnailPath: String = "",
    val pdfPath: String? = null,
    val extractedText: String? = null,
    val wordFilePath: String? = null,
    val excelFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val filterType: String = FilterType.MAGIC_COLOR.name,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotationDegrees: Int = 0,
    val watermarkText: String? = null,
    val watermarkOpacity: Float = 0.35f,
    val watermarkColor: Long = 0xFF888888,
    val signatureImagePath: String? = null,
    val notes: String? = null,
    val extractedText: String? = null
)
