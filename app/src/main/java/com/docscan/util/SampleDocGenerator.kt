package com.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object SampleDocGenerator {

    /**
     * Generates a sample National Identity / Driver License Card (Front Side) with clean English text.
     */
    fun createSampleIdCardFront(): Bitmap {
        val width = 1000
        val height = 630 // Standard 1.586 ratio
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Background gradient & security patterns
        val bgPaint = Paint().apply { color = Color.parseColor("#F4FAF6") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Security Guilloche / wavy lines
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEFE6")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        for (i in 0..height step 16) {
            val path = Path()
            path.moveTo(0f, i.toFloat())
            path.cubicTo(
                width * 0.3f, (i - 10).toFloat(),
                width * 0.7f, (i + 14).toFloat(),
                width.toFloat(), (i + 2).toFloat()
            )
            canvas.drawPath(path, wavePaint)
        }

        // 2. Header Emblem & Titles
        val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B5E20") }
        canvas.drawCircle(75f, 65f, 28f, emblemPaint)
        val emblemInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00897B") }
        canvas.drawCircle(75f, 65f, 18f, emblemInner)
        val emblemStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
        canvas.drawCircle(75f, 65f, 6f, emblemStar)

        val headerMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("STATE IDENTIFICATION AUTHORITY", width / 2f, 55f, headerMain)

        val headerSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#374151")
            textSize = 17f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DEPARTMENT OF MOTOR VEHICLES & PUBLIC SAFETY", width / 2f, 82f, headerSub)

        val headerCard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C62828")
            textSize = 21f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("NATIONAL IDENTITY & DRIVER CARD", width / 2f, 114f, headerCard)

        // 3. Photo Avatar Frame
        val photoFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val photoBg = Paint().apply { color = Color.parseColor("#E0E7FF") }
        val photoRect = RectF(50f, 140f, 250f, 400f)
        canvas.drawRoundRect(photoRect, 8f, 8f, photoBg)
        canvas.drawRoundRect(photoRect, 8f, 8f, photoFrame)

        // Male portrait silhouette
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155") }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#94A3B8") }
        val shirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E3A8A") }
        // Hair
        canvas.drawOval(RectF(100f, 170f, 200f, 260f), headPaint)
        // Face
        canvas.drawOval(RectF(110f, 190f, 190f, 280f), facePaint)
        // Shirt
        val bodyRect = RectF(70f, 290f, 230f, 400f)
        canvas.drawRoundRect(bodyRect, 20f, 20f, shirtPaint)

        // Signature under photo
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("M. Hossain", 150f, 445f, sigPaint)

        // 4. Fields & Text
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 17f
        }
        val valEng = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 19f
            isFakeBoldText = true
        }

        canvas.drawText("Full Name:", 280f, 170f, labelPaint)
        canvas.drawText("MD. MOFASSAL HOSSAIN", 380f, 170f, valEng)

        canvas.drawText("Father Name:", 280f, 215f, labelPaint)
        canvas.drawText("MUBARAK HOSSAIN", 400f, 215f, valEng)

        canvas.drawText("Mother Name:", 280f, 260f, labelPaint)
        canvas.drawText("TASLIMA KHATUN", 405f, 260f, valEng)

        canvas.drawText("Occupation:", 280f, 305f, labelPaint)
        canvas.drawText("SOFTWARE ENGINEER", 390f, 305f, valEng)

        // Date of Birth (Red bold)
        val redValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            textSize = 22f
            isFakeBoldText = true
        }
        canvas.drawText("Date of Birth:", 280f, 350f, labelPaint)
        canvas.drawText("26 AUG 1999", 410f, 350f, redValuePaint)

        // ID NO (Red bold)
        val idLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            textSize = 22f
            isFakeBoldText = true
        }
        canvas.drawText("ID NO:", 280f, 400f, idLabel)
        canvas.drawText("8713 1263 4309", 370f, 400f, redValuePaint)

        // Watermark Emblem in center
        val waterCrest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1022C55E")
        }
        canvas.drawCircle(680f, 280f, 90f, waterCrest)

        // Outer crisp black card border
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(RectF(10f, 10f, width - 10f, height - 10f), 16f, 16f, border)

        return bmp
    }

    /**
     * Generates a sample National Identity / Driver License Card (Back Side) with clean English text.
     */
    fun createSampleIdCardBack(): Bitmap {
        val width = 1000
        val height = 630
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Background
        val bgPaint = Paint().apply { color = Color.parseColor("#F9FAFB") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Security pattern
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        for (i in 0..height step 18) {
            val path = Path()
            path.moveTo(0f, i.toFloat())
            path.cubicTo(width * 0.3f, (i - 8).toFloat(), width * 0.7f, (i + 12).toFloat(), width.toFloat(), i.toFloat())
            canvas.drawPath(path, wavePaint)
        }

        // 2. Top Notice in English
        val topNotice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 15.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "This card is the official property of the State Identification Authority.",
            width / 2f,
            45f,
            topNotice
        )
        canvas.drawText(
            "If found, please return to the nearest postal office or official registry.",
            width / 2f,
            70f,
            topNotice
        )

        // 3. Address Text
        val addrLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 17f
            isFakeBoldText = true
        }
        val addrVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 17f
        }
        canvas.drawText("Residential Address:", 50f, 125f, addrLabel)
        canvas.drawText("Suite 402, Metro Boulevard, Central Postal District - 2260", 250f, 125f, addrVal)
        canvas.drawText("Metropolis Capital City, State 94016", 250f, 155f, addrVal)

        // 4. Blood Group & Place of Birth
        val redLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            textSize = 17f
            isFakeBoldText = true
        }
        canvas.drawText("Blood Group: A+ (Positive)", 50f, 215f, redLabel)
        canvas.drawText("Place of Issue: Central Registry", 550f, 215f, addrVal)

        // 5. Authority Signature & Date
        canvas.drawText("Authorized Registrar Signature", 50f, 290f, addrVal)
        canvas.drawText("Issue Date: 22/07/2025", 550f, 290f, addrVal)

        // Signature line
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawLine(70f, 320f, 200f, 310f, sigPaint)
        canvas.drawLine(100f, 305f, 140f, 330f, sigPaint)

        // 6. High-density PDF417 2D Barcode running across bottom width
        val barcodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
        val barLeft = 50f
        val barTop = 380f
        val barHeight = 180f
        for (i in 0..900 step 5) {
            val barW = if (i % 25 == 0) 4.5f else if (i % 15 == 0) 3f else 1.8f
            canvas.drawRect(barLeft + i, barTop, barLeft + i + barW, barTop + barHeight, barcodePaint)
        }

        // Outer crisp black card border
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(RectF(10f, 10f, width - 10f, height - 10f), 16f, 16f, border)

        return bmp
    }
}
