package com.docscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class SavedSignatureItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedStampItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val text: String = "",
    val dateText: String = "",
    val colorHex: Long = 0xFF00BFA5,
    val createdAt: Long = System.currentTimeMillis()
)

data class SignOverlayPlacement(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val bitmap: Bitmap,
    val type: String = "signature", // "signature" or "stamp"
    val x: Float = 0.5f,            // 0.0f..1.0f center X relative to page width
    val y: Float = 0.5f,            // 0.0f..1.0f center Y relative to page height
    val widthRatio: Float = 0.35f,  // width relative to page width (0.1f..0.9f)
    val rotationDegrees: Float = 0f
)

object SignatureManager {

    private const val SIGNATURES_DIR = "saved_signatures"
    private const val STAMPS_DIR = "saved_stamps"

    private fun getSignaturesDirectory(context: Context): File {
        val dir = File(context.filesDir, SIGNATURES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getStampsDirectory(context: Context): File {
        val dir = File(context.filesDir, STAMPS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get all saved signatures from internal disk
     */
    fun getSavedSignatures(context: Context): List<SavedSignatureItem> {
        val dir = getSignaturesDirectory(context)
        val files = dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            SavedSignatureItem(
                id = file.nameWithoutExtension,
                name = file.nameWithoutExtension.replace("_", " "),
                filePath = file.absolutePath,
                createdAt = file.lastModified()
            )
        }
    }

    /**
     * Get all saved stamps from internal disk
     */
    fun getSavedStamps(context: Context): List<SavedStampItem> {
        initializeDefaultStampsIfNeeded(context)
        val dir = getStampsDirectory(context)
        val files = dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            SavedStampItem(
                id = file.nameWithoutExtension,
                name = file.nameWithoutExtension.replace("_", " "),
                filePath = file.absolutePath,
                createdAt = file.lastModified()
            )
        }
    }

    /**
     * Delete a saved signature by ID
     */
    fun deleteSignature(context: Context, signatureId: String): Boolean {
        val dir = getSignaturesDirectory(context)
        val file = File(dir, "$signatureId.png")
        return if (file.exists()) file.delete() else false
    }

    /**
     * Delete a saved stamp by ID
     */
    fun deleteStamp(context: Context, stampId: String): Boolean {
        val dir = getStampsDirectory(context)
        val file = File(dir, "$stampId.png")
        return if (file.exists()) file.delete() else false
    }

    /**
     * Saves a drawn signature bitmap to disk as transparent PNG
     */
    suspend fun saveDrawnSignature(
        context: Context,
        signatureBitmap: Bitmap,
        customName: String? = null
    ): SavedSignatureItem = withContext(Dispatchers.IO) {
        val cropped = cropTransparentBorders(signatureBitmap)
        val dir = getSignaturesDirectory(context)
        val id = "sig_${System.currentTimeMillis()}"
        val name = customName ?: "Signature ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        val file = File(dir, "$id.png")

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        SavedSignatureItem(
            id = id,
            name = name,
            filePath = file.absolutePath,
            createdAt = file.lastModified()
        )
    }

    /**
     * Processes a scanned/captured photo of a handwritten signature, removing white/gray paper background
     * and preserving clean dark ink strokes on a transparent canvas.
     */
    suspend fun processAndSaveScannedSignature(
        context: Context,
        sourceBitmap: Bitmap,
        customName: String? = null
    ): SavedSignatureItem = withContext(Dispatchers.IO) {
        val cropped = prepareImportedSignature(sourceBitmap)

        val dir = getSignaturesDirectory(context)
        val id = "sig_scan_${System.currentTimeMillis()}"
        val name = customName ?: "Scan ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
        val file = File(dir, "$id.png")

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        SavedSignatureItem(
            id = id,
            name = name,
            filePath = file.absolutePath,
            createdAt = file.lastModified()
        )
    }

    /**
     * Processes an imported signature image from gallery
     */
    suspend fun processAndSaveImportedSignature(
        context: Context,
        sourceBitmap: Bitmap,
        customName: String? = null
    ): SavedSignatureItem = withContext(Dispatchers.IO) {
        val cropped = prepareImportedSignature(sourceBitmap)

        val dir = getSignaturesDirectory(context)
        val id = "sig_import_${System.currentTimeMillis()}"
        val name = customName ?: "Import ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
        val file = File(dir, "$id.png")

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        SavedSignatureItem(
            id = id,
            name = name,
            filePath = file.absolutePath,
            createdAt = file.lastModified()
        )
    }

    /**
     * Prepares a gallery/camera signature for preview and placement.
     * It removes the paper background, rejects isolated text/noise components,
     * keeps the connected ink cluster around the most signature-like stroke,
     * and tightly crops the final transparent bitmap.
     */
    fun prepareImportedSignature(sourceBitmap: Bitmap): Bitmap {
        val transparent = extractInkWithTransparency(sourceBitmap)
        val isolated = isolateLikelySignatureCluster(transparent)
        return cropTransparentBorders(isolated)
    }

    suspend fun savePreparedSignature(
        context: Context,
        preparedBitmap: Bitmap,
        customName: String? = null
    ): SavedSignatureItem = withContext(Dispatchers.IO) {
        val cropped = cropTransparentBorders(preparedBitmap)
        val dir = getSignaturesDirectory(context)
        val id = "sig_import_${System.currentTimeMillis()}"
        val name = customName ?: "Import ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
        val file = File(dir, "$id.png")
        FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
        SavedSignatureItem(id = id, name = name, filePath = file.absolutePath, createdAt = file.lastModified())
    }

    private data class InkComponent(
        val label: Int,
        val area: Int,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    ) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val centerX: Float get() = (minX + maxX) / 2f
        val centerY: Float get() = (minY + maxY) / 2f
    }

    /**
     * A connected-component pass designed for handwritten signatures. Printed text
     * and dust usually form many small, separated components; a signature typically
     * contains one or a few larger/longer ink clusters. We keep the dominant cluster
     * plus nearby strokes (dots, crossings, disconnected initials) and discard the rest.
     */
    private fun isolateLikelySignatureCluster(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 2 || height <= 2) return source

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val labels = IntArray(pixels.size)
        val queue = IntArray(pixels.size)
        val components = mutableListOf<InkComponent>()
        var nextLabel = 1
        val alphaThreshold = 38

        fun isInk(index: Int): Boolean = Color.alpha(pixels[index]) >= alphaThreshold

        for (start in pixels.indices) {
            if (labels[start] != 0 || !isInk(start)) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = nextLabel
            var area = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val n = idx - 1
                    if (labels[n] == 0 && isInk(n)) { labels[n] = nextLabel; queue[tail++] = n }
                }
                if (x + 1 < width) {
                    val n = idx + 1
                    if (labels[n] == 0 && isInk(n)) { labels[n] = nextLabel; queue[tail++] = n }
                }
                if (y > 0) {
                    val n = idx - width
                    if (labels[n] == 0 && isInk(n)) { labels[n] = nextLabel; queue[tail++] = n }
                }
                if (y + 1 < height) {
                    val n = idx + width
                    if (labels[n] == 0 && isInk(n)) { labels[n] = nextLabel; queue[tail++] = n }
                }
            }

            components += InkComponent(nextLabel, area, minX, minY, maxX, maxY)
            nextLabel++
        }

        if (components.isEmpty()) return source
        val minUsefulArea = max(6, (width * height) / 120_000)
        val useful = components.filter { it.area >= minUsefulArea }
        if (useful.isEmpty()) return source

        val anchor = useful.maxByOrNull { comp ->
            val aspectBoost = (comp.width.toFloat() / comp.height.coerceAtLeast(1)).coerceIn(1f, 6f)
            comp.area.toFloat() * (1f + aspectBoost * 0.22f)
        } ?: return source

        // If the anchor itself is microscopic, do not risk deleting legitimate ink.
        if (anchor.area < max(12, (width * height) / 30_000)) return source

        val xReach = max(anchor.width * 1.15f, width * 0.10f)
        val yReach = max(anchor.height * 1.35f, height * 0.07f)
        val minNeighbourArea = max(3, anchor.area / 90)

        val selectedLabels = useful.filter { comp ->
            if (comp.label == anchor.label) return@filter true
            val horizontalGap = when {
                comp.maxX < anchor.minX -> (anchor.minX - comp.maxX).toFloat()
                comp.minX > anchor.maxX -> (comp.minX - anchor.maxX).toFloat()
                else -> 0f
            }
            val verticalGap = when {
                comp.maxY < anchor.minY -> (anchor.minY - comp.maxY).toFloat()
                comp.minY > anchor.maxY -> (comp.minY - anchor.maxY).toFloat()
                else -> 0f
            }
            comp.area >= minNeighbourArea && horizontalGap <= xReach && verticalGap <= yReach
        }.mapTo(HashSet()) { it.label }

        if (selectedLabels.isEmpty()) return source
        val outPixels = IntArray(pixels.size)
        var kept = 0
        for (i in pixels.indices) {
            if (selectedLabels.contains(labels[i])) {
                outPixels[i] = pixels[i]
                kept++
            }
        }
        if (kept < 8) return source

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(outPixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Saves a stamp bitmap to disk
     */
    suspend fun saveStamp(
        context: Context,
        stampBitmap: Bitmap,
        name: String,
        text: String = "",
        dateText: String = "",
        colorHex: Long = 0xFF00BFA5
    ): SavedStampItem = withContext(Dispatchers.IO) {
        val cropped = cropTransparentBorders(stampBitmap)
        val dir = getStampsDirectory(context)
        val id = "stamp_${System.currentTimeMillis()}"
        val file = File(dir, "$id.png")

        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        SavedStampItem(
            id = id,
            name = name,
            filePath = file.absolutePath,
            text = text,
            dateText = dateText,
            colorHex = colorHex,
            createdAt = file.lastModified()
        )
    }

    /**
     * Generates a sleek, high-res official vector-style Stamp (APPROVED, VERIFIED, CONFIDENTIAL, PAID, RECEIVED)
     */
    fun createOfficialStampBitmap(
        title: String,
        dateString: String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
        primaryColor: Int = Color.parseColor("#00BFA5"),
        isCircular: Boolean = false
    ): Bitmap {
        val width = 500
        val height = if (isCircular) 500 else 240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        if (isCircular) {
            val center = width / 2f
            val radius = width * 0.45f
            canvas.drawCircle(center, center, radius, strokePaint)

            val innerPaint = Paint(strokePaint).apply { strokeWidth = 3f }
            canvas.drawCircle(center, center, radius - 14f, innerPaint)

            textPaint.textSize = 42f
            canvas.drawText(title.uppercase(Locale.getDefault()), center, center - 18f, textPaint)

            val datePaint = Paint(textPaint).apply {
                textSize = 28f
                isFakeBoldText = false
            }
            canvas.drawText(dateString, center, center + 34f, datePaint)
        } else {
            // Rectangular badge with double rounded borders
            val outerRect = RectF(16f, 16f, width - 16f, height - 16f)
            canvas.drawRoundRect(outerRect, 22f, 22f, strokePaint)

            val innerPaint = Paint(strokePaint).apply { strokeWidth = 3.5f }
            val innerRect = RectF(26f, 26f, width - 26f, height - 26f)
            canvas.drawRoundRect(innerRect, 16f, 16f, innerPaint)

            textPaint.textSize = 46f
            canvas.drawText(title.uppercase(Locale.getDefault()), width / 2f, height / 2f - 10f, textPaint)

            val datePaint = Paint(textPaint).apply {
                textSize = 26f
                isFakeBoldText = false
            }
            canvas.drawText(dateString, width / 2f, height / 2f + 42f, datePaint)
        }

        return cropTransparentBorders(bitmap)
    }

    /**
     * Generates a clean Date-Only Stamp
     */
    fun createDateOnlyStampBitmap(
        dateString: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
        primaryColor: Int = Color.parseColor("#00BFA5")
    ): Bitmap {
        val width = 360
        val height = 110
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val rect = RectF(10f, 10f, width - 10f, height - 10f)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textAlign = Paint.Align.CENTER
            textSize = 40f
            isFakeBoldText = true
        }
        val yOffset = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(dateString, width / 2f, yOffset, textPaint)

        return cropTransparentBorders(bitmap)
    }

    /**
     * Initializes default stamps if the stamps directory is empty
     */
    fun initializeDefaultStampsIfNeeded(context: Context) {
        val dir = getStampsDirectory(context)
        val existing = dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) }
        if (existing.isNullOrEmpty()) {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            val defaultStamps = listOf(
                Triple("Approved", Color.parseColor("#00BFA5"), false),
                Triple("Verified", Color.parseColor("#3B82F6"), false),
                Triple("Confidential", Color.parseColor("#EF4444"), false),
                Triple("Paid", Color.parseColor("#10B981"), true),
                Triple("Received", Color.parseColor("#8B5CF6"), false)
            )

            defaultStamps.forEach { (name, color, isCircular) ->
                try {
                    val bmp = createOfficialStampBitmap(name, dateStr, color, isCircular)
                    val file = File(dir, "${name.lowercase(Locale.getDefault())}.png")
                    FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * Converts paper/document image with dark pen ink into transparent PNG with crisp alpha strokes.
     * Intelligently detects whether the image is already transparent or an opaque photo of handwritten ink on paper.
     */
    fun extractInkWithTransparency(source: Bitmap): Bitmap {
        // Downscale large input bitmap to max 1200x1200 for fast, memory-safe processing
        val maxDim = 1200
        val scale = min(1f, maxDim.toFloat() / max(source.width, source.height))
        val workingBitmap = if (scale < 1f) {
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, w, h, true)
        } else {
            source
        }

        val width = workingBitmap.width
        val height = workingBitmap.height
        val pixels = IntArray(width * height)
        workingBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Check if the image already has significant transparency (e.g., imported transparent PNG)
        var transparentPixelCount = 0
        val sampleTotal = min(width * height, 2000)
        val step = max(1, (width * height) / sampleTotal)
        for (i in 0 until width * height step step) {
            if (Color.alpha(pixels[i]) < 30) {
                transparentPixelCount++
            }
        }
        val isAlreadyTransparent = transparentPixelCount > (sampleTotal * 0.08f)

        if (isAlreadyTransparent) {
            // Already transparent PNG: Clean up faint antialiased noise and return
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (i in pixels.indices) {
                val a = Color.alpha(pixels[i])
                if (a < 15) {
                    pixels[i] = 0
                }
            }
            output.setPixels(pixels, 0, width, 0, 0, width, height)
            return output
        }

        // 2. Opaque photo: Adaptive ink vs paper separation
        // Build luminance histogram to accurately detect paper background peak
        val histogram = IntArray(256)
        for (p in pixels) {
            val lum = getLuminance(p)
            histogram[lum]++
        }

        // Find paper background luminance (approx 85th percentile of brightness)
        var cumulative = 0
        val totalPixels = width * height
        val paperThresholdCount = (totalPixels * 0.85).toInt()
        var paperLum = 230
        for (lum in 0..255) {
            cumulative += histogram[lum]
            if (cumulative >= paperThresholdCount) {
                paperLum = lum.coerceIn(160, 255)
                break
            }
        }

        // Foreground ink is significantly darker than paper background
        val thresholdHigh = (paperLum - 15).coerceIn(120, 252)
        val thresholdLow = (paperLum - 65).coerceIn(50, 210)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = getLuminance(p)
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            if (lum >= thresholdHigh) {
                // Paper background -> 100% transparent
                pixels[i] = 0
            } else if (lum <= thresholdLow) {
                // Solid ink: preserve ink tint (black, blue, red, green, etc.) with rich contrast
                val isBlueInk = (b > r + 15 && b > g + 10)
                val isRedInk = (r > g + 25 && r > b + 25)
                val isGreenInk = (g > r + 20 && g > b + 20)

                if (isBlueInk) {
                    pixels[i] = Color.argb(255, (r * 0.5f).toInt(), (g * 0.6f).toInt(), min(255, (b * 1.25f).toInt()))
                } else if (isRedInk) {
                    pixels[i] = Color.argb(255, min(255, (r * 1.25f).toInt()), (g * 0.4f).toInt(), (b * 0.4f).toInt())
                } else if (isGreenInk) {
                    pixels[i] = Color.argb(255, (r * 0.4f).toInt(), min(255, (g * 1.25f).toInt()), (b * 0.4f).toInt())
                } else {
                    val darkVal = (lum * 0.35f).toInt().coerceIn(0, 40)
                    pixels[i] = Color.argb(255, darkVal, darkVal, darkVal)
                }
            } else {
                // Antialiased transition edge
                val alphaFactor = (thresholdHigh - lum).toFloat() / (thresholdHigh - thresholdLow).toFloat()
                val alphaVal = (alphaFactor.coerceIn(0f, 1f) * 255).toInt().coerceIn(20, 255)

                val isBlueInk = (b > r + 15 && b > g + 10)
                val isRedInk = (r > g + 25 && r > b + 25)
                if (isBlueInk) {
                    pixels[i] = Color.argb(alphaVal, (r * 0.6f).toInt(), (g * 0.7f).toInt(), min(255, (b * 1.15f).toInt()))
                } else if (isRedInk) {
                    pixels[i] = Color.argb(alphaVal, min(255, (r * 1.15f).toInt()), (g * 0.5f).toInt(), (b * 0.5f).toInt())
                } else {
                    val darkVal = (lum * 0.45f).toInt().coerceIn(0, 55)
                    pixels[i] = Color.argb(alphaVal, darkVal, darkVal, darkVal)
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun getLuminance(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    /**
     * Crops unnecessary transparent margins around signature/stamp bitmap
     */
    fun cropTransparentBorders(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        var hasContent = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = Color.alpha(pixels[y * width + x])
                if (alpha > 25) {
                    hasContent = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (!hasContent || minX > maxX || minY > maxY) {
            return source
        }

        val padding = 8
        minX = max(0, minX - padding)
        minY = max(0, minY - padding)
        maxX = min(width - 1, maxX + padding)
        maxY = min(height - 1, maxY + padding)

        val cropWidth = max(1, maxX - minX + 1)
        val cropHeight = max(1, maxY - minY + 1)

        return Bitmap.createBitmap(source, minX, minY, cropWidth, cropHeight)
    }

    /**
     * Composites overlays onto a base page Bitmap with high accuracy
     */
    fun compositeOverlaysOnPage(
        basePageBitmap: Bitmap,
        overlays: List<SignOverlayPlacement>
    ): Bitmap {
        if (overlays.isEmpty()) return basePageBitmap

        val result = basePageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val pageW = result.width.toFloat()
        val pageH = result.height.toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        overlays.forEach { overlay ->
            val overlayW = (pageW * overlay.widthRatio).toInt().coerceAtLeast(30)
            val aspect = overlay.bitmap.height.toFloat() / overlay.bitmap.width.toFloat()
            val overlayH = (overlayW * aspect).toInt().coerceAtLeast(20)

            val scaledBmp = Bitmap.createScaledBitmap(overlay.bitmap, overlayW, overlayH, true)

            val centerX = pageW * overlay.x
            val centerY = pageH * overlay.y
            val left = centerX - (overlayW / 2f)
            val top = centerY - (overlayH / 2f)

            canvas.save()
            if (overlay.rotationDegrees != 0f) {
                canvas.rotate(overlay.rotationDegrees, centerX, centerY)
            }
            canvas.drawBitmap(scaledBmp, left, top, paint)
            canvas.restore()
        }

        return result
    }
}
