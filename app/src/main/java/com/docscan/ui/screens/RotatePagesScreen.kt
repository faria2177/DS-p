package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import com.docscan.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Design Colors matching reference images
private val RefDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF16181A), Color(0xFFF8FAFC))
private val RefCardBg: Color get() = ThemeManager.themeColor(Color(0xFF212328), Color(0xFFFFFFFF))
private val RefCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val RefAccentGreen = Color(0xFF00C48C)
private val RefTextMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))
private val RefTextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val RefDeviceBlue = Color(0xFF3B82F6)

/**
 * Root Rotate PDF / Pages Screen.
 * Handles both Screen A (Document Selection / Device Import) and Screen B (Interactive Page Rotation Grid).
 */
@Composable
fun RotatePagesScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onOpenDocumentDetail: (Long) -> Unit = {}
) {
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(initialDocId, documents) {
        if (initialDocId != null && initialDocId > 0L) {
            val doc = documents.find { it.id == initialDocId }
            if (doc != null) {
                selectedDocument = doc
            }
        }
    }

    if (selectedDocument == null) {
        RotateDocumentListScreen(
            documents = documents,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onDocumentSelected = { doc ->
                selectedDocument = doc
            }
        )
    } else {
        DocumentRotateEditorScreen(
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
                onOpenDocumentDetail(savedDocId)
            }
        )
    }
}

/**
 * SCREEN A: Rotate Pages Document List & Device Import
 * Directly replicates the provided screenshot aesthetic.
 */
@Composable
fun RotateDocumentListScreen(
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
                val importedDoc = viewModel.importDocumentFromDevice(uris, "ROTATE_IMPORT")
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

    val genericFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImportingFromDevice = true
            coroutineScope.launch {
                val importedDoc = viewModel.importDocumentFromDevice(uris, "ROTATE_IMPORT")
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
        Dialog(onDismissRequest = { }) {
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
                    CircularProgressIndicator(
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
                    .testTag("button_back_rotate_list")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }

            // Top right subtle stacked pages graphic
            RotateHeaderIllustration()
        }

        // TITLE & SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Rotate Pages",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a PDF or document to rotate pages.",
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
                .testTag("card_import_device_rotate"),
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
                    .testTag("list_rotate_documents"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    RotateDocItemRow(
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
 * Header decorative illustration
 */
@Composable
private fun RotateHeaderIllustration() {
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
 * Document Row in the Selection List matching reference screenshot UI
 */
@Composable
private fun RotateDocItemRow(
    doc: DocumentEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAt) {
        try {
            dateFormat.format(Date(doc.createdAt)).lowercase()
        } catch (e: Exception) {
            "02/09/2026 5:43 pm"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("rotate_doc_${doc.id}"),
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

            // Title, Date and Page count
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = doc.title.ifBlank { "Untitled Document" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) RefTextPrimary else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 13.sp,
                        color = RefTextMuted
                    )

                    // Page count badge
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
 * SCREEN B: 2-Column Grid Document Rotate Editor
 * Allows rotating individual pages, rotating selected pages, or rotating all pages.
 */
@Composable
fun DocumentRotateEditorScreen(
    document: DocumentEntity,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    var pagesList by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val selectedPageIds = remember { mutableStateListOf<Long>() }
    val pageRotations = remember { mutableStateMapOf<Long, Int>() } // pageId -> extra rotation degrees
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedPdfFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(document.id) {
        isLoading = true
        val loaded = viewModel.getPagesForDocumentDirect(document.id)
        pagesList = loaded
        selectedPageIds.clear()
        pageRotations.clear()
        // Initialize 0 rotation for each page
        loaded.forEach { page ->
            pageRotations[page.id] = 0
        }
        isLoading = false
    }

    val allSelected = pagesList.isNotEmpty() && selectedPageIds.size == pagesList.size
    val hasRotations = pageRotations.values.any { it % 360 != 0 }

    // Helper functions for rotating
    fun rotateSelectedOrAll(degrees: Int) {
        val targets = if (selectedPageIds.isNotEmpty()) {
            selectedPageIds.toList()
        } else {
            pagesList.map { it.id }
        }
        targets.forEach { pageId ->
            val current = pageRotations[pageId] ?: 0
            pageRotations[pageId] = (current + degrees) % 360
        }
    }

    fun rotateSinglePage(pageId: Long, degrees: Int) {
        val current = pageRotations[pageId] ?: 0
        pageRotations[pageId] = (current + degrees) % 360
    }

    fun resetAllRotations() {
        pagesList.forEach { page ->
            pageRotations[page.id] = 0
        }
    }

    BackHandler {
        onNavigateBack()
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onSaveSuccess(document.id)
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = RefAccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Pages Rotated Successfully",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "The pages in '${document.title}' have been rotated and updated in the PDF document.",
                    fontSize = 14.sp,
                    color = RefTextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onSaveSuccess(document.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RefAccentGreen)
                ) {
                    Text("View Document", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                if (savedPdfFile != null) {
                    OutlinedButton(
                        onClick = {
                            PdfExporter.sharePdf(context, savedPdfFile!!)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF")
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) RefDarkBg else themePalette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR
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
                    .testTag("button_back_rotate_editor")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }

            // Selection Status
            Text(
                text = if (selectedPageIds.isNotEmpty()) "${selectedPageIds.size} selected" else "Rotate Pages",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                modifier = Modifier.testTag("text_rotate_selected_count")
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
                modifier = Modifier.testTag("button_rotate_select_all")
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = RefAccentGreen
                )
            }
        }

        // INSTRUCTION ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                text = if (selectedPageIds.isNotEmpty()) "Rotating ${selectedPageIds.size} selected page(s)" else "Tap rotate button or select pages to rotate",
                fontSize = 13.5.sp,
                color = RefTextMuted,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                    CircularProgressIndicator(color = RefAccentGreen)
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 120.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("grid_rotate_pages")
                ) {
                    itemsIndexed(pagesList, key = { _, page -> page.id }) { index, page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        val extraRotation = pageRotations[page.id] ?: 0

                        RotatePageCard(
                            page = page,
                            displayIndex = index + 1,
                            isSelected = isSelected,
                            extraRotation = extraRotation,
                            isDark = themePalette.isDark,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedPageIds.remove(page.id)
                                } else {
                                    selectedPageIds.add(page.id)
                                }
                            },
                            onRotateClockwise = {
                                rotateSinglePage(page.id, 90)
                            }
                        )
                    }
                }
            }
        }

        // FLOATING ACTION BAR: Quick Rotation Tools (Rotate Left, Rotate Right, 180°, Reset)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (themePalette.isDark) RefCardBg else Color.White,
            border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else Color(0xFFE2E8F0)),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rotate Left -90°
                RotateToolButton(
                    icon = Icons.Default.RotateLeft,
                    label = "Left 90°",
                    onClick = { rotateSelectedOrAll(270) }
                )

                // Rotate Right +90°
                RotateToolButton(
                    icon = Icons.Default.RotateRight,
                    label = "Right 90°",
                    onClick = { rotateSelectedOrAll(90) }
                )

                // Rotate 180°
                RotateToolButton(
                    icon = Icons.Default.ScreenRotation,
                    label = "180°",
                    onClick = { rotateSelectedOrAll(180) }
                )

                // Reset
                RotateToolButton(
                    icon = Icons.Default.RestartAlt,
                    label = "Reset",
                    onClick = { resetAllRotations() }
                )
            }
        }

        // BOTTOM BAR: Cancel, Title, and Save Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            color = if (themePalette.isDark) Color(0xFF191B1F) else Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_cancel_rotate")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Title
                Text(
                    text = "Rotate Pages",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )

                // Save / Confirm Button
                IconButton(
                    onClick = {
                        if (!isSaving) {
                            if (!hasRotations) {
                                Toast.makeText(context, "No rotation changes made.", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                                return@IconButton
                            }
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    val success = viewModel.saveRotatedDocumentPages(document.id, pageRotations.toMap())
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        if (success) {
                                            val updatedDoc = viewModel.getDocumentDirect(document.id)
                                            if (updatedDoc?.pdfPath != null) {
                                                savedPdfFile = File(updatedDoc.pdfPath)
                                            }
                                            Toast.makeText(context, "Pages rotated and saved!", Toast.LENGTH_SHORT).show()
                                            showSuccessDialog = true
                                        } else {
                                            Toast.makeText(context, "Failed to save rotations.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_confirm_rotate")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = RefAccentGreen,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm Rotate",
                            tint = if (hasRotations) RefAccentGreen else RefTextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotateToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val themePalette = rememberAppThemePalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = RefAccentGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
        )
    }
}

/**
 * Individual Page Item in the Rotate 2-Column Grid
 */
@Composable
private fun RotatePageCard(
    page: PageEntity,
    displayIndex: Int,
    isSelected: Boolean,
    extraRotation: Int,
    isDark: Boolean,
    onToggleSelect: () -> Unit,
    onRotateClockwise: () -> Unit
) {
    val animatedRotation by animateFloatAsState(
        targetValue = extraRotation.toFloat(),
        label = "page_rotation_anim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleSelect)
            .testTag("rotate_page_card_$displayIndex"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) RefCardBg else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) RefAccentGreen else (if (isDark) RefCardBorder else Color(0xFFE2E8F0))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card image viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF1E222A) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                // Rotated Page Image
                val imageFile = remember(page.processedImagePath, page.originalImagePath) {
                    val path = page.processedImagePath.ifBlank { page.originalImagePath }
                    if (path.isNotBlank()) File(path) else null
                }

                if (imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page $displayIndex",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .graphicsLayer {
                                rotationZ = animatedRotation
                            },
                        contentScale = ContentScale.Fit
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
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // TOP-LEFT: 2-Digit Page Number Badge
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d", displayIndex),
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // TOP-RIGHT: Selection Checkbox
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) RefAccentGreen else Color.Black.copy(alpha = 0.4f),
                    border = BorderStroke(1.5.dp, if (isSelected) RefAccentGreen else Color.White),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    if (isSelected) {
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

                // BOTTOM-LEFT: Rotation Degree Badge (if rotated)
                if (extraRotation % 360 != 0) {
                    val normalizedDeg = if (extraRotation < 0) extraRotation + 360 else extraRotation
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RefAccentGreen.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "${normalizedDeg}°",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // BOTTOM ROW: Page Title & 1-Tap Quick Rotate Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page $displayIndex",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) RefTextPrimary else Color(0xFF334155)
                )

                // 1-Tap Quick 90° Clockwise Rotation Button
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF2C3240) else Color(0xFFE2E8F0),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRotateClockwise)
                        .testTag("btn_rotate_single_$displayIndex")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate 90°",
                            tint = RefAccentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
