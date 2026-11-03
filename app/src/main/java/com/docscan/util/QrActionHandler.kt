package com.docscan.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import com.docscan.data.model.BarcodeValueType
import com.docscan.data.model.ParsedBarcode
import java.net.URLEncoder

object QrActionHandler {

    fun copyToClipboard(context: Context, text: String, label: String = "Scanned Code") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String, title: String = "Share Scanned Code") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openUrl(context: Context, url: String) {
        try {
            val formattedUrl = if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dialPhoneNumber(context: Context, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmail(context: Context, email: String, subject: String? = null, body: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open email app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendSms(context: Context, phone: String, message: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                message?.let { putExtra("sms_body", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open SMS app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveContact(
        context: Context,
        name: String?,
        phone: String?,
        email: String?,
        org: String? = null,
        address: String? = null
    ) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                org?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
                address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Contacts: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMapsLocation(context: Context, lat: Double, lng: Double, label: String? = null) {
        try {
            val uriStr = if (label != null) {
                "geo:$lat,$lng?q=$lat,$lng(${URLEncoder.encode(label, "UTF-8")})"
            } else {
                "geo:$lat,$lng?q=$lat,$lng"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Maps: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun addCalendarEvent(
        context: Context,
        title: String?,
        description: String?,
        location: String?,
        startMillis: Long?,
        endMillis: Long?
    ) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                title?.let { putExtra(CalendarContract.Events.TITLE, it) }
                description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Calendar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Wi-Fi settings", Toast.LENGTH_SHORT).show()
        }
    }

    fun searchProductOnWeb(context: Context, barcodeValue: String) {
        try {
            val query = URLEncoder.encode(barcodeValue, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot search web: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
