package com.docscan.ui.components

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.util.CompressedPdfResult
import com.docscan.util.CompressionConfig
import com.docscan.util.CompressionLevel
import com.docscan.util.CompressionSettings
import com.docscan.util.CompressionSettingsManager
import com.docscan.util.PdfCompressor
import kotlin.math.roundToInt

// Color scheme matching document scanner theme
private val DarkBg: Color get() = ThemeManager.themeColor(Color(0xFF0F172A), Color(0xFFF8FAFC))
private val CardBg: Color get() = ThemeManager.themeColor(Color(0xFF1E293B), Color(0xFFFFFFFF))
private val CardBorder: Color get() = ThemeManager.themeColor(Color(0xFF334155), Color(0xFFE2E8F0))
private val PrimaryTeal = Color(0xFF00C48C)
private val DarkTextPrimary: Color get() = ThemeManager.themeColor(Color.White, Color(0xFF0F172A))
private val DarkTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF94A3B8), Color(0xFF475569))
private val AmberAccent = Color(0xFFF59E0B)
private val BlueAccent = Color(0xFF3B82F6)

enum class ComparisonMode {
    SPLIT_SLIDER,
    SIDE_BY_SIDE,
    TOGGLE_FLIP
}

/**
 * Main Comparison Component displayed before saving the compressed PDF.
 * Shows file size difference and interactive quality loss preview.
 */
@Composable
fun PdfQualityComparisonCard(
    result: CompressedPdfResult,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    var comparisonMode by remember { mutableStateOf(ComparisonMode.SPLIT_SLIDER) }
    var sliderRatio by remember { mutableFloatStateOf(0.5f) }
    var isShowingOriginalInToggle by remember { mutableStateOf(false) }

    val originalBmp = result.originalBitmaps.getOrNull(currentPageIndex)
    val compressedBmp = result.previewBitmaps.getOrNull(currentPageIndex)
        ?: result.previewBitmaps.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        // --- 1. HEADER & COMPARISON CONTROLS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Compare,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quality & Size Comparison",
                        color = DarkTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Review visual fidelity before saving",
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )
            }

            // Tune / Settings button to adjust quality directly
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF27354A),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSettings() }
                    .testTag("btn_compression_settings_tune")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Tune Settings",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Settings",
                        color = PrimaryTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. FILE SIZE COMPARISON SUMMARY CARD ---
        FileSizeComparisonMetrics(
            originalBytes = result.originalSizeBytes,
            compressedBytes = result.compressedSizeBytes,
            reductionPercent = result.reductionPercentage,
            qualityName = result.qualityLevelName
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. COMPARISON MODE TABS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141C2C))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ComparisonModeTab(
                title = "Split Slider",
                icon = Icons.Default.SwapHoriz,
                selected = comparisonMode == ComparisonMode.SPLIT_SLIDER,
                onClick = { comparisonMode = ComparisonMode.SPLIT_SLIDER },
                modifier = Modifier.weight(1f)
            )
            ComparisonModeTab(
                title = "Side by Side",
                icon = Icons.Default.PhotoSizeSelectActual,
                selected = comparisonMode == ComparisonMode.SIDE_BY_SIDE,
                onClick = { comparisonMode = ComparisonMode.SIDE_BY_SIDE },
                modifier = Modifier.weight(1f)
            )
            ComparisonModeTab(
                title = "Toggle Flip",
                icon = Icons.Default.Visibility,
                selected = comparisonMode == ComparisonMode.TOGGLE_FLIP,
                onClick = { comparisonMode = ComparisonMode.TOGGLE_FLIP },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 4. INTERACTIVE VISUAL COMPARISON CANVAS ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B1120))
                .border(BorderStroke(1.dp, Color(0xFF26334D)), RoundedCornerShape(14.dp))
                .padding(8.dp)
        ) {
            when (comparisonMode) {
                ComparisonMode.SPLIT_SLIDER -> {
                    InteractiveSplitSliderView(
                        originalBitmap = originalBmp ?: compressedBmp,
                        compressedBitmap = compressedBmp ?: originalBmp,
                        sliderRatio = sliderRatio,
                        onSliderRatioChange = { sliderRatio = it }
                    )
                }
                ComparisonMode.SIDE_BY_SIDE -> {
                    SideBySideView(
                        originalBitmap = originalBmp ?: compressedBmp,
                        compressedBitmap = compressedBmp ?: originalBmp,
                        originalSizeFormatted = PdfCompressor.formatFileSize(result.originalSizeBytes),
                        compressedSizeFormatted = result.formattedSize
                    )
                }
                ComparisonMode.TOGGLE_FLIP -> {
                    ToggleFlipView(
                        originalBitmap = originalBmp ?: compressedBmp,
                        compressedBitmap = compressedBmp ?: originalBmp,
                        isShowingOriginal = isShowingOriginalInToggle,
                        onToggle = { isShowingOriginalInToggle = !isShowingOriginalInToggle }
                    )
                }
            }
        }

        // --- 5. MULTI-PAGE SELECTOR (if document has multiple pages) ---
        if (result.pageCount > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPageIndex > 0) onPageChange(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page",
                        tint = if (currentPageIndex > 0) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF172033),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${result.pageCount}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = { if (currentPageIndex < result.pageCount - 1) onPageChange(currentPageIndex + 1) },
                    enabled = currentPageIndex < result.pageCount - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page",
                        tint = if (currentPageIndex < result.pageCount - 1) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 6. QUALITY LOSS & FIDELITY ASSESSMENT ---
        QualityLossAssessmentBadge(
            reductionPercent = result.reductionPercentage,
            qualityName = result.qualityLevelName
        )
    }
}

/**
 * File size metrics comparison header
 */
@Composable
private fun FileSizeComparisonMetrics(
    originalBytes: Long,
    compressedBytes: Long,
    reductionPercent: Int,
    qualityName: String
) {
    val origFormatted = PdfCompressor.formatFileSize(originalBytes)
    val compFormatted = PdfCompressor.formatFileSize(compressedBytes)
    val savedBytes = (originalBytes - compressedBytes).coerceAtLeast(0L)
    val savedFormatted = PdfCompressor.formatFileSize(savedBytes)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131D31),
        border = BorderStroke(1.dp, Color(0xFF22314E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Original Size Box
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "ORIGINAL",
                        color = DarkTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = origFormatted,
                        color = Color(0xFFCBD5E1),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Quality",
                        color = DarkTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Arrow & Saved badge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PrimaryTeal.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (reductionPercent > 0) "-$reductionPercent%" else "Optimized",
                            color = PrimaryTeal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved $savedFormatted",
                        color = PrimaryTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Compressed Size Box
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "COMPRESSED",
                        color = PrimaryTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = compFormatted,
                        color = PrimaryTeal,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$qualityName Preset",
                        color = DarkTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Interactive Split-Screen Slider View.
 * Left half shows the original, right half shows the compressed version.
 * Dragging the divider line allows instant side-by-side inspection of sharp vs compressed text.
 */
@Composable
private fun InteractiveSplitSliderView(
    originalBitmap: Bitmap?,
    compressedBitmap: Bitmap?,
    sliderRatio: Float,
    onSliderRatioChange: (Float) -> Unit
) {
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Helpful instruction pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "◀ ORIGINAL",
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "Drag slider ⟷ to compare clarity",
                color = DarkTextSecondary,
                fontSize = 11.sp
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PrimaryTeal.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "COMPRESSED ▶",
                    color = PrimaryTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newRatio = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                        onSliderRatioChange(newRatio)
                    }
                }
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val splitPx = totalWidthPx * sliderRatio

            // Layer 1: Compressed Bitmap as the background base
            if (compressedBitmap != null) {
                Image(
                    bitmap = compressedBitmap.asImageBitmap(),
                    contentDescription = "Compressed Page Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 2: Original Bitmap clipped from x = 0 to splitPx
            if (originalBitmap != null) {
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Original Page Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(
                                left = 0f,
                                top = 0f,
                                right = splitPx,
                                bottom = size.height,
                                clipOp = ClipOp.Intersect
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                )
            }

            // Layer 3: Vertical Divider line at splitPx
            val splitDp = with(density) { splitPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = splitDp - 1.5.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(PrimaryTeal)
            )

            // Layer 4: Drag Handle Grabber in the center of the divider
            Box(
                modifier = Modifier
                    .offset(x = splitDp - 16.dp, y = 134.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryTeal)
                    .border(BorderStroke(2.dp, Color.White), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Drag Divider",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Side-by-Side Dual View.
 */
@Composable
private fun SideBySideView(
    originalBitmap: Bitmap?,
    compressedBitmap: Bitmap?,
    originalSizeFormatted: String,
    compressedSizeFormatted: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Original column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Original", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(originalSizeFormatted, color = DarkTextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (originalBitmap != null) {
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Original",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Compressed column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PrimaryTeal.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Compressed", color = PrimaryTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(compressedSizeFormatted, color = PrimaryTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (compressedBitmap != null) {
                    Image(
                        bitmap = compressedBitmap.asImageBitmap(),
                        contentDescription = "Compressed",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Toggle / Quick-flip View.
 * Displays one version at a time with a quick toggle button.
 */
@Composable
private fun ToggleFlipView(
    originalBitmap: Bitmap?,
    compressedBitmap: Bitmap?,
    isShowingOriginal: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            val bitmapToShow = if (isShowingOriginal) originalBitmap else compressedBitmap
            if (bitmapToShow != null) {
                Image(
                    bitmap = bitmapToShow.asImageBitmap(),
                    contentDescription = if (isShowingOriginal) "Original" else "Compressed",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay Badge indicating current view
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isShowingOriginal) Color(0xFF1E293B).copy(alpha = 0.9f) else PrimaryTeal.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = if (isShowingOriginal) "Showing Original (Uncompressed)" else "Showing Compressed Version",
                    color = if (isShowingOriginal) Color.White else Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle button
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isShowingOriginal) Color(0xFF334155) else PrimaryTeal
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                tint = if (isShowingOriginal) Color.White else Color.Black,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isShowingOriginal) "Switch to Compressed Preview" else "Switch to Original (Inspect Loss)",
                color = if (isShowingOriginal) Color.White else Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ComparisonModeTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF243248) else Color.Transparent,
        label = "tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) PrimaryTeal else DarkTextSecondary,
        label = "tab_color"
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Badge describing visual fidelity and quality impact.
 */
@Composable
private fun QualityLossAssessmentBadge(
    reductionPercent: Int,
    qualityName: String
) {
    val (statusTitle, statusDesc, badgeColor) = when {
        reductionPercent >= 75 -> Triple(
            "High Compression",
            "Optimal size reduction for email. Minor smoothing on fine gradients; text remains clearly legible.",
            AmberAccent
        )
        reductionPercent >= 50 -> Triple(
            "Balanced Fidelity",
            "Sharp font edges, intact signatures and clear diagrams with excellent space reduction.",
            PrimaryTeal
        )
        else -> Triple(
            "Maximum Visual Fidelity",
            "Virtually indistinguishable from original. All fine textures, stamps and micro-prints preserved.",
            BlueAccent
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Quality Loss: $statusTitle",
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusDesc,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Settings Dialog for PDF Compression.
 * Allows users to manually choose between different quality levels (Low, Medium, High)
 * and fine-tune compression parameters to balance file size and visual fidelity.
 */
@Composable
fun CompressionSettingsDialog(
    initialSettings: CompressionSettings,
    onDismiss: () -> Unit,
    onApplySettings: (CompressionSettings) -> Unit
) {
    val context = LocalContext.current
    var selectedLevel by remember { mutableStateOf(initialSettings.level) }
    var isCustom by remember { mutableStateOf(initialSettings.isCustom) }
    var quality by remember { mutableFloatStateOf(initialSettings.customQuality.toFloat()) }
    var maxDimension by remember { mutableIntStateOf(initialSettings.customMaxDimension) }
    var cleanBg by remember { mutableStateOf(initialSettings.cleanBackground) }
    var sharpen by remember { mutableStateOf(initialSettings.sharpenText) }
    var grayscale by remember { mutableStateOf(initialSettings.convertToGrayscale) }
    var saveAsDefault by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DarkBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Compression Settings",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Balance file size & visual fidelity",
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quality Level Presets
                Text(
                    text = "QUALITY LEVEL PRESET",
                    color = PrimaryTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val presets = listOf(
                    Triple(
                        CompressionLevel.LOW,
                        "Low Compression (Best Quality)",
                        "88% JPEG • 2400px • Highest visual sharpness, minimal quality loss"
                    ),
                    Triple(
                        CompressionLevel.MEDIUM,
                        "Medium Compression (Balanced)",
                        "80% JPEG • 1920px • Recommended for most documents, crisp text"
                    ),
                    Triple(
                        CompressionLevel.HIGH,
                        "High Compression (Smallest Size)",
                        "68% JPEG • 1440px • Maximum reduction for email & WhatsApp sharing"
                    )
                )

                presets.forEach { (level, title, desc) ->
                    val isSelected = !isCustom && selectedLevel == level
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryTeal.copy(alpha = 0.15f) else CardBg,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) PrimaryTeal else CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isCustom = false
                                selectedLevel = level
                                quality = level.jpegQuality.toFloat()
                                maxDimension = level.maxDimension
                                cleanBg = level.cleanBackground
                                sharpen = level.sharpenText
                            }
                            .testTag("preset_${level.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    isCustom = false
                                    selectedLevel = level
                                    quality = level.jpegQuality.toFloat()
                                    maxDimension = level.maxDimension
                                    cleanBg = level.cleanBackground
                                    sharpen = level.sharpenText
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PrimaryTeal,
                                    unselectedColor = DarkTextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = if (isSelected) PrimaryTeal else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = desc,
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Settings Switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCustom) PrimaryTeal.copy(alpha = 0.12f) else CardBg,
                    border = BorderStroke(if (isCustom) 1.5.dp else 1.dp, if (isCustom) PrimaryTeal else CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Fine-Tuning",
                                color = if (isCustom) PrimaryTeal else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Manually adjust quality slider and resolution",
                                color = DarkTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isCustom,
                            onCheckedChange = { isCustom = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryTeal,
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                // Granular custom controls (visible when isCustom is true)
                AnimatedVisibility(visible = isCustom) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141D2E))
                            .border(BorderStroke(1.dp, Color(0xFF22314E)), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        // Quality Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "JPEG Quality",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${quality.roundToInt()}%",
                                color = PrimaryTeal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = quality,
                            onValueChange = { quality = it },
                            valueRange = 40f..95f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryTeal,
                                activeTrackColor = PrimaryTeal,
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("slider_custom_quality")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Max Resolution Picker
                        Text(
                            text = "Max Resolution",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1080 to "1080p", 1440 to "1440p", 1920 to "FHD", 2400 to "2K").forEach { (dim, label) ->
                                val isDimSelected = maxDimension == dim
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDimSelected) PrimaryTeal else Color(0xFF1E293B),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { maxDimension = dim }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isDimSelected) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Toggles
                        CustomSettingSwitchRow(
                            title = "Smart Background Noise Cleaning",
                            subtitle = "Whiten paper background shadows for extra compression",
                            checked = cleanBg,
                            onCheckedChange = { cleanBg = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CustomSettingSwitchRow(
                            title = "Text Edge Sharpening",
                            subtitle = "Preserves character contours & signature contrast",
                            checked = sharpen,
                            onCheckedChange = { sharpen = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CustomSettingSwitchRow(
                            title = "Grayscale / B&W Mode",
                            subtitle = "Removes color channels for extra 35% size reduction",
                            checked = grayscale,
                            onCheckedChange = { grayscale = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // "Save as default preference" Checkbox Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveAsDefault = !saveAsDefault }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = saveAsDefault,
                        onCheckedChange = { saveAsDefault = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = PrimaryTeal,
                            uncheckedColor = DarkTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save as default compression preference",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }

                    Button(
                        onClick = {
                            val newSettings = CompressionSettings(
                                level = selectedLevel,
                                isCustom = isCustom,
                                customQuality = quality.roundToInt(),
                                customMaxDimension = maxDimension,
                                cleanBackground = cleanBg,
                                sharpenText = sharpen,
                                convertToGrayscale = grayscale
                            )
                            if (saveAsDefault) {
                                CompressionSettingsManager.saveSettings(context, newSettings)
                            }
                            onApplySettings(newSettings)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("btn_apply_compression_settings")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = DarkTextSecondary, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryTeal,
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}
