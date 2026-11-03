package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.docscan.data.model.FilterType
import com.docscan.data.model.PageEntity
import com.docscan.ui.components.QuadCropView
import com.docscan.ui.components.SignatureDialog
import com.docscan.ui.components.WatermarkDialog
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.AiOrchestrator
import com.docscan.util.EdgeDetector
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import com.docscan.util.PdfExporter
import com.docscan.util.TextRecognizerHelper
import com.docscan.ui.components.textedit.InPlaceTextEditorDialog
import com.docscan.util.textedit.DocumentCoordinateTransformer
import com.docscan.util.textedit.DocumentImagePreprocessor
import com.docscan.util.textedit.DocumentTextInpainter
import com.docscan.util.textedit.DocumentTextRenderer
import com.docscan.util.textedit.EditTextOperation
import com.docscan.util.textedit.HybridTextRecognitionEngine
import com.docscan.util.textedit.OcrDocument
import com.docscan.util.textedit.OcrTextBlock
import com.docscan.util.textedit.OcrTextElement
import com.docscan.util.textedit.OcrTextLine
import com.docscan.util.textedit.TextEditGranularity
import com.docscan.util.textedit.TextStyleEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// --- Data Models for Page Editor Overlays & State ---

enum class EditorTab(val title: String) {
    IMAGES("Images"),
    MARKUP("Markup"),
    PAGE("Page")
}

enum class ActiveEditorTool {
    NONE,
    CROP,
    FILTER,
    EDIT_TEXT,
    SMART_ERASE,
    BRUSH
}

enum class BrushMode(val label: String) {
    INK_PEN("Ink Pen"),
    HIGHLIGHTER("Highlighter"),
    LINE("Line"),
    ARROW("Arrow"),
    RECTANGLE("Box"),
    CIRCLE("Circle")
}

enum class EraseMode(val label: String) {
    AI_INPAINT("AI Inpaint"),
    WHITEOUT("Whiteout")
}

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val fontSizeSp: Float = 18f,
    val color: Color = Color.Black,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val backgroundColor: Color = Color.Transparent,
    val fontFamilyType: String = "Default",
    val isStamp: Boolean = false
)

data class SignatureOverlay(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val x: Float = 0.5f,
    val y: Float = 0.75f,
    val widthRatio: Float = 0.35f,
    val rotationDegrees: Float = 0f
)

data class MosaicRegion(
    val id: String = UUID.randomUUID().toString(),
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

data class BrushStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val mode: BrushMode = BrushMode.INK_PEN,
    val alpha: Float = 1.0f
)

data class EraseStroke(
    val points: List<Offset>,
    val strokeWidth: Float,
    val mode: EraseMode = EraseMode.AI_INPAINT,
    val color: Color = Color.White
)

data class WatermarkConfig(
    val text: String = "",
    val opacity: Float = 0.35f,
    val colorLong: Long = 0xFF64748B,
    val isDiagonal: Boolean = true
)

data class DetectedTextBlock(
    val text: String,
    val rect: RectF
)

data class EditorHistorySnapshot(
    val baseBitmap: Bitmap,
    val filterType: FilterType,
    val brightness: Float,
    val contrast: Float,
    val textOverlays: List<TextOverlay>,
    val signatureOverlays: List<SignatureOverlay>,
    val mosaicRegions: List<MosaicRegion>,
    val brushStrokes: List<BrushStroke>,
    val eraseStrokes: List<EraseStroke>,
    val watermarkConfig: WatermarkConfig,
    val pageTitle: String,
    val editTextOperations: List<EditTextOperation> = emptyList()
)

private fun autoTextAlignment(rect: RectF): String {
    val center = (rect.left + rect.right) / 2f
    val width = rect.width()
    return when {
        // Headings/titles that occupy a compact area around the page center.
        width < 0.72f && kotlin.math.abs(center - 0.5f) < 0.055f -> "CENTER"
        // Strongly right-positioned compact labels/numbers.
        width < 0.42f && rect.right > 0.965f && rect.left > 0.58f -> "RIGHT"
        else -> "LEFT"
    }
}

private fun calcEditorImageFrame(canvasSize: Size, imgWidth: Int, imgHeight: Int): Rect {
    if (imgWidth <= 0 || imgHeight <= 0 || canvasSize.width <= 0 || canvasSize.height <= 0) {
        return Rect(0f, 0f, canvasSize.width, canvasSize.height)
    }
    val imgAspect = imgWidth.toFloat() / imgHeight.toFloat()
    val canvasAspect = canvasSize.width / canvasSize.height

    return if (imgAspect > canvasAspect) {
        val targetWidth = canvasSize.width
        val targetHeight = targetWidth / imgAspect
        val top = (canvasSize.height - targetHeight) / 2f
        Rect(0f, top, targetWidth, top + targetHeight)
    } else {
        val targetHeight = canvasSize.height
        val targetWidth = targetHeight * imgAspect
        val left = (canvasSize.width - targetWidth) / 2f
        Rect(left, 0f, left + targetWidth, targetHeight)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePageEditorScreen(
    pageId: Long,
    viewModel: ScannerViewModel,
    initialTool: ActiveEditorTool = ActiveEditorTool.NONE,
    onNavigateBack: () -> Unit,
    onNavigateToDocumentEditText: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPdfTextEditMode = initialTool == ActiveEditorTool.EDIT_TEXT
    val pdfEditAccent = Color(0xFFFF6B63)

    // Page Data
    var pageEntity by remember { mutableStateOf<PageEntity?>(null) }
    var currentBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Editor Tab & Tool State
    var selectedTab by remember(initialTool) { mutableStateOf(EditorTab.IMAGES) }
    var activeTool by remember(initialTool) { mutableStateOf(initialTool) }

    // Filter & Adjustments State
    var activeFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    var brightnessValue by remember { mutableFloatStateOf(0f) }
    var contrastValue by remember { mutableFloatStateOf(1f) }
    var previewFilteredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Overlays State
    var textOverlays by remember { mutableStateOf<List<TextOverlay>>(emptyList()) }
    var selectedTextOverlayId by remember { mutableStateOf<String?>(null) }

    var signatureOverlays by remember { mutableStateOf<List<SignatureOverlay>>(emptyList()) }
    var selectedSignatureId by remember { mutableStateOf<String?>(null) }

    var mosaicRegions by remember { mutableStateOf<List<MosaicRegion>>(emptyList()) }
    var selectedMosaicId by remember { mutableStateOf<String?>(null) }

    var brushStrokes by remember { mutableStateOf<List<BrushStroke>>(emptyList()) }
    var currentBrushPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    var brushWidth by remember { mutableFloatStateOf(8f) }
    var brushMode by remember { mutableStateOf(BrushMode.INK_PEN) }
    var brushOpacity by remember { mutableFloatStateOf(1.0f) }

    var eraseStrokes by remember { mutableStateOf<List<EraseStroke>>(emptyList()) }
    var currentErasePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var eraseWidth by remember { mutableFloatStateOf(24f) }
    var eraseMode by remember { mutableStateOf(EraseMode.AI_INPAINT) }
    var isAiAutoCleaning by remember { mutableStateOf(false) }

    var watermarkConfig by remember { mutableStateOf(WatermarkConfig()) }
    var pageTitle by remember { mutableStateOf("") }

    // Crop State
    var cropCorners by remember {
        mutableStateOf(
            listOf(
                Offset(0.05f, 0.05f),
                Offset(0.95f, 0.05f),
                Offset(0.95f, 0.95f),
                Offset(0.05f, 0.95f)
            )
        )
    }

    // OCR / Text Detection & In-Place Editing State
    val ocrEngine = remember(context) { HybridTextRecognitionEngine(context) }
    var ocrDocument by remember { mutableStateOf<OcrDocument?>(null) }
    var isOcrDetecting by remember { mutableStateOf(false) }
    var textEditGranularity by remember { mutableStateOf(TextEditGranularity.LINE) }
    var selectedOcrItem by remember { mutableStateOf<Any?>(null) }
    var editTextOperations by remember { mutableStateOf<List<EditTextOperation>>(emptyList()) }

    // Undo / Redo History Stack
    val undoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }

    // Dialog & Sheet States
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showTextEditorDialog by remember { mutableStateOf(false) }
    var editingTextOverlay by remember { mutableStateOf<TextOverlay?>(null) }
    var showFullOcrSheet by remember { mutableStateOf(false) }
    var fullOcrExtractedText by remember { mutableStateOf("") }
    var isErasePanZoomMode by remember { mutableStateOf(false) }
    var showPageTitleDialog by remember { mutableStateOf(false) }
    var showPdfOptionsSheet by remember { mutableStateOf(false) }
    var showRetakeSourceDialog by remember { mutableStateOf(false) }
    var showSmartEraseStudio by remember { mutableStateOf(false) }

    // Helper to capture state snapshot for Undo
    fun saveSnapshot() {
        val base = currentBaseBitmap ?: return
        redoStack.clear()
        undoStack.add(
            EditorHistorySnapshot(
                baseBitmap = base,
                filterType = activeFilter,
                brightness = brightnessValue,
                contrast = contrastValue,
                textOverlays = textOverlays,
                signatureOverlays = signatureOverlays,
                mosaicRegions = mosaicRegions,
                brushStrokes = brushStrokes,
                eraseStrokes = eraseStrokes,
                watermarkConfig = watermarkConfig,
                pageTitle = pageTitle,
                editTextOperations = editTextOperations
            )
        )
    }

    // Camera launcher for Retake
    var retakeTempUri by remember { mutableStateOf<Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && retakeTempUri != null) {
            val bmp = FileUtils.loadBitmapsFromUri(context, retakeTempUri!!).firstOrNull()
            if (bmp != null) {
                saveSnapshot()
                currentBaseBitmap = bmp
                Toast.makeText(context, "Page photo updated from camera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gallery launcher for Retake
    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = FileUtils.loadBitmapsFromUri(context, uri).firstOrNull()
            if (bmp != null) {
                saveSnapshot()
                currentBaseBitmap = bmp
                Toast.makeText(context, "Page photo updated from gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load page data initially
    LaunchedEffect(pageId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val page = viewModel.getPageByIdDirect(pageId)
            pageEntity = page
            if (page != null) {
                val imagePath = if (File(page.processedImagePath).exists()) page.processedImagePath else page.originalImagePath
                val loaded = FileUtils.loadBitmap(imagePath)
                currentBaseBitmap = loaded
                pageTitle = page.notes ?: "Page ${page.pageNumber}"
                activeFilter = try {
                    FilterType.valueOf(page.filterType)
                } catch (e: Exception) {
                    FilterType.ORIGINAL
                }
                brightnessValue = page.brightness
                contrastValue = page.contrast
                if (!page.watermarkText.isNullOrBlank()) {
                    watermarkConfig = WatermarkConfig(
                        text = page.watermarkText!!,
                        opacity = page.watermarkOpacity,
                        colorLong = page.watermarkColor
                    )
                }
            }
        }
        isLoading = false
    }

    // Update filter preview whenever base bitmap, filter type, or adjustments change
    LaunchedEffect(currentBaseBitmap, activeFilter, brightnessValue, contrastValue) {
        val base = currentBaseBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            previewFilteredBitmap = if (activeFilter == FilterType.ORIGINAL && brightnessValue == 0f && contrastValue == 1f) {
                base
            } else {
                ImageProcessor.applyFilter(base, activeFilter, brightnessValue, contrastValue)
            }
        }
    }

    // Handle back button press
    BackHandler {
        if (undoStack.isNotEmpty()) {
            showDiscardConfirmDialog = true
        } else {
            onNavigateBack()
        }
    }

    // Theme Colors matching the screenshots (#18181B dark top & bottom, #121214 canvas, #2DBA8D emerald accent)
    val topBarBg = ThemeManager.themeColor(Color(0xFF18181B), Color(0xFFFFFFFF))
    val bottomBarBg = ThemeManager.themeColor(Color(0xFF19191C), Color(0xFFFFFFFF))
    val canvasBg = ThemeManager.themeColor(Color(0xFF121214), Color(0xFFF8FAFC))
    val accentEmerald = Color(0xFF2DBA8D)
    val inactiveGray = ThemeManager.themeColor(Color(0xFF9E9E9E), Color(0xFF475569))
    val surfaceBorder = ThemeManager.themeColor(Color(0xFF2C2C30), Color(0xFFE2E8F0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentEmerald, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading Page Editor...", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 14.sp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ==========================================
                // 1. TOP BAR (Close X, Undo/Redo, Share, Done)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topBarBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Close 'X' Button
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                showDiscardConfirmDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_editor_close")
                    ) {
                        Icon(
                            if (isPdfTextEditMode) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = if (isPdfTextEditMode) "Back" else "Close",
                            tint = if (isPdfTextEditMode) pdfEditAccent else Color.White,
                            modifier = Modifier.size(if (isPdfTextEditMode) 28.dp else 24.dp)
                        )
                    }

                    // Center: History Controls (Undo & Redo)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    val currentSnap = EditorHistorySnapshot(
                                        baseBitmap = currentBaseBitmap!!,
                                        filterType = activeFilter,
                                        brightness = brightnessValue,
                                        contrast = contrastValue,
                                        textOverlays = textOverlays,
                                        signatureOverlays = signatureOverlays,
                                        mosaicRegions = mosaicRegions,
                                        brushStrokes = brushStrokes,
                                        eraseStrokes = eraseStrokes,
                                        watermarkConfig = watermarkConfig,
                                        pageTitle = pageTitle,
                                        editTextOperations = editTextOperations
                                    )
                                    redoStack.add(currentSnap)

                                    val prev = undoStack.removeAt(undoStack.lastIndex)
                                    currentBaseBitmap = prev.baseBitmap
                                    activeFilter = prev.filterType
                                    brightnessValue = prev.brightness
                                    contrastValue = prev.contrast
                                    textOverlays = prev.textOverlays
                                    signatureOverlays = prev.signatureOverlays
                                    mosaicRegions = prev.mosaicRegions
                                    brushStrokes = prev.brushStrokes
                                    eraseStrokes = prev.eraseStrokes
                                    watermarkConfig = prev.watermarkConfig
                                    pageTitle = prev.pageTitle
                                    editTextOperations = prev.editTextOperations
                                    Toast.makeText(context, "Undo applied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.testTag("btn_editor_undo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) (if (isPdfTextEditMode) pdfEditAccent else ThemeManager.themeColor(Color.White, Color(0xFF0F172A))) else ThemeManager.themeColor(Color(0xFF55555A), Color(0xFF475569)),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val currentSnap = EditorHistorySnapshot(
                                        baseBitmap = currentBaseBitmap!!,
                                        filterType = activeFilter,
                                        brightness = brightnessValue,
                                        contrast = contrastValue,
                                        textOverlays = textOverlays,
                                        signatureOverlays = signatureOverlays,
                                        mosaicRegions = mosaicRegions,
                                        brushStrokes = brushStrokes,
                                        eraseStrokes = eraseStrokes,
                                        watermarkConfig = watermarkConfig,
                                        pageTitle = pageTitle,
                                        editTextOperations = editTextOperations
                                    )
                                    undoStack.add(currentSnap)

                                    val next = redoStack.removeAt(redoStack.lastIndex)
                                    currentBaseBitmap = next.baseBitmap
                                    activeFilter = next.filterType
                                    brightnessValue = next.brightness
                                    contrastValue = next.contrast
                                    textOverlays = next.textOverlays
                                    signatureOverlays = next.signatureOverlays
                                    mosaicRegions = next.mosaicRegions
                                    brushStrokes = next.brushStrokes
                                    eraseStrokes = next.eraseStrokes
                                    watermarkConfig = next.watermarkConfig
                                    pageTitle = next.pageTitle
                                    editTextOperations = next.editTextOperations
                                    Toast.makeText(context, "Redo applied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.testTag("btn_editor_redo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) (if (isPdfTextEditMode) pdfEditAccent else ThemeManager.themeColor(Color.White, Color(0xFF0F172A))) else ThemeManager.themeColor(Color(0xFF55555A), Color(0xFF475569)),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Right: Utility Actions & Prominent Save Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isPdfTextEditMode) {
                            // Save to Gallery Icon Button
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.Default) {
                                        val rendered = renderFinalPageBitmap(
                                            base = previewFilteredBitmap ?: currentBaseBitmap!!,
                                            eraseStrokes = eraseStrokes,
                                            mosaicRegions = mosaicRegions,
                                            brushStrokes = brushStrokes,
                                            textOverlays = textOverlays,
                                            signatureOverlays = signatureOverlays,
                                            watermarkConfig = watermarkConfig,
                                            editTextOperations = editTextOperations
                                        )
                                        val title = pageTitle.ifBlank { "Scanned_Page" }
                                        val uri = FileUtils.saveBitmapToGallery(context, rendered, title)
                                        withContext(Dispatchers.Main) {
                                            if (uri != null) {
                                                Toast.makeText(context, "Page saved to Gallery (Pictures/DocScanner)", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to save to Gallery", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ThemeManager.themeColor(Color(0xFF26262A), Color(0xFFF1F5F9)))
                                    .testTag("btn_editor_save_gallery")
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Save to Gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Share Icon Button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val rendered = renderFinalPageBitmap(
                                            base = previewFilteredBitmap ?: currentBaseBitmap!!,
                                            eraseStrokes = eraseStrokes,
                                            mosaicRegions = mosaicRegions,
                                            brushStrokes = brushStrokes,
                                            textOverlays = textOverlays,
                                            signatureOverlays = signatureOverlays,
                                            watermarkConfig = watermarkConfig,
                                            editTextOperations = editTextOperations
                                        )
                                        val tempFile = File(FileUtils.getTempDir(context), "PAGE_SHARE_${System.currentTimeMillis()}.jpg")
                                        FileUtils.saveBitmapToFile(rendered, tempFile)
                                        FileUtils.shareImageFiles(context, listOf(tempFile), pageTitle.ifBlank { "Scanned Page" })
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ThemeManager.themeColor(Color(0xFF26262A), Color(0xFFF1F5F9)))
                                    .testTag("btn_editor_share")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                        }

                        // Prominent Save Button
                        Button(
                            onClick = {
                                if (pageEntity != null && currentBaseBitmap != null) {
                                    isSaving = true
                                    scope.launch(Dispatchers.Default) {
                                        val finalRendered = renderFinalPageBitmap(
                                            base = previewFilteredBitmap ?: currentBaseBitmap!!,
                                            eraseStrokes = eraseStrokes,
                                            mosaicRegions = mosaicRegions,
                                            brushStrokes = brushStrokes,
                                            textOverlays = textOverlays,
                                            signatureOverlays = signatureOverlays,
                                            watermarkConfig = watermarkConfig,
                                            editTextOperations = editTextOperations
                                        )

                                        // Re-extract OCR text if text was modified
                                        val newOcr = try {
                                            TextRecognizerHelper.extractText(finalRendered)
                                        } catch (e: Exception) {
                                            pageEntity!!.extractedText
                                        }

                                        viewModel.saveEditedSinglePage(
                                            page = pageEntity!!,
                                            newBitmap = finalRendered,
                                            newTitle = pageTitle,
                                            newExtractedText = newOcr
                                        ) {
                                            isSaving = false
                                            onNavigateBack()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPdfTextEditMode) pdfEditAccent else accentEmerald),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("btn_editor_done"),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Save",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 2. MAIN DOCUMENT CANVAS AREA
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(canvasBg)
                ) {
                    val displayBitmap = previewFilteredBitmap ?: currentBaseBitmap

                    if (displayBitmap != null) {
                        if (activeTool == ActiveEditorTool.CROP) {
                            QuadCropView(
                                bitmap = displayBitmap,
                                corners = cropCorners,
                                onCornersChanged = { newCorners ->
                                    cropCorners = newCorners
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SinglePageInteractiveCanvas(
                                bitmap = displayBitmap,
                                activeTool = activeTool,
                                textOverlays = textOverlays,
                                selectedTextOverlayId = selectedTextOverlayId,
                                onSelectTextOverlay = { id ->
                                    selectedTextOverlayId = id
                                    selectedSignatureId = null
                                    selectedMosaicId = null
                                },
                                onUpdateTextOverlay = { updated ->
                                    textOverlays = textOverlays.map { if (it.id == updated.id) updated else it }
                                },
                                onDeleteTextOverlay = { id ->
                                    saveSnapshot()
                                    textOverlays = textOverlays.filter { it.id != id }
                                    selectedTextOverlayId = null
                                },
                                onEditTextOverlay = { overlay ->
                                    editingTextOverlay = overlay
                                    showTextEditorDialog = true
                                },
                                signatureOverlays = signatureOverlays,
                                selectedSignatureId = selectedSignatureId,
                                onSelectSignature = { id ->
                                    selectedSignatureId = id
                                    selectedTextOverlayId = null
                                    selectedMosaicId = null
                                },
                                onUpdateSignature = { updated ->
                                    signatureOverlays = signatureOverlays.map { if (it.id == updated.id) updated else it }
                                },
                                onDeleteSignature = { id ->
                                    saveSnapshot()
                                    signatureOverlays = signatureOverlays.filter { it.id != id }
                                    selectedSignatureId = null
                                },
                                mosaicRegions = mosaicRegions,
                                selectedMosaicId = selectedMosaicId,
                                onSelectMosaic = { id ->
                                    selectedMosaicId = id
                                    selectedTextOverlayId = null
                                    selectedSignatureId = null
                                },
                                onUpdateMosaic = { updated ->
                                    mosaicRegions = mosaicRegions.map { if (it.id == updated.id) updated else it }
                                },
                                onDeleteMosaic = { id ->
                                    saveSnapshot()
                                    mosaicRegions = mosaicRegions.filter { it.id != id }
                                    selectedMosaicId = null
                                },
                                brushStrokes = brushStrokes,
                                currentBrushPoints = currentBrushPoints,
                                brushColor = brushColor,
                                brushWidth = brushWidth,
                                brushMode = brushMode,
                                brushOpacity = brushOpacity,
                                onBrushStrokeFinished = { stroke ->
                                    saveSnapshot()
                                    brushStrokes = brushStrokes + stroke
                                    currentBrushPoints = emptyList()
                                },
                                onBrushPointsChanged = { pts ->
                                    currentBrushPoints = pts
                                },
                                eraseStrokes = eraseStrokes,
                                currentErasePoints = currentErasePoints,
                                eraseWidth = eraseWidth,
                                eraseMode = eraseMode,
                                isErasePanZoomMode = isErasePanZoomMode,
                                onEraseStrokeFinished = { stroke ->
                                    saveSnapshot()
                                    eraseStrokes = eraseStrokes + stroke
                                    currentErasePoints = emptyList()
                                },
                                onErasePointsChanged = { pts ->
                                    currentErasePoints = pts
                                },
                                watermarkConfig = watermarkConfig,
                                ocrDocument = if (activeTool == ActiveEditorTool.EDIT_TEXT) ocrDocument else null,
                                textEditGranularity = textEditGranularity,
                                editTextOperations = editTextOperations,
                                onSelectOcrItem = { item ->
                                    selectedOcrItem = item
                                },
                                onCanvasTapped = {
                                    selectedTextOverlayId = null
                                    selectedSignatureId = null
                                    selectedMosaicId = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top-left Delete / Page Badge on the Canvas
                        Surface(
                            shape = CircleShape,
                            color = Color(0x99000000),
                            modifier = Modifier
                                .padding(top = 16.dp, start = 16.dp)
                                .align(Alignment.TopStart)
                                .size(34.dp)
                                .clickable {
                                    showDiscardConfirmDialog = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Page Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 3. SUB-TOOL PANELS (Crop / Filter / Erase / Brush / Text)
                // ==========================================
                AnimatedVisibility(
                    visible = activeTool != ActiveEditorTool.NONE,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        color = Color(0xFF202024),
                        border = BorderStroke(1.dp, surfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (activeTool) {
                            ActiveEditorTool.CROP -> {
                                CropSubToolbar(
                                    onRotateLeft = {
                                        saveSnapshot()
                                        val cur = currentBaseBitmap ?: return@CropSubToolbar
                                        currentBaseBitmap = FileUtils.rotateBitmap(cur, -90f)
                                    },
                                    onRotateRight = {
                                        saveSnapshot()
                                        val cur = currentBaseBitmap ?: return@CropSubToolbar
                                        currentBaseBitmap = FileUtils.rotateBitmap(cur, 90f)
                                    },
                                    onAutoCrop = {
                                        val cur = currentBaseBitmap ?: return@CropSubToolbar
                                        val detected = EdgeDetector.detectDocumentCorners(cur)
                                        cropCorners = detected
                                        Toast.makeText(context, "Document edges auto-detected", Toast.LENGTH_SHORT).show()
                                    },
                                    onSelectAll = {
                                        cropCorners = listOf(
                                            Offset(0f, 0f),
                                            Offset(1f, 0f),
                                            Offset(1f, 1f),
                                            Offset(0f, 1f)
                                        )
                                    },
                                    onCancel = {
                                        activeTool = ActiveEditorTool.NONE
                                    },
                                    onApply = {
                                        saveSnapshot()
                                        val cur = currentBaseBitmap
                                        if (cur != null) {
                                            val cropped = ImageProcessor.perspectiveCrop(cur, cropCorners)
                                            currentBaseBitmap = cropped
                                            cropCorners = listOf(
                                                Offset(0.05f, 0.05f),
                                                Offset(0.95f, 0.05f),
                                                Offset(0.95f, 0.95f),
                                                Offset(0.05f, 0.95f)
                                            )
                                            Toast.makeText(context, "Crop applied", Toast.LENGTH_SHORT).show()
                                        }
                                        activeTool = ActiveEditorTool.NONE
                                    }
                                )
                            }
                            ActiveEditorTool.FILTER -> {
                                FilterSubToolbar(
                                    sourceBitmap = currentBaseBitmap,
                                    currentFilter = activeFilter,
                                    brightness = brightnessValue,
                                    contrast = contrastValue,
                                    onFilterSelected = { filter ->
                                        activeFilter = filter
                                    },
                                    onBrightnessChanged = { b ->
                                        brightnessValue = b
                                    },
                                    onContrastChanged = { c ->
                                        contrastValue = c
                                    },
                                    onResetAdjustments = {
                                        brightnessValue = 0f
                                        contrastValue = 1f
                                    },
                                    onCancel = {
                                        activeFilter = FilterType.ORIGINAL
                                        brightnessValue = 0f
                                        contrastValue = 1f
                                        activeTool = ActiveEditorTool.NONE
                                    },
                                    onApply = {
                                        saveSnapshot()
                                        currentBaseBitmap = previewFilteredBitmap ?: currentBaseBitmap
                                        activeFilter = FilterType.ORIGINAL
                                        brightnessValue = 0f
                                        contrastValue = 1f
                                        Toast.makeText(context, "Filter applied", Toast.LENGTH_SHORT).show()
                                        activeTool = ActiveEditorTool.NONE
                                    }
                                )
                            }
                            ActiveEditorTool.SMART_ERASE -> {
                                SmartEraseSubToolbar(
                                    eraseMode = eraseMode,
                                    onEraseModeChanged = { eraseMode = it },
                                    eraseWidth = eraseWidth,
                                    onWidthChanged = { eraseWidth = it },
                                    isPanZoomMode = isErasePanZoomMode,
                                    onTogglePanZoom = { isErasePanZoomMode = !isErasePanZoomMode },
                                    isAiCleaning = isAiAutoCleaning,
                                    onAiAutoClean = {
                                        val cur = currentBaseBitmap ?: return@SmartEraseSubToolbar
                                        isAiAutoCleaning = true
                                        scope.launch(Dispatchers.Default) {
                                            saveSnapshot()
                                            val cleaned = ImageProcessor.autoCleanDocumentArtifacts(cur)
                                            currentBaseBitmap = cleaned
                                            isAiAutoCleaning = false
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "✨ AI Auto-cleaned document borders & artifacts", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenFullStudio = { showSmartEraseStudio = true },
                                    onUndo = {
                                        if (eraseStrokes.isNotEmpty()) {
                                            eraseStrokes = eraseStrokes.dropLast(1)
                                        }
                                    },
                                    onClearAll = {
                                        saveSnapshot()
                                        eraseStrokes = emptyList()
                                    },
                                    onDone = {
                                        activeTool = ActiveEditorTool.NONE
                                        isErasePanZoomMode = false
                                    }
                                )
                            }
                            ActiveEditorTool.BRUSH -> {
                                BrushSubToolbar(
                                    brushMode = brushMode,
                                    onBrushModeChanged = { brushMode = it },
                                    selectedColor = brushColor,
                                    onColorSelected = { brushColor = it },
                                    brushWidth = brushWidth,
                                    onWidthChanged = { brushWidth = it },
                                    brushOpacity = brushOpacity,
                                    onOpacityChanged = { brushOpacity = it },
                                    onUndo = {
                                        if (brushStrokes.isNotEmpty()) {
                                            brushStrokes = brushStrokes.dropLast(1)
                                        }
                                    },
                                    onClearAll = {
                                        saveSnapshot()
                                        brushStrokes = emptyList()
                                    },
                                    onDone = {
                                        activeTool = ActiveEditorTool.NONE
                                    }
                                )
                            }
                            ActiveEditorTool.EDIT_TEXT -> {
                                // Auto-trigger OCR if not already recognized
                                LaunchedEffect(currentBaseBitmap) {
                                    if (ocrDocument == null && currentBaseBitmap != null && !isOcrDetecting) {
                                        isOcrDetecting = true
                                        try {
                                            val doc = ocrEngine.recognizeText(currentBaseBitmap!!)
                                            ocrDocument = doc
                                        } catch (e: Exception) {
                                            // Handled
                                        } finally {
                                            isOcrDetecting = false
                                        }
                                    }
                                }

                                EditTextSubToolbar(
                                    isDetecting = isOcrDetecting,
                                    ocrDocument = ocrDocument,
                                    granularity = textEditGranularity,
                                    onGranularityChanged = { textEditGranularity = it },
                                    onDetectOcr = {
                                        val cur = currentBaseBitmap ?: return@EditTextSubToolbar
                                        isOcrDetecting = true
                                        scope.launch(Dispatchers.Default) {
                                            try {
                                                val doc = ocrEngine.recognizeText(cur)
                                                ocrDocument = doc
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Detected ${doc.blocks.size} paragraphs / text blocks. Tap any text on the page to edit in place.", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "OCR detection failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            } finally {
                                                isOcrDetecting = false
                                            }
                                        }
                                    },
                                    onViewFullOcrText = {
                                        val cur = currentBaseBitmap ?: return@EditTextSubToolbar
                                        scope.launch(Dispatchers.Default) {
                                            val text = ocrDocument?.fullText?.ifBlank { null }
                                                ?: TextRecognizerHelper.extractText(cur)
                                            fullOcrExtractedText = text
                                            showFullOcrSheet = true
                                        }
                                    },
                                    onAddNewText = {
                                        editingTextOverlay = TextOverlay(
                                            text = "",
                                            x = 0.5f,
                                            y = 0.5f,
                                            fontSizeSp = 18f,
                                            color = Color.Black,
                                            backgroundColor = Color.White
                                        )
                                        showTextEditorDialog = true
                                    },
                                    onAddQuickStamp = { stampText, stampColor ->
                                        saveSnapshot()
                                        val stamp = TextOverlay(
                                            text = stampText,
                                            x = 0.5f,
                                            y = 0.45f,
                                            fontSizeSp = 22f,
                                            color = stampColor,
                                            isBold = true,
                                            isStamp = true,
                                            backgroundColor = Color.White
                                        )
                                        textOverlays = textOverlays + stamp
                                        selectedTextOverlayId = stamp.id
                                        Toast.makeText(context, "Stamp added: $stampText", Toast.LENGTH_SHORT).show()
                                    },
                                    onDone = {
                                        activeTool = ActiveEditorTool.NONE
                                    }
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // ==========================================
                // 4. FIXED BOTTOM PANEL WITH 3 TABS (Images, Markup, Page)
                // ==========================================
                Surface(
                    color = bottomBarBg,
                    border = BorderStroke(1.dp, surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // TAB HEADERS (Images, Markup, Page)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedTab = tab
                                            activeTool = ActiveEditorTool.NONE
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                        .testTag("tab_${tab.name.lowercase()}")
                                ) {
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) accentEmerald else inactiveGray,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(3.dp)
                                            .width(28.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(if (isSelected) accentEmerald else Color.Transparent)
                                    )
                                }
                            }
                        }

                        Divider(color = Color(0xFF26262A), thickness = 1.dp)

                        // TAB TOOLBAR CONTENT
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            when (selectedTab) {
                                EditorTab.IMAGES -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EditorToolbarItem(
                                            label = "Crop",
                                            icon = Icons.Default.Crop,
                                            isActive = activeTool == ActiveEditorTool.CROP,
                                            testTag = "btn_tool_crop",
                                            onClick = {
                                                activeTool = if (activeTool == ActiveEditorTool.CROP) ActiveEditorTool.NONE else ActiveEditorTool.CROP
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Filter",
                                            icon = Icons.Default.AutoAwesome,
                                            isActive = activeTool == ActiveEditorTool.FILTER,
                                            testTag = "btn_tool_filter",
                                            onClick = {
                                                activeTool = if (activeTool == ActiveEditorTool.FILTER) ActiveEditorTool.NONE else ActiveEditorTool.FILTER
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Edit Text",
                                            icon = Icons.Default.TextFields,
                                            isActive = activeTool == ActiveEditorTool.EDIT_TEXT,
                                            testTag = "btn_tool_edit_text",
                                            onClick = {
                                                val docId = pageEntity?.documentId
                                                if (docId != null && docId > 0 && onNavigateToDocumentEditText != null) {
                                                    onNavigateToDocumentEditText(docId)
                                                } else {
                                                    // Keep the existing in-page editor as a safe fallback.
                                                    activeTool = ActiveEditorTool.EDIT_TEXT
                                                }
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Smart Erase",
                                            icon = Icons.Default.AutoFixHigh,
                                            isActive = activeTool == ActiveEditorTool.SMART_ERASE || showSmartEraseStudio,
                                            testTag = "btn_tool_smart_erase",
                                            onClick = {
                                                showSmartEraseStudio = true
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Retake",
                                            icon = Icons.Default.CameraAlt,
                                            isActive = false,
                                            testTag = "btn_tool_retake",
                                            onClick = { showRetakeSourceDialog = true }
                                        )

                                        EditorToolbarItem(
                                            label = "Save to Gallery",
                                            icon = Icons.Default.Download,
                                            isActive = false,
                                            testTag = "btn_tool_save_gallery",
                                            onClick = {
                                                scope.launch(Dispatchers.Default) {
                                                    val rendered = renderFinalPageBitmap(
                                                        base = previewFilteredBitmap ?: currentBaseBitmap!!,
                                                        eraseStrokes = eraseStrokes,
                                                        mosaicRegions = mosaicRegions,
                                                        brushStrokes = brushStrokes,
                                                        textOverlays = textOverlays,
                                                        signatureOverlays = signatureOverlays,
                                                        watermarkConfig = watermarkConfig,
                                                        editTextOperations = editTextOperations
                                                    )
                                                    val title = pageTitle.ifBlank { "Scanned_Page" }
                                                    val uri = FileUtils.saveBitmapToGallery(context, rendered, title)
                                                    withContext(Dispatchers.Main) {
                                                        if (uri != null) {
                                                            Toast.makeText(context, "Page saved to Gallery (Pictures/DocScanner)", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Failed to save to Gallery", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                EditorTab.MARKUP -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EditorToolbarItem(
                                            label = "Sign",
                                            icon = Icons.Default.Draw,
                                            isActive = false,
                                            testTag = "btn_tool_markup_sign",
                                            onClick = { showSignatureDialog = true }
                                        )

                                        EditorToolbarItem(
                                            label = "Add Text",
                                            icon = Icons.Default.TextFields,
                                            isActive = false,
                                            testTag = "btn_tool_markup_add_text",
                                            onClick = {
                                                editingTextOverlay = TextOverlay(
                                                    text = "",
                                                    x = 0.5f,
                                                    y = 0.5f,
                                                    fontSizeSp = 20f,
                                                    color = Color.Black,
                                                    backgroundColor = Color.Transparent
                                                )
                                                showTextEditorDialog = true
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Brush",
                                            icon = Icons.Default.Brush,
                                            isActive = activeTool == ActiveEditorTool.BRUSH,
                                            testTag = "btn_tool_markup_brush",
                                            onClick = {
                                                activeTool = if (activeTool == ActiveEditorTool.BRUSH) ActiveEditorTool.NONE else ActiveEditorTool.BRUSH
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Mosaic",
                                            icon = Icons.Default.GridOn,
                                            isActive = false,
                                            testTag = "btn_tool_markup_mosaic",
                                            onClick = {
                                                saveSnapshot()
                                                val newMosaic = MosaicRegion(
                                                    left = 0.3f,
                                                    top = 0.4f,
                                                    width = 0.4f,
                                                    height = 0.15f
                                                )
                                                mosaicRegions = mosaicRegions + newMosaic
                                                selectedMosaicId = newMosaic.id
                                                Toast.makeText(context, "Mosaic box added. Drag or resize to redact.", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        EditorToolbarItem(
                                            label = "Add Watermark",
                                            icon = Icons.Default.Security,
                                            isActive = watermarkConfig.text.isNotBlank(),
                                            testTag = "btn_tool_markup_watermark",
                                            onClick = { showWatermarkDialog = true }
                                        )

                                        EditorToolbarItem(
                                            label = "Page Title",
                                            icon = Icons.Default.Title,
                                            isActive = false,
                                            testTag = "btn_tool_markup_page_title",
                                            onClick = { showPageTitleDialog = true }
                                        )
                                    }
                                }

                                EditorTab.PAGE -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EditorToolbarItem(
                                            label = "Page Title",
                                            icon = Icons.Default.Title,
                                            isActive = false,
                                            testTag = "btn_tool_page_title",
                                            onClick = { showPageTitleDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 5. DIALOGS & BOTTOM SHEETS
    // ==========================================

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Discard Edits?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes on this page. Are you sure you want to discard them?", color = Color(0xFFD1D1D6)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Keep Editing", color = Color.White)
                }
            },
            containerColor = Color(0xFF242428)
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureSaved = { sigBmp ->
                showSignatureDialog = false
                saveSnapshot()
                val newSig = SignatureOverlay(
                    bitmap = sigBmp,
                    x = 0.5f,
                    y = 0.75f,
                    widthRatio = 0.35f
                )
                signatureOverlays = signatureOverlays + newSig
                selectedSignatureId = newSig.id
                Toast.makeText(context, "Signature added. Drag to place.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showWatermarkDialog) {
        WatermarkDialog(
            initialText = watermarkConfig.text.ifBlank { "CONFIDENTIAL" },
            onDismiss = { showWatermarkDialog = false },
            onApplyWatermark = { text, opacity, colorLong ->
                showWatermarkDialog = false
                saveSnapshot()
                watermarkConfig = WatermarkConfig(
                    text = text,
                    opacity = opacity,
                    colorLong = colorLong,
                    isDiagonal = true
                )
                Toast.makeText(context, "Watermark applied", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTextEditorDialog && editingTextOverlay != null) {
        TextEditorDialog(
            initialOverlay = editingTextOverlay!!,
            onDismiss = {
                showTextEditorDialog = false
                editingTextOverlay = null
            },
            onSave = { updated ->
                showTextEditorDialog = false
                saveSnapshot()
                val existing = textOverlays.find { it.id == updated.id }
                textOverlays = if (existing != null) {
                    textOverlays.map { if (it.id == updated.id) updated else it }
                } else {
                    textOverlays + updated
                }
                selectedTextOverlayId = updated.id
                editingTextOverlay = null
                Toast.makeText(context, "Text updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (selectedOcrItem != null) {
        val sTargetId: String
        val sOrigText: String
        val sNormRect: RectF
        val sColor: Color
        val sSize: Float
        val sBold: Boolean
        val sItalic: Boolean
        val sUnderline: Boolean
        val sFamily: String
        val sAlign: String

        when (val item = selectedOcrItem!!) {
            is OcrTextBlock -> {
                val existingOp = editTextOperations.find { it.targetId == item.id }
                sTargetId = item.id
                sOrigText = item.text
                sNormRect = item.normalizedRect
                sColor = existingOp?.textColor ?: item.estimatedTextColor
                sSize = existingOp?.fontSizeSp ?: item.estimatedTextSizeSp
                sBold = existingOp?.isBold ?: item.estimatedFontStyle.isBold
                sItalic = existingOp?.isItalic ?: item.estimatedFontStyle.isItalic
                sUnderline = existingOp?.isUnderline ?: item.estimatedFontStyle.isUnderline
                sFamily = existingOp?.fontFamilyType ?: item.estimatedFontStyle.fontFamilyType
                sAlign = existingOp?.alignment ?: autoTextAlignment(item.normalizedRect)
            }
            is OcrTextLine -> {
                val existingOp = editTextOperations.find { it.targetId == item.id }
                sTargetId = item.id
                sOrigText = item.text
                sNormRect = item.normalizedRect
                sColor = existingOp?.textColor ?: item.estimatedTextColor
                sSize = existingOp?.fontSizeSp ?: item.estimatedTextSizeSp
                sBold = existingOp?.isBold ?: item.estimatedFontStyle.isBold
                sItalic = existingOp?.isItalic ?: item.estimatedFontStyle.isItalic
                sUnderline = existingOp?.isUnderline ?: item.estimatedFontStyle.isUnderline
                sFamily = existingOp?.fontFamilyType ?: item.estimatedFontStyle.fontFamilyType
                sAlign = existingOp?.alignment ?: autoTextAlignment(item.normalizedRect)
            }
            is OcrTextElement -> {
                val existingOp = editTextOperations.find { it.targetId == item.id }
                val autoStyle = currentBaseBitmap?.let { bmp ->
                    TextStyleEstimator.estimate(
                        bitmap = bmp,
                        pixelRect = item.pixelRect,
                        recognizedText = item.text
                    )
                }
                sTargetId = item.id
                sOrigText = item.text
                sNormRect = item.normalizedRect
                sColor = existingOp?.textColor ?: autoStyle?.textColor ?: Color(0xFF1E293B)
                sSize = existingOp?.fontSizeSp ?: autoStyle?.fontSizeSp ?: 14f
                sBold = existingOp?.isBold ?: autoStyle?.fontStyle?.isBold ?: false
                sItalic = existingOp?.isItalic ?: autoStyle?.fontStyle?.isItalic ?: false
                sUnderline = existingOp?.isUnderline ?: autoStyle?.fontStyle?.isUnderline ?: false
                sFamily = existingOp?.fontFamilyType ?: autoStyle?.fontStyle?.fontFamilyType ?: "DEFAULT"
                sAlign = existingOp?.alignment ?: "LEFT"
            }
            else -> {
                sTargetId = ""
                sOrigText = ""
                sNormRect = RectF()
                sColor = Color.Black
                sSize = 14f
                sBold = false
                sItalic = false
                sUnderline = false
                sFamily = "DEFAULT"
                sAlign = "LEFT"
            }
        }

        val existingOp = editTextOperations.find { it.targetId == sTargetId }
        val currentTextValue = existingOp?.newText ?: sOrigText
        val isPreviouslyEdited = existingOp != null

        InPlaceTextEditorDialog(
            initialText = currentTextValue,
            initialTextColor = sColor,
            initialFontSizeSp = sSize,
            initialIsBold = sBold,
            initialIsItalic = sItalic,
            initialIsUnderline = sUnderline,
            initialFontFamily = sFamily,
            initialAlignment = sAlign,
            isPreviouslyEdited = isPreviouslyEdited,
            onDismiss = { selectedOcrItem = null },
            onApplyEdit = { newText, textColor, fontSizeSp, isBold, isItalic, isUnderline, fontFamily, alignment ->
                val cur = currentBaseBitmap
                if (cur != null) {
                    saveSnapshot()
                    scope.launch(Dispatchers.Default) {
                        // 1. Reconstruct clean background texture (erases old text)
                        val inpainted = DocumentTextInpainter.inpaintRegion(cur, sNormRect)

                        // 2. Build the edit operation
                        val op = EditTextOperation(
                            targetId = sTargetId,
                            originalText = sOrigText,
                            newText = newText,
                            originalNormalizedRect = sNormRect,
                            targetNormalizedRect = sNormRect,
                            textColor = textColor,
                            fontSizeSp = fontSizeSp,
                            isBold = isBold,
                            isItalic = isItalic,
                            isUnderline = isUnderline,
                            fontFamilyType = fontFamily,
                            alignment = alignment,
                            isDeleted = false
                        )

                        // 3. Render new replaced text directly onto the document bitmap!
                        val renderedBitmap = DocumentTextRenderer.renderOperationOnBitmap(inpainted, op)

                        withContext(Dispatchers.Main) {
                            currentBaseBitmap = renderedBitmap
                            editTextOperations = editTextOperations.filter { it.targetId != sTargetId } + op
                            selectedOcrItem = null
                            Toast.makeText(context, "Text replaced in-place", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onEraseFromDocument = {
                val cur = currentBaseBitmap
                if (cur != null) {
                    saveSnapshot()
                    scope.launch(Dispatchers.Default) {
                        val inpainted = DocumentTextInpainter.inpaintRegion(cur, sNormRect)
                        withContext(Dispatchers.Main) {
                            currentBaseBitmap = inpainted

                            val op = EditTextOperation(
                                targetId = sTargetId,
                                originalText = sOrigText,
                                newText = "",
                                originalNormalizedRect = sNormRect,
                                targetNormalizedRect = sNormRect,
                                isDeleted = true
                            )
                            editTextOperations = editTextOperations.filter { it.targetId != sTargetId } + op
                            selectedOcrItem = null
                            Toast.makeText(context, "Text erased from document", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onResetToOriginal = {
                saveSnapshot()
                editTextOperations = editTextOperations.filter { it.targetId != sTargetId }
                selectedOcrItem = null
                Toast.makeText(context, "Reset to original text", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showFullOcrSheet) {
        FullOcrDocumentSheet(
            extractedText = fullOcrExtractedText,
            onDismiss = { showFullOcrSheet = false },
            onApplyToPage = { newText ->
                showFullOcrSheet = false
                editingTextOverlay = TextOverlay(
                    text = newText,
                    x = 0.5f,
                    y = 0.5f,
                    fontSizeSp = 16f,
                    backgroundColor = Color.White
                )
                showTextEditorDialog = true
            }
        )
    }

    if (showPageTitleDialog) {
        var tempTitle by remember { mutableStateOf(pageTitle) }
        AlertDialog(
            onDismissRequest = { showPageTitleDialog = false },
            title = { Text("Page Title / Note", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a descriptive title or note for this page:", color = Color(0xFFD1D1D6), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentEmerald,
                            unfocusedBorderColor = Color(0xFF44444A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveSnapshot()
                        pageTitle = tempTitle
                        showPageTitleDialog = false
                        Toast.makeText(context, "Page title updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentEmerald)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageTitleDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF242428)
        )
    }

    if (showPdfOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPdfOptionsSheet = false },
            containerColor = Color(0xFF202024),
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "PDF Operations",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = Color(0xFF2A2A30),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPdfOptionsSheet = false
                            scope.launch {
                                val rendered = renderFinalPageBitmap(
                                    base = previewFilteredBitmap ?: currentBaseBitmap!!,
                                    eraseStrokes = eraseStrokes,
                                    mosaicRegions = mosaicRegions,
                                    brushStrokes = brushStrokes,
                                    textOverlays = textOverlays,
                                    signatureOverlays = signatureOverlays,
                                    watermarkConfig = watermarkConfig,
                                    editTextOperations = editTextOperations
                                )
                                val tempFile = File(FileUtils.getTempDir(context), "PAGE_${System.currentTimeMillis()}.jpg")
                                FileUtils.saveBitmapToFile(rendered, tempFile)
                                val singlePage = pageEntity!!.copy(processedImagePath = tempFile.absolutePath)
                                val pdfFile = PdfExporter.generatePdf(context, pageTitle.ifBlank { "Page_Export" }, listOf(singlePage))
                                if (pdfFile != null) {
                                    PdfExporter.sharePdf(context, pdfFile)
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = accentEmerald)
                        Column {
                            Text("Export This Page as PDF", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Generates a single page PDF file to share or print", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    color = Color(0xFF2A2A30),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPdfOptionsSheet = false
                            viewModel.activeDocument.value?.let { doc ->
                                viewModel.sharePdfDirect(doc)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF38BDF8))
                        Column {
                            Text("Export Full Document PDF", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Exports all pages of the document", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showRetakeSourceDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeSourceDialog = false },
            title = { Text("Retake Page Photo", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select where to capture or select the replacement photo:", color = Color(0xFFD1D1D6), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFF2C2C32),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRetakeSourceDialog = false
                                val tempFile = File(FileUtils.getTempDir(context), "RETAKE_${System.currentTimeMillis()}.jpg")
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    tempFile
                                )
                                retakeTempUri = uri
                                takePhotoLauncher.launch(uri)
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = accentEmerald)
                            Text("Camera Capture", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }

                    Surface(
                        color = Color(0xFF2C2C32),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRetakeSourceDialog = false
                                pickGalleryLauncher.launch("image/*")
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF38BDF8))
                            Text("Pick from Gallery", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRetakeSourceDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF242428)
        )
    }

    if (showSmartEraseStudio && currentBaseBitmap != null) {
        SmartEraseScreen(
            sourceBitmap = currentBaseBitmap!!,
            onNavigateBack = { showSmartEraseStudio = false },
            onApplyResult = { reconstructedBitmap ->
                saveSnapshot()
                currentBaseBitmap = reconstructedBitmap
                showSmartEraseStudio = false
                Toast.makeText(context, "AI Smart Erase applied successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ==========================================
// 6. TOOLBAR ITEM & SUB-TOOLBARS
// ==========================================

@Composable
fun EditorToolbarItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val accentEmerald = Color(0xFF2DBA8D)
    val inactiveText = ThemeManager.themeColor(Color(0xFFD1D1D6), Color(0xFF334155))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isActive) accentEmerald.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) accentEmerald else ThemeManager.themeColor(Color.White, Color(0xFF334155)),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isActive) accentEmerald else inactiveText,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CropSubToolbar(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onAutoCrop: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF5252))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRotateLeft) {
                Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White)
            }
            IconButton(onClick = onRotateRight) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C2C32),
                modifier = Modifier.clickable(onClick = onAutoCrop)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF2DBA8D), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto", color = Color.White, fontSize = 12.sp)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C2C32),
                modifier = Modifier.clickable(onClick = onSelectAll)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CropFree, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("All", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF2DBA8D),
            modifier = Modifier
                .height(38.dp)
                .clickable(onClick = onApply)
                .testTag("btn_crop_apply")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class FilterPresetData(
    val type: FilterType,
    val label: String,
    val badge: String? = null,
    val badgeColor: Color = Color(0xFFF59E0B)
)

@Composable
fun FilterSubToolbar(
    sourceBitmap: Bitmap?,
    currentFilter: FilterType,
    brightness: Float,
    contrast: Float,
    onFilterSelected: (FilterType) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onResetAdjustments: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    var isAdjustTabOpen by remember { mutableStateOf(false) }

    // Downsample base image to ~120px for high-performance instant filter previews
    val thumbBmp = remember(sourceBitmap) {
        sourceBitmap?.let { src ->
            val maxDim = 120
            val scale = (maxDim.toFloat() / Math.max(src.width, src.height)).coerceAtMost(1f)
            val tw = (src.width * scale).toInt().coerceAtLeast(1)
            val th = (src.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, tw, th, true)
        }
    }

    val filterThumbnails = remember(thumbBmp) {
        val map = mutableMapOf<FilterType, Bitmap?>()
        if (thumbBmp != null) {
            FilterType.values().forEach { fType ->
                map[fType] = ImageProcessor.applyFilter(thumbBmp, fType, 0f, 1f)
            }
        }
        map
    }

    val filterPresets = listOf(
        FilterPresetData(FilterType.MAGIC_COLOR, "Magic Color", "✨ VIP", Color(0xFFF59E0B)),
        FilterPresetData(FilterType.AUTO, "Auto", "⚡ Smart", Color(0xFF38BDF8)),
        FilterPresetData(FilterType.CLEAR, "Clear", "💎 Sharp", Color(0xFFA855F7)),
        FilterPresetData(FilterType.DOCUMENT, "Document", null),
        FilterPresetData(FilterType.ORIGINAL, "Original", null),
        FilterPresetData(FilterType.BW, "B&W", "📄 Ink", Color(0xFF10B981)),
        FilterPresetData(FilterType.GRAYSCALE, "Grayscale", null),
        FilterPresetData(FilterType.LIGHTEN, "Lighten", "💡 Boost", Color(0xFFEAB308))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181B))
            .padding(top = 10.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
    ) {
        // Mode Header & Quick Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (!isAdjustTabOpen) Color(0xFF00C48C).copy(alpha = 0.2f) else Color(0xFF27272A),
                    border = BorderStroke(1.dp, if (!isAdjustTabOpen) Color(0xFF00C48C) else Color(0xFF3F3F46)),
                    modifier = Modifier.clickable { isAdjustTabOpen = false }
                ) {
                    Text(
                        text = "Filters",
                        color = if (!isAdjustTabOpen) Color(0xFF00C48C) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isAdjustTabOpen) Color(0xFF00C48C).copy(alpha = 0.2f) else Color(0xFF27272A),
                    border = BorderStroke(1.dp, if (isAdjustTabOpen) Color(0xFF00C48C) else Color(0xFF3F3F46)),
                    modifier = Modifier.clickable { isAdjustTabOpen = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Adjust Tone",
                            color = if (isAdjustTabOpen) Color(0xFF00C48C) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (brightness != 0f || contrast != 1f) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00C48C))
                            )
                        }
                    }
                }
            }

            if (isAdjustTabOpen && (brightness != 0f || contrast != 1f)) {
                Text(
                    text = "↺ Reset",
                    color = Color(0xFFF59E0B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onResetAdjustments() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (!isAdjustTabOpen) {
            // Horizontal CamScanner Filter Cards
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(filterPresets) { preset ->
                    val isSelected = currentFilter == preset.type
                    val previewBmp = filterThumbnails[preset.type]

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onFilterSelected(preset.type) }
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF27272A))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF00C48C) else Color(0xFF3F3F46),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            if (previewBmp != null) {
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = preset.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.label.take(1),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            // VIP / Smart Badge
                            if (preset.badge != null) {
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 6.dp),
                                    color = preset.badgeColor,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = preset.badge,
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // Selection Checkmark
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF00C48C),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp)
                                        .size(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = preset.label,
                            color = if (isSelected) Color(0xFF00C48C) else Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Fine-Tuning Adjustments Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Brightness",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = brightness,
                        onValueChange = onBrightnessChanged,
                        valueRange = -0.5f..0.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00C48C),
                            activeTrackColor = Color(0xFF00C48C),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${if (brightness > 0) "+" else ""}${(brightness * 100).toInt()}%",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp)
                    )
                }

                // Contrast Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Contrast",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = contrast,
                        onValueChange = onContrastChanged,
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00C48C),
                            activeTrackColor = Color(0xFF00C48C),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${((contrast * 10).toInt() / 10f)}x",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons: Cancel and Apply
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF27272A),
                border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                modifier = Modifier.clickable { onCancel() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", color = Color(0xFFE2E8F0), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF00C48C),
                modifier = Modifier.clickable { onApply() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Apply Filter", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SmartEraseSubToolbar(
    eraseMode: EraseMode,
    onEraseModeChanged: (EraseMode) -> Unit,
    eraseWidth: Float,
    onWidthChanged: (Float) -> Unit,
    isPanZoomMode: Boolean,
    onTogglePanZoom: () -> Unit,
    isAiCleaning: Boolean,
    onAiAutoClean: () -> Unit,
    onOpenFullStudio: () -> Unit = {},
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Mode switch row + AI Auto Clean button + Pan/Zoom Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
            ) {
                // Full Studio button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2DBA8D),
                    modifier = Modifier.clickable { onOpenFullStudio() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Text("AI Studio", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // AI Inpaint Pill (CamScanner local background sampling)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (eraseMode == EraseMode.AI_INPAINT && !isPanZoomMode) Color(0xFF2DBA8D) else Color(0xFF2C2C32),
                    border = BorderStroke(1.dp, if (eraseMode == EraseMode.AI_INPAINT && !isPanZoomMode) Color(0xFF2DBA8D) else Color(0xFF44444A)),
                    modifier = Modifier.clickable { 
                        if (isPanZoomMode) onTogglePanZoom()
                        onEraseModeChanged(EraseMode.AI_INPAINT) 
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("AI Inpaint", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Whiteout Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (eraseMode == EraseMode.WHITEOUT && !isPanZoomMode) Color(0xFF2DBA8D) else Color(0xFF2C2C32),
                    border = BorderStroke(1.dp, if (eraseMode == EraseMode.WHITEOUT && !isPanZoomMode) Color(0xFF2DBA8D) else Color(0xFF44444A)),
                    modifier = Modifier.clickable { 
                        if (isPanZoomMode) onTogglePanZoom()
                        onEraseModeChanged(EraseMode.WHITEOUT) 
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Whiteout", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Pan / Zoom Toggle (CamScanner move canvas mode)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPanZoomMode) Color(0xFF3B82F6) else Color(0xFF2C2C32),
                    border = BorderStroke(1.dp, if (isPanZoomMode) Color(0xFF3B82F6) else Color(0xFF44444A)),
                    modifier = Modifier.clickable { onTogglePanZoom() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Pan / Zoom", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // AI Auto Clean Document Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.clickable(enabled = !isAiCleaning, onClick = onAiAutoClean)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isAiCleaning) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        }
                        Text("AI Clean Page", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo Erase", tint = Color.White)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2DBA8D),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable(onClick = onDone)
                        .testTag("btn_erase_done")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Eraser Width Slider Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Size", color = Color(0xFF9E9E9E), fontSize = 11.sp)
            Slider(
                value = eraseWidth,
                onValueChange = onWidthChanged,
                valueRange = 10f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2DBA8D),
                    activeTrackColor = Color(0xFF2DBA8D),
                    inactiveTrackColor = Color(0xFF44444A)
                ),
                modifier = Modifier.weight(1f)
            )
            // Live size indicator dot
            Box(
                modifier = Modifier
                    .size((eraseWidth * 0.35f).coerceIn(6f, 24f).dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFF2DBA8D), CircleShape)
            )
            TextButton(onClick = onClearAll) {
                Text("Clear", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BrushSubToolbar(
    brushMode: BrushMode,
    onBrushModeChanged: (BrushMode) -> Unit,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    brushWidth: Float,
    onWidthChanged: (Float) -> Unit,
    brushOpacity: Float,
    onOpacityChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    val colorPalette = listOf(
        Color(0xFFEF4444), // Red
        Color(0xFF2563EB), // Blue
        Color(0xFF059669), // Emerald
        Color(0xFFFBBF24), // Yellow
        Color(0xFF9333EA), // Purple
        Color.Black,
        Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Row 1: Brush Modes (Pen, Highlighter, Line, Arrow, Rectangle, Circle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                BrushMode.entries.forEach { mode ->
                    val isSelected = brushMode == mode
                    val icon = when (mode) {
                        BrushMode.INK_PEN -> Icons.Default.Edit
                        BrushMode.HIGHLIGHTER -> Icons.Default.Highlight
                        BrushMode.LINE -> Icons.Default.HorizontalSplit
                        BrushMode.ARROW -> Icons.Default.ArrowForward
                        BrushMode.RECTANGLE -> Icons.Default.CropSquare
                        BrushMode.CIRCLE -> Icons.Default.RadioButtonUnchecked
                    }
                    val label = when (mode) {
                        BrushMode.INK_PEN -> "Pen"
                        BrushMode.HIGHLIGHTER -> "Highlighter"
                        BrushMode.LINE -> "Line"
                        BrushMode.ARROW -> "Arrow"
                        BrushMode.RECTANGLE -> "Box"
                        BrushMode.CIRCLE -> "Circle"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2DBA8D) else Color(0xFF2C2C32),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF2DBA8D) else Color(0xFF44444A)),
                        modifier = Modifier.clickable { onBrushModeChanged(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2DBA8D),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable(onClick = onDone)
                        .testTag("btn_brush_done")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Color Palette + Opacity & Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                colorPalette.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (selectedColor == c) 2.dp else 1.dp,
                                color = if (selectedColor == c) Color(0xFF2DBA8D) else Color(0xFF44444A),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(c) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text("Size", color = Color(0xFF9E9E9E), fontSize = 10.sp)
                Slider(
                    value = brushWidth,
                    onValueChange = onWidthChanged,
                    valueRange = 2f..32f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2DBA8D),
                        activeTrackColor = Color(0xFF2DBA8D),
                        inactiveTrackColor = Color(0xFF44444A)
                    ),
                    modifier = Modifier.width(90.dp)
                )

                Text("Alpha", color = Color(0xFF9E9E9E), fontSize = 10.sp)
                Slider(
                    value = brushOpacity,
                    onValueChange = onOpacityChanged,
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2DBA8D),
                        activeTrackColor = Color(0xFF2DBA8D),
                        inactiveTrackColor = Color(0xFF44444A)
                    ),
                    modifier = Modifier.width(80.dp)
                )

                TextButton(onClick = onClearAll) {
                    Text("Clear", color = Color(0xFFFF5252), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun EditTextSubToolbar(
    isDetecting: Boolean,
    ocrDocument: OcrDocument?,
    granularity: TextEditGranularity,
    onGranularityChanged: (TextEditGranularity) -> Unit,
    onDetectOcr: () -> Unit,
    onViewFullOcrText: () -> Unit,
    onAddNewText: () -> Unit,
    onAddQuickStamp: (String, Color) -> Unit,
    onDone: () -> Unit
) {
    var showStampDropdown by remember { mutableStateOf(false) }

    val stampPresets = listOf(
        Pair("PAID", Color(0xFF16A34A)),
        Pair("APPROVED", Color(0xFF2563EB)),
        Pair("CONFIDENTIAL", Color(0xFFDC2626)),
        Pair("RECEIVED", Color(0xFF059669)),
        Pair("ORIGINAL", Color(0xFF4F46E5)),
        Pair("COPY", Color(0xFF475569)),
        Pair("VOID", Color(0xFFB91C1C)),
        Pair("VERIFIED", Color(0xFF0284C7))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
            ) {
                // OCR Text Detect
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.clickable(onClick = onDetectOcr)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isDetecting) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (ocrDocument != null) "Re-scan Page" else "Scan Text",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Granularity Selectors (Block, Line, Word)
                Row(
                    modifier = Modifier
                        .background(Color(0xFF26262B), RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        TextEditGranularity.BLOCK to "Block",
                        TextEditGranularity.LINE to "Line",
                        TextEditGranularity.WORD to "Word"
                    ).forEach { (gran, label) ->
                        val isSelected = granularity == gran
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2DBA8D) else Color.Transparent,
                            modifier = Modifier.clickable { onGranularityChanged(gran) }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF9E9E9E),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // View OCR Document Sheet
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2C2C32),
                    border = BorderStroke(1.dp, Color(0xFF44444A)),
                    modifier = Modifier.clickable(onClick = onViewFullOcrText)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Doc Text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Add New Text
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2DBA8D),
                    modifier = Modifier.clickable(onClick = onAddNewText)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick Stamps Toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (showStampDropdown) Color(0xFF3B82F6) else Color(0xFF2C2C32),
                    border = BorderStroke(1.dp, if (showStampDropdown) Color(0xFF3B82F6) else Color(0xFF44444A)),
                    modifier = Modifier.clickable { showStampDropdown = !showStampDropdown }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stamps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            IconButton(onClick = onDone) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color(0xFF2DBA8D))
            }
        }

        // Quick Stamps selector bar (CamScanner Style)
        AnimatedVisibility(visible = showStampDropdown) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Text("CAMSCANNER OFFICIAL STAMPS", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    stampPresets.forEach { (stamp, col) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, col),
                            modifier = Modifier.clickable {
                                onAddQuickStamp(stamp, col)
                                showStampDropdown = false
                            }
                        ) {
                            Text(
                                text = stamp,
                                color = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// FULL DOCUMENT OCR EXTRACTION BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullOcrDocumentSheet(
    extractedText: String,
    onDismiss: () -> Unit,
    onApplyToPage: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var textContent by remember { mutableStateOf(extractedText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF2DBA8D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Document OCR Text",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(textContent))
                        Toast.makeText(context, "Full text copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2DBA8D),
                    unfocusedBorderColor = Color(0xFF48484A),
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onApplyToPage(textContent) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DBA8D))
                ) {
                    Text("Insert Text into Document", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// 7. RICH TEXT OVERLAY EDITING DIALOG WITH GEMINI AI
// ==========================================

@Composable
fun TextEditorDialog(
    initialOverlay: TextOverlay,
    onDismiss: () -> Unit,
    onSave: (TextOverlay) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initialOverlay.text) }
    var fontSize by remember { mutableFloatStateOf(initialOverlay.fontSizeSp) }
    var selectedColor by remember { mutableStateOf(initialOverlay.color) }
    var isBold by remember { mutableStateOf(initialOverlay.isBold) }
    var isItalic by remember { mutableStateOf(initialOverlay.isItalic) }
    var isUnderline by remember { mutableStateOf(initialOverlay.isUnderline) }
    var fontFamilyType by remember { mutableStateOf(initialOverlay.fontFamilyType) }
    var isStamp by remember { mutableStateOf(initialOverlay.isStamp) }
    var hasWhiteBg by remember { mutableStateOf(initialOverlay.backgroundColor != Color.Transparent) }

    // AI Generation states
    var isAiGenerating by remember { mutableStateOf(false) }
    var aiCustomPrompt by remember { mutableStateOf("") }
    var showAiPromptField by remember { mutableStateOf(false) }

    val colors = listOf(
        Color.Black,
        Color.White,
        Color(0xFFDC2626), // Red
        Color(0xFF2563EB), // Blue
        Color(0xFF059669), // Emerald
        Color(0xFFD97706), // Amber
        Color(0xFF7C3AED)  // Purple
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E24),
            border = BorderStroke(1.dp, Color(0xFF3E3E48)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialOverlay.text.isBlank()) "Add Text Overlay" else "Edit Text",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // AI Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Text("Gemini AI Assisted", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text Input Area
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type document text or let AI generate...", color = Color(0xFF88888E)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2DBA8D),
                        unfocusedBorderColor = Color(0xFF44444A)
                    ),
                    maxLines = 4
                )

                // AI Progress bar
                if (isAiGenerating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                        Text("Gemini AI is crafting document text...", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI SMART GENERATION PILLS
                Text("✨ AI SMART TEXT ASSISTANT", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            isAiGenerating = true
                            coroutineScope.launch {
                                val gen = AiOrchestrator.generateAiDocumentText(
                                    type = "paid",
                                    prompt = "Write an official invoice paid note with date ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
                                )
                                text = gen
                                isAiGenerating = false
                            }
                        }
                    ) {
                        Text("🪄 Paid Note", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            isAiGenerating = true
                            coroutineScope.launch {
                                val gen = AiOrchestrator.generateAiDocumentText(
                                    type = "approved",
                                    prompt = "Write a formal document verified & approved statement"
                                )
                                text = gen
                                isAiGenerating = false
                            }
                        }
                    ) {
                        Text("🪄 Official Approval", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            if (text.isNotBlank()) {
                                isAiGenerating = true
                                coroutineScope.launch {
                                    val gen = AiOrchestrator.rephraseTextAi(text, "Make this sound formal, professional and concise for an official certificate/document")
                                    text = gen
                                    isAiGenerating = false
                                }
                            }
                        }
                    ) {
                        Text("🪄 Make Formal", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            if (text.isNotBlank()) {
                                isAiGenerating = true
                                coroutineScope.launch {
                                    val gen = AiOrchestrator.rephraseTextAi(text, "Translate to Bengali accurately for legal/official document context")
                                    text = gen
                                    isAiGenerating = false
                                }
                            }
                        }
                    ) {
                        Text("🌐 to Bengali", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            if (text.isNotBlank()) {
                                isAiGenerating = true
                                coroutineScope.launch {
                                    val gen = AiOrchestrator.rephraseTextAi(text, "Translate to English accurately for official paperwork")
                                    text = gen
                                    isAiGenerating = false
                                }
                            }
                        }
                    ) {
                        Text("🌐 to English", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable { showAiPromptField = !showAiPromptField }
                    ) {
                        Text("🪄 Custom AI Prompt", color = Color(0xFF38BDF8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                // Custom AI Prompt Field
                AnimatedVisibility(visible = showAiPromptField) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = aiCustomPrompt,
                                onValueChange = { aiCustomPrompt = it },
                                placeholder = { Text("e.g. Write a legal receipt disclaimer", color = Color(0xFF88888E), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF44444A)
                                ),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (aiCustomPrompt.isNotBlank()) {
                                        isAiGenerating = true
                                        coroutineScope.launch {
                                            val gen = AiOrchestrator.generateAiDocumentText(
                                                type = "custom",
                                                prompt = aiCustomPrompt
                                            )
                                            text = gen
                                            isAiGenerating = false
                                            showAiPromptField = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                enabled = !isAiGenerating
                            ) {
                                Text("Generate", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Font Family Selector
                Text("FONT FAMILY", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val fontOptions = listOf(
                        Pair("Default", "DEFAULT"),
                        Pair("বাংলা", "BENGALI"),
                        Pair("Serif", "SERIF"),
                        Pair("Monospace", "MONO"),
                        Pair("Cursive", "CURSIVE")
                    )
                    fontOptions.forEach { (label, valKey) ->
                        val isSelected = fontFamilyType == valKey
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF2DBA8D) else Color(0xFF2C2C32),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF2DBA8D) else Color(0xFF44444A)),
                            modifier = Modifier
                                .width(92.dp)
                                .clickable { fontFamilyType = valKey }
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text Size Slider
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Size: ${fontSize.toInt()}sp", color = Color(0xFFD1D1D6), fontSize = 11.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 10f..46f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2DBA8D),
                            activeTrackColor = Color(0xFF2DBA8D),
                            inactiveTrackColor = Color(0xFF44444A)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Styling Chips (B, I, U, Stamp Badge, Paper Bg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isBold,
                            onClick = { isBold = !isBold },
                            label = { Text("B", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2DBA8D),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = isItalic,
                            onClick = { isItalic = !isItalic },
                            label = { Text("I", fontWeight = FontWeight.Normal, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2DBA8D),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = isUnderline,
                            onClick = { isUnderline = !isUnderline },
                            label = { Text("U", textDecoration = TextDecoration.Underline, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2DBA8D),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isStamp,
                            onClick = { isStamp = !isStamp },
                            label = { Text("Stamp", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = hasWhiteBg,
                            onClick = { hasWhiteBg = !hasWhiteBg },
                            label = { Text("Paper Bg", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2DBA8D),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Color Palette
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selectedColor == c) 2.dp else 1.dp,
                                    color = if (selectedColor == c) Color(0xFF2DBA8D) else Color(0xFF55555A),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dialog Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSave(
                                    initialOverlay.copy(
                                        text = text,
                                        fontSizeSp = fontSize,
                                        color = selectedColor,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        isUnderline = isUnderline,
                                        fontFamilyType = fontFamilyType,
                                        isStamp = isStamp,
                                        backgroundColor = if (hasWhiteBg) Color.White else Color.Transparent
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DBA8D))
                    ) {
                        Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. INTERACTIVE CANVAS COMPONENT
// ==========================================

@Composable
fun SinglePageInteractiveCanvas(
    bitmap: Bitmap,
    activeTool: ActiveEditorTool,
    textOverlays: List<TextOverlay>,
    selectedTextOverlayId: String?,
    onSelectTextOverlay: (String) -> Unit,
    onUpdateTextOverlay: (TextOverlay) -> Unit,
    onDeleteTextOverlay: (String) -> Unit,
    onEditTextOverlay: (TextOverlay) -> Unit,
    signatureOverlays: List<SignatureOverlay>,
    selectedSignatureId: String?,
    onSelectSignature: (String) -> Unit,
    onUpdateSignature: (SignatureOverlay) -> Unit,
    onDeleteSignature: (String) -> Unit,
    mosaicRegions: List<MosaicRegion>,
    selectedMosaicId: String?,
    onSelectMosaic: (String) -> Unit,
    onUpdateMosaic: (MosaicRegion) -> Unit,
    onDeleteMosaic: (String) -> Unit,
    brushStrokes: List<BrushStroke>,
    currentBrushPoints: List<Offset>,
    brushColor: Color,
    brushWidth: Float,
    brushMode: BrushMode,
    brushOpacity: Float,
    onBrushStrokeFinished: (BrushStroke) -> Unit,
    onBrushPointsChanged: (List<Offset>) -> Unit,
    eraseStrokes: List<EraseStroke>,
    currentErasePoints: List<Offset>,
    eraseWidth: Float,
    eraseMode: EraseMode,
    isErasePanZoomMode: Boolean = false,
    onEraseStrokeFinished: (EraseStroke) -> Unit,
    onErasePointsChanged: (List<Offset>) -> Unit,
    watermarkConfig: WatermarkConfig,
    ocrDocument: OcrDocument? = null,
    textEditGranularity: TextEditGranularity = TextEditGranularity.LINE,
    editTextOperations: List<EditTextOperation> = emptyList(),
    onSelectOcrItem: (Any) -> Unit = {},
    onCanvasTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Root pointer input stays in the untransformed viewport while the document is
    // visually scaled/translated by graphicsLayer below. Convert a visual touch back
    // into the document canvas coordinate system so OCR hit-testing and drawing remain
    // pixel-accurate even at 2x..8x zoom.
    fun untransformTouch(position: Offset, viewportWidth: Float, viewportHeight: Float): Offset {
        if (scale <= 1.0001f && panOffset == Offset.Zero) return position
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        return Offset(
            x = cx + (position.x - cx - panOffset.x) / scale,
            y = cy + (position.y - cy - panOffset.y) / scale
        )
    }

    // Live sampled color for AI Inpaint
    var currentSampledColor by remember { mutableStateOf(Color.White) }
    var liveDragTouchPos by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = maxWidth.value
        val containerHeight = maxHeight.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, isErasePanZoomMode) {
                    if (
                        activeTool == ActiveEditorTool.NONE ||
                        activeTool == ActiveEditorTool.FILTER ||
                        activeTool == ActiveEditorTool.EDIT_TEXT ||
                        isErasePanZoomMode
                    ) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 8.0f)
                            if (newScale > 1f) {
                                val maxPanX = (size.width * (newScale - 1f)) / 2f
                                val maxPanY = (size.height * (newScale - 1f)) / 2f
                                val newPan = panOffset + pan
                                panOffset = Offset(
                                    newPan.x.coerceIn(-maxPanX, maxPanX),
                                    newPan.y.coerceIn(-maxPanY, maxPanY)
                                )
                            } else {
                                panOffset = Offset.Zero
                            }
                            scale = newScale
                        }
                    }
                }
                .pointerInput(activeTool) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.1f) {
                                scale = 1f
                                panOffset = Offset.Zero
                            } else {
                                val targetScale = 2.2f
                                val maxPanX = (size.width * (targetScale - 1f)) / 2f
                                val maxPanY = (size.height * (targetScale - 1f)) / 2f
                                val focusX = (size.width / 2f - tapOffset.x) * (targetScale - 1f)
                                val focusY = (size.height / 2f - tapOffset.y) * (targetScale - 1f)
                                panOffset = Offset(
                                    focusX.coerceIn(-maxPanX, maxPanX),
                                    focusY.coerceIn(-maxPanY, maxPanY)
                                )
                                scale = targetScale
                            }
                        },
                        onTap = { tapOffset ->
                            val localTap = untransformTouch(tapOffset, size.width.toFloat(), size.height.toFloat())
                            val frame = calcEditorImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                            if (activeTool == ActiveEditorTool.EDIT_TEXT && ocrDocument != null) {
                                val normX = (localTap.x - frame.left) / frame.width
                                val normY = (localTap.y - frame.top) / frame.height
                                val hit = DocumentCoordinateTransformer.findHitTextItem(
                                    normTouch = Offset(normX, normY),
                                    ocrDoc = ocrDocument,
                                    granularity = textEditGranularity
                                )
                                if (hit != null) {
                                    onSelectOcrItem(hit)
                                    return@detectTapGestures
                                }
                            }
                            onCanvasTapped()
                        }
                    )
                }
                .pointerInput(activeTool, brushColor, brushWidth, brushMode, brushOpacity) {
                    if (activeTool == ActiveEditorTool.BRUSH) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val localStart = untransformTouch(startOffset, size.width.toFloat(), size.height.toFloat())
                                val frame = calcEditorImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                                val normX = ((localStart.x - frame.left) / frame.width).coerceIn(0f, 1f)
                                val normY = ((localStart.y - frame.top) / frame.height).coerceIn(0f, 1f)
                                onBrushPointsChanged(listOf(Offset(normX, normY)))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val localPos = untransformTouch(change.position, size.width.toFloat(), size.height.toFloat())
                                val frame = calcEditorImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                                val normX = ((localPos.x - frame.left) / frame.width).coerceIn(0f, 1f)
                                val normY = ((localPos.y - frame.top) / frame.height).coerceIn(0f, 1f)
                                onBrushPointsChanged(currentBrushPoints + Offset(normX, normY))
                            },
                            onDragEnd = {
                                if (currentBrushPoints.isNotEmpty()) {
                                    onBrushStrokeFinished(
                                        BrushStroke(
                                            points = currentBrushPoints,
                                            color = brushColor,
                                            strokeWidth = brushWidth,
                                            mode = brushMode,
                                            alpha = brushOpacity
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                .pointerInput(activeTool, eraseWidth, eraseMode, isErasePanZoomMode) {
                    if (activeTool == ActiveEditorTool.SMART_ERASE && !isErasePanZoomMode) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val localStart = untransformTouch(startOffset, size.width.toFloat(), size.height.toFloat())
                                val frame = calcEditorImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                                val normX = ((localStart.x - frame.left) / frame.width).coerceIn(0f, 1f)
                                val normY = ((localStart.y - frame.top) / frame.height).coerceIn(0f, 1f)
                                liveDragTouchPos = localStart

                                // Sample paper color locally for smart inpainting
                                if (eraseMode == EraseMode.AI_INPAINT) {
                                    val sampledInt = ImageProcessor.sampleLocalPaperColor(bitmap, normX, normY, 0.04f)
                                    currentSampledColor = Color(sampledInt)
                                } else {
                                    currentSampledColor = Color.White
                                }

                                onErasePointsChanged(listOf(Offset(normX, normY)))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val localPos = untransformTouch(change.position, size.width.toFloat(), size.height.toFloat())
                                liveDragTouchPos = localPos
                                val frame = calcEditorImageFrame(Size(size.width.toFloat(), size.height.toFloat()), bitmap.width, bitmap.height)
                                val normX = ((localPos.x - frame.left) / frame.width).coerceIn(0f, 1f)
                                val normY = ((localPos.y - frame.top) / frame.height).coerceIn(0f, 1f)

                                // Keep sampling local background if moving across gradient/shadow
                                if (eraseMode == EraseMode.AI_INPAINT && currentErasePoints.size % 4 == 0) {
                                    val sampledInt = ImageProcessor.sampleLocalPaperColor(bitmap, normX, normY, 0.035f)
                                    currentSampledColor = Color(sampledInt)
                                }

                                onErasePointsChanged(currentErasePoints + Offset(normX, normY))
                            },
                            onDragEnd = {
                                liveDragTouchPos = null
                                if (currentErasePoints.isNotEmpty()) {
                                    onEraseStrokeFinished(
                                        EraseStroke(
                                            points = currentErasePoints,
                                            strokeWidth = eraseWidth,
                                            mode = eraseMode,
                                            color = currentSampledColor
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                liveDragTouchPos = null
                            }
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frame = calcEditorImageFrame(size, bitmap.width, bitmap.height)

                // 1. Draw Base Document Bitmap
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
                    dstSize = IntSize(frame.width.toInt(), frame.height.toInt())
                )

                // 2. Draw Smart Erase Inpainted strokes with exact sampled paper background color
                val allErase = eraseStrokes + if (currentErasePoints.isNotEmpty()) listOf(
                    EraseStroke(
                        points = currentErasePoints,
                        strokeWidth = eraseWidth,
                        mode = eraseMode,
                        color = currentSampledColor
                    )
                ) else emptyList()

                allErase.forEach { erase ->
                    if (erase.points.size >= 2) {
                        val path = Path()
                        val p0 = Offset(frame.left + erase.points[0].x * frame.width, frame.top + erase.points[0].y * frame.height)
                        path.moveTo(p0.x, p0.y)
                        for (p in erase.points.drop(1)) {
                            path.lineTo(frame.left + p.x * frame.width, frame.top + p.y * frame.height)
                        }
                        drawPath(
                            path = path,
                            color = erase.color,
                            style = Stroke(
                                width = erase.strokeWidth * (frame.width / 600f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else if (erase.points.size == 1) {
                        val p0 = Offset(frame.left + erase.points[0].x * frame.width, frame.top + erase.points[0].y * frame.height)
                        drawCircle(
                            color = erase.color,
                            radius = (erase.strokeWidth * (frame.width / 600f)) / 2f,
                            center = p0
                        )
                    }
                }

                // 3. Draw Mosaic Redaction Regions
                mosaicRegions.forEach { mosaic ->
                    val mLeft = frame.left + mosaic.left * frame.width
                    val mTop = frame.top + mosaic.top * frame.height
                    val mWidth = mosaic.width * frame.width
                    val mHeight = mosaic.height * frame.height

                    drawRect(
                        color = Color(0xFFCBD5E1),
                        topLeft = Offset(mLeft, mTop),
                        size = Size(mWidth, mHeight)
                    )

                    val blockSize = 14f
                    var x = mLeft
                    while (x < mLeft + mWidth) {
                        var y = mTop
                        while (y < mTop + mHeight) {
                            val colIdx = ((x - mLeft) / blockSize).toInt()
                            val rowIdx = ((y - mTop) / blockSize).toInt()
                            if ((colIdx + rowIdx) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFF64748B),
                                    topLeft = Offset(x, y),
                                    size = Size(min(blockSize, mLeft + mWidth - x), min(blockSize, mTop + mHeight - y))
                                )
                            }
                            y += blockSize
                        }
                        x += blockSize
                    }

                    if (mosaic.id == selectedMosaicId) {
                        drawRect(
                            color = Color(0xFF2DBA8D),
                            topLeft = Offset(mLeft, mTop),
                            size = Size(mWidth, mHeight),
                            style = Stroke(width = 3f)
                        )
                    }
                }

                // 4. Draw Brush Strokes with Multi-mode & Highlighting support
                val allBrush = brushStrokes + if (currentBrushPoints.isNotEmpty()) listOf(
                    BrushStroke(
                        points = currentBrushPoints,
                        color = brushColor,
                        strokeWidth = brushWidth,
                        mode = brushMode,
                        alpha = brushOpacity
                    )
                ) else emptyList()

                allBrush.forEach { stroke ->
                    val strokeColor = stroke.color.copy(alpha = stroke.alpha)
                    val baseWidth = stroke.strokeWidth * (frame.width / 600f)

                    if (stroke.points.size >= 2) {
                        when (stroke.mode) {
                            BrushMode.INK_PEN -> {
                                val path = Path()
                                val p0 = Offset(frame.left + stroke.points[0].x * frame.width, frame.top + stroke.points[0].y * frame.height)
                                path.moveTo(p0.x, p0.y)
                                for (p in stroke.points.drop(1)) {
                                    path.lineTo(frame.left + p.x * frame.width, frame.top + p.y * frame.height)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = Stroke(width = baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                            BrushMode.HIGHLIGHTER -> {
                                val path = Path()
                                val p0 = Offset(frame.left + stroke.points[0].x * frame.width, frame.top + stroke.points[0].y * frame.height)
                                path.moveTo(p0.x, p0.y)
                                for (p in stroke.points.drop(1)) {
                                    path.lineTo(frame.left + p.x * frame.width, frame.top + p.y * frame.height)
                                }
                                drawPath(
                                    path = path,
                                    color = strokeColor.copy(alpha = (stroke.alpha * 0.40f).coerceIn(0.15f, 0.65f)),
                                    style = Stroke(width = baseWidth * 2.2f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                                )
                            }
                            BrushMode.LINE -> {
                                val start = Offset(frame.left + stroke.points.first().x * frame.width, frame.top + stroke.points.first().y * frame.height)
                                val end = Offset(frame.left + stroke.points.last().x * frame.width, frame.top + stroke.points.last().y * frame.height)
                                drawLine(
                                    color = strokeColor,
                                    start = start,
                                    end = end,
                                    strokeWidth = baseWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                            BrushMode.ARROW -> {
                                val start = Offset(frame.left + stroke.points.first().x * frame.width, frame.top + stroke.points.first().y * frame.height)
                                val end = Offset(frame.left + stroke.points.last().x * frame.width, frame.top + stroke.points.last().y * frame.height)
                                drawLine(
                                    color = strokeColor,
                                    start = start,
                                    end = end,
                                    strokeWidth = baseWidth,
                                    cap = StrokeCap.Round
                                )
                                // Draw arrow head
                                val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                val arrowLength = (baseWidth * 3.5f).coerceAtLeast(16f)
                                val arrowAngle = Math.PI / 6
                                val x1 = end.x - arrowLength * Math.cos(angle - arrowAngle).toFloat()
                                val y1 = end.y - arrowLength * Math.sin(angle - arrowAngle).toFloat()
                                val x2 = end.x - arrowLength * Math.cos(angle + arrowAngle).toFloat()
                                val y2 = end.y - arrowLength * Math.sin(angle + arrowAngle).toFloat()

                                drawLine(color = strokeColor, start = end, end = Offset(x1, y1), strokeWidth = baseWidth, cap = StrokeCap.Round)
                                drawLine(color = strokeColor, start = end, end = Offset(x2, y2), strokeWidth = baseWidth, cap = StrokeCap.Round)
                            }
                            BrushMode.RECTANGLE -> {
                                val start = stroke.points.first()
                                val end = stroke.points.last()
                                val left = frame.left + min(start.x, end.x) * frame.width
                                val top = frame.top + min(start.y, end.y) * frame.height
                                val width = Math.abs(end.x - start.x) * frame.width
                                val height = Math.abs(end.y - start.y) * frame.height

                                drawRect(
                                    color = strokeColor,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = baseWidth)
                                )
                            }
                            BrushMode.CIRCLE -> {
                                val start = stroke.points.first()
                                val end = stroke.points.last()
                                val left = frame.left + min(start.x, end.x) * frame.width
                                val top = frame.top + min(start.y, end.y) * frame.height
                                val width = Math.abs(end.x - start.x) * frame.width
                                val height = Math.abs(end.y - start.y) * frame.height

                                drawOval(
                                    color = strokeColor,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = baseWidth)
                                )
                            }
                        }
                    }
                }

                // 5. Draw Watermark
                if (watermarkConfig.text.isNotBlank()) {
                    drawContext.canvas.nativeCanvas.save()
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        val alphaInt = (watermarkConfig.opacity.coerceIn(0.05f, 1f) * 255).toInt()
                        color = (watermarkConfig.colorLong.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                        textSize = (frame.width / 16f).coerceIn(16f, 60f)
                        textAlign = AndroidPaint.Align.CENTER
                        isFakeBoldText = true
                    }

                    val stepX = frame.width * 0.45f
                    val stepY = frame.height * 0.25f

                    for (y in -frame.height.toInt()..(frame.height * 2).toInt() step stepY.toInt().coerceAtLeast(100)) {
                        for (x in -frame.width.toInt()..(frame.width * 2).toInt() step stepX.toInt().coerceAtLeast(100)) {
                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.rotate(-35f, frame.left + x, frame.top + y)
                            drawContext.canvas.nativeCanvas.drawText(
                                watermarkConfig.text,
                                frame.left + x,
                                frame.top + y,
                                paint
                            )
                            drawContext.canvas.nativeCanvas.restore()
                        }
                    }
                    drawContext.canvas.nativeCanvas.restore()
                }

                // 6. Draw OCR detected text highlights & in-place edit status (if in Edit Text mode)
                if (activeTool == ActiveEditorTool.EDIT_TEXT && ocrDocument != null) {
                    val itemsToDraw: List<Pair<RectF, Boolean>> = when (textEditGranularity) {
                        TextEditGranularity.BLOCK -> ocrDocument.blocks.map { block ->
                            val isEdited = editTextOperations.any { it.targetId == block.id && !it.isDeleted }
                            Pair(block.normalizedRect, isEdited)
                        }
                        TextEditGranularity.LINE -> ocrDocument.blocks.flatMap { it.lines }.map { line ->
                            val isEdited = editTextOperations.any { it.targetId == line.id && !it.isDeleted }
                            Pair(line.normalizedRect, isEdited)
                        }
                        TextEditGranularity.WORD -> ocrDocument.blocks.flatMap { it.lines }.flatMap { it.elements }.map { el ->
                            val isEdited = editTextOperations.any { it.targetId == el.id && !it.isDeleted }
                            Pair(el.normalizedRect, isEdited)
                        }
                    }

                    itemsToDraw.forEach { (rect, isEdited) ->
                        val bLeft = frame.left + rect.left * frame.width
                        val bTop = frame.top + rect.top * frame.height
                        val bWidth = rect.width() * frame.width
                        val bHeight = rect.height() * frame.height

                        val highlightColor = if (isEdited) Color(0xFFFF6B63) else Color(0xFF2196F3)
                        drawRect(
                            color = highlightColor.copy(alpha = if (isEdited) 0.25f else 0.15f),
                            topLeft = Offset(bLeft, bTop),
                            size = Size(bWidth, bHeight)
                        )
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(bLeft, bTop),
                            size = Size(bWidth, bHeight),
                            style = Stroke(
                                width = if (isEdited) 2.2f else 1.35f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                            )
                        )
                    }
                }

                // 7. Draw Live Touch Cursor Ring for Eraser
                if (activeTool == ActiveEditorTool.SMART_ERASE && liveDragTouchPos != null) {
                    val pos = liveDragTouchPos!!
                    val radius = (eraseWidth * (frame.width / 600f)) / 2f
                    drawCircle(
                        color = Color(0x662DBA8D),
                        radius = radius,
                        center = pos
                    )
                    drawCircle(
                        color = Color(0xFF2DBA8D),
                        radius = radius,
                        center = pos,
                        style = Stroke(width = 2f)
                    )
                }
            }

            // 7. Interactive Composable Overlays
            textOverlays.forEach { overlay ->
                InteractiveTextOverlayView(
                    overlay = overlay,
                    isSelected = overlay.id == selectedTextOverlayId,
                    onSelect = { onSelectTextOverlay(overlay.id) },
                    onUpdate = onUpdateTextOverlay,
                    onDelete = { onDeleteTextOverlay(overlay.id) },
                    onEdit = { onEditTextOverlay(overlay) },
                    parentWidth = containerWidth,
                    parentHeight = containerHeight
                )
            }

            signatureOverlays.forEach { sig ->
                InteractiveSignatureOverlayView(
                    overlay = sig,
                    isSelected = sig.id == selectedSignatureId,
                    onSelect = { onSelectSignature(sig.id) },
                    onUpdate = onUpdateSignature,
                    onDelete = { onDeleteSignature(sig.id) },
                    parentWidth = containerWidth,
                    parentHeight = containerHeight
                )
            }
        }
    }
}

@Composable
fun InteractiveTextOverlayView(
    overlay: TextOverlay,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (TextOverlay) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    parentWidth: Float,
    parentHeight: Float
) {
    var offsetX by remember(overlay.x) { mutableFloatStateOf(overlay.x) }
    var offsetY by remember(overlay.y) { mutableFloatStateOf(overlay.y) }

    val fontFam = when (overlay.fontFamilyType.uppercase()) {
        "SERIF" -> androidx.compose.ui.text.font.FontFamily.Serif
        "MONO" -> androidx.compose.ui.text.font.FontFamily.Monospace
        "CURSIVE" -> androidx.compose.ui.text.font.FontFamily.Cursive
        "BENGALI", "BANGLA" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val posX = (offsetX * parentWidth).dp
        val posY = (offsetY * parentHeight).dp

        Box(
            modifier = Modifier
                .padding(start = posX, top = posY)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / parentWidth).coerceIn(0.05f, 0.95f)
                        offsetY = (offsetY + dragAmount.y / parentHeight).coerceIn(0.05f, 0.95f)
                        onUpdate(overlay.copy(x = offsetX, y = offsetY))
                    }
                }
                .clickable { onSelect() }
        ) {
            Surface(
                shape = if (overlay.isStamp) RoundedCornerShape(4.dp) else RoundedCornerShape(4.dp),
                color = overlay.backgroundColor,
                border = if (overlay.isStamp) {
                    BorderStroke(2.dp, overlay.color)
                } else if (isSelected) {
                    BorderStroke(1.5.dp, Color(0xFF2DBA8D))
                } else null,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = overlay.text,
                    color = overlay.color,
                    fontSize = overlay.fontSizeSp.sp,
                    fontFamily = fontFam,
                    fontWeight = if (overlay.isBold || overlay.isStamp) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (overlay.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = if (overlay.isUnderline) TextDecoration.Underline else TextDecoration.None,
                    letterSpacing = if (overlay.isStamp) 1.5.sp else TextUnit.Unspecified,
                    modifier = Modifier.padding(horizontal = if (overlay.isStamp) 8.dp else 6.dp, vertical = if (overlay.isStamp) 4.dp else 2.dp)
                )
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF5252),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clickable { onDelete() }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.padding(2.dp))
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2DBA8D),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(20.dp)
                        .clickable { onEdit() }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

@Composable
fun InteractiveSignatureOverlayView(
    overlay: SignatureOverlay,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (SignatureOverlay) -> Unit,
    onDelete: () -> Unit,
    parentWidth: Float,
    parentHeight: Float
) {
    var offsetX by remember(overlay.x) { mutableFloatStateOf(overlay.x) }
    var offsetY by remember(overlay.y) { mutableFloatStateOf(overlay.y) }
    var widthRatio by remember(overlay.widthRatio) { mutableFloatStateOf(overlay.widthRatio) }

    Box(modifier = Modifier.fillMaxSize()) {
        val posX = (offsetX * parentWidth).dp
        val posY = (offsetY * parentHeight).dp
        val sigWidth = (parentWidth * widthRatio).dp

        Box(
            modifier = Modifier
                .padding(start = posX, top = posY)
                .width(sigWidth)
                .graphicsLayer {
                    rotationZ = overlay.rotationDegrees
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / parentWidth).coerceIn(0.05f, 0.95f)
                        offsetY = (offsetY + dragAmount.y / parentHeight).coerceIn(0.05f, 0.95f)
                        onUpdate(overlay.copy(x = offsetX, y = offsetY))
                    }
                }
                .clickable { onSelect() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (isSelected) BorderStroke(1.5.dp, Color(0xFF2DBA8D)) else BorderStroke(0.dp, Color.Transparent))
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = overlay.bitmap,
                    contentDescription = "Signature",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isSelected) {
                // Rotate 90 degrees button at Top-Start
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2DBA8D),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .size(24.dp)
                        .clickable {
                            val nextRot = (overlay.rotationDegrees + 90f) % 360f
                            onUpdate(overlay.copy(rotationDegrees = nextRot))
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate 90 Degrees",
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp)
                    )
                }

                // Delete button at Top-End
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF5252),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(24.dp)
                        .clickable { onDelete() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp)
                    )
                }

                // Resize handle at Bottom-End
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2DBA8D),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 8.dp)
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaRatio = (dragAmount.x + dragAmount.y) / (parentWidth * 1.2f)
                                val newRatio = (widthRatio + deltaRatio).coerceIn(0.12f, 0.90f)
                                widthRatio = newRatio
                                onUpdate(overlay.copy(widthRatio = newRatio))
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenWith,
                        contentDescription = "Resize",
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 9. FINAL BITMAP RENDERING & COMPOSITING
// ==========================================

suspend fun renderFinalPageBitmap(
    base: Bitmap,
    eraseStrokes: List<EraseStroke>,
    mosaicRegions: List<MosaicRegion>,
    brushStrokes: List<BrushStroke>,
    textOverlays: List<TextOverlay>,
    signatureOverlays: List<SignatureOverlay>,
    watermarkConfig: WatermarkConfig,
    editTextOperations: List<EditTextOperation> = emptyList()
): Bitmap = withContext(Dispatchers.Default) {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val w = result.width.toFloat()
    val h = result.height.toFloat()

    // 0. Render Edit Text in-place replacements
    if (editTextOperations.isNotEmpty()) {
        DocumentTextRenderer.renderOperationsOnCanvas(canvas, editTextOperations, w, h)
    }

    // 1. Render Smart Erase Patches
    if (eraseStrokes.isNotEmpty()) {
        for (erase in eraseStrokes) {
            val erasePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = erase.color.toArgb()
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = erase.strokeWidth * (w / 600f)
            }
            if (erase.points.size >= 2) {
                val path = android.graphics.Path()
                path.moveTo(erase.points[0].x * w, erase.points[0].y * h)
                for (p in erase.points.drop(1)) {
                    path.lineTo(p.x * w, p.y * h)
                }
                canvas.drawPath(path, erasePaint)
            } else if (erase.points.size == 1) {
                erasePaint.style = AndroidPaint.Style.FILL
                canvas.drawCircle(
                    erase.points[0].x * w,
                    erase.points[0].y * h,
                    (erase.strokeWidth * (w / 600f)) / 2f,
                    erasePaint
                )
            }
        }
    }

    // 2. Render Mosaic Regions
    if (mosaicRegions.isNotEmpty()) {
        val rects = mosaicRegions.map {
            RectF(it.left, it.top, it.left + it.width, it.top + it.height)
        }
        val pixelated = ImageProcessor.applyMosaic(result, rects)
        val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(pixelated, 0f, 0f, p)
    }

    // 3. Render Brush Inking Strokes
    if (brushStrokes.isNotEmpty()) {
        val brushPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
        for (stroke in brushStrokes) {
            if (stroke.points.size >= 2) {
                val argb = stroke.color.toArgb()
                val alpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
                val finalColor = (argb and 0x00FFFFFF) or (alpha shl 24)
                val baseWidth = stroke.strokeWidth * (w / 600f)

                brushPaint.color = finalColor

                when (stroke.mode) {
                    BrushMode.INK_PEN -> {
                        brushPaint.strokeWidth = baseWidth
                        val path = android.graphics.Path()
                        path.moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
                        for (pt in stroke.points.drop(1)) {
                            path.lineTo(pt.x * w, pt.y * h)
                        }
                        canvas.drawPath(path, brushPaint)
                    }
                    BrushMode.HIGHLIGHTER -> {
                        brushPaint.strokeWidth = baseWidth * 2.2f
                        brushPaint.strokeCap = AndroidPaint.Cap.SQUARE
                        val path = android.graphics.Path()
                        path.moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
                        for (pt in stroke.points.drop(1)) {
                            path.lineTo(pt.x * w, pt.y * h)
                        }
                        canvas.drawPath(path, brushPaint)
                        brushPaint.strokeCap = AndroidPaint.Cap.ROUND
                    }
                    BrushMode.LINE -> {
                        brushPaint.strokeWidth = baseWidth
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        canvas.drawLine(start.x * w, start.y * h, end.x * w, end.y * h, brushPaint)
                    }
                    BrushMode.ARROW -> {
                        brushPaint.strokeWidth = baseWidth
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        val sx = start.x * w
                        val sy = start.y * h
                        val ex = end.x * w
                        val ey = end.y * h
                        canvas.drawLine(sx, sy, ex, ey, brushPaint)

                        val angle = Math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                        val arrowLength = (baseWidth * 3.5f).coerceAtLeast(16f)
                        val arrowAngle = Math.PI / 6
                        val x1 = ex - arrowLength * Math.cos(angle - arrowAngle).toFloat()
                        val y1 = ey - arrowLength * Math.sin(angle - arrowAngle).toFloat()
                        val x2 = ex - arrowLength * Math.cos(angle + arrowAngle).toFloat()
                        val y2 = ey - arrowLength * Math.sin(angle + arrowAngle).toFloat()

                        canvas.drawLine(ex, ey, x1, y1, brushPaint)
                        canvas.drawLine(ex, ey, x2, y2, brushPaint)
                    }
                    BrushMode.RECTANGLE -> {
                        brushPaint.strokeWidth = baseWidth
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        val left = min(start.x, end.x) * w
                        val top = min(start.y, end.y) * h
                        val right = max(start.x, end.x) * w
                        val bottom = max(start.y, end.y) * h
                        canvas.drawRect(left, top, right, bottom, brushPaint)
                    }
                    BrushMode.CIRCLE -> {
                        brushPaint.strokeWidth = baseWidth
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        val left = min(start.x, end.x) * w
                        val top = min(start.y, end.y) * h
                        val right = max(start.x, end.x) * w
                        val bottom = max(start.y, end.y) * h
                        canvas.drawOval(RectF(left, top, right, bottom), brushPaint)
                    }
                }
            }
        }
    }

    // 4. Render Text Overlays & CamScanner Stamp Badges
    for (textOverlay in textOverlays) {
        if (textOverlay.text.isNotBlank()) {
            val tfType = when (textOverlay.fontFamilyType.uppercase()) {
                "SERIF" -> Typeface.SERIF
                "MONO" -> Typeface.MONOSPACE
                "BENGALI", "BANGLA" -> Typeface.create("sans-serif", Typeface.NORMAL)
                else -> Typeface.DEFAULT
            }
            val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = textOverlay.color.toArgb()
                textSize = textOverlay.fontSizeSp * (w / 400f).coerceIn(1f, 4f)
                typeface = if (textOverlay.isBold || textOverlay.isStamp) {
                    if (textOverlay.isItalic) Typeface.create(tfType, Typeface.BOLD_ITALIC) else Typeface.create(tfType, Typeface.BOLD)
                } else if (textOverlay.isItalic) {
                    Typeface.create(tfType, Typeface.ITALIC)
                } else {
                    tfType
                }
                isUnderlineText = textOverlay.isUnderline
            }

            val tx = textOverlay.x * w
            val ty = textOverlay.y * h

            val bounds = AndroidRect()
            textPaint.getTextBounds(textOverlay.text, 0, textOverlay.text.length, bounds)
            val padding = 10f

            if (textOverlay.backgroundColor != Color.Transparent || textOverlay.isStamp) {
                val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = textOverlay.backgroundColor.toArgb()
                    style = AndroidPaint.Style.FILL
                }
                canvas.drawRect(
                    tx - padding,
                    ty - bounds.height() - padding,
                    tx + bounds.width() + padding,
                    ty + padding,
                    bgPaint
                )
            }

            // Draw stamp border if isStamp
            if (textOverlay.isStamp) {
                val stampBorderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = textOverlay.color.toArgb()
                    style = AndroidPaint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRect(
                    tx - padding,
                    ty - bounds.height() - padding,
                    tx + bounds.width() + padding,
                    ty + padding,
                    stampBorderPaint
                )
            }

            canvas.drawText(textOverlay.text, tx, ty, textPaint)
        }
    }

    // 5. Render Signature Overlays
    for (sig in signatureOverlays) {
        val sigW = (w * sig.widthRatio).toInt().coerceAtLeast(50)
        val sigAspect = sig.bitmap.height.toFloat() / sig.bitmap.width.toFloat()
        val sigH = (sigW * sigAspect).toInt()
        val sx = sig.x * w
        val sy = sig.y * h
        val scaled = Bitmap.createScaledBitmap(sig.bitmap, sigW, sigH, true)
        val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
        canvas.save()
        if (sig.rotationDegrees != 0f) {
            canvas.rotate(sig.rotationDegrees, sx + sigW / 2f, sy + sigH / 2f)
        }
        canvas.drawBitmap(scaled, sx, sy, p)
        canvas.restore()
    }

    // 6. Render Anti-Theft Watermark
    if (watermarkConfig.text.isNotBlank()) {
        val watermarked = ImageProcessor.applyWatermark(
            source = result,
            text = watermarkConfig.text,
            opacity = watermarkConfig.opacity,
            colorLong = watermarkConfig.colorLong,
            diagonal = watermarkConfig.isDiagonal
        )
        return@withContext watermarked
    }

    result
}
