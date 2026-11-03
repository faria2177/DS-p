package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.ui.viewmodel.TempScannedPage
import java.io.File

private val NidBlue = Color(0xFF22B4E5)
private val NidText: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val NidSubText: Color get() = ThemeManager.themeColor(Color(0xFF94A3B8), Color(0xFF475569))
private val NidCardBg: Color get() = ThemeManager.themeColor(Color(0xFF1E293B), Color(0xFFFFFFFF))
private val NidBorder: Color get() = ThemeManager.themeColor(Color(0xFF475569), Color(0xFFE2E8F0))

/**
 * Reference-style NID front/back landing page.
 *
 * The landing page is app-owned so it can match the reference UI exactly. Each Front/Back card
 * opens our own CameraX/OpenCV scanner for exactly one selected side, with automatic
 * cropping and local image cleaning. SAVE ID opens the two-sided NID preview.
 */
@Composable
fun NidScanSetupScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onScanSide: (Int) -> Unit
) {
    val pages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingStage by viewModel.nidProcessingStage.collectAsStateWithLifecycle()
    fun scanSide(side: Int) {
        if (isProcessing) return
        val selectedSide = if (side == 2) 2 else 1
        viewModel.prepareNidSetupSide(selectedSide)
        onScanSide(selectedSide)
    }

    val front = pages.firstOrNull { it.label.startsWith("Front", ignoreCase = true) }
    val back = pages.firstOrNull { it.label.startsWith("Back", ignoreCase = true) }
    val bothReady = front != null && back != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeManager.themeColor(Color(0xFF0F172A), Color.White))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NidBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Scan ID",
                    color = NidBlue,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Scan the front and back of your ID",
                color = NidText,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            NidSideSection(
                title = "FRONT",
                page = front,
                isFront = true,
                onClick = { scanSide(1) }
            )

            Spacer(Modifier.height(14.dp))

            NidSideSection(
                title = "BACK",
                page = back,
                isFront = false,
                onClick = { scanSide(2) }
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (bothReady) {
                    "Both sides are ready. Tap SAVE ID to auto-crop and create your NID preview."
                } else {
                    "Scan both sides to save them together on one page."
                },
                color = if (bothReady) Color(0xFF5F6B76) else NidSubText,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onSave,
                enabled = bothReady && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NidBlue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF83D5EF),
                    disabledContentColor = Color.White.copy(alpha = 0.88f)
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = processingStage ?: "PROCESSING…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                } else {
                    Text(
                        text = "SAVE ID",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun NidSideSection(
    title: String,
    page: TempScannedPage?,
    isFront: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = title,
        color = NidText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )

    Surface(
        color = NidCardBg,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NidBorder),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.35f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val previewPath = page?.processedPath?.takeIf { it.isNotBlank() }
                ?: page?.originalPath

            if (!previewPath.isNullOrBlank() && File(previewPath).exists()) {
                AsyncImage(
                    model = File(previewPath),
                    contentDescription = "$title NID preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            } else {
                NidPlaceholderIllustration(isFront = isFront)
            }

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(NidBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (page != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Captured",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan $title",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    Text(
        text = if (page == null) {
            if (isFront) "Tap to scan front" else "Tap to scan back"
        } else {
            if (isFront) "Front captured • Tap to rescan" else "Back captured • Tap to rescan"
        },
        color = NidBlue,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun NidPlaceholderIllustration(isFront: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 46.dp, vertical = 18.dp)
    ) {
        val card = Color(0xFFEEF3F8)
        val detail = Color(0xFFC9D3DE)
        val strong = Color(0xFFB8C6D3)

        drawRoundRect(
            color = card,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )

        if (isFront) {
            drawRect(
                color = detail,
                topLeft = Offset(size.width * 0.08f, size.height * 0.10f),
                size = Size(size.width * 0.78f, size.height * 0.13f)
            )
            drawRect(
                color = strong,
                topLeft = Offset(size.width * 0.09f, size.height * 0.34f),
                size = Size(size.width * 0.28f, size.height * 0.48f)
            )
            drawCircle(
                color = detail,
                radius = size.minDimension * 0.10f,
                center = Offset(size.width * 0.23f, size.height * 0.50f)
            )
            listOf(0.36f, 0.48f, 0.60f, 0.72f).forEachIndexed { index, y ->
                drawLine(
                    color = if (index == 0) strong else detail,
                    start = Offset(size.width * 0.45f, size.height * y),
                    end = Offset(size.width * (if (index == 0) 0.82f else 0.74f), size.height * y),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        } else {
            drawRect(
                color = strong,
                topLeft = Offset(0f, size.height * 0.16f),
                size = Size(size.width, size.height * 0.26f)
            )
            listOf(0.56f, 0.66f, 0.76f).forEachIndexed { index, y ->
                drawLine(
                    color = detail,
                    start = Offset(size.width * 0.10f, size.height * y),
                    end = Offset(size.width * (0.86f - index * 0.05f), size.height * y),
                    strokeWidth = 9f,
                    cap = StrokeCap.Round
                )
            }
            drawRect(
                color = detail,
                topLeft = Offset(size.width * 0.68f, size.height * 0.76f),
                size = Size(size.width * 0.20f, size.height * 0.14f)
            )
        }
    }
}

