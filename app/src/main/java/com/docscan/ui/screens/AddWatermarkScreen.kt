package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// 10 Watermark Colors matching State 3 reference screenshot
private val WatermarkPalette = listOf(
    Color(0xFF000000), // Black
    Color(0xFFFFFFFF), // White
    Color(0xFF00C48C), // Teal / Green
    Color(0xFFF97316), // Orange
    Color(0xFFEF4444), // Coral / Red
    Color(0xFF3B82F6), // Blue
    Color(0xFFFEF08A), // Pale Yellow
    Color(0xFFFACC15), // Bright Yellow
    Color(0xFF84CC16), // Lime Green
    Color(0xFF15803D)  // Dark Green
)

/**
 * Root Add Watermark feature workflow.
 * State 1: Document Selection / Gallery / Device import.
 * State 2 & 3: Watermark Preview, Adjust Panel, Live Preview & Apply.
 */
@Composable
fun AddWatermarkScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit
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
        // State 1: Document Selection Screen matching Reference Left Screenshot
        AddWatermarkDocSelectScreen(
            documents = documents,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onDocumentSelected = { doc ->
                selectedDocument = doc
            }
        )
    } else {
        // State 2 & 3: Watermark Preview & Editor Screen matching Reference Middle & Right Screenshots
        WatermarkEditorScreen(
            document = selectedDocument!!,
            viewModel = viewModel,
            onNavigateBack = {
                if (initialDocId != null) {
                    onNavigateBack()
                } else {
                    selectedDocument = null
                }
            },
            onSaveSuccess = {
                onNavigateBack()
            }
        )
    }
}

/**
 * STATE 1: Add Watermark Document Selection Screen (Reference Left Side)
 */
@Composable
fun AddWatermarkDocSelectScreen(
    documents: List<DocumentEntity>,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onDocumentSelected: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    // Launcher for Gallery import
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    val loaded = FileUtils.loadBitmapsFromUri(context, uri)
                    bitmaps.addAll(loaded)
                }
                if (bitmaps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        viewModel.onMultipleImagesImported(bitmaps) {
                            // Saved to new active session or document
                        }
                        Toast.makeText(context, "${bitmaps.size} image(s) imported from Gallery.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Launcher for Device / PDF / File import
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    val loaded = FileUtils.loadBitmapsFromUri(context, uri)
                    bitmaps.addAll(loaded)
                }
                if (bitmaps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        viewModel.onMultipleImagesImported(bitmaps) {
                            // Ready
                        }
                        Toast.makeText(context, "${bitmaps.size} page(s) imported from Device.", Toast.LENGTH_SHORT).show()
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
                    .testTag("button_back_watermark_select")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }

            // Top right subtle stacked pages graphic
            WatermarkHeaderIllustration()
        }

        // TITLE & SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Add Watermark",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add watermarks and customize the style.",
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

        // 2 LARGE BUTTONS: GALLERY & DEVICE MATCHING REFERENCE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // GALLERY BUTTON
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { galleryLauncher.launch("image/*") }
                    .testTag("card_import_gallery"),
                shape = RoundedCornerShape(12.dp),
                color = if (themePalette.isDark) RefCardBg else themePalette.card,
                border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else themePalette.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RefDeviceBlue,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Gallery",
                                tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Gallery",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                    )
                }
            }

            // DEVICE BUTTON
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { fileLauncher.launch("image/*") }
                    .testTag("card_import_device"),
                shape = RoundedCornerShape(12.dp),
                color = if (themePalette.isDark) RefCardBg else themePalette.card,
                border = BorderStroke(1.dp, if (themePalette.isDark) RefCardBorder else themePalette.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                    )
                }
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
                        text = "Scan or import files using Gallery or Device above.",
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
                    .testTag("list_watermark_documents"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    WatermarkDocItemRow(
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
private fun WatermarkHeaderIllustration() {
    Box(
        modifier = Modifier.size(width = 64.dp, height = 54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back card
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF334155),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier
                .size(width = 34.dp, height = 44.dp)
                .offset(x = 10.dp, y = (-2).dp)
                .rotate(8f)
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "WM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RefAccentGreen.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Document Row in the Selection List matching reference UI
 */
@Composable
private fun WatermarkDocItemRow(
    doc: DocumentEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(doc.createdAt) {
        try {
            dateFormat.format(Date(doc.createdAt)).lowercase()
        } catch (e: Exception) {
            "27/08/2026 12:00 pm"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("watermark_doc_${doc.id}"),
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

            // Document Details: Title, Date, Page Count
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
 * STATE 2 & 3: Watermark Preview, Customization & Editor Screen (Reference Middle & Right Screenshots)
 */
@Composable
fun WatermarkEditorScreen(
    document: DocumentEntity,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()

    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Watermark Configuration State (Defaulting to "Confidential", Tile Mode, -43° rotation, 35% opacity)
    var watermarkEnabled by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("Confidential") }
    var isTileMode by remember { mutableStateOf(true) } // true = Tile, false = Single
    var selectedColor by remember { mutableStateOf(WatermarkPalette[2]) } // Default teal/green
    var opacity by remember { mutableFloatStateOf(0.35f) }
    var sizeScale by remember { mutableFloatStateOf(1.0f) }
    var rotationDegrees by remember { mutableFloatStateOf(-43f) }

    // Dialog & Panel states
    var showEditTextDialog by remember { mutableStateOf(false) }
    var isAdjustPanelOpen by remember { mutableStateOf(false) }

    // Load actual document pages
    LaunchedEffect(document.id) {
        isLoading = true
        val loaded = viewModel.getPagesForDocumentDirect(document.id)
        pages = loaded
        if (loaded.isNotEmpty()) {
            val first = loaded[0]
            if (!first.watermarkText.isNullOrBlank()) {
                watermarkText = first.watermarkText!!
                opacity = first.watermarkOpacity
            }
        }
        isLoading = false
    }

    BackHandler {
        onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) RefDarkBg else themePalette.background)
            .statusBarsPadding()
    ) {
        // TOP BAR WITH BACK BUTTON
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("button_back_watermark_editor")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = document.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // MAIN DOCUMENT PAGE PREVIEW AREA (State 2 & 3 center)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = RefAccentGreen)
            } else if (pages.isEmpty()) {
                Text("No pages in document", color = RefTextMuted)
            } else {
                val currentPage = pages.getOrNull(currentPageIndex)
                if (currentPage != null) {
                    val pageImageFile = remember(currentPage.id, currentPage.processedImagePath, currentPage.originalImagePath) {
                        val p = File(currentPage.processedImagePath)
                        if (p.exists()) p else File(currentPage.originalImagePath)
                    }

                    // A4 Sheet Container
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxHeight(0.96f)
                            .aspectRatio(0.707f) // A4 ratio
                            .clip(RoundedCornerShape(6.dp))
                            .testTag("preview_document_sheet")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Base Document Page Image
                            if (pageImageFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(pageImageFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Page ${currentPageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // LIVE WATERMARK OVERLAY CANVAS
                            if (watermarkEnabled && watermarkText.isNotBlank()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val canvasWidth = size.width
                                    val canvasHeight = size.height

                                    val alphaInt = (opacity.coerceIn(0.01f, 1f) * 255).toInt()
                                    val nativeColor = (selectedColor.toArgb() and 0x00FFFFFF) or (alphaInt shl 24)

                                    val baseFontSizePx = (canvasWidth / 18f).coerceAtLeast(18f)
                                    val calculatedFontSizePx = (baseFontSizePx * sizeScale.coerceIn(0.3f, 3.0f)).coerceIn(12f, 160f)

                                    val nativePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                        color = nativeColor
                                        textSize = calculatedFontSizePx
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isFakeBoldText = true
                                        letterSpacing = 0.05f
                                    }

                                    val bounds = android.graphics.Rect()
                                    nativePaint.getTextBounds(watermarkText, 0, watermarkText.length, bounds)

                                    drawIntoCanvas { composeCanvas ->
                                        val nativeCanvas = composeCanvas.nativeCanvas
                                        if (isTileMode) {
                                            // Tile Mode: Diagonal repeating pattern covering full canvas
                                            val stepX = (bounds.width().coerceAtLeast(60) * 1.5f + 100f).coerceAtLeast(140f)
                                            val stepY = (bounds.height().coerceAtLeast(30) * 2.0f + 120f).coerceAtLeast(120f)

                                            nativeCanvas.save()
                                            for (y in (-canvasHeight.toInt())..(canvasHeight.toInt() * 2) step stepY.toInt()) {
                                                for (x in (-canvasWidth.toInt())..(canvasWidth.toInt() * 2) step stepX.toInt()) {
                                                    nativeCanvas.save()
                                                    nativeCanvas.rotate(rotationDegrees, x.toFloat(), y.toFloat())
                                                    nativeCanvas.drawText(watermarkText, x.toFloat(), y.toFloat(), nativePaint)
                                                    nativeCanvas.restore()
                                                }
                                            }
                                            nativeCanvas.restore()
                                        } else {
                                            // Single Mode: Single centered watermark
                                            val centerX = canvasWidth / 2f
                                            val centerY = canvasHeight / 2f
                                            nativeCanvas.save()
                                            nativeCanvas.rotate(rotationDegrees, centerX, centerY)
                                            nativeCanvas.drawText(watermarkText, centerX, centerY + bounds.height() / 2f, nativePaint)
                                            nativeCanvas.restore()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PAGE NAVIGATION BAR: "◀  1/5  ▶" (Matching Reference Bottom Middle)
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentPageIndex > 0) currentPageIndex--
                    },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(36.dp).testTag("button_prev_page")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Previous Page",
                        tint = if (currentPageIndex > 0) (if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary) else RefTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (themePalette.isDark) Color(0xFF22262E) else Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "${currentPageIndex + 1}/${pages.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (currentPageIndex < pages.size - 1) currentPageIndex++
                    },
                    enabled = currentPageIndex < pages.size - 1,
                    modifier = Modifier.size(36.dp).testTag("button_next_page")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Next Page",
                        tint = if (currentPageIndex < pages.size - 1) (if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary) else RefTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // EDITING TOOLBAR: "Edit Text", "Adjust", "Remove Watermark" (Matching Reference State 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit Text Action
            WatermarkToolbarActionItem(
                icon = Icons.Default.TextFields,
                label = "Edit Text",
                isSelected = false,
                isDark = themePalette.isDark,
                onClick = {
                    showEditTextDialog = true
                }
            )

            // Adjust Action (Toggles State 3 panel)
            WatermarkToolbarActionItem(
                icon = Icons.Default.Tune,
                label = "Adjust",
                isSelected = isAdjustPanelOpen,
                isDark = themePalette.isDark,
                onClick = {
                    isAdjustPanelOpen = !isAdjustPanelOpen
                }
            )

            // Remove Watermark Action
            WatermarkToolbarActionItem(
                icon = Icons.Default.DeleteOutline,
                label = "Remove Watermark",
                isSelected = !watermarkEnabled,
                isDark = themePalette.isDark,
                onClick = {
                    watermarkEnabled = !watermarkEnabled
                    if (!watermarkEnabled) {
                        Toast.makeText(context, "Watermark removed from preview", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Watermark restored", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // STATE 3: ADJUST PANEL (Collapsible Bottom Adjustment Controls matching Reference Right Screenshot)
        AnimatedVisibility(
            visible = isAdjustPanelOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            WatermarkAdjustPanel(
                isTileMode = isTileMode,
                onModeChange = { isTileMode = it },
                selectedColor = selectedColor,
                onColorChange = { selectedColor = it },
                opacity = opacity,
                onOpacityChange = { opacity = it },
                sizeScale = sizeScale,
                onSizeScaleChange = { sizeScale = it },
                rotationDegrees = rotationDegrees,
                onRotationChange = { rotationDegrees = it },
                isDark = themePalette.isDark
            )
        }

        // FIXED BOTTOM CONFIRMATION BAR: Left 'X', Center 'Add Watermark', Right '✓' (Matching Reference Bottom)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_bar_add_watermark"),
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
                // LEFT: 'X' Cancel Button (Discard & Exit)
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_cancel_watermark")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // CENTER: "Add Watermark" Title
                Text(
                    text = "Add Watermark",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                    modifier = Modifier.testTag("text_add_watermark_center")
                )

                // RIGHT: '✓' Confirm Checkmark Button (Apply & Save to Document)
                IconButton(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    viewModel.applyWatermarkToDocument(
                                        documentId = document.id,
                                        text = watermarkText,
                                        opacity = opacity,
                                        colorLong = selectedColor.toArgb().toLong(),
                                        isTileMode = isTileMode,
                                        sizeScale = sizeScale,
                                        rotationDegrees = rotationDegrees,
                                        removeWatermark = !watermarkEnabled
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Watermark applied and saved successfully!", Toast.LENGTH_SHORT).show()
                                        onSaveSuccess()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("button_confirm_watermark")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = RefAccentGreen,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Apply Watermark",
                            tint = RefAccentGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }

    // EDIT TEXT DIALOG
    if (showEditTextDialog) {
        var tempText by remember { mutableStateOf(watermarkText) }
        AlertDialog(
            onDismissRequest = { showEditTextDialog = false },
            title = {
                Text(
                    text = "Edit Watermark Text",
                    fontWeight = FontWeight.Bold,
                    color = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter watermark text to embed across the document:",
                        fontSize = 13.5.sp,
                        color = RefTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_watermark_text"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RefAccentGreen,
                            unfocusedBorderColor = if (themePalette.isDark) RefCardBorder else Color(0xFFCBD5E1),
                            focusedTextColor = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary,
                            unfocusedTextColor = if (themePalette.isDark) RefTextPrimary else themePalette.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempText.isNotBlank()) {
                            watermarkText = tempText.trim()
                            watermarkEnabled = true
                        }
                        showEditTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RefAccentGreen),
                    modifier = Modifier.testTag("button_save_watermark_text")
                ) {
                    Text("OK", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTextDialog = false }) {
                    Text("Cancel", color = RefTextMuted)
                }
            },
            containerColor = if (themePalette.isDark) Color(0xFF1E222A) else Color.White,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

/**
 * Bottom Toolbar Action Item
 */
@Composable
private fun WatermarkToolbarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("toolbar_${label.lowercase().replace(" ", "_")}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) RefAccentGreen else (if (isDark) RefTextPrimary else Color(0xFF334155)),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) RefAccentGreen else RefTextMuted
        )
    }
}

/**
 * STATE 3: Adjust Watermark Panel matching Right Reference Screenshot
 */
@Composable
fun WatermarkAdjustPanel(
    isTileMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    sizeScale: Float,
    onSizeScaleChange: (Float) -> Unit,
    rotationDegrees: Float,
    onRotationChange: (Float) -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_watermark_adjust"),
        color = if (isDark) Color(0xFF1B1E24) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isDark) RefCardBorder else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. MODE SELECTOR: "Single" vs "Tile" Segmented Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SINGLE BUTTON
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onModeChange(false) }
                        .testTag("mode_single"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isTileMode) (if (isDark) Color(0xFF0F2922) else Color(0xFFD1FAE5)) else (if (isDark) Color(0xFF242831) else Color.White),
                    border = BorderStroke(
                        width = if (!isTileMode) 1.5.dp else 1.dp,
                        color = if (!isTileMode) RefAccentGreen else (if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1))
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Single",
                            fontSize = 15.sp,
                            fontWeight = if (!isTileMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isTileMode) RefAccentGreen else (if (isDark) RefTextPrimary else Color(0xFF334155))
                        )
                    }
                }

                // TILE BUTTON
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onModeChange(true) }
                        .testTag("mode_tile"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTileMode) (if (isDark) Color(0xFF0F2922) else Color(0xFFD1FAE5)) else (if (isDark) Color(0xFF242831) else Color.White),
                    border = BorderStroke(
                        width = if (isTileMode) 1.5.dp else 1.dp,
                        color = if (isTileMode) RefAccentGreen else (if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1))
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tile",
                            fontSize = 15.sp,
                            fontWeight = if (isTileMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTileMode) RefAccentGreen else (if (isDark) RefTextPrimary else Color(0xFF334155))
                        )
                    }
                }
            }

            // 2. COLOR SELECTION ROW (10 Circular Color Chips)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("row_watermark_colors"),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(WatermarkPalette) { colorItem ->
                    val isSelected = colorItem == selectedColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colorItem)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) RefAccentGreen else (if (colorItem == Color.White) Color(0xFFCBD5E1) else Color.Transparent),
                                shape = CircleShape
                            )
                            .clickable { onColorChange(colorItem) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (colorItem == Color.White || colorItem == Color(0xFFFEF08A)) Color.Black else Color.White)
                            )
                        }
                    }
                }
            }

            // 3. OPACITY SLIDER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.05f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = RefAccentGreen,
                        activeTrackColor = RefAccentGreen,
                        inactiveTrackColor = if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_opacity")
                )
            }

            // 4. SIZE SLIDER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Size",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) RefTextPrimary else Color(0xFF334155),
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = sizeScale,
                    onValueChange = onSizeScaleChange,
                    valueRange = 0.4f..2.2f,
                    colors = SliderDefaults.colors(
                        thumbColor = RefAccentGreen,
                        activeTrackColor = RefAccentGreen,
                        inactiveTrackColor = if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("slider_size")
                )
            }

            // 5. ROTATE SLIDER (Showing live angle on the right, e.g. -43°)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rotate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) RefTextPrimary else Color(0xFF334155),
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = rotationDegrees,
                    onValueChange = onRotationChange,
                    valueRange = -180f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = RefAccentGreen,
                        activeTrackColor = RefAccentGreen,
                        inactiveTrackColor = if (isDark) Color(0xFF333845) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("slider_rotate")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${rotationDegrees.roundToInt()}°",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) RefTextPrimary else Color(0xFF334155),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp).testTag("text_rotation_degrees")
                )
            }
        }
    }
}
