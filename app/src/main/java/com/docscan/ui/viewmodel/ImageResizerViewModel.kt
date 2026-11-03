package com.docscan.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.util.resizer.CustomResizeMode
import com.docscan.util.resizer.ImageMetaInfo
import com.docscan.util.resizer.ImageResizerEngine
import com.docscan.util.resizer.OutputImageFormat
import com.docscan.util.resizer.QualityPreset
import com.docscan.util.resizer.QuickPreset
import com.docscan.util.resizer.ResizeOutputSummary
import com.docscan.util.resizer.ResizeResultItem
import com.docscan.util.resizer.ResizeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

import java.io.File
import java.io.FileOutputStream

enum class ResizerStep {
    SELECT_MEDIA,
    IMAGE_PREVIEW,
    PHOTO_EDIT,
    RESIZE_SETTINGS,
    RESIZING_PROGRESS,
    RESIZE_RESULT
}

enum class EditTool {
    CROP,
    ROTATE,
    FLIP,
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
    FILTERS
}

enum class FilterEffect(val displayName: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    BW("B&W"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    HIGH_CONTRAST("Sharp")
}

enum class CropPreset(val label: String, val ratioW: Float = 0f, val ratioH: Float = 0f) {
    FREE("Free", 0f, 0f),
    ORIGINAL("Original", 0f, 0f),
    SQUARE("1:1", 1f, 1f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_9_16("9:16", 9f, 16f)
}

data class ImageResizerUiState(
    val currentStep: ResizerStep = ResizerStep.SELECT_MEDIA,
    val selectedImages: List<ImageMetaInfo> = emptyList(),
    val currentImageIndex: Int = 0,
    val toastMessage: String? = null,
    val resizeSettings: ResizeSettings = ResizeSettings(),
    val liveSummary: ResizeOutputSummary = ResizeOutputSummary(0, 0, 0L, "JPG", "Optimized"),
    val activeEditTool: EditTool = EditTool.CROP,
    val selectedCropPreset: CropPreset = CropPreset.FREE,
    val activeFilterEffect: FilterEffect = FilterEffect.ORIGINAL,
    val rotationDegrees: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val processingTotalCount: Int = 0,
    val processingCurrentIndex: Int = 0,
    val results: List<ResizeResultItem> = emptyList(),
    val isSavedToGallery: Boolean = false
)

class ImageResizerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(ImageResizerUiState())
    val uiState: StateFlow<ImageResizerUiState> = _uiState.asStateFlow()

    private var currentLoadedBitmap: Bitmap? = null
    private var editedPreviewBitmap: Bitmap? = null

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun setStep(step: ResizerStep) {
        _uiState.update { it.copy(currentStep = step) }
        if (step == ResizerStep.PHOTO_EDIT) {
            loadBitmapForEditing()
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val metaList = uris.map { uri ->
                ImageResizerEngine.extractMeta(context, uri)
            }
            val firstMeta = metaList.firstOrNull()
            val initialSettings = ResizeSettings(
                customWidth = firstMeta?.width ?: 0,
                customHeight = firstMeta?.height ?: 0
            )
            val summary = if (firstMeta != null) {
                ImageResizerEngine.calculateSummary(firstMeta, initialSettings)
            } else {
                ResizeOutputSummary(0, 0, 0L, "JPG", "Optimized")
            }

            _uiState.update {
                it.copy(
                    selectedImages = metaList,
                    currentImageIndex = 0,
                    resizeSettings = initialSettings,
                    liveSummary = summary,
                    currentStep = ResizerStep.IMAGE_PREVIEW,
                    results = emptyList(),
                    isSavedToGallery = false
                )
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newMetas = uris.map { uri ->
                ImageResizerEngine.extractMeta(context, uri)
            }
            _uiState.update { current ->
                val combined = current.selectedImages + newMetas
                current.copy(selectedImages = combined)
            }
        }
    }

    fun selectImageIndex(index: Int) {
        if (index in _uiState.value.selectedImages.indices) {
            _uiState.update {
                it.copy(currentImageIndex = index)
            }
            updateSummary()
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { current ->
            val list = current.selectedImages.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            if (list.isEmpty()) {
                current.copy(selectedImages = emptyList(), currentStep = ResizerStep.SELECT_MEDIA)
            } else {
                val nextIdx = (current.currentImageIndex.coerceAtMost(list.size - 1)).coerceAtLeast(0)
                current.copy(selectedImages = list, currentImageIndex = nextIdx)
            }
        }
        updateSummary()
    }

    fun getEditedBitmap(): Bitmap? {
        return editedPreviewBitmap ?: currentLoadedBitmap
    }

    private fun loadBitmapForEditing() {
        val currentMeta = _uiState.value.selectedImages.getOrNull(_uiState.value.currentImageIndex) ?: return
        viewModelScope.launch {
            val bmp = ImageResizerEngine.loadCorrectlyOrientedBitmap(context, currentMeta.uri)
            currentLoadedBitmap = bmp
            editedPreviewBitmap = bmp
            _uiState.update {
                it.copy(
                    rotationDegrees = 0,
                    flipH = false,
                    flipV = false,
                    brightness = 0f,
                    contrast = 1f,
                    saturation = 1f,
                    activeFilterEffect = FilterEffect.ORIGINAL,
                    activeEditTool = EditTool.CROP,
                    selectedCropPreset = CropPreset.FREE
                )
            }
        }
    }

    fun setActiveEditTool(tool: EditTool) {
        _uiState.update { it.copy(activeEditTool = tool) }
    }

    fun setCropPreset(preset: CropPreset) {
        _uiState.update { it.copy(selectedCropPreset = preset) }
    }

    fun setFilterEffect(effect: FilterEffect) {
        _uiState.update {
            when (effect) {
                FilterEffect.ORIGINAL -> it.copy(activeFilterEffect = effect, brightness = 0f, contrast = 1f, saturation = 1f)
                FilterEffect.AUTO_ENHANCE -> it.copy(activeFilterEffect = effect, brightness = 6f, contrast = 1.15f, saturation = 1.2f)
                FilterEffect.BW -> it.copy(activeFilterEffect = effect, saturation = 0f, contrast = 1.2f)
                FilterEffect.WARM -> it.copy(activeFilterEffect = effect, brightness = 4f, contrast = 1.05f, saturation = 1.3f)
                FilterEffect.COOL -> it.copy(activeFilterEffect = effect, brightness = 2f, contrast = 1.1f, saturation = 0.9f)
                FilterEffect.VINTAGE -> it.copy(activeFilterEffect = effect, brightness = -5f, contrast = 1.25f, saturation = 0.8f)
                FilterEffect.HIGH_CONTRAST -> it.copy(activeFilterEffect = effect, brightness = 0f, contrast = 1.4f, saturation = 1.1f)
            }
        }
    }

    fun rotate90() {
        rotate90Cw()
    }

    fun rotate90Cw() {
        _uiState.update {
            it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
        }
    }

    fun rotate90Ccw() {
        _uiState.update {
            it.copy(rotationDegrees = (it.rotationDegrees + 270) % 360)
        }
    }

    fun rotate180() {
        _uiState.update {
            it.copy(rotationDegrees = (it.rotationDegrees + 180) % 360)
        }
    }

    fun toggleFlipH() {
        _uiState.update { it.copy(flipH = !it.flipH) }
    }

    fun toggleFlipV() {
        _uiState.update { it.copy(flipV = !it.flipV) }
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
    }

    fun setContrast(contrast: Float) {
        _uiState.update { it.copy(contrast = contrast) }
    }

    fun setSaturation(saturation: Float) {
        _uiState.update { it.copy(saturation = saturation) }
    }

    fun resetAdjustments() {
        _uiState.update {
            it.copy(
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                activeFilterEffect = FilterEffect.ORIGINAL
            )
        }
    }

    fun resetAllEdits() {
        loadBitmapForEditing()
    }

    fun applyEditsAndConfirm() {
        val source = currentLoadedBitmap ?: return
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            // Compute crop rectangle if specific ratio preset chosen
            var cropRect: android.graphics.Rect? = null
            val preset = state.selectedCropPreset
            if (preset != CropPreset.FREE && preset != CropPreset.ORIGINAL && preset.ratioW > 0f && preset.ratioH > 0f) {
                val targetRatio = preset.ratioW / preset.ratioH
                val srcW = source.width
                val srcH = source.height
                val srcRatio = srcW.toFloat() / srcH.toFloat()

                if (srcRatio > targetRatio) {
                    // Source is wider than target ratio: crop width
                    val newW = (srcH * targetRatio).roundToInt().coerceIn(1, srcW)
                    val left = ((srcW - newW) / 2).coerceAtLeast(0)
                    cropRect = android.graphics.Rect(left, 0, left + newW, srcH)
                } else {
                    // Source is taller than target ratio: crop height
                    val newH = (srcW / targetRatio).roundToInt().coerceIn(1, srcH)
                    val top = ((srcH - newH) / 2).coerceAtLeast(0)
                    cropRect = android.graphics.Rect(0, top, srcW, top + newH)
                }
            }

            val edited = ImageResizerEngine.applyEdits(
                source = source,
                cropRect = cropRect,
                rotationDegrees = state.rotationDegrees,
                flipHorizontal = state.flipH,
                flipVertical = state.flipV,
                brightness = state.brightness,
                contrast = state.contrast,
                saturation = state.saturation
            )

            // Save edited bitmap to cache directory so subsequent steps use this updated image
            val currentIdx = state.currentImageIndex
            val cacheDir = File(context.cacheDir, "edited_images").apply { mkdirs() }
            val outFile = File(cacheDir, "edit_${System.currentTimeMillis()}_$currentIdx.jpg")
            try {
                FileOutputStream(outFile).use { fos ->
                    edited.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            currentLoadedBitmap = edited
            editedPreviewBitmap = edited

            // Update current image meta dimensions and Uri
            val list = state.selectedImages.toMutableList()
            if (currentIdx in list.indices) {
                val orig = list[currentIdx]
                val newUri = if (outFile.exists()) Uri.fromFile(outFile) else orig.uri
                val newSize = if (outFile.exists()) outFile.length() else orig.sizeBytes
                list[currentIdx] = orig.copy(
                    uri = newUri,
                    width = edited.width,
                    height = edited.height,
                    sizeBytes = newSize
                )
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        selectedImages = list,
                        currentStep = ResizerStep.IMAGE_PREVIEW,
                        toastMessage = "Edits applied successfully"
                    )
                }
                updateSummary()
            }
        }
    }

    fun setQuickPreset(preset: QuickPreset) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(quickPreset = preset)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setCustomResizeMode(mode: CustomResizeMode) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(
                quickPreset = QuickPreset.NONE,
                customMode = mode
            )
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setPercentage(pct: Int) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(percentage = pct)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setCustomWidth(width: Int) {
        _uiState.update { current ->
            val img = current.selectedImages.getOrNull(current.currentImageIndex)
            val newHeight = if (current.resizeSettings.isAspectRatioLocked && img != null && img.width > 0) {
                (width.toDouble() * img.height / img.width).roundToInt()
            } else {
                current.resizeSettings.customHeight
            }
            val newSettings = current.resizeSettings.copy(customWidth = width, customHeight = newHeight)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setCustomHeight(height: Int) {
        _uiState.update { current ->
            val img = current.selectedImages.getOrNull(current.currentImageIndex)
            val newWidth = if (current.resizeSettings.isAspectRatioLocked && img != null && img.height > 0) {
                (height.toDouble() * img.width / img.height).roundToInt()
            } else {
                current.resizeSettings.customWidth
            }
            val newSettings = current.resizeSettings.copy(customWidth = newWidth, customHeight = height)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun toggleAspectRatioLock() {
        _uiState.update { current ->
            val newLock = !current.resizeSettings.isAspectRatioLocked
            val newSettings = current.resizeSettings.copy(isAspectRatioLocked = newLock)
            current.copy(resizeSettings = newSettings)
        }
    }

    fun setDimensionPreset(width: Int, height: Int) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(
                quickPreset = QuickPreset.NONE,
                customMode = CustomResizeMode.DIMENSIONS,
                customWidth = width,
                customHeight = height
            )
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setTargetFileSizeKb(kb: Long) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(targetFileSizeKb = kb)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setOutputFormat(format: OutputImageFormat) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(outputFormat = format)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    fun setQualityPreset(preset: QualityPreset) {
        _uiState.update { current ->
            val newSettings = current.resizeSettings.copy(qualityPreset = preset)
            current.copy(resizeSettings = newSettings)
        }
        updateSummary()
    }

    private fun updateSummary() {
        val current = _uiState.value
        val primaryImage = current.selectedImages.getOrNull(current.currentImageIndex) ?: return
        val summary = ImageResizerEngine.calculateSummary(primaryImage, current.resizeSettings)
        _uiState.update { it.copy(liveSummary = summary) }
    }

    fun executeResize() {
        val state = _uiState.value
        val images = state.selectedImages
        if (images.isEmpty()) return

        _uiState.update {
            it.copy(
                currentStep = ResizerStep.RESIZING_PROGRESS,
                processingTotalCount = images.size,
                processingCurrentIndex = 1,
                isSavedToGallery = false
            )
        }

        viewModelScope.launch {
            val results = mutableListOf<ResizeResultItem>()
            for ((index, imageMeta) in images.withIndex()) {
                _uiState.update { it.copy(processingCurrentIndex = index + 1) }
                val editedBmp = if (index == state.currentImageIndex) editedPreviewBitmap else null
                val result = ImageResizerEngine.processResize(
                    context = context,
                    originalMeta = imageMeta,
                    settings = state.resizeSettings,
                    editedSourceBitmap = editedBmp
                )
                results.add(result)
            }

            _uiState.update {
                it.copy(
                    results = results,
                    currentStep = ResizerStep.RESIZE_RESULT
                )
            }
        }
    }

    fun saveResultsToGallery() {
        val results = _uiState.value.results
        if (results.isEmpty()) return

        viewModelScope.launch {
            var savedCount = 0
            withContext(Dispatchers.IO) {
                for (item in results) {
                    if (item.isSuccess && item.outputFilePath != null) {
                        val savedUri = ImageResizerEngine.saveToGallery(context, item.outputFilePath, item.originalMeta.name)
                        if (savedUri != null) {
                            savedCount++
                        }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isSavedToGallery = savedCount > 0,
                    toastMessage = if (savedCount > 0) "Saved $savedCount image(s) to Gallery" else "Failed to save to Gallery"
                )
            }
        }
    }

    fun resetToStart() {
        currentLoadedBitmap = null
        editedPreviewBitmap = null
        _uiState.update {
            ImageResizerUiState(
                currentStep = ResizerStep.SELECT_MEDIA
            )
        }
    }
}
