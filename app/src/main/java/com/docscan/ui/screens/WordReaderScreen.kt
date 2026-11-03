package com.docscan.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.data.model.PageEntity
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.DocxReader
import com.docscan.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WordReaderTheme(val label: String, val bg: Color, val paperBg: Color, val text: Color, val textSecondary: Color, val accent: Color) {
    DARK("OLED Dark", Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFE0E0E0), Color(0xFFA0A0A0), Color(0xFF60A5FA)),
    LIGHT("Classic Light", Color(0xFFF3F4F6), Color(0xFFFFFFFF), Color(0xFF1F2937), Color(0xFF4B5563), Color(0xFF2563EB)),
    SEPIA("Eye-Care Sepia", Color(0xFFF4ECD8), Color(0xFFFAF4E8), Color(0xFF4A3B32), Color(0xFF7D6B5D), Color(0xFFB45309)),
    SLATE("Modern Slate", Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF38BDF8))
}

enum class WordFontFamily(val label: String, val font: FontFamily) {
    DEFAULT("Sans", FontFamily.SansSerif),
    SERIF("Book Serif", FontFamily.Serif),
    MONOSPACE("Code Mono", FontFamily.Monospace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReaderScreen(
    initialFilePath: String? = null,
    initialUriString: String? = null,
    initialTitle: String? = null,
    viewModel: ScannerViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var parsedDoc by remember { mutableStateOf<DocxReader.ParsedDocx?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeFilePath by remember { mutableStateOf(initialFilePath) }
    var activeTitle by remember { mutableStateOf(initialTitle ?: "Word Document") }

    // Reader Preferences
    var activeTheme by remember { mutableStateOf(WordReaderTheme.DARK) }
    var activeFont by remember { mutableStateOf(WordFontFamily.DEFAULT) }
    var fontSizeSp by remember { mutableFloatStateOf(15f) }
    var lineSpacingMultiplier by remember { mutableFloatStateOf(1.4f) }

    // Search in document
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCount by remember { mutableIntStateOf(0) }

    // Dialogs
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showDocDetailsDialog by remember { mutableStateOf(false) }
    var zoomedImage by remember { mutableStateOf<Bitmap?>(null) }

    // Open file launcher
    val docxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Document.docx"
                activeTitle = fileName.removeSuffix(".docx")
                val doc = DocxReader.readDocxUri(context, uri, activeTitle)
                withContext(Dispatchers.Main) {
                    parsedDoc = doc
                    isLoading = false
                }
            }
        }
    }

    // Load initial document
    LaunchedEffect(initialFilePath, initialUriString) {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val doc = when {
                !initialFilePath.isNullOrBlank() -> {
                    val file = File(initialFilePath)
                    activeTitle = file.nameWithoutExtension
                    DocxReader.readDocxFile(context, file)
                }
                !initialUriString.isNullOrBlank() -> {
                    val uri = Uri.parse(initialUriString)
                    activeTitle = initialTitle ?: "Word Document"
                    DocxReader.readDocxUri(context, uri, activeTitle)
                }
                else -> {
                    // Try to find any existing docx file in temp/storage or show empty sample
                    val tempDocx = File(context.cacheDir, "sample.docx")
                    if (tempDocx.exists()) {
                        DocxReader.readDocxFile(context, tempDocx)
                    } else {
                        DocxReader.ParsedDocx(
                            title = "Word Reader",
                            elements = listOf(
                                DocxReader.DocxElement.Heading("Welcome to Advanced Word Reader 📖", 1),
                                DocxReader.DocxElement.Paragraph(
                                    listOf(
                                        DocxReader.TextRun("Open any Microsoft Word (.docx) document from your device or app history to read with rich formatting, tables, images, and customizable reading themes.")
                                    )
                                )
                            ),
                            wordCount = 32,
                            paragraphCount = 1,
                            tableCount = 0,
                            imageCount = 0
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                parsedDoc = doc
                isLoading = false
            }
        }
    }

    // Update match count when searching
    LaunchedEffect(searchQuery, parsedDoc) {
        if (searchQuery.isBlank() || parsedDoc == null) {
            matchCount = 0
        } else {
            var count = 0
            val q = searchQuery.lowercase()
            parsedDoc?.elements?.forEach { el ->
                when (el) {
                    is DocxReader.DocxElement.Heading -> if (el.text.lowercase().contains(q)) count++
                    is DocxReader.DocxElement.Paragraph -> {
                        val full = el.runs.joinToString("") { it.text }
                        if (full.lowercase().contains(q)) count++
                    }
                    is DocxReader.DocxElement.Table -> {
                        el.rows.forEach { row ->
                            if (row.any { it.lowercase().contains(q) }) count++
                        }
                    }
                    else -> {}
                }
            }
            matchCount = count
        }
    }

    Scaffold(
        containerColor = activeTheme.bg,
        topBar = {
            Surface(
                color = activeTheme.paperBg,
                shadowElevation = 4.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = parsedDoc?.title ?: activeTitle,
                                    color = activeTheme.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${parsedDoc?.wordCount ?: 0} words • ${parsedDoc?.tableCount ?: 0} tables • ${parsedDoc?.imageCount ?: 0} images",
                                    color = activeTheme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = activeTheme.text)
                            }
                        },
                        actions = {
                            // Search toggle
                            IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isSearchOpen) activeTheme.accent else activeTheme.text)
                            }
                            // Appearance Settings
                            IconButton(onClick = { showAppearanceDialog = true }) {
                                Icon(Icons.Outlined.FormatSize, contentDescription = "Font & Theme", tint = activeTheme.text)
                            }
                            // Open Another DOCX
                            IconButton(onClick = {
                                docxFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword"))
                            }) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = "Open DOCX", tint = activeTheme.text)
                            }
                            // Share / Save
                            IconButton(onClick = {
                                if (!activeFilePath.isNullOrBlank()) {
                                    val file = File(activeFilePath!!)
                                    if (file.exists()) {
                                        FileUtils.shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Share Word Document")
                                    } else {
                                        Toast.makeText(context, "Document available in app storage", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Document opened in Advanced Reader", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = activeTheme.text)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = activeTheme.paperBg
                        )
                    )

                    // Search Bar Dropdown
                    AnimatedVisibility(
                        visible = isSearchOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = activeTheme.bg,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = activeTheme.accent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search in document...", color = activeTheme.textSecondary, fontSize = 13.sp) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = activeTheme.text,
                                        unfocusedTextColor = activeTheme.text
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = "$matchCount found",
                                        color = activeTheme.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = activeTheme.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = activeTheme.paperBg,
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
                    // Quick Font Size Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(11f) },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = activeTheme.bg,
                                contentColor = activeTheme.text
                            )
                        ) {
                            Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${fontSizeSp.toInt()} pt",
                            color = activeTheme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(28f) },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = activeTheme.bg,
                                contentColor = activeTheme.text
                            )
                        ) {
                            Text("A+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick Theme Toggles (Color Dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WordReaderTheme.entries.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(theme.paperBg)
                                    .border(
                                        width = if (activeTheme == theme) 2.dp else 1.dp,
                                        color = if (activeTheme == theme) activeTheme.accent else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { activeTheme = theme }
                            )
                        }
                    }

                    // Copy All Text Action
                    OutlinedButton(
                        onClick = {
                            val allText = parsedDoc?.elements?.joinToString("\n\n") { el ->
                                when (el) {
                                    is DocxReader.DocxElement.Heading -> "# ${el.text}"
                                    is DocxReader.DocxElement.Paragraph -> el.runs.joinToString("") { it.text }
                                    is DocxReader.DocxElement.Table -> el.headers.joinToString(" | ") + "\n" + el.rows.joinToString("\n") { it.joinToString(" | ") }
                                    else -> ""
                                }
                            } ?: ""
                            if (allText.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(allText))
                                Toast.makeText(context, "Document text copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = activeTheme.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = activeTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(activeTheme.bg)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = activeTheme.accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reading and formatting Word document...",
                        color = activeTheme.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                val doc = parsedDoc
                if (doc == null || doc.elements.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = activeTheme.textSecondary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No content found in this document", color = activeTheme.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                docxFileLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = activeTheme.accent)
                        ) {
                            Text("Open a Word File (.docx)")
                        }
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Document Header Card
                            item {
                                Surface(
                                    color = activeTheme.paperBg,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, activeTheme.textSecondary.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = activeTheme.accent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("W", color = activeTheme.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = doc.title,
                                                    color = activeTheme.text,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Microsoft Word Document (.docx)",
                                                    color = activeTheme.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = activeTheme.textSecondary.copy(alpha = 0.15f))
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Stats Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround
                                        ) {
                                            DocStatBadge(label = "Words", value = "${doc.wordCount}", theme = activeTheme)
                                            DocStatBadge(label = "Paragraphs", value = "${doc.paragraphCount}", theme = activeTheme)
                                            DocStatBadge(label = "Tables", value = "${doc.tableCount}", theme = activeTheme)
                                            DocStatBadge(label = "Images", value = "${doc.imageCount}", theme = activeTheme)
                                        }
                                    }
                                }
                            }

                            // Document Elements
                            itemsIndexed(doc.elements) { _, element ->
                                when (element) {
                                    is DocxReader.DocxElement.Heading -> {
                                        WordHeadingView(
                                            heading = element,
                                            theme = activeTheme,
                                            font = activeFont,
                                            baseFontSize = fontSizeSp,
                                            searchQuery = searchQuery
                                        )
                                    }
                                    is DocxReader.DocxElement.Paragraph -> {
                                        WordParagraphView(
                                            paragraph = element,
                                            theme = activeTheme,
                                            font = activeFont,
                                            fontSizeSp = fontSizeSp,
                                            lineSpacingMultiplier = lineSpacingMultiplier,
                                            searchQuery = searchQuery
                                        )
                                    }
                                    is DocxReader.DocxElement.Table -> {
                                        WordTableView(
                                            table = element,
                                            theme = activeTheme,
                                            font = activeFont,
                                            fontSizeSp = fontSizeSp,
                                            searchQuery = searchQuery
                                        )
                                    }
                                    is DocxReader.DocxElement.ImageItem -> {
                                        WordImageView(
                                            imageItem = element,
                                            theme = activeTheme,
                                            onImageClick = { zoomedImage = element.bitmap }
                                        )
                                    }
                                    is DocxReader.DocxElement.Divider -> {
                                        HorizontalDivider(
                                            color = activeTheme.textSecondary.copy(alpha = 0.2f),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // End of Document spacing
                            item {
                                Spacer(modifier = Modifier.height(48.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "— End of Document —",
                                        color = activeTheme.textSecondary.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Appearance & Font Dialog
    if (showAppearanceDialog) {
        Dialog(onDismissRequest = { showAppearanceDialog = false }) {
            Surface(
                color = activeTheme.paperBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, activeTheme.textSecondary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Reader Appearance",
                        color = activeTheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Reading Theme
                    Text("Theme Palette", color = activeTheme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WordReaderTheme.entries.forEach { theme ->
                            FilterChip(
                                selected = activeTheme == theme,
                                onClick = { activeTheme = theme },
                                label = { Text(theme.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = activeTheme.accent.copy(alpha = 0.2f),
                                    selectedLabelColor = activeTheme.accent,
                                    labelColor = activeTheme.text
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Family
                    Text("Typography", color = activeTheme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WordFontFamily.entries.forEach { ff ->
                            FilterChip(
                                selected = activeFont == ff,
                                onClick = { activeFont = ff },
                                label = { Text(ff.label, fontSize = 11.sp, fontFamily = ff.font) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = activeTheme.accent.copy(alpha = 0.2f),
                                    selectedLabelColor = activeTheme.accent,
                                    labelColor = activeTheme.text
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Line Spacing
                    Text("Line Spacing", color = activeTheme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1.2f to "Compact", 1.4f to "Normal", 1.8f to "Relaxed").forEach { (spacing, label) ->
                            FilterChip(
                                selected = lineSpacingMultiplier == spacing,
                                onClick = { lineSpacingMultiplier = spacing },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = activeTheme.accent.copy(alpha = 0.2f),
                                    selectedLabelColor = activeTheme.accent,
                                    labelColor = activeTheme.text
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showAppearanceDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = activeTheme.accent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Fullscreen Zoomed Image Dialog
    zoomedImage?.let { bmp ->
        Dialog(
            onDismissRequest = { zoomedImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoomedImage = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Zoomed embedded image",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .clip(RoundedCornerShape(12.dp))
                )
                IconButton(
                    onClick = { zoomedImage = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DocStatBadge(label: String, value: String, theme: WordReaderTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = theme.accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = theme.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun WordHeadingView(
    heading: DocxReader.DocxElement.Heading,
    theme: WordReaderTheme,
    font: WordFontFamily,
    baseFontSize: Float,
    searchQuery: String
) {
    val headingSize = when (heading.level) {
        1 -> (baseFontSize + 6f).sp
        2 -> (baseFontSize + 4f).sp
        else -> (baseFontSize + 2f).sp
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(theme.accent, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = highlightSearchText(heading.text, searchQuery, theme.accent),
            color = theme.text,
            fontSize = headingSize,
            fontWeight = FontWeight.Bold,
            fontFamily = font.font
        )
    }
}

@Composable
private fun WordParagraphView(
    paragraph: DocxReader.DocxElement.Paragraph,
    theme: WordReaderTheme,
    font: WordFontFamily,
    fontSizeSp: Float,
    lineSpacingMultiplier: Float,
    searchQuery: String
) {
    val textAlign = when (paragraph.alignment.lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        "both", "justify" -> TextAlign.Justify
        else -> TextAlign.Start
    }

    val annotated = buildAnnotatedString {
        paragraph.runs.forEach { run ->
            val color = if (run.colorHex != null) {
                try {
                    Color(android.graphics.Color.parseColor("#" + run.colorHex.removePrefix("#")))
                } catch (e: Exception) {
                    theme.text
                }
            } else {
                theme.text
            }

            withStyle(
                style = SpanStyle(
                    color = color,
                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None,
                    fontSize = (fontSizeSp * (run.fontSizePt / 11f).coerceIn(0.85f, 1.3f)).sp
                )
            ) {
                append(run.text)
            }
        }
    }

    Text(
        text = if (searchQuery.isBlank()) annotated else highlightAnnotatedSearch(annotated, searchQuery, theme.accent),
        color = theme.text,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
        fontFamily = font.font,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WordTableView(
    table: DocxReader.DocxElement.Table,
    theme: WordReaderTheme,
    font: WordFontFamily,
    fontSizeSp: Float,
    searchQuery: String
) {
    Surface(
        color = theme.paperBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(theme.accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                table.headers.forEach { header ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 90.dp, max = 220.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = highlightSearchText(header, searchQuery, theme.accent),
                            color = theme.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = (fontSizeSp - 1f).sp,
                            fontFamily = font.font
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Table Data Rows
            table.rows.forEachIndexed { rowIdx, rowData ->
                val rowBg = if (rowIdx % 2 == 0) theme.paperBg else theme.bg.copy(alpha = 0.5f)
                Row(
                    modifier = Modifier
                        .background(rowBg, RoundedCornerShape(4.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    rowData.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 220.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = highlightSearchText(cell, searchQuery, theme.accent),
                                color = theme.text,
                                fontSize = (fontSizeSp - 1.5f).sp,
                                fontFamily = font.font
                            )
                        }
                    }
                }
                HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun WordImageView(
    imageItem: DocxReader.DocxElement.ImageItem,
    theme: WordReaderTheme,
    onImageClick: () -> Unit
) {
    Surface(
        color = theme.paperBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onImageClick() }
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = imageItem.bitmap.asImageBitmap(),
                contentDescription = imageItem.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap to enlarge image 🔍",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

private fun highlightSearchText(text: String, query: String, highlightColor: Color): AnnotatedString {
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
            withStyle(SpanStyle(background = highlightColor.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)) {
                append(text.substring(foundIdx, foundIdx + query.length))
            }
            startIndex = foundIdx + query.length
        }
    }
}

private fun highlightAnnotatedSearch(annotated: AnnotatedString, query: String, highlightColor: Color): AnnotatedString {
    val text = annotated.text
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) return annotated
    return highlightSearchText(text, query, highlightColor)
}
