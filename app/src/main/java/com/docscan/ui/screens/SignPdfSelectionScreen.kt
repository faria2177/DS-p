package com.docscan.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.ScanMode
import com.docscan.data.model.ScannerFeatureMode
import com.docscan.ui.components.SignBorder
import com.docscan.ui.components.SignCardBg
import com.docscan.ui.components.SignDarkCanvas
import com.docscan.ui.components.SignHeroIllustration
import com.docscan.ui.components.SignTeal
import com.docscan.ui.components.SignTextPrimary
import com.docscan.ui.components.SignTextSecondary
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignPdfSelectionScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSignEditor: (Long) -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    var isImporting by remember { mutableStateOf(false) }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val targetDoc = viewModel.importDocumentFromDevice(uris, "SIGN_GALLERY")
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        if (targetDoc != null) {
                            viewModel.pendingSignAfterScan.value = false
                            onNavigateToSignEditor(targetDoc.id)
                        } else {
                            Toast.makeText(context, "Could not load selected image(s).", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        Toast.makeText(context, "Import failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Device File Picker launcher (PDF or images)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val targetDoc = viewModel.importDocumentFromDevice(uris, "SIGN_DEVICE")
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        if (targetDoc != null) {
                            viewModel.pendingSignAfterScan.value = false
                            onNavigateToSignEditor(targetDoc.id)
                        } else {
                            Toast.makeText(context, "Only PDF and image documents can be signed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        Toast.makeText(context, "File load error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SignDarkCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. TOP HERO AREA (Matching Screenshot 1)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    // Back Arrow
                    IconButton(
                        onClick = {
                            viewModel.pendingSignAfterScan.value = false
                            onNavigateBack()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SignTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title, Subtitle, and Right-side Illustration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign",
                                color = SignTextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add signatures, stamps, and more.",
                                color = SignTextSecondary,
                                fontSize = 14.sp
                            )
                        }

                        // Right-side Custom Illustration
                        SignHeroIllustration(
                            modifier = Modifier
                                .size(width = 110.dp, height = 110.dp)
                                .padding(end = 4.dp)
                        )
                    }
                }
            }

            // 2. CREATE OR IMPORT SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Create or Import",
                        color = SignTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Row 1: Scan | Gallery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Scan Card (Green camera icon)
                        ImportActionCard(
                            title = "Scan",
                            icon = Icons.Default.CameraAlt,
                            iconColor = Color(0xFF00BFA5),
                            iconBgColor = Color(0xFF133832),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.resetScanSession()
                                viewModel.scanMode.value = ScanMode.SINGLE
                                viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                                viewModel.pendingSignAfterScan.value = true
                                onNavigateToCamera()
                            }
                        )

                        // Gallery Card (Blue image icon)
                        ImportActionCard(
                            title = "Gallery",
                            icon = Icons.Default.Image,
                            iconColor = Color(0xFF3B82F6),
                            iconBgColor = Color(0xFF1E293B),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                galleryLauncher.launch("image/*")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 2: Device File Picker Card (Blue phone/folder icon)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ImportActionCard(
                            title = "Device",
                            icon = Icons.Default.PhoneAndroid,
                            iconColor = Color(0xFF60A5FA),
                            iconBgColor = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth(0.485f),
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            }
                        )
                    }
                }
            }

            // 3. SELECT FROM THIS APP SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Select from This App",
                        color = SignTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Document List
            if (documents.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No documents found",
                            color = SignTextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Scan or import a document above to get started.",
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(documents, key = { it.id }) { doc ->
                    SignDocumentItemRow(
                        document = doc,
                        onClick = {
                            viewModel.pendingSignAfterScan.value = false
                            onNavigateToSignEditor(doc.id)
                        }
                    )
                }
            }
        }

        // Loading Overlay during file import
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SignCardBg,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = SignTeal,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Loading document...",
                            color = SignTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SignCardBg,
        modifier = modifier
            .height(68.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconBgColor,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                color = SignTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SignDocumentItemRow(
    document: DocumentEntity,
    onClick: () -> Unit
) {
    val formattedDate = remember(document.createdAt) {
        SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault()).format(Date(document.createdAt))
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Thumbnail (Large rounded preview)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C2C32),
                modifier = Modifier.size(width = 54.dp, height = 68.dp)
            ) {
                if (document.thumbnailPath.isNotBlank() && File(document.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(document.thumbnailPath),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    color = SignTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        color = SignTextSecondary,
                        fontSize = 13.sp
                    )

                    Text(
                        text = " | ",
                        color = Color(0xFF4B5563),
                        fontSize = 13.sp
                    )

                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = SignTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    Text(
                        text = "${document.pageCount}",
                        color = SignTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
