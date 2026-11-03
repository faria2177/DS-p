package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.ExcelReader
import com.docscan.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ExcelGreen = Color(0xFF107C41)
private val ExcelGreenLight = Color(0xFF16A34A)
private val ExcelHeaderBg = Color(0xFF0C5A2F)
private val DarkBg: Color get() = ThemeManager.themeColor(Color(0xFF121417), Color(0xFFF8FAFC))
private val DarkPaperBg: Color get() = ThemeManager.themeColor(Color(0xFF1E2126), Color(0xFFFFFFFF))
private val CellBorderColor: Color get() = ThemeManager.themeColor(Color(0xFF2E333D), Color(0xFFE2E8F0))

enum class ExcelViewMode {
    SPREADSHEET_GRID,
    CARD_RECORDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelReaderScreen(
    initialFilePath: String? = null,
    initialUriString: String? = null,
    initialTitle: String? = null,
    viewModel: ScannerViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var parsedWorkbook by remember { mutableStateOf<ExcelReader.ParsedWorkbook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeFilePath by remember { mutableStateOf(initialFilePath) }
    var activeTitle by remember { mutableStateOf(initialTitle ?: "Spreadsheet") }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }

    // Display & View Modes
    var viewMode by remember { mutableStateOf(ExcelViewMode.SPREADSHEET_GRID) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }

    // Selected Cell / Cell Inspector
    var selectedCellRow by remember { mutableIntStateOf(-1) }
    var selectedCellCol by remember { mutableIntStateOf(-1) }
    var selectedCellValue by remember { mutableStateOf("") }
    var selectedCellHeader by remember { mutableStateOf("") }

    // Search / Filter
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCount by remember { mutableIntStateOf(0) }

    // Numerical Stats Dialog
    var showStatsDialog by remember { mutableStateOf(false) }

    // Open file launcher
    val xlsxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Spreadsheet.xlsx"
                activeTitle = fileName.removeSuffix(".xlsx").removeSuffix(".xls")
                val wb = ExcelReader.readXlsxUri(context, uri, activeTitle)
                withContext(Dispatchers.Main) {
                    parsedWorkbook = wb
                    selectedSheetIndex = 0
                    isLoading = false
                }
            }
        }
    }

    // Load initial document
    LaunchedEffect(initialFilePath, initialUriString) {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val wb = when {
                !initialFilePath.isNullOrBlank() -> {
                    val file = File(initialFilePath)
                    activeTitle = file.nameWithoutExtension
                    ExcelReader.readXlsxFile(context, file)
                }
                !initialUriString.isNullOrBlank() -> {
                    val uri = Uri.parse(initialUriString)
                    activeTitle = initialTitle ?: "Spreadsheet"
                    ExcelReader.readXlsxUri(context, uri, activeTitle)
                }
                else -> {
                    val sampleFile = File(context.cacheDir, "sample.xlsx")
                    if (sampleFile.exists()) {
                        ExcelReader.readXlsxFile(context, sampleFile)
                    } else {
                        ExcelReader.ParsedWorkbook(
                            title = "Excel Reader",
                            sheets = listOf(
                                ExcelReader.ParsedSheet(
                                    name = "Overview",
                                    headers = listOf("ID", "Item Description", "Category", "Quantity", "Unit Price", "Total Amount"),
                                    rows = listOf(
                                        listOf("1", "Scanned Invoice Document", "Office", "2", "450.00", "900.00"),
                                        listOf("2", "AI Optical Character Recognition", "Software", "1", "1,200.00", "1,200.00"),
                                        listOf("3", "Multi-page PDF Export", "Service", "5", "150.00", "750.00"),
                                        listOf("4", "Spreadsheet Cell Reconstruction", "Data", "1", "850.00", "850.00")
                                    ),
                                    rowCount = 4,
                                    columnCount = 6
                                )
                            )
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                parsedWorkbook = wb
                selectedSheetIndex = 0
                isLoading = false
            }
        }
    }

    val activeSheet = parsedWorkbook?.sheets?.getOrNull(selectedSheetIndex)

    // Calculate match count
    LaunchedEffect(searchQuery, activeSheet) {
        if (searchQuery.isBlank() || activeSheet == null) {
            matchCount = 0
        } else {
            val q = searchQuery.lowercase()
            var count = 0
            activeSheet.headers.forEach { if (it.lowercase().contains(q)) count++ }
            activeSheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell.lowercase().contains(q)) count++
                }
            }
            matchCount = count
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Surface(
                color = DarkPaperBg,
                shadowElevation = 4.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = parsedWorkbook?.title ?: activeTitle,
                                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${parsedWorkbook?.sheets?.size ?: 1} sheet(s) • ${activeSheet?.rowCount ?: 0} rows • ${activeSheet?.columnCount ?: 0} cols",
                                    color = ThemeManager.themeColor(Color.LightGray, Color(0xFF475569)),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                            }
                        },
                        actions = {
                            // View Mode Toggle (Grid vs Cards)
                            IconButton(onClick = {
                                viewMode = if (viewMode == ExcelViewMode.SPREADSHEET_GRID) ExcelViewMode.CARD_RECORDS else ExcelViewMode.SPREADSHEET_GRID
                            }) {
                                Icon(
                                    if (viewMode == ExcelViewMode.SPREADSHEET_GRID) Icons.Outlined.ViewAgenda else Icons.Outlined.GridOn,
                                    contentDescription = "Toggle View",
                                    tint = ExcelGreenLight
                                )
                            }
                            // Search toggle
                            IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isSearchOpen) ExcelGreenLight else ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                            }
                            // Math Stats Calculator
                            IconButton(onClick = { showStatsDialog = true }) {
                                Icon(Icons.Outlined.Functions, contentDescription = "Summary & AutoSum", tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                            }
                            // Open another XLSX
                            IconButton(onClick = {
                                xlsxFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv"))
                            }) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = "Open XLSX", tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                            }
                            // Share
                            IconButton(onClick = {
                                if (!activeFilePath.isNullOrBlank()) {
                                    val file = File(activeFilePath!!)
                                    if (file.exists()) {
                                        FileUtils.shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Share Excel Spreadsheet")
                                    } else {
                                        Toast.makeText(context, "Spreadsheet saved in app storage", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Spreadsheet opened in Advanced Reader", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkPaperBg
                        )
                    )

                    // Search Bar
                    AnimatedVisibility(
                        visible = isSearchOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = DarkBg,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ExcelGreenLight.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = ExcelGreenLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search spreadsheet cells...", color = Color.Gray, fontSize = 13.sp) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = "$matchCount hits",
                                        color = ExcelGreenLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Sheets Tabs
                    val sheets = parsedWorkbook?.sheets ?: emptyList()
                    if (sheets.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedSheetIndex,
                            containerColor = DarkPaperBg,
                            contentColor = ExcelGreenLight,
                            edgePadding = 12.dp,
                            divider = {}
                        ) {
                            sheets.forEachIndexed { index, sheet ->
                                Tab(
                                    selected = selectedSheetIndex == index,
                                    onClick = {
                                        selectedSheetIndex = index
                                        selectedCellRow = -1
                                        selectedCellCol = -1
                                        selectedCellValue = ""
                                    },
                                    text = {
                                        Text(
                                            text = sheet.name,
                                            fontWeight = if (selectedSheetIndex == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedSheetIndex == index) ExcelGreenLight else Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Formula / Active Cell Value Bar
                    Surface(
                        color = ThemeManager.themeColor(Color(0xFF181B20), Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, CellBorderColor))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ExcelGreen.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (selectedCellCol >= 0 && selectedCellRow >= 0) {
                                        "${indexToColumnLetter(selectedCellCol)}${selectedCellRow + 1}"
                                    } else "fx",
                                    color = ExcelGreenLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (selectedCellValue.isNotBlank()) {
                                    if (selectedCellHeader.isNotBlank()) "[$selectedCellHeader]: $selectedCellValue" else selectedCellValue
                                } else "Tap any cell to inspect or copy formula/data",
                                color = if (selectedCellValue.isNotBlank()) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedCellValue.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(selectedCellValue))
                                        Toast.makeText(context, "Cell content copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Cell", tint = ExcelGreenLight, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = DarkPaperBg,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Font Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(10f) },
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = DarkBg,
                                contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                            )
                        ) {
                            Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${fontSizeSp.toInt()} pt",
                            color = ThemeManager.themeColor(Color.LightGray, Color(0xFF475569)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(22f) },
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = DarkBg,
                                contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                            )
                        ) {
                            Text("A+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Copy Sheet As CSV
                    OutlinedButton(
                        onClick = {
                            if (activeSheet != null) {
                                val csvText = buildString {
                                    append(activeSheet.headers.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                                    append("\n")
                                    activeSheet.rows.forEach { row ->
                                        append(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                                        append("\n")
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(csvText))
                                Toast.makeText(context, "Entire sheet copied as CSV format!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ExcelGreenLight.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = ExcelGreenLight, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy CSV", color = ExcelGreenLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ExcelGreenLight, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reading and parsing Excel spreadsheet...",
                        color = ThemeManager.themeColor(Color.LightGray, Color(0xFF475569)),
                        fontSize = 13.sp
                    )
                }
            } else if (activeSheet == null || activeSheet.rows.isEmpty() && activeSheet.headers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.TableChart, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No rows found in this sheet", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            xlsxFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcelGreenLight)
                    ) {
                        Text("Open an Excel File (.xlsx)")
                    }
                }
            } else {
                SelectionContainer {
                    if (viewMode == ExcelViewMode.SPREADSHEET_GRID) {
                        // 2D Matrix Spreadsheet Table View
                        SpreadsheetGridView(
                            sheet = activeSheet,
                            fontSizeSp = fontSizeSp,
                            searchQuery = searchQuery,
                            selectedCellRow = selectedCellRow,
                            selectedCellCol = selectedCellCol,
                            onCellClick = { rowIdx, colIdx, value, header ->
                                selectedCellRow = rowIdx
                                selectedCellCol = colIdx
                                selectedCellValue = value
                                selectedCellHeader = header
                            }
                        )
                    } else {
                        // Card / Record List View
                        SpreadsheetCardsView(
                            sheet = activeSheet,
                            fontSizeSp = fontSizeSp,
                            searchQuery = searchQuery,
                            onCellClick = { rowIdx, colIdx, value, header ->
                                selectedCellRow = rowIdx
                                selectedCellCol = colIdx
                                selectedCellValue = value
                                selectedCellHeader = header
                            }
                        )
                    }
                }
            }
        }
    }

    // Auto-Sum & Summary Statistics Dialog
    if (showStatsDialog && activeSheet != null) {
        val numValues = mutableListOf<Double>()
        activeSheet.rows.forEach { r ->
            r.forEach { cell ->
                val cleaned = cell.replace(",", "").replace("$", "").replace("৳", "").trim()
                cleaned.toDoubleOrNull()?.let { numValues.add(it) }
            }
        }

        val sum = numValues.sum()
        val avg = if (numValues.isNotEmpty()) sum / numValues.size else 0.0
        val max = numValues.maxOrNull() ?: 0.0
        val min = numValues.minOrNull() ?: 0.0

        Dialog(onDismissRequest = { showStatsDialog = false }) {
            Surface(
                color = DarkPaperBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CellBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ExcelGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Functions, contentDescription = null, tint = ExcelGreenLight, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Sheet Summary & AutoSum", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${activeSheet.name} • ${activeSheet.rowCount} rows", color = ThemeManager.themeColor(Color.LightGray, Color(0xFF475569)), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CellBorderColor)
                    Spacer(modifier = Modifier.height(12.dp))

                    StatRowItem("Total Numerical Cells", "${numValues.size}")
                    StatRowItem("AutoSum (Total Σ)", String.format(java.util.Locale.US, "%,.2f", sum))
                    StatRowItem("Average (Mean µ)", String.format(java.util.Locale.US, "%,.2f", avg))
                    StatRowItem("Max Value", String.format(java.util.Locale.US, "%,.2f", max))
                    StatRowItem("Min Value", String.format(java.util.Locale.US, "%,.2f", min))

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showStatsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ExcelGreenLight),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = ThemeManager.themeColor(Color.LightGray, Color(0xFF475569)), fontSize = 13.sp)
        Text(text = value, color = ExcelGreenLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpreadsheetGridView(
    sheet: ExcelReader.ParsedSheet,
    fontSizeSp: Float,
    searchQuery: String,
    selectedCellRow: Int,
    selectedCellCol: Int,
    onCellClick: (row: Int, col: Int, value: String, header: String) -> Unit
) {
    val hScrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState)) {
        LazyColumn(modifier = Modifier.fillMaxHeight()) {
            // Column Letters Header (A, B, C...)
            item {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF0F1216))
                        .border(BorderStroke(1.dp, CellBorderColor))
                ) {
                    // Corner blank box
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(32.dp)
                            .background(ThemeManager.themeColor(Color(0xFF161A20), Color(0xFFF1F5F9)))
                            .border(BorderStroke(0.5.dp, CellBorderColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    sheet.headers.forEachIndexed { colIdx, _ ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(32.dp)
                                .background(ThemeManager.themeColor(Color(0xFF161A20), Color(0xFFF1F5F9)))
                                .border(BorderStroke(0.5.dp, CellBorderColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = indexToColumnLetter(colIdx),
                                color = ExcelGreenLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Headers Row
            item {
                Row(
                    modifier = Modifier
                        .background(ExcelHeaderBg)
                        .border(BorderStroke(1.dp, CellBorderColor))
                ) {
                    // Row 0 corner
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(40.dp)
                            .background(ExcelHeaderBg)
                            .border(BorderStroke(0.5.dp, CellBorderColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    sheet.headers.forEachIndexed { colIdx, headerText ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp)
                                .background(ExcelHeaderBg)
                                .border(BorderStroke(0.5.dp, CellBorderColor))
                                .clickable { onCellClick(0, colIdx, headerText, headerText) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = highlightSearch(headerText, searchQuery),
                                color = Color.White,
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Data Rows
            itemsIndexed(sheet.rows) { rowIdx, rowCells ->
                val displayRowNum = rowIdx + 2
                val isRowSelected = selectedCellRow == rowIdx

                Row(
                    modifier = Modifier
                        .background(if (isRowSelected) ThemeManager.themeColor(Color(0xFF1A2A20), Color(0xFFCCFBF1)) else if (rowIdx % 2 == 0) DarkPaperBg else DarkBg)
                        .border(BorderStroke(0.5.dp, CellBorderColor))
                ) {
                    // Row Number Label
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(36.dp)
                            .background(ThemeManager.themeColor(Color(0xFF161A20), Color(0xFFF1F5F9)))
                            .border(BorderStroke(0.5.dp, CellBorderColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$displayRowNum",
                            color = if (isRowSelected) ExcelGreenLight else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Cells
                    rowCells.forEachIndexed { colIdx, cellValue ->
                        val isCellSelected = selectedCellRow == rowIdx && selectedCellCol == colIdx
                        val header = sheet.headers.getOrNull(colIdx) ?: "Col ${colIdx + 1}"

                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(36.dp)
                                .background(
                                    if (isCellSelected) ExcelGreen.copy(alpha = 0.35f)
                                    else Color.Transparent
                                )
                                .border(
                                    BorderStroke(
                                        if (isCellSelected) 1.5.dp else 0.5.dp,
                                        if (isCellSelected) ExcelGreenLight else CellBorderColor
                                    )
                                )
                                .clickable { onCellClick(rowIdx, colIdx, cellValue, header) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = highlightSearch(cellValue, searchQuery),
                                color = if (isCellSelected) ThemeManager.themeColor(Color.White, Color(0xFF0F172A)) else ThemeManager.themeColor(Color(0xFFE2E8F0), Color(0xFF0F172A)),
                                fontSize = fontSizeSp.sp,
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

@Composable
private fun SpreadsheetCardsView(
    sheet: ExcelReader.ParsedSheet,
    fontSizeSp: Float,
    searchQuery: String,
    onCellClick: (row: Int, col: Int, value: String, header: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(sheet.rows) { rowIdx, rowData ->
            Surface(
                color = DarkPaperBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CellBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ExcelGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Row #${rowIdx + 1}",
                                color = ExcelGreenLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    rowData.forEachIndexed { colIdx, cellValue ->
                        val header = sheet.headers.getOrNull(colIdx) ?: "Column ${colIdx + 1}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCellClick(rowIdx, colIdx, cellValue, header) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = header,
                                color = Color.Gray,
                                fontSize = (fontSizeSp - 1f).sp,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = highlightSearch(cellValue, searchQuery),
                                color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                        if (colIdx < rowData.size - 1) {
                            HorizontalDivider(color = CellBorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun indexToColumnLetter(index: Int): String {
    var num = index + 1
    val sb = StringBuilder()
    while (num > 0) {
        val rem = (num - 1) % 26
        sb.append(('A'.code + rem).toChar())
        num = (num - 1) / 26
    }
    return sb.reverse().toString()
}

private fun highlightSearch(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var startIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        while (startIndex < text.length) {
            val foundIdx = lowerText.indexOf(lowerQuery, startIndex)
            if (foundIdx == -1) {
                append(text.substring(startIndex))
                break
            }
            if (foundIdx > startIndex) {
                append(text.substring(startIndex, foundIdx))
            }
            withStyle(SpanStyle(background = Color(0xFF107C41).copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                append(text.substring(foundIdx, foundIdx + query.length))
            }
            startIndex = foundIdx + query.length
        }
    }
}
