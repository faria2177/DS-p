package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.util.AiOrchestrator
import com.docscan.util.AutoOrientationHelper
import com.docscan.util.FileUtils
import com.docscan.util.GeminiAiService
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.textedit.DocumentEditorImageAnalyzer
import com.docscan.util.textedit.DocumentEditorOcrCache
import com.docscan.util.textedit.DocumentEditorPageState
import com.docscan.util.textedit.DocumentEditorSnapshot
import com.docscan.util.textedit.DocumentTextInpainter
import com.docscan.util.textedit.DocumentTextRenderer
import com.docscan.util.textedit.EditTextOperation
import com.docscan.util.textedit.EditorElementType
import com.docscan.util.textedit.EditorImageElement
import com.docscan.util.textedit.EditorTextElement
import com.docscan.util.textedit.FontStyleEstimate
import com.docscan.util.textedit.HybridTextRecognitionEngine
import com.docscan.util.textedit.NeighborFontStyleMatcher
import com.docscan.util.textedit.OcrDocument
import com.docscan.util.textedit.area
import com.docscan.util.textedit.isProtectedDocumentText
import com.docscan.util.textedit.toEditorTextElements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

private val EditorBg: Color get() = ThemeManager.themeColor(Color(0xFF111214), Color(0xFFF8FAFC))
private val EditorTop: Color get() = ThemeManager.themeColor(Color(0xFF17181B), Color(0xFFFFFFFF))
private val EditorPopupBg: Color get() = ThemeManager.themeColor(Color(0xFF202227), Color(0xFFFFFFFF))
private val EditorPopupDivider: Color get() = ThemeManager.themeColor(Color(0xFF353840), Color(0xFFE2E8F0))
private val EditorInputBg: Color get() = ThemeManager.themeColor(Color(0xFF2A2D34), Color(0xFFF1F5F9))
private val EditorInstructionBg: Color get() = ThemeManager.themeColor(Color(0xFF24262B), Color(0xFFE9EFF5))
private val EditorPanel: Color get() = ThemeManager.themeColor(Color(0xFF1E2025), Color(0xFFFFFFFF))
private val EditorAccent = Color(0xFF18CDB1)
private val EditorCyan = Color(0xFF35C9F2)
private val EditorText: Color get() = ThemeManager.themeColor(Color(0xFFF5F7FA), Color(0xFF0F172A))
private val EditorMuted: Color get() = ThemeManager.themeColor(Color(0xFFA8AFBB), Color(0xFF475569))

private data class SelectedEditorElement(
    val pageId: Long,
    val text: EditorTextElement? = null,
    val image: EditorImageElement? = null
)

private data class PendingAiReplacement(
    val pageId: Long,
    val element: EditorTextElement,
    val currentText: String,
    val proposedText: String,
    val title: String
)

private data class ManualEditSheetState(
    val pageId: Long,
    val element: EditorTextElement,
    val text: String
)

@Composable
fun DocumentEditTextScreen(
    documentId: Long,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val recognizer = remember(context) { HybridTextRecognitionEngine(context) }

    var document by remember(documentId) { mutableStateOf<DocumentEntity?>(null) }
    val pageStates = remember(documentId) { mutableStateListOf<DocumentEditorPageState>() }
    val displayBitmaps = remember(documentId) { mutableStateMapOf<Long, Bitmap>() }
    val analysisStarted = remember(documentId) { mutableStateMapOf<Long, Boolean>() }
    val operationsByPage = remember(documentId) { mutableStateMapOf<Long, List<EditTextOperation>>() }

    val history = remember(documentId) { mutableStateListOf(DocumentEditorSnapshot(emptyMap())) }
    var historyIndex by remember(documentId) { mutableIntStateOf(0) }

    var selected by remember { mutableStateOf<SelectedEditorElement?>(null) }
    var selectAllPageId by remember { mutableStateOf<Long?>(null) }
    var editingElementId by remember { mutableStateOf<String?>(null) }
    var editingPageId by remember { mutableStateOf<Long?>(null) }
    var editingDraft by remember { mutableStateOf("") }
    var pendingAi by remember { mutableStateOf<PendingAiReplacement?>(null) }
    var manualEditSheet by remember { mutableStateOf<ManualEditSheetState?>(null) }
    var isAiWorking by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isProcessingTextChange by remember { mutableStateOf(false) }
    var fontMatchProcessingPageId by remember { mutableStateOf<Long?>(null) }
    var fontMatchProcessingTargetId by remember { mutableStateOf<String?>(null) }
    var fontMatchNeighborIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun hasChanges(): Boolean = operationsByPage.values.any { it.isNotEmpty() }

    fun snapshotCurrent(): DocumentEditorSnapshot = DocumentEditorSnapshot(
        operationsByPage.mapValues { (_, value) -> value.map { it.copy(targetNormalizedRect = RectF(it.targetNormalizedRect)) } }
    )

    fun restoreSnapshot(snapshot: DocumentEditorSnapshot) {
        operationsByPage.clear()
        snapshot.pageOperations.forEach { (pageId, ops) ->
            operationsByPage[pageId] = ops.map { it.copy(targetNormalizedRect = RectF(it.targetNormalizedRect)) }
        }
        selected = null
        selectAllPageId = null
        editingElementId = null
        editingPageId = null
    }

    fun pushHistory() {
        while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
        history += snapshotCurrent()
        if (history.size > 60) {
            history.removeAt(0)
        }
        historyIndex = history.lastIndex
    }

    fun mutatePageOperations(pageId: Long, ops: List<EditTextOperation>, push: Boolean = true) {
        operationsByPage[pageId] = ops
        if (push) pushHistory()
    }

    fun currentElement(pageId: Long, elementId: String): EditorTextElement? {
        val state = pageStates.firstOrNull { it.page.id == pageId } ?: return null
        return effectiveTextElements(state, operationsByPage[pageId].orEmpty()).firstOrNull { it.id == elementId }
    }

    fun upsertOperation(pageId: Long, element: EditorTextElement, transform: (EditTextOperation) -> EditTextOperation, push: Boolean = true) {
        val currentOps = operationsByPage[pageId].orEmpty()
        val existing = currentOps.firstOrNull { it.targetId == element.id }
        val base = existing ?: operationFromElement(element)
        val updated = transform(base)
        val next = currentOps.filterNot { it.targetId == element.id } + updated
        mutatePageOperations(pageId, next, push)
    }

    fun requestClose() {
        if (hasChanges()) showDiscardDialog = true else onNavigateBack()
    }

    BackHandler { requestClose() }

    LaunchedEffect(documentId) {
        val doc = viewModel.getDocumentDirect(documentId)
        val pages = viewModel.getPagesForDocumentDirect(documentId)
        document = doc
        pageStates.clear()
        displayBitmaps.clear()
        analysisStarted.clear()
        operationsByPage.clear()
        pages.sortedBy { it.pageNumber }.forEachIndexed { index, page ->
            val path = page.processedImagePath.ifBlank { page.originalImagePath }
            val bounds = decodeBounds(path)
            pageStates += DocumentEditorPageState(
                page = page,
                pageIndex = index,
                sourcePath = path,
                displayWidth = bounds.first.coerceAtLeast(1),
                displayHeight = bounds.second.coerceAtLeast(1)
            )
        }
        history.clear()
        history += DocumentEditorSnapshot(emptyMap())
        historyIndex = 0
    }

    suspend fun analyzePage(pageId: Long) {
        if (analysisStarted[pageId] == true) return
        val index = pageStates.indexOfFirst { it.page.id == pageId }
        if (index < 0) return
        analysisStarted[pageId] = true
        val initial = pageStates[index]
        pageStates[index] = initial.copy(isAnalyzing = true, analysisError = null)

        withContext(Dispatchers.Default) {
            try {
                val cached = DocumentEditorOcrCache.load(context, documentId, pageId, initial.sourcePath)
                val preview = FileUtils.loadBitmap(initial.sourcePath, maxDimension = 2600)
                    ?: throw IllegalStateException("Cannot load page image")

                if (cached != null) {
                    val upright = rotateBitmap(preview, cached.rotationDegrees)
                    withContext(Dispatchers.Main) {
                        displayBitmaps[pageId] = upright
                        val currentIndex = pageStates.indexOfFirst { it.page.id == pageId }
                        if (currentIndex >= 0) {
                            pageStates[currentIndex] = pageStates[currentIndex].copy(
                                displayWidth = upright.width,
                                displayHeight = upright.height,
                                orientationDegrees = cached.rotationDegrees,
                                ocrDocument = cached.ocrDocument,
                                imageElements = cached.imageElements,
                                isAnalyzing = false,
                                analysisComplete = true
                            )
                        }
                    }
                    if (upright !== preview) runCatching { preview.recycle() }
                    return@withContext
                }

                // Show the source immediately while orientation/OCR work continues behind it.
                withContext(Dispatchers.Main) { displayBitmaps[pageId] = preview }

                val orientation = AutoOrientationHelper.detectAndCorrectOrientation(preview)
                val upright = orientation.rotatedBitmap
                val ocr = recognizer.recognizeText(upright)
                val images = DocumentEditorImageAnalyzer.analyze(upright, initial.pageIndex, ocr)
                DocumentEditorOcrCache.save(
                    context = context,
                    documentId = documentId,
                    pageId = pageId,
                    sourcePath = initial.sourcePath,
                    rotationDegrees = orientation.rotationAppliedDegrees,
                    ocrDocument = ocr,
                    imageElements = images
                )

                withContext(Dispatchers.Main) {
                    displayBitmaps[pageId] = upright
                    val currentIndex = pageStates.indexOfFirst { it.page.id == pageId }
                    if (currentIndex >= 0) {
                        pageStates[currentIndex] = pageStates[currentIndex].copy(
                            displayWidth = upright.width,
                            displayHeight = upright.height,
                            orientationDegrees = orientation.rotationAppliedDegrees,
                            ocrDocument = ocr,
                            imageElements = images,
                            isAnalyzing = false,
                            analysisComplete = true
                        )
                    }
                }
                if (upright !== preview) runCatching { preview.recycle() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    val currentIndex = pageStates.indexOfFirst { it.page.id == pageId }
                    if (currentIndex >= 0) {
                        pageStates[currentIndex] = pageStates[currentIndex].copy(
                            isAnalyzing = false,
                            analysisComplete = true,
                            analysisError = t.message ?: "Analysis failed"
                        )
                    }
                }
            }
        }
    }

    // Lazy analysis: visible page and one page on either side only.
    LaunchedEffect(listState, pageStates.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible ->
                val candidates = visible.flatMap { listOf(it - 1, it, it + 1) }
                    .filter { it in pageStates.indices }
                    .distinct()
                candidates.forEach { index ->
                    val id = pageStates[index].page.id
                    if (analysisStarted[id] != true) launch { analyzePage(id) }
                }
            }
    }

    fun selectedTextElement(): EditorTextElement? {
        val s = selected ?: return null
        val id = s.text?.id ?: return null
        return currentElement(s.pageId, id)
    }

    fun beginInlineEdit(pageId: Long, element: EditorTextElement) {
        selected = SelectedEditorElement(pageId, text = element)
        manualEditSheet = ManualEditSheetState(
            pageId = pageId,
            element = element,
            text = element.text
        )
    }

    fun commitInlineEdit() {
        val pageId = editingPageId ?: return
        val id = editingElementId ?: return
        val element = currentElement(pageId, id) ?: selected?.text ?: return
        val newValue = editingDraft
        upsertOperation(pageId, element, { op ->
            op.copy(
                newText = newValue,
                isDeleted = false,
                fontFamilyType = if (com.docscan.util.textedit.BengaliTextSupport.containsBangla(newValue)) "BENGALI" else op.fontFamilyType
            )
        })
        editingElementId = null
        editingPageId = null
    }

    fun applyManualEdit(
        pageId: Long,
        element: EditorTextElement,
        newValue: String,
        useGeminiPrecision: Boolean
    ) {
        val pageState = pageStates.firstOrNull { it.page.id == pageId }
        val effectiveElements = pageState?.let {
            effectiveTextElements(it, operationsByPage[pageId].orEmpty())
        }.orEmpty()
        val neighbours = NeighborFontStyleMatcher.findNeighbors(element, effectiveElements)

        fontMatchProcessingPageId = pageId
        fontMatchProcessingTargetId = element.id
        fontMatchNeighborIds = neighbours.mapTo(linkedSetOf()) { it.id }
        manualEditSheet = null
        selected = null
        isProcessingTextChange = true

        scope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                val source = displayBitmaps[pageId]
                val localMatch = withContext(Dispatchers.Default) {
                    source?.let { NeighborFontStyleMatcher.analyze(it, element, neighbours) }
                }

                val geminiStyle = if (
                    useGeminiPrecision &&
                    source != null &&
                    GeminiAiService.isApiKeyValid(context)
                ) {
                    val contextRect = NeighborFontStyleMatcher.contextRect(element, neighbours)
                    val crop = withContext(Dispatchers.Default) {
                        cropNormalizedBitmap(source, contextRect, 0.04f)
                    }
                    try {
                        crop?.let {
                            GeminiAiService.analyzeTextStyleAi(
                                textCrop = it,
                                originalText = element.text,
                                nearbyText = neighbours.map { n -> n.text }.filter { it.isNotBlank() }.take(8),
                                context = context
                            )
                        }
                    } finally {
                        crop?.let { runCatching { it.recycle() } }
                    }
                } else null

                // Keep the original raster + local neighbour analysis as source of truth.
                // Gemini may refine family/weight only when confidence is strong.
                upsertOperation(pageId, element, { op ->
                    val local = localMatch
                    val confidentAi = geminiStyle?.takeIf { it.confidence >= 0.64f }
                    op.copy(
                        newText = newValue,
                        isDeleted = false,
                        // Never silently replace the original font with a low-confidence guess.
                        fontFamilyType = when {
                            com.docscan.util.textedit.BengaliTextSupport.containsBangla(newValue) -> "BENGALI"
                            confidentAi != null && (confidentAi?.confidence ?: 0f) >= 0.85f -> confidentAi.family
                            local != null && (local?.confidence ?: 0f) >= 0.86f -> local.fontFamilyType
                            else -> op.fontFamilyType
                        },
                        fontSizeSp = local?.fontSizeSp?.takeIf {
                            (local?.confidence ?: 0f) >= 0.82f &&
                                it in (op.fontSizeSp * 0.83f)..(op.fontSizeSp * 1.17f)
                        } ?: op.fontSizeSp,
                        textColor = local?.textColor?.takeIf { (local?.confidence ?: 0f) >= 0.85f } ?: op.textColor,
                        isBold = confidentAi?.isBold?.takeIf { (confidentAi?.confidence ?: 0f) >= 0.85f }
                            ?: local?.isBold?.takeIf { (local?.confidence ?: 0f) >= 0.86f } ?: op.isBold,
                        isItalic = confidentAi?.isItalic?.takeIf { (confidentAi?.confidence ?: 0f) >= 0.85f }
                            ?: local?.isItalic?.takeIf { (local?.confidence ?: 0f) >= 0.86f } ?: op.isItalic,
                        isUnderline = local?.isUnderline?.takeIf { (local?.confidence ?: 0f) >= 0.86f } ?: op.isUnderline
                    )
                })

                // Keep the scanning animation visible long enough to communicate that the
                // neighbouring font samples were actually analysed.
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 1100L) delay(1100L - elapsed)

                val updated = currentElement(pageId, element.id)
                selected = SelectedEditorElement(pageId, text = updated ?: element)
                Toast.makeText(
                    context,
                    when {
                        geminiStyle != null -> "DS AI matched the surrounding font style"
                        localMatch != null -> "Surrounding font style matched locally"
                        else -> "Text updated"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isProcessingTextChange = false
                fontMatchProcessingPageId = null
                fontMatchProcessingTargetId = null
                fontMatchNeighborIds = emptySet()
            }
        }
    }

    fun deleteElement(pageId: Long, element: EditorTextElement) {
        upsertOperation(pageId, element, { it.copy(newText = "", isDeleted = true) })
        selected = null
    }

    fun runAi(pageId: Long, element: EditorTextElement, polish: Boolean) {
        val currentText = element.text
        if (polish && (element.protectedFactualField || isProtectedDocumentText(currentText))) {
            Toast.makeText(context, "AI Polish is disabled for identity/number fields", Toast.LENGTH_LONG).show()
            return
        }
        isAiWorking = true
        scope.launch {
            val instruction = if (polish) {
                "Polish this normal document text for clarity. Preserve the original language and factual meaning. Do not invent names, dates, IDs, numbers or facts. Return only the replacement text."
            } else {
                "Proofread OCR text only: fix OCR mistakes, spelling, punctuation and grammar while preserving every factual value. Never change names, IDs, dates, phone/account/ticket/passport/NID numbers or MRZ values. Preserve Bangla/English language. Return only the corrected text."
            }
            val result = AiOrchestrator.editTextAi(currentText, instruction, context)
            isAiWorking = false
            if (result.isBlank()) return@launch
            if (!structuredValuesPreserved(currentText, result) ||
                (element.protectedFactualField && factualCore(currentText) != factualCore(result))
            ) {
                Toast.makeText(context, "AI result rejected because a factual value changed", Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingAi = PendingAiReplacement(
                pageId = pageId,
                element = element,
                currentText = currentText,
                proposedText = result,
                title = if (polish) "AI Polish" else "AI Proofread"
            )
        }
    }

    fun applyFormatting(transform: (EditTextOperation) -> EditTextOperation) {
        val s = selected ?: return
        val element = selectedTextElement() ?: return
        upsertOperation(s.pageId, element, transform)
        selected = SelectedEditorElement(s.pageId, text = currentElement(s.pageId, element.id))
    }

    fun addTextToVisiblePage() {
        val index = listState.firstVisibleItemIndex.coerceIn(0, (pageStates.size - 1).coerceAtLeast(0))
        val state = pageStates.getOrNull(index) ?: return
        val id = "added_${UUID.randomUUID()}"
        val op = EditTextOperation(
            targetId = id,
            originalText = "",
            newText = "New text",
            originalNormalizedRect = RectF(0.14f, 0.14f, 0.56f, 0.20f),
            targetNormalizedRect = RectF(0.14f, 0.14f, 0.56f, 0.20f),
            textColor = Color.Black,
            fontSizeSp = 16f,
            backgroundColor = Color.Transparent,
            autoInpaintBackground = false
        )
        mutatePageOperations(state.page.id, operationsByPage[state.page.id].orEmpty() + op)
        val virtual = virtualElementFromOperation(state, op)
        selected = SelectedEditorElement(state.page.id, text = virtual)
        beginInlineEdit(state.page.id, virtual)
    }

    suspend fun saveDocument() {
        if (!hasChanges()) {
            Toast.makeText(context, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        try {
            val rendered = mutableMapOf<Long, Bitmap>()
            val extracted = mutableMapOf<Long, String>()
            pageStates.forEach { state ->
                val ops = operationsByPage[state.page.id].orEmpty()
                if (ops.isEmpty()) return@forEach
                val source = withContext(Dispatchers.IO) { decodeBitmapForSaving(state.sourcePath) } ?: return@forEach
                val upright = withContext(Dispatchers.Default) { rotateBitmap(source, state.orientationDegrees) }
                if (upright !== source) runCatching { source.recycle() }
                val composed = renderOperations(upright, ops)
                if (composed !== upright) runCatching { upright.recycle() }
                rendered[state.page.id] = composed
                extracted[state.page.id] = effectiveTextElements(state, ops)
                    .sortedWith(compareBy<EditorTextElement> { it.normalizedRect.top }.thenBy { it.normalizedRect.left })
                    .joinToString(" ") { it.text }
            }

            val ok = viewModel.saveDocumentEditorPages(documentId, rendered, extracted)
            rendered.values.forEach { runCatching { it.recycle() } }
            if (ok) {
                pageStates.forEach { state ->
                    if (operationsByPage[state.page.id].orEmpty().isNotEmpty()) {
                        DocumentEditorOcrCache.invalidate(context, documentId, state.page.id)
                    }
                }
                Toast.makeText(context, "Document edits saved", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            } else {
                Toast.makeText(context, "Could not save document edits", Toast.LENGTH_LONG).show()
            }
        } finally {
            isSaving = false
        }
    }

    val anyAnalyzing = pageStates.any { it.isAnalyzing }
    val totalCharacters = pageStates.sumOf { state ->
        effectiveTextElements(state, operationsByPage[state.page.id].orEmpty()).sumOf { it.text.length }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // Premium top app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(EditorTop)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { requestClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = EditorText)
                }
                Column(Modifier.weight(1f)) {
                    Text("Edit Text", color = EditorText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        document?.title ?: "Document",
                        color = EditorMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { Toast.makeText(context, "Feedback option ready", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Feedback", color = EditorText)
                }
                Button(
                    onClick = { scope.launch { saveDocument() } },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = EditorAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            // Undo / redo row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(EditorPanel)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = historyIndex > 0,
                    onClick = {
                        if (historyIndex > 0) {
                            historyIndex--
                            restoreSnapshot(history[historyIndex])
                        }
                    }
                ) { Icon(Icons.Default.Undo, "Undo", tint = if (historyIndex > 0) EditorText else EditorMuted.copy(alpha = 0.35f)) }
                IconButton(
                    enabled = historyIndex < history.lastIndex,
                    onClick = {
                        if (historyIndex < history.lastIndex) {
                            historyIndex++
                            restoreSnapshot(history[historyIndex])
                        }
                    }
                ) { Icon(Icons.Default.Redo, "Redo", tint = if (historyIndex < history.lastIndex) EditorText else EditorMuted.copy(alpha = 0.35f)) }
                Spacer(Modifier.weight(1f))
                Text("$totalCharacters chars", color = EditorMuted, fontSize = 11.sp)
            }

            // Instruction bar
            Surface(color = EditorInstructionBg, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        selectAllPageId != null -> "All text on this page is selected."
                        anyAnalyzing -> "Analyzing document…"
                        else -> "Tap text or image to edit."
                    },
                    color = if (anyAnalyzing) EditorAccent else EditorMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (selectAllPageId != null) {
                val pageId = selectAllPageId!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditorInstructionBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = {
                        val state = pageStates.firstOrNull { it.page.id == pageId }
                        if (state != null) {
                            val text = effectiveTextElements(state, operationsByPage[pageId].orEmpty()).joinToString(" ") { it.text }
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "Page text copied", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Copy all", color = EditorCyan) }
                    TextButton(onClick = {
                        val state = pageStates.firstOrNull { it.page.id == pageId }
                        if (state != null) {
                            val elements = effectiveTextElements(state, operationsByPage[pageId].orEmpty())
                            var ops = operationsByPage[pageId].orEmpty()
                            elements.forEach { element ->
                                val base = ops.firstOrNull { it.targetId == element.id } ?: operationFromElement(element)
                                ops = ops.filterNot { it.targetId == element.id } + base.copy(newText = "", isDeleted = true)
                            }
                            mutatePageOperations(pageId, ops)
                        }
                        selectAllPageId = null
                    }) { Text("Delete all", color = Color(0xFFFF6B6B)) }
                    TextButton(onClick = { selectAllPageId = null }) { Text("Cancel", color = EditorMuted) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pageStates.size, key = { pageStates[it].page.id }) { index ->
                    val state = pageStates[index]
                    LaunchedEffect(state.page.id) {
                        if (analysisStarted[state.page.id] != true) analyzePage(state.page.id)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (pageStates.size > 1) {
                            Text(
                                "Page ${index + 1} of ${pageStates.size}",
                                color = EditorMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = if (index == 0) 12.dp else 2.dp, bottom = 5.dp)
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }
                        DocumentEditorPage(
                            state = state,
                            baseBitmap = displayBitmaps[state.page.id],
                            operations = operationsByPage[state.page.id].orEmpty(),
                            selected = selected?.takeIf { it.pageId == state.page.id },
                            selectAll = selectAllPageId == state.page.id,
                            editingElementId = editingElementId?.takeIf { editingPageId == state.page.id },
                            editingDraft = editingDraft,
                            onEditingDraftChange = { editingDraft = it },
                            onCommitEditing = { commitInlineEdit() },
                            onSelectText = { element ->
                                selectAllPageId = null
                                selected = SelectedEditorElement(state.page.id, text = element)
                            },
                            onSelectImage = { image ->
                                selectAllPageId = null
                                selected = SelectedEditorElement(state.page.id, image = image)
                            },
                            onDismissSelection = { selected = null },
                            onCopy = { element ->
                                clipboard.setText(AnnotatedString(element.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { element -> deleteElement(state.page.id, element) },
                            onSelectAll = {
                                selected = null
                                selectAllPageId = state.page.id
                            },
                            onEdit = { element -> beginInlineEdit(state.page.id, element) },
                            onProofread = { element -> runAi(state.page.id, element, false) },
                            onPolish = { element -> runAi(state.page.id, element, true) },
                            onMoveRect = { element, rect, push ->
                                upsertOperation(state.page.id, element, { it.copy(targetNormalizedRect = RectF(rect)) }, push)
                                selected = SelectedEditorElement(state.page.id, text = currentElement(state.page.id, element.id))
                            },
                            onImageInfo = { image ->
                                Toast.makeText(
                                    context,
                                    if (image.protected) "${image.label} is protected from automatic modification" else "${image.label} selected",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            fontMatchTargetId = fontMatchProcessingTargetId?.takeIf { fontMatchProcessingPageId == state.page.id },
                            fontMatchNeighborIds = if (fontMatchProcessingPageId == state.page.id) fontMatchNeighborIds else emptySet()
                        )
                        if (state.analysisError != null) {
                            Text(
                                state.analysisError,
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(if (index == pageStates.lastIndex) 90.dp else 0.dp))
                    }
                }
            }
        }

        // Formatting controls remain in the same full-page editor; no popup editor page.
        val selectedText = selectedTextElement()
        if (selectedText != null && editingElementId == null) {
            Surface(
                color = EditorPopupBg,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val op = operationsByPage[selected!!.pageId].orEmpty().firstOrNull { it.targetId == selectedText.id }
                        ?: operationFromElement(selectedText)
                    IconButton(onClick = { applyFormatting { it.copy(isBold = !it.isBold) } }) {
                        Icon(Icons.Default.FormatBold, "Bold", tint = if (op.isBold) EditorAccent else EditorText)
                    }
                    IconButton(onClick = { applyFormatting { it.copy(isItalic = !it.isItalic) } }) {
                        Icon(Icons.Default.FormatItalic, "Italic", tint = if (op.isItalic) EditorAccent else EditorText)
                    }
                    IconButton(onClick = { applyFormatting { it.copy(isUnderline = !it.isUnderline) } }) {
                        Icon(Icons.Default.FormatUnderlined, "Underline", tint = if (op.isUnderline) EditorAccent else EditorText)
                    }
                    TextButton(onClick = { applyFormatting { it.copy(fontSizeSp = (it.fontSizeSp - 1f).coerceAtLeast(6f)) } }) {
                        Text("A−", color = EditorText)
                    }
                    TextButton(onClick = { applyFormatting { it.copy(fontSizeSp = (it.fontSizeSp + 1f).coerceAtMost(72f)) } }) {
                        Text("A+", color = EditorText)
                    }
                    IconButton(onClick = {
                        applyFormatting {
                            val next = when (it.alignment) { "LEFT" -> "CENTER"; "CENTER" -> "RIGHT"; else -> "LEFT" }
                            it.copy(alignment = next)
                        }
                    }) {
                        val icon = when (op.alignment) {
                            "CENTER" -> Icons.Default.FormatAlignCenter
                            "RIGHT" -> Icons.Default.FormatAlignRight
                            else -> Icons.Default.FormatAlignLeft
                        }
                        Icon(icon, "Alignment", tint = EditorText)
                    }
                    TextButton(onClick = {
                        applyFormatting {
                            val family = when (it.fontFamilyType.uppercase()) {
                                "DEFAULT" -> "SERIF"
                                "SERIF" -> "MONO"
                                "MONO" -> if (com.docscan.util.textedit.BengaliTextSupport.containsBangla(it.newText)) "BENGALI" else "DEFAULT"
                                else -> "DEFAULT"
                            }
                            it.copy(fontFamilyType = family)
                        }
                    }) { Text("Aa", color = EditorAccent, fontWeight = FontWeight.Bold) }
                }
            }
        }

        FloatingActionButton(
            onClick = { addTextToVisiblePage() },
            containerColor = EditorAccent,
            contentColor = Color.Black,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = if (selectedTextElement() != null) 86.dp else 18.dp)
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("T", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Add, "Add text", modifier = Modifier.size(16.dp))
            }
        }

        if (anyAnalyzing || isAiWorking || isSaving || isProcessingTextChange) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isProcessingTextChange) 0.24f else 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xEE1C1E22),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = EditorAccent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            when {
                                isSaving -> "DS AI is saving…"
                                isProcessingTextChange -> "DS AI is processing"
                                isAiWorking -> "DS AI is analyzing"
                                else -> "DS AI is analyzing"
                            },
                            color = EditorText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = EditorPanel,
            title = { Text("Discard changes?", color = EditorText) },
            text = { Text("Your unsaved document edits will be lost.", color = EditorMuted) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onNavigateBack() }) {
                    Text("Discard", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel", color = EditorAccent) } }
        )
    }

    manualEditSheet?.let { sheet ->
        DsAiEditTextSheet(
            initialText = sheet.text,
            geminiAvailable = GeminiAiService.isApiKeyValid(context),
            onDismiss = { manualEditSheet = null },
            onConfirm = { newText, useGemini ->
                applyManualEdit(sheet.pageId, sheet.element, newText, useGemini)
            }
        )
    }

    pendingAi?.let { pending ->
        DsAiSuggestionSheet(
            title = pending.title,
            suggestion = pending.proposedText,
            onDismiss = { pendingAi = null },
            onRegenerate = {
                val polish = pending.title.contains("Polish", ignoreCase = true)
                pendingAi = null
                runAi(pending.pageId, pending.element, polish)
            },
            onReplace = {
                upsertOperation(pending.pageId, pending.element, { it.copy(newText = pending.proposedText, isDeleted = false) })
                pendingAi = null
            }
        )
    }
}

@Composable
private fun DocumentEditorPage(
    state: DocumentEditorPageState,
    baseBitmap: Bitmap?,
    operations: List<EditTextOperation>,
    selected: SelectedEditorElement?,
    selectAll: Boolean,
    editingElementId: String?,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    onCommitEditing: () -> Unit,
    onSelectText: (EditorTextElement) -> Unit,
    onSelectImage: (EditorImageElement) -> Unit,
    onDismissSelection: () -> Unit,
    onCopy: (EditorTextElement) -> Unit,
    onDelete: (EditorTextElement) -> Unit,
    onSelectAll: () -> Unit,
    onEdit: (EditorTextElement) -> Unit,
    onProofread: (EditorTextElement) -> Unit,
    onPolish: (EditorTextElement) -> Unit,
    onMoveRect: (EditorTextElement, RectF, Boolean) -> Unit,
    onImageInfo: (EditorImageElement) -> Unit,
    fontMatchTargetId: String?,
    fontMatchNeighborIds: Set<String>
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val textElements = remember(state.ocrDocument, operations) { effectiveTextElements(state, operations) }
    val imageElements = state.imageElements

    var zoom by remember(state.page.id) { mutableFloatStateOf(1f) }
    var pan by remember(state.page.id) { mutableStateOf(Offset.Zero) }
    var pagePx by remember(state.page.id) { mutableStateOf(IntSize.Zero) }
    var renderedBitmap by remember(state.page.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(baseBitmap, operations) {
        if (baseBitmap == null) {
            renderedBitmap = null
            return@LaunchedEffect
        }
        if (operations.isEmpty()) {
            renderedBitmap = null
            return@LaunchedEffect
        }
        delay(150)
        renderedBitmap = withContext(Dispatchers.Default) { renderOperations(baseBitmap, operations) }
    }

    val aspect = state.displayWidth.toFloat() / state.displayHeight.toFloat().coerceAtLeast(1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        val maxPageWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
                .onSizeChanged { pagePx = it }
                .pointerInput(state.page.id, zoom, pan, textElements, imageElements) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoom > 1.05f) { zoom = 1f; pan = Offset.Zero } else { zoom = 2f }
                        },
                        onTap = { tap ->
                            if (pagePx.width <= 0 || pagePx.height <= 0) return@detectTapGestures
                            val center = Offset(pagePx.width / 2f, pagePx.height / 2f)
                            val content = Offset(
                                center.x + (tap.x - center.x - pan.x) / zoom,
                                center.y + (tap.y - center.y - pan.y) / zoom
                            )
                            val nx = (content.x / pagePx.width.toFloat()).coerceIn(0f, 1f)
                            val ny = (content.y / pagePx.height.toFloat()).coerceIn(0f, 1f)

                            val textHit = textElements
                                .filter { it.normalizedRect.contains(nx, ny) }
                                .minByOrNull { it.normalizedRect.area() }
                            if (textHit != null) {
                                onSelectText(textHit)
                                return@detectTapGestures
                            }
                            val imageHit = imageElements
                                .filter { it.normalizedRect.contains(nx, ny) }
                                .minByOrNull { it.normalizedRect.area() }
                            if (imageHit != null) onSelectImage(imageHit) else onDismissSelection()
                        }
                    )
                }
                .pointerInput(state.page.id, pagePx) {
                    // At fit-to-screen, a one-finger drag belongs to the LazyColumn so
                    // multi-page documents can scroll normally. Pinch (2+ fingers) always
                    // zooms/pans; after zooming in, one finger pans the page.
                    awaitEachGesture {
                        var keepGoing: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            val shouldOwnGesture = pressedCount >= 2 || zoom > 1.01f
                            if (shouldOwnGesture) {
                                val zoomChange = if (pressedCount >= 2) event.calculateZoom() else 1f
                                val panChange = event.calculatePan()
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                zoom = nextZoom
                                pan = if (nextZoom <= 1.01f) Offset.Zero else {
                                    val maxX = pagePx.width * (nextZoom - 1f) * 0.5f
                                    val maxY = pagePx.height * (nextZoom - 1f) * 0.5f
                                    Offset(
                                        (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                        (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            }
                            keepGoing = event.changes.any { it.pressed }
                        } while (keepGoing)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = pan.x,
                        translationY = pan.y,
                        transformOrigin = TransformOrigin.Center
                    )
            ) {
                val bitmapToShow = renderedBitmap ?: baseBitmap
                if (bitmapToShow != null) {
                    Image(
                        bitmap = bitmapToShow.asImageBitmap(),
                        contentDescription = "Document page ${state.pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    AsyncImage(
                        model = File(state.sourcePath),
                        contentDescription = "Document page ${state.pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                if (selectAll) {
                    textElements.forEach { element ->
                        NormalizedSelectionRect(
                            rect = element.normalizedRect,
                            selected = false,
                            faint = true
                        )
                    }
                }

                val selectedText = selected?.text?.let { ref -> textElements.firstOrNull { it.id == ref.id } }
                val selectedImage = selected?.image

                if (selectedText != null) {
                    val isEditing = editingElementId == selectedText.id
                    NormalizedEditableSelection(
                        element = selectedText,
                        zoom = zoom,
                        isEditing = isEditing,
                        editingDraft = editingDraft,
                        onEditingDraftChange = onEditingDraftChange,
                        focusRequester = focusRequester,
                        onCommitEditing = onCommitEditing,
                        onDismissSelection = onDismissSelection,
                        onCopy = { onCopy(selectedText) },
                        onDelete = { onDelete(selectedText) },
                        onSelectAll = onSelectAll,
                        onEdit = { onEdit(selectedText) },
                        onProofread = { onProofread(selectedText) },
                        onPolish = { onPolish(selectedText) },
                        onMoveRect = { rect, push -> onMoveRect(selectedText, rect, push) }
                    )
                    if (isEditing) {
                        LaunchedEffect(selectedText.id) {
                            delay(80)
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    }
                }

                if (selectedImage != null) {
                    NormalizedImageSelection(
                        image = selectedImage,
                        onClick = { onImageInfo(selectedImage) }
                    )
                }

                if (fontMatchTargetId != null) {
                    FontMatchProcessingOverlay(
                        elements = textElements,
                        targetId = fontMatchTargetId,
                        neighborIds = fontMatchNeighborIds
                    )
                }

                if (state.isAnalyzing) {
                    LaserScanOverlay()
                }
            }
        }
    }
}

@Composable
private fun NormalizedSelectionRect(
    rect: RectF,
    selected: Boolean,
    faint: Boolean = false
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Box(
            Modifier
                .offset(x = w * rect.left, y = h * rect.top)
                .size(w * rect.width(), h * rect.height())
                .background(if (faint) EditorCyan.copy(alpha = 0.08f) else Color.Transparent)
                .border(
                    width = if (selected) 1.5.dp else 0.7.dp,
                    color = EditorCyan.copy(alpha = if (faint) 0.22f else 0.95f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
private fun NormalizedEditableSelection(
    element: EditorTextElement,
    zoom: Float,
    isEditing: Boolean,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onCommitEditing: () -> Unit,
    onDismissSelection: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onProofread: () -> Unit,
    onPolish: () -> Unit,
    onMoveRect: (RectF, Boolean) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val rect = element.normalizedRect
        var draftRect by remember(element.id, rect.left, rect.top, rect.right, rect.bottom) { mutableStateOf(RectF(rect)) }

        Box(
            modifier = Modifier
                .offset(x = w * draftRect.left, y = h * draftRect.top)
                .size(w * draftRect.width().coerceAtLeast(0.02f), h * draftRect.height().coerceAtLeast(0.012f))
                .border(1.5.dp, EditorCyan, RoundedCornerShape(2.dp))
                .pointerInput(element.id, zoom) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            val dx = amount.x / size.width.toFloat().coerceAtLeast(1f) * draftRect.width() / zoom.coerceAtLeast(1f)
                            val dy = amount.y / size.height.toFloat().coerceAtLeast(1f) * draftRect.height() / zoom.coerceAtLeast(1f)
                            val width = draftRect.width()
                            val height = draftRect.height()
                            val left = (draftRect.left + dx).coerceIn(0f, 1f - width)
                            val top = (draftRect.top + dy).coerceIn(0f, 1f - height)
                            draftRect = RectF(left, top, left + width, top + height)
                            onMoveRect(draftRect, false)
                        },
                        onDragEnd = { onMoveRect(draftRect, true) }
                    )
                }
        ) {
            if (isEditing) {
                val family = when (element.fontStyle.fontFamilyType.uppercase()) {
                    "SERIF" -> FontFamily.Serif
                    "MONO" -> FontFamily.Monospace
                    else -> FontFamily.SansSerif
                }
                BasicTextField(
                    value = editingDraft,
                    onValueChange = onEditingDraftChange,
                    textStyle = TextStyle(
                        color = element.textColor,
                        fontSize = element.fontSizeSp.coerceIn(6f, 48f).sp,
                        fontWeight = if (element.fontStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (element.fontStyle.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (element.fontStyle.isUnderline) TextDecoration.Underline else TextDecoration.None,
                        fontFamily = family,
                        textAlign = when (element.alignment) {
                            "CENTER" -> TextAlign.Center
                            "RIGHT" -> TextAlign.Right
                            else -> TextAlign.Left
                        }
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitEditing() }),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .background(element.backgroundColor.copy(alpha = 0.92f))
                        .padding(horizontal = 1.dp)
                )
            }

            // Cyan corner handles; bottom-right handle also resizes the selected text box.
            Handle(Modifier.align(Alignment.TopStart))
            Handle(Modifier.align(Alignment.TopEnd))
            Handle(Modifier.align(Alignment.BottomStart))
            Handle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .pointerInput(element.id, zoom) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                val parentW = with(density) { w.toPx() }.coerceAtLeast(1f)
                                val parentH = with(density) { h.toPx() }.coerceAtLeast(1f)
                                val right = (draftRect.right + amount.x / parentW / zoom.coerceAtLeast(1f))
                                    .coerceIn(draftRect.left + 0.02f, 1f)
                                val bottom = (draftRect.bottom + amount.y / parentH / zoom.coerceAtLeast(1f))
                                    .coerceIn(draftRect.top + 0.012f, 1f)
                                draftRect = RectF(draftRect.left, draftRect.top, right, bottom)
                                onMoveRect(draftRect, false)
                            },
                            onDragEnd = { onMoveRect(draftRect, true) }
                        )
                    }
            )

            DropdownMenu(
                expanded = !isEditing,
                onDismissRequest = onDismissSelection,
                containerColor = EditorPopupBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Copy", color = EditorText) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = EditorMuted) },
                    onClick = onCopy
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFFF7777)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF7777)) },
                    onClick = onDelete
                )
                DropdownMenuItem(
                    text = { Text("Select All", color = EditorText) },
                    leadingIcon = { Icon(Icons.Default.SelectAll, null, tint = EditorMuted) },
                    onClick = onSelectAll
                )
                HorizontalDivider(color = EditorPopupDivider)
                DropdownMenuItem(
                    text = { Text("DS AI", color = EditorMuted) },
                    enabled = false,
                    onClick = {}
                )
                DropdownMenuItem(
                    text = { Text("Edit Text", color = EditorAccent, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = EditorAccent) },
                    onClick = onEdit
                )
                DropdownMenuItem(
                    text = { Text("AI Proofread", color = EditorText) },
                    leadingIcon = { Icon(Icons.Default.Psychology, null, tint = EditorCyan) },
                    onClick = onProofread
                )
                DropdownMenuItem(
                    text = { Text("AI Polish", color = EditorText) },
                    leadingIcon = { Icon(Icons.Default.Psychology, null, tint = EditorAccent) },
                    onClick = onPolish
                )
            }
        }
    }
}

@Composable
private fun Handle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = 0.dp, y = 0.dp)
            .size(11.dp)
            .clip(CircleShape)
            .background(EditorCyan)
            .border(1.dp, Color.White, CircleShape)
    )
}

@Composable
private fun NormalizedImageSelection(
    image: EditorImageElement,
    onClick: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val rect = image.normalizedRect
        Box(
            modifier = Modifier
                .offset(x = w * rect.left, y = h * rect.top)
                .size(w * rect.width(), h * rect.height())
                .border(
                    1.5.dp,
                    if (image.protected) Color(0xFFFFB74D) else EditorCyan,
                    RoundedCornerShape(3.dp)
                )
                .background(Color.Transparent)
                .clickable(onClick = onClick)
        ) {
            Surface(
                color = Color(0xDD202227),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    if (image.protected) "${image.label} • Protected" else image.label,
                    color = if (image.protected) Color(0xFFFFCC80) else EditorText,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun FontMatchProcessingOverlay(
    elements: List<EditorTextElement>,
    targetId: String,
    neighborIds: Set<String>
) {
    val infinite = rememberInfiniteTransition(label = "font_match_glow")
    val pulse by infinite.animateFloat(
        initialValue = 0.30f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "font_match_pulse"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val highlighted = elements.filter { it.id == targetId || it.id in neighborIds }
        highlighted.forEach { element ->
            val r = element.normalizedRect
            val isTarget = element.id == targetId
            Box(
                modifier = Modifier
                    .offset(x = w * r.left, y = h * r.top)
                    .size(
                        width = w * r.width().coerceAtLeast(0.018f),
                        height = h * r.height().coerceAtLeast(0.010f)
                    )
                    .background(
                        (if (isTarget) EditorAccent else EditorCyan)
                            .copy(alpha = if (isTarget) 0.18f + 0.18f * pulse else 0.07f + 0.11f * pulse),
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        width = if (isTarget) 2.dp else 1.dp,
                        color = (if (isTarget) EditorAccent else EditorCyan)
                            .copy(alpha = 0.55f + 0.45f * pulse),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }

        val target = elements.firstOrNull { it.id == targetId }
        if (target != null) {
            val r = target.normalizedRect
            Surface(
                color = Color(0xEE15171B),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EditorAccent.copy(alpha = 0.8f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset(
                        x = (w * r.left).coerceAtMost(w - 190.dp),
                        y = (h * (r.top - 0.065f)).coerceAtLeast(6.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = EditorAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "DS AI is processing",
                        color = EditorText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LaserScanOverlay() {
    val infinite = rememberInfiniteTransition(label = "laser_scan")
    val progress = infinite.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laser_line"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .offset(y = h * progress.value)
                .background(EditorAccent.copy(alpha = 0.9f))
        )
    }
}

@Composable
private fun DsAiEditTextSheet(
    initialText: String,
    geminiAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var useGeminiPrecision by remember(geminiAvailable) { mutableStateOf(geminiAvailable) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = EditorPopupBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Edit Text ✨", color = EditorAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("DS AI matches your text to the original font style.", color = EditorMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = EditorInputBg,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EditorCyan)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = EditorText, fontSize = 17.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemini precision match", color = EditorText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (geminiAvailable) "Analyze the original glyph style before rendering" else "Gemini API key is not configured",
                            color = EditorMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    Switch(
                        checked = useGeminiPrecision,
                        onCheckedChange = { if (geminiAvailable) useGeminiPrecision = it },
                        enabled = geminiAvailable
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EditorInputBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel", color = EditorText) }
                    Button(
                        onClick = { onConfirm(text, useGeminiPrecision) },
                        colors = ButtonDefaults.buttonColors(containerColor = EditorAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank()
                    ) { Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun DsAiSuggestionSheet(
    title: String,
    suggestion: String,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onReplace: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = EditorPopupBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("DS $title ✨", color = EditorAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("✕", color = EditorMuted) }
                }
                Text("DS AI-generated content is for reference only. Please review carefully.", color = EditorMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = Color(0xFF1E2025),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EditorCyan)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            if (title.contains("Polish", ignoreCase = true)) "Polish complete" else "Proofreading complete",
                            color = EditorMuted,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(suggestion, color = EditorText, fontSize = 18.sp)
                        Spacer(Modifier.height(32.dp))
                        HorizontalDivider(color = Color(0xFF34373D))
                        Spacer(Modifier.height(8.dp))
                        Text("Powered by DocScanner", color = EditorMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onRegenerate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EditorAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Regenerate", color = EditorAccent) }
                    Button(
                        onClick = onReplace,
                        colors = ButtonDefaults.buttonColors(containerColor = EditorAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Replace", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun effectiveTextElements(
    state: DocumentEditorPageState,
    operations: List<EditTextOperation>
): List<EditorTextElement> {
    val opMap = operations.associateBy { it.targetId }
    val base = state.ocrDocument?.toEditorTextElements(state.pageIndex).orEmpty().mapNotNull { element ->
        val op = opMap[element.id] ?: return@mapNotNull element
        if (op.isDeleted) return@mapNotNull null
        element.copy(
            text = op.newText,
            normalizedRect = RectF(op.targetNormalizedRect),
            fontSizeSp = op.fontSizeSp,
            textColor = op.textColor,
            backgroundColor = if (op.backgroundColor == Color.Transparent) element.backgroundColor else op.backgroundColor,
            fontStyle = FontStyleEstimate(op.isBold, op.isItalic, op.isUnderline, op.fontFamilyType),
            alignment = op.alignment,
            protectedFactualField = element.protectedFactualField || isProtectedDocumentText(op.newText)
        )
    }

    val added = operations.filter { it.targetId.startsWith("added_") && !it.isDeleted }.map { op ->
        virtualElementFromOperation(state, op)
    }
    return base + added
}

private fun virtualElementFromOperation(state: DocumentEditorPageState, op: EditTextOperation): EditorTextElement {
    return EditorTextElement(
        id = op.targetId,
        pageIndex = state.pageIndex,
        text = op.newText,
        normalizedRect = RectF(op.targetNormalizedRect),
        confidence = 1f,
        pageWidth = state.displayWidth,
        pageHeight = state.displayHeight,
        fontSizeSp = op.fontSizeSp,
        textColor = op.textColor,
        backgroundColor = if (op.backgroundColor == Color.Transparent) Color.White else op.backgroundColor,
        fontStyle = FontStyleEstimate(op.isBold, op.isItalic, op.isUnderline, op.fontFamilyType),
        alignment = op.alignment,
        protectedFactualField = false
    )
}

private fun operationFromElement(element: EditorTextElement): EditTextOperation {
    return EditTextOperation(
        targetId = element.id,
        originalText = element.text,
        newText = element.text,
        originalNormalizedRect = RectF(element.normalizedRect),
        targetNormalizedRect = RectF(element.normalizedRect),
        textColor = element.textColor,
        fontSizeSp = element.fontSizeSp,
        isBold = element.fontStyle.isBold,
        isItalic = element.fontStyle.isItalic,
        isUnderline = element.fontStyle.isUnderline,
        fontFamilyType = element.fontStyle.fontFamilyType,
        alignment = element.alignment,
        backgroundColor = Color.Transparent,
        autoInpaintBackground = true
    )
}

private suspend fun renderOperations(source: Bitmap, operations: List<EditTextOperation>): Bitmap {
    if (operations.isEmpty()) return source
    val regions = operations
        .filter { it.originalText.isNotBlank() }
        .map { RectF(it.originalNormalizedRect) }
        .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }
    val base = if (regions.isNotEmpty()) {
        DocumentTextInpainter.inpaintRegions(source, regions)
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val mutable = if (base.isMutable) base else base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    DocumentTextRenderer.renderOperationsOnCanvas(
        canvas = canvas,
        operations = operations,
        bitmapWidth = mutable.width.toFloat(),
        bitmapHeight = mutable.height.toFloat()
    )
    return mutable
}

private fun decodeBounds(path: String): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    return Pair(options.outWidth.coerceAtLeast(1), options.outHeight.coerceAtLeast(1))
}

private fun decodeBitmapForSaving(path: String, maxPixels: Long = 28_000_000L): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample).toLong() > maxPixels) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })
}

private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return source
    val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun cropNormalizedBitmap(source: Bitmap, rect: RectF, paddingRatio: Float): Bitmap? {
    if (source.width <= 1 || source.height <= 1) return null
    val padX = rect.width() * paddingRatio
    val padY = rect.height() * paddingRatio
    val left = ((rect.left - padX).coerceIn(0f, 1f) * source.width).toInt()
    val top = ((rect.top - padY).coerceIn(0f, 1f) * source.height).toInt()
    val right = ((rect.right + padX).coerceIn(0f, 1f) * source.width).toInt()
    val bottom = ((rect.bottom + padY).coerceIn(0f, 1f) * source.height).toInt()
    val width = (right - left).coerceAtLeast(1)
    val height = (bottom - top).coerceAtLeast(1)
    return runCatching { Bitmap.createBitmap(source, left, top, width, height) }.getOrNull()
}

private fun factualCore(value: String): String =
    value.filter { it.isLetterOrDigit() || it == '<' }.lowercase()

private fun structuredValuesPreserved(original: String, candidate: String): Boolean {
    val originalDigits = Regex("[0-9০-৯]+").findAll(original).map { it.value }.toList()
    val candidateDigits = Regex("[0-9০-৯]+").findAll(candidate).map { it.value }.toList()
    if (originalDigits != candidateDigits) return false

    val originalMrz = original.filter { it == '<' }
    val candidateMrz = candidate.filter { it == '<' }
    if (originalMrz.isNotEmpty() && originalMrz.length != candidateMrz.length) return false
    return true
}
