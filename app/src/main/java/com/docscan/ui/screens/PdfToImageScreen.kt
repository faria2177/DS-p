package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.FilterType
import com.docscan.data.model.PageEntity
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import com.docscan.util.ImageProcessor
import com.docscan.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Theme colors strictly matching reference screenshot
private val DarkCanvasBg: Color get() = ThemeManager.themeColor(Color(0xFF141618), Color(0xFFF8FAFC))
private val DarkCardSurface: Color get() = ThemeManager.themeColor(Color(0xFF222428), Color(0xFFFFFFFF))
private val DarkCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val AccentTeal = Color(0xFF00BFA5)
private val AccentGreen = Color(0xFF00C48C)
private val TextPrimaryWhite: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextSecondaryMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))
private val DeviceIconBlue = Color(0xFF3B82F6)
private val PdfBadgeRed = Color(0xFFEF4444)

/**
 * PDF to Images Screen
 * Matches reference UI layout and provides modern conversion options
 * with auto saving to App Documents, MediaStore Gallery, and ZIP archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    viewModel: ScannerViewModel,
    initialDocument: DocumentEntity? = null,
    allDocuments: List<DocumentEntity> = emptyList(),
    onNavigateToDocumentDetail: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    var activeDoc by remember { mutableStateOf<DocumentEntity?>(initialDocument) }
    var showSetupDialog by remember { mutableStateOf(initialDocument != null) }

    // Search and filter state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    // Loading & Conversion states
    var isImportingFromDevice by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }
    var conversionStatusMessage by remember { mutableStateOf("") }

    // Extracted result state
    var extractedImageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var newlyCreatedDocId by remember { mutableStateOf<Long?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var previewImageForZoom by remember { mutableStateOf<File?>(null) }

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
                        activeDoc = importedDoc
                        showSetupDialog = true
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
                val importedDoc = viewModel.importDocumentFromDeviceForExtract(uris)
                withContext(Dispatchers.Main) {
                    isImportingFromDevice = false
                    if (importedDoc != null) {
                        Toast.makeText(context, "Loaded ${importedDoc.pageCount} page(s) from selected file", Toast.LENGTH_SHORT).show()
                        activeDoc = importedDoc
                        showSetupDialog = true
                    } else {
                        Toast.makeText(context, "Could not open or render the selected document.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Filter documents list based on search
    val filteredDocuments = remember(allDocuments, searchQuery) {
        if (searchQuery.isBlank()) allDocuments
        else allDocuments.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    BackHandler {
        onBack()
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("button_back_pdf_to_images")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )
            }

            // Top Right Stacked Visual Preview Graphic (Exact match to screenshot with blue image badge & PDF document)
            PdfToImagesHeaderIllustration()
        }

        // TITLE & SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
        ) {
            Text(
                text = "PDF to Images",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary,
                modifier = Modifier.testTag("title_pdf_to_images")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Convert PDF pages to individual images.",
                fontSize = 14.sp,
                color = TextSecondaryMuted
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 1: CREATE OR IMPORT
        Text(
            text = "Create or Import",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // LARGE DEVICE BUTTON CARD (Matching Reference Screenshot)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                .clickable {
                    try {
                        fileLauncher.launch(arrayOf("application/pdf", "image/*", "*/*"))
                    } catch (e: Exception) {
                        genericFileLauncher.launch("*/*")
                    }
                }
                .testTag("card_device_import_pdf_to_images"),
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
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Device",
                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier.size(20.dp)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select from This App",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryMuted
            )

            IconButton(
                onClick = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search Documents",
                    tint = if (isSearchOpen) AccentTeal else TextSecondaryMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // EXPANDABLE LIVE SEARCH BAR
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = if (themePalette.isDark) Color(0xFF1E2127) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search PDF documents...",
                                color = TextSecondaryMuted,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // DOCUMENT LIST FROM LOCAL APP DATABASE (Matching Reference Screenshot)
        if (filteredDocuments.isEmpty()) {
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
                        tint = TextSecondaryMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No documents available" else "No documents matching \"$searchQuery\"",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import a PDF from Device to convert pages into images.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("list_pdf_to_images_documents"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredDocuments, key = { it.id }) { doc ->
                    PdfToImageDocRow(
                        doc = doc,
                        isDark = themePalette.isDark,
                        onClick = {
                            activeDoc = doc
                            showSetupDialog = true
                        }
                    )
                }
            }
        }
    }

    // LOADING DEVICE IMPORT DIALOG
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
                        color = AccentTeal,
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
                        text = "Rendering document pages for image extraction.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // CONVERSION SETUP BOTTOM SHEET / DIALOG
    if (showSetupDialog && activeDoc != null) {
        PdfToImageSetupSheet(
            doc = activeDoc!!,
            viewModel = viewModel,
            onDismiss = { showSetupDialog = false },
            onStartConversion = { config, selectedPages ->
                showSetupDialog = false
                isConverting = true
                coroutineScope.launch {
                    val result = convertPdfPagesToImages(
                        context = context,
                        doc = activeDoc!!,
                        selectedPages = selectedPages,
                        config = config,
                        viewModel = viewModel,
                        onProgress = { progress, msg ->
                            conversionProgress = progress
                            conversionStatusMessage = msg
                        }
                    )
                    withContext(Dispatchers.Main) {
                        isConverting = false
                        if (result.success && result.imageFiles.isNotEmpty()) {
                            extractedImageFiles = result.imageFiles
                            newlyCreatedDocId = result.createdDocId
                            showSuccessDialog = true
                            Toast.makeText(context, "Successfully extracted ${result.imageFiles.size} image(s)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.errorMessage ?: "Conversion failed.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // REAL-TIME CONVERSION PROGRESS DIALOG
    if (isConverting) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (themePalette.isDark) Color(0xFF1C1F26) else Color.White,
                border = BorderStroke(1.dp, if (themePalette.isDark) Color(0xFF2C3240) else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { conversionProgress },
                        color = AccentTeal,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Converting PDF to Images...",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Text(
                        text = conversionStatusMessage.ifBlank { "Processing pages..." },
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                    LinearProgressIndicator(
                        progress = { conversionProgress },
                        color = AccentTeal,
                        trackColor = Color(0xFF2C3240),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // CONVERSION SUCCESS & RESULT GALLERY DIALOG
    if (showSuccessDialog && extractedImageFiles.isNotEmpty()) {
        PdfToImageSuccessDialog(
            imageFiles = extractedImageFiles,
            docTitle = activeDoc?.title ?: "Document",
            newlyCreatedDocId = newlyCreatedDocId,
            onDismiss = { showSuccessDialog = false },
            onViewDocument = { docId ->
                showSuccessDialog = false
                onNavigateToDocumentDetail(docId)
            },
            onZoomImage = { file ->
                previewImageForZoom = file
            }
        )
    }

    // FULLSCREEN IMAGE ZOOM PREVIEW
    if (previewImageForZoom != null) {
        Dialog(
            onDismissRequest = { previewImageForZoom = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(previewImageForZoom)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Zoomed Page",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { previewImageForZoom = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }
            }
        }
    }
}

/**
 * Top right decorative header preview card matching the reference screenshot
 */
@Composable
private fun PdfToImagesHeaderIllustration() {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 66.dp)
            .padding(end = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back white card with sample document text
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .size(width = 46.dp, height = 58.dp)
                .offset(x = 6.dp, y = 0.dp)
                .shadow(4.dp, RoundedCornerShape(4.dp))
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text("Docs Done Right", fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text("In one app. Convert, extract, compress, all upgraded. Spot-on recognition, formatting preserved, any document handled with ease.", fontSize = 3.5.sp, color = Color.DarkGray, lineHeight = 5.sp, maxLines = 5)
            }
        }

        // Blue Gallery/Image badge icon in upper left of the stacked card (Matching Screenshot)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF2563EB),
            modifier = Modifier
                .size(20.dp)
                .offset(x = (-16).dp, y = (-14).dp)
                .shadow(2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = null,
                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Small Red PDF badge at top right
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = PdfBadgeRed,
            modifier = Modifier
                .size(14.dp)
                .offset(x = 24.dp, y = (-18).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("A", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Document Row in the Selection List (Exact match to reference screenshot with Red PDF badge on thumbnail)
 */
@Composable
private fun PdfToImageDocRow(
    doc: DocumentEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAt) {
        try {
            dateFormat.format(Date(doc.createdAt)).lowercase()
        } catch (e: Exception) {
            "25/08/2026 1:00 am"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("pdf_image_doc_item_${doc.id}"),
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
            // Thumbnail preview card with red PDF badge on top-left (Matching Screenshot)
            Box(modifier = Modifier.size(width = 48.dp, height = 60.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxSize()
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

                // Red PDF icon badge on top left corner (Matching Screenshot)
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = PdfBadgeRed,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "A",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Document Title, Date and Page count
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = doc.title,
                    fontSize = 15.sp,
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
                        fontSize = 12.sp,
                        color = TextSecondaryMuted
                    )

                    Text(
                        text = "|",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted.copy(alpha = 0.5f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${doc.pageCount}",
                            fontSize = 12.sp,
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
 * Modern Conversion Options Configuration Data Model
 */
data class ImageConversionConfig(
    val format: String = "JPG", // "JPG", "PNG", "WEBP"
    val qualityDpi: Int = 300,  // 150, 300, 600
    val applyMagicColor: Boolean = false,
    val cleanBackground: Boolean = false,
    val saveAsAppDocument: Boolean = true,
    val saveToGallery: Boolean = true,
    val createZipArchive: Boolean = false
)

/**
 * PDF to Image Setup & Options Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfToImageSetupSheet(
    doc: DocumentEntity,
    viewModel: ScannerViewModel,
    onDismiss: () -> Unit,
    onStartConversion: (ImageConversionConfig, List<PageEntity>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themePalette = rememberAppThemePalette()

    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val selectedPageIds = remember { mutableStateListOf<Long>() }
    var isLoadingPages by remember { mutableStateOf(true) }

    // Multi-page selection mode: 0 = All Pages, 1 = Selected Pages Grid, 2 = Page Range
    var pageSelectionMode by remember { mutableIntStateOf(0) }
    var rangeStartInput by remember { mutableStateOf("1") }
    var rangeEndInput by remember { mutableStateOf("1") }

    // Conversion Options
    var selectedFormat by remember { mutableStateOf("JPG") } // "JPG", "PNG", "WEBP"
    var selectedDpi by remember { mutableIntStateOf(300) } // 150, 300, 600
    var applyMagicColor by remember { mutableStateOf(false) }
    var cleanBackground by remember { mutableStateOf(false) }
    var saveAsAppDocument by remember { mutableStateOf(true) }
    var saveToGallery by remember { mutableStateOf(true) }
    var createZipArchive by remember { mutableStateOf(false) }

    var showAdvancedOptions by remember { mutableStateOf(false) }

    LaunchedEffect(doc.id) {
        isLoadingPages = true
        val loaded = viewModel.getPagesForDocumentDirect(doc.id)
        pages = loaded
        selectedPageIds.clear()
        selectedPageIds.addAll(loaded.map { it.id })
        rangeEndInput = loaded.size.toString()
        isLoadingPages = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (themePalette.isDark) Color(0xFF181A20) else Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PDF to Image Setup",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Text(
                        text = doc.title,
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondaryMuted
                    )
                }
            }

            HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

            // SECTION 1: PAGE SELECTION
            Text(
                text = "Page Selection (${if (pageSelectionMode == 0) pages.size else selectedPageIds.size} of ${pages.size} selected)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = pageSelectionMode == 0,
                    onClick = {
                        pageSelectionMode = 0
                        selectedPageIds.clear()
                        selectedPageIds.addAll(pages.map { it.id })
                    },
                    label = { Text("All Pages (${pages.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentTeal,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = pageSelectionMode == 1,
                    onClick = { pageSelectionMode = 1 },
                    label = { Text("Select Pages") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentTeal,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = pageSelectionMode == 2,
                    onClick = { pageSelectionMode = 2 },
                    label = { Text("Page Range") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }

            // Interactive Page Grid if "Select Pages" mode is active
            if (pageSelectionMode == 1) {
                if (isLoadingPages) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentTeal)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pages) { index, page ->
                            val isSelected = selectedPageIds.contains(page.id)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (themePalette.isDark) DarkCardSurface else Color(0xFFF1F5F9),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) AccentTeal else if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSelected) selectedPageIds.remove(page.id)
                                        else selectedPageIds.add(page.id)
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val thumbFile = File(page.processedImagePath)
                                    if (thumbFile.exists()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(thumbFile)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    // Page Number Chip
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "P.${index + 1}",
                                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Checkmark
                                    if (isSelected) {
                                        Surface(
                                            color = AccentTeal,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Page Range Inputs
            if (pageSelectionMode == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rangeStartInput,
                        onValueChange = { rangeStartInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("From Page") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            focusedLabelColor = AccentTeal
                        )
                    )
                    Text("to", color = TextSecondaryMuted)
                    OutlinedTextField(
                        value = rangeEndInput,
                        onValueChange = { rangeEndInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("To Page") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            focusedLabelColor = AccentTeal
                        )
                    )
                }
            }

            // SECTION 2: OUTPUT IMAGE FORMAT
            Text(
                text = "Output Image Format",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentTeal.copy(alpha = 0.15f) else if (themePalette.isDark) DarkCardSurface else Color(0xFFF1F5F9),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) AccentTeal else if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedFormat = fmt }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = fmt,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isSelected) AccentTeal else if (themePalette.isDark) TextPrimaryWhite else Color(0xFF0F172A)
                            )
                            Text(
                                text = when (fmt) {
                                    "JPG" -> "Standard"
                                    "PNG" -> "Crisp HD"
                                    else -> "Small Size"
                                },
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }
            }

            // SECTION 3: RESOLUTION / DPI QUALITY
            Text(
                text = "Resolution & Quality",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(150 to "Standard (150 DPI)", 300 to "High (300 DPI)", 600 to "Ultra HD (600 DPI)").forEach { (dpi, label) ->
                    FilterChip(
                        selected = selectedDpi == dpi,
                        onClick = { selectedDpi = dpi },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // SECTION 4: SAVE DESTINATIONS & ZIP
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (themePalette.isDark) DarkCardSurface else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Save as Document in App (CRITICAL REQUIREMENT)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save to Document Files (App Library)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Creates a document in app library for fast viewing & sharing",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = saveAsAppDocument,
                            onCheckedChange = { saveAsAppDocument = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentTeal)
                        )
                    }

                    HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

                    // Save to Device Gallery / Photos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save to Gallery / Photos",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Saves directly into Pictures/DocScanner gallery folder",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = saveToGallery,
                            onCheckedChange = { saveToGallery = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentTeal)
                        )
                    }

                    HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

                    // Package into ZIP Archive
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Create ZIP Archive (.zip)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Packages all extracted images into a single zip file for sharing",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = createZipArchive,
                            onCheckedChange = { createZipArchive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentTeal)
                        )
                    }
                }
            }

            // PRIMARY ACTION: CONVERT TO IMAGES BUTTON
            Button(
                onClick = {
                    val finalPagesToConvert = when (pageSelectionMode) {
                        0 -> pages
                        1 -> pages.filter { selectedPageIds.contains(it.id) }
                        else -> {
                            val start = (rangeStartInput.toIntOrNull() ?: 1).coerceIn(1, pages.size)
                            val end = (rangeEndInput.toIntOrNull() ?: pages.size).coerceIn(start, pages.size)
                            pages.subList(start - 1, end)
                        }
                    }

                    if (finalPagesToConvert.isEmpty()) {
                        return@Button
                    }

                    val config = ImageConversionConfig(
                        format = selectedFormat,
                        qualityDpi = selectedDpi,
                        applyMagicColor = applyMagicColor,
                        cleanBackground = cleanBackground,
                        saveAsAppDocument = saveAsAppDocument,
                        saveToGallery = saveToGallery,
                        createZipArchive = createZipArchive
                    )

                    onStartConversion(config, finalPagesToConvert)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("button_start_convert_to_images"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
            ) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = null,
                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Convert to Images",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                )
            }
        }
    }
}

/**
 * Result data class for conversion operation
 */
private data class ImageConversionResult(
    val success: Boolean,
    val imageFiles: List<File> = emptyList(),
    val createdDocId: Long? = null,
    val zipFile: File? = null,
    val errorMessage: String? = null
)

/**
 * Background conversion engine that extracts PDF pages to individual images,
 * applies requested enhancement filters, creates a Document Entity in the app DB,
 * and saves to MediaStore / creates ZIP.
 */
private suspend fun convertPdfPagesToImages(
    context: android.content.Context,
    doc: DocumentEntity,
    selectedPages: List<PageEntity>,
    config: ImageConversionConfig,
    viewModel: ScannerViewModel,
    onProgress: (Float, String) -> Unit
): ImageConversionResult = withContext(Dispatchers.IO) {
    try {
        val total = selectedPages.size
        val extractedFiles = mutableListOf<File>()
        val newPageEntities = mutableListOf<PageEntity>()

        val scaleFactor = when (config.qualityDpi) {
            150 -> 1.5f
            600 -> 3.5f
            else -> 2.5f
        }

        val ext = config.format.lowercase()
        val compressFormat = when (config.format) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.JPEG
        }

        val baseCleanName = doc.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")

        for ((index, page) in selectedPages.withIndex()) {
            val progress = (index + 0.2f) / total
            onProgress(progress, "Rendering page ${index + 1} of $total...")

            var baseBitmap: Bitmap? = null

            // 1. Try to load directly from page image path
            val srcFile = File(page.processedImagePath).let { if (it.exists()) it else File(page.originalImagePath) }
            if (srcFile.exists()) {
                baseBitmap = FileUtils.loadBitmap(srcFile.absolutePath, 3500)
            }

            // 2. If not found, and doc has pdfPath, render from PDF
            if (baseBitmap == null && !doc.pdfPath.isNullOrBlank()) {
                val pdfFile = File(doc.pdfPath)
                if (pdfFile.exists()) {
                    baseBitmap = renderSinglePageFromPdf(context, pdfFile, page.pageNumber - 1, scaleFactor)
                }
            }

            if (baseBitmap == null) {
                continue
            }

            // Apply enhancements if requested
            if (config.applyMagicColor) {
                onProgress(progress + 0.1f, "Applying color filter to page ${index + 1}...")
                baseBitmap = ImageProcessor.applyFilter(baseBitmap, FilterType.MAGIC_COLOR)
            }

            // Save to Doc storage
            val outputFileName = "${baseCleanName}_page_${index + 1}_${System.currentTimeMillis()}.$ext"
            val targetFile = File(FileUtils.getDocumentsDir(context), outputFileName)
            FileOutputStream(targetFile).use { out ->
                baseBitmap.compress(compressFormat, 95, out)
            }

            extractedFiles.add(targetFile)

            // Save to Gallery if enabled
            if (config.saveToGallery) {
                FileUtils.saveBitmapToGallery(context, baseBitmap, "${baseCleanName}_page_${index + 1}")
            }

            // Prepare PageEntity for app document storage
            newPageEntities.add(
                PageEntity(
                    documentId = 0,
                    pageNumber = index + 1,
                    originalImagePath = targetFile.absolutePath,
                    processedImagePath = targetFile.absolutePath,
                    filterType = if (config.applyMagicColor) FilterType.MAGIC_COLOR.name else FilterType.ORIGINAL.name,
                    extractedText = page.extractedText
                )
            )

            onProgress((index + 1f) / total, "Completed page ${index + 1} of $total")
        }

        if (extractedFiles.isEmpty()) {
            return@withContext ImageConversionResult(success = false, errorMessage = "No pages could be converted to images.")
        }

        // SAVE AS A DOCUMENT FILE IN THE ROOM DATABASE (CRITICAL USER REQUIREMENT)
        var createdDocId: Long? = null
        if (config.saveAsAppDocument && newPageEntities.isNotEmpty()) {
            onProgress(0.95f, "Saving to Document library...")
            val newDocTitle = "${doc.title} (Images)"
            createdDocId = viewModel.saveNewDocument(
                title = newDocTitle,
                folder = "Images",
                pages = newPageEntities
            )
        }

        // Optional ZIP packaging
        var zipFile: File? = null
        if (config.createZipArchive && extractedFiles.isNotEmpty()) {
            onProgress(0.98f, "Creating ZIP archive...")
            zipFile = File(FileUtils.getDocumentsDir(context), "${baseCleanName}_images.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                for (file in extractedFiles) {
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        onProgress(1f, "Done!")
        ImageConversionResult(
            success = true,
            imageFiles = extractedFiles,
            createdDocId = createdDocId,
            zipFile = zipFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        ImageConversionResult(success = false, errorMessage = "Error converting PDF: ${e.message}")
    }
}

/**
 * Renders a specific page from a PDF file using PdfRenderer
 */
private fun renderSinglePageFromPdf(context: android.content.Context, pdfFile: File, pageIndex: Int, scaleFactor: Float): Bitmap? {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
        pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        if (pageIndex in 0 until renderer.pageCount) {
            val page = renderer.openPage(pageIndex)
            val width = (page.width * scaleFactor).toInt().coerceAtLeast(1080)
            val height = (page.height * scaleFactor).toInt()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        try { renderer?.close() } catch (ignored: Exception) {}
        try { pfd?.close() } catch (ignored: Exception) {}
    }
}

/**
 * Success Dialog & Extracted Images Gallery Modal
 */
@Composable
private fun PdfToImageSuccessDialog(
    imageFiles: List<File>,
    docTitle: String,
    newlyCreatedDocId: Long?,
    onDismiss: () -> Unit,
    onViewDocument: (Long) -> Unit,
    onZoomImage: (File) -> Unit
) {
    val context = LocalContext.current
    val themePalette = rememberAppThemePalette()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (themePalette.isDark) Color(0xFF181A20) else Color.White,
            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Success Badge
                Surface(
                    shape = CircleShape,
                    color = AccentTeal.copy(alpha = 0.15f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Conversion Complete!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )

                Text(
                    text = "${imageFiles.size} image(s) extracted and saved to Document files.",
                    fontSize = 13.sp,
                    color = TextSecondaryMuted,
                    textAlign = TextAlign.Center
                )

                // Image Preview Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(imageFiles) { idx, file ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (themePalette.isDark) DarkCardSurface else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onZoomImage(file) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(file)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Image ${idx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "Image ${idx + 1}",
                                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom",
                                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share Images Button
                    OutlinedButton(
                        onClick = {
                            FileUtils.shareImageFiles(context, imageFiles, docTitle)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp)
                    }

                    // Open / View Document in App
                    if (newlyCreatedDocId != null) {
                        Button(
                            onClick = {
                                onViewDocument(newlyCreatedDocId)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Doc", fontSize = 13.sp, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Done", color = TextSecondaryMuted)
                }
            }
        }
    }
}
