package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.data.model.ScanMode
import com.docscan.data.model.ScannerFeatureMode
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.AiOrchestrator
import com.docscan.util.FileUtils
import com.docscan.util.TextRecognizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val DarkCanvasBg: Color get() = ThemeManager.themeColor(Color(0xFF16181A), Color(0xFFF8FAFC))
private val DarkCardSurface: Color get() = ThemeManager.themeColor(Color(0xFF212328), Color(0xFFFFFFFF))
private val DarkCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C3038), Color(0xFFE2E8F0))
private val AccentTeal = Color(0xFF00D2D3)
private val AccentGreen = Color(0xFF10B981)
private val TextPrimaryWhite: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextSecondaryMuted: Color get() = ThemeManager.themeColor(Color(0xFF8E9BAE), Color(0xFF475569))

@Composable
fun ExtractTextScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()

    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isOcrRunning by remember { mutableStateOf(false) }

    var extractedContent by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) } // 0 = Full Text, 1 = AI Summary, 2 = AI Translate

    // Text to speech instance
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Load initial document if passed
    LaunchedEffect(initialDocId, documents) {
        if (initialDocId != null && initialDocId > 0L) {
            val doc = documents.find { it.id == initialDocId }
            if (doc != null) {
                selectedDocument = doc
            }
        }
    }

    // Extract text whenever selected document or page changes
    LaunchedEffect(selectedDocument) {
        val doc = selectedDocument
        if (doc != null) {
            isOcrRunning = true
            val pages = viewModel.getPagesForDocumentDirect(doc.id)
            documentPages = pages
            selectedPageIndex = 0
            
            // Collect text
            val combined = pages.mapNotNull { it.extractedText }.filter { it.isNotBlank() }.joinToString("\n\n")
            if (combined.isNotBlank()) {
                extractedContent = combined
                isOcrRunning = false
            } else {
                // Run OCR on pages
                coroutineScope.launch(Dispatchers.IO) {
                    val results = mutableListOf<String>()
                    for (page in pages) {
                        val bmp = FileUtils.loadBitmap(page.processedImagePath) ?: FileUtils.loadBitmap(page.originalImagePath)
                        if (bmp != null) {
                            try {
                                val visionText = TextRecognizerHelper.extractText(bmp)
                                if (visionText.isNotBlank()) {
                                    results.add(visionText)
                                    viewModel.updatePageExtractedText(page, visionText)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    val finalTxt = results.joinToString("\n\n")
                    withContext(Dispatchers.Main) {
                        extractedContent = if (finalTxt.isNotBlank()) finalTxt else "No text could be extracted from this document."
                        isOcrRunning = false
                    }
                }
            }
        }
    }

    // Gallery Picker for instant OCR
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                isOcrRunning = true
                val bmp = FileUtils.loadBitmapsFromUri(context, uri).firstOrNull()
                if (bmp != null) {
                    try {
                        val visionText = TextRecognizerHelper.extractText(bmp)
                        withContext(Dispatchers.Main) {
                            extractedContent = if (visionText.isNotBlank()) visionText else "No text detected in this image."
                            selectedDocument = null // Single image mode
                            isOcrRunning = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            extractedContent = "OCR Failed: ${e.message}"
                            isOcrRunning = false
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isOcrRunning = false
                    }
                }
            }
        }
    }

    BackHandler {
        if (selectedDocument != null && initialDocId == null) {
            selectedDocument = null
            extractedContent = ""
        } else {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) DarkCanvasBg else themePalette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (selectedDocument != null && initialDocId == null) {
                            selectedDocument = null
                            extractedContent = ""
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.testTag("button_back_extract_text")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Extract Text & OCR",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                    )
                    Text(
                        text = if (selectedDocument != null) selectedDocument!!.title else "Smart Text Recognition",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick source shortcuts if text is loaded
            if (extractedContent.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Extracted Text", extractedContent))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentTeal)
                    }

                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, extractedContent)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = AccentTeal)
                    }
                }
            }
        }

        if (selectedDocument == null && extractedContent.isBlank()) {
            // ==========================================
            // STATE 1: SELECT SOURCE / IMPORT
            // ==========================================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // Hero Feature Banner
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (themePalette.isDark) DarkCardSurface else themePalette.card,
                        border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else themePalette.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentTeal.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "High-Precision OCR",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Extract printed & handwritten text instantly from photos, receipts, notes & PDF pages.",
                                    fontSize = 13.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }
                }

                item {
                    // Quick Action Source Buttons
                    Text(
                        text = "Instant Capture or Import",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Camera Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (themePalette.isDark) DarkCardSurface else themePalette.card,
                            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else themePalette.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.scanMode.value = ScanMode.SINGLE
                                    viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                                    onNavigateToCamera()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Scan Camera", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary)
                            }
                        }

                        // Gallery Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (themePalette.isDark) DarkCardSurface else themePalette.card,
                            border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else themePalette.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { galleryLauncher.launch("image/*") }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("From Gallery", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (themePalette.isDark) TextPrimaryWhite else themePalette.textPrimary)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Or Select Scanned Document",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }

                items(documents, key = { it.id }) { doc ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (themePalette.isDark) DarkCardSurface else Color.White,
                        border = BorderStroke(1.dp, if (themePalette.isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDocument = doc }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.size(width = 40.dp, height = 50.dp)
                            ) {
                                val thumbFile = remember(doc.thumbnailPath) {
                                    if (doc.thumbnailPath.isNotBlank()) File(doc.thumbnailPath) else null
                                }
                                if (thumbFile != null && thumbFile.exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(thumbFile).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (themePalette.isDark) TextPrimaryWhite else Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${doc.pageCount} page(s) • Tap to extract text",
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // STATE 2: EXTRACTED TEXT STUDIO & AI TOOLS
            // ==========================================
            if (isOcrRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentTeal)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Extracting text with AI & OCR...",
                            color = TextSecondaryMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Tab Selection: Full Text | AI Summary | AI Translate
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (themePalette.isDark) Color(0xFF1E2128) else themePalette.card,
                    contentColor = AccentTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentTeal,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Extracted Text", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("✨ AI Summary", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("✨ Translate", fontWeight = FontWeight.Bold) }
                    )
                }

                // Main Tab Content
                when (selectedTab) {
                    0 -> ExtractedTextTab(
                        text = extractedContent,
                        isDark = themePalette.isDark,
                        onTextChange = { extractedContent = it },
                        tts = tts,
                        isSpeaking = isSpeaking,
                        onToggleSpeech = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                isSpeaking = true
                                tts?.speak(extractedContent, TextToSpeech.QUEUE_FLUSH, null, "ocr_speech")
                            }
                        }
                    )
                    1 -> AiSummaryTab(
                        originalText = extractedContent,
                        isDark = themePalette.isDark
                    )
                    2 -> AiTranslateTab(
                        originalText = extractedContent,
                        isDark = themePalette.isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractedTextTab(
    text: String,
    isDark: Boolean,
    onTextChange: (String) -> Unit,
    tts: TextToSpeech?,
    isSpeaking: Boolean,
    onToggleSpeech: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        editedText = text
    }

    val wordCount = remember(editedText) {
        if (editedText.isBlank()) 0 else editedText.trim().split("\\s+".toRegex()).size
    }
    val charCount = remember(editedText) { editedText.length }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // STATS & CONTROLS ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$wordCount Words",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = "$charCount Chars",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) TextPrimaryWhite else Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Speech Read Aloud Button
                IconButton(
                    onClick = onToggleSpeech,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read Aloud",
                        tint = if (isSpeaking) AccentGreen else (if (isDark) TextPrimaryWhite else Color(0xFF0F172A))
                    )
                }

                // Edit Mode Toggle Button
                IconButton(
                    onClick = {
                        if (isEditing) {
                            onTextChange(editedText)
                        }
                        isEditing = !isEditing
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = "Edit Text",
                        tint = if (isEditing) AccentGreen else AccentTeal
                    )
                }
            }
        }

        // TEXT DISPLAY / EDIT CARD
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) DarkCardSurface else Color.White,
            border = BorderStroke(1.dp, if (isDark) DarkCardBorder else Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = if (isDark) TextPrimaryWhite else Color(0xFF0F172A),
                        unfocusedTextColor = if (isDark) TextPrimaryWhite else Color(0xFF0F172A)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
                )
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = editedText,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            color = if (isDark) TextPrimaryWhite else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSummaryTab(
    originalText: String,
    isDark: Boolean
) {
    val context = LocalContext.current
    var summaryText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(originalText) {
        if (originalText.isNotBlank()) {
            isLoading = true
            val res = AiOrchestrator.summarizeDocumentAi(originalText)
            summaryText = if (res.isNotBlank()) res else "Could not generate summary."
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentTeal)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("⚡ AI Ensemble Generating Summary...", color = TextSecondaryMuted, fontSize = 14.sp)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) DarkCardSurface else Color.White,
                border = BorderStroke(1.dp, if (isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Executive Summary & Key Takeaways", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccentTeal)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = summaryText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = if (isDark) TextPrimaryWhite else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTranslateTab(
    originalText: String,
    isDark: Boolean
) {
    val languages = listOf("Bengali", "English", "Spanish", "Hindi", "Arabic", "French", "German", "Japanese", "Chinese")
    var selectedLang by remember { mutableStateOf("Bengali") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }

    LaunchedEffect(originalText, selectedLang) {
        if (originalText.isNotBlank()) {
            isTranslating = true
            val res = AiOrchestrator.translateTextAi(originalText, selectedLang)
            translatedText = res
            isTranslating = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Target Translation Language:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryMuted)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languages) { lang ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedLang == lang) AccentTeal else (if (isDark) Color(0xFF2A2D36) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { selectedLang = lang }
                ) {
                    Text(
                        text = lang,
                        fontSize = 13.sp,
                        fontWeight = if (selectedLang == lang) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedLang == lang) Color.Black else (if (isDark) TextPrimaryWhite else Color(0xFF0F172A)),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isTranslating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentTeal)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) DarkCardSurface else Color.White,
                border = BorderStroke(1.dp, if (isDark) DarkCardBorder else Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = translatedText,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            color = if (isDark) TextPrimaryWhite else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}
