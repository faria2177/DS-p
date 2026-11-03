package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        width: Int = 600,
        height: Int = 600,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun buildWifiQrString(ssid: String, password: String, securityType: String = "WPA", isHidden: Boolean = false): String {
        return "WIFI:T:$securityType;S:$ssid;P:$password;H:$isHidden;;"
    }

    fun buildVCardQrString(
        name: String,
        phone: String = "",
        email: String = "",
        organization: String = "",
        jobTitle: String = "",
        address: String = "",
        website: String = ""
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCARD")
        sb.appendLine("VERSION:3.0")
        sb.appendLine("FN:$name")
        if (organization.isNotBlank()) sb.appendLine("ORG:$organization")
        if (jobTitle.isNotBlank()) sb.appendLine("TITLE:$jobTitle")
        if (phone.isNotBlank()) sb.appendLine("TEL;TYPE=CELL:$phone")
        if (email.isNotBlank()) sb.appendLine("EMAIL;TYPE=WORK:$email")
        if (address.isNotBlank()) sb.appendLine("ADR;TYPE=WORK:;;$address;;;;")
        if (website.isNotBlank()) sb.appendLine("URL:$website")
        sb.append("END:VCARD")
        return sb.toString()
    }
}
