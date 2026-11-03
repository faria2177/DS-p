package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Clean, high-contrast dark theme colors matching reference screenshots
private val MergeDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF141416), Color(0xFFF8FAFC))
private val MergeHeroBg: Color get() = ThemeManager.themeColor(Color(0xFF1E1E22), Color(0xFFFFFFFF))
private val MergeCardBg: Color get() = ThemeManager.themeColor(Color(0xFF26262A), Color(0xFFFFFFFF))
private val MergeTealAccent = Color(0xFF00BFA5)
private val MergeTealHighlight: Color get() = ThemeManager.themeColor(Color(0xFF143831), Color(0xFFCCFBF1))
private val MergeTextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFFFFFFF), Color(0xFF0F172A))
private val MergeTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9E9EA4), Color(0xFF475569))
private val MergeItemBorder: Color get() = ThemeManager.themeColor(Color(0xFF2C2C30), Color(0xFFE2E8F0))

enum class MergeWorkflowStep {
    SELECT,   // Screen 1 & Screen 2: Merge Files Selection & Multi-select
    REORDER   // Screen 3: Reorder & Merge Documents
}

@Composable
fun MergeFilesScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onMergeCompleted: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDocuments by viewModel.allDocuments.collectAsState(initial = emptyList())

    // Workflow state: Step 1 (Select) or Step 2 (Reorder)
    var currentStep by remember { mutableStateOf(MergeWorkflowStep.SELECT) }

    // Set of selected document IDs (preserves selection across screens & imports)
    val selectedDocIds = remember { mutableStateListOf<Long>() }

    // Pre-select initialDocId if provided
    LaunchedEffect(initialDocId) {
        if (initialDocId != null && !selectedDocIds.contains(initialDocId)) {
            selectedDocIds.add(initialDocId)
        }
    }

    // Ordered list of selected documents for Screen 3
    val orderedSelectedDocs = remember { mutableStateListOf<DocumentEntity>() }

    // Keep originals checkbox state on Screen 3 (default: checked)
    var keepOriginals by remember { mutableStateOf(true) }

    // Merging progress state
    var isMerging by remember { mutableStateOf(false) }

    // System File Picker for "Create or Import -> Device" (PDF, JPG, PNG)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isMerging = true
                val newDocs = viewModel.importFilesForMerge(uris)
                isMerging = false
                if (newDocs.isNotEmpty()) {
                    newDocs.forEach { doc ->
                        if (!selectedDocIds.contains(doc.id)) {
                            selectedDocIds.add(doc.id)
                        }
                    }
                    Toast.makeText(context, "Imported ${newDocs.size} file(s)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not import selected files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Synchronize orderedSelectedDocs when transitioning or selecting
    fun syncOrderedDocs() {
        val currentDocsMap = allDocuments.associateBy { it.id }
        val currentIds = orderedSelectedDocs.map { it.id }.toSet()

        // Remove unselected
        orderedSelectedDocs.removeAll { !selectedDocIds.contains(it.id) }

        // Add newly selected preserving existing order
        selectedDocIds.forEach { id ->
            if (!currentIds.contains(id)) {
                currentDocsMap[id]?.let { orderedSelectedDocs.add(it) }
            }
        }
    }

    // Handle system back navigation
    BackHandler {
        if (currentStep == MergeWorkflowStep.REORDER) {
            currentStep = MergeWorkflowStep.SELECT
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MergeDarkBg)
    ) {
        when (currentStep) {
            MergeWorkflowStep.SELECT -> {
                MergeSelectionScreenContent(
                    allDocuments = allDocuments,
                    selectedDocIds = selectedDocIds,
                    onToggleSelection = { docId ->
                        if (selectedDocIds.contains(docId)) {
                            selectedDocIds.remove(docId)
                        } else {
                            selectedDocIds.add(docId)
                        }
                    },
                    onDeviceImportClick = {
                        filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                    },
                    onNavigateBack = onNavigateBack,
                    onProceedToReorder = {
                        syncOrderedDocs()
                        currentStep = MergeWorkflowStep.REORDER
                    }
                )
            }

            MergeWorkflowStep.REORDER -> {
                MergeReorderScreenContent(
                    selectedDocs = orderedSelectedDocs,
                    keepOriginals = keepOriginals,
                    onKeepOriginalsChanged = { keepOriginals = it },
                    onReorder = { fromIndex, toIndex ->
                        if (fromIndex in 0 until orderedSelectedDocs.size &&
                            toIndex in 0 until orderedSelectedDocs.size
                        ) {
                            val item = orderedSelectedDocs.removeAt(fromIndex)
                            orderedSelectedDocs.add(toIndex, item)
                        }
                    },
                    onAddFileClick = {
                        currentStep = MergeWorkflowStep.SELECT
                    },
                    onMergeClick = {
                        if (orderedSelectedDocs.size < 2) {
                            Toast.makeText(context, "Select at least 2 documents to merge", Toast.LENGTH_SHORT).show()
                            return@MergeReorderScreenContent
                        }
                        isMerging = true
                        viewModel.mergeDocumentsWithCustomOrder(
                            orderedDocuments = orderedSelectedDocs.toList(),
                            newTitle = "",
                            keepOriginals = keepOriginals,
                            onSuccess = { newDocId ->
                                isMerging = false
                                onMergeCompleted(newDocId)
                            },
                            onError = { errorMsg ->
                                isMerging = false
                                Toast.makeText(context, "Merge error: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onNavigateBack = {
                        currentStep = MergeWorkflowStep.SELECT
                    }
                )
            }
        }

        // Merging Progress Overlay
        if (isMerging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .zIndex(99f)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF222226),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MergeTealAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MergeTealAccent,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Merging documents...",
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Combining pages into a single PDF",
                            color = MergeTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SCREEN 1 & SCREEN 2: MERGE FILES SELECTION CONTENT
// -----------------------------------------------------------------------------
@Composable
private fun MergeSelectionScreenContent(
    allDocuments: List<DocumentEntity>,
    selectedDocIds: List<Long>,
    onToggleSelection: (Long) -> Unit,
    onDeviceImportClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onProceedToReorder: () -> Unit
) {
    val selectedCount = selectedDocIds.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // TOP HERO AREA (Reference Screenshot 1 & 2)
            MergeHeroHeader(
                onNavigateBack = onNavigateBack
            )

            // MAIN SCROLLABLE CONTENT
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = if (selectedCount >= 2) 90.dp else 24.dp
                )
            ) {
                // SECTION: CREATE OR IMPORT
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Create or Import",
                            color = MergeTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // DEVICE BUTTON / CARD (Matching reference image)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MergeCardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { onDeviceImportClick() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E88E5),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Device Storage",
                                            tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Device",
                                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION: SELECT FROM THIS APP
                        Text(
                            text = "Select from This App",
                            color = MergeTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // EMPTY APP DOCUMENTS STATE
                if (allDocuments.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF55555A),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No documents found in DocScanner",
                                color = MergeTextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Device' above to import PDFs or images",
                                color = Color(0xFF66666E),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // DOCUMENT LIST (Matching reference screenshot item layout)
                    itemsIndexed(
                        items = allDocuments,
                        key = { _, doc -> doc.id }
                    ) { _, doc ->
                        val isSelected = selectedDocIds.contains(doc.id)
                        DocumentSelectionRow(
                            document = doc,
                            isSelected = isSelected,
                            onToggleSelection = { onToggleSelection(doc.id) }
                        )
                    }
                }
            }
        }

        // DYNAMIC BOTTOM MERGE BUTTON (SCREEN 2: Merge (2), Merge (3), etc.)
        AnimatedVisibility(
            visible = selectedCount >= 2,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.Transparent
            ) {
                Button(
                    onClick = onProceedToReorder,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MergeTealAccent,
                        contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = MergeTealAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Merge ($selectedCount)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TOP HERO HEADER & ILLUSTRATION (Matching Screenshot 1 & 2)
// -----------------------------------------------------------------------------
@Composable
private fun MergeHeroHeader(
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MergeHeroBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Top Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Hero Content Row: Title/Subtitle on Left, Illustration on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Merge Files",
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Merge files in seconds to save time and effort.",
                        color = MergeTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Custom Modern Document Merge Vector Illustration
                DocumentMergeVectorArt(
                    modifier = Modifier
                        .size(width = 110.dp, height = 90.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentMergeVectorArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Sheet 1 (Back document, slightly rotated/offset)
        val backDocRect = Path().apply {
            moveTo(w * 0.35f, h * 0.05f)
            lineTo(w * 0.92f, h * 0.05f)
            lineTo(w * 0.92f, h * 0.78f)
            lineTo(w * 0.35f, h * 0.78f)
            close()
        }
        drawPath(
            path = backDocRect,
            color = Color(0xFFECEFF1)
        )
        // Back document lines
        for (i in 1..4) {
            drawLine(
                color = Color(0xFFCFD8DC),
                start = Offset(w * 0.42f, h * 0.15f + i * h * 0.11f),
                end = Offset(w * 0.85f, h * 0.15f + i * h * 0.11f),
                strokeWidth = 2.5f
            )
        }

        // Sheet 2 (Front document sheet)
        val frontDocRect = Path().apply {
            moveTo(w * 0.08f, h * 0.20f)
            lineTo(w * 0.65f, h * 0.20f)
            lineTo(w * 0.65f, h * 0.95f)
            lineTo(w * 0.08f, h * 0.95f)
            close()
        }
        drawPath(
            path = frontDocRect,
            color = Color(0xFFFFFFFF)
        )
        // Front doc subtle outline
        drawPath(
            path = frontDocRect,
            color = Color(0xFFE2E8F0),
            style = Stroke(width = 1.5f)
        )

        // Front document content lines
        for (i in 1..5) {
            val lineWidth = if (i == 3) w * 0.35f else w * 0.46f
            drawLine(
                color = Color(0xFFB0BEC5),
                start = Offset(w * 0.15f, h * 0.28f + i * h * 0.10f),
                end = Offset(w * 0.15f + lineWidth, h * 0.28f + i * h * 0.10f),
                strokeWidth = 2.5f
            )
        }

        // Merge Arrow linking documents (Orange/Coral gradient style from reference)
        val arrowCenter = Offset(w * 0.70f, h * 0.50f)
        val arrowPath = Path().apply {
            moveTo(arrowCenter.x - w * 0.16f, arrowCenter.y)
            lineTo(arrowCenter.x + w * 0.08f, arrowCenter.y)
            lineTo(arrowCenter.x + w * 0.02f, arrowCenter.y - h * 0.10f)
            lineTo(arrowCenter.x + w * 0.18f, arrowCenter.y)
            lineTo(arrowCenter.x + w * 0.02f, arrowCenter.y + h * 0.10f)
            lineTo(arrowCenter.x + w * 0.08f, arrowCenter.y)
            close()
        }
        drawPath(
            path = arrowPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFF7043), Color(0xFFFF5252)),
                start = Offset(arrowCenter.x - w * 0.16f, arrowCenter.y),
                end = Offset(arrowCenter.x + w * 0.18f, arrowCenter.y)
            )
        )
    }
}

// -----------------------------------------------------------------------------
// DOCUMENT SELECTION ROW (Matching Screenshot 1 & 2)
// -----------------------------------------------------------------------------
@Composable
private fun DocumentSelectionRow(
    document: DocumentEntity,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    val context = LocalContext.current
    val rowBg = if (isSelected) MergeTealHighlight else Color.Transparent

    // Format date matching the reference screenshot: e.g. "27/08/2026 3:26 pm"
    val dateText = remember(document.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
        sdf.format(Date(document.createdAt)).lowercase()
    }

    Surface(
        color = rowBg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MergeTealAccent)
            ) {
                onToggleSelection()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Thumbnail
            DocumentThumbnailBox(
                thumbnailPath = document.thumbnailPath,
                modifier = Modifier.size(width = 56.dp, height = 72.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        color = MergeTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = " | ",
                        color = Color(0xFF55555A),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "📄 ${document.pageCount}",
                        color = MergeTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Checkbox (Custom styled matching reference)
            CustomMergeCheckbox(
                isChecked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// DOCUMENT THUMBNAIL COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun DocumentThumbnailBox(
    thumbnailPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = remember(thumbnailPath) { if (thumbnailPath.isNotBlank()) File(thumbnailPath) else null }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF26262A))
            .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (file != null && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = "Document Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF777780),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// CUSTOM CHECKBOX (Matching reference screenshot square style)
// -----------------------------------------------------------------------------
@Composable
private fun CustomMergeCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isChecked) MergeTealAccent else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (isChecked) MergeTealAccent else Color(0xFF55555A),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onCheckedChange(!isChecked) },
        contentAlignment = Alignment.Center
    ) {
        if (isChecked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// SCREEN 3: SELECTED DOCUMENTS REORDER CONTENT (Matching Screenshot 3)
// -----------------------------------------------------------------------------
@Composable
private fun MergeReorderScreenContent(
    selectedDocs: List<DocumentEntity>,
    keepOriginals: Boolean,
    onKeepOriginalsChanged: (Boolean) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddFileClick: () -> Unit,
    onMergeClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val density = LocalDensity.current

    // Drag and Drop state
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // TOP APP BAR (Back, "Merge Documents", Checklist icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Merge Documents",
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = { /* Action icon matching reference */ }) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = "Select Action",
                    tint = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // INFORMATION BAR: "ⓘ Press and Drag to Sort" (Matching Screenshot 3)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1B1B1E)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MergeTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Press and Drag to Sort",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // REORDERABLE LIST OF SELECTED DOCUMENTS
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            itemsIndexed(
                items = selectedDocs,
                key = { _, doc -> doc.id }
            ) { index, doc ->
                val isCurrentlyDragging = draggingIndex == index
                val offsetTranslationY = if (isCurrentlyDragging) dragDeltaY else 0f

                ReorderDocumentRow(
                    document = doc,
                    isDragging = isCurrentlyDragging,
                    offsetY = offsetTranslationY,
                    onItemMeasured = { hPx ->
                        if (hPx > 0) itemHeightPx = hPx
                    },
                    onDragStart = {
                        draggingIndex = index
                        dragDeltaY = 0f
                    },
                    onDrag = { amountY ->
                        dragDeltaY += amountY
                        if (itemHeightPx > 0f) {
                            val current = draggingIndex ?: return@ReorderDocumentRow
                            val shiftRows = (dragDeltaY / itemHeightPx).let {
                                if (it >= 0) kotlin.math.floor(it) else kotlin.math.ceil(it)
                            }.toInt()

                            if (shiftRows != 0) {
                                val target = (current + shiftRows).coerceIn(0, selectedDocs.size - 1)
                                if (target != current) {
                                    onReorder(current, target)
                                    draggingIndex = target
                                    dragDeltaY -= shiftRows * itemHeightPx
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingIndex = null
                        dragDeltaY = 0f
                    },
                    onDragCancel = {
                        draggingIndex = null
                        dragDeltaY = 0f
                    }
                )
            }
        }

        // BOTTOM OPTIONS & BUTTONS (Matching Screenshot 3)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MergeDarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // "☑ Keep originals" CHECKBOX ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeepOriginalsChanged(!keepOriginals) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomMergeCheckbox(
                        isChecked = keepOriginals,
                        onCheckedChange = onKeepOriginalsChanged
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Keep originals",
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TWO SIDE-BY-SIDE BUTTONS ("Add File" and "Merge Documents")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LEFT BUTTON: Add File
                    Button(
                        onClick = onAddFileClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38383C),
                            contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Add File",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // RIGHT BUTTON: Merge Documents (Primary Action)
                    Button(
                        onClick = onMergeClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MergeTealAccent,
                            contentColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .shadow(6.dp, RoundedCornerShape(10.dp), spotColor = MergeTealAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Merge Documents",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// REORDERABLE DOCUMENT ROW (Matching Screenshot 3 with Drag Handle)
// -----------------------------------------------------------------------------
@Composable
private fun ReorderDocumentRow(
    document: DocumentEntity,
    isDragging: Boolean,
    offsetY: Float,
    onItemMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val dateText = remember(document.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(document.createdAt))
    }

    Surface(
        color = if (isDragging) Color(0xFF222228) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onItemMeasured(coordinates.size.height.toFloat())
            }
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .zIndex(if (isDragging) 10f else 1f)
            .shadow(if (isDragging) 8.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            DocumentThumbnailBox(
                thumbnailPath = document.thumbnailPath,
                modifier = Modifier.size(width = 56.dp, height = 72.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Info (Title and Date | Page count)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        color = MergeTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = " | ",
                        color = Color(0xFF55555A),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "📄 ${document.pageCount}",
                        color = MergeTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // DRAG HANDLE (☰)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .pointerInput(document.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Drag to sort",
                    tint = if (isDragging) MergeTealAccent else Color(0xFFAAAAAE),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
