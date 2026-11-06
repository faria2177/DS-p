package com.docscan.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import com.docscan.ui.components.SettingsTabContent
import com.docscan.ui.components.getDeviceStorageInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.MainActivity
import com.docscan.R
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.data.model.ScanMode
import com.docscan.data.model.ScannerFeatureMode
import com.docscan.security.DocumentLockManager
import com.docscan.ui.components.AiToExcelDialog
import com.docscan.ui.components.AiToWordDialog
import com.docscan.ui.components.CompressDialog
import com.docscan.ui.components.CustomDeleteConfirmationDialog
import com.docscan.ui.components.ExitConfirmationDialog
import com.docscan.ui.components.ImageResizerDialog
import com.docscan.ui.components.LockDocumentDialog
import com.docscan.ui.components.MergeFilesDialog
import com.docscan.ui.components.PassportPhotoDialog
import com.docscan.ui.components.PdfExportDialog
import com.docscan.ui.components.PdfResizeDialog
import com.docscan.ui.components.RotatePdfDialog
import com.docscan.ui.components.SettingsBottomSheet
import com.docscan.ui.components.TagsManagerDialog
import com.docscan.ui.components.SendToPcDialog
import com.docscan.ui.components.SignatureDialog
import com.docscan.ui.components.SplitPdfDialog
import com.docscan.ui.components.TextToPdfDialog
import com.docscan.ui.components.TranslateDialog
import com.docscan.ui.components.UnlockDocumentDialog
import com.docscan.ui.components.WatermarkDialog
import com.docscan.ui.screens.PdfToWordScreen
import com.docscan.ui.screens.PdfToExcelScreen
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.AiOrchestrator
import com.docscan.util.FileUtils
import com.docscan.util.PageSize
import com.docscan.util.PdfExportConfig
import com.docscan.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Dynamic Design System Colors supporting Light, Dark, and System Default themes
private val ThemeDarkBg: Color @Composable get() = rememberAppThemePalette().background
private val ThemeSurfaceDark: Color @Composable get() = rememberAppThemePalette().card
private val ThemeSearchBg: Color @Composable get() = rememberAppThemePalette().searchBg
private val ThemeAccentTeal: Color @Composable get() = rememberAppThemePalette().primaryAccent
private val ThemeBorderDark: Color @Composable get() = rememberAppThemePalette().cardBorder
private val ThemeTextMuted: Color @Composable get() = rememberAppThemePalette().textMuted
private val ThemeTextPrimary: Color @Composable get() = rememberAppThemePalette().textPrimary
private val ThemeTextSecondary: Color @Composable get() = rememberAppThemePalette().textSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: ScannerViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToNidSetup: () -> Unit,
    onNavigateToCrop: () -> Unit,
    onNavigateToDocumentPreview: () -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit,
    onNavigateToMergeFiles: () -> Unit = {},
    onNavigateToSignPdf: () -> Unit = {},
    onNavigateToCompressPdf: () -> Unit = {},
    onNavigateToReorderPages: () -> Unit = {},
    onNavigateToReorderDoc: (Long) -> Unit = {},
    onNavigateToRotatePdf: () -> Unit = {},
    onNavigateToRotatePdfDoc: (Long) -> Unit = {},
    onNavigateToAddWatermark: () -> Unit = {},
    onNavigateToAddWatermarkDoc: (Long) -> Unit = {},
    onNavigateToExtractPdfPages: () -> Unit = {},
    onNavigateToExtractPdfPagesDoc: (Long) -> Unit = {},
    onNavigateToExtractText: () -> Unit = {},
    onNavigateToExtractTextDoc: (Long) -> Unit = {},
    onNavigateToPassportPhoto: () -> Unit = {},
    onNavigateToToWord: () -> Unit = {},
    onNavigateToToWordDoc: (Long) -> Unit = {},
    onNavigateToToExcel: () -> Unit = {},
    onNavigateToToExcelDoc: (Long) -> Unit = {},
    onNavigateToPdfToImages: () -> Unit = {},
    onNavigateToPdfToImagesDoc: (Long) -> Unit = {},
    onNavigateToPdfToLongImage: () -> Unit = {},
    onNavigateToPdfToLongImageDoc: (Long) -> Unit = {},
    onNavigateToWordReader: (String?) -> Unit = {},
    onNavigateToExcelReader: (String?) -> Unit = {},
    onNavigateToImageResizer: () -> Unit = {},
    onNavigateToPhotoBackgroundRemover: () -> Unit = {},
    onNavigateToEditPdf: () -> Unit = {},
    onNavigateToLock: (Long?) -> Unit = {},
    onNavigateToAiChat: (Long?) -> Unit = {},
    onNavigateToOpenCvCrop: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val selectedDocIds by viewModel.selectedDocIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    var selectedNavTab by remember { mutableStateOf("Home") } // "Home", "Files", "Tools", "Me"

    // Dialog & Flow States
    var docForUnlock by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToMove by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToExportPdf by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentForTags by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentForSendToPc by remember { mutableStateOf<DocumentEntity?>(null) }

    // "Hide History" only hides a document from the Home/Recent feed.
    // The file remains available in Files and can be restored from the same More menu.
    val historyVisibilityPrefs = remember {
        context.getSharedPreferences("document_history_visibility", Context.MODE_PRIVATE)
    }
    var hiddenHistoryDocIds by remember {
        mutableStateOf(
            historyVisibilityPrefs.getStringSet("hidden_document_ids", emptySet())
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        )
    }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showBatchMergeDialog by remember { mutableStateOf(false) }
    var showBatchMoveFolderDialog by remember { mutableStateOf(false) }
    var showBatchMoreSheet by remember { mutableStateOf(false) }
    var batchMoveCopyMode by remember { mutableStateOf("Move") }
    var batchTargetFolder by remember { mutableStateOf("Business") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var mergedDocTitle by remember { mutableStateOf("") }

    // Tools Feature Dialogs States
    var showImageResizer by remember { mutableStateOf(false) }
    var showImageToPdfOptions by remember { mutableStateOf(false) }
    var imageToPdfAutoEnhance by remember { mutableStateOf(true) }
    var showTextToPdf by remember { mutableStateOf(false) }
    var showPassportPhoto by remember { mutableStateOf(false) }
    var showPdfToWordScreen by remember { mutableStateOf(false) }
    var showPdfToExcelScreen by remember { mutableStateOf(false) }
    var selectedDocForWord by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedDocForExcel by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForPdfResize by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForSplitPdf by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForRotatePdf by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForWatermark by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForSignature by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForProtect by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForCompress by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForMerge by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForDocx by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForExcel by remember { mutableStateOf<DocumentEntity?>(null) }
    var docForOcrText by remember { mutableStateOf<DocumentEntity?>(null) }
    var showDocSelectForTool by remember { mutableStateOf<String?>(null) }

    val folders by viewModel.availableFolders.collectAsStateWithLifecycle()
    val moveCopyFolders = remember(folders) { folders.filterNot { it == "All" || it == "Starred" } }

    // Folder navigation must not affect Home or Tools. Only Files applies the folder filter.
    val visibleDocsForSelection = remember(documents, selectedNavTab, selectedFolder, searchQuery, hiddenHistoryDocIds) {
        val scopeDocs = if (selectedNavTab == "Files") {
            when (selectedFolder) {
                "All" -> documents
                "Starred" -> documents.filter { it.isStarred }
                else -> documents.filter { it.folder == selectedFolder }
            }
        } else {
            documents.filterNot { hiddenHistoryDocIds.contains(it.id) }
        }
        if (searchQuery.isBlank()) scopeDocs else scopeDocs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.tags.contains(searchQuery, ignoreCase = true) ||
                it.extractedText.orEmpty().contains(searchQuery, ignoreCase = true)
        }
    }

    // Animated Camera Speed Dial state
    var isSpeedDialOpen by remember { mutableStateOf(false) }
    // Close speed dial if tab changes or selection mode starts
    LaunchedEffect(selectedNavTab, isSelectionMode) {
        if (isSpeedDialOpen) {
            isSpeedDialOpen = false
        }
    }

    // All scan entries use our on-device CameraX/OpenCV scanner, including Tools modes.
    fun launchNativeScanner(
        mode: ScanMode = ScanMode.SINGLE,
        featureMode: ScannerFeatureMode = ScannerFeatureMode.SCAN
    ) {
        if (mode == ScanMode.ID_CARD || featureMode == ScannerFeatureMode.ID_CARDS) {
            viewModel.enterNidCardMode()
            onNavigateToNidSetup()
        } else {
            viewModel.prepareNativeDocumentScan(mode, featureMode)
            onNavigateToCamera()
        }
    }

    // Exit Confirmation & Back Navigation Handling
    BackHandler {
        when {
            isSpeedDialOpen -> {
                isSpeedDialOpen = false
            }
            isSelectionMode -> {
                viewModel.clearSelection()
            }
            searchQuery.isNotBlank() -> {
                viewModel.setSearch("")
            }
            selectedNavTab == "Files" && selectedFolder != "All" -> {
                viewModel.setFolder("All")
            }
            selectedNavTab != "Home" -> {
                selectedNavTab = "Home"
            }
            else -> {
                showExitConfirmDialog = true
            }
        }
    }

    // Gallery Picker launcher
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
                            onNavigateToCrop()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load selected images.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Dedicated Image -> PDF picker. Selected photos are routed through the
    // high-accuracy four-corner detector before the normal crop/review screen.
    val imageToPdfGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                uris.forEach { uri -> bitmaps.addAll(FileUtils.loadBitmapsFromUri(context, uri)) }
                withContext(Dispatchers.Main) {
                    if (bitmaps.isNotEmpty()) {
                        viewModel.importImagesForPdf(
                            bitmaps = bitmaps,
                            autoEnhance = imageToPdfAutoEnhance,
                            onNavigateToCrop = onNavigateToCrop
                        )
                    } else {
                        Toast.makeText(context, "Could not load selected image(s).", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // PDF / Document File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmaps = FileUtils.loadBitmapsFromUri(context, it)
                if (bitmaps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        viewModel.onMultipleImagesImported(bitmaps) {
                            onNavigateToCrop()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Document loaded successfully.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = ThemeDarkBg,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedDocIds.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ThemeTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection", tint = ThemeTextPrimary)
                        }
                    },
                    actions = {
                        val allSelected = visibleDocsForSelection.isNotEmpty() &&
                            visibleDocsForSelection.all { selectedDocIds.contains(it.id) }
                        TextButton(
                            onClick = { viewModel.selectAllDocuments(visibleDocsForSelection) }
                        ) {
                            Text(
                                if (allSelected) "Deselect All" else "Select All",
                                color = ThemeAccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ThemeSurfaceDark
                    )
                )
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedDocIds.isNotEmpty()) {
                Surface(
                    color = ThemeSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionBottomBarItem(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                val selectedDocs = documents.filter { selectedDocIds.contains(it.id) }
                                if (selectedDocs.isNotEmpty()) {
                                    viewModel.sharePdfDirect(selectedDocs.first())
                                }
                            }
                        )

                        SelectionBottomBarItem(
                            icon = Icons.Default.DriveFileMove,
                            label = "Move/Copy",
                            onClick = {
                                batchMoveCopyMode = "Move"
                                batchTargetFolder = documents.firstOrNull { selectedDocIds.contains(it.id) }?.folder
                                    ?.takeIf { it.isNotBlank() && it != "All" && it != "Starred" }
                                    ?: moveCopyFolders.firstOrNull()
                                    ?: "Business"
                                showBatchMoveFolderDialog = true
                            }
                        )

                        // DYNAMIC ACTION: Rename only when exactly 1 selected, Merge only when 2+ selected
                        if (selectedDocIds.size == 1) {
                            SelectionBottomBarItem(
                                icon = Icons.Default.DriveFileRenameOutline,
                                label = "Rename",
                                onClick = {
                                    val singleDoc = documents.find { selectedDocIds.contains(it.id) }
                                    if (singleDoc != null) {
                                        documentToRename = singleDoc
                                    }
                                }
                            )
                        } else if (selectedDocIds.size >= 2) {
                            SelectionBottomBarItem(
                                icon = Icons.Default.CallMerge,
                                label = "Merge",
                                onClick = {
                                    val timeStamp = SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
                                    mergedDocTitle = "Merged Doc $timeStamp"
                                    showBatchMergeDialog = true
                                }
                            )
                        }

                        SelectionBottomBarItem(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { showBatchDeleteConfirm = true }
                        )

                        SelectionBottomBarItem(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            onClick = { showBatchMoreSheet = true }
                        )
                    }
                }
            } else {
                // Bottom Navigation Bar matching the 4-tab CamScanner design (Home, Files, Tools, Me)
                NavigationBar(
                    containerColor = ThemeDarkBg,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedNavTab == "Home",
                        onClick = { selectedNavTab = "Home" },
                        icon = {
                            Icon(
                                if (selectedNavTab == "Home") Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text(
                                "Home",
                                fontWeight = if (selectedNavTab == "Home") FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ThemeAccentTeal,
                            selectedTextColor = ThemeAccentTeal,
                            unselectedIconColor = ThemeTextMuted,
                            unselectedTextColor = ThemeTextMuted,
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationBarItem(
                        selected = selectedNavTab == "Files",
                        onClick = { selectedNavTab = "Files" },
                        icon = {
                            Icon(
                                if (selectedNavTab == "Files") Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = "Files"
                            )
                        },
                        label = {
                            Text(
                                "Files",
                                fontWeight = if (selectedNavTab == "Files") FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ThemeAccentTeal,
                            selectedTextColor = ThemeAccentTeal,
                            unselectedIconColor = ThemeTextMuted,
                            unselectedTextColor = ThemeTextMuted,
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationBarItem(
                        selected = selectedNavTab == "Tools",
                        onClick = { selectedNavTab = "Tools" },
                        icon = {
                            Icon(
                                if (selectedNavTab == "Tools") Icons.Filled.GridView else Icons.Outlined.GridView,
                                contentDescription = "Tools"
                            )
                        },
                        label = {
                            Text(
                                "Tools",
                                fontWeight = if (selectedNavTab == "Tools") FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ThemeAccentTeal,
                            selectedTextColor = ThemeAccentTeal,
                            unselectedIconColor = ThemeTextMuted,
                            unselectedTextColor = ThemeTextMuted,
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationBarItem(
                        selected = selectedNavTab == "Settings",
                        onClick = { selectedNavTab = "Settings" },
                        icon = {
                            Icon(
                                if (selectedNavTab == "Settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = {
                            Text(
                                "Settings",
                                fontWeight = if (selectedNavTab == "Settings") FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ThemeAccentTeal,
                            selectedTextColor = ThemeAccentTeal,
                            unselectedIconColor = ThemeTextMuted,
                            unselectedTextColor = ThemeTextMuted,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && (selectedNavTab == "Home" || selectedNavTab == "Files")) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedVisibility(
                        visible = isSpeedDialOpen,
                        enter = fadeIn(animationSpec = tween(180)) +
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) +
                                scaleIn(
                                    initialScale = 0.6f,
                                    transformOrigin = TransformOrigin(1f, 1f)
                                ),
                        exit = fadeOut(animationSpec = tween(140)) +
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(140)
                                ) +
                                scaleOut(
                                    targetScale = 0.6f,
                                    transformOrigin = TransformOrigin(1f, 1f)
                                )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. ID Card Option (Top)
                            SpeedDialActionItem(
                                label = "ID Card",
                                subtitle = "Both Sides (Max 2 Pages)",
                                icon = Icons.Default.Badge,
                                buttonBg = Color(0xFF0284C7),
                                onClick = {
                                    isSpeedDialOpen = false
                                    launchNativeScanner(mode = ScanMode.ID_CARD)
                                }
                            )

                            // 2. Scan Option (Middle, right above Camera button)
                            SpeedDialActionItem(
                                label = "Scan",
                                subtitle = "Documents / Multi-Page",
                                icon = Icons.Default.DocumentScanner,
                                buttonBg = ThemeAccentTeal,
                                onClick = {
                                    isSpeedDialOpen = false
                                    launchNativeScanner()
                                }
                            )
                        }
                    }

                    // Main Camera FAB
                    FloatingActionButton(
                        onClick = {
                            // The primary Scan button opens the document camera
                            // immediately. ID Card remains available from Tools.
                            isSpeedDialOpen = false
                            launchNativeScanner()
                        },
                        containerColor = ThemeAccentTeal,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Crossfade(
                            targetState = isSpeedDialOpen,
                            animationSpec = tween(220),
                            label = "fab_crossfade"
                        ) { open ->
                            if (open) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Scan Options",
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Camera Scanner",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ThemeDarkBg)
        ) {
            when (selectedNavTab) {
                "Home" -> {
                    HomeContent(
                        documents = visibleDocsForSelection,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearch(it) },
                        selectedDocIds = selectedDocIds,
                        isSelectionMode = isSelectionMode,
                        onDocumentClick = { doc ->
                            if (isSelectionMode) {
                                viewModel.toggleDocSelection(doc.id)
                            } else {
                                if (DocumentLockManager.isLockedAndGuarded(context, doc.id)) {
                                    docForUnlock = doc
                                } else {
                                    onNavigateToDocumentDetail(doc.id)
                                }
                            }
                        },
                        onDocumentLongClick = { doc ->
                            viewModel.toggleDocSelection(doc.id)
                        },
                        onToggleSelection = { docId ->
                            viewModel.toggleDocSelection(docId)
                        },
                        onNavigateToCamera = { launchNativeScanner() }
                    )
                }

                "Files" -> {
                    FilesContent(
                        documents = documents,
                        folders = folders,
                        selectedFolder = selectedFolder,
                        onSelectFolder = { viewModel.setFolder(it) },
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearch(it) },
                        isGridView = isGridView,
                        onToggleGridView = { viewModel.toggleGridView() },
                        selectedDocIds = selectedDocIds,
                        isSelectionMode = isSelectionMode,
                        onToggleSelectionMode = {
                            if (isSelectionMode) viewModel.clearSelection()
                            else viewModel.isSelectionMode.value = true
                        },
                        onDocumentClick = { doc ->
                            if (isSelectionMode) {
                                viewModel.toggleDocSelection(doc.id)
                            } else {
                                if (DocumentLockManager.isLockedAndGuarded(context, doc.id)) {
                                    docForUnlock = doc
                                } else {
                                    onNavigateToDocumentDetail(doc.id)
                                }
                            }
                        },
                        onDocumentLongClick = { doc ->
                            viewModel.toggleDocSelection(doc.id)
                        },
                        onToggleSelection = { docId ->
                            viewModel.toggleDocSelection(docId)
                        },
                        onNewFolderClick = { showNewFolderDialog = true },
                        onNavigateToCamera = { launchNativeScanner() }
                    )
                }

                "Tools" -> {
                    ToolsContent(
                        onSelectScanMode = { mode, featureMode ->
                            launchNativeScanner(
                                mode = mode,
                                featureMode = featureMode ?: ScannerFeatureMode.SCAN
                            )
                        },
                        onExtractText = {
                            onNavigateToExtractText()
                        },
                        onPassportPhotoMaker = onNavigateToPassportPhoto,
                        onPhotoTranslation = {
                            onNavigateToExtractText()
                        },
                        onImageToPdf = { showImageToPdfOptions = true },
                        onTextToPdf = { showTextToPdf = true },
                        onToWord = {
                            onNavigateToToWord()
                        },
                        onToExcel = {
                            onNavigateToToExcel()
                        },
                        onPdfToImages = onNavigateToPdfToImages,
                        onPdfToLongImage = onNavigateToPdfToLongImage,
                        onImageResizer = { onNavigateToImageResizer() },
                        onPhotoBackgroundRemover = { onNavigateToPhotoBackgroundRemover() },
                        onEditPdf = { onNavigateToEditPdf() },
                        onResizePdf = {
                            if (documents.isNotEmpty()) {
                                showDocSelectForTool = "Resize PDF"
                            } else {
                                Toast.makeText(context, "Scan or import a document first", Toast.LENGTH_SHORT).show()
                                launchNativeScanner()
                            }
                        },
                        onSplitPdf = {
                            onNavigateToExtractPdfPages()
                        },
                        onRotatePdf = {
                            onNavigateToRotatePdf()
                        },
                        onSign = {
                            onNavigateToSignPdf()
                        },
                        onAddWatermark = {
                            onNavigateToAddWatermark()
                        },
                        onSmartErase = {
                            val firstDoc = documents.firstOrNull()
                            if (firstDoc != null) {
                                onNavigateToDocumentDetail(firstDoc.id)
                            } else {
                                Toast.makeText(context, "Scan or import a document first to use Smart Erase", Toast.LENGTH_SHORT).show()
                                launchNativeScanner()
                            }
                        },
                        onEraseMarks = {
                            val firstDoc = documents.firstOrNull()
                            if (firstDoc != null) {
                                onNavigateToDocumentDetail(firstDoc.id)
                            } else {
                                Toast.makeText(context, "Scan or import a document first", Toast.LENGTH_SHORT).show()
                                launchNativeScanner()
                            }
                        },
                        onMergeFiles = {
                            onNavigateToMergeFiles()
                        },
                        onExtractPdfPages = {
                            onNavigateToExtractPdfPages()
                        },
                        onReorderPages = {
                            onNavigateToReorderPages()
                        },
                        onLock = {
                            onNavigateToLock(null)
                        },
                        onCompress = {
                            onNavigateToCompressPdf()
                        },
                        onAiChat = { onNavigateToAiChat(null) },
                        onPrint = {
                            val firstDoc = documents.firstOrNull()
                            if (firstDoc != null) {
                                coroutineScope.launch {
                                    val pages = viewModel.getPagesForDocumentDirect(firstDoc.id)
                                    val pdfFile = PdfExporter.generatePdf(context, firstDoc.title, pages)
                                    if (pdfFile != null) {
                                        PdfExporter.sharePdf(context, pdfFile)
                                    }
                                }
                            } else {
                                Toast.makeText(context, "No document available to print", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onScanCode = {
                            viewModel.scanMode.value = ScanMode.SINGLE
                            viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                            viewModel.mainScanMode.value = com.docscan.data.model.MainScanMode.QR_BARCODE
                            viewModel.isQrOnlyMode.value = true
                            onNavigateToCamera()
                        },
                        onOpenCvCrop = {
                            onNavigateToOpenCvCrop()
                        }
                    )
                }

                "Settings" -> {
                    SettingsTabContent(
                        totalDocumentsCount = documents.size,
                        onNavigateToScan = { launchNativeScanner() },
                        onNavigateToFiles = { selectedNavTab = "Files" }
                    )
                }
            }

            // Speed Dial Dim Scrim
            if (isSpeedDialOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isSpeedDialOpen = false
                        }
                )
            }
        }
    }

    // ==================== ALL ACTIVE DIALOGS ====================

    // Generic Document Selector Picker Dialog
    if (showDocSelectForTool != null) {
        val toolName = showDocSelectForTool!!
        AlertDialog(
            onDismissRequest = { showDocSelectForTool = null },
            title = {
                Text(
                    text = "Select Document for $toolName",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            },
            containerColor = Color(0xFF242426),
            text = {
                if (documents.isEmpty()) {
                    Text(
                        "No scanned documents found. Please scan or import a document first.",
                        color = Color(0xFF9E9E9E)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Choose a document:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)
                        )
                        documents.forEach { doc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF2F2F33),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDocSelectForTool = null
                                        when (toolName) {
                                            "Merge PDF" -> docForMerge = doc
                                            "Resize PDF" -> docForPdfResize = doc
                                            "Organize PDF" -> onNavigateToDocumentDetail(doc.id)
                                            "Protect PDF" -> docForProtect = doc
                                            "Add Watermark" -> onNavigateToAddWatermarkDoc(doc.id)
                                            "PDF to JPG", "PDF to Images", "PDF to Image" -> onNavigateToPdfToImagesDoc(doc.id)
                                            "PDF to Text" -> onNavigateToExtractTextDoc(doc.id)
                                            "PDF to DOCX", "PDF to Word", "To Word" -> {
                                                onNavigateToToWordDoc(doc.id)
                                            }
                                            "PDF to Excel", "To Excel" -> {
                                                onNavigateToToExcelDoc(doc.id)
                                            }
                                            "Split PDF", "Extract PDF Pages" -> onNavigateToExtractPdfPagesDoc(doc.id)
                                            "Rotate PDF" -> docForRotatePdf = doc
                                            "Compress PDF" -> docForCompress = doc
                                            "Sign PDF" -> docForSignature = doc
                                            else -> onNavigateToDocumentDetail(doc.id)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = ThemeAccentTeal,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            doc.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${doc.pageCount} pages • ${doc.folder}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF9E9E9E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (documents.isEmpty()) {
                    Button(
                        onClick = {
                            showDocSelectForTool = null
                            launchNativeScanner()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeAccentTeal)
                    ) {
                        Text("Scan Document", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showDocSelectForTool = null }) {
                        Text("Cancel", color = ThemeAccentTeal)
                    }
                }
            }
        )
    }

    // Move / Copy destination dialog for selected documents.
    if (showBatchMoveFolderDialog && selectedDocIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchMoveFolderDialog = false },
            title = {
                Text(
                    text = "Move or Copy",
                    color = ThemeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = ThemeSurfaceDark,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${selectedDocIds.size} selected document(s)",
                        color = ThemeTextMuted,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("Move", "Copy").forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (batchMoveCopyMode == mode) ThemeAccentTeal.copy(alpha = 0.16f) else ThemeSearchBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (batchMoveCopyMode == mode) ThemeAccentTeal else ThemeBorderDark
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { batchMoveCopyMode = mode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = batchMoveCopyMode == mode,
                                        onClick = { batchMoveCopyMode = mode },
                                        colors = RadioButtonDefaults.colors(selectedColor = ThemeAccentTeal)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (mode == "Move") Icons.Default.DriveFileMove else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (batchMoveCopyMode == mode) ThemeAccentTeal else ThemeTextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(mode, color = ThemeTextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Choose destination folder",
                        color = ThemeTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    moveCopyFolders.forEach { folder ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (batchTargetFolder == folder) ThemeAccentTeal.copy(alpha = 0.16f) else ThemeSearchBg,
                            border = BorderStroke(
                                1.dp,
                                if (batchTargetFolder == folder) ThemeAccentTeal else ThemeBorderDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { batchTargetFolder = folder }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (batchTargetFolder == folder) ThemeAccentTeal else ThemeTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = folder,
                                    color = ThemeTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (batchTargetFolder == folder) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = ThemeAccentTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = batchTargetFolder.isNotBlank(),
                    onClick = {
                        val target = batchTargetFolder.trim()
                        if (batchMoveCopyMode == "Copy") {
                            viewModel.copySelectedDocuments(target)
                        } else {
                            viewModel.moveSelectedDocuments(target)
                        }
                        showBatchMoveFolderDialog = false
                    }
                ) {
                    Text(
                        if (batchMoveCopyMode == "Copy") "Copy Here" else "Move Here",
                        color = ThemeAccentTeal,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchMoveFolderDialog = false }) {
                    Text("Cancel", color = ThemeTextSecondary)
                }
            }
        )
    }

    // Three-dot More menu in selection mode.
    if (showBatchMoreSheet && selectedDocIds.isNotEmpty()) {
        val selectedDocsForMore = documents.filter { selectedDocIds.contains(it.id) }

        if (selectedDocsForMore.size == 1) {
            val selectedDoc = selectedDocsForMore.first()
            val isHiddenFromHistory = hiddenHistoryDocIds.contains(selectedDoc.id)

            ModalBottomSheet(
                onDismissRequest = { showBatchMoreSheet = false },
                containerColor = Color(0xFF202124),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 4.dp)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF5E626A))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "More",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4A4D52))
                                .clickable { showBatchMoreSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFD8DADF),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }

                    SelectionMoreGroup {
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Tv,
                            title = "Send to PC",
                            onClick = {
                                showBatchMoreSheet = false
                                documentForSendToPc = selectedDoc
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3A3D42), thickness = 0.7.dp)
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Print,
                            title = "Print",
                            onClick = {
                                showBatchMoreSheet = false
                                viewModel.printDocumentPdf(context, selectedDoc)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    SelectionMoreGroup {
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.PhotoLibrary,
                            title = "PDF to Images",
                            onClick = {
                                showBatchMoreSheet = false
                                viewModel.clearSelection()
                                onNavigateToPdfToImagesDoc(selectedDoc.id)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3A3D42), thickness = 0.7.dp)
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Home,
                            title = "Add to Home Screen",
                            onClick = {
                                showBatchMoreSheet = false
                                addDocumentShortcutToHome(context, selectedDoc)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3A3D42), thickness = 0.7.dp)
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Badge,
                            title = "Tags",
                            onClick = {
                                showBatchMoreSheet = false
                                documentForTags = selectedDoc
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3A3D42), thickness = 0.7.dp)
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Lock,
                            title = "Lock",
                            onClick = {
                                showBatchMoreSheet = false
                                viewModel.clearSelection()
                                onNavigateToLock(selectedDoc.id)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF3A3D42), thickness = 0.7.dp)
                        SelectionMoreReferenceRow(
                            icon = Icons.Default.Schedule,
                            title = if (isHiddenFromHistory) "Show in History" else "Hide History",
                            onClick = {
                                val updated = hiddenHistoryDocIds.toMutableSet().apply {
                                    if (isHiddenFromHistory) remove(selectedDoc.id) else add(selectedDoc.id)
                                }.toSet()
                                hiddenHistoryDocIds = updated
                                historyVisibilityPrefs.edit()
                                    .putStringSet("hidden_document_ids", updated.map { it.toString() }.toSet())
                                    .apply()
                                showBatchMoreSheet = false
                                viewModel.clearSelection()
                                Toast.makeText(
                                    context,
                                    if (isHiddenFromHistory) "Document restored to Home history" else "Document hidden from Home history",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        } else {
            // Multi-document selection keeps the existing file-management actions.
            val allStarred = selectedDocsForMore.isNotEmpty() && selectedDocsForMore.all { it.isStarred }
            ModalBottomSheet(
                onDismissRequest = { showBatchMoreSheet = false },
                containerColor = Color(0xFF202124)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
                ) {
                    Text(
                        text = "${selectedDocIds.size} selected",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    BatchMoreActionRow(
                        icon = Icons.Default.Star,
                        title = if (allStarred) "Remove from Starred" else "Add to Starred",
                        onClick = {
                            showBatchMoreSheet = false
                            viewModel.setSelectedDocumentsStarred(!allStarred)
                        }
                    )
                    BatchMoreActionRow(
                        icon = Icons.Default.DriveFileMove,
                        title = "Move to folder",
                        onClick = {
                            showBatchMoreSheet = false
                            batchMoveCopyMode = "Move"
                            batchTargetFolder = selectedDocsForMore.firstOrNull()?.folder
                                ?.takeIf { it.isNotBlank() && it != "All" && it != "Starred" }
                                ?: moveCopyFolders.firstOrNull() ?: "Business"
                            showBatchMoveFolderDialog = true
                        }
                    )
                    BatchMoreActionRow(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy to folder",
                        onClick = {
                            showBatchMoreSheet = false
                            batchMoveCopyMode = "Copy"
                            batchTargetFolder = selectedDocsForMore.firstOrNull()?.folder
                                ?.takeIf { it.isNotBlank() && it != "All" && it != "Starred" }
                                ?: moveCopyFolders.firstOrNull() ?: "Business"
                            showBatchMoveFolderDialog = true
                        }
                    )
                    BatchMoreActionRow(
                        icon = Icons.Default.CallMerge,
                        title = "Merge selected documents",
                        onClick = {
                            showBatchMoreSheet = false
                            showBatchMergeDialog = true
                        }
                    )
                    BatchMoreActionRow(
                        icon = Icons.Default.Delete,
                        title = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            showBatchMoreSheet = false
                            showBatchDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    documentForTags?.let { doc ->
        TagsManagerDialog(
            initialTags = doc.tags,
            onDismiss = { documentForTags = null },
            onSaveTags = { tags ->
                viewModel.updateDocumentTags(doc, tags)
                documentForTags = null
                viewModel.clearSelection()
            }
        )
    }

    documentForSendToPc?.let { doc ->
        SendToPcDialog(
            docTitle = doc.title,
            onDismiss = {
                documentForSendToPc = null
                viewModel.clearSelection()
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder", color = ThemeTextPrimary) },
            containerColor = ThemeSurfaceDark,
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    label = { Text("Folder Name", color = ThemeTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary,
                        focusedBorderColor = ThemeAccentTeal,
                        unfocusedBorderColor = ThemeBorderDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val folderName = newFolderNameInput.trim()
                        if (viewModel.createFolder(folderName)) {
                            Toast.makeText(context, "Folder '$folderName' created", Toast.LENGTH_SHORT).show()
                            newFolderNameInput = ""
                            showNewFolderDialog = false
                        } else {
                            Toast.makeText(context, "Invalid or existing folder name", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeAccentTeal)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = ThemeTextSecondary)
                }
            }
        )
    }

    // PDF to Word Screen Dialog
    if (showPdfToWordScreen) {
        Dialog(
            onDismissRequest = { showPdfToWordScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            PdfToWordScreen(
                viewModel = viewModel,
                initialDocument = selectedDocForWord,
                allDocuments = documents,
                onBack = { showPdfToWordScreen = false }
            )
        }
    }

    // PDF to Excel Screen Dialog
    if (showPdfToExcelScreen) {
        Dialog(
            onDismissRequest = { showPdfToExcelScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            PdfToExcelScreen(
                viewModel = viewModel,
                initialDocument = selectedDocForExcel,
                allDocuments = documents,
                onBack = { showPdfToExcelScreen = false }
            )
        }
    }

    // Professional Image -> PDF setup sheet.
    if (showImageToPdfOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImageToPdfOptions = false },
            containerColor = Color(0xFF1F2024),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color(0xFF34D399))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Image to PDF", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Smart 4-corner detection before PDF creation", color = Color(0xFFAEB4BE), fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2A2C31),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic document edges", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Detects and refines all 4 corners; you can adjust them manually before export", color = Color(0xFFAEB4BE), fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart document enhancement", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Apply document-friendly enhancement after crop", color = Color(0xFFAEB4BE), fontSize = 11.sp)
                            }
                            Switch(
                                checked = imageToPdfAutoEnhance,
                                onCheckedChange = { imageToPdfAutoEnhance = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }
                }

                Text(
                    "Tip: choose clear photos with all page corners visible. Low-confidence detections open with a safe full-image crop so nothing is lost.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Button(
                    onClick = {
                        showImageToPdfOptions = false
                        imageToPdfGalleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Images", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // Image Resizer Dialog
    if (showImageResizer) {
        ImageResizerDialog(
            onDismiss = { showImageResizer = false },
            onSaved = { showImageResizer = false }
        )
    }

    // Text to PDF Dialog
    if (showTextToPdf) {
        TextToPdfDialog(
            onDismiss = { showTextToPdf = false },
            onPdfCreated = { showTextToPdf = false }
        )
    }

    // Passport Photo Dialog
    if (showPassportPhoto) {
        PassportPhotoDialog(
            onDismiss = { showPassportPhoto = false },
            onSaved = { showPassportPhoto = false }
        )
    }

    // Rename Dialog (Selected Documents = 1)
    documentToRename?.let { doc ->
        var newTitle by remember(doc) { mutableStateOf(doc.title) }
        var renameError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = { Text("Rename Document", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF242426),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = {
                            newTitle = it
                            if (it.trim().isNotEmpty()) renameError = null
                        },
                        singleLine = true,
                        isError = renameError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ThemeAccentTeal,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (renameError != null) {
                        Text(
                            text = renameError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newTitle.trim()
                    if (trimmed.isEmpty()) {
                        renameError = "Document name cannot be empty"
                        return@TextButton
                    }
                    val sanitized = trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    viewModel.renameDocument(doc, sanitized)
                    documentToRename = null
                    viewModel.clearSelection()
                }) {
                    Text("Save", color = ThemeAccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToRename = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Batch Merge Dialog (Selected Documents = 2 or more)
    if (showBatchMergeDialog) {
        val selectedDocs = documents.filter { selectedDocIds.contains(it.id) }
        var mergeTitle by remember {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            mutableStateOf("Merged Doc $timeStamp")
        }
        var mergeError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showBatchMergeDialog = false },
            title = { Text("Merge Documents", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF242426),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Merge ${selectedDocs.size} selected documents into a new single document:",
                        color = Color(0xFF8E9BAE),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = mergeTitle,
                        onValueChange = {
                            mergeTitle = it
                            if (it.trim().isNotEmpty()) mergeError = null
                        },
                        label = { Text("Merged Document Title") },
                        singleLine = true,
                        isError = mergeError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ThemeAccentTeal,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = ThemeAccentTeal,
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mergeError != null) {
                        Text(
                            text = mergeError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = mergeTitle.trim()
                    if (trimmed.isEmpty()) {
                        mergeError = "Title cannot be empty"
                        return@TextButton
                    }
                    val sanitized = trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    viewModel.mergeSelectedDocuments(sanitized) { newDocId ->
                        onNavigateToDocumentDetail(newDocId)
                    }
                    showBatchMergeDialog = false
                }) {
                    Text("Merge", color = ThemeAccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchMergeDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Single Document Delete Confirmation Dialog
    documentToDelete?.let { doc ->
        CustomDeleteConfirmationDialog(
            title = "Delete",
            message = "This will permanently delete the selected content.",
            confirmButtonText = "Delete",
            cancelButtonText = "Cancel",
            onConfirm = {
                viewModel.deleteDocument(doc)
                documentToDelete = null
            },
            onDismiss = {
                documentToDelete = null
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        CustomDeleteConfirmationDialog(
            title = "Delete",
            message = "This will permanently delete the ${selectedDocIds.size} selected document(s).",
            confirmButtonText = "Delete",
            cancelButtonText = "Cancel",
            onConfirm = {
                viewModel.deleteSelectedDocuments()
                showBatchDeleteConfirm = false
            },
            onDismiss = {
                showBatchDeleteConfirm = false
            }
        )
    }

    // App Exit Confirmation Dialog
    if (showExitConfirmDialog) {
        ExitConfirmationDialog(
            title = "Exit CS Scanner",
            message = "Are you sure you want to exit the application?",
            confirmButtonText = "Exit",
            cancelButtonText = "Cancel",
            onConfirm = {
                showExitConfirmDialog = false
                val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>()
                    .firstOrNull() ?: (context as? Activity)
                activity?.finish()
            },
            onDismiss = {
                showExitConfirmDialog = false
            }
        )
    }

    // PDF Resize Dialog
    docForPdfResize?.let { doc ->
        var docPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
        LaunchedEffect(doc.id) {
            docPages = viewModel.getPagesForDocumentDirect(doc.id)
        }
        PdfResizeDialog(
            doc = doc,
            pages = docPages,
            onDismiss = { docForPdfResize = null },
            onExportWithPageSize = { pageSize ->
                coroutineScope.launch {
                    val pdfFile = PdfExporter.generatePdf(
                        context = context,
                        documentTitle = doc.title,
                        pages = docPages,
                        config = PdfExportConfig(title = doc.title, pageSize = pageSize)
                    )
                    if (pdfFile != null) {
                        Toast.makeText(context, "Exported PDF with ${pageSize.displayName}!", Toast.LENGTH_SHORT).show()
                        PdfExporter.sharePdf(context, pdfFile)
                    }
                }
            }
        )
    }

    // Split PDF Dialog
    docForSplitPdf?.let { doc ->
        var docPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
        LaunchedEffect(doc.id) {
            docPages = viewModel.getPagesForDocumentDirect(doc.id)
        }
        SplitPdfDialog(
            doc = doc,
            pages = docPages,
            onDismiss = { docForSplitPdf = null },
            onSplitPages = { selectedPages, newTitle ->
                coroutineScope.launch {
                    val pdfFile = PdfExporter.generatePdf(
                        context = context,
                        documentTitle = newTitle,
                        pages = selectedPages
                    )
                    if (pdfFile != null) {
                        Toast.makeText(context, "Extracted ${selectedPages.size} pages to new PDF!", Toast.LENGTH_SHORT).show()
                        PdfExporter.sharePdf(context, pdfFile)
                    }
                }
            }
        )
    }

    // Rotate PDF Dialog
    docForRotatePdf?.let { doc ->
        RotatePdfDialog(
            doc = doc,
            onDismiss = { docForRotatePdf = null },
            onRotateAllPages = {
                viewModel.rotateAllPagesDirect(doc)
            },
            onAutoUpright = {
                viewModel.autoUprightDocumentPages(doc)
            }
        )
    }

    // Watermark Dialog
    docForWatermark?.let { doc ->
        WatermarkDialog(
            initialText = "CONFIDENTIAL",
            onDismiss = { docForWatermark = null },
            onApplyWatermark = { text, opacity, colorLong ->
                coroutineScope.launch(Dispatchers.IO) {
                    val pages = viewModel.getPagesForDocumentDirect(doc.id)
                    pages.forEach { p ->
                        viewModel.applyWatermarkToPage(p, text, opacity, colorLong)
                    }
                }
                docForWatermark = null
            }
        )
    }

    // Signature Dialog
    docForSignature?.let { doc ->
        SignatureDialog(
            onDismiss = { docForSignature = null },
            onSignatureSaved = { sigBitmap ->
                coroutineScope.launch(Dispatchers.IO) {
                    val pages = viewModel.getPagesForDocumentDirect(doc.id)
                    val firstPage = pages.firstOrNull()
                    if (firstPage != null) {
                        viewModel.applySignatureToPage(firstPage, sigBitmap)
                    }
                }
                docForSignature = null
                onNavigateToDocumentDetail(doc.id)
            }
        )
    }

    // Unlock Document Dialog (Password protection check when opening document)
    docForUnlock?.let { doc ->
        UnlockDocumentDialog(
            document = doc,
            onDismiss = { docForUnlock = null },
            onUnlockSuccess = {
                val targetDocId = doc.id
                docForUnlock = null
                onNavigateToDocumentDetail(targetDocId)
            }
        )
    }

    // Protect Dialog
    docForProtect?.let { doc ->
        LockDocumentDialog(
            docTitle = doc.title,
            onDismiss = { docForProtect = null }
        )
    }

    // Compress Dialog
    docForCompress?.let { doc ->
        CompressDialog(
            doc = doc,
            onDismiss = { docForCompress = null },
            onCompressAndShare = { config ->
                viewModel.exportAndSharePdf(doc, config)
                docForCompress = null
            }
        )
    }

    // Merge Dialog
    docForMerge?.let { doc ->
        MergeFilesDialog(
            currentDocId = doc.id,
            allDocuments = documents,
            onDismiss = { docForMerge = null },
            onMergeWith = { sourceId ->
                viewModel.mergeDocuments(doc, sourceId)
                docForMerge = null
            }
        )
    }

    // OCR Text / Translate Dialog
    docForOcrText?.let { doc ->
        var extractedText by remember { mutableStateOf("") }
        LaunchedEffect(doc.id) {
            val pages = viewModel.getPagesForDocumentDirect(doc.id)
            extractedText = pages.mapNotNull { it.extractedText }.filter { it.isNotBlank() }.joinToString("\n\n")
        }
        TranslateDialog(
            extractedText = extractedText,
            onDismiss = { docForOcrText = null }
        )
    }

}

// =========================================================================================
// 1. HOME SCREEN TAB CONTENT (Exact match to Screenshot 2)
// =========================================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    documents: List<DocumentEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedDocIds: Set<Long>,
    isSelectionMode: Boolean,
    onDocumentClick: (DocumentEntity) -> Unit,
    onDocumentLongClick: (DocumentEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onNavigateToCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
    ) {
        // Top Full-Width Dark Search Bar (Screenshot 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ThemeSearchBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ThemeTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ThemeTextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(ThemeAccentTeal),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    color = ThemeTextMuted,
                                    fontSize = 14.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = ThemeTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Document List
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ThemeSurfaceDark,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = ThemeAccentTeal,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Documents Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ThemeTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tap the camera button to scan your first document",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThemeTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    CamScannerDocItem(
                        doc = doc,
                        isSelected = selectedDocIds.contains(doc.id),
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(doc) },
                        onLongClick = { onDocumentLongClick(doc) },
                        onCheckboxClick = { onToggleSelection(doc.id) }
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 2. FILES SCREEN TAB CONTENT (Exact match to Screenshot 3)
// =========================================================================================
@Composable
private fun FilesContent(
    documents: List<DocumentEntity>,
    folders: List<String>,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    selectedDocIds: Set<Long>,
    isSelectionMode: Boolean,
    onToggleSelectionMode: () -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onDocumentLongClick: (DocumentEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onNewFolderClick: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("date") } // "date", "name", "pages"
    var isSearchExpanded by remember { mutableStateOf(false) }
    val storageInfo = remember { getDeviceStorageInfo() }

    val filteredDocs = remember(documents, selectedFolder, searchQuery, sortBy) {
        val folderFiltered = when (selectedFolder) {
            "All" -> documents
            "Starred" -> documents.filter { it.isStarred }
            else -> documents.filter { it.folder == selectedFolder }
        }
        val searched = if (searchQuery.isBlank()) {
            folderFiltered
        } else {
            folderFiltered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.tags.contains(searchQuery, ignoreCase = true) ||
                    it.extractedText.orEmpty().contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortBy) {
            "name" -> searched.sortedBy { it.title.lowercase() }
            "pages" -> searched.sortedByDescending { it.pageCount }
            else -> searched.sortedByDescending { it.createdAt }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
    ) {
        // --- TOP BAR: Phone Storage indicator on left, Folder & Search actions on right ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Real Device Storage indicator + Progress bar
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2C323D),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Device Storage",
                            tint = Color(0xFF67B0FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = storageInfo.first,
                        color = ThemeTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF333D4B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(storageInfo.third.coerceIn(0.05f, 1f))
                                .height(2.5.dp)
                                .background(ThemeAccentTeal)
                        )
                    }
                }
            }

            // Right Action Icons: Add Folder [+], Search (Cloud icon removed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNewFolderClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.AddBox,
                        contentDescription = "New Folder",
                        tint = ThemeTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) {
                            onSearchChange("")
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchExpanded || searchQuery.isNotBlank()) ThemeAccentTeal else ThemeTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- EXPANDABLE SEARCH BAR ---
        AnimatedVisibility(
            visible = isSearchExpanded || searchQuery.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = ThemeSearchBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ThemeBorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ThemeAccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search files by name...",
                                color = ThemeTextMuted,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = ThemeTextPrimary,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = ThemeTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- SUBHEADER: "All (402) ▼" on left | Sort, ViewList/Grid, SelectAll on right ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder dropdown button
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showFolderDropdown = true }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "$selectedFolder (${filteredDocs.size})",
                        color = ThemeTextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("▼", color = Color(0xFF8E9BAE), fontSize = 10.sp)
                }

                DropdownMenu(
                    expanded = showFolderDropdown,
                    onDismissRequest = { showFolderDropdown = false },
                    modifier = Modifier.background(ThemeSurfaceDark)
                ) {
                    folders.forEach { folder ->
                        val count = when (folder) {
                            "All" -> documents.size
                            "Starred" -> documents.count { it.isStarred }
                            else -> documents.count { it.folder == folder }
                        }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(folder, color = if (selectedFolder == folder) ThemeAccentTeal else ThemeTextPrimary)
                                    Text("($count)", color = ThemeTextMuted)
                                }
                            },
                            onClick = {
                                onSelectFolder(folder)
                                showFolderDropdown = false
                            }
                        )
                    }
                }
            }

            // Right icons: Sort, Grid/List toggle, Multi-select checklist
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Sort",
                            tint = Color(0xFF8E9BAE),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(ThemeSurfaceDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Recent First", color = ThemeTextPrimary) },
                            onClick = { sortBy = "date"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)", color = ThemeTextPrimary) },
                            onClick = { sortBy = "name"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Page Count", color = ThemeTextPrimary) },
                            onClick = { sortBy = "pages"; showSortMenu = false }
                        )
                    }
                }

                // Grid / List Toggle
                IconButton(onClick = onToggleGridView, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle Grid",
                        tint = Color(0xFF8E9BAE),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Select All / Selection Mode Toggle
                IconButton(onClick = onToggleSelectionMode, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = "Multi Select",
                        tint = if (isSelectionMode) ThemeAccentTeal else Color(0xFF8E9BAE),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Document List or Grid
        if (filteredDocs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF4A5568), modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No documents in $selectedFolder", color = ThemeTextMuted, fontSize = 14.sp)
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
                    CamScannerDocGridCard(
                        doc = doc,
                        isSelected = selectedDocIds.contains(doc.id),
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(doc) },
                        onLongClick = { onDocumentLongClick(doc) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
                    CamScannerDocItem(
                        doc = doc,
                        isSelected = selectedDocIds.contains(doc.id),
                        isSelectionMode = isSelectionMode,
                        onClick = { onDocumentClick(doc) },
                        onLongClick = { onDocumentLongClick(doc) },
                        onCheckboxClick = { onToggleSelection(doc.id) }
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 3. TOOLS SCREEN TAB CONTENT (Exact match to Reference Screenshot 1 & 4)
// =========================================================================================
@Composable
private fun ToolsContent(
    onSelectScanMode: (ScanMode, ScannerFeatureMode?) -> Unit,
    onExtractText: () -> Unit,
    onPassportPhotoMaker: () -> Unit,
    onPhotoTranslation: () -> Unit,
    onImageToPdf: () -> Unit,
    onTextToPdf: () -> Unit,
    onToWord: () -> Unit,
    onToExcel: () -> Unit,
    onPdfToImages: () -> Unit,
    onPdfToLongImage: () -> Unit,
    onImageResizer: () -> Unit,
    onPhotoBackgroundRemover: () -> Unit = {},
    onEditPdf: () -> Unit = {},
    onResizePdf: () -> Unit,
    onSplitPdf: () -> Unit,
    onRotatePdf: () -> Unit,
    onSign: () -> Unit,
    onAddWatermark: () -> Unit,
    onSmartErase: () -> Unit,
    onEraseMarks: () -> Unit,
    onMergeFiles: () -> Unit,
    onExtractPdfPages: () -> Unit,
    onReorderPages: () -> Unit,
    onLock: () -> Unit,
    onCompress: () -> Unit,
    onAiChat: () -> Unit,
    onPrint: () -> Unit,
    onScanCode: () -> Unit,
    onOpenCvCrop: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("Scan") }
    val categories = listOf("Scan", "Convert", "Edit", "Utilities")
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Sync selected category tab with scrolling
    val currentVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(currentVisibleIndex) {
        if (currentVisibleIndex in categories.indices) {
            selectedCategory = categories[currentVisibleIndex]
        }
    }

    val scanTools = remember {
        listOf(
            ToolCircleItem("ID Cards", Icons.Default.Badge, Color(0xFF00BFA5)) {
                onSelectScanMode(ScanMode.ID_CARD, ScannerFeatureMode.ID_CARDS)
            },
            ToolCircleItem("Extract Text", Icons.Default.TextFields, Color(0xFF00D2D3)) {
                onExtractText()
            },
            ToolCircleItem("Passport Photo Maker", Icons.Default.Person, Color(0xFF3B82F6)) {
                onPassportPhotoMaker()
            },
            ToolCircleItem("photo translation", Icons.Default.Translate, Color(0xFF818CF8)) {
                onPhotoTranslation()
            },
            ToolCircleItem("Scan Code", Icons.Default.QrCodeScanner, Color(0xFF10B981)) {
                onScanCode()
            }
        )
    }

    val convertTools = remember {
        listOf(
            ToolCircleItem("Merge PDF", Icons.Default.CallMerge, Color(0xFF00BFA5)) {
                onMergeFiles()
            },
            ToolCircleItem("Image to PDF", Icons.Default.Image, Color(0xFF10B981)) {
                onImageToPdf()
            },
            ToolCircleItem("Text to PDF", Icons.Default.Article, Color(0xFF0EA5E9)) {
                onTextToPdf()
            },
            ToolCircleItem("To Word", Icons.Default.Article, Color(0xFF2563EB)) {
                onToWord()
            },
            ToolCircleItem("To Excel", Icons.Default.TableChart, Color(0xFF16A34A)) {
                onToExcel()
            },
            ToolCircleItem("PDF to Images", Icons.Default.Collections, Color(0xFF00BFA5)) {
                onPdfToImages()
            },
            ToolCircleItem("PDF to Long Image", Icons.Default.PhotoSizeSelectActual, Color(0xFF0EA5E9)) {
                onPdfToLongImage()
            }
        )
    }

    val editTools = remember {
        listOf(
            ToolCircleItem("Edit PDF", Icons.Default.Edit, Color(0xFF14B8A6), hasNewBadge = true) {
                onEditPdf()
            },
            ToolCircleItem("BG Remover", Icons.Default.AutoFixHigh, Color(0xFF8B5CF6), hasNewBadge = true) {
                onPhotoBackgroundRemover()
            },
            ToolCircleItem("Image Resizer", Icons.Default.Crop, Color(0xFFEC4899)) {
                onImageResizer()
            },
            ToolCircleItem("Split PDF", Icons.Default.CallSplit, Color(0xFF3B82F6)) {
                onSplitPdf()
            },
            ToolCircleItem("Sign", Icons.Default.Draw, Color(0xFF00BFA5)) {
                onSign()
            },
            ToolCircleItem("Add Watermark", Icons.Default.BrandingWatermark, Color(0xFF3B82F6)) {
                onAddWatermark()
            },
            ToolCircleItem("Extract PDF Pages", Icons.Default.CallSplit, Color(0xFF3B82F6)) {
                onExtractPdfPages()
            },
            ToolCircleItem("Reorder Pages", Icons.Default.SwapVert, Color(0xFF6366F1)) {
                onReorderPages()
            },
            ToolCircleItem("Rotate PDF", Icons.Default.RotateRight, Color(0xFF8B5CF6)) {
                onRotatePdf()
            },
            ToolCircleItem("Lock", Icons.Default.Lock, Color(0xFF10B981)) {
                onLock()
            },
            ToolCircleItem("Compress", Icons.Default.Compress, Color(0xFF3B82F6)) {
                onCompress()
            }
        )
    }

    val utilityTools = remember {
        listOf(
            ToolCircleItem("AI Chat", Icons.Default.Psychology, Color(0xFF00BFA5)) {
                onAiChat()
            },
            ToolCircleItem("Print", Icons.Default.Print, Color(0xFF00BFA5)) {
                onPrint()
            },
            ToolCircleItem("Create QR Code", Icons.Default.QrCode2, Color(0xFF00BFA5)) {
                onScanCode()
            }
        )
    }

    val allTools = remember(scanTools, convertTools, editTools, utilityTools) {
        scanTools + convertTools + editTools + utilityTools
    }

    val filteredTools = remember(searchQuery, allTools) {
        if (searchQuery.isBlank()) emptyList()
        else allTools.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
    ) {
        // --- HEADER: "Tools" (24sp bold white) + Search Icon (Screenshot 1 & 4) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tools",
                color = ThemeTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                isSearchOpen = !isSearchOpen
                if (!isSearchOpen) searchQuery = ""
            }) {
                Icon(
                    if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchOpen || searchQuery.isNotBlank()) ThemeAccentTeal else ThemeTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // --- EXPANDABLE SEARCH BAR ---
        AnimatedVisibility(
            visible = isSearchOpen || searchQuery.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = ThemeSearchBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ThemeBorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ThemeAccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search tools (e.g. Word, Sign, Excel)...",
                                color = ThemeTextMuted,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = ThemeTextPrimary,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = ThemeTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            // Search Results
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Results (${filteredTools.size})",
                        color = ThemeTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                    )
                    if (filteredTools.isEmpty()) {
                        Text(
                            text = "No tools found matching \"$searchQuery\"",
                            color = ThemeTextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    } else {
                        ToolCircleGrid(tools = filteredTools)
                    }
                }
            }
        } else {
            // --- CATEGORY TABS: Scan, Import, Convert, Edit, Utilities (Top Navigation) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                categories.forEachIndexed { index, cat ->
                    val isSelected = selectedCategory == cat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                selectedCategory = cat
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) ThemeTextPrimary else ThemeTextMuted,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ThemeAccentTeal)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.5.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- 4-COLUMN GRID OF TOOLS: ALL 5 CATEGORIES (Scan, Import, Convert, Edit, Utilities) ---
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // ================= SECTION 1: SCAN =================
                item {
                    Column {
                        Text(
                            text = "Scan",
                            color = ThemeTextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                        )
                        ToolCircleGrid(tools = scanTools)
                    }
                }

                // ================= SECTION 2: CONVERT =================
                item {
                    Column {
                        Text(
                            text = "Convert",
                            color = ThemeTextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                        )
                        ToolCircleGrid(tools = convertTools)
                    }
                }

                // ================= SECTION 3: EDIT =================
                item {
                    Column {
                        Text(
                            text = "Edit",
                            color = ThemeTextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                        )
                        ToolCircleGrid(tools = editTools)
                    }
                }

                // ================= SECTION 5: UTILITIES =================
                item {
                    Column {
                        Text(
                            text = "Utilities",
                            color = ThemeTextPrimary,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                        )
                        ToolCircleGrid(tools = utilityTools)
                    }
                }
            }
        }
    }
}

// Data class and component for 4-column circular tools
private data class ToolCircleItem(
    val label: String,
    val icon: ImageVector,
    val iconTint: Color,
    val hasNewBadge: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun ToolCircleGrid(tools: List<ToolCircleItem>) {
    // 4 items per row layout matching Screenshots
    val rows = tools.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 0 until 4) {
                    if (i < rowItems.size) {
                        val tool = rowItems[i]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(78.dp)
                                .clickable(onClick = tool.onClick)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val isDark = rememberAppThemePalette().isDark
                                val effectiveTint = if (isDark) {
                                    tool.iconTint
                                } else {
                                    when (tool.iconTint) {
                                        Color(0xFF00D2D3) -> Color(0xFF0D9488)
                                        Color(0xFF818CF8) -> Color(0xFF4F46E5)
                                        Color(0xFF0EA5E9) -> Color(0xFF0284C7)
                                        Color(0xFF06B6D4) -> Color(0xFF0891B2)
                                        else -> tool.iconTint
                                    }
                                }
                                val circleBg = if (isDark) {
                                    Color(0xFF202632)
                                } else {
                                    effectiveTint.copy(alpha = 0.12f)
                                }
                                val circleBorder = if (isDark) {
                                    BorderStroke(1.dp, Color(0xFF2E3646))
                                } else {
                                    BorderStroke(1.dp, effectiveTint.copy(alpha = 0.22f))
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = circleBg,
                                    border = circleBorder,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            tool.icon,
                                            contentDescription = tool.label,
                                            tint = effectiveTint,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                if (tool.hasNewBadge) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            "New",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tool.label,
                                color = ThemeTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(78.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================================
// REUSABLE DOCUMENT LIST CARD (Screenshot 2 & 3 exact design)
// =========================================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CamScannerDocItem(
    doc: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckboxClick: () -> Unit
) {
    val formattedDate = remember(doc.createdAt) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(doc.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) Color(0xFF0F3A35) else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Document Thumbnail (White paper preview with subtle rounded corners)
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (doc.thumbnailPath.isNotBlank() && File(doc.thumbnailPath).exists()) {
                AsyncImage(
                    model = File(doc.thumbnailPath),
                    contentDescription = doc.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Text("PDF", color = Color(0xFFDC2626), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Top-left PDF/A or OCR badge
            if (!doc.extractedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    color = Color(0xFF0F766E),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "OCR",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // Lock badge overlay
            if (DocumentLockManager.isDocumentLocked(doc.id)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFF00BFA5),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = doc.title,
                color = ThemeTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedDate,
                    color = ThemeTextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = ThemeTextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = doc.pageCount.toString(),
                    color = ThemeTextMuted,
                    fontSize = 12.sp
                )
                if (doc.isStarred) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Starred",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Checkbox on the right (Square with rounded corners - Screenshot 2)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onCheckboxClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = ThemeAccentTeal,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, Color(0xFF555B66), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

// Reusable Grid Card for 2-column view
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CamScannerDocGridCard(
    doc: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formattedDate = remember(doc.createdAt) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(doc.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0F3A35) else ThemeSurfaceDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF2C323D)),
                contentAlignment = Alignment.Center
            ) {
                if (doc.thumbnailPath.isNotBlank() && File(doc.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(doc.thumbnailPath),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        tint = ThemeAccentTeal,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Page count badge
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp),
                    color = Color(0xCC000000),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        "${doc.pageCount} P",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Lock badge overlay
                if (DocumentLockManager.isDocumentLocked(doc.id)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFF00BFA5),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = doc.title,
                    color = ThemeTextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    color = ThemeTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SelectionMoreGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFF2D2F33),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SelectionMoreReferenceRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFE8E9EC),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            color = Color(0xFFF3F4F6),
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun addDocumentShortcutToHome(context: Context, doc: DocumentEntity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "Home screen shortcuts require Android 8.0 or newer", Toast.LENGTH_SHORT).show()
        return
    }

    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
        Toast.makeText(context, "Your launcher does not support pinned shortcuts", Toast.LENGTH_SHORT).show()
        return
    }

    val openIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_OPEN_DOCUMENT_ID, doc.id)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    val shortcut = ShortcutInfo.Builder(context, "document_${doc.id}")
        .setShortLabel(doc.title.take(24).ifBlank { "Document" })
        .setLongLabel(doc.title.ifBlank { "Open document" })
        .setIcon(AndroidIcon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(openIntent)
        .build()

    try {
        shortcutManager.requestPinShortcut(shortcut, null)
    } catch (_: Throwable) {
        Toast.makeText(context, "Could not add shortcut to Home screen", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun BatchMoreActionRow(
    icon: ImageVector,
    title: String,
    tint: Color = ThemeTextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF2D2F33), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (tint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ThemeTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SelectionBottomBarItem(
    icon: ImageVector,
    label: String,
    iconTint: Color = ThemeTextSecondary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (iconTint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else ThemeTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpeedDialActionItem(
    label: String,
    subtitle: String? = null,
    icon: ImageVector,
    buttonBg: Color,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(end = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E2228),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFF333942))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(6.dp, CircleShape)
                .background(buttonBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
