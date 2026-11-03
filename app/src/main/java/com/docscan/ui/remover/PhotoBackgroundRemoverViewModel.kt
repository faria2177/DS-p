package com.docscan.ui.remover

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.ui.remover.engine.AlphaMatteProcessor
import com.docscan.ui.remover.engine.BackgroundRemovalEngine
import com.docscan.ui.remover.engine.MaskProcessor
import com.docscan.ui.remover.engine.TransparentPngExporter
import com.docscan.ui.remover.model.AutoCropPadding
import com.docscan.ui.remover.model.BackgroundRemovalResult
import com.docscan.ui.remover.model.BackgroundStyle
import com.docscan.ui.remover.model.BgRemovalProvider
import com.docscan.ui.remover.model.BgRemovalStage
import com.docscan.ui.remover.model.ManualBrushMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

data class PhotoBgRemoverUiState(
    val originalBitmap: Bitmap? = null,
    val transparentBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val maskBitmap: Bitmap? = null,
    val stage: BgRemovalStage = BgRemovalStage.IDLE,
    val isProcessing: Boolean = false,
    val provider: BgRemovalProvider = BgRemovalProvider.GEMINI,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.Transparent,
    val keepNaturalShadow: Boolean = false,
    val autoCropPadding: AutoCropPadding = AutoCropPadding.NONE,
    val subjectBox: Rect? = null,
    val showBeforeAfterSplit: Boolean = false,
    val splitPosition: Float = 0.5f,
    val manualMode: ManualBrushMode = ManualBrushMode.NONE,
    val brushSize: Float = 36f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class PhotoBackgroundRemoverViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = BackgroundRemovalEngine(application.applicationContext)

    private val _uiState = MutableStateFlow(PhotoBgRemoverUiState())
    val uiState: StateFlow<PhotoBgRemoverUiState> = _uiState.asStateFlow()

    // Undo / Redo stacks for mask editing
    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()
    private val maxHistorySize = 10

    /**
     * Loads a photo from Uri
     */
    fun loadPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, stage = BgRemovalStage.ANALYZING)
            val bmp = loadBitmapEfficiently(uri)
            if (bmp != null) {
                undoStack.clear()
                redoStack.clear()
                _uiState.value = PhotoBgRemoverUiState(
                    originalBitmap = bmp,
                    previewBitmap = bmp,
                    stage = BgRemovalStage.IDLE,
                    isProcessing = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    stage = BgRemovalStage.ERROR,
                    errorMessage = "Failed to load selected image."
                )
            }
        }
    }

    /**
     * Sets photo directly from a Bitmap
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        undoStack.clear()
        redoStack.clear()
        _uiState.value = PhotoBgRemoverUiState(
            originalBitmap = bitmap,
            previewBitmap = bitmap,
            stage = BgRemovalStage.IDLE,
            isProcessing = false
        )
    }

    /**
     * Triggers AI background removal
     */
    fun removeBackground() {
        val original = _uiState.value.originalBitmap ?: return
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            try {
                val result = engine.removeBackground(
                    bitmap = original,
                    providerChoice = _uiState.value.provider,
                    keepNaturalShadow = _uiState.value.keepNaturalShadow,
                    onStageChanged = { stage ->
                        _uiState.value = _uiState.value.copy(stage = stage)
                    }
                )

                undoStack.clear()
                redoStack.clear()
                undoStack.add(result.alphaMask.copy(result.alphaMask.config ?: Bitmap.Config.ARGB_8888, true))

                _uiState.value = _uiState.value.copy(
                    transparentBitmap = result.transparentBitmap,
                    previewBitmap = result.transparentBitmap,
                    maskBitmap = result.alphaMask,
                    subjectBox = result.boundingBox,
                    isProcessing = false,
                    stage = BgRemovalStage.DONE,
                    provider = result.providerUsed,
                    canUndo = false,
                    canRedo = false,
                    userMessage = "Background removed cleanly using ${result.providerUsed.displayName}!"
                )
                refreshCompositedPreview()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    stage = BgRemovalStage.ERROR,
                    errorMessage = e.message ?: "Background removal encountered an error. Please retry."
                )
            }
        }
    }

    fun setProvider(provider: BgRemovalProvider) {
        // Keep the professional tool deterministic: Gemini for AI quality, or
        // explicit local processing when the user wants offline/on-device mode.
        val resolved = if (provider == BgRemovalProvider.LOCAL_PROCESSING) {
            BgRemovalProvider.LOCAL_PROCESSING
        } else {
            BgRemovalProvider.GEMINI
        }
        _uiState.value = _uiState.value.copy(provider = resolved)
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        _uiState.value = _uiState.value.copy(backgroundStyle = style)
        refreshCompositedPreview()
    }

    fun toggleNaturalShadow(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(keepNaturalShadow = enabled)
        if (_uiState.value.transparentBitmap != null) {
            removeBackground()
        }
    }

    fun setAutoCropPadding(padding: AutoCropPadding) {
        _uiState.value = _uiState.value.copy(autoCropPadding = padding)
        refreshCompositedPreview()
    }

    fun toggleBeforeAfterSplit(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBeforeAfterSplit = show)
    }

    fun setSplitPosition(position: Float) {
        _uiState.value = _uiState.value.copy(splitPosition = position.coerceIn(0f, 1f))
    }

    fun setManualMode(mode: ManualBrushMode) {
        _uiState.value = _uiState.value.copy(manualMode = mode)
    }

    fun setBrushSize(size: Float) {
        _uiState.value = _uiState.value.copy(brushSize = size.coerceIn(10f, 100f))
    }

    /**
     * Applies manual stroke onto the alpha mask
     */
    fun applyManualBrushStroke(x: Float, y: Float, isErase: Boolean) {
        val currentMask = _uiState.value.maskBitmap ?: return
        val orig = _uiState.value.originalBitmap ?: return

        // Push to undo stack before mutation if not already pushed this stroke
        val mutableMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableMask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (isErase) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = Color.WHITE
            }
        }
        canvas.drawCircle(x, y, _uiState.value.brushSize / 2, paint)

        // Regenerate transparent bitmap from new mask
        val maskBytes = MaskProcessor.extractAlphaBytes(mutableMask)
        val cleanPixels = IntArray(orig.width * orig.height)
        orig.getPixels(cleanPixels, 0, orig.width, 0, 0, orig.width, orig.height)

        val newTransparent = AlphaMatteProcessor.createTransparentBitmap(
            cleanPixels = cleanPixels,
            alphaBytes = maskBytes,
            width = orig.width,
            height = orig.height,
            keepNaturalShadow = _uiState.value.keepNaturalShadow
        )

        _uiState.value = _uiState.value.copy(
            maskBitmap = mutableMask,
            transparentBitmap = newTransparent
        )
        refreshCompositedPreview()
    }

    fun commitBrushStroke() {
        val mask = _uiState.value.maskBitmap ?: return
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeAt(0)
        }
        undoStack.add(mask.copy(Bitmap.Config.ARGB_8888, true))
        redoStack.clear()
        _uiState.value = _uiState.value.copy(canUndo = true, canRedo = false)
    }

    fun undo() {
        if (undoStack.size > 1) {
            val current = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current)
            val previous = undoStack.last()
            restoreMask(previous)
            _uiState.value = _uiState.value.copy(
                canUndo = undoStack.size > 1,
                canRedo = true
            )
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(next)
            restoreMask(next)
            _uiState.value = _uiState.value.copy(
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    private fun restoreMask(mask: Bitmap) {
        val orig = _uiState.value.originalBitmap ?: return
        val maskBytes = MaskProcessor.extractAlphaBytes(mask)
        val cleanPixels = IntArray(orig.width * orig.height)
        orig.getPixels(cleanPixels, 0, orig.width, 0, 0, orig.width, orig.height)

        val restoredTransparent = AlphaMatteProcessor.createTransparentBitmap(
            cleanPixels = cleanPixels,
            alphaBytes = maskBytes,
            width = orig.width,
            height = orig.height,
            keepNaturalShadow = _uiState.value.keepNaturalShadow
        )

        _uiState.value = _uiState.value.copy(
            maskBitmap = mask.copy(Bitmap.Config.ARGB_8888, true),
            transparentBitmap = restoredTransparent
        )
        refreshCompositedPreview()
    }

    fun reset() {
        val orig = _uiState.value.originalBitmap
        undoStack.clear()
        redoStack.clear()
        _uiState.value = PhotoBgRemoverUiState(
            originalBitmap = orig,
            previewBitmap = orig,
            stage = BgRemovalStage.IDLE,
            isProcessing = false
        )
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null, errorMessage = null)
    }

    private fun refreshCompositedPreview() {
        val transparent = _uiState.value.transparentBitmap ?: return
        val original = _uiState.value.originalBitmap ?: return
        val style = _uiState.value.backgroundStyle
        val box = _uiState.value.subjectBox
        val padding = _uiState.value.autoCropPadding

        var baseComposited = AlphaMatteProcessor.compositeOverBackground(
            subjectBitmap = transparent,
            style = style,
            originalBitmap = original
        )

        if (box != null && padding != AutoCropPadding.NONE) {
            baseComposited = TransparentPngExporter.applyAutoCrop(baseComposited, box, padding)
        }

        _uiState.value = _uiState.value.copy(previewBitmap = baseComposited)
    }

    /**
     * Saves current image (Transparent PNG or Composite JPEG) to gallery
     */
    fun saveResult(isPng: Boolean, onComplete: (Boolean) -> Unit) {
        val preview = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            val uri = if (isPng && _uiState.value.backgroundStyle is BackgroundStyle.Transparent) {
                TransparentPngExporter.saveTransparentPng(getApplication(), preview)
            } else {
                TransparentPngExporter.saveCompositeJpeg(getApplication(), preview)
            }
            if (uri != null) {
                _uiState.value = _uiState.value.copy(userMessage = "Saved successfully to Pictures/DocScan!")
                onComplete(true)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save image to gallery.")
                onComplete(false)
            }
        }
    }

    /**
     * Shares current image
     */
    fun shareResult() {
        val preview = _uiState.value.previewBitmap ?: return
        val isPng = _uiState.value.backgroundStyle is BackgroundStyle.Transparent
        viewModelScope.launch {
            TransparentPngExporter.shareBitmap(getApplication(), preview, isPng)
        }
    }

    private suspend fun loadBitmapEfficiently(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val cr = getApplication<Application>().contentResolver
        try {
            // First decode bounds
            var input: InputStream? = cr.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            val maxDim = 1920
            var sampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while (halfH / sampleSize >= maxDim && halfW / sampleSize >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            input = cr.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(input, null, decodeOptions)
            input?.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
