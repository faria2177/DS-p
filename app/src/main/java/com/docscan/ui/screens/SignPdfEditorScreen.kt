package com.docscan.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.components.AddSignatureBottomSheet
import com.docscan.ui.components.PageOverlayContainer
import com.docscan.ui.components.ImportedSignaturePreviewDialog
import com.docscan.ui.components.SignBorder
import com.docscan.ui.components.StampCustomColorPicker
import com.docscan.ui.components.SignCardBg
import com.docscan.ui.components.SignDarkCanvas
import com.docscan.ui.components.SignSheetBg
import com.docscan.ui.components.SignTeal
import com.docscan.ui.components.SignTealContainer
import com.docscan.ui.components.SignTextPrimary
import com.docscan.ui.components.SignTextSecondary
import com.docscan.ui.components.SignatureDrawingDialog
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import com.docscan.util.PdfExportConfig
import com.docscan.util.PdfExporter
import com.docscan.util.SavedSignatureItem
import com.docscan.util.SavedStampItem
import com.docscan.util.SignOverlayPlacement
import com.docscan.util.SignatureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfEditorScreen(
    documentId: Long,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onSavedAndOpenDoc: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var pages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Placed overlays across pages
    val placedOverlays = remember { mutableStateListOf<SignOverlayPlacement>() }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var activePageIndex by remember { mutableIntStateOf(0) }

    // Bottom Selection Panel states
    var showBottomPanel by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Signature, 1 = Stamp

    // Modals
    var showAddSignatureOptions by remember { mutableStateOf(false) }
    var showDrawSignatureDialog by remember { mutableStateOf(false) }
    var showDateStampDialog by remember { mutableStateOf(false) }
    var showCustomStampDialog by remember { mutableStateOf(false) }

    // Saved Items from Disk
    var savedSignatures by remember { mutableStateOf<List<SavedSignatureItem>>(emptyList()) }
    var savedStamps by remember { mutableStateOf<List<SavedStampItem>>(emptyList()) }

    // Processing & deletion states
    var isProcessingItem by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(id, type: "signature"|"stamp")
    var pendingImportedSignature by remember { mutableStateOf<Bitmap?>(null) }

    fun refreshSavedItems() {
        savedSignatures = SignatureManager.getSavedSignatures(context)
        savedStamps = SignatureManager.getSavedStamps(context)
    }

    LaunchedEffect(Unit) {
        refreshSavedItems()
    }

    // Load Document and Pages
    LaunchedEffect(documentId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val doc = viewModel.getDocumentDirect(documentId)
            val pList = viewModel.getPagesForDocumentDirect(documentId)
            withContext(Dispatchers.Main) {
                document = doc
                pages = pList
                isLoading = false
            }
        }
    }

    // Gallery Picker for Signature Extraction
    val gallerySignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingItem = true
            processingMessage = "Importing & processing signature..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bmp = FileUtils.loadBitmapsFromUri(context, it).firstOrNull()
                    if (bmp != null) {
                        val prepared = SignatureManager.prepareImportedSignature(bmp)
                        withContext(Dispatchers.Main) {
                            pendingImportedSignature = prepared
                            showBottomPanel = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isProcessingItem = false
                    }
                }
            }
        }
    }

    // Camera capture for Signature Scan
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraSignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            isProcessingItem = true
            processingMessage = "Scanning & extracting signature..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bmp = FileUtils.loadBitmapsFromUri(context, tempCameraUri!!).firstOrNull()
                    if (bmp != null) {
                        val saved = SignatureManager.processAndSaveScannedSignature(context, bmp)
                        val loadedBmp = FileUtils.loadBitmap(saved.filePath) ?: bmp
                        withContext(Dispatchers.Main) {
                            refreshSavedItems()
                            val newOverlay = SignOverlayPlacement(
                                pageIndex = activePageIndex,
                                bitmap = loadedBmp,
                                type = "signature",
                                x = 0.5f,
                                y = 0.65f,
                                widthRatio = 0.35f
                            )
                            placedOverlays.add(newOverlay)
                            selectedOverlayId = newOverlay.id
                            showBottomPanel = false
                            Toast.makeText(context, "Signature scanned", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not capture image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to scan: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isProcessingItem = false
                    }
                }
            }
        }
    }

    // Runtime Permission for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val photoFile = File(context.cacheDir, "sig_capture_${System.currentTimeMillis()}.jpg")
                photoFile.parentFile?.mkdirs()
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                tempCameraUri = uri
                cameraSignatureLauncher.launch(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Cannot open camera: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission needed to scan signature", Toast.LENGTH_SHORT).show()
        }
    }

    val launchSignatureCamera = {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            try {
                val photoFile = File(context.cacheDir, "sig_capture_${System.currentTimeMillis()}.jpg")
                photoFile.parentFile?.mkdirs()
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                tempCameraUri = uri
                cameraSignatureLauncher.launch(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Cannot open camera: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Gallery Picker for Stamp Image
    val galleryStampLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingItem = true
            processingMessage = "Importing stamp..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bmp = FileUtils.loadBitmapsFromUri(context, it).firstOrNull()
                    if (bmp != null) {
                        val saved = SignatureManager.saveStamp(context, bmp, "Custom Stamp ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                        withContext(Dispatchers.Main) {
                            refreshSavedItems()
                            val loadedBmp = FileUtils.loadBitmap(saved.filePath)
                            if (loadedBmp != null) {
                                val newOverlay = SignOverlayPlacement(
                                    pageIndex = activePageIndex,
                                    bitmap = loadedBmp,
                                    type = "stamp",
                                    x = 0.5f,
                                    y = 0.65f,
                                    widthRatio = 0.30f
                                )
                                placedOverlays.add(newOverlay)
                                selectedOverlayId = newOverlay.id
                                showBottomPanel = false
                                Toast.makeText(context, "Stamp imported", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not load stamp image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import stamp: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isProcessingItem = false
                    }
                }
            }
        }
    }

    // Save Signed Document Function
    fun saveSignedDocument() {
        if (pages.isEmpty()) {
            Toast.makeText(context, "This document has no pages to sign.", Toast.LENGTH_SHORT).show()
            return
        }
        if (placedOverlays.isEmpty()) {
            Toast.makeText(context, "Add a signature or stamp before saving.", Toast.LENGTH_SHORT).show()
            showBottomPanel = true
            selectedTab = 0
            return
        }
        if (isSaving) return

        isSaving = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val baseTitle = (document?.title ?: "Document").removeSuffix("_signed")
                val signedTitle = "${baseTitle}_signed"
                val updatedPages = mutableListOf<PageEntity>()

                pages.forEachIndexed { index, pageEntity ->
                    val baseBmp = FileUtils.loadBitmap(pageEntity.processedImagePath)
                        ?: FileUtils.loadBitmap(pageEntity.originalImagePath)
                        ?: throw IllegalStateException("Page ${index + 1} could not be loaded")

                    val pageOverlays = placedOverlays.filter { it.pageIndex == index }
                    val compositedBmp = if (pageOverlays.isNotEmpty()) {
                        SignatureManager.compositeOverlaysOnPage(baseBmp, pageOverlays)
                    } else {
                        baseBmp
                    }

                    // Every signed document gets its own page files. Never reuse source paths,
                    // otherwise deleting one document can break the other document.
                    val newPath = FileUtils.saveBitmapToDocStorage(context, compositedBmp, "SIGNED")
                    if (newPath.isBlank() || !File(newPath).exists()) {
                        throw IllegalStateException("Could not save signed page ${index + 1}")
                    }

                    updatedPages.add(
                        pageEntity.copy(
                            id = 0,
                            documentId = 0,
                            pageNumber = index + 1,
                            processedImagePath = newPath,
                            originalImagePath = newPath,
                            rotationDegrees = 0,
                            signatureImagePath = null
                        )
                    )
                }

                if (updatedPages.size != pages.size) {
                    throw IllegalStateException("Not all pages were prepared for signing")
                }

                val generatedPdf = PdfExporter.generatePdf(
                    context = context,
                    documentTitle = signedTitle,
                    pages = updatedPages,
                    config = PdfExportConfig(title = signedTitle)
                ) ?: throw IllegalStateException("Signed PDF could not be generated")

                val newDocId = viewModel.saveNewDocument(
                    title = signedTitle,
                    folder = document?.folder ?: "All Docs",
                    pages = updatedPages
                )

                val savedDoc = viewModel.getDocumentDirect(newDocId)
                    ?: throw IllegalStateException("Signed document could not be saved")
                viewModel.updateDocument(savedDoc.copy(pdfPath = generatedPdf.absolutePath))

                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Signed document saved successfully", Toast.LENGTH_SHORT).show()
                    onSavedAndOpenDoc(newDocId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(
                        context,
                        "Failed to save signed document: ${e.localizedMessage ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SignDarkCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // TOP APP BAR (Matching Screenshot 2 & 4: ← Sign)
            Surface(
                color = SignDarkCanvas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SignTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Sign",
                        color = SignTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (pages.isNotEmpty()) {
                        Text(
                            text = "Page ${activePageIndex + 1} of ${pages.size}",
                            color = SignTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            }

            // MAIN DOCUMENT VIEWER (Vertically scrollable pages with touch selection & overlays)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            // Tap outside deselects active bounding box
                            selectedOverlayId = null
                        }
                    }
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SignTeal)
                    }
                } else if (pages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pages found in this document", color = SignTextSecondary)
                    }
                } else {
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(pages.size) { pageIdx ->
                            val page = pages[pageIdx]

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Page Container Card
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            activePageIndex = pageIdx
                                            selectedOverlayId = null
                                        }
                                ) {
                                    var renderedHeightPx by remember(page.id) { mutableFloatStateOf(0f) }

                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onSizeChanged { size ->
                                                if (size.height > 0) {
                                                    renderedHeightPx = size.height.toFloat()
                                                }
                                            }
                                    ) {
                                        val boxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                                        val boxHeightPx = if (renderedHeightPx > 0f) renderedHeightPx else boxWidthPx * 1.414f

                                        // Render page image
                                        AsyncImage(
                                            model = File(page.processedImagePath),
                                            contentDescription = "Page ${pageIdx + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Render overlays for this specific page
                                        val pageOverlays = placedOverlays.filter { it.pageIndex == pageIdx }
                                        pageOverlays.forEach { overlay ->
                                            PageOverlayContainer(
                                                overlay = overlay,
                                                isSelected = selectedOverlayId == overlay.id,
                                                pageWidthPx = boxWidthPx,
                                                pageHeightPx = boxHeightPx,
                                                onSelect = {
                                                    activePageIndex = pageIdx
                                                    selectedOverlayId = overlay.id
                                                },
                                                onUpdate = { updated ->
                                                    val index = placedOverlays.indexOfFirst { it.id == overlay.id }
                                                    if (index != -1) {
                                                        placedOverlays[index] = updated
                                                    }
                                                },
                                                onDelete = {
                                                    placedOverlays.removeIf { it.id == overlay.id }
                                                    if (selectedOverlayId == overlay.id) {
                                                        selectedOverlayId = null
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "- Page ${pageIdx + 1} -",
                                    color = SignTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // BOTTOM EDITOR TOOLBAR (Matching Reference Screenshot 2 & 4: [✍️ Sign & Stamp] [📅 Date] [✓ Button])
            Surface(
                color = SignSheetBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Tool: Sign & Stamp
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectedTab = 0
                                showBottomPanel = !showBottomPanel
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (showBottomPanel && selectedTab == 0) SignTeal else SignTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign & Stamp",
                            color = if (showBottomPanel && selectedTab == 0) SignTeal else SignTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Middle Tool: Date
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showDateStampDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = SignTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Date",
                            color = SignTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Right Tool: Confirm / Save Button (Green circular checkmark)
                    FloatingActionButton(
                        onClick = { saveSignedDocument() },
                        shape = CircleShape,
                        containerColor = SignTeal,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Signed Document",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Floating Quick Controls Bar for Selected Signature / Stamp Overlay
        AnimatedVisibility(
            visible = selectedOverlayId != null && !showBottomPanel,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            val activeOverlay = placedOverlays.firstOrNull { it.id == selectedOverlayId }
            if (activeOverlay != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1E24),
                    border = BorderStroke(1.dp, Color(0xFF33333E)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rotate 90° button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val nextRot = (activeOverlay.rotationDegrees + 90f) % 360f
                                    val index = placedOverlays.indexOfFirst { it.id == activeOverlay.id }
                                    if (index != -1) {
                                        placedOverlays[index] = activeOverlay.copy(rotationDegrees = nextRot)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate 90°",
                                tint = SignTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rotate 90° (${activeOverlay.rotationDegrees.toInt()}°)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .width(1.dp)
                                .background(Color(0xFF33333E))
                        )

                        // Delete button
                        IconButton(
                            onClick = {
                                placedOverlays.removeIf { it.id == activeOverlay.id }
                                selectedOverlayId = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Done button
                        IconButton(
                            onClick = { selectedOverlayId = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // SCREEN 3: SIGNATURE / STAMP BOTTOM SELECTION PANEL (Slides up from bottom)
        AnimatedVisibility(
            visible = showBottomPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = SignSheetBg,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    // Panel Header: Tabs (Signature | Stamp) and Close Button '✕'
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tabs
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Signature Tab
                            Column(
                                modifier = Modifier
                                    .clickable { selectedTab = 0 }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Signature",
                                    color = if (selectedTab == 0) SignTextPrimary else SignTextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedTab == 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(SignTeal)
                                    )
                                }
                            }

                            // Stamp Tab
                            Column(
                                modifier = Modifier
                                    .clickable { selectedTab = 1 }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Stamp",
                                    color = if (selectedTab == 1) SignTextPrimary else SignTextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedTab == 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(SignTeal)
                                    )
                                }
                            }
                        }

                        // Close '✕' Button
                        IconButton(onClick = { showBottomPanel = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Panel",
                                tint = SignTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Content
                    if (selectedTab == 0) {
                        // SIGNATURE TAB CONTENT (Matching Reference Screenshot 3)
                        if (savedSignatures.isEmpty()) {
                            // Empty State: "No signatures yet. Add one:" with Scan & Import options
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "No signatures yet. Add one:",
                                    color = SignTextSecondary,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Scan Button
                                    PanelEmptyActionCard(
                                        icon = Icons.Default.CameraAlt,
                                        title = "Scan",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            launchSignatureCamera()
                                        }
                                    )

                                    // Import Button
                                    PanelEmptyActionCard(
                                        icon = Icons.Default.Image,
                                        title = "Import",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            gallerySignatureLauncher.launch("image/*")
                                        }
                                    )

                                    // Draw Button
                                    PanelEmptyActionCard(
                                        icon = Icons.Default.Draw,
                                        title = "Draw",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            showDrawSignatureDialog = true
                                        }
                                    )
                                }
                            }
                        } else {
                            // Horizontal List: [+ Add Button] + [Saved Signature Items]
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Add New Button (Teal Circle Plus)
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SignCardBg,
                                        border = BorderStroke(1.dp, SignBorder),
                                        modifier = Modifier
                                            .size(width = 80.dp, height = 70.dp)
                                            .clickable { showAddSignatureOptions = true }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = SignTeal,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Signature",
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Saved Signature Items
                                items(savedSignatures, key = { it.id }) { sigItem ->
                                    Box(
                                        modifier = Modifier.size(width = 110.dp, height = 70.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable {
                                                    // Place signature on document
                                                    val bmp = FileUtils.loadBitmap(sigItem.filePath)
                                                    if (bmp != null) {
                                                        val overlay = SignOverlayPlacement(
                                                            pageIndex = activePageIndex,
                                                            bitmap = bmp,
                                                            type = "signature",
                                                            x = 0.5f,
                                                            y = 0.65f,
                                                            widthRatio = 0.35f
                                                        )
                                                        placedOverlays.add(overlay)
                                                        selectedOverlayId = overlay.id
                                                        showBottomPanel = false
                                                    }
                                                }
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = File(sigItem.filePath),
                                                    contentDescription = sigItem.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        // Delete mini-button
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xCCEF4444),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .clickable {
                                                    itemToDelete = Pair(sigItem.id, "signature")
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete Signature",
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // STAMP TAB CONTENT
                        if (savedStamps.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "No stamps yet. Add one:",
                                    color = SignTextSecondary,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PanelEmptyActionCard(
                                        icon = Icons.Default.DateRange,
                                        title = "Date",
                                        modifier = Modifier.weight(1f),
                                        onClick = { showDateStampDialog = true }
                                    )

                                    PanelEmptyActionCard(
                                        icon = Icons.Default.Verified,
                                        title = "Official",
                                        modifier = Modifier.weight(1f),
                                        onClick = { showCustomStampDialog = true }
                                    )

                                    PanelEmptyActionCard(
                                        icon = Icons.Default.Image,
                                        title = "Import",
                                        modifier = Modifier.weight(1f),
                                        onClick = { galleryStampLauncher.launch("image/*") }
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Add New Stamp Button
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SignCardBg,
                                        border = BorderStroke(1.dp, SignBorder),
                                        modifier = Modifier
                                            .size(width = 80.dp, height = 70.dp)
                                            .clickable { showCustomStampDialog = true }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = SignTeal,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Stamp",
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Saved Stamp Items
                                items(savedStamps, key = { it.id }) { stampItem ->
                                    Box(
                                        modifier = Modifier.size(width = 110.dp, height = 70.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF26262C),
                                            border = BorderStroke(1.dp, Color(0xFF3A3A42)),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable {
                                                    val bmp = FileUtils.loadBitmap(stampItem.filePath)
                                                    if (bmp != null) {
                                                        val overlay = SignOverlayPlacement(
                                                            pageIndex = activePageIndex,
                                                            bitmap = bmp,
                                                            type = "stamp",
                                                            x = 0.5f,
                                                            y = 0.5f,
                                                            widthRatio = 0.30f
                                                        )
                                                        placedOverlays.add(overlay)
                                                        selectedOverlayId = overlay.id
                                                        showBottomPanel = false
                                                    }
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = File(stampItem.filePath),
                                                    contentDescription = stampItem.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        // Delete mini-button
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xCCEF4444),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .clickable {
                                                    itemToDelete = Pair(stampItem.id, "stamp")
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete Stamp",
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SAVING PROGRESS OVERLAY
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SignCardBg,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = SignTeal,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Text(
                            text = "Saving signed document...",
                            color = SignTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // MODAL BOTTOM SHEET: Add Signature (3 Options: Create, Scan, Import)
    if (showAddSignatureOptions) {
        AddSignatureBottomSheet(
            onDismiss = { showAddSignatureOptions = false },
            onCreateSignature = {
                showAddSignatureOptions = false
                showDrawSignatureDialog = true
            },
            onScanSignature = {
                showAddSignatureOptions = false
                launchSignatureCamera()
            },
            onImportFromGallery = {
                showAddSignatureOptions = false
                gallerySignatureLauncher.launch("image/*")
            }
        )
    }

    pendingImportedSignature?.let { preparedSignature ->
        ImportedSignaturePreviewDialog(
            signatureBitmap = preparedSignature,
            onDismiss = {
                pendingImportedSignature = null
                showAddSignatureOptions = true
            },
            onUseSignature = {
                pendingImportedSignature = null
                isProcessingItem = true
                processingMessage = "Saving signature..."
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val saved = SignatureManager.savePreparedSignature(context, preparedSignature)
                        val loadedBmp = FileUtils.loadBitmap(saved.filePath) ?: preparedSignature
                        withContext(Dispatchers.Main) {
                            refreshSavedItems()
                            val newOverlay = SignOverlayPlacement(
                                pageIndex = activePageIndex,
                                bitmap = loadedBmp,
                                type = "signature",
                                x = 0.5f,
                                y = 0.65f,
                                widthRatio = 0.32f
                            )
                            placedOverlays.add(newOverlay)
                            selectedOverlayId = newOverlay.id
                            showBottomPanel = false
                            Toast.makeText(context, "Signature added", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not save signature: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isProcessingItem = false }
                    }
                }
            }
        )
    }

    // DIALOG: Draw Signature Canvas
    if (showDrawSignatureDialog) {
        SignatureDrawingDialog(
            onDismiss = { showDrawSignatureDialog = false },
            onSignatureDrawn = { drawnBitmap ->
                showDrawSignatureDialog = false
                // Immediately place drawn signature directly on page
                val overlay = SignOverlayPlacement(
                    pageIndex = activePageIndex,
                    bitmap = drawnBitmap,
                    type = "signature",
                    x = 0.5f,
                    y = 0.65f,
                    widthRatio = 0.35f
                )
                placedOverlays.add(overlay)
                selectedOverlayId = overlay.id
                showBottomPanel = false

                // Asynchronously save to persistent library
                coroutineScope.launch(Dispatchers.IO) {
                    SignatureManager.saveDrawnSignature(context, drawnBitmap)
                    withContext(Dispatchers.Main) {
                        refreshSavedItems()
                    }
                }
            }
        )
    }

    // DIALOG: Quick Date Stamp Creator
    if (showDateStampDialog) {
        var dateText by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
        var selectedColor by remember { mutableStateOf(Color(0xFF00BFA5)) }

        Dialog(onDismissRequest = { showDateStampDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SignSheetBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Insert Date Stamp",
                        color = SignTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E24), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = selectedColor.copy(alpha = 0.12f),
                            border = BorderStroke(2.dp, selectedColor)
                        ) {
                            Text(
                                text = dateText.ifBlank { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) },
                                color = selectedColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date text", color = SignTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SignTextPrimary,
                            unfocusedTextColor = SignTextPrimary,
                            focusedBorderColor = SignTeal,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Color Picker for Date Stamp
                    StampCustomColorPicker(
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDateStampDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = SignTextSecondary)
                        }

                        Button(
                            onClick = {
                                showDateStampDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    val bmp = SignatureManager.createDateOnlyStampBitmap(
                                        dateString = dateText,
                                        primaryColor = android.graphics.Color.argb(
                                            (selectedColor.alpha * 255).toInt(),
                                            (selectedColor.red * 255).toInt(),
                                            (selectedColor.green * 255).toInt(),
                                            (selectedColor.blue * 255).toInt()
                                        )
                                    )
                                    val saved = SignatureManager.saveStamp(
                                        context = context,
                                        stampBitmap = bmp,
                                        name = "Date $dateText",
                                        text = dateText,
                                        colorHex = selectedColor.value.toLong()
                                    )
                                    withContext(Dispatchers.Main) {
                                        refreshSavedItems()
                                        val overlay = SignOverlayPlacement(
                                            pageIndex = activePageIndex,
                                            bitmap = bmp,
                                            type = "stamp",
                                            x = 0.5f,
                                            y = 0.5f,
                                            widthRatio = 0.30f
                                        )
                                        placedOverlays.add(overlay)
                                        selectedOverlayId = overlay.id
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SignTeal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Custom Official Stamp Creator (e.g. APPROVED, VERIFIED, PAID)
    if (showCustomStampDialog) {
        var stampTitle by remember { mutableStateOf("APPROVED") }
        var isCircular by remember { mutableStateOf(false) }
        var selectedColor by remember { mutableStateOf(Color(0xFF00BFA5)) }

        Dialog(onDismissRequest = { showCustomStampDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SignSheetBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Create Official Stamp",
                        color = SignTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Stamp Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E24), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCircular) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .border(3.dp, selectedColor, CircleShape)
                                    .padding(4.dp)
                                    .border(1.dp, selectedColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stampTitle.ifBlank { "APPROVED" }.uppercase(Locale.getDefault()),
                                        color = selectedColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                                        color = selectedColor,
                                        fontSize = 8.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = selectedColor.copy(alpha = 0.10f),
                                border = BorderStroke(2.5.dp, selectedColor)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, selectedColor, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stampTitle.ifBlank { "APPROVED" }.uppercase(Locale.getDefault()),
                                            color = selectedColor,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                                            color = selectedColor,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset options
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("APPROVED", "VERIFIED", "PAID", "RECEIVED", "CONFIDENTIAL")) { title ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (stampTitle.equals(title, ignoreCase = true)) SignTealContainer else Color(0xFF2C2C32),
                                border = BorderStroke(1.dp, if (stampTitle.equals(title, ignoreCase = true)) SignTeal else Color.Transparent),
                                modifier = Modifier.clickable { stampTitle = title }
                            ) {
                                Text(
                                    text = title,
                                    color = if (stampTitle.equals(title, ignoreCase = true)) SignTeal else SignTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = stampTitle,
                        onValueChange = { stampTitle = it },
                        label = { Text("Stamp Title", color = SignTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SignTextPrimary,
                            unfocusedTextColor = SignTextPrimary,
                            focusedBorderColor = SignTeal,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shape switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isCircular) SignTealContainer else Color(0xFF2C2C32),
                            border = BorderStroke(1.dp, if (!isCircular) SignTeal else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isCircular = false }
                        ) {
                            Text(
                                text = "Rectangle Badge",
                                color = if (!isCircular) SignTeal else SignTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCircular) SignTealContainer else Color(0xFF2C2C32),
                            border = BorderStroke(1.dp, if (isCircular) SignTeal else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isCircular = true }
                        ) {
                            Text(
                                text = "Circular Seal",
                                color = if (isCircular) SignTeal else SignTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Color Picker for Official Stamp
                    StampCustomColorPicker(
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCustomStampDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = SignTextSecondary)
                        }

                        Button(
                            onClick = {
                                showCustomStampDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                                    val bmp = SignatureManager.createOfficialStampBitmap(
                                        title = stampTitle.ifBlank { "APPROVED" },
                                        dateString = dateStr,
                                        primaryColor = android.graphics.Color.argb(
                                            (selectedColor.alpha * 255).toInt(),
                                            (selectedColor.red * 255).toInt(),
                                            (selectedColor.green * 255).toInt(),
                                            (selectedColor.blue * 255).toInt()
                                        ),
                                        isCircular = isCircular
                                    )
                                    val saved = SignatureManager.saveStamp(
                                        context = context,
                                        stampBitmap = bmp,
                                        name = stampTitle.ifBlank { "Stamp" },
                                        text = stampTitle,
                                        dateText = dateStr,
                                        colorHex = selectedColor.value.toLong()
                                    )
                                    withContext(Dispatchers.Main) {
                                        refreshSavedItems()
                                        val overlay = SignOverlayPlacement(
                                            pageIndex = activePageIndex,
                                            bitmap = bmp,
                                            type = "stamp",
                                            x = 0.5f,
                                            y = 0.5f,
                                            widthRatio = if (isCircular) 0.28f else 0.35f
                                        )
                                        placedOverlays.add(overlay)
                                        selectedOverlayId = overlay.id
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SignTeal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Confirm delete saved signature/stamp
    if (itemToDelete != null) {
        val (id, type) = itemToDelete!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = if (type == "signature") "Delete Signature" else "Delete Stamp",
                    fontWeight = FontWeight.Bold,
                    color = SignTextPrimary
                )
            },
            text = {
                Text(
                    text = if (type == "signature")
                        "Are you sure you want to delete this saved signature?"
                    else
                        "Are you sure you want to delete this saved stamp?",
                    color = SignTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (type == "signature") {
                            SignatureManager.deleteSignature(context, id)
                        } else {
                            SignatureManager.deleteStamp(context, id)
                        }
                        refreshSavedItems()
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = SignTextSecondary)
                }
            },
            containerColor = SignCardBg
        )
    }

    // PROCESSING / IMPORTING OVERLAY
    if (isProcessingItem) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SignCardBg,
                border = BorderStroke(1.dp, SignBorder),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = SignTeal,
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = processingMessage.ifBlank { "Processing..." },
                        color = SignTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelEmptyActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SignCardBg,
        border = BorderStroke(1.dp, SignBorder),
        modifier = modifier
            .height(72.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SignTeal,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = SignTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
