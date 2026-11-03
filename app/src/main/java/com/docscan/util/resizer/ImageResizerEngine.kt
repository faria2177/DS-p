package com.docscan.util.resizer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class QuickPreset {
    SMALL, MEDIUM, LARGE, NONE
}

enum class CustomResizeMode {
    PERCENTAGE, DIMENSIONS, FILE_SIZE
}

enum class OutputImageFormat(val extension: String, val mimeType: String) {
    ORIGINAL("jpg", "image/jpeg"),
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

enum class QualityPreset(val qualityVal: Int) {
    AUTO(85),
    HIGH(95),
    MEDIUM(80),
    LOW(65)
}

data class ImageMetaInfo(
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val format: String,
    val localFilePath: String? = null
)

data class ResizeSettings(
    val quickPreset: QuickPreset = QuickPreset.MEDIUM,
    val customMode: CustomResizeMode = CustomResizeMode.PERCENTAGE,
    val percentage: Int = 50,
    val customWidth: Int = 0,
    val customHeight: Int = 0,
    val isAspectRatioLocked: Boolean = true,
    val targetFileSizeKb: Long = 100,
    val outputFormat: OutputImageFormat = OutputImageFormat.ORIGINAL,
    val qualityPreset: QualityPreset = QualityPreset.AUTO
)

data class ResizeOutputSummary(
    val newWidth: Int,
    val newHeight: Int,
    val estimatedBytes: Long,
    val format: String,
    val qualityLabel: String
)

data class ResizeResultItem(
    val originalMeta: ImageMetaInfo,
    val outputUri: Uri? = null,
    val outputFilePath: String? = null,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val outputSizeBytes: Long = 0,
    val savedPercentage: Int = 0,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

object ImageResizerEngine {

    /**
     * Reads image metadata safely with inJustDecodeBounds and EXIF inspection
     */
    suspend fun extractMeta(context: Context, uri: Uri): ImageMetaInfo = withContext(Dispatchers.IO) {
        var width = 0
        var height = 0
        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        var name = "image_${System.currentTimeMillis()}.jpg"
        var sizeBytes = 0L

        // Query metadata from ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val n = cursor.getString(nameIndex)
                        if (!n.isNullOrBlank()) name = n
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Extract dimensions and rotation
        var rotationDegrees = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight
                if (!options.outMimeType.isNullOrBlank()) {
                    mimeType = options.outMimeType
                }
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    val tmp = width
                    width = height
                    height = tmp
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    sizeBytes = it.length
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val formatStr = when {
            mimeType.contains("png", true) || name.endsWith(".png", true) -> "PNG"
            mimeType.contains("webp", true) || name.endsWith(".webp", true) -> "WEBP"
            mimeType.contains("gif", true) || name.endsWith(".gif", true) -> "GIF"
            else -> "JPG"
        }

        ImageMetaInfo(
            uri = uri,
            name = name,
            width = max(1, width),
            height = max(1, height),
            sizeBytes = max(100L, sizeBytes),
            format = formatStr
        )
    }

    /**
     * Calculates calculated live summary based on original dimensions and current resize settings.
     */
    fun calculateSummary(
        originalMeta: ImageMetaInfo,
        settings: ResizeSettings
    ): ResizeOutputSummary {
        val origW = originalMeta.width
        val origH = originalMeta.height
        val origBytes = originalMeta.sizeBytes

        var targetW = origW
        var targetH = origH
        var estimatedBytes = origBytes

        if (settings.quickPreset != QuickPreset.NONE) {
            when (settings.quickPreset) {
                QuickPreset.SMALL -> {
                    // Small: ~40% scale, ~65% quality compression -> ~50-80KB target
                    val scale = 0.40f
                    targetW = max(1, (origW * scale).roundToInt())
                    targetH = max(1, (origH * scale).roundToInt())
                    estimatedBytes = min(origBytes, (origBytes * 0.15f).toLong().coerceAtLeast(35 * 1024L))
                }
                QuickPreset.MEDIUM -> {
                    // Medium: ~65% scale, ~80% quality -> ~100-150KB
                    val scale = 0.65f
                    targetW = max(1, (origW * scale).roundToInt())
                    targetH = max(1, (origH * scale).roundToInt())
                    estimatedBytes = min(origBytes, (origBytes * 0.35f).toLong().coerceAtLeast(80 * 1024L))
                }
                QuickPreset.LARGE -> {
                    // Large: ~85% scale, ~90% quality -> ~200-300KB
                    val scale = 0.85f
                    targetW = max(1, (origW * scale).roundToInt())
                    targetH = max(1, (origH * scale).roundToInt())
                    estimatedBytes = min(origBytes, (origBytes * 0.65f).toLong().coerceAtLeast(150 * 1024L))
                }
                QuickPreset.NONE -> {}
            }
        } else {
            when (settings.customMode) {
                CustomResizeMode.PERCENTAGE -> {
                    val factor = (settings.percentage.coerceIn(1, 200)) / 100f
                    targetW = max(1, (origW * factor).roundToInt())
                    targetH = max(1, (origH * factor).roundToInt())
                    val areaFactor = factor * factor
                    val qualityFactor = settings.qualityPreset.qualityVal / 100f
                    estimatedBytes = (origBytes * areaFactor * qualityFactor).toLong().coerceAtLeast(10 * 1024L)
                }
                CustomResizeMode.DIMENSIONS -> {
                    targetW = if (settings.customWidth > 0) settings.customWidth else origW
                    targetH = if (settings.customHeight > 0) settings.customHeight else origH
                    val areaFactor = (targetW.toDouble() * targetH) / max(1.0, origW.toDouble() * origH)
                    val qualityFactor = settings.qualityPreset.qualityVal / 100f
                    estimatedBytes = (origBytes * areaFactor * qualityFactor).toLong().coerceAtLeast(10 * 1024L)
                }
                CustomResizeMode.FILE_SIZE -> {
                    val targetKb = settings.targetFileSizeKb.coerceAtLeast(10)
                    estimatedBytes = targetKb * 1024L
                    // Estimate potential downscale if target size is much smaller
                    if (estimatedBytes < origBytes) {
                        val ratio = estimatedBytes.toDouble() / origBytes
                        val dimScale = kotlin.math.sqrt(ratio).coerceIn(0.2, 1.0)
                        targetW = max(1, (origW * dimScale).roundToInt())
                        targetH = max(1, (origH * dimScale).roundToInt())
                    }
                }
            }
        }

        val formatStr = when (settings.outputFormat) {
            OutputImageFormat.ORIGINAL -> originalMeta.format
            OutputImageFormat.JPG -> "JPG"
            OutputImageFormat.PNG -> "PNG"
            OutputImageFormat.WEBP -> "WEBP"
        }

        val qualityLabel = when (settings.qualityPreset) {
            QualityPreset.AUTO -> "Optimized"
            QualityPreset.HIGH -> "High (95%)"
            QualityPreset.MEDIUM -> "Balanced (80%)"
            QualityPreset.LOW -> "Max Compression (65%)"
        }

        return ResizeOutputSummary(
            newWidth = targetW,
            newHeight = targetH,
            estimatedBytes = estimatedBytes,
            format = formatStr,
            qualityLabel = qualityLabel
        )
    }

    /**
     * Decodes and scales bitmap safely handling memory and EXIF orientation
     */
    suspend fun loadCorrectlyOrientedBitmap(
        context: Context,
        uri: Uri,
        maxDim: Int = 4096
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First check bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            while ((options.outWidth / inSampleSize) > maxDim || (options.outHeight / inSampleSize) > maxDim) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            // Read EXIF orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    val exif = ExifInterface(it)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Executes the complete resize and compression process for a single image.
     */
    suspend fun processResize(
        context: Context,
        originalMeta: ImageMetaInfo,
        settings: ResizeSettings,
        editedSourceBitmap: Bitmap? = null
    ): ResizeResultItem = withContext(Dispatchers.IO) {
        try {
            val srcBitmap: Bitmap = editedSourceBitmap ?: loadCorrectlyOrientedBitmap(context, originalMeta.uri)
            ?: return@withContext ResizeResultItem(
                originalMeta = originalMeta,
                isSuccess = false,
                errorMessage = "Failed to load original image."
            )

            val summary = calculateSummary(originalMeta, settings)
            val targetW = max(1, summary.newWidth)
            val targetH = max(1, summary.newHeight)

            // High quality bilinear scaling
            val scaledBitmap = if (srcBitmap.width == targetW && srcBitmap.height == targetH) {
                srcBitmap
            } else {
                Bitmap.createScaledBitmap(srcBitmap, targetW, targetH, true)
            }

            // Determine compress format
            val compressFormat = when (settings.outputFormat) {
                OutputImageFormat.PNG -> Bitmap.CompressFormat.PNG
                OutputImageFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                OutputImageFormat.JPG, OutputImageFormat.ORIGINAL -> {
                    if (originalMeta.format == "PNG" && settings.outputFormat == OutputImageFormat.ORIGINAL) {
                        Bitmap.CompressFormat.PNG
                    } else if (originalMeta.format == "WEBP" && settings.outputFormat == OutputImageFormat.ORIGINAL) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                }
            }

            // Compression Strategy
            val outputBytes: ByteArray
            if (settings.quickPreset == QuickPreset.NONE && settings.customMode == CustomResizeMode.FILE_SIZE) {
                outputBytes = compressToTargetFileSize(scaledBitmap, compressFormat, settings.targetFileSizeKb * 1024L)
            } else {
                val quality = when (settings.quickPreset) {
                    QuickPreset.SMALL -> 65
                    QuickPreset.MEDIUM -> 80
                    QuickPreset.LARGE -> 90
                    QuickPreset.NONE -> settings.qualityPreset.qualityVal
                }
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(compressFormat, quality, stream)
                outputBytes = stream.toByteArray()
            }

            // Save to temp app-specific storage for preview & saving
            val ext = when (compressFormat) {
                Bitmap.CompressFormat.PNG -> "png"
                Bitmap.CompressFormat.JPEG -> "jpg"
                else -> "webp"
            }
            val tempDir = File(context.cacheDir, "resized_images").apply { if (!exists()) mkdirs() }
            val cleanBaseName = originalMeta.name.substringBeforeLast(".")
            val tempFile = File(tempDir, "resized_${cleanBaseName}_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { it.write(outputBytes) }

            val savedPercentage = if (originalMeta.sizeBytes > 0) {
                val reduction = ((originalMeta.sizeBytes - outputBytes.size).toDouble() / originalMeta.sizeBytes * 100).roundToInt()
                max(0, reduction)
            } else 0

            return@withContext ResizeResultItem(
                originalMeta = originalMeta,
                outputFilePath = tempFile.absolutePath,
                outputUri = Uri.fromFile(tempFile),
                outputWidth = targetW,
                outputHeight = targetH,
                outputSizeBytes = outputBytes.size.toLong(),
                savedPercentage = savedPercentage,
                isSuccess = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext ResizeResultItem(
                originalMeta = originalMeta,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Resize failed"
            )
        }
    }

    /**
     * Binary search compression algorithm to target exact file size smoothly.
     */
    private fun compressToTargetFileSize(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        targetBytes: Long
    ): ByteArray {
        if (format == Bitmap.CompressFormat.PNG) {
            // PNG is lossless, compression level is not variable like JPEG/WebP
            val stream = ByteArrayOutputStream()
            bitmap.compress(format, 100, stream)
            return stream.toByteArray()
        }

        var low = 15
        var high = 95
        var bestBytes: ByteArray? = null
        var bestDiff = Long.MAX_VALUE

        for (iter in 0..6) {
            val mid = (low + high) / 2
            val stream = ByteArrayOutputStream()
            bitmap.compress(format, mid, stream)
            val currentBytes = stream.toByteArray()
            val currentSize = currentBytes.size.toLong()
            val diff = kotlin.math.abs(currentSize - targetBytes)

            if (diff < bestDiff) {
                bestDiff = diff
                bestBytes = currentBytes
            }

            if (currentSize > targetBytes) {
                high = mid - 1
            } else {
                low = mid + 1
            }

            if (low > high) break
        }

        return bestBytes ?: run {
            val fallback = ByteArrayOutputStream()
            bitmap.compress(format, 80, fallback)
            fallback.toByteArray()
        }
    }

    /**
     * Saves resized file into Android MediaStore Pictures/Resized folder without needing legacy storage permissions.
     */
    suspend fun saveToGallery(context: Context, sourceFilePath: String, originalName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return@withContext null

            val ext = sourceFile.extension.ifBlank { "jpg" }
            val mimeType = when (ext.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            val cleanBaseName = originalName.substringBeforeLast(".")
            val newFileName = "Resized_${cleanBaseName}_${System.currentTimeMillis()}.$ext"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newFileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DocScan_Resizer")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = context.contentResolver.insert(collection, values) ?: return@withContext null

            context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(insertedUri, values, null, null)
            }

            return@withContext insertedUri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Applies image edit adjustments (crop, rotation, flip, brightness, contrast, saturation).
     */
    suspend fun applyEdits(
        source: Bitmap,
        cropRect: Rect? = null,
        rotationDegrees: Int = 0,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        brightness: Float = 0f, // -100 to 100
        contrast: Float = 1f,   // 0.5 to 2.0
        saturation: Float = 1f  // 0.0 to 2.0
    ): Bitmap = withContext(Dispatchers.Default) {
        var bmp = source

        // 1. Crop if specified
        if (cropRect != null) {
            val safeLeft = cropRect.left.coerceIn(0, bmp.width - 1)
            val safeTop = cropRect.top.coerceIn(0, bmp.height - 1)
            val safeRight = cropRect.right.coerceIn(safeLeft + 1, bmp.width)
            val safeBottom = cropRect.bottom.coerceIn(safeTop + 1, bmp.height)
            val w = safeRight - safeLeft
            val h = safeBottom - safeTop
            val cropped = Bitmap.createBitmap(bmp, safeLeft, safeTop, w, h)
            if (cropped != bmp && bmp != source) {
                bmp.recycle()
            }
            bmp = cropped
        }

        // 2. Matrix transformations (Rotate & Flip)
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        val sx = if (flipHorizontal) -1f else 1f
        val sy = if (flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            matrix.postScale(sx, sy)
        }

        if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (transformed != bmp && bmp != source) {
                bmp.recycle()
            }
            bmp = transformed
        }

        // 3. Color Filter adjustments (Brightness, Contrast, Saturation)
        if (brightness != 0f || contrast != 1f || saturation != 1f) {
            val adjusted = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(adjusted)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val cm = ColorMatrix()

            // Saturation
            if (saturation != 1f) {
                val satCm = ColorMatrix()
                satCm.setSaturation(saturation)
                cm.postConcat(satCm)
            }

            // Contrast & Brightness
            // scale: contrast, translate: brightness
            val scale = contrast
            val translate = brightness * 1.5f
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(bmp, 0f, 0f, paint)

            if (bmp != source) {
                bmp.recycle()
            }
            bmp = adjusted
        }

        return@withContext bmp
    }
}
