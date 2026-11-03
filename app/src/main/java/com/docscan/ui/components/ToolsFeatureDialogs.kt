package com.docscan.ui.components

import com.docscan.util.ThemeManager

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.util.AiOrchestrator
import com.docscan.util.FileUtils
import com.docscan.util.PageSize
import com.docscan.util.PdfExportConfig
import com.docscan.util.PdfExporter
import com.docscan.util.TextToPdfOptions
import com.docscan.util.TextToPdfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val DarkDialogBg: Color get() = ThemeManager.themeColor(Color(0xFF242426), Color(0xFFFFFFFF))
private val DarkCardBg: Color get() = ThemeManager.themeColor(Color(0xFF2F2F33), Color(0xFFF1F5F9))
private val OrangeAccent = Color(0xFFFFA000)
private val TealAccent = Color(0xFF00BFA5)
private val DarkTextColor: Color get() = ThemeManager.themeColor(Color(0xFFE2E8F0), Color(0xFF0F172A))
private val DarkTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9E9E9E), Color(0xFF475569))

// ==================== MODERN IMAGE RESIZER & OPTIMIZER STUDIO DIALOG ====================
@Composable
fun ImageResizerDialog(
    initialBitmap: Bitmap? = null,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf(initialBitmap) }

    // 1. Pixels: Width & Height in px
    var widthInput by remember { mutableStateOf("1080") }
    var heightInput by remember { mutableStateOf("1080") }
    var isAspectRatioLocked by remember { mutableStateOf(true) }
    var activePresetName by remember { mutableStateOf("Original") }
    var sizeMode by remember { mutableStateOf("Presets") } // "Presets" or "Custom"

    // 2. Target File Size (KB)
    var isTargetSizeEnabled by remember { mutableStateOf(false) }
    var targetSizeKbInput by remember { mutableStateOf("100") }

    // 3. Output Format
    var selectedFormat by remember { mutableStateOf("JPEG") } // "JPEG", "PNG", "WEBP"

    // 4. Quality
    var quality by remember { mutableFloatStateOf(90f) }

    // Processing status
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val bmp = FileUtils.loadBitmapsFromUri(context, it).firstOrNull()
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        selectedBitmap = bmp
                        widthInput = bmp.width.toString()
                        heightInput = bmp.height.toString()
                        activePresetName = "Original"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (selectedBitmap == null) {
            galleryLauncher.launch("image/*")
        } else {
            selectedBitmap?.let {
                widthInput = it.width.toString()
                heightInput = it.height.toString()
            }
        }
    }

    val sourceWidth = remember(selectedBitmap) { selectedBitmap?.width ?: 1 }
    val sourceHeight = remember(selectedBitmap) { selectedBitmap?.height ?: 1 }
    val sourceAspectRatio = remember(sourceWidth, sourceHeight) {
        if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight else 1f
    }

    // Calculate target width and height in pixels
    fun calculateTargetPixels(): Pair<Int, Int> {
        val bmp = selectedBitmap ?: return Pair(1080, 1080)
        val rawW = widthInput.toIntOrNull() ?: bmp.width
        val rawH = heightInput.toIntOrNull() ?: bmp.height
        return Pair(rawW.coerceIn(16, 8000), rawH.coerceIn(16, 8000))
    }

    // Execute standard scaling resize
    fun executeResize(): Bitmap? {
        val bmp = selectedBitmap ?: return null
        val (targetW, targetH) = calculateTargetPixels()
        return Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
    }

    // Execute compression to strictly meet target KB/MB
    fun executeCompressToSize(targetBytes: Long, format: Bitmap.CompressFormat): Bitmap {
        val baseResized = executeResize() ?: selectedBitmap!!
        var currentBitmap = baseResized
        val q = quality.toInt().coerceIn(10, 100)
        val baos = java.io.ByteArrayOutputStream()
        currentBitmap.compress(format, q, baos)

        if (baos.size() <= targetBytes) {
            return currentBitmap
        }

        // Binary search quality adjustment
        var lowQ = 10
        var highQ = q
        var bestBaos = baos

        while (lowQ <= highQ) {
            val midQ = (lowQ + highQ) / 2
            val testBaos = java.io.ByteArrayOutputStream()
            currentBitmap.compress(format, midQ, testBaos)
            if (testBaos.size() <= targetBytes) {
                bestBaos = testBaos
                lowQ = midQ + 1
            } else {
                highQ = midQ - 1
            }
        }

        // If still too large, downscale resolution smoothly
        if (bestBaos.size() > targetBytes && format != Bitmap.CompressFormat.PNG) {
            var scale = 0.85f
            while (scale >= 0.2f) {
                val newW = (currentBitmap.width * scale).toInt().coerceAtLeast(80)
                val newH = (currentBitmap.height * scale).toInt().coerceAtLeast(80)
                val downscaled = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                val testBaos = java.io.ByteArrayOutputStream()
                downscaled.compress(format, 80, testBaos)
                if (testBaos.size() <= targetBytes) {
                    return downscaled
                }
                scale -= 0.15f
            }
        }

        return currentBitmap
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF18181B),
            border = BorderStroke(1.dp, Color(0xFF27272A)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = TealAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Crop, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Image Resizer", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Quick, one-tap resizing", color = Color(0xFFA1A1AA), fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFA1A1AA))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Image Preview Card with Live Resolution Info
                if (selectedBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF09090B),
                        border = BorderStroke(1.dp, Color(0xFF27272A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemeManager.themeColor(Color(0xFF121214), Color(0xFFF8FAFC))),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = selectedBitmap!!.asImageBitmap(),
                                    contentDescription = "Selected Image",
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Current Resolution Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        "Original: ${selectedBitmap!!.width} × ${selectedBitmap!!.height} px",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Target Preview Badge
                                val (tW, tH) = calculateTargetPixels()
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TealAccent.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        if (isTargetSizeEnabled) "➔ $tW × $tH px (< $targetSizeKbInput KB)" else "➔ $tW × $tH px ($selectedFormat)",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TealAccent, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change Image", color = TealAccent, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TealAccent)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TealAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Image from Gallery", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Clean Scrollable Settings Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  // CARD 1: SIZE
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1C1C1F),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2E)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                   Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = TealAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Size", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThemeManager.themeColor(Color(0xFF09090B), Color(0xFFF1F5F9)))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("Presets", "Custom").forEach { mode ->
                            val isSelected = sizeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TealAccent else Color.Transparent)
                                    .clickable {
                                        sizeMode = mode
                                        if (mode == "Custom") activePresetName = "Custom"
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mode,
                                    color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (sizeMode == "Presets") {
                        val quickPresets = listOf(
                            Triple("Original", sourceWidth, sourceHeight),
                            Triple("Passport (413×531)", 413, 531),
                            Triple("Square 1:1", 1080, 1080),
                            Triple("Story 9:16", 1080, 1920),
                            Triple("Full HD", 1920, 1080),
                            Triple("A4 Print", 2480, 3508),
                            Triple("Half Size", (sourceWidth * 0.5f).toInt().coerceAtLeast(64), (sourceHeight * 0.5f).toInt().coerceAtLeast(64))
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickPresets) { (name, w, h) ->
                                val isSelected = activePresetName == name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        activePresetName = name
                                        widthInput = w.toString()
                                        heightInput = h.toString()
                                        if (name.contains("Passport")) {
                                            isTargetSizeEnabled = true
                                            targetSizeKbInput = "100"
                                        }
                                    },
                                    label = { Text(name, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TealAccent,
                                        selectedLabelColor = Color.White,
                                        labelColor = Color(0xFFE4E4E7)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Output: $widthInput × $heightInput px",
                            color = Color(0xFF9E9E9E),
                            fontSize = 10.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = widthInput,
                                onValueChange = { newVal ->
                                    widthInput = newVal.filter { it.isDigit() }
                                    activePresetName = "Custom"
                                    if (isAspectRatioLocked) {
                                        val w = widthInput.toFloatOrNull()
                                        if (w != null && sourceAspectRatio > 0) {
                                            heightInput = (w / sourceAspectRatio).toInt().toString()
                                        }
                                    }
                                },
                                label = { Text("Width (px)", color = Color(0xFFA1A1AA), fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    focusedBorderColor = TealAccent,
                                    unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Aspect Ratio Lock
                            Surface(
                                shape = CircleShape,
                                color = if (isAspectRatioLocked) TealAccent.copy(alpha = 0.2f) else Color(0xFF27272A),
                                border = BorderStroke(1.dp, if (isAspectRatioLocked) TealAccent else Color(0xFF3F3F46)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { isAspectRatioLocked = !isAspectRatioLocked }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isAspectRatioLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Aspect Ratio",
                                        tint = if (isAspectRatioLocked) TealAccent else Color(0xFFA1A1AA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { newVal ->
                                    heightInput = newVal.filter { it.isDigit() }
                                    activePresetName = "Custom"
                                    if (isAspectRatioLocked) {
                                        val h = heightInput.toFloatOrNull()
                                        if (h != null && sourceAspectRatio > 0) {
                                            widthInput = (h * sourceAspectRatio).toInt().toString()
                                        }
                                    }
                                },
                                label = { Text("Height (px)", color = Color(0xFFA1A1AA), fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    focusedBorderColor = TealAccent,
                                    unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                   }
                  }

                  // CARD 2: OUTPUT (Target Size, Format, Quality)
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1C1C1F),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2E)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                   Column(modifier = Modifier.padding(12.dp)) {
                    // TARGET FILE SIZE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Compress, contentDescription = null, tint = TealAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Target File Size", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("For job applications / government forms", color = Color(0xFFA1A1AA), fontSize = 9.sp, modifier = Modifier.padding(start = 20.dp))
                        }
                        Switch(
                            checked = isTargetSizeEnabled,
                            onCheckedChange = { isTargetSizeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TealAccent
                            )
                        )
                    }

                    if (isTargetSizeEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(50, 100, 200, 500, 1024)) { kb ->
                                val label = if (kb >= 1024) "< ${kb / 1024} MB" else "< $kb KB"
                                FilterChip(
                                    selected = targetSizeKbInput == kb.toString(),
                                    onClick = { targetSizeKbInput = kb.toString() },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TealAccent,
                                        selectedLabelColor = Color.White,
                                        labelColor = Color(0xFFE4E4E7)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = targetSizeKbInput,
                            onValueChange = { targetSizeKbInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Custom size (KB)", color = Color(0xFFA1A1AA), fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF2A2A2E), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // FORMAT — same simple pill style as Size toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = TealAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Format", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThemeManager.themeColor(Color(0xFF09090B), Color(0xFFF1F5F9)))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("JPEG", "PNG", "WEBP").forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TealAccent else Color.Transparent)
                                    .clickable { selectedFormat = fmt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    fmt,
                                    color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // QUALITY
                    if (selectedFormat != "PNG") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quality", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${quality.toInt()}% (${if (quality >= 85) "High" else if (quality >= 50) "Medium" else "Low"})", color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = quality,
                            onValueChange = { quality = it },
                            valueRange = 10f..100f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = TealAccent,
                                activeTrackColor = TealAccent
                            )
                        )
                    }
                   }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons (Gallery, Share, Save)
                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(statusMessage.ifBlank { "Processing..." }, color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Gallery Button
                        OutlinedButton(
                            onClick = {
                                val bmp = selectedBitmap ?: return@OutlinedButton
                                isProcessing = true
                                statusMessage = "Saving to Gallery..."
                                scope.launch(Dispatchers.IO) {
                                    val compressFormat = when (selectedFormat) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> Bitmap.CompressFormat.WEBP
                                        else -> Bitmap.CompressFormat.JPEG
                                    }

                                    val finalBmp = if (isTargetSizeEnabled) {
                                        val targetKb = targetSizeKbInput.toIntOrNull() ?: 100
                                        executeCompressToSize(targetKb * 1024L, compressFormat)
                                    } else {
                                        executeResize() ?: bmp
                                    }

                                    val uri = FileUtils.saveBitmapToGallery(context, finalBmp, "RESIZED_${System.currentTimeMillis()}")
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (uri != null) {
                                            Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Failed to save to gallery", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = selectedBitmap != null && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TealAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery", color = Color.White, fontSize = 12.sp)
                        }

                        // Share Button
                        OutlinedButton(
                            onClick = {
                                val bmp = selectedBitmap ?: return@OutlinedButton
                                isProcessing = true
                                statusMessage = "Preparing image..."
                                scope.launch(Dispatchers.IO) {
                                    val compressFormat = when (selectedFormat) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> Bitmap.CompressFormat.WEBP
                                        else -> Bitmap.CompressFormat.JPEG
                                    }

                                    val finalBmp = if (isTargetSizeEnabled) {
                                        val targetKb = targetSizeKbInput.toIntOrNull() ?: 100
                                        executeCompressToSize(targetKb * 1024L, compressFormat)
                                    } else {
                                        executeResize() ?: bmp
                                    }

                                    val tempPath = FileUtils.saveBitmapToTemp(context, finalBmp, "SHARE_RESIZED")
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        FileUtils.shareImageFiles(context, listOf(File(tempPath)), "Resized Image")
                                    }
                                }
                            },
                            enabled = selectedBitmap != null && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp)
                        }

                        // Primary Save Button
                        Button(
                            onClick = {
                                val bmp = selectedBitmap ?: return@Button
                                isProcessing = true
                                statusMessage = "Saving image..."
                                scope.launch(Dispatchers.IO) {
                                    val compressFormat = when (selectedFormat) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> Bitmap.CompressFormat.WEBP
                                        else -> Bitmap.CompressFormat.JPEG
                                    }

                                    val finalBmp = if (isTargetSizeEnabled) {
                                        val targetKb = targetSizeKbInput.toIntOrNull() ?: 100
                                        executeCompressToSize(targetKb * 1024L, compressFormat)
                                    } else {
                                        executeResize() ?: bmp
                                    }

                                    val savedPath = FileUtils.saveBitmapToDocStorage(context, finalBmp, "RESIZED")
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        Toast.makeText(context, "Resized: ${finalBmp.width}×${finalBmp.height} px", Toast.LENGTH_SHORT).show()
                                        onSaved(savedPath)
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = selectedBitmap != null && !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==================== MODERN TEXT TO PDF STUDIO DIALOG ====================
@Composable
fun TextToPdfDialog(
    onDismiss: () -> Unit,
    onPdfCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Content State
    var docTitle by remember { mutableStateOf("Document Notes") }
    var authorName by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }

    // Tab State: 0 = Content & AI, 1 = Layout & Style, 2 = Header, Footer & Watermark
    var selectedTab by remember { mutableIntStateOf(0) }

    // Styling Options State
    var selectedPageSize by remember { mutableStateOf(PageSize.A4) }
    var isLandscape by remember { mutableStateOf(false) }
    var selectedFontFamily by remember { mutableStateOf("SansSerif") }
    var fontSize by remember { mutableFloatStateOf(13f) }
    var lineSpacingMultiplier by remember { mutableFloatStateOf(1.35f) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var textAlign by remember { mutableStateOf("Left") }
    var selectedTheme by remember { mutableStateOf(TextToPdfTheme.CLASSIC) }
    var marginSize by remember { mutableFloatStateOf(40f) }

    // Header, Footer & Watermark State
    var includeHeader by remember { mutableStateOf(true) }
    var headerText by remember { mutableStateOf("") }
    var includeDate by remember { mutableStateOf(true) }
    var includeFooter by remember { mutableStateOf(true) }
    var includePageNumbers by remember { mutableStateOf(true) }
    var customFooterText by remember { mutableStateOf("") }
    var watermarkText by remember { mutableStateOf("") }
    var showPageBorder by remember { mutableStateOf(false) }

    // Loading States
    var isGenerating by remember { mutableStateOf(false) }
    var isAiProcessing by remember { mutableStateOf(false) }
    var aiStatusMessage by remember { mutableStateOf("") }

    // File Picker for importing .txt
    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val importedText = stream.bufferedReader().use { r -> r.readText() }
                        withContext(Dispatchers.Main) {
                            textContent = if (textContent.isBlank()) importedText else "$textContent\n\n$importedText"
                            Toast.makeText(context, "Text file imported successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read text file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Word and character count stats
    val wordCount = remember(textContent) {
        if (textContent.isBlank()) 0 else textContent.trim().split(Regex("\\s+")).size
    }
    val charCount = remember(textContent) { textContent.length }
    val estimatedPages = remember(textContent, fontSize, lineSpacingMultiplier, selectedPageSize, isLandscape) {
        val charsPerPage = if (isLandscape) 1800 else 2200
        val base = ((charCount / charsPerPage) + 1).coerceAtLeast(1)
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkDialogBg,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0EA5E9).copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Text to PDF Studio", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Professional Multi-Page PDF Creator", color = DarkTextSecondary, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkCardBg,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF0EA5E9),
                            height = 3.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Content & AI", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Layout & Style", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Headers & More", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        // TAB 0: CONTENT & AI ASSISTANTS
                        0 -> {
                            // Document Title & Author
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = docTitle,
                                    onValueChange = { docTitle = it },
                                    label = { Text("Document Title", color = DarkTextSecondary, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        focusedBorderColor = Color(0xFF0EA5E9),
                                        unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                    ),
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = authorName,
                                    onValueChange = { authorName = it },
                                    label = { Text("Author / Note", color = DarkTextSecondary, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        focusedBorderColor = Color(0xFF0EA5E9),
                                        unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                    ),
                                    modifier = Modifier.weight(0.8f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Action Buttons (Paste, Import .txt, Clear, Templates)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Paste from Clipboard
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkCardBg,
                                        border = BorderStroke(1.dp, ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))),
                                        modifier = Modifier.clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                textContent = if (textContent.isBlank()) clip else "$textContent\n\n$clip"
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Paste", color = Color.White, fontSize = 11.sp)
                                        }
                                    }

                                    // Import .TXT File
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkCardBg,
                                        border = BorderStroke(1.dp, ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))),
                                        modifier = Modifier.clickable {
                                            txtFileLauncher.launch("text/plain")
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import .txt", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }

                                if (textContent.isNotEmpty()) {
                                    TextButton(
                                        onClick = { textContent = "" },
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Clear", color = Color(0xFFEF4444), fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Main Text Editor Area
                            OutlinedTextField(
                                value = textContent,
                                onValueChange = { textContent = it },
                                label = { Text("Write, paste notes or use Markdown (# Headings, • Bullets, --- Lines)...", color = DarkTextSecondary, fontSize = 11.sp) },
                                minLines = 7,
                                maxLines = 11,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Word & Character count counter row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "$wordCount words  •  $charCount characters  •  ~$estimatedPages page(s)",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text("Markdown Enabled", color = Color(0xFF0EA5E9), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Multi-Model AI Enhancer Toolbar
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("AI Document Assistant", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("(Google Gemini)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isAiProcessing) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(aiStatusMessage.ifBlank { "AI is enhancing your document..." }, color = Color(0xFF38BDF8), fontSize = 11.sp)
                                        }
                                    } else {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                                    modifier = Modifier.clickable {
                                                        if (textContent.isNotBlank()) {
                                                            isAiProcessing = true
                                                            aiStatusMessage = "Structuring headings & bullets..."
                                                            scope.launch {
                                                                val res = AiOrchestrator.editTextAi(
                                                                    textContent,
                                                                    "Structure and format this document with clean markdown headings #, ##, bullet points -, and clear paragraphs.",
                                                                    context
                                                                )
                                                                if (res.isNotBlank()) textContent = res
                                                                isAiProcessing = false
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("🪄 Format Headings", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }

                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                                    modifier = Modifier.clickable {
                                                        if (textContent.isNotBlank()) {
                                                            isAiProcessing = true
                                                            aiStatusMessage = "Polishing grammar & business tone..."
                                                            scope.launch {
                                                                val res = AiOrchestrator.editTextAi(
                                                                    textContent,
                                                                    "Improve grammar, remove typos, and rewrite in a polished, professional formal business tone.",
                                                                    context
                                                                )
                                                                if (res.isNotBlank()) textContent = res
                                                                isAiProcessing = false
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("✨ Polish Tone", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }

                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                                    modifier = Modifier.clickable {
                                                        if (textContent.isNotBlank()) {
                                                            isAiProcessing = true
                                                            aiStatusMessage = "Generating Executive Summary..."
                                                            scope.launch {
                                                                val res = AiOrchestrator.editTextAi(
                                                                    textContent,
                                                                    "Create a comprehensive Executive Summary report with Key Highlights and Action Items.",
                                                                    context
                                                                )
                                                                if (res.isNotBlank()) textContent = res
                                                                isAiProcessing = false
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("📊 Summarize", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }

                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                                    modifier = Modifier.clickable {
                                                        if (textContent.isNotBlank()) {
                                                            isAiProcessing = true
                                                            aiStatusMessage = "Translating to Bengali..."
                                                            scope.launch {
                                                                val res = AiOrchestrator.translateTextAi(textContent, "Bengali", context)
                                                                if (res.isNotBlank()) textContent = res
                                                                isAiProcessing = false
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("🇧🇩 বাংলায় অনুবাদ", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }

                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                                    modifier = Modifier.clickable {
                                                        if (textContent.isNotBlank()) {
                                                            isAiProcessing = true
                                                            aiStatusMessage = "Translating to English..."
                                                            scope.launch {
                                                                val res = AiOrchestrator.translateTextAi(textContent, "English", context)
                                                                if (res.isNotBlank()) textContent = res
                                                                isAiProcessing = false
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("🇬🇧 To English", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Starter Templates
                                    Text("Starter Templates:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    docTitle = "Official Business Letter"
                                                    textContent = """
# OFFICIAL BUSINESS LETTER
**Date:** [Insert Date]
**To:** [Recipient Name / Organization]
**From:** [Your Name / Company]

Dear Sir/Madam,

I am writing this letter to formally communicate regarding [Subject / Project Name]. We are pleased to provide all necessary documentations and agreements as agreed upon.

### Key Details & Summary:
* Item 1: Scope of Work and Deliverables
* Item 2: Milestone schedule and timelines
* Item 3: Payment terms and compliance verification

Should you require any further information or clarification, please do not hesitate to contact the undersigned.

Sincerely,
[Your Name]
[Designation / Company Name]
                                                    """.trimIndent()
                                                },
                                                label = { Text("📄 Formal Letter", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    docTitle = "Meeting Minutes & Action Items"
                                                    textContent = """
# MEETING MINUTES & SUMMARY
**Meeting Title:** Project Sync & Strategy
**Date & Time:** [Date, Time]
**Attendees:** [List of participants]

### 1. Agenda Overview
The team gathered to discuss weekly progress, blocking challenges, and roadmap prioritization for upcoming milestones.

### 2. Key Decisions & Discussion Points
* Decision A: Approved the updated design framework and export standards.
* Decision B: Streamlined multi-model AI pipeline for higher accuracy and faster response times.
* Decision C: Scheduled release review for end of week.

### 3. Action Items
* [ ] Assignee 1: Finalize client documentation by Wednesday.
* [ ] Assignee 2: Deploy verified build to production environment.
                                                    """.trimIndent()
                                                },
                                                label = { Text("📋 Meeting Minutes", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    docTitle = "Payment Receipt & Invoice Note"
                                                    textContent = """
# PAYMENT RECEIPT & INVOICE MEMO
**Receipt No:** REC-${System.currentTimeMillis().toString().takeLast(6)}
**Billed To:** [Client / Customer Name]
**Date:** [Current Date]

---

### Itemized Summary:
* 01. Professional Scanning & OCR Processing - $150.00
* 02. Multi-Model AI Document Formatting - $120.00
* 03. High Resolution PDF Export & Archiving - $80.00

---
**Subtotal:** $350.00
**Tax / VAT (5%):** $17.50
**Total Paid:** $367.50

*Payment Status: Paid & Verified*
Thank you for your business!
                                                    """.trimIndent()
                                                },
                                                label = { Text("🧾 Invoice / Receipt", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TAB 1: LAYOUT & STYLING
                        1 -> {
                            Text("Page Format & Orientation", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Page Size
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(PageSize.A4, PageSize.LETTER, PageSize.LEGAL, PageSize.A5).forEach { size ->
                                    FilterChip(
                                        selected = selectedPageSize == size,
                                        onClick = { selectedPageSize = size },
                                        label = { Text(size.displayName.split(" ")[0], fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0EA5E9),
                                            selectedLabelColor = Color.Black,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Orientation & Margins
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkCardBg,
                                    border = BorderStroke(1.dp, if (!isLandscape) Color(0xFF0EA5E9) else Color(0xFF3F3F46)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isLandscape = false }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Portrait", color = if (!isLandscape) Color(0xFF0EA5E9) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Standard vertical", color = DarkTextSecondary, fontSize = 10.sp)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkCardBg,
                                    border = BorderStroke(1.dp, if (isLandscape) Color(0xFF0EA5E9) else Color(0xFF3F3F46)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isLandscape = true }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Landscape", color = if (isLandscape) Color(0xFF0EA5E9) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Wide horizontal", color = DarkTextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF3F3F46))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Typography
                            Text("Typography & Font Styling", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("SansSerif" to "Modern (Sans)", "Serif" to "Classic (Serif)", "Monospace" to "Code (Mono)").forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedFontFamily == key,
                                        onClick = { selectedFontFamily = key },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0EA5E9),
                                            selectedLabelColor = Color.Black,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Font Size & Line Spacing Sliders
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Font Size: ${fontSize.toInt()} pt", color = DarkTextColor, fontSize = 12.sp)
                                    Text("Line Spacing: ${"%.2f".format(lineSpacingMultiplier)}x", color = DarkTextColor, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { fontSize = it },
                                        valueRange = 10f..18f,
                                        steps = 7,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0EA5E9), activeTrackColor = Color(0xFF0EA5E9))
                                    )
                                    Slider(
                                        value = lineSpacingMultiplier,
                                        onValueChange = { lineSpacingMultiplier = it },
                                        valueRange = 1.15f..1.8f,
                                        steps = 5,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0EA5E9), activeTrackColor = Color(0xFF0EA5E9))
                                    )
                                }
                            }

                            // Alignment & Weight
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Left" to Icons.Default.FormatAlignLeft, "Center" to Icons.Default.FormatAlignCenter, "Right" to Icons.Default.FormatAlignRight).forEach { (align, icon) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (textAlign == align) Color(0xFF0EA5E9) else DarkCardBg,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clickable { textAlign = align }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = align, tint = if (textAlign == align) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = isBold,
                                        onClick = { isBold = !isBold },
                                        label = { Text("Bold", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0EA5E9), selectedLabelColor = Color.Black)
                                    )
                                    FilterChip(
                                        selected = isItalic,
                                        onClick = { isItalic = !isItalic },
                                        label = { Text("Italic", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0EA5E9), selectedLabelColor = Color.Black)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF3F3F46))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Color Theme Cards
                            Text("Page Color Themes", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TextToPdfTheme.values()) { theme ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(theme.bgColor),
                                        border = BorderStroke(2.dp, if (selectedTheme == theme) Color(0xFF0EA5E9) else Color(theme.borderColor)),
                                        modifier = Modifier
                                            .width(110.dp)
                                            .clickable { selectedTheme = theme }
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(theme.title, color = Color(theme.textColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(theme.titleColor)))
                                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(theme.accentColor)))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TAB 2: HEADER, FOOTER & WATERMARK
                        2 -> {
                            Text("Header Configuration", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Include Document Header", color = Color.White, fontSize = 12.sp)
                                    Text("Displays title and rule line at top", color = DarkTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = includeHeader,
                                    onCheckedChange = { includeHeader = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0EA5E9))
                                )
                            }

                            if (includeHeader) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = headerText,
                                    onValueChange = { headerText = it },
                                    label = { Text("Custom Header Text (defaults to Document Title)", color = DarkTextSecondary, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        focusedBorderColor = Color(0xFF0EA5E9),
                                        unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = includeDate,
                                        onCheckedChange = { includeDate = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0EA5E9))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Include Date in Header", color = DarkTextColor, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF3F3F46))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Footer Configuration
                            Text("Footer Configuration", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Include Document Footer", color = Color.White, fontSize = 12.sp)
                                    Text("Displays page numbers and sign-off", color = DarkTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = includeFooter,
                                    onCheckedChange = { includeFooter = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0EA5E9))
                                )
                            }

                            if (includeFooter) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customFooterText,
                                    onValueChange = { customFooterText = it },
                                    label = { Text("Custom Footer Note / Sign-off", color = DarkTextSecondary, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                        focusedBorderColor = Color(0xFF0EA5E9),
                                        unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = includePageNumbers,
                                        onCheckedChange = { includePageNumbers = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0EA5E9))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Show 'Page X / Y' numbering", color = DarkTextColor, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF3F3F46))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Decorative Page Border
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Decorative Page Border", color = Color.White, fontSize = 12.sp)
                                    Text("Adds an elegant frame around every page", color = DarkTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = showPageBorder,
                                    onCheckedChange = { showPageBorder = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0EA5E9))
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF3F3F46))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Watermark
                            Text("Watermark & Security", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text (Diagonal)", color = DarkTextSecondary, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = ThemeManager.themeColor(Color(0xFF3F3F46), Color(0xFFE2E8F0))
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("CONFIDENTIAL", "DRAFT", "ORIGINAL", "OFFICIAL", "PRIVATE").forEach { tag ->
                                    item {
                                        FilterChip(
                                            selected = watermarkText == tag,
                                            onClick = { watermarkText = if (watermarkText == tag) "" else tag },
                                            label = { Text(tag, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0EA5E9),
                                                selectedLabelColor = Color.Black,
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Style Preview Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF18181B),
                        border = BorderStroke(1.dp, Color(0xFF27272A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${selectedPageSize.displayName.split(" ")[0]} • ${if (isLandscape) "Landscape" else "Portrait"} • ${selectedTheme.title} • ${fontSize.toInt()}pt • ${selectedFontFamily}",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("~${estimatedPages}p", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                val isContentValid = textContent.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export & Share
                    OutlinedButton(
                        onClick = {
                            if (!isContentValid) {
                                Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isGenerating = true
                            scope.launch {
                                val opts = TextToPdfOptions(
                                    pageSize = selectedPageSize,
                                    isLandscape = isLandscape,
                                    fontSize = fontSize,
                                    lineSpacingMultiplier = lineSpacingMultiplier,
                                    fontFamily = selectedFontFamily,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    textAlign = textAlign,
                                    theme = selectedTheme,
                                    margin = marginSize,
                                    includeHeader = includeHeader,
                                    headerText = headerText,
                                    includeFooter = includeFooter,
                                    includePageNumbers = includePageNumbers,
                                    includeDate = includeDate,
                                    customFooterText = customFooterText,
                                    watermarkText = watermarkText.ifBlank { null },
                                    showPageBorder = showPageBorder,
                                    author = authorName
                                )
                                val file = PdfExporter.generatePdfFromText(
                                    context = context,
                                    title = docTitle.ifBlank { "Text_Document" },
                                    textContent = textContent,
                                    options = opts
                                )
                                isGenerating = false
                                if (file != null) {
                                    onPdfCreated(file)
                                    PdfExporter.sharePdf(context, file)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isContentValid && !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color(0xFF0EA5E9), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Print PDF
                    OutlinedButton(
                        onClick = {
                            if (!isContentValid) {
                                Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isGenerating = true
                            scope.launch {
                                val opts = TextToPdfOptions(
                                    pageSize = selectedPageSize,
                                    isLandscape = isLandscape,
                                    fontSize = fontSize,
                                    lineSpacingMultiplier = lineSpacingMultiplier,
                                    fontFamily = selectedFontFamily,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    textAlign = textAlign,
                                    theme = selectedTheme,
                                    margin = marginSize,
                                    includeHeader = includeHeader,
                                    headerText = headerText,
                                    includeFooter = includeFooter,
                                    includePageNumbers = includePageNumbers,
                                    includeDate = includeDate,
                                    customFooterText = customFooterText,
                                    watermarkText = watermarkText.ifBlank { null },
                                    showPageBorder = showPageBorder,
                                    author = authorName
                                )
                                val file = PdfExporter.generatePdfFromText(
                                    context = context,
                                    title = docTitle.ifBlank { "Text_Document" },
                                    textContent = textContent,
                                    options = opts
                                )
                                isGenerating = false
                                if (file != null) {
                                    onPdfCreated(file)
                                    PdfExporter.printPdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isContentValid && !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF00BFA5)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF00BFA5), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print", color = Color(0xFF00BFA5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Generate & Save PDF
                    Button(
                        onClick = {
                            if (!isContentValid) {
                                Toast.makeText(context, "Please enter some text first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGenerating = true
                            scope.launch {
                                val opts = TextToPdfOptions(
                                    pageSize = selectedPageSize,
                                    isLandscape = isLandscape,
                                    fontSize = fontSize,
                                    lineSpacingMultiplier = lineSpacingMultiplier,
                                    fontFamily = selectedFontFamily,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    textAlign = textAlign,
                                    theme = selectedTheme,
                                    margin = marginSize,
                                    includeHeader = includeHeader,
                                    headerText = headerText,
                                    includeFooter = includeFooter,
                                    includePageNumbers = includePageNumbers,
                                    includeDate = includeDate,
                                    customFooterText = customFooterText,
                                    watermarkText = watermarkText.ifBlank { null },
                                    showPageBorder = showPageBorder,
                                    author = authorName
                                )
                                val file = PdfExporter.generatePdfFromText(
                                    context = context,
                                    title = docTitle.ifBlank { "Text_Document" },
                                    textContent = textContent,
                                    options = opts
                                )
                                isGenerating = false
                                if (file != null) {
                                    Toast.makeText(context, "PDF saved to Documents!", Toast.LENGTH_SHORT).show()
                                    onPdfCreated(file)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isContentValid && !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==================== PASSPORT PHOTO DIALOG ====================
@Composable
fun PassportPhotoDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSizeName by remember { mutableStateOf("Standard Passport (35 x 45 mm)") }
    var backgroundColorOption by remember { mutableIntStateOf(0) } // 0: White, 1: Soft Blue, 2: Light Gray
    var isCreatingSheet by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val bmp = FileUtils.loadBitmapsFromUri(context, it).firstOrNull()
                withContext(Dispatchers.Main) {
                    selectedBitmap = bmp
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (selectedBitmap == null) {
            galleryLauncher.launch("image/*")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Passport Photo Maker", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (backgroundColorOption) {
                                    1 -> Color(0xFFD6E4FF)
                                    2 -> Color(0xFFF0F0F0)
                                    else -> Color.White
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Passport Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Select Another Photo", color = OrangeAccent, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = OrangeAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Portrait from Gallery", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Standard Photo Size Preset:", color = DarkTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                val sizePresets = listOf(
                    "Standard Passport (35 x 45 mm)",
                    "US Visa / Square (2 x 2 inch / 51x51 mm)",
                    "Stamp Size (25 x 30 mm)"
                )

                sizePresets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedSizeName == preset) OrangeAccent.copy(alpha = 0.15f) else DarkCardBg,
                        border = if (selectedSizeName == preset) BorderStroke(1.dp, OrangeAccent) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedSizeName = preset }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSizeName == preset,
                                onClick = { selectedSizeName = preset },
                                colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(preset, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Background Color:", color = DarkTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bgOptions = listOf(
                        Triple("White", Color.White, 0),
                        Triple("Soft Blue", Color(0xFFD6E4FF), 1),
                        Triple("Off-White", Color(0xFFF0F0F0), 2)
                    )
                    bgOptions.forEach { (label, col, idx) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (backgroundColorOption == idx) OrangeAccent.copy(alpha = 0.2f) else DarkCardBg,
                            border = if (backgroundColorOption == idx) BorderStroke(1.5.dp, OrangeAccent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { backgroundColorOption = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(shape = CircleShape, color = col, modifier = Modifier.size(14.dp)) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action: Export 8-in-1 printable sheet
                Button(
                    onClick = {
                        val bmp = selectedBitmap ?: return@Button
                        isCreatingSheet = true
                        scope.launch(Dispatchers.IO) {
                            // Create standard 8-photo printable sheet (4x2 on 1800x1200 px canvas)
                            val sheetW = 1800
                            val sheetH = 1200
                            val sheetBmp = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(sheetBmp)
                            canvas.drawColor(AndroidColor.WHITE)

                            val cellW = (sheetW - 150) / 4
                            val cellH = (sheetH - 120) / 2
                            val photoScaled = Bitmap.createScaledBitmap(bmp, cellW - 20, cellH - 20, true)

                            val borderPaint = Paint().apply {
                                style = Paint.Style.STROKE
                                strokeWidth = 2f
                                color = AndroidColor.LTGRAY
                            }

                            for (row in 0 until 2) {
                                for (col in 0 until 4) {
                                    val left = 40 + col * (cellW + 20)
                                    val top = 40 + row * (cellH + 20)
                                    canvas.drawBitmap(photoScaled, left.toFloat() + 10f, top.toFloat() + 10f, null)
                                    canvas.drawRect(RectF(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat()), borderPaint)
                                }
                            }

                            val savedPath = FileUtils.saveBitmapToDocStorage(context, sheetBmp, "PASSPORT_SHEET")
                            withContext(Dispatchers.Main) {
                                isCreatingSheet = false
                                Toast.makeText(context, "8-in-1 Passport Photo Sheet created!", Toast.LENGTH_SHORT).show()
                                onSaved(savedPath)
                                onDismiss()
                            }
                        }
                    },
                    enabled = selectedBitmap != null && !isCreatingSheet,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCreatingSheet) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create 8-in-1 Printable Sheet", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== RESIZE PDF DIALOG ====================
@Composable
fun PdfResizeDialog(
    doc: DocumentEntity,
    pages: List<PageEntity>,
    onDismiss: () -> Unit,
    onExportWithPageSize: (PageSize) -> Unit
) {
    var selectedPageSize by remember { mutableStateOf(PageSize.A4) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resize PDF Pages", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Target PDF: ${doc.title} (${doc.pageCount} pages)",
                    color = DarkTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                val pageSizes = listOf(
                    Pair(PageSize.A4, "A4 (Standard 210 x 297 mm)"),
                    Pair(PageSize.LETTER, "US Letter (8.5 x 11 in)"),
                    Pair(PageSize.LEGAL, "US Legal (8.5 x 14 in)"),
                    Pair(PageSize.A5, "A5 Pocket (148 x 210 mm)"),
                    Pair(PageSize.COMPACT, "Compact Mobile View")
                )

                pageSizes.forEach { (size, label) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedPageSize == size) OrangeAccent.copy(alpha = 0.15f) else DarkCardBg,
                        border = if (selectedPageSize == size) BorderStroke(1.dp, OrangeAccent) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedPageSize = size }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPageSize == size,
                                onClick = { selectedPageSize = size },
                                colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onExportWithPageSize(selectedPageSize)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Export PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== SPLIT PDF DIALOG ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitPdfDialog(
    doc: DocumentEntity,
    pages: List<PageEntity>,
    onDismiss: () -> Unit,
    onSplitPages: (List<PageEntity>, String) -> Unit
) {
    val selectedPageIndices = remember { mutableStateListOf<Int>() }
    var newDocTitle by remember { mutableStateOf("${doc.title}_Extracted") }

    LaunchedEffect(Unit) {
        if (pages.isNotEmpty()) {
            selectedPageIndices.add(0)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Split / Extract Pages", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newDocTitle,
                    onValueChange = { newDocTitle = it },
                    label = { Text("New Document Title", color = DarkTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        focusedBorderColor = OrangeAccent,
                        unfocusedBorderColor = Color(0xFF4A4A4A)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Select pages to extract into new document:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedPageIndices.clear()
                        selectedPageIndices.addAll(pages.indices)
                    }) {
                        Text("Select All", color = OrangeAccent, fontSize = 12.sp)
                    }
                    TextButton(onClick = { selectedPageIndices.clear() }) {
                        Text("Clear", color = DarkTextSecondary, fontSize = 12.sp)
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.forEachIndexed { idx, page ->
                        val isSelected = selectedPageIndices.contains(idx)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) OrangeAccent else DarkCardBg,
                            modifier = Modifier
                                .clickable {
                                    if (isSelected) selectedPageIndices.remove(idx)
                                    else selectedPageIndices.add(idx)
                                }
                        ) {
                            Text(
                                "Page ${idx + 1}",
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val pagesToExtract = pages.filterIndexed { index, _ -> selectedPageIndices.contains(index) }
                        onSplitPages(pagesToExtract, newDocTitle)
                        onDismiss()
                    },
                    enabled = selectedPageIndices.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Split (${selectedPageIndices.size} pages)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== ROTATE PDF DIALOG ====================
@Composable
fun RotatePdfDialog(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onRotateAllPages: () -> Unit,
    onAutoUpright: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rotate PDF Pages", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Choose a rotation action for '${doc.title}':", color = DarkTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onRotateAllPages()
                            onDismiss()
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Rotate 90° Clockwise", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Rotates all pages 90 degrees", color = DarkTextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAutoUpright()
                            onDismiss()
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TealAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Auto AI Orientation Upright", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Automatically detects text flow & rotates upright", color = DarkTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
