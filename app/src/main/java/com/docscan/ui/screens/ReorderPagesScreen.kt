package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Clean Design Colors matching reference images
private val RefDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF16181A), Color(0xFFF8FAFC))
private val RefCardBg: Color get() = ThemeManager.themeColor(Color(0xFF212328), Color(0xFFFFFFFF))
private val RefCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val RefAccentGreen = Color(0xFF00C48C)
private val RefCheckGreen = Color(0xFF10B981)
private val RefTextMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))
private val RefTextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val RefDeviceBlue = Color(0xFF3B82F6)

/**
 * Root Reorder Pages Screen workflow.
 * Handles both Screen A (Document Selection / Device Import) and Screen B (Hold & Drag 2-Column Grid Reorder).
 */
@Composable
fun ReorderPagesScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onOpenDocumentDetail: (Long) -> Unit = {}
) {
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }

    // If initial document ID was passed, auto-select it
    LaunchedEffect(initialDocId, documents) {
        if (initialDocId != null && initialDocId > 0L) {
            val doc = documents.find { it.id == initialDocId }
            if (doc != null) {
                selectedDocument = doc
            }
        }
    }

    if (selectedDocument == null) {
        // Screen A: Document Picker & Device Import matching reference left side
        ReorderDocumentListScreen(
            documents = documents,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onDocumentSelected = { doc ->
                selectedDocument = doc
            }
        )
    } else {
        // Screen B: 2-Column Grid Hold & Drag Reorder matching reference right side
        DocumentReorderEditorScreen(
            document = selectedDocument!!,
            viewModel = viewModel,
            onNavigateBack = {
                if (initialDocId != null) {
                    onNavigateBack()
                } else {
                    selectedDocument = null
                }
            },
            onSaveSuccess = { savedDocId ->
                onNavigateBack()
            }
        )
    }
}

/**
 * SCREEN A: Reorder Pages Home / Document List (Reference Left Side)
 */
@Composable
fun ReorderDocumentListScreen(
    documents: List<DocumentEntity>,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onDocumentSelected: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()
    var isImportingFromDevice by remember { mutableStateOf(false) }

    // File/Device Import Launcher for PDF & Images
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImportingFromDevice = true
            coroutineScope.launch {
                val importedDoc = viewModel.importDocumentFromDevice(uris, "REORDER_IMPORT")
                withContext(Dispatchers.Main) {
                    isImportingFromDevice = false
                    if (importedDoc != null) {
                        Toast.makeText(context, "Loaded ${importedDoc.pageCount} page(s) from selected file", Toast.LENGTH_SHORT).show()
                        onDocumentSelected(importedDoc)
                    } else {
                        Toast.makeText(context, "Could not open or render the selected document.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Fallback picker for general media/file providers
    val genericFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImportingFromDevice = true
            coroutineScope.launch {
                val importedDoc = viewModel.importDocumentFromDevice(uris, "REORDER_IMPORT")
                withContext(Dispatchers.Main) {
                    isImportingFromDevice = false
                    if (importedDoc != null) {
                        Toast.makeText(context, "Loaded ${importedDoc.pageCount} page(s) from selected file", Toast.LENGTH_SHORT).show()
                        onDocumentSelected(importedDoc)
                    } else {
                        Toast.makeText(context, "Could not open or render the selected document.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (isImportingFromDevice) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (themePalette.isDark) RefCardBg else Color.White,
                border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else Color(0xFFE2E8F0)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = RefAccentGreen,
                        strokeWidth = 3.dp
                    )
                    Column {
                        Text(
                            text = "Importing document...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                        )
                        Text(
                            text = "Extracting pages from PDF/images",
                            fontSize = 13.sp,
                            color = RefTextMuted
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) RefDarkBg else themePalette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR & HEADER SECTION
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("button_back_reorder_list")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }

            // Top right subtle stacked pages graphic
            ReorderHeaderIllustration()
        }

        // TITLE & SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Reorder Pages",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Drag and drop to reorder pages.",
                fontSize = 14.sp,
                color = RefTextMuted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: CREATE OR IMPORT
        Text(
            text = "Create or Import",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = RefTextMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // DEVICE IMPORT CARD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable {
                    try {
                        fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                    } catch (e: Exception) {
                        genericFileLauncher.launch("*/*")
                    }
                }
                .testTag("card_import_device"),
            shape = RoundedCornerShape(12.dp),
            color = if (themePalette.isDark) RefCardBg else themePalette.card,
            border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else themePalette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Blue Square with device icon matching reference
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RefDeviceBlue,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Device",
                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Device",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 2: SELECT FROM THIS APP
        Text(
            text = "Select from This App",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = RefTextMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // DOCUMENT LIST
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = RefTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No documents found",
                        fontSize = 15.sp,
                        color = RefTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan or import files using the Device button above.",
                        fontSize = 13.sp,
                        color = RefTextMuted.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("list_reorder_documents"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    ReorderDocItemRow(
                        doc = doc,
                        isDark = themePalette.isDark,
                        onClick = { onDocumentSelected(doc) }
                    )
                }
            }
        }
    }
}

/**
 * Header decorative illustration matching reference upper-right visual
 */
@Composable
private fun ReorderHeaderIllustration() {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back card (angled)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF334155),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier
                .size(width = 34.dp, height = 44.dp)
                .offset(x = 10.dp, y = (-2).dp)
                .rotate(8f)
        ) {}

        // Middle card
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF64748B)),
            modifier = Modifier
                .size(width = 34.dp, height = 44.dp)
                .offset(x = (-6).dp, y = 2.dp)
                .rotate(-6f)
        ) {}

        // Front active card with subtle lines
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, RefAccentGreen),
            modifier = Modifier
                .size(width = 32.dp, height = 42.dp)
                .offset(x = 0.dp, y = (-2).dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(2.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * Document Row in the Selection List matching reference UI
 */
@Composable
private fun ReorderDocItemRow(
    doc: DocumentEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAt) {
        try {
            dateFormat.format(Date(doc.createdAt)).lowercase()
        } catch (e: Exception) {
            "27/08/2026 1:12 pm"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("reorder_doc_${doc.id}"),
        color = if (isDark) RefCardBg else Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isDark) RefCardBorder else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with document preview
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.size(width = 46.dp, height = 58.dp)
            ) {
                val thumbFile = remember(doc.thumbnailPath) {
                    if (doc.thumbnailPath.isNotBlank()) File(doc.thumbnailPath) else null
                }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = doc.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Document Details: Name, Date, Page Count
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = doc.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) RefTextPrimary else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 13.sp,
                        color = RefTextMuted
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = RefTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${doc.pageCount}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = RefTextMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * SCREEN B: 2-Column Grid Document Reorder Editor (Reference Right Side)
 */
@Composable
fun DocumentReorderEditorScreen(
    document: DocumentEntity,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    var originalPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val pagesList = remember { mutableStateListOf<PageEntity>() }
    val selectedPageIds = remember { mutableStateListOf<Long>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Drag and drop state
    var draggingPageId by remember { mutableStateOf<Long?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Load actual document pages from database/repository
    LaunchedEffect(document.id) {
        isLoading = true
        val loaded = viewModel.getPagesForDocumentDirect(document.id)
        originalPages = loaded
        pagesList.clear()
        pagesList.addAll(loaded)
        // By default select 1st page if available matching reference "1 selected"
        selectedPageIds.clear()
        if (loaded.isNotEmpty()) {
            selectedPageIds.add(loaded[0].id)
        }
        isLoading = false
    }

    val hasUnsavedChanges by remember {
        derivedStateOf {
            if (pagesList.size != originalPages.size) return@derivedStateOf true
            pagesList.map { it.id } != originalPages.map { it.id }
        }
    }

    // Handle Android Back Navigation cleanly
    BackHandler {
        onNavigateBack()
    }

    val allSelected = pagesList.isNotEmpty() && selectedPageIds.size == pagesList.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) RefDarkBg else themePalette.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR: Back arrow, Selection status (e.g. "1 selected"), "Select All"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("button_back_reorder_editor")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }

            // Selection Status (e.g., "1 selected", "0 selected")
            Text(
                text = "${selectedPageIds.size} selected",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                modifier = Modifier.testTag("text_selected_count")
            )

            // Select All / Deselect All Action Button
            TextButton(
                onClick = {
                    if (allSelected) {
                        selectedPageIds.clear()
                    } else {
                        selectedPageIds.clear()
                        selectedPageIds.addAll(pagesList.map { it.id })
                    }
                },
                modifier = Modifier.testTag("button_select_all")
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = RefAccentGreen
                )
            }
        }

        // INSTRUCTION ROW: "ⓘ Hold and drag to reorder"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = RefTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Hold a page, then drag it to the new position",
                fontSize = 13.5.sp,
                color = RefTextMuted,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2-COLUMN PAGE GRID
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading pages...",
                        color = RefTextMuted,
                        fontSize = 14.sp
                    )
                }
            } else if (pagesList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pages found in this document.",
                        color = RefTextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                val gridState = rememberLazyGridState()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("grid_reorder_pages"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = pagesList,
                        key = { _, page -> page.id }
                    ) { index, page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        val isBeingDragged = draggingPageId == page.id

                        ReorderPageCard(
                            page = page,
                            displayIndex = index + 1,
                            isSelected = isSelected,
                            isDragging = isBeingDragged,
                            isDark = themePalette.isDark,
                            dragOffset = if (isBeingDragged) dragOffset else Offset.Zero,
                            onToggleSelect = {
                                if (selectedPageIds.contains(page.id)) {
                                    selectedPageIds.remove(page.id)
                                } else {
                                    selectedPageIds.add(page.id)
                                }
                            },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingPageId = page.id
                                draggingIndex = pagesList.indexOfFirst { it.id == page.id }
                                dragOffset = Offset.Zero
                            },
                            onDragDelta = { delta ->
                                dragOffset += delta

                                val currentIdx = draggingIndex
                                val draggedId = draggingPageId
                                if (currentIdx in pagesList.indices && draggedId != null) {
                                    val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                    val currentInfo = visibleItems.firstOrNull { it.key == draggedId }
                                    if (currentInfo != null) {
                                    val draggedCenterX = currentInfo.offset.x + currentInfo.size.width / 2f + dragOffset.x
                                    val draggedCenterY = currentInfo.offset.y + currentInfo.size.height / 2f + dragOffset.y

                                    // Auto-scroll while dragging close to the viewport edges.
                                    val edgeZone = with(density) { 88.dp.toPx() }
                                    val viewportTop = gridState.layoutInfo.viewportStartOffset.toFloat()
                                    val viewportBottom = gridState.layoutInfo.viewportEndOffset.toFloat()
                                    when {
                                        draggedCenterY < viewportTop + edgeZone -> coroutineScope.launch { gridState.scrollBy(-24.dp.value * density.density) }
                                        draggedCenterY > viewportBottom - edgeZone -> coroutineScope.launch { gridState.scrollBy(24.dp.value * density.density) }
                                    }

                                    val targetInfo = visibleItems
                                        .asSequence()
                                        .filter { it.key != draggedId }
                                        .filter {
                                            draggedCenterX >= it.offset.x &&
                                                draggedCenterX <= it.offset.x + it.size.width &&
                                                draggedCenterY >= it.offset.y &&
                                                draggedCenterY <= it.offset.y + it.size.height
                                        }
                                        .firstOrNull()

                                    val targetId = targetInfo?.key as? Long
                                    val targetIdx = targetId?.let { id -> pagesList.indexOfFirst { it.id == id } } ?: -1
                                    if (targetInfo != null && targetIdx >= 0 && targetIdx != currentIdx) {
                                        // Preserve the card under the finger instead of snapping after each swap.
                                        dragOffset = Offset(
                                            x = dragOffset.x + currentInfo.offset.x - targetInfo.offset.x,
                                            y = dragOffset.y + currentInfo.offset.y - targetInfo.offset.y
                                        )
                                        val movedItem = pagesList.removeAt(currentIdx)
                                        pagesList.add(targetIdx, movedItem)
                                        draggingIndex = targetIdx
                                    }
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingPageId = null
                                draggingIndex = -1
                                dragOffset = Offset.Zero
                            }
                        )
                    }
                }
            }
        }

        // FIXED BOTTOM ACTION BAR (Matching Reference Right Bottom)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_action_bar_reorder"),
            color = if (themePalette.isDark) Color(0xFF0C0E12) else themePalette.bottomBarBg,
            border = BorderStroke(1.dp, if (themePalette.isDark) Color(0xFF1E232E) else themePalette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Large 'X' Cancel Button (Discard & Exit)
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_cancel_reorder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // CENTER: "Reorder Pages" Title
                Text(
                    text = "Reorder Pages",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                    modifier = Modifier.testTag("text_reorder_pages_bottom")
                )

                // RIGHT: Large '✓' Confirm Checkmark Button (Save to DB & Update Home)
                IconButton(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    viewModel.reorderPages(document.id, pagesList.toList())
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Page order saved successfully!", Toast.LENGTH_SHORT).show()
                                        onSaveSuccess(document.id)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "Failed to save order: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_confirm_reorder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm Reorder",
                        tint = if (hasUnsavedChanges) RefAccentGreen else (if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual Page Item in the 2-Column Grid with selection state, badge & 2-digit page numbering
 */
@Composable
private fun ReorderPageCard(
    page: PageEntity,
    displayIndex: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    isDark: Boolean,
    dragOffset: Offset,
    onToggleSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val formattedIndex = remember(displayIndex) {
        String.format(Locale.getDefault(), "%02d", displayIndex)
    }

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 2.dp,
        label = "dragElevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f,
        label = "dragScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .scale(scale)
            .shadow(elevation, shape = RoundedCornerShape(10.dp))
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    }
                )
            }
            .clickable(onClick = onToggleSelect)
            .testTag("page_card_${page.id}")
    ) {
        // Document Page Thumbnail Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) RefCheckGreen else (if (isDark) RefCardBorder else Color(0xFFCBD5E1))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f) // A4 page aspect ratio matching reference
                .clip(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageFile = remember(page.processedImagePath, page.originalImagePath) {
                    val p = File(page.processedImagePath)
                    if (p.exists()) p else File(page.originalImagePath)
                }

                if (imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page $formattedIndex",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Page $formattedIndex",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = if (isDragging) 0.72f else 0.46f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Hold and drag page",
                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // TOP-RIGHT GREEN CHECKMARK BADGE (When selected, matching reference right screenshot)
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RefCheckGreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .testTag("badge_selected_${page.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // PAGE NUMBER PILL (Centered underneath thumbnail: "01", "02", "03", "04")
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) RefAccentGreen else (if (isDark) Color(0xFF1E232B) else Color(0xFFE2E8F0)),
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .testTag("page_number_$formattedIndex")
        ) {
            Text(
                text = formattedIndex,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else (if (isDark) RefTextPrimary else Color(0xFF334155)),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}
