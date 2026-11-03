package com.docscan.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.util.smarterase.DocumentAnalyzer
import com.docscan.util.smarterase.HandwritingDetector
import com.docscan.util.smarterase.HighResolutionProcessor
import com.docscan.util.smarterase.InpaintConfig
import com.docscan.util.smarterase.InpaintMode
import com.docscan.util.smarterase.InpaintingEngineFactory
import com.docscan.util.smarterase.MaskProcessor
import com.docscan.util.smarterase.ProtectedContentDetector
import com.docscan.util.smarterase.ProtectedRegion
import com.docscan.util.smarterase.SmartEraseHistoryManager
import com.docscan.util.smarterase.SmartSelectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class SmartEraseToolMode(val title: String) {
    SMART_BRUSH("Smart Brush"),
    RECT_ERASE("Box Erase"),
    LASSO("Lasso"),
    SMART_SELECT("Smart Select"),
    AUTO_DETECT("Auto Detect")
}

data class SmartEraseUiState(
    val originalBitmap: Bitmap? = null,
    val workingBitmap: Bitmap? = null,
    val activeMaskBitmap: Bitmap? = null,
    val candidateMaskBitmap: Bitmap? = null,
    val currentToolMode: SmartEraseToolMode = SmartEraseToolMode.SMART_BRUSH,
    val brushSizeDp: Float = 24f,
    val isSubtractMode: Boolean = false,
    val edgeSoftness: Float = 2.0f,
    val smartSelectTolerance: Float = 35f,
    val inpaintMode: InpaintMode = InpaintMode.DOCUMENT_AWARE,
    val autoInpaintOnRelease: Boolean = true,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val isComparing: Boolean = false,
    val isCompareSliderActive: Boolean = false,
    val compareSliderPos: Float = 0.5f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isPanZoomMode: Boolean = false,
    val hasUnappliedMask: Boolean = false,
    val statusMessage: String? = null
)

class SmartEraseViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SmartEraseUiState())
    val uiState: StateFlow<SmartEraseUiState> = _uiState.asStateFlow()

    private val historyManager = SmartEraseHistoryManager(maxHistorySize = 10)
    private var highResOriginal: Bitmap? = null
    private var activeInpaintJob: Job? = null

    /**
     * Initializes the Smart Erase Studio with a source document bitmap.
     */
    fun initialize(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Analyzing document structure...") }

            highResOriginal = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Scaled working bitmap (max 1600px for lightning-fast real-time interactive editing)
            val maxDim = 1600
            val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                maxDim.toFloat() / max(bitmap.width, bitmap.height).toFloat()
            } else {
                1.0f
            }

            val workingBmp = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }

            val emptyMask = Bitmap.createBitmap(workingBmp.width, workingBmp.height, Bitmap.Config.ARGB_8888)

            historyManager.initialize(workingBmp)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        originalBitmap = workingBmp.copy(Bitmap.Config.ARGB_8888, false),
                        workingBitmap = workingBmp,
                        activeMaskBitmap = emptyMask,
                        candidateMaskBitmap = null,
                        isProcessing = false,
                        processingMessage = "",
                        canUndo = historyManager.canUndo,
                        canRedo = historyManager.canRedo,
                        hasUnappliedMask = false
                    )
                }
            }
        }
    }

    fun setToolMode(mode: SmartEraseToolMode) {
        _uiState.update { it.copy(currentToolMode = mode) }
    }

    fun setBrushSize(sizeDp: Float) {
        _uiState.update { it.copy(brushSizeDp = sizeDp.coerceIn(6f, 100f)) }
    }

    fun setSubtractMode(isSubtract: Boolean) {
        _uiState.update { it.copy(isSubtractMode = isSubtract) }
    }

    fun setSmartSelectTolerance(tolerance: Float) {
        _uiState.update { it.copy(smartSelectTolerance = tolerance.coerceIn(10f, 80f)) }
    }

    fun setAutoInpaintOnRelease(enabled: Boolean) {
        _uiState.update { it.copy(autoInpaintOnRelease = enabled) }
    }

    fun togglePanZoomMode() {
        _uiState.update { it.copy(isPanZoomMode = !it.isPanZoomMode) }
    }

    fun setComparing(isComparing: Boolean) {
        _uiState.update { it.copy(isComparing = isComparing) }
    }

    fun toggleCompareSlider() {
        _uiState.update { it.copy(isCompareSliderActive = !it.isCompareSliderActive) }
    }

    fun setCompareSliderPos(pos: Float) {
        _uiState.update { it.copy(compareSliderPos = pos.coerceIn(0f, 1f)) }
    }

    /**
     * Mode A: Draws brush stroke on active mask with instant auto-inpaint option (CamScanner style).
     */
    fun applyBrushStroke(
        pointsNorm: List<Offset>,
        radiusNorm: Float,
        isSubtract: Boolean = false,
        autoInpaint: Boolean = _uiState.value.autoInpaintOnRelease
    ) {
        val currentMask = _uiState.value.activeMaskBitmap ?: return
        val w = currentMask.width
        val h = currentMask.height
        if (pointsNorm.isEmpty() || w <= 0 || h <= 0) return

        viewModelScope.launch(Dispatchers.Default) {
            val updatedMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(updatedMask)

            val radiusPx = radiusNorm * max(w, h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = radiusPx * 2f
                if (isSubtract) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                } else {
                    color = Color.WHITE
                }
            }

            if (pointsNorm.size == 1) {
                val pt = pointsNorm[0]
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    if (isSubtract) {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    } else {
                        color = Color.WHITE
                    }
                }
                canvas.drawCircle(pt.x * w, pt.y * h, radiusPx, fillPaint)
            } else {
                val path = Path()
                val p0 = pointsNorm[0]
                path.moveTo(p0.x * w, p0.y * h)
                for (i in 1 until pointsNorm.size) {
                    val pt = pointsNorm[i]
                    path.lineTo(pt.x * w, pt.y * h)
                }
                canvas.drawPath(path, paint)
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeMaskBitmap = updatedMask,
                        hasUnappliedMask = true
                    )
                }
            }

            if (autoInpaint && !isSubtract) {
                executeInpaint(updatedMask)
            }
        }
    }

    /**
     * Mode B: Rectangle / Box Erase (CamScanner style).
     */
    fun applyRectangle(
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        isSubtract: Boolean = false,
        autoInpaint: Boolean = _uiState.value.autoInpaintOnRelease
    ) {
        val working = _uiState.value.workingBitmap ?: return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Erasing box selection...") }

            val rectMask = SmartSelectionEngine.createRectangleMask(
                width = working.width,
                height = working.height,
                leftNorm = leftNorm,
                topNorm = topNorm,
                rightNorm = rightNorm,
                bottomNorm = bottomNorm,
                featherSigma = 2.0f
            )

            val currentMask = _uiState.value.activeMaskBitmap ?: Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            val mergedMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mergedMask)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (isSubtract) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
            }
            canvas.drawBitmap(rectMask, 0f, 0f, paint)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeMaskBitmap = mergedMask,
                        hasUnappliedMask = true,
                        isProcessing = false,
                        processingMessage = ""
                    )
                }
            }

            if (autoInpaint && !isSubtract) {
                executeInpaint(mergedMask)
            }
        }
    }

    /**
     * Mode C: Smart Object Selection / Magic Wand on Tap.
     */
    fun applySmartSelect(tapNormX: Float, tapNormY: Float) {
        val working = _uiState.value.workingBitmap ?: return
        val tolerance = _uiState.value.smartSelectTolerance
        val isSubtract = _uiState.value.isSubtractMode

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Segmenting object...") }

            val objectMask = SmartSelectionEngine.segmentObjectAtPoint(
                bitmap = working,
                tapXNorm = tapNormX,
                tapYNorm = tapNormY,
                tolerance = tolerance
            )

            // Merge into active mask
            val currentMask = _uiState.value.activeMaskBitmap ?: Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            val mergedMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mergedMask)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (isSubtract) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
            }
            canvas.drawBitmap(objectMask, 0f, 0f, paint)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeMaskBitmap = mergedMask,
                        hasUnappliedMask = true,
                        isProcessing = false,
                        processingMessage = ""
                    )
                }
            }
        }
    }

    /**
     * Mode D: Lasso Selection.
     */
    fun applyLasso(
        pointsNorm: List<Offset>,
        isSubtract: Boolean = false,
        autoInpaint: Boolean = _uiState.value.autoInpaintOnRelease
    ) {
        val working = _uiState.value.workingBitmap ?: return
        if (pointsNorm.size < 3) return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Generating lasso selection...") }

            val lassoMask = SmartSelectionEngine.createLassoMask(
                width = working.width,
                height = working.height,
                pointsNorm = pointsNorm,
                featherSigma = 2.0f
            )

            val currentMask = _uiState.value.activeMaskBitmap ?: Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            val mergedMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mergedMask)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (isSubtract) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
            }
            canvas.drawBitmap(lassoMask, 0f, 0f, paint)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeMaskBitmap = mergedMask,
                        hasUnappliedMask = true,
                        isProcessing = false,
                        processingMessage = ""
                    )
                }
            }

            if (autoInpaint && !isSubtract) {
                executeInpaint(mergedMask)
            }
        }
    }

    /**
     * Mode D: Auto Detect Handwriting & Pen Marks.
     */
    fun autoDetectHandwriting() {
        val working = _uiState.value.workingBitmap ?: return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "AI detecting handwriting & pen marks...") }

            val candidateMask = HandwritingDetector.detectHandwritingCandidateMask(working)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        candidateMaskBitmap = candidateMask,
                        isProcessing = false,
                        processingMessage = "",
                        statusMessage = "Handwriting candidate detected! Tap 'Apply Selection' to inpaint."
                    )
                }
            }
        }
    }

    /**
     * Merges candidate handwriting mask into active mask.
     */
    fun acceptCandidateMask() {
        val candidate = _uiState.value.candidateMaskBitmap ?: return
        val currentMask = _uiState.value.activeMaskBitmap ?: Bitmap.createBitmap(candidate.width, candidate.height, Bitmap.Config.ARGB_8888)

        viewModelScope.launch(Dispatchers.Default) {
            val mergedMask = currentMask.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mergedMask)
            canvas.drawBitmap(candidate, 0f, 0f, null)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeMaskBitmap = mergedMask,
                        candidateMaskBitmap = null,
                        hasUnappliedMask = true,
                        statusMessage = "Selection added to mask."
                    )
                }
            }
        }
    }

    fun clearCandidateMask() {
        _uiState.update { it.copy(candidateMaskBitmap = null) }
    }

    fun clearActiveMask() {
        val working = _uiState.value.workingBitmap ?: return
        val emptyMask = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        _uiState.update {
            it.copy(
                activeMaskBitmap = emptyMask,
                candidateMaskBitmap = null,
                hasUnappliedMask = false
            )
        }
    }

    /**
     * Executes AI Smart Inpainting on the working image using the active mask.
     */
    fun executeInpaint(maskToUse: Bitmap? = null) {
        val working = _uiState.value.workingBitmap ?: return
        val mask = maskToUse ?: _uiState.value.activeMaskBitmap ?: return

        val bounds = MaskProcessor.calculateMaskBounds(mask)
        if (bounds == null) {
            _uiState.update { it.copy(statusMessage = "Please paint or select an area to erase first.") }
            return
        }

        activeInpaintJob?.cancel()
        activeInpaintJob = viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "AI Reconstructing document background...") }

            val config = InpaintConfig(
                mode = _uiState.value.inpaintMode,
                edgeSoftness = _uiState.value.edgeSoftness,
                preserveLines = true,
                synthesizeTexture = true
            )

            val engine = InpaintingEngineFactory.createEngine(config.mode)
            val reconstructed = engine.reconstruct(working, mask, config)

            historyManager.pushState(reconstructed, mask, "Smart Inpaint")

            val emptyMask = Bitmap.createBitmap(reconstructed.width, reconstructed.height, Bitmap.Config.ARGB_8888)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        workingBitmap = reconstructed,
                        activeMaskBitmap = emptyMask,
                        candidateMaskBitmap = null,
                        isProcessing = false,
                        processingMessage = "",
                        canUndo = historyManager.canUndo,
                        canRedo = historyManager.canRedo,
                        hasUnappliedMask = false,
                        statusMessage = "Content cleanly removed"
                    )
                }
            }
        }
    }

    fun undo() {
        val previousState = historyManager.undo() ?: return
        _uiState.update {
            it.copy(
                workingBitmap = previousState.workingBitmap,
                activeMaskBitmap = Bitmap.createBitmap(previousState.workingBitmap.width, previousState.workingBitmap.height, Bitmap.Config.ARGB_8888),
                canUndo = historyManager.canUndo,
                canRedo = historyManager.canRedo,
                hasUnappliedMask = false,
                statusMessage = "Undo"
            )
        }
    }

    fun redo() {
        val nextState = historyManager.redo() ?: return
        _uiState.update {
            it.copy(
                workingBitmap = nextState.workingBitmap,
                activeMaskBitmap = Bitmap.createBitmap(nextState.workingBitmap.width, nextState.workingBitmap.height, Bitmap.Config.ARGB_8888),
                canUndo = historyManager.canUndo,
                canRedo = historyManager.canRedo,
                hasUnappliedMask = false,
                statusMessage = "Redo"
            )
        }
    }

    fun reset() {
        val initial = historyManager.resetToInitial() ?: return
        _uiState.update {
            it.copy(
                workingBitmap = initial.workingBitmap,
                activeMaskBitmap = Bitmap.createBitmap(initial.workingBitmap.width, initial.workingBitmap.height, Bitmap.Config.ARGB_8888),
                candidateMaskBitmap = null,
                canUndo = historyManager.canUndo,
                canRedo = historyManager.canRedo,
                hasUnappliedMask = false,
                statusMessage = "Reset to original"
            )
        }
    }

    /**
     * Processes full high-resolution image non-destructively and returns the final pristine bitmap.
     */
    suspend fun applyAndGetFinalBitmap(): Bitmap = withContext(Dispatchers.Default) {
        val highRes = highResOriginal
        val currentWorking = _uiState.value.workingBitmap
        if (highRes == null || currentWorking == null) {
            return@withContext currentWorking ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        }

        if (currentWorking.width != highRes.width || currentWorking.height != highRes.height) {
            return@withContext Bitmap.createScaledBitmap(currentWorking, highRes.width, highRes.height, true)
        }
        return@withContext currentWorking
    }
}
