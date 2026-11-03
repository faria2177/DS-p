package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme colors strictly matching reference screenshots
private val DarkCanvasBg: Color get() = ThemeManager.themeColor(Color(0xFF16181A), Color(0xFFF8FAFC))
private val DarkCardSurface: Color get() = ThemeManager.themeColor(Color(0xFF212328), Color(0xFFFFFFFF))
private val DarkCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val AccentSkyBlue = Color(0xFF0EA5E9)
private val AccentTeal = Color(0xFF00BFA5)
private val TextPrimaryWhite: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextSecondaryMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))
private val DeviceIconBlue = Color(0xFF3B82F6)
private val PdfBadgeCyan = Color(0xFF0284C7)

/**
 * PDF to Long Image Screen
 * Seamlessly matches the other tools (PDF to Word, PDF to Excel, PDF to Image, Document Lock)
 * with a 2-step unified workflow:
 * 1. Document selection & device import screen
 * 2. Long Image Stitcher Studio modal with real-time preview, customizable spacing, background, headers, quality, and 1-tap export to Gallery/Share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToLongImageScreen(
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
    var showStudioDialog by remember { mutableStateOf(initialDocument != null) }

    // Search and filter state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }

    // Loading & Conversion states
    var isImportingFromDevice by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }
    var conversionStatusMessage by remember { mutableStateOf("") }

    // Stitched result state
    var stitchedResultFile by remember { mutableStateOf<File?>(null) }
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
                        Toast.makeText(context, "Loaded ${importedDoc.pageCount} page(s) for Long Image stitching", Toast.LENGTH_SHORT).show()
                        activeDoc = importedDoc
                        showStudioDialog = true
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
                        Toast.makeText(context, "Loaded ${importedDoc.pageCount} page(s) for Long Image stitching", Toast.LENGTH_SHORT).show()
                        activeDoc = importedDoc
                        showStudioDialog = true
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
                    .testTag("button_back_pdf_to_long_image")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )
            }

            // Top Right Stacked Visual Preview Graphic
            PdfToLongImageHeaderIllustration()
        }

        // TITLE & SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
        ) {
            Text(
                text = "PDF to Long Image",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary,
                modifier = Modifier.testTag("title_pdf_to_long_image")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Stitch multiple pages into a seamless vertical image.",
                fontSize = 14.sp,
                color = TextSecondaryMuted
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 1: CREATE OR IMPORT
        Text(
            text = "Create or Import",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    try {
                        fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                    } catch (e: Exception) {
                        genericFileLauncher.launch("*/*")
                    }
                }
                .testTag("card_import_device_long_image"),
            color = if (themePalette.isDark) DarkCardSurface else Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DeviceIconBlue.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Import",
                            tint = DeviceIconBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import from Device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select PDF document or multiple photos to stitch",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted
                    )
                }

                if (isImportingFromDevice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = AccentSkyBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 2: SELECT FROM THIS APP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select from This App",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondaryMuted
                )
                Surface(
                    shape = CircleShape,
                    color = AccentSkyBlue.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${filteredDocuments.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSkyBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = { isSearchOpen = !isSearchOpen },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchOpen) AccentSkyBlue else TextSecondaryMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Expandable Search Bar
        AnimatedVisibility(visible = isSearchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search documents...", color = TextSecondaryMuted, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentSkyBlue,
                    unfocusedBorderColor = if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1),
                    focusedContainerColor = if (themePalette.isDark) DarkCardSurface else Color.White,
                    unfocusedContainerColor = if (themePalette.isDark) DarkCardSurface else Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // DOCUMENTS LIST OR EMPTY STATE
        if (filteredDocuments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AccentSkyBlue.copy(alpha = 0.12f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoSizeSelectActual,
                                contentDescription = null,
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching documents" else "No Scanned Documents Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )

                    Text(
                        text = "Scan new pages with camera or import a PDF from device to stitch long images.",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                items(filteredDocuments, key = { it.id }) { doc ->
                    PdfToLongImageDocRow(
                        doc = doc,
                        isDark = themePalette.isDark,
                        onClick = {
                            activeDoc = doc
                            showStudioDialog = true
                        }
                    )
                }
            }
        }
    }

    // ==================== STUDIO MODAL BOTTOM SHEET ====================
    if (showStudioDialog && activeDoc != null) {
        PdfToLongImageStudioSheet(
            doc = activeDoc!!,
            viewModel = viewModel,
            onDismiss = { showStudioDialog = false },
            onStartStitching = { config, pagesToStitch ->
                showStudioDialog = false
                isConverting = true
                conversionProgress = 0.05f
                conversionStatusMessage = "Preparing ${pagesToStitch.size} page(s)..."

                coroutineScope.launch {
                    val result = stitchPagesToLongImage(
                        context = context,
                        doc = activeDoc!!,
                        pages = pagesToStitch,
                        config = config,
                        viewModel = viewModel,
                        onProgress = { prog, status ->
                            conversionProgress = prog
                            conversionStatusMessage = status
                        }
                    )

                    withContext(Dispatchers.Main) {
                        isConverting = false
                        if (result.success && result.stitchedFile != null) {
                            stitchedResultFile = result.stitchedFile
                            newlyCreatedDocId = result.createdDocId
                            showSuccessDialog = true
                            Toast.makeText(context, "Long image stitched successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.errorMessage ?: "Failed to stitch long image", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // ==================== CONVERSION PROGRESS DIALOG ====================
    if (isConverting) {
        Dialog(
            onDismissRequest = { /* Non-cancelable while converting */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (themePalette.isDark) Color(0xFF1F2228) else Color.White,
                border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(AccentSkyBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoSizeSelectActual,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Text(
                        text = "Stitching Long Image",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )

                    Text(
                        text = conversionStatusMessage,
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )

                    LinearProgressIndicator(
                        progress = { conversionProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentSkyBlue,
                        trackColor = if (themePalette.isDark) Color(0xFF2C3038) else Color(0xFFE2E8F0),
                    )

                    Text(
                        text = "${(conversionProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSkyBlue
                    )
                }
            }
        }
    }

    // ==================== SUCCESS MODAL & GALLERY ====================
    if (showSuccessDialog && stitchedResultFile != null) {
        PdfToLongImageSuccessDialog(
            stitchedFile = stitchedResultFile!!,
            docTitle = activeDoc?.title ?: "Stitched Document",
            newlyCreatedDocId = newlyCreatedDocId,
            onDismiss = {
                showSuccessDialog = false
                stitchedResultFile = null
            },
            onViewDocument = { docId ->
                showSuccessDialog = false
                stitchedResultFile = null
                onNavigateToDocumentDetail(docId)
            },
            onZoomImage = { file ->
                previewImageForZoom = file
            }
        )
    }

    // ==================== FULLSCREEN ZOOM VIEWER ====================
    if (previewImageForZoom != null) {
        Dialog(
            onDismissRequest = { previewImageForZoom = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(previewImageForZoom)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Long Image Fullscreen",
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentScale = ContentScale.FillWidth
                )

                IconButton(
                    onClick = { previewImageForZoom = null },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
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
 * Top Right Visual Graphic: Stacked document sheets with a cyan panoramic/long image badge
 */
@Composable
private fun PdfToLongImageHeaderIllustration() {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 70.dp)
            .padding(top = 4.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Bottom paper sheet (White with slight tilt)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier
                .size(width = 38.dp, height = 52.dp)
                .offset(x = (-8).dp, y = 4.dp)
                .rotate(-8f)
                .shadow(3.dp, RoundedCornerShape(4.dp))
        ) {
            Column(modifier = Modifier.padding(3.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFCBD5E1)))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFE2E8F0)))
            }
        }

        // Top main elongated paper sheet
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .size(width = 44.dp, height = 58.dp)
                .offset(x = 6.dp, y = 0.dp)
                .shadow(4.dp, RoundedCornerShape(4.dp))
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text("Panoramic", fontSize = 5.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Stitched long canvas. Beautiful continuous vertical flow for easy sharing.", fontSize = 3.5.sp, color = Color.DarkGray, lineHeight = 5.sp, maxLines = 5)
            }
        }

        // Sky Blue Long Image badge icon in upper left of the stacked card
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF0284C7),
            modifier = Modifier
                .size(20.dp)
                .offset(x = (-16).dp, y = (-14).dp)
                .shadow(2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PhotoSizeSelectActual,
                    contentDescription = null,
                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Small Cyan badge at top right
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = AccentSkyBlue,
            modifier = Modifier
                .size(14.dp)
                .offset(x = 24.dp, y = (-18).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("LI", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Document Row in the Selection List (with Cyan Long Image badge on thumbnail)
 */
@Composable
private fun PdfToLongImageDocRow(
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("long_image_doc_item_${doc.id}"),
        color = if (isDark) DarkCardSurface else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDark) DarkCardBorder else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview card with cyan badge on top-left
            Box(modifier = Modifier.size(width = 48.dp, height = 62.dp)) {
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

                // Cyan Long Image icon badge on top left corner
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = PdfBadgeCyan,
                    modifier = Modifier
                        .size(17.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "LI",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 8.5.sp,
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
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${doc.pageCount} pages",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentSkyBlue
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentSkyBlue.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentSkyBlue.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Stitch",
                    color = AccentSkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Long Image Stitching Configuration Options Model
 */
data class LongImageConfig(
    val spacingPx: Int = 16,            // 0 (Seamless), 16 (Standard), 32 (Spacious)
    val cornerRadiusDp: Int = 12,        // 0 (Flush), 12 (Rounded cards)
    val canvasBgColorHex: String = "#16181A", // "#FFFFFF", "#16181A", "#FFFDF8", "#F1F5F9"
    val outputWidthPx: Int = 1080,      // 1080, 1440, 2160
    val format: String = "JPG",         // "JPG", "PNG"
    val includeHeaderBanner: Boolean = true,
    val showPageNumberBadges: Boolean = true,
    val saveToGallery: Boolean = true,
    val saveAsAppDocument: Boolean = true
)

/**
 * PDF to Long Image Setup & Options Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfToLongImageStudioSheet(
    doc: DocumentEntity,
    viewModel: ScannerViewModel,
    onDismiss: () -> Unit,
    onStartStitching: (LongImageConfig, List<PageEntity>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themePalette = rememberAppThemePalette()

    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val selectedPageIds = remember { mutableStateListOf<Long>() }
    var isLoadingPages by remember { mutableStateOf(true) }

    // Multi-page selection mode: 0 = All Pages, 1 = Select Pages, 2 = Page Range
    var pageSelectionMode by remember { mutableIntStateOf(0) }
    var rangeStartInput by remember { mutableStateOf("1") }
    var rangeEndInput by remember { mutableStateOf("1") }

    // Long Image Stitching Options
    var selectedSpacing by remember { mutableIntStateOf(16) } // 0, 16, 32
    var selectedCornerRadius by remember { mutableIntStateOf(12) } // 0, 12
    var selectedBgColor by remember { mutableStateOf(if (themePalette.isDark) "#16181A" else "#FFFFFF") }
    var selectedWidth by remember { mutableIntStateOf(1080) } // 1080, 1440, 2160
    var selectedFormat by remember { mutableStateOf("JPG") } // "JPG", "PNG"
    var includeHeaderBanner by remember { mutableStateOf(true) }
    var showPageNumberBadges by remember { mutableStateOf(true) }
    var saveToGallery by remember { mutableStateOf(true) }
    var saveAsAppDocument by remember { mutableStateOf(true) }

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
                        text = "Long Image Stitcher Studio",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Text(
                        text = "${doc.title} (${doc.pageCount} pages)",
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
                text = "1. Page Selection (${if (pageSelectionMode == 0) pages.size else selectedPageIds.size} of ${pages.size} selected)",
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
                        selectedContainerColor = AccentSkyBlue,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = pageSelectionMode == 1,
                    onClick = { pageSelectionMode = 1 },
                    label = { Text("Custom Select") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSkyBlue,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = pageSelectionMode == 2,
                    onClick = { pageSelectionMode = 2 },
                    label = { Text("Page Range") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentSkyBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }

            // Interactive Page Grid if "Custom Select" mode is active
            if (pageSelectionMode == 1) {
                if (isLoadingPages) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentSkyBlue)
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
                                    if (isSelected) AccentSkyBlue else if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)
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

                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
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

                                    if (isSelected) {
                                        Surface(
                                            color = AccentSkyBlue,
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
                            focusedBorderColor = AccentSkyBlue,
                            focusedLabelColor = AccentSkyBlue
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
                            focusedBorderColor = AccentSkyBlue,
                            focusedLabelColor = AccentSkyBlue
                        )
                    )
                }
            }

            // SECTION 2: STITCHING SPACING & LAYOUT
            Text(
                text = "2. Page Seam & Spacing",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "Seamless (0px)",
                    16 to "Standard (16px)",
                    32 to "Spacious (32px)"
                ).forEach { (spacing, label) ->
                    val isSelected = selectedSpacing == spacing
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentSkyBlue.copy(alpha = 0.15f) else if (themePalette.isDark) DarkCardSurface else Color(0xFFF1F5F9),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) AccentSkyBlue else if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedSpacing = spacing
                                if (spacing == 0) selectedCornerRadius = 0
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AccentSkyBlue else if (themePalette.isDark) TextPrimaryWhite else Color(0xFF0F172A),
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // SECTION 3: BACKGROUND CANVAS COLOR
            Text(
                text = "3. Background Canvas",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "#FFFFFF" to "White",
                    "#16181A" to "Dark",
                    "#FFFDF8" to "Warm",
                    "#F1F5F9" to "Gray"
                ).forEach { (hex, name) ->
                    val isSelected = selectedBgColor.equals(hex, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentSkyBlue.copy(alpha = 0.15f) else if (themePalette.isDark) DarkCardSurface else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) AccentSkyBlue else if (themePalette.isDark) DarkCardBorder else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedBgColor = hex }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (hex) {
                                            "#FFFFFF" -> Color.White
                                            "#16181A" -> Color(0xFF16181A)
                                            "#FFFDF8" -> Color(0xFFFFFDF8)
                                            else -> Color(0xFFF1F5F9)
                                        }
                                    )
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (themePalette.isDark) TextPrimaryWhite else Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            // SECTION 4: RESOLUTION & FORMAT
            Text(
                text = "4. Resolution & Format",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1080 to "1080p HD",
                    1440 to "1440p 2K",
                    2160 to "2160p 4K"
                ).forEach { (w, label) ->
                    FilterChip(
                        selected = selectedWidth == w,
                        onClick = { selectedWidth = w },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentSkyBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                listOf("JPG", "PNG").forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { selectedFormat = fmt },
                        label = { Text(fmt, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // SECTION 5: HEADER BANNER & EXPORT OPTIONS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (themePalette.isDark) DarkCardSurface else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Include Title Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include Document Header",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Adds document title & timestamp bar at the top",
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = includeHeaderBanner,
                            onCheckedChange = { includeHeaderBanner = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentSkyBlue)
                        )
                    }

                    HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

                    // Show Page Numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Page Number Badges",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Labels each page segment in the continuous image",
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = showPageNumberBadges,
                            onCheckedChange = { showPageNumberBadges = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentSkyBlue)
                        )
                    }

                    HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

                    // Save to Gallery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save to Device Photos / Gallery",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Export directly into Pictures/DocScanner",
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = saveToGallery,
                            onCheckedChange = { saveToGallery = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentSkyBlue)
                        )
                    }

                    HorizontalDivider(color = if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0))

                    // Save as Document in App
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save as New Document in App",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                            )
                            Text(
                                text = "Keep a copy in your DocScan document library",
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = saveAsAppDocument,
                            onCheckedChange = { saveAsAppDocument = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentSkyBlue)
                        )
                    }
                }
            }

            // PRIMARY ACTION BUTTON
            val targetPages = remember(pageSelectionMode, pages, selectedPageIds.toList(), rangeStartInput, rangeEndInput) {
                when (pageSelectionMode) {
                    0 -> pages
                    1 -> pages.filter { selectedPageIds.contains(it.id) }
                    2 -> {
                        val start = (rangeStartInput.toIntOrNull() ?: 1).coerceIn(1, pages.size.coerceAtLeast(1))
                        val end = (rangeEndInput.toIntOrNull() ?: pages.size).coerceIn(start, pages.size.coerceAtLeast(1))
                        pages.subList((start - 1).coerceAtLeast(0), end.coerceAtMost(pages.size))
                    }
                    else -> pages
                }
            }

            Button(
                onClick = {
                    val config = LongImageConfig(
                        spacingPx = selectedSpacing,
                        cornerRadiusDp = selectedCornerRadius,
                        canvasBgColorHex = selectedBgColor,
                        outputWidthPx = selectedWidth,
                        format = selectedFormat,
                        includeHeaderBanner = includeHeaderBanner,
                        showPageNumberBadges = showPageNumberBadges,
                        saveToGallery = saveToGallery,
                        saveAsAppDocument = saveAsAppDocument
                    )
                    onStartStitching(config, targetPages)
                },
                enabled = targetPages.isNotEmpty() && !isLoadingPages,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_stitch_long_image_submit"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentSkyBlue)
            ) {
                Icon(imageVector = Icons.Default.PhotoSizeSelectActual, contentDescription = null, tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stitch ${targetPages.size} Page(s) into Long Image",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                )
            }
        }
    }
}

/**
 * Result data class for Long Image stitching operation
 */
private data class LongImageResult(
    val success: Boolean,
    val stitchedFile: File? = null,
    val createdDocId: Long? = null,
    val errorMessage: String? = null
)

/**
 * Background Coroutine task to stitch selected pages into a single continuous high-res long image
 */
private suspend fun stitchPagesToLongImage(
    context: android.content.Context,
    doc: DocumentEntity,
    pages: List<PageEntity>,
    config: LongImageConfig,
    viewModel: ScannerViewModel,
    onProgress: (Float, String) -> Unit
): LongImageResult = withContext(Dispatchers.Default) {
    try {
        if (pages.isEmpty()) {
            return@withContext LongImageResult(success = false, errorMessage = "No pages selected to stitch")
        }

        onProgress(0.1f, "Loading page bitmaps...")

        val loadedBitmaps = mutableListOf<Bitmap>()
        for ((idx, page) in pages.withIndex()) {
            onProgress(0.1f + (0.4f * (idx.toFloat() / pages.size)), "Loading page ${idx + 1} of ${pages.size}...")
            val file = File(page.processedImagePath)
            var bmp: Bitmap? = null
            if (file.exists()) {
                bmp = BitmapFactory.decodeFile(file.absolutePath)
            } else if (page.originalImagePath.isNotBlank()) {
                val origFile = File(page.originalImagePath)
                if (origFile.exists()) {
                    bmp = BitmapFactory.decodeFile(origFile.absolutePath)
                }
            }

            if (bmp != null) {
                loadedBitmaps.add(bmp)
            }
        }

        if (loadedBitmaps.isEmpty()) {
            return@withContext LongImageResult(success = false, errorMessage = "Could not decode any page images for stitching")
        }

        onProgress(0.55f, "Calculating long image canvas layout...")

        val targetWidth = config.outputWidthPx
        val spacing = config.spacingPx
        val padding = if (spacing > 0) spacing else 0

        // Scale heights
        val scaledHeights = loadedBitmaps.map { bmp ->
            val scale = targetWidth.toFloat() / bmp.width.toFloat()
            (bmp.height * scale).toInt()
        }

        // Header height if enabled
        val headerHeight = if (config.includeHeaderBanner) 160 else 0

        // Total calculated height
        var totalCanvasHeight = headerHeight + padding * 2
        for (h in scaledHeights) {
            totalCanvasHeight += h
        }
        if (loadedBitmaps.size > 1) {
            totalCanvasHeight += spacing * (loadedBitmaps.size - 1)
        }

        onProgress(0.65f, "Rendering high-res continuous image (${targetWidth}x${totalCanvasHeight}px)...")

        // Create Master Canvas Bitmap
        val masterBitmap = Bitmap.createBitmap(targetWidth, totalCanvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(masterBitmap)

        // Draw Canvas Background
        val bgColorInt = try {
            AndroidColor.parseColor(config.canvasBgColorHex)
        } catch (e: Exception) {
            AndroidColor.parseColor("#16181A")
        }
        canvas.drawColor(bgColorInt)

        var currentY = padding.toFloat()

        // 1. Draw Header Banner if enabled
        if (config.includeHeaderBanner) {
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.canvasBgColorHex.equals("#FFFFFF", true)) AndroidColor.parseColor("#0284C7") else AndroidColor.parseColor("#0369A1")
            }
            val headerRect = RectF(0f, 0f, targetWidth.toFloat(), headerHeight.toFloat())
            canvas.drawRect(headerRect, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 44f
                isFakeBoldText = true
            }
            canvas.drawText(doc.title.take(40), 40f, 75f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#E0F2FE")
                textSize = 28f
            }
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("${pages.size} Pages Stitched | $dateStr | DocScan", 40f, 125f, subPaint)

            currentY = headerHeight.toFloat() + spacing.toFloat()
        }

        // 2. Draw each page image
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (i in loadedBitmaps.indices) {
            onProgress(0.7f + (0.2f * (i.toFloat() / loadedBitmaps.size)), "Stitching segment ${i + 1} of ${loadedBitmaps.size}...")
            val bmp = loadedBitmaps[i]
            val sHeight = scaledHeights[i]

            val destRect = RectF(
                (if (spacing > 0) padding else 0).toFloat(),
                currentY,
                (targetWidth - (if (spacing > 0) padding else 0)).toFloat(),
                currentY + sHeight
            )

            if (config.cornerRadiusDp > 0 && spacing > 0) {
                // Draw rounded page card
                val radius = config.cornerRadiusDp * 3f
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.save()
                val path = android.graphics.Path().apply {
                    addRoundRect(destRect, radius, radius, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(path)
                canvas.drawBitmap(bmp, null, destRect, paint)
                canvas.restore()

                // Subtle card border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = AndroidColor.parseColor("#334155")
                }
                canvas.drawRoundRect(destRect, radius, radius, borderPaint)
            } else {
                // Direct flush draw
                canvas.drawBitmap(bmp, null, destRect, paint)
            }

            // Draw optional Page Number Badge
            if (config.showPageNumberBadges) {
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.parseColor("#80000000")
                }
                val badgeRect = RectF(
                    destRect.right - 130f,
                    destRect.bottom - 45f,
                    destRect.right - 15f,
                    destRect.bottom - 15f
                )
                canvas.drawRoundRect(badgeRect, 10f, 10f, badgePaint)

                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    textSize = 22f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("P.${i + 1}/${loadedBitmaps.size}", badgeRect.centerX(), badgeRect.centerY() + 8f, badgeTextPaint)
            }

            currentY += sHeight + spacing
        }

        onProgress(0.9f, "Saving and compressing long image file...")

        // Save Master Bitmap to File
        val baseCleanName = doc.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val ext = if (config.format.equals("PNG", ignoreCase = true)) "png" else "jpg"
        val compressFmt = if (config.format.equals("PNG", ignoreCase = true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outputFile = File(FileUtils.getDocumentsDir(context), "${baseCleanName}_LongImage_${System.currentTimeMillis()}.$ext")

        FileOutputStream(outputFile).use { out ->
            masterBitmap.compress(compressFmt, 92, out)
        }

        // Save to Gallery if enabled
        if (config.saveToGallery) {
            FileUtils.saveBitmapToGallery(context, masterBitmap, "${doc.title}_LongImage")
        }

        // Save as new App Document if enabled
        var createdDocId: Long? = null
        if (config.saveAsAppDocument) {
            val newPage = PageEntity(
                documentId = 0L,
                pageNumber = 1,
                originalImagePath = outputFile.absolutePath,
                processedImagePath = outputFile.absolutePath
            )
            createdDocId = viewModel.saveNewDocument(
                title = "${doc.title} (Long Image)",
                folder = "Long Images",
                pages = listOf(newPage)
            )
        }

        onProgress(1.0f, "Done!")

        LongImageResult(
            success = true,
            stitchedFile = outputFile,
            createdDocId = createdDocId
        )
    } catch (e: Exception) {
        e.printStackTrace()
        LongImageResult(success = false, errorMessage = "Error stitching long image: ${e.message}")
    }
}

/**
 * Success Dialog & Stitched Long Image Viewer
 */
@Composable
private fun PdfToLongImageSuccessDialog(
    stitchedFile: File,
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
            shape = RoundedCornerShape(18.dp),
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
                    color = AccentSkyBlue.copy(alpha = 0.15f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Long Image Stitched!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                )

                Text(
                    text = "Continuous panoramic image created and saved.",
                    fontSize = 13.sp,
                    color = TextSecondaryMuted,
                    textAlign = TextAlign.Center
                )

                // Image Panoramic Preview Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (themePalette.isDark) DarkCardSurface else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onZoomImage(stitchedFile) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(stitchedFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Stitched Long Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Tap to view full length",
                                color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom",
                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share Long Image Button
                    OutlinedButton(
                        onClick = {
                            FileUtils.shareFile(context, stitchedFile, "image/*", "Share Long Image")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentSkyBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    // Save to Gallery Button
                    Button(
                        onClick = {
                            val bitmap = BitmapFactory.decodeFile(stitchedFile.absolutePath)
                            if (bitmap != null) {
                                FileUtils.saveBitmapToGallery(context, bitmap, "${docTitle}_LongImage")
                                Toast.makeText(context, "Saved to Gallery / Photos!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSkyBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }

                if (newlyCreatedDocId != null) {
                    Button(
                        onClick = { onViewDocument(newlyCreatedDocId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in App Library", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = TextSecondaryMuted)
                }
            }
        }
    }
}
