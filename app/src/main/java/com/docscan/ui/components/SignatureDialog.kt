package com.docscan.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class SignaturePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onSignatureSaved: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<SignaturePath>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    val colorOptions = listOf(
        Color.Black,
        Color(0xFF1E40AF), // Deep Blue
        Color(0xFF991B1B), // Crimson Red
        Color(0xFF047857)  // Forest Green
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Draw,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add E-Signature", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Sign inside the box below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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
                                            paths.add(SignaturePath(currentPoints, selectedColor, strokeWidth))
                                            currentPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Draw previously completed strokes
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

                        // Draw current active stroke
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

                    // Watermark signature baseline
                    Text(
                        "Sign on line ____________________",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color and width controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Pickers
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { col ->
                            Surface(
                                shape = CircleShape,
                                color = col,
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(
                                        width = if (selectedColor == col) 3.dp else 1.dp,
                                        color = if (selectedColor == col) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    ),
                                onClick = { selectedColor = col }
                            ) {}
                        }
                    }

                    // Undo and Clear
                    Row {
                        IconButton(
                            onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                            enabled = paths.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            onClick = { paths.clear() },
                            enabled = paths.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }

                // Stroke Width Slider
                Text("Stroke Width: ${strokeWidth.toInt()}px", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 3f..14f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (paths.isNotEmpty()) {
                        val bitmap = renderPathsToBitmap(paths)
                        onSignatureSaved(bitmap)
                    }
                },
                enabled = paths.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply Signature")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun renderPathsToBitmap(paths: List<SignaturePath>, padding: Float = 24f): Bitmap {
    if (paths.isEmpty()) {
        return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    }

    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
    var maxStroke = 6f

    for (p in paths) {
        if (p.strokeWidth > maxStroke) maxStroke = p.strokeWidth
        for (pt in p.points) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
    }

    if (minX >= maxX || minY >= maxY) {
        return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    }

    val actualPadding = padding.coerceAtLeast(maxStroke * 1.5f)
    val width = ((maxX - minX) + actualPadding * 2f).toInt().coerceAtLeast(80)
    val height = ((maxY - minY) + actualPadding * 2f).toInt().coerceAtLeast(50)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    paths.forEach { pathData ->
        if (pathData.points.size > 1) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pathData.color.toArgb()
                strokeWidth = pathData.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pathData.color.toArgb()
                style = Paint.Style.FILL
            }
            val cx = pathData.points.first().x - minX + actualPadding
            val cy = pathData.points.first().y - minY + actualPadding
            canvas.drawCircle(cx, cy, pathData.strokeWidth / 2f, paint)
        }
    }
    return bitmap
}
