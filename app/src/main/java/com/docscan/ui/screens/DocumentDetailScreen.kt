package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.util.PdfExportConfig
import com.docscan.security.DocumentLockManager
import com.docscan.ui.components.CaseSummaryDialog
import com.docscan.ui.components.CompressDialog
import com.docscan.ui.components.CustomDeleteConfirmationDialog
import com.docscan.ui.components.DocumentShareBottomSheet
import com.docscan.ui.components.LockDocumentDialog
import com.docscan.ui.components.MergeFilesDialog
import com.docscan.ui.components.MoreActionBottomSheet
import com.docscan.ui.components.MoveFolderDialog
import com.docscan.ui.components.PdfExportDialog
import com.docscan.ui.components.ReadModeDialog
import com.docscan.ui.components.SignatureDialog
import com.docscan.ui.components.TagsManagerDialog
import com.docscan.ui.components.TranslateDialog
import com.docscan.ui.components.UnlockDocumentDialog
import com.docscan.ui.components.WatermarkDialog
import com.docscan.ui.components.WordExportDialog
import com.docscan.ui.components.ExcelExportDialog
import com.docscan.ui.components.PdfToImagesDialog
import com.docscan.ui.components.SaveToGalleryConfirmDialog
import com.docscan.data.model.ScanMode
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TopBarBg: Color get() = ThemeManager.themeColor(Color(0xFF1E1E1E), Color(0xFFFFFFFF))
private val CanvasDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF121212), Color(0xFFF8FAFC))
private val BottomBarBg: Color get() = ThemeManager.themeColor(Color(0xFF1E1E1E), Color(0xFFFFFFFF))
private val TealAccent = Color(0xFF00BFA5)
private val ActionIconColor: Color get() = ThemeManager.themeColor(Color(0xFFE2E8F0), Color(0xFF0F172A))
private val DividerColor: Color get() = ThemeManager.themeColor(Color(0xFF2C2C2E), Color(0xFFE2E8F0))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: Long,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCameraForAppend: (Long) -> Unit,
    onNavigateToCrop: () -> Unit,
    onNavigateToDocumentEditText: ((Long) -> Unit)? = null,
    onNavigateToSinglePageEditor: ((Long) -> Unit)? = null,
    onNavigateToSignPdf: ((Long) -> Unit)? = null,
    onNavigateToAddWatermark: ((Long) -> Unit)? = null,
    onNavigateToToWord: ((Long) -> Unit)? = null,
    onNavigateToToExcel: ((Long) -> Unit)? = null,
    onNavigateToExtractText: ((Long) -> Unit)? = null,
    onNavigateToExtractPages: ((Long) -> Unit)? = null,
    onNavigateToTranslate: ((Long) -> Unit)? = null,
    onNavigateToCompress: ((Long) -> Unit)? = null,
    onNavigateToLock: ((Long) -> Unit)? = null,
    onNavigateToMerge: ((Long) -> Unit)? = null,
    onNavigateToManagePages: ((Long) -> Unit)? = null,
    onNavigateToPdfToImages: ((Long) -> Unit)? = null,
    onNavigateToAiChat: ((Long) -> Unit)? = null,
    onNavigateToPdfToLongImage: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(documentId) {
        viewModel.loadDocumentDetails(documentId)
    }

    val document by viewModel.activeDocument.collectAsStateWithLifecycle()
    val pages by viewModel.activeDocumentPages.collectAsStateWithLifecycle()
    val allDocuments by viewModel.documentsList.collectAsState(initial = emptyList())

    // View mode: true = Grid view (matching user photo), false = Full document page preview (swipe/zoom)
    var isGridView by remember { mutableStateOf(true) }

    // Holds the page index the user tapped in grid view. It's consumed by the
    // LaunchedEffect below only once the HorizontalPager has actually entered
    // composition — see comments near that LaunchedEffect and near the card's
    // onClick handler for why this two-step approach is required.
    var pendingScrollIndex by remember { mutableStateOf<Int?>(null) }

    // Dialog and Sheet states
    var showModernShareSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var showSendToPcDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showReadModeDialog by remember { mutableStateOf(false) }
    var showCaseSummaryDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showWordExportDialog by remember { mutableStateOf(false) }
    var showExcelExportDialog by remember { mutableStateOf(false) }
    var showImagesExportDialog by remember { mutableStateOf(false) }
    var showSaveToGalleryConfirmDialog by remember { mutableStateOf(false) }

    var selectedPageIndexForEdit by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<PageEntity?>(null) }
    var pageMenuExpandedIndex by remember { mutableIntStateOf(-1) }

    // Hold & drag reorder state for the multi-page grid view.
    // orderedPages mirrors `pages` but is updated live while a card is being
    // dragged; it re-syncs whenever the underlying page list changes (e.g.
    // after navigating away and back, or after a delete/rotate).
    var orderedPages by remember(pages) { mutableStateOf(pages) }
    var draggingPageIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var gridItemSize by remember { mutableStateOf(IntSize.Zero) }

    // Append one page through our own camera; CropAppend handles document persistence.
    fun launchNativeAppend() {
        viewModel.prepareNativeDocumentScan(ScanMode.SINGLE)
        onNavigateToCameraForAppend(documentId)
    }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    val loaded = FileUtils.loadBitmapsFromUri(context, uri)
                    bitmaps.addAll(loaded)
                }
                if (bitmaps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        bitmaps.forEach { bmp ->
                            viewModel.onImageCaptured(bmp) {
                                onNavigateToCrop()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load selected file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    BackHandler {
        if (!isGridView && pages.size > 1) {
            isGridView = true
        } else {
            onNavigateBack()
        }
    }

    val doc = document ?: run {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading Document...", color = ActionIconColor)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size.coerceAtLeast(1) }
    )

    // Once the pager leaves grid mode and is actually composed, consume any
    // pending scroll target requested from the grid view. We can't call
    // pagerState.scrollToPage() directly inside the grid card's onClick,
    // because HorizontalPager isn't composed at all while isGridView is
    // still true — that suspend call would just hang forever and
    // isGridView would never flip to false, so the page never opened.
    LaunchedEffect(isGridView, pendingScrollIndex, pages.size) {
        if (!isGridView) {
            pendingScrollIndex?.let { targetIndex ->
                if (targetIndex in pages.indices) {
                    pagerState.scrollToPage(targetIndex)
                }
                pendingScrollIndex = null
            }
        }
    }

    val aggregatedOcrText = remember(pages) {
        pages.mapNotNull { it.extractedText }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasDarkBg)
    ) {
        // ==================== TOP APP BAR ====================
        Surface(
            color = TopBarBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Arrow
                    IconButton(
                        onClick = {
                            if (!isGridView && pages.size > 1) isGridView = true else onNavigateBack()
                        },
                        modifier = Modifier.testTag("btn_document_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ActionIconColor
                        )
                    }

                    // Filename + Edit Pencil Icon + Tags Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Editable Filename
                        Text(
                            text = doc.title,
                            color = ActionIconColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showRenameDialog = true }
                                .testTag("document_title_editable")
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { showRenameDialog = true }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Tags + Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ThemeManager.themeColor(Color(0xFF2A2A2E), Color(0xFFF1F5F9)),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, TealAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { showTagsDialog = true }
                                .testTag("btn_document_tags")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (doc.tags.isNotBlank()) "Tags: ${doc.tags.split(',').firstOrNull()?.trim() ?: ""}" else "Tags +",
                                    color = TealAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Top Right Actions: Grid/Slide Toggle & 3-dots Menu
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onNavigateToAiChat?.invoke(documentId) },
                            modifier = Modifier.testTag("btn_document_ask_ai")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Ask AI about this document",
                                tint = TealAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showPdfDialog = true },
                            modifier = Modifier.testTag("btn_document_save_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Save / Export PDF",
                                tint = ActionIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.testTag("btn_toggle_grid_view")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewCarousel else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Slide Preview" else "Grid View",
                                tint = ActionIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showMoreSheet = true },
                            modifier = Modifier.testTag("btn_document_top_more")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = ActionIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.8.dp,
            color = DividerColor
        )

        // ==================== DOCUMENT CONTENT AREA ====================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CanvasDarkBg)
        ) {
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No pages in this document", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { launchNativeAppend() },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Page", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!isGridView) {
                // FULL DOCUMENT PREVIEW MODE (Horizontal Swipe & Pinch to Zoom)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page != null) {
                        ZoomablePageCanvas(
                            imagePath = page.processedImagePath,
                            pageIndex = pageIndex,
                            extractedText = page.extractedText
                        )
                    }
                }

                // Dynamic Page Indicator (e.g., "1/4")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xCC1E1E1E),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF3F3F46)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${pages.size}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            } else {
                // MULTI-PAGE GRID VIEW MODE (Matching User's Photo)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(orderedPages, key = { _, p -> p.id }) { index, page ->
                        val isDragging = draggingPageIndex == index
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    if (gridItemSize == IntSize.Zero) gridItemSize = coords.size
                                }
                                // Long-press anywhere on a page card, then drag it to reorder.
                                // The entire card is the drag target so it is easy to use on phones.
                                .pointerInput(page.id, orderedPages.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            // Resolve by stable page id instead of a captured grid index,
                                            // so a page remains draggable correctly after previous reorders.
                                            draggingPageIndex = orderedPages.indexOfFirst { it.id == page.id }
                                                .takeIf { it >= 0 }
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount

                                            val currentIndex = draggingPageIndex
                                                ?: return@detectDragGesturesAfterLongPress
                                            val itemWidth = gridItemSize.width.toFloat()
                                            val itemHeight = gridItemSize.height.toFloat()
                                            if (itemWidth <= 0f || itemHeight <= 0f) {
                                                return@detectDragGesturesAfterLongPress
                                            }

                                            val colShift = when {
                                                dragOffset.x > itemWidth * 0.5f -> 1
                                                dragOffset.x < -itemWidth * 0.5f -> -1
                                                else -> 0
                                            }
                                            val rowShift = when {
                                                dragOffset.y > itemHeight * 0.5f -> 1
                                                dragOffset.y < -itemHeight * 0.5f -> -1
                                                else -> 0
                                            }

                                            if (colShift != 0 || rowShift != 0) {
                                                val targetIndex = (currentIndex + rowShift * 2 + colShift)
                                                    .coerceIn(0, orderedPages.lastIndex)
                                                if (targetIndex != currentIndex) {
                                                    orderedPages = orderedPages.toMutableList().apply {
                                                        add(targetIndex, removeAt(currentIndex))
                                                    }
                                                    draggingPageIndex = targetIndex
                                                    dragOffset = Offset(
                                                        dragOffset.x - colShift * itemWidth,
                                                        dragOffset.y - rowShift * itemHeight
                                                    )
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingPageIndex = null
                                            dragOffset = Offset.Zero
                                            viewModel.reorderPages(doc.id, orderedPages)
                                        },
                                        onDragCancel = {
                                            draggingPageIndex = null
                                            dragOffset = Offset.Zero
                                            orderedPages = pages
                                        }
                                    )
                                }
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 14f
                                        alpha = 0.96f
                                    }
                                }
                                .zIndex(if (isDragging) 2f else 0f)
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clickable {
                                        // Just record which page was tapped and flip to
                                        // pager mode immediately. The actual scroll happens
                                        // in the LaunchedEffect above, once the
                                        // HorizontalPager has actually been composed.
                                        pendingScrollIndex = index
                                        isGridView = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333338))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    AsyncImage(
                                        model = File(page.processedImagePath),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Visual drag affordance. Long-press anywhere on the page
                                    // card itself to start reordering.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(if (isDragging) TealAccent else Color(0x99000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragIndicator,
                                            contentDescription = "Hold and drag page to reorder",
                                            tint = if (isDragging) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 3 Dots Menu Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { pageMenuExpandedIndex = index },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x99000000))
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Page Options",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = pageMenuExpandedIndex == index,
                                            onDismissRequest = { pageMenuExpandedIndex = -1 },
                                            shape = RoundedCornerShape(14.dp),
                                            containerColor = Color(0xFF252529),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 12.dp,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                Color(0xFF3A3A40)
                                            )
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Page", fontWeight = FontWeight.Bold, color = TealAccent) },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealAccent) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    if (onNavigateToSinglePageEditor != null) {
                                                        onNavigateToSinglePageEditor(page.id)
                                                    } else {
                                                        viewModel.prepareExistingDocForCrop(doc, index) {
                                                            onNavigateToCrop()
                                                        }
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Edit Text", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, tint = TealAccent) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    onNavigateToDocumentEditText?.invoke(doc.id)
                                                        ?: onNavigateToSinglePageEditor?.invoke(page.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Rotate 90°", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null, tint = Color(0xFF94A3B8)) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    viewModel.rotatePageBy90(page)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate Page", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF94A3B8)) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    viewModel.duplicatePage(page)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Extract Pages", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.ContentCut,
                                                        contentDescription = null,
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    if (onNavigateToExtractPages != null) {
                                                        onNavigateToExtractPages(documentId)
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Extract Pages is not available from this screen",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Save to Gallery", color = TealAccent, fontWeight = FontWeight.SemiBold) },
                                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = TealAccent) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    viewModel.saveSinglePageToGallery(
                                                        page = page,
                                                        docTitle = doc.title,
                                                        pageIndex = index + 1
                                                    )
                                                }
                                            )
                                            // Page actions divider 1
                                            HorizontalDivider(
                                                color = Color(0xFF3A3A40),
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add Signature", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, tint = Color(0xFF94A3B8)) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    selectedPageIndexForEdit = index
                                                    if (onNavigateToSignPdf != null) {
                                                        // Use the full professional signature editor rather than the
                                                        // small legacy dialog. The selected page remains available in
                                                        // state for future page-targeted placement.
                                                        viewModel.selectedPreviewCardIndex.value = index
                                                        onNavigateToSignPdf(documentId)
                                                    } else {
                                                        showSignatureDialog = true
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add Watermark", color = Color(0xFFE5E7EB)) },
                                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF94A3B8)) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    selectedPageIndexForEdit = index
                                                    if (onNavigateToAddWatermark != null) {
                                                        viewModel.selectedPreviewCardIndex.value = index
                                                        onNavigateToAddWatermark(documentId)
                                                    } else {
                                                        showWatermarkDialog = true
                                                    }
                                                }
                                            )
                                            // Page actions divider 2
                                            HorizontalDivider(
                                                color = Color(0xFF3A3A40),
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Page", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    pageMenuExpandedIndex = -1
                                                    pageToDelete = page
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 2-digit zero-padded Page Number below card (e.g. 01, 02, 03, 04)
                            Text(
                                text = String.format(Locale.getDefault(), "%02d", index + 1),
                                color = Color(0xFFD4D4D8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Add Page Card
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF18181B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F46)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clickable { launchNativeAppend() }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(TealAccent.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = TealAccent, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Add Page", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "+",
                                color = Color(0xFF71717A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.8.dp,
            color = DividerColor
        )

        // ==================== BOTTOM ACTION BAR ====================
        Surface(
            color = BottomBarBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarItem(
                    label = "Add",
                    icon = Icons.Default.Add,
                    testTag = "btn_action_add",
                    onClick = { launchNativeAppend() }
                )

                BottomBarItem(
                    label = "Edit",
                    icon = Icons.Default.Edit,
                    testTag = "btn_action_edit",
                    onClick = {
                        // Preserve the app's original hierarchy:
                        // Document Detail -> Edit -> editor tools -> Edit Text.
                        // The dedicated full-page Edit Text engine remains available
                        // from the editor's Edit Text action and other existing routes.
                        val activeIndex = if (isGridView) 0 else pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        val activePage = pages.getOrNull(activeIndex)
                        if (activePage != null && onNavigateToSinglePageEditor != null) {
                            onNavigateToSinglePageEditor(activePage.id)
                        } else {
                            viewModel.prepareExistingDocForCrop(doc, activeIndex) {
                                onNavigateToCrop()
                            }
                        }
                    }
                )

                BottomBarItem(
                    label = "Share",
                    icon = Icons.Default.Share,
                    testTag = "btn_action_share",
                    onClick = { showModernShareSheet = true }
                )

                BottomBarItem(
                    label = "To Word",
                    icon = Icons.Default.Description,
                    testTag = "btn_action_to_word",
                    hasHotBadge = true,
                    onClick = {
                        if (onNavigateToToWord != null) {
                            onNavigateToToWord(documentId)
                        } else {
                            showWordExportDialog = true
                        }
                    }
                )

                BottomBarItem(
                    label = "Save to Gallery",
                    icon = Icons.Default.Download,
                    testTag = "btn_action_save_to_gallery",
                    onClick = {
                        val activeIndex = if (isGridView) 0 else pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        val activePage = pages.getOrNull(activeIndex)
                        if (activePage != null) {
                            viewModel.saveSinglePageToGallery(
                                page = activePage,
                                docTitle = doc.title,
                                pageIndex = activeIndex + 1
                            )
                        } else {
                            viewModel.saveDocumentToGallery(doc)
                        }
                    }
                )
            }
        }
    }

    // ==================== MORE ACTION BOTTOM SHEET ====================
    if (showMoreSheet) {
        MoreActionBottomSheet(
            onDismiss = { showMoreSheet = false },
            onSignClick = {
                if (onNavigateToSignPdf != null) {
                    onNavigateToSignPdf(documentId)
                } else {
                    selectedPageIndexForEdit = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    showSignatureDialog = true
                }
            },
            onWatermarkClick = {
                if (onNavigateToAddWatermark != null) {
                    onNavigateToAddWatermark(documentId)
                } else {
                    selectedPageIndexForEdit = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    showWatermarkDialog = true
                }
            },
            onSendToPcClick = { showSendToPcDialog = true },
            onPdfToImagesClick = {
                if (onNavigateToPdfToImages != null) {
                    onNavigateToPdfToImages(documentId)
                } else {
                    showImagesExportDialog = true
                }
            },
            onPrintClick = { viewModel.printDocumentPdf(context, doc) },
            onToWordClick = {
                if (onNavigateToToWord != null) {
                    onNavigateToToWord(documentId)
                } else {
                    showWordExportDialog = true
                }
            },
            onToExcelClick = {
                if (onNavigateToToExcel != null) {
                    onNavigateToToExcel(documentId)
                } else {
                    showExcelExportDialog = true
                }
            },
            onExtractTextClick = {
                if (onNavigateToExtractText != null) {
                    onNavigateToExtractText(documentId)
                } else {
                    showOcrDialog = true
                }
            },
            onTranslateClick = {
                if (onNavigateToTranslate != null) {
                    onNavigateToTranslate(documentId)
                } else {
                    showTranslateDialog = true
                }
            },
            onCompressClick = {
                if (onNavigateToCompress != null) {
                    onNavigateToCompress(documentId)
                } else {
                    showCompressDialog = true
                }
            },
            onReadModeClick = { showReadModeDialog = true },
            onCaseSummaryClick = { showCaseSummaryDialog = true },
            onManagePagesClick = {
                if (onNavigateToManagePages != null) {
                    onNavigateToManagePages(documentId)
                } else {
                    isGridView = true
                }
            },
            onLockClick = {
                if (onNavigateToLock != null) {
                    onNavigateToLock(documentId)
                } else {
                    showLockDialog = true
                }
            },
            onMergeFilesClick = {
                if (onNavigateToMerge != null) {
                    onNavigateToMerge(documentId)
                } else {
                    showMergeDialog = true
                }
            },
            onCopyMoveClick = { showMoveFolderDialog = true },
            onEmailToMyselfClick = { viewModel.emailPdfToMyself(doc) },
            onSaveToDevicePdfClick = { showPdfDialog = true },
            onSaveToGalleryClick = { showSaveToGalleryConfirmDialog = true },
            onDeleteClick = { showDeleteConfirm = true }
        )
    }

    // ==================== ALL DIALOGS & OVERLAYS ====================

    // Tags Manager Dialog
    if (showTagsDialog) {
        TagsManagerDialog(
            initialTags = doc.tags,
            onDismiss = { showTagsDialog = false },
            onSaveTags = { newTags ->
                viewModel.updateDocumentTags(doc, newTags)
            }
        )
    }

    // Send To PC Dialog
    if (showSendToPcDialog) {
        com.docscan.ui.components.SendToPcDialog(
            docTitle = doc.title,
            onDismiss = { showSendToPcDialog = false }
        )
    }

    // Modern Custom Share Bottom Sheet (matching user screenshot)
    if (showModernShareSheet && doc != null) {
        DocumentShareBottomSheet(
            document = doc,
            pages = pages,
            onDismiss = { showModernShareSheet = false },
            onNavigateToCompress = { docId ->
                onNavigateToCompress?.invoke(docId)
            },
            onNavigateToToWord = { docId ->
                onNavigateToToWord?.invoke(docId)
            },
            onNavigateToPdfToLongImage = { docId ->
                onNavigateToPdfToLongImage?.invoke(docId)
            }
        )
    }

    // Translate Dialog
    if (showTranslateDialog) {
        TranslateDialog(
            extractedText = aggregatedOcrText,
            onDismiss = { showTranslateDialog = false }
        )
    }

    // Compress Dialog
    if (showCompressDialog) {
        CompressDialog(
            doc = doc,
            onDismiss = { showCompressDialog = false },
            onCompressAndShare = { config ->
                viewModel.exportAndSharePdf(doc, config)
            }
        )
    }

    // Read Mode Dialog
    if (showReadModeDialog) {
        ReadModeDialog(
            docTitle = doc.title,
            extractedText = aggregatedOcrText,
            onDismiss = { showReadModeDialog = false }
        )
    }

    // Case Summary Dialog
    if (showCaseSummaryDialog) {
        CaseSummaryDialog(
            docTitle = doc.title,
            extractedText = aggregatedOcrText,
            onDismiss = { showCaseSummaryDialog = false }
        )
    }

    // Merge Files Dialog
    if (showMergeDialog) {
        MergeFilesDialog(
            currentDocId = doc.id,
            allDocuments = allDocuments,
            onDismiss = { showMergeDialog = false },
            onMergeWith = { sourceId ->
                viewModel.mergeDocuments(doc, sourceId)
            }
        )
    }

    // Move Folder Dialog
    if (showMoveFolderDialog) {
        MoveFolderDialog(
            currentFolder = doc.folder,
            onDismiss = { showMoveFolderDialog = false },
            onMove = { newFolder ->
                viewModel.moveDocumentToFolder(doc, newFolder)
            }
        )
    }

    // Session unlock state if document is password protected
    var isUnlockedInScreen by remember { mutableStateOf(false) }

    // Lock Document Dialog
    if (showLockDialog) {
        LockDocumentDialog(
            docTitle = doc.title,
            docId = doc.id,
            onDismiss = { showLockDialog = false },
            onLockChanged = {
                // Refresh lock status
            }
        )
    }

    // Guard screen with Unlock dialog if locked and not yet unlocked
    if (!isUnlockedInScreen && DocumentLockManager.isLockedAndGuarded(context, doc.id)) {
        UnlockDocumentDialog(
            document = doc,
            onDismiss = {
                onNavigateBack()
            },
            onUnlockSuccess = {
                isUnlockedInScreen = true
            }
        )
    }

    // Watermark Dialog
    if (showWatermarkDialog) {
        val targetPage = pages.getOrNull(selectedPageIndexForEdit) ?: pages.firstOrNull()
        targetPage?.let { p ->
            WatermarkDialog(
                initialText = p.watermarkText ?: "",
                onDismiss = { showWatermarkDialog = false },
                onApplyWatermark = { text, opacity, colorLong ->
                    viewModel.applyWatermarkToPage(p, text, opacity, colorLong)
                    showWatermarkDialog = false
                }
            )
        }
    }

    // Signature Dialog
    if (showSignatureDialog) {
        val targetPage = pages.getOrNull(selectedPageIndexForEdit) ?: pages.firstOrNull()
        targetPage?.let { p ->
            SignatureDialog(
                onDismiss = { showSignatureDialog = false },
                onSignatureSaved = { sigBitmap ->
                    viewModel.applySignatureToPage(p, sigBitmap)
                    showSignatureDialog = false
                }
            )
        }
    }

    // PDF Export Dialog
    if (showPdfDialog) {
        PdfExportDialog(
            initialTitle = doc.title,
            pageCount = pages.size,
            onDismiss = { showPdfDialog = false },
            onExportAndSaveToDevice = { config ->
                viewModel.saveDocumentPdfToDevice(doc, config)
                showPdfDialog = false
            },
            onExportAndShare = { config ->
                viewModel.exportAndSharePdf(doc, config)
                showPdfDialog = false
            },
            onExportAndOpen = { config ->
                viewModel.exportAndOpenPdf(doc, config)
                showPdfDialog = false
            }
        )
    }

    // Word Export Dialog
    if (showWordExportDialog) {
        WordExportDialog(
            docTitle = doc.title,
            pageCount = pages.size,
            onDismiss = { showWordExportDialog = false },
            onShareDocx = { config ->
                viewModel.exportAndShareDocx(doc, config)
            },
            onOpenDocx = { config ->
                viewModel.exportAndOpenDocx(doc, config)
            }
        )
    }

    // Excel Export Dialog
    if (showExcelExportDialog) {
        ExcelExportDialog(
            docTitle = doc.title,
            onDismiss = { showExcelExportDialog = false },
            onShareExcel = {
                viewModel.exportAndShareXlsx(doc)
            },
            onOpenExcel = {
                viewModel.exportAndOpenXlsx(doc)
            }
        )
    }

    // PDF to Images Dialog
    if (showImagesExportDialog) {
        PdfToImagesDialog(
            docTitle = doc.title,
            pageCount = pages.size,
            onDismiss = { showImagesExportDialog = false },
            onShareImages = {
                viewModel.exportAllPagesAsImages(doc)
            },
            onSaveToGallery = {
                viewModel.saveDocumentToGallery(doc)
            }
        )
    }

    // Save To Gallery Confirmation Dialog
    if (showSaveToGalleryConfirmDialog) {
        SaveToGalleryConfirmDialog(
            docTitle = doc.title,
            pageCount = pages.size,
            onDismiss = { showSaveToGalleryConfirmDialog = false },
            onConfirmSave = {
                viewModel.saveDocumentToGallery(doc)
            }
        )
    }

    // OCR Extracted Text Dialog
    if (showOcrDialog) {
        var editText by remember { mutableStateOf(aggregatedOcrText) }

        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            containerColor = Color(0xFF242426),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = TealAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OCR Extracted Text", color = Color.White)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Extracted on-device with ML Kit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (aggregatedOcrText.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No text detected yet on pages.", color = Color.Gray)
                        }
                    } else {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = Color(0xFF454545)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (aggregatedOcrText.isNotBlank()) {
                        Button(
                            onClick = {
                                showOcrDialog = false
                                viewModel.exportAndShareDocx(doc)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Word (.docx)")
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(editText))
                                Toast.makeText(context, "Copied OCR text", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showOcrDialog = false }) {
                        Text("Close", color = Color.Gray)
                    }
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Color(0xFF242426),
            title = { Text("Rename Document", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color(0xFF454545)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.renameDocument(doc, newTitle.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Delete Document Confirmation (Using our custom dark & teal UI dialog)
    if (showDeleteConfirm) {
        CustomDeleteConfirmationDialog(
            title = "Delete",
            message = "This will permanently delete the selected document and all its pages.",
            confirmButtonText = "Delete",
            cancelButtonText = "Cancel",
            onConfirm = {
                viewModel.deleteDocument(doc)
                showDeleteConfirm = false
                onNavigateBack()
            },
            onDismiss = {
                showDeleteConfirm = false
            }
        )
    }

    // Delete Single Page Confirmation
    pageToDelete?.let { page ->
        CustomDeleteConfirmationDialog(
            title = "Delete",
            message = "This will permanently delete page ${page.pageNumber}.",
            confirmButtonText = "Delete",
            cancelButtonText = "Cancel",
            onConfirm = {
                viewModel.deletePage(page)
                pageToDelete = null
            },
            onDismiss = {
                pageToDelete = null
            }
        )
    }
}

// ==================== ZOOMABLE PAGE CANVAS ====================
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePageCanvas(
    imagePath: String,
    pageIndex: Int,
    extractedText: String?
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom when page changes
    LaunchedEffect(imagePath) {
        scale = 1f
        offset = Offset.Zero
    }

    // Let HorizontalPager handle a one-finger swipe at normal zoom. When zoomed,
    // transformable handles pan; two fingers can always pinch in/out.
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (nextScale > 1.01f) {
            val maxX = viewportSize.width * (nextScale - 1f) / 2f
            val maxY = viewportSize.height * (nextScale - 1f) / 2f
            offset = Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY)
            )
        } else {
            offset = Offset.Zero
        }
        scale = nextScale
    }

    // Inspect image dimensions quickly without allocating bitmap pixels
    val imageAspect = remember(imagePath) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() / options.outHeight.toFloat()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .onSizeChanged { viewportSize = it },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth.value
        val containerHeight = maxHeight.value
        val containerAspect = if (containerHeight > 0f) containerWidth / containerHeight else 1f

        // Document container sizing: perfectly scale to maximum available bounds
        // while strictly preserving original aspect ratio
        val documentModifier = when {
            imageAspect != null && imageAspect > containerAspect -> {
                // Landscape / Wider than available area -> span available width, scale height
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspect)
            }
            imageAspect != null -> {
                // Portrait / Taller than available area -> span available height, scale width
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(imageAspect)
            }
            else -> {
                Modifier.fillMaxSize()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imagePath) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (targetScale - 1f)) / 2f
                                val focusX = (size.width / 2f - tapOffset.x) * (targetScale - 1f)
                                val focusY = (size.height / 2f - tapOffset.y) * (targetScale - 1f)
                                offset = Offset(
                                    focusX.coerceIn(-maxOffsetX, maxOffsetX),
                                    focusY.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                scale = targetScale
                            }
                        }
                    )
                }
                .transformable(
                    state = transformState,
                    canPan = { scale > 1.01f }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Document Canvas Sheet: dynamically matching the image bounds
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = documentModifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "Document Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    testTag: String,
    hasHotBadge: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ActionIconColor,
                modifier = Modifier.size(24.dp)
            )
            if (hasHotBadge) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFEF4444),
                    modifier = Modifier
                        .offset(x = 10.dp, y = (-4).dp)
                ) {
                    Text(
                        text = "HOT",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = ActionIconColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}