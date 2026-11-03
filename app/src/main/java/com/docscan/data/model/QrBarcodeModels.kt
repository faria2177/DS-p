package com.docscan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MainScanMode {
    DOCUMENT,
    QR_BARCODE
}

enum class QrScanSubMode {
    SINGLE,
    CONTINUOUS
}

enum class BarcodeValueType {
    URL,
    TEXT,
    PHONE,
    EMAIL,
    SMS,
    WIFI,
    CONTACT_INFO,
    GEO,
    CALENDAR_EVENT,
    PRODUCT,
    ISBN,
    DRIVER_LICENSE,
    OTHER
}

data class ParsedBarcode(
    val id: Long = 0,
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val formatName: String,
    val valueType: BarcodeValueType,
    val title: String,
    val subtitle: String = "",
    val url: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val emailSubject: String? = null,
    val emailBody: String? = null,
    val smsNumber: String? = null,
    val smsMessage: String? = null,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val wifiEncryptionType: String? = null,
    val contactName: String? = null,
    val contactPhones: List<String> = emptyList(),
    val contactEmails: List<String> = emptyList(),
    val contactOrg: String? = null,
    val contactTitle: String? = null,
    val contactAddresses: List<String> = emptyList(),
    val contactUrls: List<String> = emptyList(),
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val calendarSummary: String? = null,
    val calendarDescription: String? = null,
    val calendarLocation: String? = null,
    val calendarStart: Long? = null,
    val calendarEnd: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "qr_scan_history")
data class QrHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val formatName: String,
    val valueType: String,
    val title: String,
    val subtitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
