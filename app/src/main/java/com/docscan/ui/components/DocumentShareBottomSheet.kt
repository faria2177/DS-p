package com.docscan.ui.components

import com.docscan.util.ThemeManager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.util.FileUtils
import com.docscan.util.DocxExporter
import com.docscan.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SheetBgColor: Color get() = ThemeManager.themeColor(Color(0xFF1E1E22), Color(0xFFFFFFFF))
private val CardBorderColor: Color get() = ThemeManager.themeColor(Color(0xFF2E323B), Color(0xFFE2E8F0))
private val TealAccent = Color(0xFF00BFA5)
private val TextWhite: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextMuted: Color get() = ThemeManager.themeColor(Color(0xFF94A3B8), Color(0xFF475569))
private val ButtonPillBg: Color get() = ThemeManager.themeColor(Color(0xFF282B32), Color(0xFFF1F5F9))
private val ButtonPillBorder: Color get() = ThemeManager.themeColor(Color(0xFF3F4450), Color(0xFFE2E8F0))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentShareBottomSheet(
    document: DocumentEntity,
    pages: List<PageEntity>,
    onDismiss: () -> Unit,
    onNavigateToCompress: (Long) -> Unit,
    onNavigateToToWord: (Long) -> Unit,
    onNavigateToPdfToLongImage: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Selection tracking for each page thumbnail
    val selectedPageIds = remember(pages) {
        mutableStateListOf<Long>().apply {
            addAll(pages.map { it.id })
        }
    }

    // Active Tab: 0 = Share File, 1 = Share Link
    var selectedTab by remember { mutableIntStateOf(0) }

    // Loading / Processing states
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    // Stitched Long Image state
    var stitchedLongImageFile by remember { mutableStateOf<File?>(null) }
    var showLongImagePreviewDialog by remember { mutableStateOf(false) }

    // Calculate total size of selected pages
    val selectedPages = pages.filter { selectedPageIds.contains(it.id) }
    val totalEstimatedBytes = remember(selectedPages) {
        selectedPages.sumOf { page ->
            val path = page.processedImagePath.ifBlank { page.originalImagePath }
            val f = File(path)
            if (f.exists()) f.length() else 0L
        }
    }
    // Flag if file size is 4MB or greater (or user has 3+ high-res pages)
    val isOver4Mb = totalEstimatedBytes >= 4 * 1024 * 1024L || selectedPages.size >= 4

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBgColor,
        scrimColor = Color(0x99000000),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // ==================== TOP HEADER ====================
            // "${selectedCount} image(s) selected" & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedPageIds.size} image(s) selected",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2C2F36),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ==================== TABS: SHARE FILE | SHARE LINK ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Tab 0: Share File
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = 0 }
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Share File",
                        color = if (selectedTab == 0) TealAccent else TextMuted,
                        fontSize = 15.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(3.dp)
                                .background(TealAccent, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                // Tab 1: Share Link
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = 1 }
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Share Link",
                        color = if (selectedTab == 1) TealAccent else TextMuted,
                        fontSize = 15.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (selectedTab == 1) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(3.dp)
                                .background(TealAccent, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 1) {
                // ==================== SHARE LINK VIEW ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262930),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Document: ${document.title}",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(document.createdAt))
                            Text(
                                text = "${pages.size} pages • $formattedDate",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val shareSummary = "Document: ${document.title} (${pages.size} pages)\nScanned with DocScanner"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareSummary)
                                putExtra(Intent.EXTRA_SUBJECT, document.title)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Document Info"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Document Summary", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ==================== HORIZONTAL PAGE THUMBNAILS ====================
                // Displays each page with a checkmark toggle on the top-right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    pages.forEachIndexed { index, page ->
                        val isSelected = selectedPageIds.contains(page.id)
                        val imagePath = page.processedImagePath.ifBlank { page.originalImagePath }

                        Box(
                            modifier = Modifier
                                .width(135.dp)
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) TealAccent else Color(0xFF4B5563)
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        if (selectedPageIds.size > 1) {
                                            selectedPageIds.remove(page.id)
                                        } else {
                                            Toast.makeText(context, "At least 1 page must be selected", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        selectedPageIds.add(page.id)
                                    }
                                }
                        ) {
                            // Page Preview Image
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(imagePath))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top-Right Checkmark Selection Circle (Teal / Emerald matching reference image)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) TealAccent else Color(0x80000000))
                                    .border(
                                        BorderStroke(
                                            1.5.dp,
                                            if (isSelected) Color.White else Color(0xFFCBD5E1)
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==================== LIST OF SHARE OPTIONS ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Option 1: Share as PDF (with Compress button if >= 4MB or on demand)
                    ShareOptionItem(
                        icon = {
                            PdfBadgeIcon()
                        },
                        title = "Share as PDF",
                        subActionLabel = "Compress",
                        // Always show or highlight when file size is large or >= 4MB
                        showSubAction = true,
                        onRowClick = {
                            if (selectedPages.isEmpty()) {
                                Toast.makeText(context, "Please select at least 1 page", Toast.LENGTH_SHORT).show()
                                return@ShareOptionItem
                            }
                            isProcessing = true
                            processingMessage = "Generating PDF..."
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val pdfFile = PdfExporter.generatePdf(
                                        context = context,
                                        documentTitle = document.title,
                                        pages = selectedPages
                                    )
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (pdfFile != null && pdfFile.exists()) {
                                            onDismiss()
                                            sharePdfFileNative(context, pdfFile, document.title)
                                        } else {
                                            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onSubActionClick = {
                            onDismiss()
                            onNavigateToCompress(document.id)
                        }
                    )

                    // Option 2: Share as Word
                    ShareOptionItem(
                        icon = {
                            WordBadgeIcon()
                        },
                        title = "Share as Word",
                        showSubAction = false,
                        onRowClick = {
                            if (isProcessing) return@ShareOptionItem
                            if (selectedPages.isEmpty()) {
                                Toast.makeText(context, "Select at least one page", Toast.LENGTH_SHORT).show()
                                return@ShareOptionItem
                            }
                            val pagesToShare = selectedPages.toList()
                            isProcessing = true
                            processingMessage = "Preparing Word document..."
                            scope.launch {
                                val wordFile = try {
                                    DocxExporter.generateDocx(
                                        context = context,
                                        documentTitle = document.title,
                                        pages = pagesToShare,
                                        config = DocxExporter.DocxExportConfig(
                                            title = document.title,
                                            layoutMode = "Exact Layout",
                                            imageHandling = "Preserve Images",
                                            includeFormattedText = true
                                        )
                                    )
                                } catch (_: Exception) { null }
                                isProcessing = false
                                if (wordFile != null && wordFile.exists() && wordFile.length() > 0L) {
                                    onDismiss()
                                    DocxExporter.shareDocx(context, wordFile)
                                } else {
                                    Toast.makeText(context, "Unable to create Word document", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )

                    // Option 3: Share as Long Image
                    ShareOptionItem(
                        icon = {
                            LongImageBadgeIcon()
                        },
                        title = "Share as Long Image",
                        showSubAction = false,
                        onRowClick = {
                            if (selectedPages.isEmpty()) {
                                Toast.makeText(context, "Please select at least 1 page", Toast.LENGTH_SHORT).show()
                                return@ShareOptionItem
                            }
                            isProcessing = true
                            processingMessage = "Stitching ${selectedPages.size} pages into long image..."
                            scope.launch(Dispatchers.IO) {
                                val stitchedFile = stitchPagesDirectly(context, document.title, selectedPages)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (stitchedFile != null && stitchedFile.exists()) {
                                        stitchedLongImageFile = stitchedFile
                                        showLongImagePreviewDialog = true
                                    } else {
                                        Toast.makeText(context, "Failed to create long image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    // Option 4: Share as Images (with Compress button)
                    ShareOptionItem(
                        icon = {
                            ImagesBadgeIcon()
                        },
                        title = "Share as Images",
                        subActionLabel = "Compress",
                        showSubAction = true,
                        onRowClick = {
                            if (selectedPages.isEmpty()) {
                                Toast.makeText(context, "Please select at least 1 page", Toast.LENGTH_SHORT).show()
                                return@ShareOptionItem
                            }
                            val imagePaths = selectedPages.map { it.processedImagePath.ifBlank { it.originalImagePath } }
                            onDismiss()
                            PdfExporter.shareImages(context, imagePaths, document.title)
                        },
                        onSubActionClick = {
                            onDismiss()
                            onNavigateToCompress(document.id)
                        }
                    )
                }
            }
        }
    }

    // ==================== PROCESSING OVERLAY ====================
    if (isProcessing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SheetBgColor,
                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f)),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = TealAccent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = processingMessage,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // ==================== STITCHED LONG IMAGE PREVIEW DIALOG ====================
    // Displays continuous image with a Share button at the bottom for Gmail, WhatsApp, etc.
    if (showLongImagePreviewDialog && stitchedLongImageFile != null) {
        LongImagePreviewDialog(
            stitchedFile = stitchedLongImageFile!!,
            documentTitle = document.title,
            onDismiss = {
                showLongImagePreviewDialog = false
                stitchedLongImageFile = null
            },
            onShare = {
                FileUtils.shareFile(context, stitchedLongImageFile!!, "image/jpeg", "Share Long Image")
            },
            onSaveToGallery = {
                val bmp = BitmapFactory.decodeFile(stitchedLongImageFile!!.absolutePath)
                if (bmp != null) {
                    FileUtils.saveBitmapToGallery(context, bmp, "${document.title}_LongImage")
                    Toast.makeText(context, "Saved to Gallery / Photos!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ==================== HELPER COMPONENT: SHARE OPTION ITEM ====================
@Composable
private fun ShareOptionItem(
    icon: @Composable () -> Unit,
    title: String,
    subActionLabel: String? = null,
    showSubAction: Boolean = false,
    onRowClick: () -> Unit,
    onSubActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon Badge
            icon()

            Spacer(modifier = Modifier.width(16.dp))

            // Title Text
            Text(
                text = title,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Sub-Action Button (e.g. [Compress] button pill)
        if (showSubAction && subActionLabel != null && onSubActionClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.padding(start = 52.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ButtonPillBg,
                    border = BorderStroke(1.dp, ButtonPillBorder),
                    modifier = Modifier
                        .clickable { onSubActionClick() }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = subActionLabel,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ==================== BADGE ICONS MATCHING SCREENSHOT ====================
@Composable
private fun PdfBadgeIcon() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF232832),
        border = BorderStroke(1.dp, Color(0xFF3F4654)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "PDF",
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WordBadgeIcon() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF1E2C4A),
        border = BorderStroke(1.dp, Color(0xFF2B4375)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "W",
                color = Color(0xFF60A5FA),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun LongImageBadgeIcon() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF242B28),
        border = BorderStroke(1.dp, Color(0xFF334A3E)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.PhotoSizeSelectActual,
                contentDescription = null,
                tint = Color(0xFF34D399),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ImagesBadgeIcon() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF2F2633),
        border = BorderStroke(1.dp, Color(0xFF513859)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = null,
                tint = Color(0xFFF472B6),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== STITCH PAGES TO CONTINUOUS LONG IMAGE ====================
private suspend fun stitchPagesDirectly(
    context: Context,
    title: String,
    pages: List<PageEntity>
): File? = withContext(Dispatchers.IO) {
    try {
        if (pages.isEmpty()) return@withContext null

        val loadedBitmaps = mutableListOf<Bitmap>()
        for (page in pages) {
            val path = page.processedImagePath.ifBlank { page.originalImagePath }
            val bmp = FileUtils.loadBitmap(path, maxDimension = 2200)
            if (bmp != null) {
                loadedBitmaps.add(bmp)
            }
        }
        if (loadedBitmaps.isEmpty()) return@withContext null

        val targetWidth = 1400
        val scaledHeights = loadedBitmaps.map { bmp ->
            val scale = targetWidth.toFloat() / bmp.width.toFloat()
            (bmp.height * scale).toInt().coerceAtLeast(1)
        }
        val totalHeight = scaledHeights.sum()

        // Create Master Bitmap
        val masterBitmap = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(masterBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        var currentY = 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (i in loadedBitmaps.indices) {
            val bmp = loadedBitmaps[i]
            val sHeight = scaledHeights[i]
            val destRect = RectF(0f, currentY, targetWidth.toFloat(), currentY + sHeight)
            canvas.drawBitmap(bmp, null, destRect, paint)
            currentY += sHeight
            bmp.recycle()
        }

        val baseClean = title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val outputFile = File(
            FileUtils.getDocumentsDir(context),
            "${baseClean}_LongImage_${System.currentTimeMillis()}.jpg"
        )
        FileOutputStream(outputFile).use { out ->
            masterBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        masterBitmap.recycle()
        outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ==================== LONG IMAGE PREVIEW DIALOG ====================
@Composable
private fun LongImagePreviewDialog(
    stitchedFile: File,
    documentTitle: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SheetBgColor,
            border = BorderStroke(1.dp, CardBorderColor),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Long Image Created",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = documentTitle,
                            color = TextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Panoramic Scrollable View of the Long Image
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, CardBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(stitchedFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Long Image Preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons: Share, Gallery, Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share Button (Opens native share sheet with Gmail, WhatsApp, etc.)
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    // Save to Gallery
                    OutlinedButton(
                        onClick = onSaveToGallery,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TextWhite)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery", color = TextWhite)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = TextMuted)
                }
            }
        }
    }
}

// ==================== NATIVE ANDROID SYSTEM SHARE FOR PDF ====================
private fun sharePdfFileNative(context: Context, pdfFile: File, title: String) {
    try {
        val authority = "${context.packageName}.provider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "$title.pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
