package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.components.CompressionSettingsDialog
import com.docscan.ui.components.PdfQualityComparisonCard
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.CompressedPdfResult
import com.docscan.util.CompressionConfig
import com.docscan.util.CompressionLevel
import com.docscan.util.CompressionSettings
import com.docscan.util.CompressionSettingsManager
import com.docscan.util.FileUtils
import com.docscan.util.PdfCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme Color Tokens
private val CompressDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF141619), Color(0xFFF8FAFC))
private val CompressSurface: Color get() = ThemeManager.themeColor(Color(0xFF1F2227), Color(0xFFFFFFFF))
private val CompressTeal = Color(0xFF00C48C)
private val CompressTealLight = Color(0xFF00D2A0)
private val CompressTealDim = Color(0x1F00C48C)
private val CompressBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val CompressTextPrimary: Color get() = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
private val CompressTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9EABB8), Color(0xFF475569))
private val CompressTextMuted: Color get() = ThemeManager.themeColor(Color(0xFF677282), Color(0xFF64748B))

enum class CompressWorkflowStep {
    SELECT,     // Screen 1: Choose file from Device or App
    CONFIGURE,  // Screen 2: Select compression level (Medium / High)
    PREVIEW     // Screen 3: View compressed PDF & multi-page swipe & share
}

@Composable
fun CompressPdfScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onOpenDocumentDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDocuments by viewModel.allDocuments.collectAsState(initial = emptyList())

    // Multi-Step Workflow State
    var currentStep by remember { mutableStateOf(CompressWorkflowStep.SELECT) }

    // Selected Document & Compression State
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedCompressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var compressionSettings by remember { mutableStateOf(CompressionSettingsManager.loadSettings(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSavedToLibrary by remember { mutableStateOf(false) }
    var compressedResult by remember { mutableStateOf<CompressedPdfResult?>(null) }
    var sourceSizeBytes by remember { mutableStateOf(0L) }

    // Auto-select initialDocId if provided
    LaunchedEffect(initialDocId, allDocuments) {
        if (initialDocId != null && selectedDocument == null && allDocuments.isNotEmpty()) {
            val target = allDocuments.find { it.id == initialDocId }
            if (target != null) {
                selectedDocument = target
                currentStep = CompressWorkflowStep.CONFIGURE
            }
        }
    }

    LaunchedEffect(selectedDocument?.id) {
        val doc = selectedDocument ?: run {
            sourceSizeBytes = 0L
            return@LaunchedEffect
        }
        val pages = viewModel.getPagesForDocumentDirect(doc.id)
        sourceSizeBytes = pages.sumOf { page ->
            val processed = File(page.processedImagePath)
            val original = File(page.originalImagePath)
            when {
                processed.exists() && processed.length() > 0L -> processed.length()
                original.exists() && original.length() > 0L -> original.length()
                else -> 0L
            }
        }
    }

    // Loading & Progress States
    var isCompressing by remember { mutableStateOf(false) }
    var compressionProgress by remember { mutableFloatStateOf(0f) }
    var compressionStatusText by remember { mutableStateOf("Compressing PDF...") }

    // Handle Hardware & Gesture Back Navigation
    BackHandler {
        when (currentStep) {
            CompressWorkflowStep.PREVIEW -> currentStep = CompressWorkflowStep.CONFIGURE
            CompressWorkflowStep.CONFIGURE -> currentStep = CompressWorkflowStep.SELECT
            CompressWorkflowStep.SELECT -> onNavigateBack()
        }
    }

    // System File Picker for Screen 1: "Create or Import -> Device"
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isCompressing = true
            compressionStatusText = "Importing file from device..."
            coroutineScope.launch {
                try {
                    val importedDocs = viewModel.importFilesForMerge(uris)
                    isCompressing = false
                    val first = importedDocs.firstOrNull()
                    if (first != null) {
                        selectedDocument = first
                        currentStep = CompressWorkflowStep.CONFIGURE
                    } else {
                        Toast.makeText(context, "Could not load selected document.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    isCompressing = false
                    Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CompressDarkBg)
    ) {
        when (currentStep) {
            CompressWorkflowStep.SELECT -> {
                CompressFileSelectionScreen(
                    allDocuments = allDocuments,
                    onNavigateBack = onNavigateBack,
                    onDeviceClick = {
                        filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                    },
                    onDocumentSelected = { doc ->
                        selectedDocument = doc
                        currentStep = CompressWorkflowStep.CONFIGURE
                    }
                )
            }

            CompressWorkflowStep.CONFIGURE -> {
                selectedDocument?.let { doc ->
                    CompressLevelSelectionScreen(
                        document = doc,
                        selectedLevel = selectedCompressionLevel,
                        settings = compressionSettings,
                        sourceSizeBytes = sourceSizeBytes,
                        onLevelSelected = { level ->
                            selectedCompressionLevel = level
                            val updated = CompressionSettings.fromLevel(level).copy(
                                cleanBackground = compressionSettings.cleanBackground,
                                sharpenText = compressionSettings.sharpenText
                            )
                            compressionSettings = updated
                            CompressionSettingsManager.saveSettings(context, updated)
                        },
                        onOpenSettings = {
                            showSettingsDialog = true
                        },
                        onNavigateBack = {
                            currentStep = CompressWorkflowStep.SELECT
                        },
                        onCompressClick = {
                            isCompressing = true
                            compressionProgress = 0f
                            compressionStatusText = "Compressing PDF..."

                            coroutineScope.launch {
                                val pages = viewModel.getPagesForDocumentDirect(doc.id)
                                val result = PdfCompressor.compressDocument(
                                    context = context,
                                    documentTitle = doc.title,
                                    pages = pages,
                                    config = compressionSettings.toConfig(),
                                    onProgress = { fraction, status ->
                                        compressionProgress = fraction
                                        compressionStatusText = status
                                    }
                                )
                                isCompressing = false
                                if (result != null) {
                                    compressedResult = result
                                    isSavedToLibrary = false
                                    currentStep = CompressWorkflowStep.PREVIEW
                                } else {
                                    Toast.makeText(context, "Compression failed. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }

            CompressWorkflowStep.PREVIEW -> {
                compressedResult?.let { result ->
                    val doc = selectedDocument
                    CompressedPdfPreviewScreen(
                        documentTitle = doc?.title ?: "Document",
                        compressedResult = result,
                        isSaved = isSavedToLibrary,
                        onSaveDocument = {
                            if (doc != null && !isSavedToLibrary) {
                                coroutineScope.launch {
                                    try {
                                        val compressedPages = result.previewBitmaps.mapIndexed { idx, bmp ->
                                            val path = FileUtils.saveBitmapToDocStorage(context, bmp, "COMPRESSED_${doc.id}_${idx + 1}")
                                            PageEntity(
                                                documentId = 0L,
                                                pageNumber = idx + 1,
                                                originalImagePath = path,
                                                processedImagePath = path
                                            )
                                        }
                                        if (compressedPages.isNotEmpty()) {
                                            viewModel.saveNewDocument(
                                                title = "${doc.title} (Compressed)",
                                                folder = "Compressed",
                                                pages = compressedPages
                                            )
                                            isSavedToLibrary = true
                                            Toast.makeText(context, "Saved compressed document to Library & Files!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onOpenSettings = {
                            showSettingsDialog = true
                        },
                        onNavigateBack = {
                            currentStep = CompressWorkflowStep.CONFIGURE
                        }
                    )
                }
            }
        }

        // Settings Dialog for custom quality level and compression fine-tuning
        if (showSettingsDialog) {
            CompressionSettingsDialog(
                initialSettings = compressionSettings,
                onDismiss = { showSettingsDialog = false },
                onApplySettings = { newSettings ->
                    compressionSettings = newSettings
                    selectedCompressionLevel = newSettings.level
                    showSettingsDialog = false

                    // If currently in PREVIEW and document selected, re-compress with new settings immediately!
                    if (currentStep == CompressWorkflowStep.PREVIEW && selectedDocument != null) {
                        val doc = selectedDocument!!
                        isCompressing = true
                        compressionProgress = 0f
                        compressionStatusText = "Re-compressing with new settings..."
                        coroutineScope.launch {
                            val pages = viewModel.getPagesForDocumentDirect(doc.id)
                            val result = PdfCompressor.compressDocument(
                                context = context,
                                documentTitle = doc.title,
                                pages = pages,
                                config = newSettings.toConfig(),
                                onProgress = { fraction, status ->
                                    compressionProgress = fraction
                                    compressionStatusText = status
                                }
                            )
                            isCompressing = false
                            if (result != null) {
                                compressedResult = result
                                isSavedToLibrary = false
                            } else {
                                Toast.makeText(context, "Re-compression failed. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        // Global Compression / Processing Progress Dialog Overlay
        if (isCompressing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .zIndex(100f)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CompressSurface,
                    border = BorderStroke(1.dp, CompressTeal.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = CompressTeal,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Compressing PDF...",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = compressionStatusText,
                            color = CompressTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// SCREEN 1: COMPRESS FILE SELECTION SCREEN (Matching Reference Screenshot 1)
// =============================================================================

@Composable
private fun CompressFileSelectionScreen(
    allDocuments: List<DocumentEntity>,
    onNavigateBack: () -> Unit,
    onDeviceClick: () -> Unit,
    onDocumentSelected: (DocumentEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. TOP HERO SECTION
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Top Left Back Arrow
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("button_compress_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hero Content: Title, Subtitle on Left, Illustration on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Compress",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Reduce file size to save space.",
                            color = CompressTextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Original Styled Hero Illustration
                    CompressHeroIllustration(
                        modifier = Modifier.size(width = 112.dp, height = 98.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. CREATE OR IMPORT SECTION
                Text(
                    text = "Create or Import",
                    color = CompressTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Large Device Button Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CompressSurface,
                    border = BorderStroke(1.dp, CompressBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("button_import_device")
                        .clickable { onDeviceClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Blue Device / Folder Icon Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Device",
                                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Device",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. SELECT FROM THIS APP SECTION
                Text(
                    text = "Select from This App",
                    color = CompressTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        // Empty App Documents State
        if (allDocuments.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = CompressTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No documents found in DocScanner",
                        color = CompressTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Device' above to import a PDF to compress",
                        color = CompressTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // Scrollable Document List (Tapping any row immediately selects document & opens Screen 2)
            items(
                items = allDocuments,
                key = { doc -> doc.id }
            ) { doc ->
                CompressDocumentItem(
                    document = doc,
                    onClick = { onDocumentSelected(doc) }
                )
            }
        }
    }
}

/**
 * Single document item in Screen 1 list.
 */
@Composable
private fun CompressDocumentItem(
    document: DocumentEntity,
    onClick: () -> Unit
) {
    // Format date matching reference screenshot: e.g. "27/08/2026 1:12 pm"
    val dateText = remember(document.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
        sdf.format(Date(document.createdAt)).lowercase()
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("item_doc_${document.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Document Thumbnail (56dp x 72dp rounded)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CompressSurface,
                border = BorderStroke(1.dp, CompressBorder),
                modifier = Modifier.size(width = 56.dp, height = 72.dp)
            ) {
                if (!document.thumbnailPath.isNullOrBlank() && File(document.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(document.thumbnailPath),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = CompressTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center Document Title & Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        color = CompressTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = " | ",
                        color = Color(0xFF4B5563),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "📄 ${document.pageCount}",
                        color = CompressTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Original Hero Illustration matching the card in Screenshot 1.
 */
@Composable
private fun CompressHeroIllustration(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.shadow(6.dp, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Docs Done Right",
                    color = Color(0xFF1E293B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "All in one app. Convert,",
                    color = Color(0xFF64748B),
                    fontSize = 6.sp,
                    maxLines = 1
                )
            }

            // Size comparison chips: 5.8MB -> 2.6MB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 5.8MB tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(0.5.dp, Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = "5.8MB",
                        color = Color(0xFF64748B),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }

                // Arrow
                Text(
                    text = "→",
                    color = CompressTeal,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                // 2.6MB tag (Teal highlighted)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(0.5.dp, CompressTeal.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "2.6MB",
                        color = Color(0xFF047857),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = "reduced storage with ease.",
                color = Color(0xFF94A3B8),
                fontSize = 5.sp,
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// SCREEN 2: SELECT COMPRESSION LEVEL (Matching Reference Screenshot 2)
// =============================================================================

@Composable
private fun CompressLevelSelectionScreen(
    document: DocumentEntity,
    selectedLevel: CompressionLevel,
    settings: CompressionSettings,
    sourceSizeBytes: Long,
    onLevelSelected: (CompressionLevel) -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onCompressClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar: "← Compress" + Settings Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("button_level_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Compress",
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("button_open_settings_topbar")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = CompressTeal
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center: Selected Document Large Icon & Document Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Circular Teal Badge (~76dp)
            Surface(
                shape = CircleShape,
                color = CompressTeal,
                modifier = Modifier
                    .size(76.dp)
                    .shadow(12.dp, CircleShape, spotColor = CompressTeal.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val w = size.width
                        val h = size.height

                        // White document body with folded top-right corner
                        val path = Path().apply {
                            moveTo(w * 0.15f, h * 0.1f)
                            lineTo(w * 0.62f, h * 0.1f)
                            lineTo(w * 0.85f, h * 0.33f)
                            lineTo(w * 0.85f, h * 0.9f)
                            lineTo(w * 0.15f, h * 0.9f)
                            close()
                        }
                        drawPath(path, Color.White)

                        // Fold corner outline
                        val foldPath = Path().apply {
                            moveTo(w * 0.62f, h * 0.1f)
                            lineTo(w * 0.62f, h * 0.33f)
                            lineTo(w * 0.85f, h * 0.33f)
                        }
                        drawPath(foldPath, CompressTeal, style = Stroke(width = 2.5f))

                        // Horizontal lines representing text
                        for (i in 1..3) {
                            drawLine(
                                color = CompressTeal,
                                start = Offset(w * 0.28f, h * 0.38f + i * h * 0.12f),
                                end = Offset(w * 0.72f, h * 0.38f + i * h * 0.12f),
                                strokeWidth = 2.5f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Document Name
            Text(
                text = document.title,
                color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Compression Level Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Select compression level:",
                color = CompressTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Best Quality (Maximum Clarity)
            CompressionOptionCard(
                title = "Best Quality",
                subtitle = "Near-original detail • safest for photos, signatures & small text",
                isSelected = (selectedLevel == CompressionLevel.LOW),
                onClick = { onLevelSelected(CompressionLevel.LOW) },
                testTag = "option_low"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 2: Medium (Default selected - Recommended)
            CompressionOptionCard(
                title = "Medium (Recommended)",
                subtitle = "High fidelity with a moderate size reduction",
                isSelected = (selectedLevel == CompressionLevel.MEDIUM),
                onClick = { onLevelSelected(CompressionLevel.MEDIUM) },
                testTag = "option_medium"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 3: High
            CompressionOptionCard(
                title = "High",
                subtitle = "Stronger reduction while protecting readable detail",
                isSelected = (selectedLevel == CompressionLevel.HIGH),
                onClick = { onLevelSelected(CompressionLevel.HIGH) },
                testTag = "option_high"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 4: Custom Settings & Fine-Tuning Menu
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CompressSurface,
                border = BorderStroke(1.dp, CompressBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .testTag("button_open_compression_settings")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CompressTealDim,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Settings",
                                    tint = CompressTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Compression Settings",
                                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CompressTeal.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${settings.effectiveQuality}% Quality",
                                        color = CompressTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Max dimension: ${settings.effectiveMaxDimension}px • Clean bg: ${if (settings.effectiveCleanBackground) "On" else "Off"}",
                                color = CompressTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Edit Settings",
                        tint = CompressTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val estimatedBytes = remember(sourceSizeBytes, selectedLevel) {
            PdfCompressor.estimateOutputSize(sourceSizeBytes, selectedLevel)
        }
        val sizeLabel = if (estimatedBytes > 0L) PdfCompressor.formatFileSize(estimatedBytes) else ""

        // Fixed Bottom Compress Button
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onCompressClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CompressTeal,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = CompressTeal.copy(alpha = 0.5f))
                    .testTag("button_start_compress")
            ) {
                Text(
                    text = if (sizeLabel.isNotBlank()) "Compress  •  ~$sizeLabel" else "Compress",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Selectable Compression Option Card (Medium / High)
 */
@Composable
private fun CompressionOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, CompressTeal)
    } else {
        BorderStroke(1.dp, CompressBorder)
    }

    val cardBg = if (isSelected) {
        CompressTealDim
    } else {
        CompressSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) CompressTeal else CompressTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = CompressTextSecondary,
                    fontSize = 13.sp
                )
            }

            // Visible Teal Checkmark on Right when Selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = CompressTeal,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 3: COMPRESSED PDF PREVIEW (Matching Reference Screenshot 3)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressedPdfPreviewScreen(
    documentTitle: String,
    compressedResult: CompressedPdfResult,
    isSaved: Boolean,
    onSaveDocument: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val totalPages = compressedResult.pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { totalPages })

    var showShareBottomSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar: "← Document Name" + Status & Settings Tune Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("button_preview_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = documentTitle,
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSaved) "Saved to Library • Quality Checked" else "Compare Quality Before Saving",
                        color = if (isSaved) CompressTeal else CompressTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("button_preview_tune")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = CompressTeal
                )
            }
        }

        // PDF COMPARISON & PREVIEW AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CompressDarkBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Interactive Comparison Card (Split slider, Side-by-side, Toggle, Size Difference)
                PdfQualityComparisonCard(
                    result = compressedResult,
                    currentPageIndex = pagerState.currentPage,
                    onPageChange = { targetPage ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onOpenSettings = onOpenSettings
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // BOTTOM ACTION BAR: Save before share or Share once saved
        Surface(
            color = CompressSurface,
            border = BorderStroke(1.dp, CompressBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                if (!isSaved) {
                    // Primary Save Button
                    Button(
                        onClick = onSaveDocument,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CompressTeal,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = CompressTeal.copy(alpha = 0.5f))
                            .testTag("button_save_compressed_document")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Compressed Document (${compressedResult.formattedSize})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Actions: Adjust Quality Settings & Share Direct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CompressBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("button_adjust_quality")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CompressTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adjust Quality",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { showShareBottomSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CompressTealDim,
                                contentColor = CompressTeal
                            ),
                            border = BorderStroke(1.dp, CompressTeal.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("button_share_compressed")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = CompressTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share PDF",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Saved Success Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, CompressTeal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CompressTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saved to Library & Files as \"${documentTitle} (Compressed)\"",
                                color = CompressTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Share Button (Full Width)
                    Button(
                        onClick = { showShareBottomSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CompressTeal,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("button_share_compressed")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share PDF (${compressedResult.formattedSize})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }

    // SHARE / SAVE OPTIONS BOTTOM SHEET
    if (showShareBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareBottomSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = CompressSurface,
            scrimColor = Color(0x99000000)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = documentTitle,
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Compressed Size: ${compressedResult.formattedSize}",
                            color = CompressTeal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CompressTealDim
                    ) {
                        Text(
                            text = "${compressedResult.pageCount} Pages",
                            color = CompressTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CompressBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Share PDF (Native Android Sharesheet)
                ShareActionItem(
                    title = "Share PDF",
                    subtitle = "Send via WhatsApp, Gmail, Drive, etc.",
                    icon = Icons.Default.Share,
                    iconBgColor = CompressTealDim,
                    iconTint = CompressTeal,
                    onClick = {
                        showShareBottomSheet = false
                        PdfCompressor.sharePdf(context, compressedResult.file)
                    }
                )

                // Option 2: Save to Device
                ShareActionItem(
                    title = "Save to Device",
                    subtitle = "Export to Downloads / DocScanner folder",
                    icon = Icons.Default.Download,
                    iconBgColor = Color(0xFF2563EB).copy(alpha = 0.15f),
                    iconTint = Color(0xFF3B82F6),
                    onClick = {
                        showShareBottomSheet = false
                        PdfCompressor.savePdfToDevice(context, compressedResult.file)
                    }
                )

                // Option 3: Open in PDF Viewer
                ShareActionItem(
                    title = "Open in PDF Viewer",
                    subtitle = "View with default or external PDF reader",
                    icon = Icons.Default.OpenInNew,
                    iconBgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                    iconTint = Color(0xFF10B981),
                    onClick = {
                        showShareBottomSheet = false
                        PdfCompressor.openPdf(context, compressedResult.file)
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBgColor,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = CompressTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
