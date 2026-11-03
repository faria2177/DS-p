package com.docscan.util

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.docscan.data.model.BarcodeValueType
import com.docscan.data.model.ParsedBarcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object BarcodeAnalyzerHelper {

    val scannerOptions: BarcodeScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        )
        .build()

    fun createScanner(): BarcodeScanner {
        return BarcodeScanning.getClient(scannerOptions)
    }

    fun parseBarcode(barcode: Barcode): ParsedBarcode {
        val rawValue = barcode.rawValue ?: barcode.displayValue ?: ""
        val displayValue = barcode.displayValue ?: rawValue
        val format = barcode.format
        val formatName = getFormatName(format)

        val valueType: BarcodeValueType
        val title: String
        var subtitle = ""

        var url: String? = null
        var phone: String? = null
        var email: String? = null
        var emailSubject: String? = null
        var emailBody: String? = null
        var smsNumber: String? = null
        var smsMessage: String? = null
        var wifiSsid: String? = null
        var wifiPassword: String? = null
        var wifiEncryptionType: String? = null
        var contactName: String? = null
        var contactPhones: List<String> = emptyList()
        var contactEmails: List<String> = emptyList()
        var contactOrg: String? = null
        var contactTitle: String? = null
        var contactAddresses: List<String> = emptyList()
        var contactUrls: List<String> = emptyList()
        var geoLat: Double? = null
        var geoLng: Double? = null
        var calendarSummary: String? = null
        var calendarDescription: String? = null
        var calendarLocation: String? = null
        var calendarStart: Long? = null
        var calendarEnd: Long? = null

        when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                valueType = BarcodeValueType.URL
                url = barcode.url?.url ?: rawValue
                title = "Website Link"
                subtitle = barcode.url?.title?.takeIf { it.isNotBlank() } ?: url
            }
            Barcode.TYPE_WIFI -> {
                valueType = BarcodeValueType.WIFI
                val wifi = barcode.wifi
                wifiSsid = wifi?.ssid ?: ""
                wifiPassword = wifi?.password
                wifiEncryptionType = when (wifi?.encryptionType) {
                    Barcode.WiFi.TYPE_OPEN -> "Open (No Password)"
                    Barcode.WiFi.TYPE_WEP -> "WEP"
                    Barcode.WiFi.TYPE_WPA -> "WPA/WPA2/WPA3"
                    else -> "Secured"
                }
                title = "Wi-Fi Network"
                subtitle = "SSID: $wifiSsid ($wifiEncryptionType)"
            }
            Barcode.TYPE_CONTACT_INFO -> {
                valueType = BarcodeValueType.CONTACT_INFO
                val contact = barcode.contactInfo
                contactName = contact?.name?.formattedName ?: contact?.name?.first ?: "Contact"
                contactPhones = contact?.phones?.mapNotNull { it.number } ?: emptyList()
                contactEmails = contact?.emails?.mapNotNull { it.address } ?: emptyList()
                contactOrg = contact?.organization
                contactTitle = contact?.title
                contactAddresses = contact?.addresses?.mapNotNull { it.addressLines.joinToString(", ") } ?: emptyList()
                contactUrls = contact?.urls ?: emptyList()
                title = "Contact Card"
                subtitle = contactName
            }
            Barcode.TYPE_PHONE -> {
                valueType = BarcodeValueType.PHONE
                phone = barcode.phone?.number ?: rawValue
                title = "Phone Number"
                subtitle = phone
            }
            Barcode.TYPE_EMAIL -> {
                valueType = BarcodeValueType.EMAIL
                val emailObj = barcode.email
                email = emailObj?.address ?: rawValue
                emailSubject = emailObj?.subject
                emailBody = emailObj?.body
                title = "Email Address"
                subtitle = email
            }
            Barcode.TYPE_SMS -> {
                valueType = BarcodeValueType.SMS
                val smsObj = barcode.sms
                smsNumber = smsObj?.phoneNumber ?: rawValue
                smsMessage = smsObj?.message
                title = "SMS Message"
                subtitle = "To: $smsNumber"
            }
            Barcode.TYPE_GEO -> {
                valueType = BarcodeValueType.GEO
                val geo = barcode.geoPoint
                geoLat = geo?.lat
                geoLng = geo?.lng
                title = "Geographic Location"
                subtitle = "Lat: ${String.format(Locale.US, "%.5f", geoLat ?: 0.0)}, Lng: ${String.format(Locale.US, "%.5f", geoLng ?: 0.0)}"
            }
            Barcode.TYPE_CALENDAR_EVENT -> {
                valueType = BarcodeValueType.CALENDAR_EVENT
                val cal = barcode.calendarEvent
                calendarSummary = cal?.summary
                calendarDescription = cal?.description
                calendarLocation = cal?.location
                calendarStart = cal?.start?.rawValue?.toLongOrNull()
                calendarEnd = cal?.end?.rawValue?.toLongOrNull()
                title = "Calendar Event"
                subtitle = calendarSummary ?: "Event"
            }
            Barcode.TYPE_PRODUCT -> {
                valueType = BarcodeValueType.PRODUCT
                title = "Product Barcode"
                subtitle = "$formatName: $displayValue"
            }
            Barcode.TYPE_ISBN -> {
                valueType = BarcodeValueType.ISBN
                title = "ISBN Book Code"
                subtitle = displayValue
            }
            Barcode.TYPE_DRIVER_LICENSE -> {
                valueType = BarcodeValueType.DRIVER_LICENSE
                title = "Driver License Data"
                subtitle = displayValue
            }
            else -> {
                // Secondary check for typical patterns in plain text
                val rawLower = rawValue.lowercase()
                when {
                    rawLower.startsWith("http://") || rawLower.startsWith("https://") || rawLower.startsWith("www.") -> {
                        valueType = BarcodeValueType.URL
                        url = if (rawLower.startsWith("www.")) "https://$rawValue" else rawValue
                        title = "Website Link"
                        subtitle = url
                    }
                    rawLower.startsWith("wifi:") -> {
                        valueType = BarcodeValueType.WIFI
                        title = "Wi-Fi Network"
                        val parsedWifi = parseRawWifiString(rawValue)
                        wifiSsid = parsedWifi.first
                        wifiPassword = parsedWifi.second
                        wifiEncryptionType = parsedWifi.third
                        subtitle = "SSID: $wifiSsid"
                    }
                    rawLower.startsWith("tel:") -> {
                        valueType = BarcodeValueType.PHONE
                        phone = rawValue.substringAfter("tel:")
                        title = "Phone Number"
                        subtitle = phone
                    }
                    rawLower.startsWith("mailto:") -> {
                        valueType = BarcodeValueType.EMAIL
                        email = rawValue.substringAfter("mailto:").substringBefore("?")
                        title = "Email Address"
                        subtitle = email
                    }
                    rawLower.startsWith("smsto:") || rawLower.startsWith("sms:") -> {
                        valueType = BarcodeValueType.SMS
                        smsNumber = rawValue.substringAfter(":").substringBefore(":")
                        smsMessage = rawValue.substringAfterLast(":", "")
                        title = "SMS Message"
                        subtitle = "To: $smsNumber"
                    }
                    isProductBarcodeFormat(format) -> {
                        valueType = BarcodeValueType.PRODUCT
                        title = "Product Barcode"
                        subtitle = "$formatName: $displayValue"
                    }
                    else -> {
                        valueType = BarcodeValueType.TEXT
                        title = "Plain Text"
                        subtitle = if (displayValue.length > 50) displayValue.take(47) + "..." else displayValue
                    }
                }
            }
        }

        return ParsedBarcode(
            rawValue = rawValue,
            displayValue = displayValue,
            format = format,
            formatName = formatName,
            valueType = valueType,
            title = title,
            subtitle = subtitle,
            url = url,
            phone = phone,
            email = email,
            emailSubject = emailSubject,
            emailBody = emailBody,
            smsNumber = smsNumber,
            smsMessage = smsMessage,
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            wifiEncryptionType = wifiEncryptionType,
            contactName = contactName,
            contactPhones = contactPhones,
            contactEmails = contactEmails,
            contactOrg = contactOrg,
            contactTitle = contactTitle,
            contactAddresses = contactAddresses,
            contactUrls = contactUrls,
            geoLat = geoLat,
            geoLng = geoLng,
            calendarSummary = calendarSummary,
            calendarDescription = calendarDescription,
            calendarLocation = calendarLocation,
            calendarStart = calendarStart,
            calendarEnd = calendarEnd,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun isProductBarcodeFormat(format: Int): Boolean {
        return format == Barcode.FORMAT_EAN_13 ||
                format == Barcode.FORMAT_EAN_8 ||
                format == Barcode.FORMAT_UPC_A ||
                format == Barcode.FORMAT_UPC_E ||
                format == Barcode.FORMAT_CODE_128 ||
                format == Barcode.FORMAT_CODE_39 ||
                format == Barcode.FORMAT_CODE_93 ||
                format == Barcode.FORMAT_ITF ||
                format == Barcode.FORMAT_CODABAR
    }

    fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_AZTEC -> "Aztec"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            else -> "Barcode"
        }
    }

    private fun parseRawWifiString(raw: String): Triple<String, String?, String> {
        // WIFI:T:WPA;S:MyNetwork;P:MyPassword;H:false;;
        var ssid = ""
        var pass: String? = null
        var type = "WPA"

        val parts = raw.removePrefix("WIFI:").removePrefix("wifi:").split(";")
        for (part in parts) {
            if (part.startsWith("S:", ignoreCase = true)) {
                ssid = part.substring(2)
            } else if (part.startsWith("P:", ignoreCase = true)) {
                pass = part.substring(2)
            } else if (part.startsWith("T:", ignoreCase = true)) {
                type = part.substring(2)
            }
        }
        return Triple(ssid, pass, type)
    }

    /**
     * Analyzes a bitmap image (e.g. imported from gallery) for barcodes.
     */
    fun analyzeBitmap(
        bitmap: Bitmap,
        onSuccess: (List<ParsedBarcode>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = createScanner()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val parsedList = barcodes.map { parseBarcode(it) }
                onSuccess(parsedList)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    /**
     * Computes average luminance of YUV image proxy frame for low-light notification
     */
    fun computeAverageLuminance(imageProxy: ImageProxy): Double {
        val plane = imageProxy.planes.firstOrNull() ?: return 128.0
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        var sum = 0L
        val step = 16 // Downsample for performance
        var count = 0
        for (i in data.indices step step) {
            sum += (data[i].toInt() and 0xFF)
            count++
        }
        return if (count > 0) sum.toDouble() / count else 128.0
    }
}
