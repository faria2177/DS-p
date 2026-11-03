package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme colors strictly matching reference screenshots
private val DarkCanvasBg: Color get() = ThemeManager.themeColor(Color(0xFF16181A), Color(0xFFF8FAFC))
private val DarkCardSurface: Color get() = ThemeManager.themeColor(Color(0xFF212328), Color(0xFFFFFFFF))
private val DarkCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val AccentGreen = Color(0xFF00C48C)
private val TextPrimaryWhite: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextSecondaryMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))
private val DeviceIconBlue = Color(0xFF3B82F6)

/**
 * PDF Extract / Extract PDF Pages Root Feature Component
 * Exactly reproduces the attached 2-screen workflow:
 * 1. Document Selection / Import Screen
 * 2. 2-Column Page Grid Selection & "Extract as New Document" Workflow
 */
@Composable
fun ExtractPdfPagesScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit
) {
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(initialDocId, documents) {
        if (initialDocId != null && initialDocId > 0L) {
            val found = documents.find { it.id == initialDocId }
            if (found != null) {
                selectedDocument = found
            }
        }
    }

    if (selectedDocument == null) {
        // SCREEN 1: Document Selection Screen (Reference Left Side)
        PdfExtractDocSelectScreen(
            documents = documents,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onDocumentSelected = { doc ->
                selectedDocument = doc
            }
        )
    } else {
        // SCREEN 2: 2-Column Page Selection & Extraction Screen (Reference Right Side)
        PdfExtractPageGridScreen(
            document = selectedDocument!!,
            viewModel = viewModel,
            onNavigateBack = {
                if (initialDocId != null) {
                    onNavigateBack()
                } else {
                    selectedDocument = null
                }
            },
            onExtractionComplete = { newDocId ->
                onNavigateToDocumentDetail(newDocId)
            }
        )
    }
}

/**
 * SCREEN 1: PDF Extract Document Selection & Device Import Screen (Reference Left Side)
 */
@Composable
private fun PdfExtractDocSelectScreen(
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
                val importedDoc = viewModel.importDocumentFromDeviceForExtract(uris)
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
                val importedDoc = viewModel.importDocumentFromDeviceForExtract(uris)
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
                color = if (themePalette.isDark) Color(0xFF1E222B) else Color.White,
                border = BorderStroke(1.dp, if (themePalette.isDark) Color(0xFF2C3240) else Color(0xFFE2E8F0)),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Loading PDF Pages...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Text(
                        text = "Rendering document pages for extraction.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) DarkCanvasBg else themePalette.background)
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
                    .testTag("button_back_pdf_extract")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )
            }

            // Top right subtle stacked preview illustration
            PdfExtractHeaderIllustration(latestDoc = documents.firstOrNull())
        }

        // TITLE & SUBTITLE (Matching Reference Left Screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = "PDF Extract",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Extract selected pages in seconds for faster filing.",
                fontSize = 14.sp,
                color = TextSecondaryMuted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: CREATE OR IMPORT
        Text(
            text = "Create or Import",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // LARGE DEVICE BUTTON CARD (Matching Reference Left Screen)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable {
                    try {
                        fileLauncher.launch(arrayOf("application/pdf", "image/*", "*/*"))
                    } catch (e: Exception) {
                        genericFileLauncher.launch("*/*")
                    }
                }
                .testTag("card_device_import"),
            shape = RoundedCornerShape(12.dp),
            color = if (themePalette.isDark) DarkCardSurface else themePalette.card,
            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else themePalette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DeviceIconBlue,
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
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Device",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 2: SELECT FROM THIS APP
        Text(
            text = "Select from This App",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // DOCUMENT LIST FROM LOCAL DATABASE
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
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No documents found",
                        fontSize = 15.sp,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import a PDF or scan documents to extract pages.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("list_extract_documents"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    PdfExtractDocRow(
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
 * Top right decorative header preview card
 */
@Composable
private fun PdfExtractHeaderIllustration(latestDoc: DocumentEntity?) {
    Box(
        modifier = Modifier.size(width = 64.dp, height = 54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back rotated card
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF334155),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier
                .size(width = 34.dp, height = 44.dp)
                .offset(x = 8.dp, y = (-2).dp)
                .rotate(8f)
        ) {}

        // Front active card with thumbnail
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(1.dp, AccentGreen),
            modifier = Modifier
                .size(width = 32.dp, height = 42.dp)
                .offset(x = 0.dp, y = (-2).dp)
        ) {
            val thumbFile = remember(latestDoc?.thumbnailPath) {
                if (!latestDoc?.thumbnailPath.isNullOrBlank()) File(latestDoc!!.thumbnailPath) else null
            }
            if (thumbFile != null && thumbFile.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Document Row in the Selection List (Reference Left Screen)
 */
@Composable
private fun PdfExtractDocRow(
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
            .testTag("extract_doc_item_${doc.id}"),
        color = if (isDark) DarkCardSurface else Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isDark) DarkCardBorder else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview card
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

            // Document Details: Title, Date & Page Count Badge
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = doc.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextPrimaryWhite else Color(0xFF0F172A),
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
                        color = TextSecondaryMuted
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${doc.pageCount}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * SCREEN 2: 2-Column Page Grid Selection & Extraction Screen (Reference Right Side)
 */
@Composable
private fun PdfExtractPageGridScreen(
    document: DocumentEntity,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onExtractionComplete: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val selectedPageIds = remember { mutableStateListOf<Long>() }
    var isLoading by remember { mutableStateOf(true) }
    var isExtracting by remember { mutableStateOf(false) }

    // Load actual document pages directly
    LaunchedEffect(document.id) {
        isLoading = true
        val loaded = viewModel.getPagesForDocumentDirect(document.id)
        pages = loaded
        // Select first page by default matching reference screenshot "1 selected"
        if (loaded.isNotEmpty() && selectedPageIds.isEmpty()) {
            selectedPageIds.add(loaded[0].id)
        }
        isLoading = false
    }

    BackHandler {
        onNavigateBack()
    }

    val totalPages = pages.size
    val selectedCount = selectedPageIds.size
    val isAllSelected = totalPages > 0 && selectedCount == totalPages

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) DarkCanvasBg else themePalette.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR: "←  X selected                     Select All" (Matching Reference Right Screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("button_back_grid_extract")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                }

                Text(
                    text = "$selectedCount selected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary,
                    modifier = Modifier.testTag("text_selected_count")
                )
            }

            TextButton(
                onClick = {
                    if (isAllSelected) {
                        selectedPageIds.clear()
                    } else {
                        selectedPageIds.clear()
                        selectedPageIds.addAll(pages.map { it.id })
                    }
                },
                modifier = Modifier.testTag("button_select_all")
            ) {
                Text(
                    text = if (isAllSelected) "Deselect All" else "Select All",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen
                )
            }
        }

        // MAIN 2-COLUMN PAGE GRID AREA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = AccentGreen)
            } else if (pages.isEmpty()) {
                Text(
                    text = "No pages found in this document",
                    color = TextSecondaryMuted,
                    fontSize = 15.sp
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("grid_extract_pages")
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        val pageNumberFormatted = String.format(Locale.getDefault(), "%02d", index + 1)

                        ExtractPageGridItem(
                            page = page,
                            pageNumberString = pageNumberFormatted,
                            isSelected = isSelected,
                            isDark = themePalette.isDark,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedPageIds.remove(page.id)
                                } else {
                                    selectedPageIds.add(page.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // FIXED BOTTOM ACTION BAR: "Extract as New Document" BUTTON (Matching Reference Right Screen)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_bar_extract_action"),
            color = if (themePalette.isDark) Color(0xFF0C0E12) else themePalette.bottomBarBg,
            border = BorderStroke(1.dp, if (themePalette.isDark) Color(0xFF1E232E) else themePalette.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedCount > 0 && !isExtracting) {
                            isExtracting = true
                            coroutineScope.launch {
                                try {
                                    // Filter selected pages while preserving their original order
                                    val pagesToExtract = pages.filter { selectedPageIds.contains(it.id) }
                                    val newDocId = viewModel.extractPagesDirectly(
                                        sourceDoc = document,
                                        pagesToExtract = pagesToExtract,
                                        customTitle = "${document.title} - Extracted"
                                    )
                                    withContext(Dispatchers.Main) {
                                        isExtracting = false
                                        Toast.makeText(
                                            context,
                                            "Extracted ${pagesToExtract.size} page(s) to '${document.title} - Extracted'",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onExtractionComplete(newDocId)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isExtracting = false
                                        Toast.makeText(context, "Extraction failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = selectedCount > 0 && !isExtracting,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        disabledContainerColor = AccentGreen.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("button_extract_as_new_doc")
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Extract as New Document",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2-Column Page Grid Item matching Reference Right Screenshot
 */
@Composable
private fun ExtractPageGridItem(
    page: PageEntity,
    pageNumberString: String,
    isSelected: Boolean,
    isDark: Boolean,
    onToggleSelect: () -> Unit
) {
    val pageFile = remember(page.id, page.processedImagePath, page.originalImagePath) {
        val processed = File(page.processedImagePath)
        if (processed.exists()) processed else File(page.originalImagePath)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
            .testTag("page_item_${page.id}")
    ) {
        // Page Sheet Card with Outline & Checkmark Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) AccentGreen else (if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1))
            ),
            shadowElevation = if (isSelected) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f) // Standard document page aspect ratio
                .clip(RoundedCornerShape(6.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Page Image Preview
                if (pageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(pageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page $pageNumberString",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top Right Selection Badge / Checkbox (Matching Reference Screenshot)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentGreen,
                            modifier = Modifier.size(22.dp)
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
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.size(22.dp)
                        ) {}
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Page Number Indicator Underneath (Matching Reference: Selected gets Green Pill, Unselected is clean text)
        if (isSelected) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentGreen,
                modifier = Modifier
                    .height(22.dp)
                    .width(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = pageNumberString,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = pageNumberString,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) TextPrimaryWhite.copy(alpha = 0.8f) else Color(0xFF475569),
                modifier = Modifier.height(22.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
