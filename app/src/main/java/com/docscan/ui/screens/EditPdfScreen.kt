package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.PageSize
import com.docscan.util.PdfExportConfig
import com.docscan.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val EditPdfBg: Color get() = ThemeManager.themeColor(Color(0xFF101114), Color(0xFFF8FAFC))
private val EditPdfCard: Color get() = ThemeManager.themeColor(Color(0xFF1A1C21), Color(0xFFFFFFFF))
private val EditPdfCard2: Color get() = ThemeManager.themeColor(Color(0xFF22252B), Color(0xFFF1F5F9))
private val EditPdfAccent = Color(0xFF20C9A6)
private val EditPdfBlue = Color(0xFF4AA3FF)
private val EditPdfText: Color get() = ThemeManager.themeColor(Color(0xFFF7F8FA), Color(0xFF0F172A))
private val EditPdfMuted: Color get() = ThemeManager.themeColor(Color(0xFFA8ADB7), Color(0xFF475569))

/**
 * Professional PDF workspace.
 *
 * The project intentionally avoids a heavyweight PDF object-editing dependency. PDFs are rendered
 * at high resolution into editable page images, then the existing OCR/inpainting page editor is
 * used for true visual text replacement (including scanned PDFs). The final PDF is flattened at
 * high quality. This keeps APK size small while allowing text/font/add/remove on both digital and
 * scanned documents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPdfScreen(
    viewModel: ScannerViewModel,
    allDocuments: List<DocumentEntity>,
    onBack: () -> Unit,
    onEditTextAndFont: (Long) -> Unit,
    onSign: (Long) -> Unit,
    onWatermark: (Long) -> Unit,
    onReorder: (Long) -> Unit,
    onRotate: (Long) -> Unit,
    onExtractPages: (Long) -> Unit,
    onAiChat: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeDoc by viewModel.activeDocument.collectAsState()
    val activePages by viewModel.activeDocumentPages.collectAsState()

    var selectedDocumentId by remember { mutableLongStateOf(0L) }
    var selectedPageId by remember { mutableLongStateOf(0L) }
    var isImporting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showDocumentPicker by remember { mutableStateOf(false) }
    // When a PDF/document is chosen from Edit PDF, open page 1 directly in the
    // visual text editor (matching the reference workflow) instead of stopping
    // at the intermediate workspace preview. This flag is consumed exactly once.
    var openEditorWhenReady by remember { mutableStateOf(false) }

    val selectedDocument = activeDoc?.takeIf { it.id == selectedDocumentId }
    val selectedPage = activePages.firstOrNull { it.id == selectedPageId }
        ?: activePages.firstOrNull()

    LaunchedEffect(selectedDocumentId) {
        if (selectedDocumentId > 0L) {
            viewModel.loadDocumentDetails(selectedDocumentId)
        }
    }

    LaunchedEffect(activePages, selectedPageId) {
        if (activePages.isNotEmpty() && activePages.none { it.id == selectedPageId }) {
            selectedPageId = activePages.first().id
        }
    }

    // Direct Edit-PDF flow: wait until the newly selected document and its pages
    // are actually loaded, then open the first page with Edit Text active. Checking
    // activeDoc.id prevents a stale page list from the previously opened document
    // from being launched by mistake.
    LaunchedEffect(activeDoc?.id, activePages, openEditorWhenReady, selectedDocumentId) {
        if (
            openEditorWhenReady &&
            selectedDocumentId > 0L &&
            activeDoc?.id == selectedDocumentId &&
            activePages.isNotEmpty()
        ) {
            val firstPage = activePages.minByOrNull { it.pageNumber } ?: activePages.first()
            selectedPageId = firstPage.id
            openEditorWhenReady = false
            onEditTextAndFont(selectedDocumentId)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    viewModel.importDocumentFromDevice(listOf(uri), "EDIT_PDF")
                }
                isImporting = false
                if (imported != null) {
                    selectedDocumentId = imported.id
                    selectedPageId = 0L
                    openEditorWhenReady = true
                    viewModel.loadDocumentDetails(imported.id)
                    Toast.makeText(context, "PDF loaded in Edit PDF", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditPdfBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditPdfTopBar(
            title = selectedDocument?.title ?: "Edit PDF",
            hasDocument = selectedDocument != null,
            isExporting = isExporting,
            onBack = onBack,
            onExport = {
                val doc = selectedDocument
                if (doc != null && activePages.isNotEmpty()) {
                    isExporting = true
                    scope.launch {
                    val generated = PdfExporter.generatePdf(
                        context = context,
                        documentTitle = "${doc.title}_edited",
                        pages = activePages,
                        config = PdfExportConfig(
                            title = "${doc.title}_edited",
                            pageSize = PageSize.AUTO,
                            includePageNumbers = false,
                            quality = 98
                        )
                    )
                    if (generated != null) {
                        val saved = PdfExporter.savePdfToDevice(
                            context,
                            generated,
                            "${doc.title}_edited.pdf"
                        )
                        Toast.makeText(
                            context,
                            if (saved != null) "Edited PDF saved" else "PDF created but could not save to Downloads",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Could not export PDF", Toast.LENGTH_LONG).show()
                    }
                        isExporting = false
                    }
                }
            }
        )

        if (selectedDocument == null) {
            EditPdfSourceChooser(
                documents = allDocuments,
                isImporting = isImporting,
                onChoosePdf = { filePicker.launch(arrayOf("application/pdf")) },
                onChooseDocument = {
                    selectedDocumentId = it.id
                    selectedPageId = 0L
                    openEditorWhenReady = true
                    viewModel.loadDocumentDetails(it.id)
                },
                onShowAll = { showDocumentPicker = true }
            )
        } else {
            EditPdfWorkspace(
                document = selectedDocument,
                pages = activePages,
                selectedPage = selectedPage,
                onSelectPage = { selectedPageId = it.id },
                onEditText = { onEditTextAndFont(selectedDocument.id) },
                onAddText = { onEditTextAndFont(selectedDocument.id) },
                onRemoveText = { onEditTextAndFont(selectedDocument.id) },
                onAiEdit = { onEditTextAndFont(selectedDocument.id) },
                onDuplicate = {
                    selectedPage?.let { page -> viewModel.duplicatePage(page) }
                },
                onDelete = {
                    selectedPage?.let { page ->
                        if (activePages.size <= 1) {
                            Toast.makeText(context, "A PDF must keep at least one page", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.deletePage(page)
                        }
                    }
                },
                onRotatePage = {
                    selectedPage?.let { page -> viewModel.rotatePageBy90(page) }
                },
                onSign = { onSign(selectedDocument.id) },
                onWatermark = { onWatermark(selectedDocument.id) },
                onReorder = { onReorder(selectedDocument.id) },
                onRotateAll = { onRotate(selectedDocument.id) },
                onExtractPages = { onExtractPages(selectedDocument.id) },
                onAiChat = { onAiChat(selectedDocument.id) },
                onChangeDocument = { showDocumentPicker = true }
            )
        }
    }

    if (showDocumentPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDocumentPicker = false },
            sheetState = sheetState,
            containerColor = EditPdfCard,
            contentColor = EditPdfText
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text("Choose a document", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Open an existing scanned/imported document in Edit PDF", color = EditPdfMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))

                if (allDocuments.isEmpty()) {
                    Text("No saved documents yet", color = EditPdfMuted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(modifier = Modifier.height(420.dp)) {
                        items(allDocuments, key = { it.id }) { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedDocumentId = doc.id
                                        selectedPageId = 0L
                                        openEditorWhenReady = true
                                        viewModel.loadDocumentDetails(doc.id)
                                        showDocumentPicker = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = File(doc.thumbnailPath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EditPdfCard2),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text("${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"}", color = EditPdfMuted, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.Edit, contentDescription = null, tint = EditPdfAccent)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        showDocumentPicker = false
                        filePicker.launch(arrayOf("application/pdf"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EditPdfAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open PDF from device", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun EditPdfTopBar(
    title: String,
    hasDocument: Boolean,
    isExporting: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF17181C))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EditPdfText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = EditPdfText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hasDocument) {
                Text("Professional PDF editor", color = EditPdfMuted, fontSize = 10.sp)
            }
        }
        if (hasDocument) {
            Button(
                onClick = onExport,
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = EditPdfAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Save PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EditPdfSourceChooser(
    documents: List<DocumentEntity>,
    isImporting: Boolean,
    onChoosePdf: () -> Unit,
    onChooseDocument: (DocumentEntity) -> Unit,
    onShowAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Surface(
                color = EditPdfCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF2C3037)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = EditPdfAccent.copy(alpha = 0.14f), shape = CircleShape) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = EditPdfAccent, modifier = Modifier.padding(12.dp).size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Edit any PDF", color = EditPdfText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Text • font • add/remove • AI • pages • sign", color = EditPdfMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Works with scanned and normal PDFs. Text is detected with OCR, the old pixels are professionally inpainted, then the edited text is rendered back in place.",
                        color = Color(0xFFD5D8DE),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onChoosePdf,
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EditPdfAccent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Preparing pages…", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("Open PDF from device", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent documents", color = EditPdfText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (documents.size > 4) {
                    TextButton(onClick = onShowAll) { Text("See all", color = EditPdfAccent) }
                }
            }
        }

        if (documents.isEmpty()) {
            item {
                Surface(color = EditPdfCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("No saved documents. Open a PDF from your device to start.", color = EditPdfMuted, modifier = Modifier.padding(18.dp), textAlign = TextAlign.Center)
                }
            }
        } else {
            items(documents.take(4), key = { it.id }) { doc ->
                Surface(
                    color = EditPdfCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF292D34)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseDocument(doc) }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = File(doc.thumbnailPath),
                            contentDescription = null,
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(9.dp)).background(Color.White),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, color = EditPdfText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} • Tap to edit", color = EditPdfMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Edit, contentDescription = null, tint = EditPdfAccent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun EditPdfWorkspace(
    document: DocumentEntity,
    pages: List<PageEntity>,
    selectedPage: PageEntity?,
    onSelectPage: (PageEntity) -> Unit,
    onEditText: () -> Unit,
    onAddText: () -> Unit,
    onRemoveText: () -> Unit,
    onAiEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRotatePage: () -> Unit,
    onSign: () -> Unit,
    onWatermark: () -> Unit,
    onReorder: () -> Unit,
    onRotateAll: () -> Unit,
    onExtractPages: () -> Unit,
    onAiChat: () -> Unit,
    onChangeDocument: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (selectedPage == null) {
                Box(modifier = Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EditPdfAccent)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF08090B)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(selectedPage.processedImagePath),
                        contentDescription = "PDF page",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )

                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).clickable(onClick = onEditText)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = EditPdfAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Tap text to edit • OCR + inpaint", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            if (pages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        val selected = page.id == selectedPage?.id
                        Surface(
                            color = if (selected) EditPdfAccent.copy(alpha = 0.14f) else EditPdfCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EditPdfAccent else Color(0xFF30343B)),
                            modifier = Modifier.width(72.dp).clickable { onSelectPage(page) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(5.dp)) {
                                AsyncImage(
                                    model = File(page.processedImagePath),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("${page.pageNumber}", color = if (selected) EditPdfAccent else EditPdfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(14.dp)) }

        item {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TEXT & CONTENT", color = EditPdfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Edit visible text without rebuilding the whole page", color = EditPdfText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onChangeDocument) { Text("Change PDF", color = EditPdfBlue, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(8.dp))
                ToolActionGrid(
                    listOf(
                        PdfEditorAction("Edit Text & Font", "OCR detects text; edit content, font, size, color & alignment", Icons.Default.FormatColorText, EditPdfAccent, onEditText),
                        PdfEditorAction("Add Text", "Insert movable text anywhere on the page", Icons.Default.Add, Color(0xFF60A5FA), onAddText),
                        PdfEditorAction("Remove Text", "Erase selected text with background-aware inpainting", Icons.Default.Delete, Color(0xFFF87171), onRemoveText),
                        PdfEditorAction("AI Text Edit", "Gemini-assisted typo fix, rewrite, translate & formalize", Icons.Default.AutoAwesome, Color(0xFFA78BFA), onAiEdit)
                    )
                )
            }
        }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text("INSERT & REVIEW", color = EditPdfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToolActionGrid(
                    listOf(
                        PdfEditorAction("Signature", "Place, resize and move signatures", Icons.Default.Draw, Color(0xFF34D399), onSign),
                        PdfEditorAction("Watermark", "Text/image watermark with opacity and page selection", Icons.Default.BrandingWatermark, Color(0xFF38BDF8), onWatermark),
                        PdfEditorAction("AI Document Chat", "Ask Gemini about this PDF while editing", Icons.Default.SmartToy, Color(0xFF22D3EE), onAiChat),
                        PdfEditorAction("Duplicate Page", "Create a copy of the selected page", Icons.Default.ContentCopy, Color(0xFFFBBF24), onDuplicate)
                    )
                )
            }
        }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Text("PAGE TOOLS", color = EditPdfMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToolActionGrid(
                    listOf(
                        PdfEditorAction("Reorder Pages", "Hold & drag pages into the correct order", Icons.Default.Reorder, Color(0xFF818CF8), onReorder),
                        PdfEditorAction("Rotate Page", "Rotate only the selected page by 90°", Icons.Default.RotateRight, Color(0xFFFB7185), onRotatePage),
                        PdfEditorAction("Rotate Multiple", "Choose and rotate several pages together", Icons.Default.RotateRight, Color(0xFFF97316), onRotateAll),
                        PdfEditorAction("Extract Pages", "Create a new PDF from selected pages", Icons.Default.Description, Color(0xFF2DD4BF), onExtractPages)
                    )
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFF2A1618),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF5A252A)),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B73))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Delete selected page", color = Color(0xFFFF9CA2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("The remaining pages stay in the PDF", color = Color(0xFFC88F93), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = Color(0xFF111D1A),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EditPdfAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EditPdfAccent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("High-quality flattened export", color = EditPdfText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "Edits are rendered into the page so they display consistently in every PDF viewer. Original source files remain untouched.",
                            color = EditPdfMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(22.dp)) }
    }
}

private data class PdfEditorAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun ToolActionGrid(actions: List<PdfEditorAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    Surface(
                        color = EditPdfCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A2E35)),
                        modifier = Modifier.weight(1f).clickable(onClick = action.onClick)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Surface(color = action.color.copy(alpha = 0.13f), shape = RoundedCornerShape(9.dp)) {
                                Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.padding(7.dp).size(19.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(action.title, color = EditPdfText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(action.subtitle, color = EditPdfMuted, fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
