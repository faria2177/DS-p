package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.AiOrchestrator
import com.docscan.util.ExcelExporter
import com.docscan.util.FileUtils
import com.docscan.util.PdfExporter
import com.docscan.util.PdfTableExtractor
import com.docscan.util.TableGeometryDetector
import com.docscan.util.TextRecognizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

// Theme Styling Constants matching reference screenshots
private val ExcelDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF141618), Color(0xFFF8FAFC))
private val ExcelCardBg: Color get() = ThemeManager.themeColor(Color(0xFF1E2126), Color(0xFFFFFFFF))
private val ExcelCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2E343D), Color(0xFFE2E8F0))
private val ExcelPrimaryGreen = Color(0xFF00C853)
private val ExcelGreenAccent = Color(0xFF10B981)
private val ExcelBlueAccent = Color(0xFF2563EB)
private val ExcelTealAccent = Color(0xFF00BFA5)
private val ExcelTextWhite: Color get() = ThemeManager.themeColor(Color(0xFFFFFFFF), Color(0xFF0F172A))
private val ExcelTextMuted: Color get() = ThemeManager.themeColor(Color(0xFF9E9E9E), Color(0xFF475569))
private val ExcelTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF757575), Color(0xFF64748B))

enum class ToExcelStage {
    LANDING,        // Reference landing screen (To Excel ✨, Excel card, Scan/Gallery/Device, Select from This App)
    CONVERTING,     // Scanning with DS AI circular animated radar & laser sweep
    SPREADSHEET,    // Interactive editable spreadsheet matrix (Rows, Columns, Cells)
    EXPORT_SUCCESS  // Export summary, Open in Excel, Share, Save to App
}

data class ExcelPageItem(
    val pageIndex: Int,
    val bitmap: Bitmap,
    var rawText: String? = null,
    var sourceImagePath: String? = null
)

data class EditableCell(
    var value: String,
    val isHeader: Boolean = false,
    val isNumeric: Boolean = false,
    val isMerged: Boolean = false
)

data class EditableTable(
    var sheetTitle: String = "Sheet 1",
    val headers: MutableList<String> = mutableListOf(),
    val rows: MutableList<MutableList<String>> = mutableListOf(),
    var documentTitle: String = "",
    val sourcePage: Int = 1
)

/**
 * Dedicated "To Excel ✨" Screen matching the reference screenshots exactly.
 * Supports Scan, Gallery, Device Import, App Document Selection,
 * DS AI Table OCR with scanning radar, interactive editable spreadsheet matrix,
 * and XLSX & PDF exports.
 */
@Composable
fun PdfToExcelScreen(
    viewModel: ScannerViewModel,
    initialDocument: DocumentEntity? = null,
    allDocuments: List<DocumentEntity> = emptyList(),
    onNavigateToScan: () -> Unit = {},
    onNavigateToDocumentDetail: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Active screen stage
    var currentStage by remember {
        mutableStateOf(if (initialDocument != null) ToExcelStage.CONVERTING else ToExcelStage.LANDING)
    }

    val pageItems = remember { mutableStateListOf<ExcelPageItem>() }
    var selectedDocTitle by remember { mutableStateOf(initialDocument?.title ?: "pay slip") }
    var originalDocEntity by remember { mutableStateOf(initialDocument) }

    // Progress State for Converting Screen
    var currentStepText by remember { mutableStateOf("Preparing document...") }
    var progressPercent by remember { mutableIntStateOf(10) }
    var isConversionFinished by remember { mutableStateOf(false) }

    // Extracted Tables
    val extractedTables = remember { mutableStateListOf<EditableTable>() }
    var activeSheetIndex by remember { mutableIntStateOf(0) }

    // Cell Editing Dialog State
    var editingCellCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (row, col)
    var editingCellValue by remember { mutableStateOf("") }
    var editingHeaderCoords by remember { mutableStateOf<Int?>(null) }
    var editingHeaderValue by remember { mutableStateOf("") }

    // Raw CSV / Text Editor Dialog
    var showRawTextEditorDialog by remember { mutableStateOf(false) }
    var rawCsvEditorText by remember { mutableStateOf("") }

    // Export Result State
    var generatedXlsxFile by remember { mutableStateOf<File?>(null) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var isSavingToLibrary by remember { mutableStateOf(false) }
    var isSavedToLibrary by remember { mutableStateOf(false) }

    // Landing Screen Search
    var landingSearchQuery by remember { mutableStateOf("") }

    // ----------------------------------------------------
    // ACTIVITY RESULT LAUNCHERS (Gallery & Device)
    // ----------------------------------------------------
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val loaded = mutableListOf<ExcelPageItem>()
                uris.forEachIndexed { idx, uri ->
                    val bmps = FileUtils.loadBitmapsFromUri(context, uri)
                    bmps.forEachIndexed { bIdx, bmp ->
                        val tempPath = FileUtils.saveBitmapToTemp(context, bmp, "EXCEL_GALLERY_${idx}_${bIdx}")
                        loaded.add(
                            ExcelPageItem(
                                pageIndex = loaded.size + 1,
                                bitmap = bmp,
                                sourceImagePath = tempPath
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    if (loaded.isNotEmpty()) {
                        pageItems.clear()
                        pageItems.addAll(loaded)
                        selectedDocTitle = "Scanned_Table_${System.currentTimeMillis() % 10000}"
                        currentStage = ToExcelStage.CONVERTING
                    } else {
                        Toast.makeText(context, "Could not load images", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val deviceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val loaded = mutableListOf<ExcelPageItem>()
                uris.forEachIndexed { idx, uri ->
                    val bmps = FileUtils.loadBitmapsFromUri(context, uri)
                    bmps.forEachIndexed { bIdx, bmp ->
                        val tempPath = FileUtils.saveBitmapToTemp(context, bmp, "EXCEL_DEVICE_${idx}_${bIdx}")
                        loaded.add(
                            ExcelPageItem(
                                pageIndex = loaded.size + 1,
                                bitmap = bmp,
                                sourceImagePath = tempPath
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    if (loaded.isNotEmpty()) {
                        pageItems.clear()
                        pageItems.addAll(loaded)
                        selectedDocTitle = "Document_${System.currentTimeMillis() % 10000}"
                        currentStage = ToExcelStage.CONVERTING
                    } else {
                        Toast.makeText(context, "Could not load document files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Function to execute the complete OCR + Table Extraction Pipeline
    val runTableExtractionPipeline: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            try {
                isConversionFinished = false
                withContext(Dispatchers.Main) {
                    currentStepText = "Preparing document..."
                    progressPercent = 15
                }
                delay(250)

                withContext(Dispatchers.Main) {
                    currentStepText = "Detecting real table boundaries..."
                    progressPercent = 30
                }

                val generatedTables = mutableListOf<EditableTable>()

                for ((idx, item) in pageItems.withIndex()) {
                    val pageNumber = idx + 1
                    withContext(Dispatchers.Main) {
                        currentStepText = "Gemini: extracting & verifying rows, columns and Bengali text ($pageNumber/${pageItems.size})..."
                        progressPercent = 35 + ((idx + 1) * 50 / pageItems.size.coerceAtLeast(1))
                    }

                    // Best path: a vision model returns semantic JSON with the page heading and every real table.
                    val structuredAi = try {
                        AiOrchestrator.convertToExcelAi(
                            bitmap = item.bitmap,
                            rawText = item.rawText,
                            context = context,
                            structuredLayout = true
                        )
                    } catch (_: Exception) {
                        ""
                    }

                    val aiTables = parseStructuredExcelJson(structuredAi, pageNumber)
                    if (aiTables.isNotEmpty()) {
                        generatedTables.addAll(aiTables)
                        continue
                    }

                    // Offline fallback: OpenCV finds actual grid lines; OCR boxes are assigned to the correct cells.
                    val geometryDoc = try {
                        TableGeometryDetector.extractStructuredDocument(item.bitmap)
                    } catch (_: Exception) {
                        null
                    }

                    if (geometryDoc != null && geometryDoc.tables.isNotEmpty()) {
                        geometryDoc.tables.forEachIndexed { tableIndex, table ->
                            val maxCols = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
                            val headers = table.headers.toMutableList()
                            while (headers.size < maxCols) headers.add("Column ${headers.size + 1}")
                            val rows = table.rows.map { row ->
                                row.toMutableList().apply { while (size < maxCols) add("") }
                            }.toMutableList()

                            generatedTables.add(
                                EditableTable(
                                    sheetTitle = table.title.ifBlank {
                                        if (pageItems.size > 1) "Page $pageNumber • Table ${tableIndex + 1}" else "Table ${tableIndex + 1}"
                                    },
                                    headers = headers,
                                    rows = rows,
                                    documentTitle = geometryDoc.documentTitle,
                                    sourcePage = pageNumber
                                )
                            )
                        }
                        continue
                    }

                    // Last fallback: accept CSV only when it is a genuine multi-column result.
                    val csvResult = try {
                        AiOrchestrator.convertToExcelAi(
                            bitmap = item.bitmap,
                            rawText = item.rawText,
                            context = context,
                            structuredLayout = false
                        )
                    } catch (_: Exception) {
                        ""
                    }

                    val csvLines = csvResult.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (csvLines.isNotEmpty()) {
                        val parsed = csvLines.map { parseCsvRow(it) }
                        val headers = (parsed.firstOrNull() ?: emptyList()).toMutableList()
                        val rows = (if (parsed.size > 1) parsed.drop(1) else emptyList())
                            .map { it.toMutableList() }.toMutableList()
                        val maxCols = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

                        val genericHeader = headers.joinToString(" ").lowercase()
                        val fakeLineList = maxCols <= 3 && (
                            genericHeader.contains("index") ||
                            genericHeader.contains("line") ||
                            genericHeader.contains("item / field") ||
                            genericHeader.contains("details / value")
                        )

                        if (maxCols >= 2 && !fakeLineList) {
                            while (headers.size < maxCols) headers.add("Column ${headers.size + 1}")
                            rows.forEach { while (it.size < maxCols) it.add("") }
                            generatedTables.add(
                                EditableTable(
                                    sheetTitle = if (pageItems.size > 1) "Page $pageNumber" else "Table 1",
                                    headers = headers,
                                    rows = rows,
                                    sourcePage = pageNumber
                                )
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    currentStepText = "Validating extracted table structure..."
                    progressPercent = 92
                }
                delay(250)

                if (generatedTables.isEmpty()) {
                    generatedTables.add(
                        EditableTable(
                            sheetTitle = "No table detected",
                            headers = mutableListOf("Status"),
                            rows = mutableListOf(
                                mutableListOf("No reliable table was detected. Try a clearer scan or enable a Vision AI provider for Bengali/multilingual tables.")
                            ),
                            sourcePage = 1
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    extractedTables.clear()
                    extractedTables.addAll(generatedTables)
                    activeSheetIndex = 0
                    currentStepText = "Conversion complete!"
                    progressPercent = 100
                    isConversionFinished = true
                    delay(200)
                    currentStage = ToExcelStage.SPREADSHEET
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Table extraction error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    currentStepText = "Table extraction could not be completed"
                    isConversionFinished = true
                    currentStage = ToExcelStage.SPREADSHEET
                }
            }
        }
    }

    // Load initial document if provided
    LaunchedEffect(initialDocument) {
        if (initialDocument != null) {
            originalDocEntity = initialDocument
            selectedDocTitle = initialDocument.title
            scope.launch(Dispatchers.IO) {
                val pages = viewModel.getPagesForDocumentDirect(initialDocument.id)
                val loaded = mutableListOf<ExcelPageItem>()
                pages.forEachIndexed { index, pageEntity ->
                    val bmp = FileUtils.loadBitmap(pageEntity.processedImagePath)
                        ?: FileUtils.loadBitmap(pageEntity.originalImagePath)
                    if (bmp != null) {
                        loaded.add(
                            ExcelPageItem(
                                pageIndex = index + 1,
                                bitmap = bmp,
                                rawText = pageEntity.extractedText,
                                sourceImagePath = pageEntity.processedImagePath
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    pageItems.clear()
                    pageItems.addAll(loaded)
                    if (loaded.isNotEmpty()) {
                        currentStage = ToExcelStage.CONVERTING
                        runTableExtractionPipeline()
                    } else {
                        currentStage = ToExcelStage.LANDING
                    }
                }
            }
        }
    }

    // When transitioning into CONVERTING stage, run the extraction pipeline automatically
    LaunchedEffect(currentStage) {
        if (currentStage == ToExcelStage.CONVERTING && !isConversionFinished && pageItems.isNotEmpty()) {
            runTableExtractionPipeline()
        }
    }

    // Export to XLSX Action
    val executeExportXlsx: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val pageGroups = extractedTables.groupBy { it.sourcePage }.toSortedMap()
            val structuredSheets = pageGroups.map { (page, tablesOnPage) ->
                val pageTitle = tablesOnPage.firstOrNull { it.documentTitle.isNotBlank() }?.documentTitle.orEmpty()
                val sheetName = if (pageGroups.size == 1) {
                    selectedDocTitle.ifBlank { "Table 1" }.take(31)
                } else {
                    "Page $page"
                }

                ExcelExporter.StructuredSheetData(
                    sheetName = sheetName,
                    documentTitle = pageTitle,
                    sections = tablesOnPage.map { table ->
                        ExcelExporter.StructuredTableSection(
                            title = table.sheetTitle.takeUnless {
                                it.startsWith("Table ") && tablesOnPage.size == 1
                            }.orEmpty(),
                            headers = table.headers.toList(),
                            rows = table.rows.map { it.toList() }
                        )
                    }
                )
            }

            val file = ExcelExporter.generateStructuredXlsx(
                context = context,
                documentTitle = selectedDocTitle.ifBlank { "Excel_Sheet" },
                sheets = structuredSheets
            )

            withContext(Dispatchers.Main) {
                if (file != null && file.exists()) {
                    generatedXlsxFile = file
                    currentStage = ToExcelStage.EXPORT_SUCCESS
                    Toast.makeText(context, "Layout-preserving spreadsheet exported: ${file.name}", Toast.LENGTH_SHORT).show()
                    viewModel.attachExcelExport(originalDocEntity?.id, file.absolutePath)
                } else {
                    Toast.makeText(context, "Failed to generate Excel file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Save as PDF Action
    val executeSaveAsPdf: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val sb = java.lang.StringBuilder()
            sb.append("# ${selectedDocTitle}\n\n")
            extractedTables.forEach { table ->
                sb.append("## ${table.sheetTitle}\n\n")
                sb.append("| ").append(table.headers.joinToString(" | ")).append(" |\n")
                sb.append("| ").append(table.headers.map { "---" }.joinToString(" | ")).append(" |\n")
                table.rows.forEach { row ->
                    sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                }
                sb.append("\n\n")
            }

            val pdfFile = PdfExporter.generatePdfFromText(
                context = context,
                title = selectedDocTitle.ifBlank { "Excel_Table_Export" },
                textContent = sb.toString()
            )

            withContext(Dispatchers.Main) {
                if (pdfFile != null && pdfFile.exists()) {
                    generatedPdfFile = pdfFile
                    Toast.makeText(context, "PDF saved successfully: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                    PdfExporter.sharePdf(context, pdfFile)
                } else {
                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ----------------------------------------------------
    // MAIN UI CONTAINER
    // ----------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExcelDarkBg)
    ) {
        when (currentStage) {
            // ====================================================
            // 1. LANDING STAGE (Reference Screenshot 1)
            // ====================================================
            ToExcelStage.LANDING -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Top App Bar & Decorative Artwork
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = ExcelTextWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Top-Right CamScanner style Stacked Cards Artwork ("1 To Word", "2 To Excel", "3 To PDF", "4 To PPT")
                        ExcelArtworkBadge()
                    }

                    // Title & Subtitle
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "To Excel",
                                color = ExcelTextWhite,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✨",
                                fontSize = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Convert images and PDFs into editable Excel spreadsheets.",
                            color = ExcelTextMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // SECTION: Create or Import
                    Text(
                        text = "Create or Import",
                        color = ExcelTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    // 3 Action Cards (Scan, Gallery, Device) matching Screenshot 1 layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Scan Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ExcelCardBg,
                            border = BorderStroke(1.dp, ExcelCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable {
                                    onNavigateToScan()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ExcelTealAccent,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Scan",
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Scan",
                                    color = ExcelTextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 2. Gallery Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ExcelCardBg,
                            border = BorderStroke(1.dp, ExcelCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable {
                                    galleryLauncher.launch("image/*")
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ExcelBlueAccent,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PhotoLibrary,
                                            contentDescription = "Gallery",
                                            tint = ExcelTextWhite,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Gallery",
                                    color = ExcelTextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 3. Device Card (Row below)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ExcelCardBg,
                        border = BorderStroke(1.dp, ExcelCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .height(56.dp)
                            .clickable {
                                deviceFileLauncher.launch(arrayOf("application/pdf", "image/*", "*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ExcelBlueAccent,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = "Device",
                                        tint = ExcelTextWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Device",
                                color = ExcelTextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION: Select from This App
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select from This App",
                            color = ExcelTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${allDocuments.size} docs",
                            color = ExcelTextMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Search Filter
                    if (allDocuments.size > 4) {
                        Surface(
                            color = ExcelCardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ExcelCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = ExcelTextMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (landingSearchQuery.isEmpty()) {
                                        Text("Search documents...", color = ExcelTextMuted, fontSize = 13.sp)
                                    }
                                    BasicTextField(
                                        value = landingSearchQuery,
                                        onValueChange = { landingSearchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = ExcelTextWhite, fontSize = 13.sp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (landingSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { landingSearchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = ExcelTextMuted, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Document List
                    val filteredDocs = allDocuments.filter {
                        landingSearchQuery.isBlank() || it.title.contains(landingSearchQuery, ignoreCase = true)
                    }

                    if (filteredDocs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = ExcelTextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No documents found in app", color = ExcelTextMuted, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap 'Scan' or 'Gallery' above to create one", color = ExcelTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredDocs, key = { it.id }) { doc ->
                                DocumentExcelItemRow(
                                    doc = doc,
                                    onClick = {
                                        originalDocEntity = doc
                                        selectedDocTitle = doc.title
                                        scope.launch(Dispatchers.IO) {
                                            val pages = viewModel.getPagesForDocumentDirect(doc.id)
                                            val loaded = mutableListOf<ExcelPageItem>()
                                            pages.forEachIndexed { index, pageEntity ->
                                                val bmp = FileUtils.loadBitmap(pageEntity.processedImagePath)
                                                    ?: FileUtils.loadBitmap(pageEntity.originalImagePath)
                                                if (bmp != null) {
                                                    loaded.add(
                                                        ExcelPageItem(
                                                            pageIndex = index + 1,
                                                            bitmap = bmp,
                                                            rawText = pageEntity.extractedText,
                                                            sourceImagePath = pageEntity.processedImagePath
                                                        )
                                                    )
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                if (loaded.isNotEmpty()) {
                                                    pageItems.clear()
                                                    pageItems.addAll(loaded)
                                                    currentStage = ToExcelStage.CONVERTING
                                                    runTableExtractionPipeline()
                                                } else {
                                                    Toast.makeText(context, "No readable pages found in document", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ====================================================
            // 2. CONVERTING STAGE (Reference Screenshot 2 & 3)
            // ====================================================
            ToExcelStage.CONVERTING -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Top App Bar matching Screenshot 2: "Converting to Excel pay slip" + Tags+
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentStage = ToExcelStage.LANDING }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ExcelTextWhite)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Converting to Excel ${selectedDocTitle.take(18)}",
                            color = ExcelTextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Tags+ Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2C323D),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                "Tags+",
                                color = ExcelTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = ExcelTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Main Center Document Preview with Circular Radar Scanning Effect
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val activeBitmap = pageItems.firstOrNull()?.bitmap

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .fillMaxHeight(0.92f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (activeBitmap != null) {
                                    Image(
                                        bitmap = activeBitmap.asImageBitmap(),
                                        contentDescription = "Document Scan Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFF0F4F8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Sonali Bank PLC\nSalary Slip", color = Color.Gray, textAlign = TextAlign.Center)
                                    }
                                }

                                // Animated Laser Scan Bar moving up and down across the document
                                LaserScanBar()

                                // Semi-transparent overlay while converting
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f))
                                )

                                // Center Glowing Animated Radar/Scanning Circle matching Screenshot 2 & 3
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    RadarScanningCircle()

                                    Spacer(modifier = Modifier.height(18.dp))

                                    // "Converting with DS AI" Pill
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.Black.copy(alpha = 0.75f),
                                        border = BorderStroke(1.dp, Color(0xFF00BFA5).copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ExcelTealAccent,
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("DS", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Converting with DS AI",
                                                color = ExcelTextWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = currentStepText,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Bar matching Screenshot 2 & 3:
                    // [Edit Text]  [Save as PDF]  [Export Document (Green Button)]
                    BottomConvertingBar(
                        isReady = isConversionFinished,
                        onEditText = {
                            if (extractedTables.isNotEmpty()) {
                                val t = extractedTables[activeSheetIndex]
                                rawCsvEditorText = t.headers.joinToString(",") + "\n" + t.rows.joinToString("\n") { it.joinToString(",") }
                                showRawTextEditorDialog = true
                            }
                        },
                        onSavePdf = executeSaveAsPdf,
                        onExportDocument = {
                            if (extractedTables.isNotEmpty()) {
                                currentStage = ToExcelStage.SPREADSHEET
                            } else {
                                executeExportXlsx()
                            }
                        }
                    )
                }
            }

            // ====================================================
            // 3. SPREADSHEET STAGE (Interactive Editable Matrix)
            // ====================================================
            ToExcelStage.SPREADSHEET -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Top App Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentStage = ToExcelStage.CONVERTING }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ExcelTextWhite)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDocTitle,
                                color = ExcelTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Editable Spreadsheet Matrix",
                                color = ExcelTealAccent,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(onClick = {
                            if (extractedTables.isNotEmpty()) {
                                val t = extractedTables[activeSheetIndex]
                                rawCsvEditorText = t.headers.joinToString(",") + "\n" + t.rows.joinToString("\n") { it.joinToString(",") }
                                showRawTextEditorDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Text", tint = ExcelTextWhite)
                        }

                        IconButton(onClick = executeSaveAsPdf) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Save PDF", tint = ExcelTextWhite)
                        }
                    }

                    // Sheet Tabs (if multi-table)
                    if (extractedTables.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = activeSheetIndex,
                            containerColor = ExcelCardBg,
                            contentColor = ExcelTealAccent,
                            edgePadding = 12.dp,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSheetIndex]),
                                    color = ExcelPrimaryGreen
                                )
                            }
                        ) {
                            extractedTables.forEachIndexed { idx, table ->
                                Tab(
                                    selected = activeSheetIndex == idx,
                                    onClick = { activeSheetIndex = idx },
                                    text = {
                                        Text(
                                            table.sheetTitle,
                                            color = if (activeSheetIndex == idx) ExcelTextWhite else ExcelTextMuted,
                                            fontWeight = if (activeSheetIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (extractedTables.isNotEmpty()) {
                        val activeTable = extractedTables[activeSheetIndex]
                        if (activeTable.documentTitle.isNotBlank() || activeTable.sheetTitle.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (activeTable.documentTitle.isNotBlank()) {
                                    Text(
                                        text = activeTable.documentTitle,
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (activeTable.sheetTitle.isNotBlank() && !activeTable.sheetTitle.startsWith("Table ")) {
                                    Text(
                                        text = activeTable.sheetTitle,
                                        color = Color(0xFF1F4E78),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Matrix Control Toolbar: (Add Row, Add Col, Clear)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ExcelCardBg)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap any cell to edit value",
                            color = ExcelTextMuted,
                            fontSize = 11.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (extractedTables.isNotEmpty()) {
                                        val activeTable = extractedTables[activeSheetIndex]
                                        val newRow = MutableList(activeTable.headers.size) { "" }
                                        activeTable.rows.add(newRow)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ExcelTealAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Row", color = ExcelTealAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            TextButton(
                                onClick = {
                                    if (extractedTables.isNotEmpty()) {
                                        val activeTable = extractedTables[activeSheetIndex]
                                        activeTable.headers.add("Col ${activeTable.headers.size + 1}")
                                        activeTable.rows.forEach { it.add("") }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ExcelTealAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Column", color = ExcelTealAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Interactive Editable Spreadsheet Grid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0F1113))
                    ) {
                        if (extractedTables.isNotEmpty()) {
                            val activeTable = extractedTables[activeSheetIndex]
                            InteractiveSpreadsheetGrid(
                                table = activeTable,
                                onHeaderClick = { colIdx ->
                                    editingHeaderCoords = colIdx
                                    editingHeaderValue = activeTable.headers.getOrElse(colIdx) { "" }
                                },
                                onCellClick = { rIdx, cIdx ->
                                    editingCellCoords = Pair(rIdx, cIdx)
                                    editingCellValue = activeTable.rows.getOrNull(rIdx)?.getOrNull(cIdx) ?: ""
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No table data found", color = ExcelTextMuted)
                            }
                        }
                    }

                    // Bottom Action Bar: [Edit Text]  [Save as PDF]  [Export Document]
                    BottomConvertingBar(
                        isReady = true,
                        onEditText = {
                            if (extractedTables.isNotEmpty()) {
                                val t = extractedTables[activeSheetIndex]
                                rawCsvEditorText = t.headers.joinToString(",") + "\n" + t.rows.joinToString("\n") { it.joinToString(",") }
                                showRawTextEditorDialog = true
                            }
                        },
                        onSavePdf = executeSaveAsPdf,
                        onExportDocument = executeExportXlsx
                    )
                }
            }

            // ====================================================
            // 4. EXPORT SUCCESS STAGE
            // ====================================================
            ToExcelStage.EXPORT_SUCCESS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ExcelPrimaryGreen.copy(alpha = 0.15f),
                        border = BorderStroke(2.dp, ExcelPrimaryGreen),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Success",
                                tint = ExcelPrimaryGreen,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Excel Spreadsheet Ready!",
                        color = ExcelTextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = generatedXlsxFile?.name ?: "${selectedDocTitle}.xlsx",
                        color = ExcelTealAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Real editable XLSX format with rows, columns, numbers, and formulas preserved.",
                        color = ExcelTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Action Buttons
                    // 1. Open in Excel / Office
                    Button(
                        onClick = {
                            generatedXlsxFile?.let { ExcelExporter.openXlsx(context, it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcelPrimaryGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Excel / Sheets", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Share XLSX File
                    OutlinedButton(
                        onClick = {
                            generatedXlsxFile?.let { ExcelExporter.shareXlsx(context, it) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ExcelTealAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = ExcelTealAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Spreadsheet (.xlsx)", color = ExcelTealAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Save as App Document in Room DB
                    if (!isSavedToLibrary) {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    isSavingToLibrary = true
                                    // Save thumbnail / page as App Document
                                    val bmps = pageItems.map { it.bitmap }
                                    viewModel.createDocumentFromBitmaps(
                                        title = selectedDocTitle.ifBlank { "Excel_Spreadsheet" },
                                        bitmaps = bmps
                                    )
                                    withContext(Dispatchers.Main) {
                                        isSavingToLibrary = false
                                        isSavedToLibrary = true
                                        Toast.makeText(context, "Saved to App Documents!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSavingToLibrary,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ExcelCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isSavingToLibrary) {
                                CircularProgressIndicator(color = ExcelTextWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = ExcelTextWhite)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save to App Library", color = ExcelTextWhite, fontSize = 15.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { currentStage = ToExcelStage.LANDING }) {
                        Text("Convert Another Document", color = ExcelTextMuted, fontSize = 14.sp)
                    }
                }
            }
        }

        // ----------------------------------------------------
        // CELL EDIT MODAL DIALOG
        // ----------------------------------------------------
        if (editingCellCoords != null) {
            val (rIdx, cIdx) = editingCellCoords!!
            Dialog(onDismissRequest = { editingCellCoords = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ExcelCardBg,
                    border = BorderStroke(1.dp, ExcelCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val activeTable = extractedTables.getOrNull(activeSheetIndex)
                        val headerName = activeTable?.headers?.getOrNull(cIdx) ?: "Col ${cIdx + 1}"

                        Text(
                            text = "Edit Cell [Row ${rIdx + 1}, $headerName]",
                            color = ExcelTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editingCellValue,
                            onValueChange = { editingCellValue = it },
                            label = { Text("Cell Value", color = ExcelTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ExcelTextWhite,
                                unfocusedTextColor = ExcelTextWhite,
                                focusedBorderColor = ExcelTealAccent,
                                unfocusedBorderColor = ExcelCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editingCellCoords = null }) {
                                Text("Cancel", color = ExcelTextMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    activeTable?.rows?.getOrNull(rIdx)?.let { rowList ->
                                        if (cIdx < rowList.size) {
                                            rowList[cIdx] = editingCellValue
                                        }
                                    }
                                    editingCellCoords = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ExcelPrimaryGreen)
                            ) {
                                Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // HEADER EDIT MODAL DIALOG
        // ----------------------------------------------------
        if (editingHeaderCoords != null) {
            val cIdx = editingHeaderCoords!!
            Dialog(onDismissRequest = { editingHeaderCoords = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ExcelCardBg,
                    border = BorderStroke(1.dp, ExcelCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Edit Column Header [Col ${cIdx + 1}]",
                            color = ExcelTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editingHeaderValue,
                            onValueChange = { editingHeaderValue = it },
                            label = { Text("Header Title", color = ExcelTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ExcelTextWhite,
                                unfocusedTextColor = ExcelTextWhite,
                                focusedBorderColor = ExcelTealAccent,
                                unfocusedBorderColor = ExcelCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { editingHeaderCoords = null }) {
                                Text("Cancel", color = ExcelTextMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val activeTable = extractedTables.getOrNull(activeSheetIndex)
                                    if (activeTable != null && cIdx < activeTable.headers.size) {
                                        activeTable.headers[cIdx] = editingHeaderValue
                                    }
                                    editingHeaderCoords = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ExcelPrimaryGreen)
                            ) {
                                Text("Save Header", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // RAW CSV / TEXT EDITOR MODAL DIALOG
        // ----------------------------------------------------
        if (showRawTextEditorDialog) {
            Dialog(onDismissRequest = { showRawTextEditorDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ExcelCardBg,
                    border = BorderStroke(1.dp, ExcelCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Raw Table Editor (CSV/Text)",
                                color = ExcelTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showRawTextEditorDialog = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ExcelTextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = rawCsvEditorText,
                            onValueChange = { rawCsvEditorText = it },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ExcelTextWhite,
                                unfocusedTextColor = ExcelTextWhite,
                                focusedBorderColor = ExcelTealAccent,
                                unfocusedBorderColor = ExcelCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showRawTextEditorDialog = false }) {
                                Text("Cancel", color = ExcelTextMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val lines = rawCsvEditorText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                    if (lines.isNotEmpty()) {
                                        val parsed = lines.map { parseCsvRow(it) }
                                        val headers = (parsed.firstOrNull() ?: listOf("Col 1")).toMutableList()
                                        val rows = (if (parsed.size > 1) parsed.subList(1, parsed.size) else emptyList())
                                            .map { it.toMutableList() }.toMutableList()

                                        if (extractedTables.isNotEmpty()) {
                                            extractedTables[activeSheetIndex] = EditableTable(
                                                sheetTitle = extractedTables[activeSheetIndex].sheetTitle,
                                                headers = headers,
                                                rows = rows
                                            )
                                        }
                                    }
                                    showRawTextEditorDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ExcelPrimaryGreen)
                            ) {
                                Text("Update Spreadsheet", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SUPPORTING COMPOSABLES
// ----------------------------------------------------

/**
 * Top-Right CamScanner style Stacked Cards Artwork matching Reference Screenshot 1
 */
@Composable
private fun ExcelArtworkBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E2126),
        border = BorderStroke(1.dp, Color(0xFF2E343D)),
        modifier = Modifier.padding(top = 4.dp, end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Green "X" Badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ExcelPrimaryGreen,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "X",
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Mini stacked list
            Column {
                Text("1  To Word", color = ExcelTextMuted, fontSize = 10.sp)
                Text("2  To Excel", color = ExcelPrimaryGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("3  To PDF", color = ExcelTextMuted, fontSize = 10.sp)
                Text("4  To PPT", color = ExcelTextMuted, fontSize = 10.sp)
            }
        }
    }
}

/**
 * Document List Item Row matching Screenshot 1
 */
@Composable
private fun DocumentExcelItemRow(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(doc.id) {
        withContext(Dispatchers.IO) {
            val bmp = if (!doc.thumbnailPath.isNullOrBlank()) {
                FileUtils.loadBitmap(doc.thumbnailPath)
            } else null
            withContext(Dispatchers.Main) {
                thumbBitmap = bmp
            }
        }
    }

    val formattedDate = remember(doc.updatedAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        sdf.format(Date(doc.updatedAt))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ExcelCardBg,
        border = BorderStroke(1.dp, ExcelCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with PDF tag
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C323D),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = ExcelTealAccent, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    color = ExcelTextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        color = ExcelTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = ExcelTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${doc.pageCount}",
                        color = ExcelTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Animated Circular Radar Scanning Indicator matching Reference Screenshot 2 & 3
 */
@Composable
private fun RadarScanningCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_transition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size((110 * pulseScale).dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 6.dp.toPx()

            // Outer cyan glowing ring
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                radius = radius + 4.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 4.dp.toPx())
            )

            // Inner dark circle background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00BFA5).copy(alpha = 0.4f), Color(0xFF141618).copy(alpha = 0.85f)),
                    center = centerOffset,
                    radius = radius
                ),
                radius = radius,
                center = centerOffset
            )

            // Rotating sweep gradient radar beam
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.8f)),
                    center = centerOffset
                ),
                startAngle = rotationAngle,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Glowing border ring
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = radius,
                center = centerOffset,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner glowing AI Icon
        Surface(
            shape = CircleShape,
            color = Color(0xFF141618).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFF00E5FF)),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Animated Laser Scan Bar moving up and down across the document preview
 */
@Composable
private fun LaserScanBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserOffsetRatio by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_offset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.04f)
                .align(Alignment.TopCenter)
                .offset(y = (laserOffsetRatio * 320).dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = 0.7f),
                            Color(0xFF00E5FF),
                            Color(0xFF00E5FF).copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Bottom Action Bar matching Reference Screenshots 2 & 3:
 * [Edit Text]  [Save as PDF]  [Export Document (Green Button)]
 */
@Composable
private fun BottomConvertingBar(
    isReady: Boolean,
    onEditText: () -> Unit,
    onSavePdf: () -> Unit,
    onExportDocument: () -> Unit
) {
    Surface(
        color = Color(0xFF181A1D),
        border = BorderStroke(1.dp, Color(0xFF2E343D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Edit Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onEditText() }
                    .padding(horizontal = 8.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Text",
                    tint = ExcelTextWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Edit Text",
                    color = ExcelTextWhite,
                    fontSize = 11.sp
                )
            }

            // 2. Save as PDF
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSavePdf() }
                    .padding(horizontal = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, ExcelTextWhite),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "PDF",
                            color = ExcelTextWhite,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Save as PDF",
                    color = ExcelTextWhite,
                    fontSize = 11.sp
                )
            }

            // 3. Prominent Green Button: "Export Document"
            Button(
                onClick = onExportDocument,
                colors = ButtonDefaults.buttonColors(containerColor = ExcelPrimaryGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .width(180.dp)
            ) {
                Text(
                    text = "Export Document",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Real Interactive Editable Spreadsheet Matrix Component
 */
@Composable
private fun InteractiveSpreadsheetGrid(
    table: EditableTable,
    onHeaderClick: (Int) -> Unit,
    onCellClick: (Int, Int) -> Unit
) {
    val hScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScrollState)
            .verticalScroll(rememberScrollState())
    ) {
        // Headers Row
        Row(modifier = Modifier.background(Color(0xFF1A1D21))) {
            // Row number corner block
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(38.dp)
                    .border(0.5.dp, Color(0xFF2E343D)),
                contentAlignment = Alignment.Center
            ) {
                Text("#", color = ExcelTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            table.headers.forEachIndexed { colIdx, header ->
                val colLetter = getColumnLetter(colIdx)
                Surface(
                    color = Color(0xFF22262C),
                    border = BorderStroke(0.5.dp, Color(0xFF2E343D)),
                    modifier = Modifier
                        .width(150.dp)
                        .height(38.dp)
                        .clickable { onHeaderClick(colIdx) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$colLetter: $header",
                            color = ExcelTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Data Rows
        table.rows.forEachIndexed { rowIdx, rowList ->
            val isEven = rowIdx % 2 == 0
            Row(modifier = Modifier.background(if (isEven) Color(0xFF141618) else Color(0xFF181B1F))) {
                // Row Index Number Column
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(40.dp)
                        .border(0.5.dp, Color(0xFF2E343D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${rowIdx + 1}", color = ExcelTextMuted, fontSize = 11.sp)
                }

                // Data Cells
                table.headers.indices.forEach { colIdx ->
                    val cellVal = rowList.getOrElse(colIdx) { "" }
                    val isNum = cellVal.replace("Tk.", "").replace("$", "").replace(",", "").trim().toDoubleOrNull() != null

                    Surface(
                        color = Color.Transparent,
                        border = BorderStroke(0.5.dp, Color(0xFF2E343D)),
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp)
                            .clickable { onCellClick(rowIdx, colIdx) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                text = cellVal.ifEmpty { "-" },
                                color = if (cellVal.isEmpty()) ExcelTextSecondary else if (isNum) Color(0xFF38BDF8) else ExcelTextWhite,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// UTILITY PARSERS
// ----------------------------------------------------

private fun parseStructuredExcelJson(raw: String, sourcePage: Int): List<EditableTable> {
    if (raw.isBlank()) return emptyList()

    val clean = raw
        .replace("```json", "")
        .replace("```", "")
        .trim()

    val firstBrace = clean.indexOf('{')
    val lastBrace = clean.lastIndexOf('}')
    if (firstBrace < 0 || lastBrace <= firstBrace) return emptyList()

    return try {
        val root = JSONObject(clean.substring(firstBrace, lastBrace + 1))
        val documentTitle = root.optString("documentTitle", "").trim()
        val tablesArray = root.optJSONArray("tables") ?: return emptyList()
        val result = mutableListOf<EditableTable>()

        for (i in 0 until tablesArray.length()) {
            val tableObj = tablesArray.optJSONObject(i) ?: continue
            val headersArray = tableObj.optJSONArray("headers") ?: continue
            val rowsArray = tableObj.optJSONArray("rows") ?: continue

            val headers = mutableListOf<String>()
            for (h in 0 until headersArray.length()) {
                headers.add(headersArray.optString(h, "").trim())
            }

            val rows = mutableListOf<MutableList<String>>()
            for (r in 0 until rowsArray.length()) {
                val rowArray = rowsArray.optJSONArray(r) ?: continue
                val row = mutableListOf<String>()
                for (c in 0 until rowArray.length()) {
                    row.add(rowArray.optString(c, "").trim())
                }
                rows.add(row)
            }

            val maxCols = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
            if (maxCols < 2 || rows.isEmpty()) continue

            while (headers.size < maxCols) headers.add("")
            headers.indices.forEach { idx ->
                if (headers[idx].isBlank()) headers[idx] = "Column ${idx + 1}"
            }
            rows.forEach { row -> while (row.size < maxCols) row.add("") }

            val title = tableObj.optString("title", "").trim()
            result.add(
                EditableTable(
                    sheetTitle = title.ifBlank { "Table ${result.size + 1}" },
                    headers = headers,
                    rows = rows,
                    documentTitle = documentTitle,
                    sourcePage = sourcePage
                )
            )
        }

        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseCsvRow(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '\"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == ',' && !inQuotes -> {
                result.add(sb.toString().trim())
                sb.clear()
            }
            c == '\t' && !inQuotes -> {
                result.add(sb.toString().trim())
                sb.clear()
            }
            c == '|' && !inQuotes -> {
                result.add(sb.toString().trim())
                sb.clear()
            }
            else -> {
                sb.append(c)
            }
        }
        i++
    }
    result.add(sb.toString().trim())
    return result.filter { it.isNotEmpty() || result.size > 1 }
}

private fun getColumnLetter(colIdx: Int): String {
    val sb = StringBuilder()
    var num = colIdx
    while (num >= 0) {
        sb.insert(0, ('A'.code + (num % 26)).toChar())
        num = (num / 26) - 1
    }
    return sb.toString()
}
