package com.docscan.ui.components

import com.docscan.util.ThemeManager

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.docscan.util.SignOverlayPlacement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class SignDrawingPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

// Custom Design Colors matching user reference screenshots
val SignDarkCanvas: Color get() = ThemeManager.themeColor(Color(0xFF141416), Color(0xFFF8FAFC))
val SignCardBg: Color get() = ThemeManager.themeColor(Color(0xFF222226), Color(0xFFFFFFFF))
val SignSheetBg: Color get() = ThemeManager.themeColor(Color(0xFF1E1E22), Color(0xFFFFFFFF))
val SignTeal = Color(0xFF00BFA5)
val SignTealLight = Color(0xFF1DE9B6)
val SignTealContainer: Color get() = ThemeManager.themeColor(Color(0xFF103E38), Color(0xFFCCFBF1))
val SignTextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFFFFFFF), Color(0xFF0F172A))
val SignTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9E9EA4), Color(0xFF475569))
val SignBorder = Color(0xFF2C2C32)

/**
 * Custom Hero Illustration for Screen 1 Header (Document, Signature Bounding Box, Pen, Desk plant)
 */
@Composable
fun SignHeroIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Subtle warm background shadow card
        drawRoundRect(
            color = Color(0xFF2A2A30),
            topLeft = Offset(w * 0.25f, h * 0.10f),
            size = Size(w * 0.65f, h * 0.82f),
            cornerRadius = CornerRadius(16f, 16f)
        )

        // 2. White Paper Document
        drawRoundRect(
            color = Color(0xFFFAFAFC),
            topLeft = Offset(w * 0.20f, h * 0.05f),
            size = Size(w * 0.65f, h * 0.82f),
            cornerRadius = CornerRadius(14f, 14f)
        )

        // Document header accent line
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(w * 0.28f, h * 0.14f),
            size = Size(w * 0.40f, 6f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // Sub-lines
        drawRoundRect(
            color = Color(0xFFEEF2F6),
            topLeft = Offset(w * 0.28f, h * 0.22f),
            size = Size(w * 0.48f, 5f),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        drawRoundRect(
            color = Color(0xFFEEF2F6),
            topLeft = Offset(w * 0.28f, h * 0.28f),
            size = Size(w * 0.35f, 5f),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )

        // 3. Signature Bounding Box with Teal border
        val boxLeft = w * 0.15f
        val boxTop = h * 0.36f
        val boxWidth = w * 0.58f
        val boxHeight = h * 0.38f

        drawRoundRect(
            color = Color(0x2200BFA5),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(8f, 8f)
        )

        drawRoundRect(
            color = Color(0xFF00BFA5),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3.5f)
        )

        // Corner resize handles
        val handleRadius = 6.5f
        val tealHandleColor = Color(0xFF00BFA5)
        drawCircle(tealHandleColor, handleRadius, Offset(boxLeft, boxTop))
        drawCircle(tealHandleColor, handleRadius, Offset(boxLeft + boxWidth, boxTop))
        drawCircle(tealHandleColor, handleRadius, Offset(boxLeft, boxTop + boxHeight))
        drawCircle(tealHandleColor, handleRadius, Offset(boxLeft + boxWidth, boxTop + boxHeight))

        // 4. Stylized Handwritten Signature Stroke inside Box
        val sigPath = Path().apply {
            val startX = boxLeft + boxWidth * 0.18f
            val startY = boxTop + boxHeight * 0.70f
            moveTo(startX, startY)
            cubicTo(
                boxLeft + boxWidth * 0.25f, boxTop + boxHeight * 0.20f,
                boxLeft + boxWidth * 0.35f, boxTop + boxHeight * 0.15f,
                boxLeft + boxWidth * 0.40f, boxTop + boxHeight * 0.65f
            )
            cubicTo(
                boxLeft + boxWidth * 0.45f, boxTop + boxHeight * 0.85f,
                boxLeft + boxWidth * 0.55f, boxTop + boxHeight * 0.35f,
                boxLeft + boxWidth * 0.65f, boxTop + boxHeight * 0.55f
            )
            cubicTo(
                boxLeft + boxWidth * 0.72f, boxTop + boxHeight * 0.75f,
                boxLeft + boxWidth * 0.82f, boxTop + boxHeight * 0.45f,
                boxLeft + boxWidth * 0.90f, boxTop + boxHeight * 0.50f
            )
        }
        drawPath(
            path = sigPath,
            color = Color(0xFF1E293B),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 5. Stylized Pen / Quill accent
        val penStart = Offset(w * 0.78f, h * 0.25f)
        val penEnd = Offset(w * 0.60f, h * 0.48f)
        drawLine(
            color = Color(0xFF00BFA5),
            start = penStart,
            end = penEnd,
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        // Pen tip
        drawLine(
            color = Color(0xFF334155),
            start = penEnd,
            end = Offset(w * 0.57f, h * 0.52f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Add Signature Bottom Sheet (Matching Reference Screenshot 2)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSignatureBottomSheet(
    onDismiss: () -> Unit,
    onCreateSignature: () -> Unit,
    onScanSignature: () -> Unit,
    onImportFromGallery: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SignSheetBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Signature",
                    color = SignTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2C2C32),
                    modifier = Modifier.size(32.dp),
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SignTextSecondary,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Create a Signature
            AddSignatureActionRow(
                icon = Icons.Default.Edit,
                title = "Create a Signature",
                onClick = {
                    onDismiss()
                    onCreateSignature()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 2: Scan a Signature
            AddSignatureActionRow(
                icon = Icons.Default.CameraAlt,
                title = "Scan a Signature",
                onClick = {
                    onDismiss()
                    onScanSignature()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 3: Import from Gallery
            AddSignatureActionRow(
                icon = Icons.Default.Image,
                title = "Import from Gallery",
                onClick = {
                    onDismiss()
                    onImportFromGallery()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddSignatureActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF26262B),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SignTeal,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                color = SignTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Full-featured Draw Signature Dialog
 */
@Composable
fun SignatureDrawingDialog(
    onDismiss: () -> Unit,
    onSignatureDrawn: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<SignDrawingPath>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF0F172A)) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    val colorOptions = listOf(
        Color(0xFF0F172A), // Black Ink
        Color(0xFF1E40AF), // Deep Blue Ink
        Color(0xFF991B1B), // Crimson Red Ink
        Color(0xFF047857)  // Forest Green Ink
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SignSheetBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = SignTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Draw Signature",
                            color = SignTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2C2C32),
                        modifier = Modifier.size(30.dp),
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SignTextSecondary,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Drawing Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.5.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColor, strokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.isNotEmpty()) {
                                            paths.add(SignDrawingPath(currentPoints, selectedColor, strokeWidth))
                                            currentPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Watermark line
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(40f, size.height - 40f),
                            end = Offset(size.width - 40f, size.height - 40f),
                            strokeWidth = 2f
                        )

                        // Draw completed paths
                        paths.forEach { pathData ->
                            if (pathData.points.size > 1) {
                                val p = Path().apply {
                                    moveTo(pathData.points.first().x, pathData.points.first().y)
                                    for (i in 1 until pathData.points.size) {
                                        lineTo(pathData.points[i].x, pathData.points[i].y)
                                    }
                                }
                                drawPath(
                                    path = p,
                                    color = pathData.color,
                                    style = Stroke(
                                        width = pathData.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Draw active path
                        if (currentPoints.size > 1) {
                            val activePath = Path().apply {
                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = activePath,
                                color = selectedColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (paths.isEmpty() && currentPoints.isEmpty()) {
                        Text(
                            text = "Sign here with finger or stylus",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Ink Colors & Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Palette
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { col ->
                            Surface(
                                shape = CircleShape,
                                color = col,
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(
                                        width = if (selectedColor == col) 2.5.dp else 1.dp,
                                        color = if (selectedColor == col) SignTeal else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                onClick = { selectedColor = col }
                            ) {}
                        }
                    }

                    // Undo & Clear Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2C2C32),
                            modifier = Modifier.size(36.dp),
                            onClick = {
                                if (paths.isNotEmpty()) paths.removeAt(paths.size - 1)
                            }
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (paths.isNotEmpty()) SignTextPrimary else Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2C2C32),
                            modifier = Modifier.size(36.dp),
                            onClick = { paths.clear() }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = if (paths.isNotEmpty()) Color(0xFFEF4444) else Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stroke Width Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thickness:",
                        color = SignTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 3f..14f,
                        colors = SliderDefaults.colors(
                            thumbColor = SignTeal,
                            activeTrackColor = SignTeal,
                            inactiveTrackColor = Color(0xFF33333A)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF3E3E46))
                    ) {
                        Text("Cancel", color = SignTextSecondary)
                    }

                    Button(
                        onClick = {
                            val allPaths = paths.toMutableList()
                            if (currentPoints.isNotEmpty()) {
                                allPaths.add(SignDrawingPath(currentPoints, selectedColor, strokeWidth))
                            }
                            if (allPaths.isNotEmpty()) {
                                val bmp = renderDrawingToTransparentBitmap(allPaths)
                                onSignatureDrawn(bmp)
                            }
                        },
                        enabled = paths.isNotEmpty() || currentPoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignTeal,
                            disabledContainerColor = Color(0xFF2A2A30)
                        )
                    ) {
                        Text("Save Signature", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun renderDrawingToTransparentBitmap(
    paths: List<SignDrawingPath>,
    padding: Float = 24f
): Bitmap {
    if (paths.isEmpty()) {
        return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    }

    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
    var maxStroke = 6f
    var totalPoints = 0

    for (p in paths) {
        if (p.strokeWidth > maxStroke) maxStroke = p.strokeWidth
        for (pt in p.points) {
            totalPoints++
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
    }

    if (totalPoints == 0) {
        return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    }

    // Protect against zero-area bounding boxes (e.g. single dots or flat lines)
    if (maxX <= minX) {
        maxX = minX + maxStroke * 2f
        minX -= maxStroke * 2f
    }
    if (maxY <= minY) {
        maxY = minY + maxStroke * 2f
        minY -= maxStroke * 2f
    }

    val actualPadding = padding.coerceAtLeast(maxStroke * 1.5f)
    val width = ((maxX - minX) + actualPadding * 2f).toInt().coerceAtLeast(80)
    val height = ((maxY - minY) + actualPadding * 2f).toInt().coerceAtLeast(50)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    paths.forEach { pathData ->
        if (pathData.points.size > 1) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = pathData.color.toArgb()
                strokeWidth = pathData.strokeWidth
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }

            val p = android.graphics.Path().apply {
                val startX = pathData.points.first().x - minX + actualPadding
                val startY = pathData.points.first().y - minY + actualPadding
                moveTo(startX, startY)
                for (i in 1 until pathData.points.size) {
                    val nx = pathData.points[i].x - minX + actualPadding
                    val ny = pathData.points[i].y - minY + actualPadding
                    lineTo(nx, ny)
                }
            }
            canvas.drawPath(p, paint)
        } else if (pathData.points.size == 1) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = pathData.color.toArgb()
                style = android.graphics.Paint.Style.FILL
            }
            val cx = pathData.points.first().x - minX + actualPadding
            val cy = pathData.points.first().y - minY + actualPadding
            canvas.drawCircle(cx, cy, pathData.strokeWidth / 2f, paint)
        }
    }
    return bitmap
}

/**
 * Interactive Placed Signature / Stamp Overlay with Move, Resize Handle, and Delete
 */
@Composable
fun InteractiveSignPlacementOverlay(
    overlay: SignOverlayPlacement,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (SignOverlayPlacement) -> Unit,
    onDelete: () -> Unit,
    pageWidthDp: Float,
    pageHeightDp: Float
) {
    var posX by remember(overlay.x) { mutableFloatStateOf(overlay.x) }
    var posY by remember(overlay.y) { mutableFloatStateOf(overlay.y) }
    var widthRatio by remember(overlay.widthRatio) { mutableFloatStateOf(overlay.widthRatio) }

    val overlayWidthDp = (pageWidthDp * widthRatio).coerceIn(40f, pageWidthDp * 0.95f)
    val aspect = overlay.bitmap.height.toFloat() / overlay.bitmap.width.toFloat()
    val overlayHeightDp = overlayWidthDp * aspect

    // Center coordinates translated to top-left for Compose Box positioning
    val leftDp = (posX * pageWidthDp - overlayWidthDp / 2f).coerceIn(0f, pageWidthDp - overlayWidthDp)
    val topDp = (posY * pageHeightDp - overlayHeightDp / 2f).coerceIn(0f, pageHeightDp - overlayHeightDp)

    Box(
        modifier = Modifier
            .offset { IntOffset((leftDp * 2.75f).roundToInt(), (topDp * 2.75f).roundToInt()) } // fallback / measured in Parent Box with local density
    )
}

/**
 * Page-contained Interactive Overlay View positioned inside a BoxWithConstraints of the Page
 */
@Composable
fun PageOverlayContainer(
    overlay: SignOverlayPlacement,
    isSelected: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    onSelect: () -> Unit,
    onUpdate: (SignOverlayPlacement) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val safePageWidth = pageWidthPx.coerceAtLeast(1f)
    val safePageHeight = pageHeightPx.coerceAtLeast(1f)

    var relCenterX by remember(overlay.x) { mutableFloatStateOf(overlay.x) }
    var relCenterY by remember(overlay.y) { mutableFloatStateOf(overlay.y) }
    var relWidth by remember(overlay.widthRatio) { mutableFloatStateOf(overlay.widthRatio) }
    var relRotation by remember(overlay.rotationDegrees) { mutableFloatStateOf(overlay.rotationDegrees) }

    val bmpW = overlay.bitmap.width.toFloat().coerceAtLeast(1f)
    val bmpH = overlay.bitmap.height.toFloat().coerceAtLeast(1f)
    val aspect = bmpH / bmpW

    fun maxWidthRatioForPage(): Float {
        val byWidth = 0.92f
        val byHeight = ((safePageHeight * 0.86f) / aspect / safePageWidth).coerceAtMost(0.92f)
        return minOf(byWidth, byHeight).coerceAtLeast(0.12f)
    }

    fun clampPlacement(centerX: Float, centerY: Float, widthRatio: Float): Triple<Float, Float, Float> {
        val wr = widthRatio.coerceIn(0.10f, maxWidthRatioForPage())
        val widthPx = safePageWidth * wr
        val heightPx = widthPx * aspect
        val halfX = (widthPx / (2f * safePageWidth)).coerceIn(0f, 0.49f)
        val halfY = (heightPx / (2f * safePageHeight)).coerceIn(0f, 0.49f)
        val cx = centerX.coerceIn(halfX, 1f - halfX)
        val cy = centerY.coerceIn(halfY, 1f - halfY)
        return Triple(cx, cy, wr)
    }

    val overlayWidthPx = safePageWidth * relWidth
    val overlayHeightPx = overlayWidthPx * aspect
    val leftPx = relCenterX * safePageWidth - overlayWidthPx / 2f
    val topPx = relCenterY * safePageHeight - overlayHeightPx / 2f

    val handleSizeDp = 34.dp
    val handlePaddingDp = 17.dp
    val leftDp = with(density) { leftPx.toDp() }
    val topDp = with(density) { topPx.toDp() }
    val widthDp = with(density) { overlayWidthPx.toDp() }
    val heightDp = with(density) { overlayHeightPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = leftDp - handlePaddingDp, y = topDp - handlePaddingDp)
            .size(width = widthDp + handleSizeDp, height = heightDp + handleSizeDp)
    ) {
        Box(
            modifier = Modifier
                .padding(handlePaddingDp)
                .size(width = widthDp, height = heightDp)
                .graphicsLayer { rotationZ = relRotation }
                .border(
                    BorderStroke(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) SignTeal else Color.Transparent
                    ),
                    shape = RoundedCornerShape(3.dp)
                )
                .pointerInput(overlay.id, safePageWidth, safePageHeight) {
                    detectTransformGestures(
                        panZoomLock = true,
                        onGesture = { _, pan, zoom, rotation ->
                            onSelect()
                            val proposedWidth = relWidth * zoom.coerceIn(0.75f, 1.35f)
                            val proposedX = relCenterX + pan.x / safePageWidth
                            val proposedY = relCenterY + pan.y / safePageHeight
                            val clamped = clampPlacement(proposedX, proposedY, proposedWidth)
                            relCenterX = clamped.first
                            relCenterY = clamped.second
                            relWidth = clamped.third
                            if (rotation != 0f) {
                                relRotation = (relRotation + rotation) % 360f
                            }
                            onUpdate(
                                overlay.copy(
                                    x = relCenterX,
                                    y = relCenterY,
                                    widthRatio = relWidth,
                                    rotationDegrees = relRotation
                                )
                            )
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect() }
        ) {
            Image(
                bitmap = overlay.bitmap.asImageBitmap(),
                contentDescription = "Signature Overlay",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            )
        }

        if (isSelected) {
            Surface(
                shape = CircleShape,
                color = SignTeal,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(handleSizeDp)
                    .clickable { onDelete() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Signature",
                    tint = Color.White,
                    modifier = Modifier.padding(7.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = SignTeal,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(handleSizeDp)
                    .clickable {
                        relRotation = (relRotation + 90f) % 360f
                        onUpdate(overlay.copy(rotationDegrees = relRotation))
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate 90 Degrees",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = SignTeal,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(handleSizeDp)
                    .pointerInput(overlay.id, safePageWidth, safePageHeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val sensitivity = 0.9f / safePageWidth
                            val proposed = relWidth + (dragAmount.x + dragAmount.y) * sensitivity
                            val clamped = clampPlacement(relCenterX, relCenterY, proposed)
                            relCenterX = clamped.first
                            relCenterY = clamped.second
                            relWidth = clamped.third
                            onUpdate(
                                overlay.copy(
                                    x = relCenterX,
                                    y = relCenterY,
                                    widthRatio = relWidth,
                                    rotationDegrees = relRotation
                                )
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Resize Signature",
                    tint = Color.White,
                    modifier = Modifier.padding(7.dp)
                )
            }
        }
    }
}

/**
 * Preview shown after gallery import. The bitmap is already background-cleaned
 * and tightly cropped so the user sees only the detected signature before use.
 */
@Composable
fun ImportedSignaturePreviewDialog(
    signatureBitmap: Bitmap,
    onDismiss: () -> Unit,
    onUseSignature: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SignSheetBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signature detected", color = SignTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Only the ink area will be added. Check the preview before placing it.",
                            color = SignTextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SignTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = signatureBitmap.asImageBitmap(),
                            contentDescription = "Detected signature preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Tip: after adding, drag anywhere on the signature to move it and pinch with two fingers to resize.",
                    color = SignTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SignBorder)
                    ) {
                        Text("Choose Another", color = SignTextSecondary)
                    }
                    Button(
                        onClick = onUseSignature,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignTeal)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Signature", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modern Custom Color Picker for Stamps and Dates with preset swatches,
 * toggleable RGB sliders, and direct Hex code input.
 */
@Composable
fun StampCustomColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedPicker by remember { mutableStateOf(false) }
    var redValue by remember(selectedColor) { mutableFloatStateOf(selectedColor.red * 255f) }
    var greenValue by remember(selectedColor) { mutableFloatStateOf(selectedColor.green * 255f) }
    var blueValue by remember(selectedColor) { mutableFloatStateOf(selectedColor.blue * 255f) }
    var hexInputText by remember(selectedColor) {
        val r = (selectedColor.red * 255).toInt().coerceIn(0, 255)
        val g = (selectedColor.green * 255).toInt().coerceIn(0, 255)
        val b = (selectedColor.blue * 255).toInt().coerceIn(0, 255)
        mutableStateOf(String.format("#%02X%02X%02X", r, g, b))
    }

    val presetColors = listOf(
        Color(0xFF00BFA5), // CamScanner Teal
        Color(0xFF1D4ED8), // Classic Blue
        Color(0xFFDC2626), // Official Red
        Color(0xFF059669), // Verified Green
        Color(0xFF7C3AED), // Royal Purple
        Color(0xFFEA580C), // Orange
        Color(0xFF1E293B), // Navy / Black
        Color(0xFF9D174D)  // Deep Rose
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color / রঙ",
                color = SignTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            // Current color indicator & Custom Toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (showAdvancedPicker) SignTealContainer else Color(0xFF2C2C34),
                border = BorderStroke(1.dp, if (showAdvancedPicker) SignTeal else Color(0xFF3F3F48)),
                modifier = Modifier.clickable { showAdvancedPicker = !showAdvancedPicker }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(selectedColor, CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Custom Color",
                        tint = if (showAdvancedPicker) SignTeal else SignTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (showAdvancedPicker) "Hide Mixer" else "Custom Color",
                        color = if (showAdvancedPicker) SignTeal else SignTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Swatches & Custom Color Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(presetColors) { col ->
                val isSel = (selectedColor.toArgb() and 0x00FFFFFF) == (col.toArgb() and 0x00FFFFFF)
                Surface(
                    shape = CircleShape,
                    color = col,
                    modifier = Modifier
                        .size(32.dp)
                        .border(
                            width = if (isSel) 2.5.dp else 0.dp,
                            color = if (isSel) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            onColorSelected(col)
                        }
                ) {
                    if (isSel) {
                        Box(contentAlignment = Alignment.Center) {
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

            // Dedicated Custom Color Swatch with Gradient & Palette Icon
            item {
                val isCustomSelected = presetColors.none { (selectedColor.toArgb() and 0x00FFFFFF) == (it.toArgb() and 0x00FFFFFF) }
                Surface(
                    shape = CircleShape,
                    color = if (isCustomSelected) selectedColor else Color(0xFF2C2C34),
                    border = BorderStroke(
                        width = if (isCustomSelected) 2.5.dp else 1.5.dp,
                        brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { showAdvancedPicker = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCustomSelected) Icons.Default.Check else Icons.Default.Palette,
                            contentDescription = "Custom Color",
                            tint = if (isCustomSelected) Color.White else SignTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Advanced Color Mixer panel
        AnimatedVisibility(
            visible = showAdvancedPicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F1F26),
                border = BorderStroke(1.dp, Color(0xFF33333E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom RGB & Hex",
                            color = SignTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Hex Input
                        OutlinedTextField(
                            value = hexInputText,
                            onValueChange = { input ->
                                hexInputText = input
                                try {
                                    val cleaned = input.trim().removePrefix("#")
                                    if (cleaned.length == 6) {
                                        val parsed = android.graphics.Color.parseColor("#$cleaned")
                                        val newCol = Color(parsed)
                                        onColorSelected(newCol)
                                        redValue = (android.graphics.Color.red(parsed)).toFloat()
                                        greenValue = (android.graphics.Color.green(parsed)).toFloat()
                                        blueValue = (android.graphics.Color.blue(parsed)).toFloat()
                                    }
                                } catch (_: Exception) {}
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = SignTextPrimary),
                            singleLine = true,
                            modifier = Modifier.width(105.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SignTeal,
                                unfocusedBorderColor = Color(0xFF475569)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Red Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("R", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        Slider(
                            value = redValue,
                            onValueChange = {
                                redValue = it
                                val newCol = Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f)
                                onColorSelected(newCol)
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFEF4444),
                                activeTrackColor = Color(0xFFEF4444)
                            )
                        )
                        Text("${redValue.toInt()}", color = SignTextSecondary, fontSize = 11.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }

                    // Green Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("G", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        Slider(
                            value = greenValue,
                            onValueChange = {
                                greenValue = it
                                val newCol = Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f)
                                onColorSelected(newCol)
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981)
                            )
                        )
                        Text("${greenValue.toInt()}", color = SignTextSecondary, fontSize = 11.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }

                    // Blue Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("B", color = Color(0xFF3B82F6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        Slider(
                            value = blueValue,
                            onValueChange = {
                                blueValue = it
                                val newCol = Color(redValue / 255f, greenValue / 255f, blueValue / 255f, 1f)
                                onColorSelected(newCol)
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6)
                            )
                        )
                        Text("${blueValue.toInt()}", color = SignTextSecondary, fontSize = 11.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
