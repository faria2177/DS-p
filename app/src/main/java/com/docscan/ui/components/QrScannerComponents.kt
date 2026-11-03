package com.docscan.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.docscan.data.model.BarcodeValueType
import com.docscan.data.model.MainScanMode
import com.docscan.data.model.ParsedBarcode
import com.docscan.data.model.QrHistoryEntity
import com.docscan.data.model.QrScanSubMode
import com.docscan.data.repository.QrHistoryRepository
import com.docscan.util.FileUtils
import com.docscan.util.QrActionHandler
import com.docscan.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Neon Scan Theme Accents
val QrNeonCyan = Color(0xFF00E5FF)
val QrNeonTeal = Color(0xFF00BFA5)
val QrDarkOverlay = Color(0x99000000)
val QrCardBg = Color(0xFF1E2024)
val QrDarkBg = Color(0xFF121417)

/**
 * Top segmented mode selector: [ 📄 Document ] [ ⚡ QR & Barcode ]
 */
@Composable
fun ModernScanModeSwitcher(
    selectedMode: MainScanMode,
    onModeSelected: (MainScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0x99000000),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier.shadow(8.dp, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Scan Tab
            val isDocSelected = selectedMode == MainScanMode.DOCUMENT
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDocSelected) QrNeonTeal else Color.Transparent,
                modifier = Modifier.clickable { onModeSelected(MainScanMode.DOCUMENT) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Document Scan",
                        tint = if (isDocSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Document",
                        color = if (isDocSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = if (isDocSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // QR & Barcode Tab
            val isQrSelected = selectedMode == MainScanMode.QR_BARCODE
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isQrSelected) QrNeonCyan else Color.Transparent,
                modifier = Modifier.clickable { onModeSelected(MainScanMode.QR_BARCODE) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "QR & Barcode",
                        tint = if (isQrSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QR & Barcode",
                        color = if (isQrSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = if (isQrSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Modern QR and Barcode Scanning Overlay with animated laser beam,
 * corner brackets, target guide, tap-to-focus ring, zoom controls, and low-light hint.
 */
@Composable
fun QrScannerOverlay(
    isWideBarcodeMode: Boolean,
    onToggleBarcodeFormat: () -> Unit,
    subMode: QrScanSubMode,
    onToggleSubMode: () -> Unit,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenHistory: () -> Unit,
    onCreateQr: () -> Unit,
    onSwitchCamera: () -> Unit,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    focusTapOffset: Offset?,
    showLowLightHint: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Dark Cutout Canvas with Scanning Frame & Laser Line
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Calculate Viewfinder Dimensions
            val frameW: Float
            val frameH: Float
            if (isWideBarcodeMode) {
                frameW = (canvasW * 0.86f).coerceAtMost(360.dp.toPx())
                frameH = (frameW * 0.45f).coerceAtLeast(140.dp.toPx())
            } else {
                val side = (canvasW * 0.72f).coerceAtMost(280.dp.toPx())
                frameW = side
                frameH = side
            }

            val left = (canvasW - frameW) / 2f
            val top = (canvasH - frameH) / 2f - 30.dp.toPx()
            val right = left + frameW
            val bottom = top + frameH
            val cornerRadius = 20.dp.toPx()

            // 1. Draw 4 dark surrounding rectangles
            val overlayColor = Color(0xAA0A0D10)
            // Top
            drawRect(color = overlayColor, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
            // Bottom
            drawRect(color = overlayColor, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            // Left
            drawRect(color = overlayColor, topLeft = Offset(0f, top), size = Size(left, frameH))
            // Right
            drawRect(color = overlayColor, topLeft = Offset(right, top), size = Size(canvasW - right, frameH))

            // 2. Draw Subtle Inner Frame Border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 3. Draw Neon Corner Accents
            val cornerLen = 28.dp.toPx()
            val strokeW = 4.dp.toPx()
            val accentColor = QrNeonCyan.copy(alpha = pulseGlow)

            // Top-Left Corner
            val tlPath = Path().apply {
                moveTo(left, top + cornerLen)
                lineTo(left, top + cornerRadius)
                quadraticTo(left, top, left + cornerRadius, top)
                lineTo(left + cornerLen, top)
            }
            drawPath(tlPath, accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Top-Right Corner
            val trPath = Path().apply {
                moveTo(right - cornerLen, top)
                lineTo(right - cornerRadius, top)
                quadraticTo(right, top, right, top + cornerRadius)
                lineTo(right, top + cornerLen)
            }
            drawPath(trPath, accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Bottom-Left Corner
            val blPath = Path().apply {
                moveTo(left, bottom - cornerLen)
                lineTo(left, bottom - cornerRadius)
                quadraticTo(left, bottom, left + cornerRadius, bottom)
                lineTo(left + cornerLen, bottom)
            }
            drawPath(blPath, accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Bottom-Right Corner
            val brPath = Path().apply {
                moveTo(right - cornerLen, bottom)
                lineTo(right - cornerRadius, bottom)
                quadraticTo(right, bottom, right, bottom - cornerRadius)
                lineTo(right, bottom - cornerLen)
            }
            drawPath(brPath, accentColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // 4. Draw Animated Laser Scan Beam with Gradient Trail
            val laserY = top + (bottom - top) * laserProgress
            val laserPad = 8.dp.toPx()

            // Laser Beam Glow Trail
            val beamBrush = Brush.verticalGradient(
                colors = listOf(
                    QrNeonCyan.copy(alpha = 0f),
                    QrNeonCyan.copy(alpha = 0.25f),
                    QrNeonCyan.copy(alpha = 0.85f)
                ),
                startY = laserY - 30.dp.toPx(),
                endY = laserY
            )
            drawRect(
                brush = beamBrush,
                topLeft = Offset(left + laserPad, (laserY - 25.dp.toPx()).coerceAtLeast(top)),
                size = Size(frameW - laserPad * 2, (25.dp.toPx()).coerceAtMost(laserY - top))
            )

            // Sharp Main Laser Line
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        QrNeonCyan,
                        Color.White,
                        QrNeonCyan,
                        Color.Transparent
                    )
                ),
                start = Offset(left + laserPad, laserY),
                end = Offset(right - laserPad, laserY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Tap-to-Focus Animated Ring Indicator
        focusTapOffset?.let { tap ->
            Box(
                modifier = Modifier
                    .offset(x = (tap.x - 30).dp, y = (tap.y - 30).dp)
                    .size(60.dp)
                    .border(2.dp, QrNeonCyan, CircleShape)
            )
        }

        // Floating Low Light Warning Banner
        AnimatedVisibility(
            visible = showLowLightHint && !isTorchOn,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xDD2A2210),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.clickable { onToggleTorch() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Low light detected • Tap to turn on Flashlight",
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Top Status & Frame Mode Switcher
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // QR / 1D Barcode Aspect Ratio Switcher
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xAA111827),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier.clickable { onToggleBarcodeFormat() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isWideBarcodeMode) Icons.Default.ViewStream else Icons.Default.CropSquare,
                        contentDescription = null,
                        tint = QrNeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isWideBarcodeMode) "Barcode View" else "QR View",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Single / Continuous Mode Switcher
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (subMode == QrScanSubMode.CONTINUOUS) QrNeonTeal.copy(alpha = 0.2f) else Color(0xAA111827),
                border = BorderStroke(1.dp, if (subMode == QrScanSubMode.CONTINUOUS) QrNeonTeal else Color(0x33FFFFFF)),
                modifier = Modifier.clickable { onToggleSubMode() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (subMode == QrScanSubMode.CONTINUOUS) Icons.Default.ViewAgenda else Icons.Default.QrCode,
                        contentDescription = null,
                        tint = if (subMode == QrScanSubMode.CONTINUOUS) QrNeonTeal else Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (subMode == QrScanSubMode.CONTINUOUS) "Batch Mode" else "Single Scan",
                        color = if (subMode == QrScanSubMode.CONTINUOUS) QrNeonTeal else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Floating Quick Action Rail (Torch, Gallery, History, Create QR, Camera Switch)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flashlight Toggle
            FloatingActionButtonPill(
                icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                label = "Torch",
                isActive = isTorchOn,
                activeColor = Color(0xFFF59E0B),
                onClick = onToggleTorch
            )

            // Import from Gallery
            FloatingActionButtonPill(
                icon = Icons.Default.Image,
                label = "Gallery",
                onClick = onOpenGallery
            )

            // Scan History
            FloatingActionButtonPill(
                icon = Icons.Default.History,
                label = "History",
                onClick = onOpenHistory
            )

            // Create QR Code
            FloatingActionButtonPill(
                icon = Icons.Default.QrCode2,
                label = "Create",
                onClick = onCreateQr
            )

            // Switch Camera (Front/Back)
            FloatingActionButtonPill(
                icon = Icons.Default.FlipCameraAndroid,
                label = "Flip",
                onClick = onSwitchCamera
            )
        }

        // Bottom Zoom Ratio Quick Pills (1x, 2x, 4x)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomRatioButton(label = "1x", isSelected = zoomRatio <= 1.2f) { onZoomChange(1.0f) }
            ZoomRatioButton(label = "2x", isSelected = zoomRatio in 1.8f..2.5f) { onZoomChange(2.0f) }
            ZoomRatioButton(label = "4x", isSelected = zoomRatio >= 3.5f) { onZoomChange(4.0f) }
        }
    }
}

@Composable
fun ZoomRatioButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) QrNeonCyan else Color(0x99000000),
        border = BorderStroke(1.dp, if (isSelected) QrNeonCyan else Color(0x44FFFFFF)),
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FloatingActionButtonPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = QrNeonCyan,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) activeColor else Color(0xAA1E2024),
            border = BorderStroke(1.dp, if (isActive) activeColor else Color(0x33FFFFFF)),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Scan Result Bottom Sheet with customized UI actions according to the decoded content type
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultBottomSheet(
    result: ParsedBarcode,
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showWifiPassword by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QrCardBg,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Category Icon, Title, Format Badge
            val categoryInfo = getCategoryVisuals(result.valueType)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = categoryInfo.accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryInfo.accentColor.copy(alpha = 0.3f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = categoryInfo.icon,
                                contentDescription = null,
                                tint = categoryInfo.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = categoryInfo.categoryName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.formatName,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Copy raw text action in header
                IconButton(
                    onClick = { QrActionHandler.copyToClipboard(context, result.rawValue) }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Content Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141619),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (result.valueType) {
                        BarcodeValueType.URL -> {
                            Text("Website URL", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.url ?: result.rawValue,
                                color = QrNeonCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        BarcodeValueType.WIFI -> {
                            Text("Wi-Fi Network Information", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SSID:", color = Color.LightGray, fontSize = 13.sp)
                                Text(result.wifiSsid ?: "Unknown", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Security:", color = Color.LightGray, fontSize = 13.sp)
                                Text(result.wifiEncryptionType ?: "WPA/WPA2", color = Color.White, fontSize = 13.sp)
                            }
                            if (!result.wifiPassword.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Password:", color = Color.LightGray, fontSize = 13.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (showWifiPassword) result.wifiPassword else "••••••••",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = if (showWifiPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility",
                                            tint = Color.Gray,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { showWifiPassword = !showWifiPassword }
                                        )
                                    }
                                }
                            }
                        }
                        BarcodeValueType.CONTACT_INFO -> {
                            Text("Contact Card", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(result.contactName ?: "Contact", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (result.contactPhones.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Phone: ${result.contactPhones.joinToString(", ")}", color = Color.LightGray, fontSize = 13.sp)
                            }
                            if (result.contactEmails.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Email: ${result.contactEmails.joinToString(", ")}", color = Color.LightGray, fontSize = 13.sp)
                            }
                            if (!result.contactOrg.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Company: ${result.contactOrg}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                        BarcodeValueType.PHONE -> {
                            Text("Phone Number", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.phone ?: result.rawValue, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        BarcodeValueType.EMAIL -> {
                            Text("Email", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.email ?: result.rawValue, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            if (!result.emailSubject.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Subject: ${result.emailSubject}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                        BarcodeValueType.SMS -> {
                            Text("SMS Message", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("To: ${result.smsNumber ?: result.rawValue}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (!result.smsMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Message: ${result.smsMessage}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                        BarcodeValueType.GEO -> {
                            Text("Location Coordinates", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Latitude: ${result.geoLat ?: 0.0}\nLongitude: ${result.geoLng ?: 0.0}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        BarcodeValueType.CALENDAR_EVENT -> {
                            Text("Calendar Event", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.calendarSummary ?: "Event", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (!result.calendarLocation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Location: ${result.calendarLocation}", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                        BarcodeValueType.PRODUCT, BarcodeValueType.ISBN -> {
                            Text("Barcode Number", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.displayValue, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        else -> {
                            Text("Decoded Text", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.displayValue,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Contextual Actions (Open, Call, Email, Save Contact, etc.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (result.valueType) {
                    BarcodeValueType.URL -> {
                        Button(
                            onClick = { QrActionHandler.openUrl(context, result.url ?: result.rawValue) },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Link", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.WIFI -> {
                        Button(
                            onClick = {
                                if (!result.wifiPassword.isNullOrBlank()) {
                                    QrActionHandler.copyToClipboard(context, result.wifiPassword, "Wi-Fi Password")
                                }
                                QrActionHandler.openWifiSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Wi-Fi", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.CONTACT_INFO -> {
                        Button(
                            onClick = {
                                QrActionHandler.saveContact(
                                    context = context,
                                    name = result.contactName,
                                    phone = result.contactPhones.firstOrNull(),
                                    email = result.contactEmails.firstOrNull(),
                                    org = result.contactOrg,
                                    address = result.contactAddresses.firstOrNull()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Contact", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.PHONE -> {
                        Button(
                            onClick = { QrActionHandler.dialPhoneNumber(context, result.phone ?: result.rawValue) },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Number", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.EMAIL -> {
                        Button(
                            onClick = {
                                QrActionHandler.sendEmail(
                                    context = context,
                                    email = result.email ?: result.rawValue,
                                    subject = result.emailSubject,
                                    body = result.emailBody
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Email", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.SMS -> {
                        Button(
                            onClick = {
                                QrActionHandler.sendSms(
                                    context = context,
                                    phone = result.smsNumber ?: result.rawValue,
                                    message = result.smsMessage
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send SMS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.GEO -> {
                        Button(
                            onClick = {
                                QrActionHandler.openMapsLocation(
                                    context = context,
                                    lat = result.geoLat ?: 0.0,
                                    lng = result.geoLng ?: 0.0,
                                    label = result.title
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Maps", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.CALENDAR_EVENT -> {
                        Button(
                            onClick = {
                                QrActionHandler.addCalendarEvent(
                                    context = context,
                                    title = result.calendarSummary,
                                    description = result.calendarDescription,
                                    location = result.calendarLocation,
                                    startMillis = result.calendarStart,
                                    endMillis = result.calendarEnd
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add to Calendar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    BarcodeValueType.PRODUCT, BarcodeValueType.ISBN -> {
                        Button(
                            onClick = { QrActionHandler.searchProductOnWeb(context, result.rawValue) },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search on Web", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { QrActionHandler.copyToClipboard(context, result.rawValue) },
                            colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Text", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Share Button
                OutlinedButton(
                    onClick = { QrActionHandler.shareText(context, result.rawValue) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.weight(0.7f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Actions: Scan Again & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View History", color = Color.LightGray, fontSize = 13.sp)
                }

                Button(
                    onClick = onScanAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3238)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Again", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class CategoryVisualInfo(
    val categoryName: String,
    val icon: ImageVector,
    val accentColor: Color
)

fun getCategoryVisuals(type: BarcodeValueType): CategoryVisualInfo {
    return when (type) {
        BarcodeValueType.URL -> CategoryVisualInfo("Website Link", Icons.Default.Language, Color(0xFF38BDF8))
        BarcodeValueType.WIFI -> CategoryVisualInfo("Wi-Fi Network", Icons.Default.Wifi, Color(0xFF34D399))
        BarcodeValueType.CONTACT_INFO -> CategoryVisualInfo("Contact Card", Icons.Default.Person, Color(0xFFA78BFA))
        BarcodeValueType.PHONE -> CategoryVisualInfo("Phone Number", Icons.Default.Call, Color(0xFF2DD4BF))
        BarcodeValueType.EMAIL -> CategoryVisualInfo("Email Address", Icons.Default.Email, Color(0xFFF472B6))
        BarcodeValueType.SMS -> CategoryVisualInfo("SMS Message", Icons.Default.Message, Color(0xFFFB923C))
        BarcodeValueType.GEO -> CategoryVisualInfo("Location", Icons.Default.LocationOn, Color(0xFFFBBF24))
        BarcodeValueType.CALENDAR_EVENT -> CategoryVisualInfo("Calendar Event", Icons.Default.CalendarMonth, Color(0xFFF87171))
        BarcodeValueType.PRODUCT, BarcodeValueType.ISBN -> CategoryVisualInfo("Product Barcode", Icons.Default.ShoppingCart, Color(0xFF4ADE80))
        BarcodeValueType.DRIVER_LICENSE -> CategoryVisualInfo("Driver License", Icons.Default.Description, Color(0xFF818CF8))
        else -> CategoryVisualInfo("Plain Text", Icons.Default.TextFields, Color(0xFF94A3B8))
    }
}

/**
 * Bottom Floating Badge for Continuous Multi-Scan Mode
 */
@Composable
fun QrContinuousScanBar(
    scannedCount: Int,
    onDoneClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF0101316),
        border = BorderStroke(1.dp, QrNeonTeal.copy(alpha = 0.5f)),
        modifier = modifier.shadow(12.dp, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = QrNeonTeal,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$scannedCount",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Scanned Codes",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            if (scannedCount > 0) {
                TextButton(
                    onClick = onClearClick,
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Clear", color = Color(0xFFF87171), fontSize = 12.sp)
                }
            }

            Button(
                onClick = onDoneClick,
                colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Done", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Scan History Modal Bottom Sheet with local storage, search, filter chips, and clear options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrHistoryBottomSheet(
    historyRepository: QrHistoryRepository,
    historyList: List<QrHistoryEntity>,
    onDismiss: () -> Unit,
    onItemClick: (QrHistoryEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var saveHistoryPref by remember { mutableStateOf(historyRepository.isHistorySaveEnabled) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredList = historyList.filter { item ->
        val matchesQuery = searchQuery.isBlank() ||
                item.rawValue.contains(searchQuery, ignoreCase = true) ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.subtitle.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Websites" -> item.valueType == BarcodeValueType.URL.name
            "Products" -> item.valueType == BarcodeValueType.PRODUCT.name || item.valueType == BarcodeValueType.ISBN.name
            "Contacts" -> item.valueType == BarcodeValueType.CONTACT_INFO.name
            "Wi-Fi" -> item.valueType == BarcodeValueType.WIFI.name
            "Text" -> item.valueType == BarcodeValueType.TEXT.name
            else -> true
        }

        matchesQuery && matchesFilter
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QrCardBg,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Title & Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = QrNeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("QR & Barcode History", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                if (historyList.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History", tint = Color(0xFFF87171))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search history...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = QrNeonCyan,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0xFF141619),
                    unfocusedContainerColor = Color(0xFF141619),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Websites", "Products", "Contacts", "Wi-Fi", "Text").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = QrNeonCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF141619),
                            labelColor = Color.LightGray
                        ),
                        border = BorderStroke(1.dp, if (isSelected) QrNeonCyan else Color(0x22FFFFFF))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // History List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No scan history found", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                        val formattedDate = dateFormat.format(Date(item.timestamp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF141619),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val valType = try {
                                        BarcodeValueType.valueOf(item.valueType)
                                    } catch (e: Exception) {
                                        BarcodeValueType.TEXT
                                    }
                                    val catVisual = getCategoryVisuals(valType)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = catVisual.accentColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = catVisual.icon,
                                                contentDescription = null,
                                                tint = catVisual.accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title.ifBlank { item.rawValue },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle.ifBlank { item.rawValue },
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formattedDate,
                                            color = Color(0xFF6B7280),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { QrActionHandler.copyToClipboard(context, item.rawValue) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                historyRepository.deleteHistory(item.id)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save History Preference Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Save QR & Barcode History", color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = saveHistoryPref,
                    onCheckedChange = {
                        saveHistoryPref = it
                        historyRepository.isHistorySaveEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = QrNeonTeal,
                        checkedTrackColor = QrNeonTeal.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Scan History?", color = Color.White) },
            text = { Text("This will remove all previously scanned QR and barcodes from this device.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        coroutineScope.launch {
                            historyRepository.clearAllHistory()
                        }
                    }
                ) {
                    Text("Clear All", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = QrCardBg
        )
    }
}

/**
 * Bottom Sheet to generate and create QR codes from Text, URL, Wi-Fi, Contact, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQrBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text", "URL", "Wi-Fi", "Contact", "Phone", "Email", "SMS")

    // Form inputs
    var textInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("https://") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactOrg by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }
    var smsPhone by remember { mutableStateOf("") }
    var smsMsg by remember { mutableStateOf("") }

    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayload by remember { mutableStateOf("") }

    fun refreshQrBitmap() {
        val payload = when (selectedTab) {
            0 -> textInput
            1 -> urlInput
            2 -> QrCodeGenerator.buildWifiQrString(wifiSsid, wifiPass)
            3 -> QrCodeGenerator.buildVCardQrString(name = contactName, phone = contactPhone, email = contactEmail, organization = contactOrg)
            4 -> if (phoneInput.isNotBlank()) "tel:$phoneInput" else ""
            5 -> if (emailInput.isNotBlank()) "mailto:$emailInput?subject=${emailSubject}" else ""
            6 -> if (smsPhone.isNotBlank()) "smsto:$smsPhone:$smsMsg" else ""
            else -> ""
        }
        qrPayload = payload
        if (payload.isNotBlank()) {
            generatedBitmap = QrCodeGenerator.generateQrBitmap(payload)
        } else {
            generatedBitmap = null
        }
    }

    LaunchedEffect(
        selectedTab, textInput, urlInput, wifiSsid, wifiPass,
        contactName, contactPhone, contactEmail, contactOrg,
        phoneInput, emailInput, emailSubject, smsPhone, smsMsg
    ) {
        refreshQrBitmap()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QrCardBg,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = QrNeonCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Create QR Code", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF141619),
                contentColor = QrNeonCyan,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = QrNeonCyan
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) QrNeonCyan else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Form inputs depending on active tab
            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Enter plain text or note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = getQrFieldColors()
                    )
                }
                1 -> {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Website URL (e.g. https://example.com)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                }
                2 -> {
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("Network Name / SSID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wifiPass,
                        onValueChange = { wifiPass = it },
                        label = { Text("Wi-Fi Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                }
                3 -> {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Full Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                }
                4 -> {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number to Dial") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                }
                5 -> {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Recipient Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailSubject,
                        onValueChange = { emailSubject = it },
                        label = { Text("Email Subject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                }
                6 -> {
                    OutlinedTextField(
                        value = smsPhone,
                        onValueChange = { smsPhone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = getQrFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smsMsg,
                        onValueChange = { smsMsg = it },
                        label = { Text("Message Text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = getQrFieldColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Generated QR Preview Canvas
            generatedBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        AsyncImage(
                            model = bmp,
                            contentDescription = "Generated QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Save & Share Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val savedPath = FileUtils.saveBitmapToDocStorage(context, bmp, "QR_GEN")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "QR Code saved to gallery!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QrNeonTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Image", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val savedPath = FileUtils.saveBitmapToDocStorage(context, bmp, "QR_GEN")
                                withContext(Dispatchers.Main) {
                                    QrActionHandler.shareText(context, qrPayload, "Share QR Code")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = Color.White)
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Enter information above to generate your QR Code", color = Color.Gray, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun getQrFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = QrNeonCyan,
    unfocusedBorderColor = Color(0x33FFFFFF),
    focusedContainerColor = Color(0xFF141619),
    unfocusedContainerColor = Color(0xFF141619),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = QrNeonCyan,
    unfocusedLabelColor = Color.Gray
)

/**
 * Dialog when multiple codes are detected in an imported gallery image
 */
@Composable
fun GalleryCodeSelectionDialog(
    codes: List<ParsedBarcode>,
    onSelectCode: (ParsedBarcode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = QrCardBg,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Multiple Codes Found (${codes.size})",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select a code to view its details and actions:",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(codes) { code ->
                        val visual = getCategoryVisuals(code.valueType)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141619),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSelectCode(code)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(visual.icon, contentDescription = null, tint = visual.accentColor, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(code.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(code.displayValue, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.LightGray)
                    }
                }
            }
        }
    }
}
