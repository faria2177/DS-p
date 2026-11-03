package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.docscan.ui.viewmodel.CropPreset
import com.docscan.ui.viewmodel.EditTool
import com.docscan.ui.viewmodel.FilterEffect
import com.docscan.ui.viewmodel.ImageResizerUiState
import com.docscan.ui.viewmodel.ImageResizerViewModel
import com.docscan.ui.viewmodel.ResizerStep
import com.docscan.util.FileUtils
import com.docscan.util.resizer.CustomResizeMode
import com.docscan.util.resizer.ImageMetaInfo
import com.docscan.util.resizer.OutputImageFormat
import com.docscan.util.resizer.QualityPreset
import com.docscan.util.resizer.QuickPreset
import com.docscan.util.resizer.ResizeResultItem
import java.io.File
import java.text.DecimalFormat
import kotlin.math.roundToInt

// Dark Premium Theme Colors for Resizer
private val DarkBg: Color get() = ThemeManager.themeColor(Color(0xFF0F172A), Color(0xFFF8FAFC))
private val DarkSurface: Color get() = ThemeManager.themeColor(Color(0xFF1E293B), Color(0xFFFFFFFF))
private val DarkSurfaceElevated: Color get() = ThemeManager.themeColor(Color(0xFF334155), Color(0xFFF1F5F9))
private val DarkCardBorder: Color get() = ThemeManager.themeColor(Color(0xFF475569), Color(0xFFE2E8F0)).copy(alpha = 0.5f)
private val PrimaryAccent = Color(0xFF0D9488) // Teal-Emerald Accent
private val PrimaryAccentLight = Color(0xFF14B8A6)
private val AccentSoft = Color(0xFF14B8A6).copy(alpha = 0.15f)
private val TextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFF8FAFC), Color(0xFF0F172A))
private val TextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF94A3B8), Color(0xFF475569))
private val TextMuted: Color get() = ThemeManager.themeColor(Color(0xFF64748B), Color(0xFF64748B))

@Composable
fun ImageResizerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImageResizerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Handle Toast notifications
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        when (uiState.currentStep) {
            ResizerStep.SELECT_MEDIA -> {
                ResizerHomeSelectScreen(
                    onNavigateBack = onNavigateBack,
                    onImagesPicked = { uris -> viewModel.onImagesSelected(uris) }
                )
            }
            ResizerStep.IMAGE_PREVIEW -> {
                ResizerImagePreviewScreen(
                    uiState = uiState,
                    onBack = { viewModel.setStep(ResizerStep.SELECT_MEDIA) },
                    onEditClick = { viewModel.setStep(ResizerStep.PHOTO_EDIT) },
                    onResizeClick = { viewModel.setStep(ResizerStep.RESIZE_SETTINGS) },
                    onSaveDirect = { viewModel.executeResize() },
                    onSelectImageIndex = { idx -> viewModel.selectImageIndex(idx) },
                    onRemoveImage = { idx -> viewModel.removeImage(idx) },
                    onAddMore = { uris -> viewModel.addImages(uris) }
                )
            }
            ResizerStep.PHOTO_EDIT -> {
                ResizerPhotoEditScreen(
                    uiState = uiState,
                    editedBitmap = viewModel.getEditedBitmap(),
                    onClose = { viewModel.setStep(ResizerStep.IMAGE_PREVIEW) },
                    onApply = { viewModel.applyEditsAndConfirm() },
                    onResetAll = { viewModel.resetAllEdits() },
                    onResetAdjustments = { viewModel.resetAdjustments() },
                    onSelectTool = { tool -> viewModel.setActiveEditTool(tool) },
                    onSelectCropPreset = { preset -> viewModel.setCropPreset(preset) },
                    onSelectFilterEffect = { filter -> viewModel.setFilterEffect(filter) },
                    onRotateCw = { viewModel.rotate90Cw() },
                    onRotateCcw = { viewModel.rotate90Ccw() },
                    onRotate180 = { viewModel.rotate180() },
                    onFlipH = { viewModel.toggleFlipH() },
                    onFlipV = { viewModel.toggleFlipV() },
                    onBrightnessChange = { b -> viewModel.setBrightness(b) },
                    onContrastChange = { c -> viewModel.setContrast(c) },
                    onSaturationChange = { s -> viewModel.setSaturation(s) }
                )
            }
            ResizerStep.RESIZE_SETTINGS -> {
                ResizerSettingsScreen(
                    uiState = uiState,
                    onBack = { viewModel.setStep(ResizerStep.IMAGE_PREVIEW) },
                    onQuickPresetSelected = { preset -> viewModel.setQuickPreset(preset) },
                    onCustomModeSelected = { mode -> viewModel.setCustomResizeMode(mode) },
                    onPercentageChanged = { pct -> viewModel.setPercentage(pct) },
                    onWidthChanged = { w -> viewModel.setCustomWidth(w) },
                    onHeightChanged = { h -> viewModel.setCustomHeight(h) },
                    onToggleAspectLock = { viewModel.toggleAspectRatioLock() },
                    onDimensionPresetSelected = { w, h -> viewModel.setDimensionPreset(w, h) },
                    onTargetFileSizeChanged = { kb -> viewModel.setTargetFileSizeKb(kb) },
                    onOutputFormatChanged = { fmt -> viewModel.setOutputFormat(fmt) },
                    onQualityPresetChanged = { q -> viewModel.setQualityPreset(q) },
                    onExecuteResize = { viewModel.executeResize() }
                )
            }
            ResizerStep.RESIZING_PROGRESS -> {
                ResizerProgressScreen(uiState = uiState)
            }
            ResizerStep.RESIZE_RESULT -> {
                ResizerResultScreen(
                    uiState = uiState,
                    onBack = { viewModel.setStep(ResizerStep.IMAGE_PREVIEW) },
                    onSave = { viewModel.saveResultsToGallery() },
                    onShare = {
                        shareResizedImages(context, uiState.results)
                    },
                    onEditAgain = {
                        viewModel.setStep(ResizerStep.RESIZE_SETTINGS)
                    },
                    onResizeAnother = {
                        viewModel.resetToStart()
                    }
                )
            }
        }
    }
}

// =========================================================================
// SCREEN 1: RESIZER HOME (SELECT PHOTOS / TAKE PHOTO)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizerHomeSelectScreen(
    onNavigateBack: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // System Photo Picker (Multiple selection)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImagesPicked(uris)
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            onImagesPicked(listOf(tempCameraUri!!))
        }
    }

    // Helper to initiate camera capture
    fun launchCameraInternal() {
        try {
            val tempDir = FileUtils.getTempDir(context)
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, FileUtils.generateFileName("CAM", "jpg"))
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                tempFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open camera: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("resizer_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Centered Header Title
            Text(
                text = "Photo & Picture Resizer",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = TextPrimary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Fast, high-quality image resizing & compression",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Selection Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Option 1: Select Photos
                    SelectionActionRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Select Photos",
                        subtitle = "Choose photos from your device",
                        testTag = "select_photos_option",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    HorizontalDivider(color = DarkCardBorder.copy(alpha = 0.4f), thickness = 1.dp)

                    // Option 2: Take a Photo
                    SelectionActionRow(
                        icon = Icons.Default.AddAPhoto,
                        title = "Take a Photo",
                        subtitle = "Capture a new photo and resize it",
                        testTag = "take_photo_option",
                        onClick = {
                            val hasCameraPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasCameraPerm) {
                                launchCameraInternal()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Badge / Info Pill
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = DarkSurfaceElevated.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryAccentLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Supports JPG, PNG, WEBP & Multi-Select",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(AccentSoft, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryAccentLight,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary
                )
            )
        }
    }
}

// =========================================================================
// SCREEN 2: IMAGE PREVIEW
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizerImagePreviewScreen(
    uiState: ImageResizerUiState,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onResizeClick: () -> Unit,
    onSaveDirect: () -> Unit,
    onSelectImageIndex: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onAddMore: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val currentImage = uiState.selectedImages.getOrNull(uiState.currentImageIndex)

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Resize",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preview_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentImage?.let { meta ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, meta.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                            }
                        },
                        modifier = Modifier.testTag("preview_share_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Multi-image selection strip if more than 1 image
            if (uiState.selectedImages.size > 1) {
                MultiImageThumbnailStrip(
                    images = uiState.selectedImages,
                    currentIndex = uiState.currentImageIndex,
                    onSelect = onSelectImageIndex,
                    onRemove = onRemoveImage,
                    onAddMore = onAddMore
                )
            }

            // Compact Information Card
            if (currentImage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DarkSurfaceElevated, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = PrimaryAccentLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = currentImage.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentImage.width} × ${currentImage.height} px • ${formatFileSize(currentImage.sizeBytes)} • ${currentImage.format}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Main Interactive Pinch-Zoom Preview Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            if (zoomScale > 1f) {
                                panOffset = Offset(
                                    x = panOffset.x + pan.x,
                                    y = panOffset.y + pan.y
                                )
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentImage != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentImage.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Selected Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )
                }

                if (zoomScale > 1.05f) {
                    IconButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(DarkSurface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Zoom",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Modern Floating Bottom Action Panel: [ Edit ] [ Resize (Primary) ] [ Save ]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit Button
                    FilledTonalButton(
                        onClick = onEditClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("preview_edit_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Edit", fontWeight = FontWeight.Medium)
                    }

                    // Resize Primary Highlighted Action
                    Button(
                        onClick = onResizeClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .testTag("preview_resize_primary_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Resize",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Save Button
                    FilledTonalButton(
                        onClick = onSaveDirect,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("preview_save_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Save", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiImageThumbnailStrip(
    images: List<ImageMetaInfo>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddMore: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddMore(uris)
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(images) { index, meta ->
            val isSelected = index == currentIndex
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) PrimaryAccentLight else DarkCardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(index) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(meta.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Delete chip on thumb
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add More",
                    tint = TextSecondary
                )
            }
        }
    }
}

// =========================================================================
// SCREEN 3: EDIT PHOTO (Crop, Rotate, Flip, Adjustments)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizerPhotoEditScreen(
    uiState: ImageResizerUiState,
    editedBitmap: Bitmap?,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onResetAll: () -> Unit,
    onResetAdjustments: () -> Unit,
    onSelectTool: (EditTool) -> Unit,
    onSelectCropPreset: (CropPreset) -> Unit,
    onSelectFilterEffect: (FilterEffect) -> Unit,
    onRotateCw: () -> Unit,
    onRotateCcw: () -> Unit,
    onRotate180: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit
) {
    // Dynamic real-time Color Matrix computation for live 60fps adjustments
    val liveColorMatrix = remember(uiState.brightness, uiState.contrast, uiState.saturation) {
        val matrix = ColorMatrix()
        if (uiState.saturation != 1f) {
            matrix.setToSaturation(uiState.saturation.coerceIn(0f, 3f))
        }
        val scale = uiState.contrast.coerceIn(0.2f, 3f)
        val translate = (uiState.brightness * 1.5f).coerceIn(-150f, 150f)
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.timesAssign(contrastMatrix)
        matrix
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Photo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                },
                actions = {
                    // Reset to original
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Reset", color = TextSecondary, fontSize = 13.sp)
                    }

                    // Apply Edits
                    IconButton(
                        onClick = onApply,
                        modifier = Modifier.testTag("edit_done_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = "Apply", tint = PrimaryAccentLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Edit Canvas with Live Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                if (editedBitmap != null) {
                    Image(
                        bitmap = editedBitmap.asImageBitmap(),
                        contentDescription = "Editing Canvas",
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(liveColorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = uiState.rotationDegrees.toFloat()
                                scaleX = if (uiState.flipH) -1f else 1f
                                scaleY = if (uiState.flipV) -1f else 1f
                            }
                    )

                    // Crop Overlay with Aspect Ratio Guide if Crop tool is active
                    if (uiState.activeEditTool == EditTool.CROP) {
                        InteractiveCropGuideOverlay(preset = uiState.selectedCropPreset)
                    }
                } else {
                    CircularProgressIndicator(color = PrimaryAccentLight)
                }
            }

            // Secondary Controls for Active Tool (e.g. Crop presets, Rotation buttons, Sliders, Filter presets)
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (uiState.activeEditTool) {
                        EditTool.CROP -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Aspect Ratio: ${uiState.selectedCropPreset.label}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(CropPreset.values().size) { idx ->
                                        val preset = CropPreset.values()[idx]
                                        val selected = uiState.selectedCropPreset == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectCropPreset(preset) },
                                            label = { Text(preset.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryAccent,
                                                selectedLabelColor = Color.White,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        EditTool.ROTATE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onRotateCcw,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rotate -90°", modifier = Modifier.size(18.dp).graphicsLayer { scaleX = -1f })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("-90°", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = onRotateCw,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Rotate +90°", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+90°", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = onRotate180,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rotate 180°", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("180°", fontSize = 13.sp)
                                }
                            }
                        }

                        EditTool.FLIP -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = uiState.flipH,
                                    onClick = onFlipH,
                                    label = { Text("Flip Horizontal") },
                                    leadingIcon = { Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryAccent,
                                        selectedLabelColor = Color.White,
                                        containerColor = DarkSurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )

                                FilterChip(
                                    selected = uiState.flipV,
                                    onClick = onFlipV,
                                    label = { Text("Flip Vertical") },
                                    leadingIcon = { Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryAccent,
                                        selectedLabelColor = Color.White,
                                        containerColor = DarkSurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        EditTool.FILTERS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Color & Contrast Filters",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(FilterEffect.values().size) { idx ->
                                        val filter = FilterEffect.values()[idx]
                                        val selected = uiState.activeFilterEffect == filter
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectFilterEffect(filter) },
                                            label = { Text(filter.displayName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryAccent,
                                                selectedLabelColor = Color.White,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        EditTool.BRIGHTNESS -> {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Brightness: ${uiState.brightness.toInt()}", color = TextSecondary, fontSize = 12.sp)
                                    TextButton(onClick = { onBrightnessChange(0f) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Reset", color = PrimaryAccentLight, fontSize = 11.sp)
                                    }
                                }
                                Slider(
                                    value = uiState.brightness,
                                    onValueChange = onBrightnessChange,
                                    valueRange = -50f..50f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryAccentLight,
                                        activeTrackColor = PrimaryAccent
                                    )
                                )
                            }
                        }

                        EditTool.CONTRAST -> {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Contrast: ${(uiState.contrast * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                                    TextButton(onClick = { onContrastChange(1f) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Reset", color = PrimaryAccentLight, fontSize = 11.sp)
                                    }
                                }
                                Slider(
                                    value = uiState.contrast,
                                    onValueChange = onContrastChange,
                                    valueRange = 0.5f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryAccentLight,
                                        activeTrackColor = PrimaryAccent
                                    )
                                )
                            }
                        }

                        EditTool.SATURATION -> {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Saturation: ${(uiState.saturation * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                                    TextButton(onClick = { onSaturationChange(1f) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Reset", color = PrimaryAccentLight, fontSize = 11.sp)
                                    }
                                }
                                Slider(
                                    value = uiState.saturation,
                                    onValueChange = onSaturationChange,
                                    valueRange = 0.0f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryAccentLight,
                                        activeTrackColor = PrimaryAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Primary Bottom Editing Toolbar
            Surface(
                color = DarkBg,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val tools = listOf(
                        Triple(EditTool.CROP, Icons.Default.Crop, "Crop"),
                        Triple(EditTool.ROTATE, Icons.Default.Rotate90DegreesCw, "Rotate"),
                        Triple(EditTool.FLIP, Icons.Default.Flip, "Flip"),
                        Triple(EditTool.FILTERS, Icons.Default.AutoAwesome, "Filters"),
                        Triple(EditTool.BRIGHTNESS, Icons.Default.Brightness6, "Brightness"),
                        Triple(EditTool.CONTRAST, Icons.Default.Contrast, "Contrast"),
                        Triple(EditTool.SATURATION, Icons.Default.Palette, "Saturation")
                    )

                    items(tools.size) { i ->
                        val (tool, icon, label) = tools[i]
                        val isSelected = uiState.activeEditTool == tool
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectTool(tool) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) PrimaryAccentLight else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) PrimaryAccentLight else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropGuideOverlay(preset: CropPreset) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Compute Aspect Ratio box
        val aspectModifier = if (preset.ratioW > 0f && preset.ratioH > 0f) {
            Modifier
                .fillMaxSize()
                .aspectRatio(preset.ratioW / preset.ratioH, matchHeightConstraintsFirst = true)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = aspectModifier
                .border(2.dp, PrimaryAccentLight, RoundedCornerShape(4.dp))
        ) {
            // Rule of thirds lines
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = PrimaryAccentLight.copy(alpha = 0.35f), thickness = 1.dp)
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = PrimaryAccentLight.copy(alpha = 0.35f), thickness = 1.dp)
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(PrimaryAccentLight.copy(alpha = 0.35f)))
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(PrimaryAccentLight.copy(alpha = 0.35f)))
                Spacer(modifier = Modifier.weight(1f))
            }

            // Ratio badge at top left of crop box
            if (preset != CropPreset.FREE && preset != CropPreset.ORIGINAL) {
                Surface(
                    color = PrimaryAccent,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = preset.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// SCREEN 4: PREMIUM RESIZE INTERFACE (Quick Resize, Custom, Advanced, Sticky CTA)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizerSettingsScreen(
    uiState: ImageResizerUiState,
    onBack: () -> Unit,
    onQuickPresetSelected: (QuickPreset) -> Unit,
    onCustomModeSelected: (CustomResizeMode) -> Unit,
    onPercentageChanged: (Int) -> Unit,
    onWidthChanged: (Int) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onToggleAspectLock: () -> Unit,
    onDimensionPresetSelected: (Int, Int) -> Unit,
    onTargetFileSizeChanged: (Long) -> Unit,
    onOutputFormatChanged: (OutputImageFormat) -> Unit,
    onQualityPresetChanged: (QualityPreset) -> Unit,
    onExecuteResize: () -> Unit
) {
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    val primaryImage = uiState.selectedImages.getOrNull(uiState.currentImageIndex)

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Resize Image",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            // Sticky Bottom Bar with Dynamic Resize CTA
            Surface(
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                val buttonText = when (uiState.resizeSettings.quickPreset) {
                    QuickPreset.SMALL -> "Resize to Small"
                    QuickPreset.MEDIUM -> "Resize to Medium"
                    QuickPreset.LARGE -> "Resize to Large"
                    QuickPreset.NONE -> "Resize Image"
                }

                Button(
                    onClick = onExecuteResize,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(4.dp)
                        .testTag("execute_resize_cta_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Description
            Column {
                Text(
                    text = "Choose how you want to resize your image",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            // SECTION 1: QUICK RESIZE (3 Compact segmented cards)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK RESIZE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryAccentLight,
                        letterSpacing = 1.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickPresetCard(
                        title = "Small",
                        subtitle = "For sharing",
                        estimatedSize = "~${formatFileSize(uiState.selectedImages.firstOrNull()?.let { (it.sizeBytes * 0.15f).toLong() } ?: 50 * 1024L)}",
                        isSelected = uiState.resizeSettings.quickPreset == QuickPreset.SMALL,
                        onClick = { onQuickPresetSelected(QuickPreset.SMALL) },
                        modifier = Modifier.weight(1f),
                        testTag = "quick_small_preset"
                    )

                    QuickPresetCard(
                        title = "Medium",
                        subtitle = "Balanced",
                        estimatedSize = "~${formatFileSize(uiState.selectedImages.firstOrNull()?.let { (it.sizeBytes * 0.35f).toLong() } ?: 100 * 1024L)}",
                        isSelected = uiState.resizeSettings.quickPreset == QuickPreset.MEDIUM,
                        onClick = { onQuickPresetSelected(QuickPreset.MEDIUM) },
                        modifier = Modifier.weight(1f),
                        testTag = "quick_medium_preset"
                    )

                    QuickPresetCard(
                        title = "Large",
                        subtitle = "High quality",
                        estimatedSize = "~${formatFileSize(uiState.selectedImages.firstOrNull()?.let { (it.sizeBytes * 0.65f).toLong() } ?: 200 * 1024L)}",
                        isSelected = uiState.resizeSettings.quickPreset == QuickPreset.LARGE,
                        onClick = { onQuickPresetSelected(QuickPreset.LARGE) },
                        modifier = Modifier.weight(1f),
                        testTag = "quick_large_preset"
                    )
                }
            }

            // SECTION 2: CUSTOM RESIZE (Segmented Tabs: Percentage, Dimensions, File Size)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Custom Resize",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    // Mode Selector Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isCustomActive = uiState.resizeSettings.quickPreset == QuickPreset.NONE
                        val modes = listOf(
                            CustomResizeMode.PERCENTAGE to "Percentage",
                            CustomResizeMode.DIMENSIONS to "Dimensions",
                            CustomResizeMode.FILE_SIZE to "File Size"
                        )

                        modes.forEach { (mode, label) ->
                            val isSelected = isCustomActive && uiState.resizeSettings.customMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryAccent else Color.Transparent)
                                    .clickable { onCustomModeSelected(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Controls for Selected Mode
                    when {
                        uiState.resizeSettings.quickPreset == QuickPreset.NONE && uiState.resizeSettings.customMode == CustomResizeMode.PERCENTAGE -> {
                            PercentageModeControls(
                                currentPercentage = uiState.resizeSettings.percentage,
                                onPercentageChanged = onPercentageChanged
                            )
                        }
                        uiState.resizeSettings.quickPreset == QuickPreset.NONE && uiState.resizeSettings.customMode == CustomResizeMode.DIMENSIONS -> {
                            DimensionsModeControls(
                                originalWidth = primaryImage?.width ?: 1000,
                                originalHeight = primaryImage?.height ?: 1000,
                                currentWidth = uiState.resizeSettings.customWidth,
                                currentHeight = uiState.resizeSettings.customHeight,
                                isLocked = uiState.resizeSettings.isAspectRatioLocked,
                                onWidthChanged = onWidthChanged,
                                onHeightChanged = onHeightChanged,
                                onToggleLock = onToggleAspectLock,
                                onPresetSelected = onDimensionPresetSelected
                            )
                        }
                        uiState.resizeSettings.quickPreset == QuickPreset.NONE && uiState.resizeSettings.customMode == CustomResizeMode.FILE_SIZE -> {
                            FileSizeModeControls(
                                currentSizeKb = uiState.resizeSettings.targetFileSizeKb,
                                onSizeKbChanged = onTargetFileSizeChanged
                            )
                        }
                        else -> {
                            Text(
                                text = "Select a tab above to customize dimensions, scale, or target file size.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }
                }
            }

            // SECTION 3: ADVANCED SETTINGS (Collapsed by Default)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp))
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .testTag("advanced_settings_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Advanced Output Settings",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Output Format
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Output Format", color = TextSecondary, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutputImageFormat.values().forEach { fmt ->
                                        val isSel = uiState.resizeSettings.outputFormat == fmt
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { onOutputFormatChanged(fmt) },
                                            label = { Text(fmt.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryAccent,
                                                selectedLabelColor = Color.White,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            // Image Quality
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Image Quality", color = TextSecondary, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QualityPreset.values().forEach { q ->
                                        val isSel = uiState.resizeSettings.qualityPreset == q
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { onQualityPresetChanged(q) },
                                            label = { Text(q.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryAccent,
                                                selectedLabelColor = Color.White,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: LIVE OUTPUT SUMMARY
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentSoft)
                    .border(1.dp, PrimaryAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NEW IMAGE ESTIMATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccentLight,
                                letterSpacing = 1.sp
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentSoft
                        ) {
                            Text(
                                text = uiState.liveSummary.qualityLabel,
                                style = MaterialTheme.typography.labelSmall.copy(color = PrimaryAccentLight),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Resolution", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                            Text(
                                text = "${uiState.liveSummary.newWidth} × ${uiState.liveSummary.newHeight} px",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Estimated Size", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                            Text(
                                text = "~${formatFileSize(uiState.liveSummary.estimatedBytes)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryAccentLight
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun QuickPresetCard(
    title: String,
    subtitle: String,
    estimatedSize: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PrimaryAccentLight else DarkCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PrimaryAccentLight else TextPrimary
                    )
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PrimaryAccentLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = estimatedSize,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) TextPrimary else TextMuted
                )
            )
        }
    }
}

@Composable
private fun PercentageModeControls(
    currentPercentage: Int,
    onPercentageChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(25, 50, 75, 100).forEach { pct ->
                val isSel = currentPercentage == pct
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) PrimaryAccent else DarkSurfaceElevated)
                        .clickable { onPercentageChanged(pct) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else TextPrimary
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Resize Scale", color = TextSecondary, fontSize = 13.sp)
            Text("$currentPercentage%", color = PrimaryAccentLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Slider(
            value = currentPercentage.toFloat(),
            onValueChange = { onPercentageChanged(it.roundToInt()) },
            valueRange = 10f..150f,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryAccentLight,
                activeTrackColor = PrimaryAccent
            )
        )
    }
}

@Composable
private fun DimensionsModeControls(
    originalWidth: Int,
    originalHeight: Int,
    currentWidth: Int,
    currentHeight: Int,
    isLocked: Boolean,
    onWidthChanged: (Int) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onToggleLock: () -> Unit,
    onPresetSelected: (Int, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = if (currentWidth > 0) currentWidth.toString() else "",
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onWidthChanged(num)
                },
                label = { Text("Width (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryAccentLight,
                    unfocusedBorderColor = DarkCardBorder
                ),
                singleLine = true
            )

            // Aspect Lock Toggle
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .background(if (isLocked) AccentSoft else DarkSurfaceElevated, CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Aspect Ratio Lock",
                    tint = if (isLocked) PrimaryAccentLight else TextSecondary
                )
            }

            OutlinedTextField(
                value = if (currentHeight > 0) currentHeight.toString() else "",
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onHeightChanged(num)
                },
                label = { Text("Height (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryAccentLight,
                    unfocusedBorderColor = DarkCardBorder
                ),
                singleLine = true
            )
        }

        // Quick dimension presets
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(
                "Original" to (originalWidth to originalHeight),
                "HD (720p)" to (1280 to 720),
                "Full HD" to (1920 to 1080),
                "2K" to (2560 to 1440)
            )
            items(presets.size) { i ->
                val (label, pair) = presets[i]
                FilterChip(
                    selected = currentWidth == pair.first && currentHeight == pair.second,
                    onClick = { onPresetSelected(pair.first, pair.second) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryAccent,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun FileSizeModeControls(
    currentSizeKb: Long,
    onSizeKbChanged: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickKb = listOf(50L, 100L, 200L, 500L, 1024L)
            quickKb.forEach { kb ->
                val label = if (kb >= 1024L) "1 MB" else "$kb KB"
                val isSel = currentSizeKb == kb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) PrimaryAccent else DarkSurfaceElevated)
                        .clickable { onSizeKbChanged(kb) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else TextPrimary
                        )
                    )
                }
            }
        }

        OutlinedTextField(
            value = if (currentSizeKb > 0) currentSizeKb.toString() else "",
            onValueChange = { str ->
                val num = str.filter { it.isDigit() }.toLongOrNull() ?: 0L
                onSizeKbChanged(num)
            },
            label = { Text("Target File Size (KB)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = PrimaryAccentLight,
                unfocusedBorderColor = DarkCardBorder
            ),
            singleLine = true
        )

        Text(
            text = "The final size may vary slightly depending on image content.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
        )
    }
}

// =========================================================================
// SCREEN 5: RESIZING PROGRESS
// =========================================================================

@Composable
private fun ResizerProgressScreen(uiState: ImageResizerUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, DarkCardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = PrimaryAccentLight,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "Resizing Images...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                if (uiState.processingTotalCount > 1) {
                    Text(
                        text = "Processing ${uiState.processingCurrentIndex} of ${uiState.processingTotalCount}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                } else {
                    Text(
                        text = "Applying high-quality scaling and optimization",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =========================================================================
// SCREEN 6: RESIZE RESULT (Comparison, Save, Share, Edit Again)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizerResultScreen(
    uiState: ImageResizerUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEditAgain: () -> Unit,
    onResizeAnother: () -> Unit
) {
    val context = LocalContext.current
    val successResults = uiState.results.filter { it.isSuccess }
    val firstSuccess = successResults.firstOrNull()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Resize Result",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("result_share_top_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            // Bottom Action Bar: [ Share ] [ Save (Primary) ] [ Edit Again ]
            Surface(
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Share
                        FilledTonalButton(
                            onClick = onShare,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkSurfaceElevated,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("result_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        // Save (Primary)
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isSavedToGallery) Color(0xFF10B981) else PrimaryAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("result_save_primary_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSavedToGallery) Icons.Default.Check else Icons.Default.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isSavedToGallery) "Saved" else "Save to Gallery",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onEditAgain,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Change Settings", color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onResizeAnother,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resize Another", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Resized Image
            if (firstSuccess?.outputUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(firstSuccess.outputUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Resized Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Comparison Card (ORIGINAL vs NEW vs SAVED)
            if (firstSuccess != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "IMAGE COMPARISON",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccentLight,
                                letterSpacing = 1.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Original Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ORIGINAL", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${firstSuccess.originalMeta.width} × ${firstSuccess.originalMeta.height} px",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = formatFileSize(firstSuccess.originalMeta.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            // New Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NEW", style = MaterialTheme.typography.labelSmall.copy(color = PrimaryAccentLight))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${firstSuccess.outputWidth} × ${firstSuccess.outputHeight} px",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryAccentLight
                                    )
                                )
                                Text(
                                    text = formatFileSize(firstSuccess.outputSizeBytes),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                )
                            }

                            // Saved Badge
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SAVED", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${firstSuccess.savedPercentage}% smaller",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Batch status list if multiple
            if (uiState.results.size > 1) {
                Text(
                    text = "Batch Processing Results (${uiState.results.count { it.isSuccess }} / ${uiState.results.size} successful)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.results.forEachIndexed { i, res ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = res.originalMeta.name,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (res.isSuccess) {
                                    Text(
                                        text = "${res.outputWidth}×${res.outputHeight} • ${formatFileSize(res.outputSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = PrimaryAccentLight)
                                    )
                                } else {
                                    Text(
                                        text = "Failed",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEF4444))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun shareResizedImages(context: Context, results: List<ResizeResultItem>) {
    val successList = results.filter { it.isSuccess && it.outputFilePath != null }
    if (successList.isEmpty()) return

    try {
        if (successList.size == 1) {
            val file = File(successList.first().outputFilePath!!)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Resized Image"))
        } else {
            val uris = ArrayList<Uri>()
            for (item in successList) {
                val file = File(item.outputFilePath!!)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                uris.add(uri)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Resized Images"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share images", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
        else -> "$bytes B"
    }
}
