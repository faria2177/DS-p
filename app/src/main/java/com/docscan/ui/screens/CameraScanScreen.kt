package com.docscan.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.data.model.BarcodeValueType
import com.docscan.data.model.IdCardType
import com.docscan.data.model.MainScanMode
import com.docscan.data.model.ParsedBarcode
import com.docscan.data.model.QrScanSubMode
import com.docscan.data.model.ScanMode
import com.docscan.data.model.ScannerFeatureMode
import com.docscan.ui.components.CreateQrBottomSheet
import com.docscan.ui.components.GalleryCodeSelectionDialog
import com.docscan.ui.components.IdCardIntroOverlay
import com.docscan.ui.components.QrContinuousScanBar
import com.docscan.ui.components.QrHistoryBottomSheet
import com.docscan.ui.components.QrResultBottomSheet
import com.docscan.ui.components.QrScannerOverlay
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.scanner.BlueEdgeDetector
import com.docscan.scanner.CameraMotionDetector
import com.docscan.scanner.CameraState
import com.docscan.scanner.QuadTracker
import com.docscan.ui.components.BlueEdgeOverlay
import com.docscan.util.BarcodeAnalyzerHelper
import com.docscan.scanner.PremiumDetectionResult
import com.docscan.util.DetectionState
import com.docscan.util.FileUtils
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

// CamScanner Teal Brand Accents
val ScannerTeal = Color(0xFF00BFA5)
val ScannerTealBright = Color(0xFF00E5FF)
val ScannerDarkOverlay = Color(0x99000000)

data class AlignmentGuideInfo(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bg: Color,
    val fg: Color,
    val isHighlighted: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCrop: () -> Unit,
    onNavigateToDocumentPreview: () -> Unit,
    onNavigateToNidSetup: () -> Unit = {},
    onNidSideCaptured: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val activeFeatureMode by viewModel.activeFeatureMode.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val isMagicEnhanceEnabled by viewModel.isMagicEnhanceEnabled.collectAsStateWithLifecycle()
    val isHdModeEnabled by viewModel.isHdModeEnabled.collectAsStateWithLifecycle()
    val isAutoCropEnabled by viewModel.isAutoCropEnabled.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val nidProcessingStage by viewModel.nidProcessingStage.collectAsStateWithLifecycle()
    val showGridGuidelines by viewModel.showGridGuidelines.collectAsStateWithLifecycle()
    val idCardStep by viewModel.idCardStep.collectAsStateWithLifecycle()
    val isNidSetupCapture by viewModel.isNidSetupCapture.collectAsStateWithLifecycle()
    val selectedIdCardType by viewModel.selectedIdCardType.collectAsStateWithLifecycle()
    val showIdCardIntro by viewModel.showIdCardIntro.collectAsStateWithLifecycle()
    val isNidCaptureMode = (scanMode == ScanMode.ID_CARD || activeFeatureMode == ScannerFeatureMode.ID_CARDS)

    // QR & Barcode Mode State Observers
    val mainScanMode by viewModel.mainScanMode.collectAsStateWithLifecycle()
    // Only the document camera is letterboxed by the top and bottom controls.
    // A full-height FILL_CENTER preview crops the 4:3 sensor horizontally and
    // makes a page look artificially zoomed-in on tall phones.
    val useDocumentViewfinder = mainScanMode == MainScanMode.DOCUMENT && !isNidCaptureMode
    // CameraX binds its analyzer once; read the latest mode without rebinding the camera.
    val latestMainScanMode by rememberUpdatedState(mainScanMode)
    val qrScanSubMode by viewModel.qrScanSubMode.collectAsStateWithLifecycle()
    val isWideBarcodeMode by viewModel.isWideBarcodeMode.collectAsStateWithLifecycle()
    val activeScannedBarcode by viewModel.activeScannedBarcode.collectAsStateWithLifecycle()
    val continuousScannedList by viewModel.continuousScannedList.collectAsStateWithLifecycle()
    val showQrHistorySheet by viewModel.showQrHistorySheet.collectAsStateWithLifecycle()
    val showCreateQrSheet by viewModel.showCreateQrSheet.collectAsStateWithLifecycle()
    val galleryParsedCodes by viewModel.galleryParsedCodes.collectAsStateWithLifecycle()
    val showGalleryMultipleCodesDialog by viewModel.showGalleryMultipleCodesDialog.collectAsStateWithLifecycle()
    val qrHistoryList by viewModel.qrHistoryList.collectAsStateWithLifecycle()
    val isQrOnlyMode by viewModel.isQrOnlyMode.collectAsStateWithLifecycle()

    BackHandler {
        if (isQrOnlyMode) {
            viewModel.isQrOnlyMode.value = false
            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
        }
        onNavigateBack()
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) } // OFF, AUTO, ON
    var isTorchOn by remember { mutableStateOf(false) }
    val latestTorchOn by rememberUpdatedState(isTorchOn)
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAllFeaturesSheet by remember { mutableStateOf(false) }
    var isShutterBusy by remember { mutableStateOf(false) }

    // Camera Lens, Zoom & Tap-to-Focus Controls
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var showLowLightHint by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var rootBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var previewBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val barcodeScanner = remember { BarcodeAnalyzerHelper.createScanner() }

    // Real-time edge detection frame state
    // Start with EMPTY corners so no blue box is drawn until a real document
    // is found while the camera is held steady.
    var detectedState by remember { mutableStateOf(DetectionState.SEARCHING_DOCUMENT) }
    var detectedCorners by remember { mutableStateOf(emptyList<Offset>()) }
    // Upright aspect ratio (width/height) of the live camera analysis frame. The
    // PreviewView uses FILL_CENTER (center-crop) scaling, so the overlay needs this
    // to reproduce the same crop when mapping normalized corners to screen pixels —
    // otherwise the drawn box drifts away from the real document as soon as the
    // screen's aspect ratio differs from the camera's (true on almost every phone).
    var detectedFrameAspect by remember { mutableStateOf(3f / 4f) }
    // Physical camera stillness detector for auto-capture gating
    val motionDetector = remember { CameraMotionDetector() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Shutter flash visual feedback
    val flashAnim = remember { Animatable(0f) }

    // Dedicated Single-Image Gallery Import for QR/Barcode scanning
    val qrGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmap = FileUtils.loadBitmapsFromUri(context, uri).firstOrNull()
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        viewModel.processGalleryBarcodeBitmap(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load image file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission needed for scanning", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Photo Gallery Import launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (isNidCaptureMode && !isNidSetupCapture) {
                viewModel.importIdCardUris(uris) { onNavigateToDocumentPreview() }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    val bitmaps = mutableListOf<Bitmap>()
                    uris.forEach { uri ->
                        val loaded = FileUtils.loadBitmapsFromUri(context, uri)
                        bitmaps.addAll(loaded)
                    }
                    if (bitmaps.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isNidCaptureMode && isNidSetupCapture) {
                                // Legacy Scan ID landing: import into its selected side.
                                viewModel.importNidSetupSide(bitmaps.first()) {
                                    onNidSideCaptured()
                                }
                            } else {
                                viewModel.onMultipleImagesImported(
                                    bitmaps = bitmaps,
                                    onNavigateToCrop = { onNavigateToCrop() },
                                    onNavigateToPreview = { onNavigateToDocumentPreview() }
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not load selected file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Document Files Import launcher (Supports PDF and Images)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    val loaded = FileUtils.loadBitmapsFromUri(context, uri)
                    bitmaps.addAll(loaded)
                }
                if (bitmaps.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onMultipleImagesImported(
                            bitmaps = bitmaps,
                            onNavigateToCrop = { onNavigateToCrop() },
                            onNavigateToPreview = { onNavigateToDocumentPreview() }
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load PDF or image file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun triggerShutterFlash() {
        coroutineScope.launch {
            flashAnim.snapTo(0.85f)
            flashAnim.animateTo(0f, animationSpec = tween(durationMillis = 200))
        }
    }

    fun centeredNidFallbackCorners(bitmap: Bitmap): List<Offset> {
        // Keep a true ID-1/NID aspect ratio even when live edge detection is unavailable.
        // Coordinates are normalized against the actual captured bitmap, not the phone screen.
        val targetRatio = 1.586f
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val xSpan = 0.88f
        val ySpan = (xSpan * imageRatio / targetRatio).coerceIn(0.34f, 0.82f)
        val left = (1f - xSpan) / 2f
        val top = (1f - ySpan) / 2f
        return listOf(
            Offset(left, top),
            Offset(left + xSpan, top),
            Offset(left + xSpan, top + ySpan),
            Offset(left, top + ySpan)
        )
    }

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (isShutterBusy || isProcessing) return
        isShutterBusy = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        triggerShutterFlash()

        val photoFile = File(FileUtils.getTempDir(context), FileUtils.generateFileName("SCAN_CAP"))
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (bitmap != null) {
                            // Always re-run high-resolution still detection on the
                            // captured photo. Live preview corners are low-res and
                            // frequently lock onto bedspread / inner ruled lines —
                            // the full still pipeline is far more accurate.
                            // ID Card mode keeps the fixed CR-80 frame.
                            val activeCorners = if (scanMode == ScanMode.ID_CARD && selectedIdCardType.isTwoSided) {
                                // Prefer the real live quad when the NID/card is detected.
                                // If detection is temporarily unavailable, fall back to the fixed capture frame.
                                if (detectedCorners.size == 4 &&
                                    (detectedState == DetectionState.DOCUMENT_DETECTED || detectedState == DetectionState.DOCUMENT_STABLE)
                                ) detectedCorners else centeredNidFallbackCorners(bitmap)
                            } else {
                                null  // forces EdgeDetector.detectDocumentCorners(bitmap)
                            }

                            withContext(Dispatchers.Main) {
                                isShutterBusy = false
                                viewModel.onImageCaptured(
                                    bitmap = bitmap,
                                    customCorners = activeCorners,
                                    onNavigateToCrop = { onNavigateToCrop() },
                                    onNavigateToPreview = { onNavigateToDocumentPreview() },
                                    onNidSideCaptured = { onNidSideCaptured() }
                                )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isShutterBusy = false
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isShutterBusy = false
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Clear tracking when the lens or scan mode changes. Run resets on the
    // analyzer's executor to avoid racing an in-flight image-processing frame.
    LaunchedEffect(lensFacing, mainScanMode) {
        detectedCorners = emptyList()
        detectedState = DetectionState.SEARCHING_DOCUMENT
        cameraExecutor.execute {
            motionDetector.reset()
            BlueEdgeDetector.PremiumQuadTracker.reset()
        }
    }

    // Effect to bind/rebind camera when lens or permission changes
    LaunchedEffect(lensFacing, hasCameraPermission, previewViewRef) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode)
                    .build()
                imageCapture = capture

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (latestMainScanMode == MainScanMode.QR_BARCODE) {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            val avgLum = BarcodeAnalyzerHelper.computeAverageLuminance(imageProxy)
                            coroutineScope.launch(Dispatchers.Main) {
                                showLowLightHint = (avgLum < 35.0 && !latestTorchOn)
                            }
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (barcodes.isNotEmpty()) {
                                        val first = barcodes.first()
                                        val parsed = BarcodeAnalyzerHelper.parseBarcode(first)
                                        coroutineScope.launch(Dispatchers.Main) {
                                            if (latestMainScanMode != MainScanMode.QR_BARCODE) return@launch
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.onBarcodeDetected(parsed)
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // frame ignored
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    } else {
                        // Run the same premium live quad detector in NID mode as well.
                        // The custom NID UI keeps a fixed visual guide, while these real corners
                        // drive the "Document detected" state and high-quality perspective crop.
                        val cameraState = motionDetector.processFrame(imageProxy)
                        val detection = try {
                            BlueEdgeDetector.analyzeImageProxy(imageProxy)
                        } finally {
                            imageProxy.close()
                        }

                        coroutineScope.launch(Dispatchers.Main) {
                            // Drop in-flight results from the previous scan mode.
                            if (latestMainScanMode == MainScanMode.QR_BARCODE) return@launch
                            if (detection.isDocumentDetected && detection.corners.size == 4) {
                                detectedCorners = detection.corners
                                detectedFrameAspect = detection.frameAspectRatio
                                detectedState = when {
                                    cameraState == CameraState.STABLE && detection.confidence >= 0.48f -> DetectionState.DOCUMENT_STABLE
                                    else -> DetectionState.DOCUMENT_DETECTED
                                }
                            } else {
                                detectedCorners = emptyList()
                                detectedState = DetectionState.SEARCHING_DOCUMENT
                            }
                        }
                    }
                }


                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { rootBoundsInRoot = it.boundsInRoot() }
            .pointerInput(previewViewRef) {
                detectTapGestures { offset ->
                    focusTapOffset = offset
                    previewViewRef?.let { pv ->
                        try {
                            val previewBounds = previewBoundsInRoot
                            val rootBounds = rootBoundsInRoot
                            val previewOffset = if (previewBounds != null && rootBounds != null) {
                                offset + rootBounds.topLeft - previewBounds.topLeft
                            } else offset
                            if (previewBounds != null &&
                                (previewOffset.x !in 0f..previewBounds.width ||
                                    previewOffset.y !in 0f..previewBounds.height)) {
                                return@let
                            }
                            val factory = pv.meteringPointFactory
                            val point = factory.createPoint(previewOffset.x, previewOffset.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val newZoom = (zoomRatio * zoom).coerceIn(1f, 8f)
                    zoomRatio = newZoom
                    camera?.cameraControl?.setLinearZoom((newZoom - 1f) / 7f)
                }
            }
    ) {
        if (hasCameraPermission && !useDocumentViewfinder) {
            // NID and barcode modes retain their original full-screen camera.
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { previewBoundsInRoot = it.boundsInRoot() },
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef = this
                    }
                }
            )

        } else if (!hasCameraPermission) {
            // Permission fallback message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = ScannerTeal,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Camera Access Required",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please allow camera permissions to scan documents and QR/barcodes.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ScannerTeal,
                        modifier = Modifier.clickable {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text(
                            "Enable Camera",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // Shutter Screen Flash
        if (flashAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAnim.value))
            )
        }

        // UI OVERLAYS (Top Controls + Viewfinder Area + Bottom Navigation Deck)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top Mode Switcher or Dedicated QR Top Bar
            if (isQrOnlyMode) {
                // When opened from Tools -> Scan Code, ONLY QR & Barcode option is shown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            viewModel.isQrOnlyMode.value = false
                            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
                            onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Tools",
                            tint = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x33000000),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300C48C))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF00C48C),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "QR & Barcode",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val nextTorch = !isTorchOn
                            isTorchOn = nextTorch
                            camera?.cameraControl?.enableTorch(nextTorch)
                        }
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) Color(0xFFFBBF24) else Color.White
                        )
                    }
                }
            }

            if (mainScanMode == MainScanMode.DOCUMENT) {
                // 1. TOP BAR CONTROLS
                if (isNidCaptureMode) {
                    NidReferenceTopBar(
                        isTorchOn = isTorchOn,
                        isHdEnabled = isHdModeEnabled,
                        onCloseClick = onNavigateBack,
                        onHdClick = { viewModel.isHdModeEnabled.value = !isHdModeEnabled },
                        onFlashClick = {
                            val nextTorch = !isTorchOn
                            isTorchOn = nextTorch
                            camera?.cameraControl?.enableTorch(nextTorch)
                        }
                    )
                } else {
                    ScannerTopBar(
                        flashMode = flashMode,
                        isTorchOn = isTorchOn,
                        isMagicEnhanceOn = isMagicEnhanceEnabled,
                        isHdOn = isHdModeEnabled,
                        onCloseClick = onNavigateBack,
                        onFlashClick = {
                            val nextMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                            flashMode = nextMode
                            imageCapture?.flashMode = nextMode
                        },
                        onMagicEnhanceClick = {
                            viewModel.isMagicEnhanceEnabled.value = !isMagicEnhanceEnabled
                        },
                        onHdClick = {
                            viewModel.isHdModeEnabled.value = !isHdModeEnabled
                        },
                        onMoreClick = { showMoreMenu = true },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // More Options Dropdown Menu (normal document scanner only)
                if (!isNidCaptureMode) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2022))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Grid Guidelines", color = Color.White, fontSize = 14.sp)
                                    Switch(
                                        checked = showGridGuidelines,
                                        onCheckedChange = { viewModel.showGridGuidelines.value = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ScannerTeal, checkedTrackColor = ScannerTeal.copy(alpha = 0.5f))
                                    )
                                }
                            },
                            onClick = { viewModel.showGridGuidelines.value = !showGridGuidelines }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Auto Edge Crop", color = Color.White, fontSize = 14.sp)
                                    Switch(
                                        checked = isAutoCropEnabled,
                                        onCheckedChange = { viewModel.isAutoCropEnabled.value = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ScannerTeal, checkedTrackColor = ScannerTeal.copy(alpha = 0.5f))
                                    )
                                }
                            },
                            onClick = { viewModel.isAutoCropEnabled.value = !isAutoCropEnabled }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Torch / Lamp", color = Color.White, fontSize = 14.sp)
                                    Switch(
                                        checked = isTorchOn,
                                        onCheckedChange = {
                                            isTorchOn = it
                                            camera?.cameraControl?.enableTorch(it)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ScannerTeal, checkedTrackColor = ScannerTeal.copy(alpha = 0.5f))
                                    )
                                }
                            },
                            onClick = {
                                val next = !isTorchOn
                                isTorchOn = next
                                camera?.cameraControl?.enableTorch(next)
                            }
                        )
                    }
                }

                // 2. ACTIVE CAMERA VIEWFINDER & EDGE DETECTION REGION (OR ID CARD INTRO OVERLAY)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    if (hasCameraPermission && useDocumentViewfinder) {
                        // Camera and quadrilateral use the identical center
                        // viewport. This avoids the full-screen center crop
                        // while retaining accurate preview-to-edge mapping.
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { previewBoundsInRoot = it.boundsInRoot() },
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    // TextureView respects Compose's clip and
                                    // prevents camera surface from covering the
                                    // bottom half of the top toolbar.
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    previewViewRef = this
                                }
                            }
                        )
                        BlueEdgeOverlay(
                            corners = detectedCorners,
                            state = detectedState,
                            scanMode = scanMode,
                            idCardStep = idCardStep,
                            idCardType = selectedIdCardType,
                            showGrid = showGridGuidelines,
                            frameAspectRatio = detectedFrameAspect,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (hasCameraPermission) {
                        if (isNidCaptureMode && !showIdCardIntro) {
                            NidReferenceViewfinder(
                                state = detectedState,
                                idCardStep = idCardStep,
                                idCardType = selectedIdCardType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (isNidCaptureMode && isProcessing) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 28.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xE6121519),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3342C6FF))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = Color(0xFF42C6FF),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = nidProcessingStage ?: "Processing NID…",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Auto crop • Perspective correction • ML Kit verification",
                                    color = Color(0xFFB8C2CC),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (!isNidCaptureMode) {
                        // Real-time Document Alignment Guidance & Status Indicator
                        val guideInfo = when {
                            detectedState == DetectionState.DOCUMENT_STABLE -> {
                                AlignmentGuideInfo(
                                    "Document Ready • Tap to Scan",
                                    Icons.Default.Check,
                                    Color(0xFF28D8A1).copy(alpha = 0.95f),
                                    Color.White,
                                    true
                                )
                            }
                            detectedState == DetectionState.DOCUMENT_DETECTED -> {
                                AlignmentGuideInfo(
                                    "Document Found • Adjusting Edges",
                                    Icons.Default.AutoFixHigh,
                                    Color(0xDD0F172A),
                                    Color(0xFF28D8A1),
                                    false
                                )
                            }
                            else -> {
                                AlignmentGuideInfo(
                                    "Point camera at document",
                                    Icons.Default.DocumentScanner,
                                    Color(0xAA111827),
                                    Color(0xFFD1D5DB),
                                    false
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = guideInfo.bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (guideInfo.isHighlighted) Color(0xFF28D8A1) else if (detectedState == DetectionState.DOCUMENT_DETECTED) Color(0xFF28D8A1).copy(alpha = 0.7f) else Color(0x33FFFFFF)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        guideInfo.icon,
                                        contentDescription = null,
                                        tint = guideInfo.fg,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = guideInfo.text,
                                        color = guideInfo.fg,
                                        fontSize = 12.sp,
                                        fontWeight = if (guideInfo.isHighlighted) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    }

                // 3. BOTTOM CONTROLS DECK
                if (isNidCaptureMode) {
                    NidReferenceBottomControls(
                        isShutterBusy = isShutterBusy || isProcessing,
                        idCardStep = idCardStep,
                        selectedType = selectedIdCardType,
                        pageCount = capturedPages.size,
                        onUndoClick = { viewModel.undoLastIdCardCapture() },
                        onDoneClick = {
                            if (capturedPages.isNotEmpty()) onNavigateToDocumentPreview()
                        },
                        onGalleryClick = { galleryLauncher.launch("image/*") },
                        onShutterClick = { capturePhoto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                } else Surface(
                    color = Color(0xF0101316),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        // Segmented Mode Selector: [ Single | Batch ]
                        SegmentedScanModeSelector(
                            selectedMode = scanMode,
                            onModeSelected = { newMode ->
                                if (newMode == ScanMode.ID_CARD) {
                                    viewModel.enterNidCardMode()
                                } else {
                                    viewModel.scanMode.value = newMode
                                    if (viewModel.activeFeatureMode.value == ScannerFeatureMode.ID_CARDS) {
                                        viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                        )

                        // HORIZONTAL FEATURE NAVIGATION BAR
                        HorizontalFeatureBar(
                            selectedFeature = activeFeatureMode,
                            onFeatureSelected = { feature ->
                                viewModel.activeFeatureMode.value = feature
                                when (feature) {
                                    ScannerFeatureMode.ID_CARDS -> {
                                        viewModel.enterNidCardMode()
                                        viewModel.showIdCardIntro.value = true
                                    }
                                    ScannerFeatureMode.SCAN -> {
                                        viewModel.showIdCardIntro.value = false
                                        if (scanMode == ScanMode.ID_CARD) viewModel.scanMode.value = ScanMode.SINGLE
                                    }
                                    else -> {
                                        viewModel.showIdCardIntro.value = false
                                        if (scanMode == ScanMode.ID_CARD) viewModel.scanMode.value = ScanMode.SINGLE
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        )

                        // BOTTOM CAMERA CONTROLS
                        BottomScannerControls(
                            isShutterBusy = isShutterBusy || isProcessing,
                            batchPageCount = capturedPages.size,
                            isBatchMode = (scanMode == ScanMode.BATCH),
                            lastCapturedThumbnail = capturedPages.lastOrNull()?.originalPath,
                            onAllFeaturesClick = { showAllFeaturesSheet = true },
                            onShutterClick = { capturePhoto() },
                            onImportImagesClick = { galleryLauncher.launch("image/*") },
                            onImportFilesClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                            onFinishBatchClick = {
                                viewModel.onBatchCaptureFinished {
                                    onNavigateToCrop()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                // QR & BARCODE SCANNER OVERLAY & INTERACTION DECK
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    QrScannerOverlay(
                        isWideBarcodeMode = isWideBarcodeMode,
                        onToggleBarcodeFormat = {
                            viewModel.isWideBarcodeMode.value = !isWideBarcodeMode
                        },
                        subMode = qrScanSubMode,
                        onToggleSubMode = {
                            viewModel.qrScanSubMode.value = if (qrScanSubMode == QrScanSubMode.SINGLE) {
                                QrScanSubMode.CONTINUOUS
                            } else {
                                QrScanSubMode.SINGLE
                            }
                        },
                        isTorchOn = isTorchOn,
                        onToggleTorch = {
                            val nextTorch = !isTorchOn
                            isTorchOn = nextTorch
                            camera?.cameraControl?.enableTorch(nextTorch)
                        },
                        onOpenGallery = {
                            qrGalleryLauncher.launch("image/*")
                        },
                        onOpenHistory = {
                            viewModel.showQrHistorySheet.value = true
                        },
                        onCreateQr = {
                            viewModel.showCreateQrSheet.value = true
                        },
                        onSwitchCamera = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        zoomRatio = zoomRatio,
                        onZoomChange = { newRatio ->
                            zoomRatio = newRatio
                            camera?.cameraControl?.setLinearZoom((newRatio - 1f) / 7f)
                        },
                        focusTapOffset = focusTapOffset,
                        showLowLightHint = showLowLightHint,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Bottom Controls for Continuous / Batch Scan
                    if (qrScanSubMode == QrScanSubMode.CONTINUOUS) {
                        QrContinuousScanBar(
                            scannedCount = continuousScannedList.size,
                            onDoneClick = {
                                if (continuousScannedList.isNotEmpty()) {
                                    viewModel.showQrHistorySheet.value = true
                                } else {
                                    Toast.makeText(context, "No codes scanned yet", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClearClick = {
                                viewModel.clearContinuousScannedList()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // The ID Cards landing screen floats above the live preview, as in the supplied
        // reference.  Selection is kept in the ViewModel and used by the actual scanner.
        if (isNidCaptureMode && showIdCardIntro && !isNidSetupCapture) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Spacer(Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.Black)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        viewModel.showIdCardIntro.value = false
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close ID Cards", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val enabled = !isTorchOn
                        isTorchOn = enabled
                        camera?.cameraControl?.enableTorch(enabled)
                    }) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle flashlight",
                            tint = if (isTorchOn) Color.Yellow else Color.White
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isHdModeEnabled) ScannerTeal else Color(0xFF32353B),
                        modifier = Modifier.clickable {
                            viewModel.isHdModeEnabled.value = !isHdModeEnabled
                        }
                    ) {
                        Text("HD", color = if (isHdModeEnabled) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More camera options", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isHdModeEnabled) "Turn HD off" else "Turn HD on") },
                                onClick = {
                                    viewModel.isHdModeEnabled.value = !isHdModeEnabled
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    IdCardIntroOverlay(
                        selectedType = selectedIdCardType,
                        onTypeSelected = { viewModel.selectedIdCardType.value = it },
                        onMakeItNow = {
                            viewModel.beginSelectedIdCardCapture()
                        },
                        modifier = Modifier.offset(y = (-14).dp)
                    )
                }
                // Match the camera's horizontal feature tabs. "All Features"
                // belongs exclusively to the icon in the bottom deck below.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Scan", "Smart Erase", "ID Cards", "Question Set", "Translate").forEach { tab ->
                        val selected = tab == "ID Cards"
                        Column(
                            modifier = Modifier.clickable {
                                when (tab) {
                                    "Scan" -> {
                                        viewModel.showIdCardIntro.value = false
                                        viewModel.scanMode.value = ScanMode.SINGLE
                                        viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                                    }
                                    "Translate" -> {
                                        viewModel.showIdCardIntro.value = false
                                        viewModel.scanMode.value = ScanMode.SINGLE
                                        viewModel.activeFeatureMode.value = ScannerFeatureMode.TRANSLATE
                                    }
                                    "Smart Erase", "Question Set" -> showAllFeaturesSheet = true
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(3.dp)
                                    .background(if (selected) ScannerTeal else Color.Transparent, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = tab,
                                color = if (selected) ScannerTeal else Color(0xFFBEBEC1),
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(116.dp).background(Color.Black),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clickable { showAllFeaturesSheet = true }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = "All Features", tint = Color.White, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("All Features", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // QR / BARCODE MODAL BOTTOM SHEETS & DIALOGS
        activeScannedBarcode?.let { result ->
            QrResultBottomSheet(
                result = result,
                onDismiss = { viewModel.onScanAgain() },
                onScanAgain = { viewModel.onScanAgain() },
                onOpenHistory = {
                    viewModel.onScanAgain()
                    viewModel.showQrHistorySheet.value = true
                }
            )
        }

        if (showQrHistorySheet) {
            QrHistoryBottomSheet(
                historyRepository = viewModel.qrHistoryRepository,
                historyList = qrHistoryList,
                onDismiss = { viewModel.showQrHistorySheet.value = false },
                onItemClick = { item ->
                    viewModel.activeScannedBarcode.value = ParsedBarcode(
                        rawValue = item.rawValue,
                        displayValue = item.displayValue,
                        format = item.format,
                        formatName = item.formatName,
                        valueType = try {
                            BarcodeValueType.valueOf(item.valueType)
                        } catch (e: Exception) {
                            BarcodeValueType.TEXT
                        },
                        title = item.title,
                        subtitle = item.subtitle,
                        timestamp = item.timestamp
                    )
                    viewModel.showQrHistorySheet.value = false
                }
            )
        }

        if (showCreateQrSheet) {
            CreateQrBottomSheet(
                onDismiss = { viewModel.showCreateQrSheet.value = false }
            )
        }

        if (showGalleryMultipleCodesDialog) {
            GalleryCodeSelectionDialog(
                codes = galleryParsedCodes,
                onSelectCode = { code ->
                    viewModel.activeScannedBarcode.value = code
                    viewModel.showGalleryMultipleCodesDialog.value = false
                },
                onDismiss = {
                    viewModel.showGalleryMultipleCodesDialog.value = false
                }
            )
        }

        // ALL FEATURES MODAL BOTTOM SHEET
        if (showAllFeaturesSheet) {
            AllFeaturesBottomSheet(
                onDismiss = { showAllFeaturesSheet = false },
                onFeatureSelected = { feature ->
                    showAllFeaturesSheet = false
                    when (feature) {
                        "QR & Barcode" -> {
                            viewModel.mainScanMode.value = MainScanMode.QR_BARCODE
                        }
                        "ID Card" -> {
                            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
                            viewModel.enterNidCardMode()
                            viewModel.showIdCardIntro.value = true
                        }
                        "Translate" -> {
                            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
                            viewModel.activeFeatureMode.value = ScannerFeatureMode.TRANSLATE
                            if (viewModel.scanMode.value == ScanMode.ID_CARD) viewModel.scanMode.value = ScanMode.SINGLE
                        }
                        "Scan to PDF" -> {
                            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
                            viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                            if (viewModel.scanMode.value == ScanMode.ID_CARD) viewModel.scanMode.value = ScanMode.SINGLE
                        }
                        "Batch Scan" -> {
                            viewModel.mainScanMode.value = MainScanMode.DOCUMENT
                            viewModel.scanMode.value = ScanMode.BATCH
                            viewModel.activeFeatureMode.value = ScannerFeatureMode.SCAN
                        }
                        "Import Gallery" -> {
                            galleryLauncher.launch("image/*")
                        }
                        "Import Files" -> {
                            filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                        }
                    }
                }
            )
        }
    }
}

/**
 * Dedicated NID CameraX top bar inspired by the reference scanner UI.
 * This is fully app-owned, unlike the Google ML Kit document-scanner camera UI.
 */
@Composable
fun NidReferenceTopBar(
    isTorchOn: Boolean,
    isHdEnabled: Boolean,
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
    onHdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCameraMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(top = 42.dp, start = 14.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.size(46.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close card scanner",
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onFlashClick,
            modifier = Modifier.size(46.dp)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isTorchOn) "Turn flash off" else "Turn flash on",
                tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White,
                modifier = Modifier.size(27.dp)
            )
        }
        Surface(
            color = if (isHdEnabled) Color.White else Color(0xFF36383D),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.clickable(onClick = onHdClick)
        ) {
            Text(
                "HD",
                color = if (isHdEnabled) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
            )
        }
        Box {
            IconButton(onClick = { showCameraMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Camera options", tint = Color.White)
            }
            DropdownMenu(
                expanded = showCameraMenu,
                onDismissRequest = { showCameraMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (isHdEnabled) "Disable HD" else "Enable HD") },
                    onClick = { onHdClick(); showCameraMenu = false }
                )
            }
        }
    }
}

/**
 * Fixed visual NID guide matching the reference: dark camera, thin frame,
 * cyan L-corners and a blue "Document detected" status pill.
 * Real detected corners are intentionally not drawn here; they are used by
 * the capture/perspective pipeline while the UI stays stable and predictable.
 */
@Composable
fun NidReferenceViewfinder(
    state: DetectionState,
    idCardStep: Int,
    idCardType: IdCardType = IdCardType.ID_CARD,
    modifier: Modifier = Modifier
) {
    val accent = Color.White

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // The reference frame is intentionally a little wider than a physical ID-1 card.
            // Perspective correction later normalizes the actual card to its correct ratio.
            val isPortraitDocument = idCardType == IdCardType.CERTIFICATE || idCardType == IdCardType.PASSPORT
            val frameW = if (isPortraitDocument) w * 0.92f else w * 0.90f
            val frameH = if (isPortraitDocument) {
                (frameW * 1.414f).coerceAtMost(h * 0.86f)
            } else {
                (frameW / 1.62f).coerceAtMost(h * 0.48f)
            }
            val left = (w - frameW) / 2f
            val top = (h - frameH) / 2f
            val rect = Rect(left, top, left + frameW, top + frameH)

            // Gentle dim outside the guide to create the reference scanner look.
            val outer = Path().apply { addRect(Rect(0f, 0f, w, h)) }
            val inner = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(10.dp.toPx()))) }
            drawPath(
                Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outer, inner),
                Color(0x68000000)
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.11f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(10.dp.toPx())
            )

            // Very thin neutral frame line.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            val len = 42.dp.toPx()
            val sw = 5.dp.toPx()
            val r = 10.dp.toPx()

            // TL
            drawLine(accent, Offset(rect.left, rect.top + len), Offset(rect.left, rect.top + r), sw)
            drawLine(accent, Offset(rect.left + r, rect.top), Offset(rect.left + len, rect.top), sw)
            // TR
            drawLine(accent, Offset(rect.right - len, rect.top), Offset(rect.right - r, rect.top), sw)
            drawLine(accent, Offset(rect.right, rect.top + r), Offset(rect.right, rect.top + len), sw)
            // BL
            drawLine(accent, Offset(rect.left, rect.bottom - len), Offset(rect.left, rect.bottom - r), sw)
            drawLine(accent, Offset(rect.left + r, rect.bottom), Offset(rect.left + len, rect.bottom), sw)
            // BR
            drawLine(accent, Offset(rect.right - len, rect.bottom), Offset(rect.right - r, rect.bottom), sw)
            drawLine(accent, Offset(rect.right, rect.bottom - len), Offset(rect.right, rect.bottom - r), sw)
        }

        val guideLabel = when {
            idCardType.isTwoSided -> if (idCardStep == 1) "Front" else "Back"
            idCardType == IdCardType.CERTIFICATE -> "Rotate your device and fit the document into the borders"
            else -> "Fit it into the borders"
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0x880D0F15),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp, start = 12.dp, end = 12.dp)
        ) {
            Text(
                text = guideLabel,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Text(
            text = idCardType.title,
            color = ScannerTeal,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}

/**
 * Dedicated NID bottom deck: gallery and manual shutter.
 */
@Composable
fun NidReferenceBottomControls(
    isShutterBusy: Boolean,
    idCardStep: Int,
    selectedType: IdCardType,
    pageCount: Int,
    onUndoClick: () -> Unit,
    onDoneClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onUndoClick,
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo last card image or return to card types",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isShutterBusy) { onShutterClick() }
                        .border(4.dp, ScannerTeal, CircleShape)
                        .padding(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .background(if (isShutterBusy) Color(0xFFB8B8B8) else Color.White, CircleShape)
                    )
                }

                Column(
                    modifier = Modifier.width(82.dp).clickable(onClick = onGalleryClick),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Import Images",
                        tint = Color.White,
                        modifier = Modifier.size(29.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Import Images",
                        color = Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (selectedType == IdCardType.CERTIFICATE && pageCount > 0) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = ScannerTeal,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.clickable(enabled = !isShutterBusy, onClick = onDoneClick)
                ) {
                    Text(
                        text = "Done • $pageCount page${if (pageCount == 1) "" else "s"}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 34.dp, vertical = 10.dp)
                    )
                }
            } else if (selectedType.isTwoSided && pageCount == 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Front ready • Scan or import Back",
                    color = Color(0xFFB7C2C9),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Top bar with transparent background and sleek rounded control buttons
 */
@Composable
fun ScannerTopBar(
    flashMode: Int,
    isTorchOn: Boolean,
    isMagicEnhanceOn: Boolean,
    isHdOn: Boolean,
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
    onMagicEnhanceClick: () -> Unit,
    onHdClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Close Button (X)
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .size(40.dp)
                .background(Color(0x55000000), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        // Right action items
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Toggle
            IconButton(
                onClick = onFlashClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x55000000), CircleShape)
            ) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    else -> Icons.Default.FlashOff
                }
                val tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) ScannerTeal else Color.White
                Icon(icon, contentDescription = "Flash", tint = tint, modifier = Modifier.size(18.dp))
            }

            // Magic Auto-Enhance Sparkle
            IconButton(
                onClick = onMagicEnhanceClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x55000000), CircleShape)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Magic Enhancement",
                    tint = if (isMagicEnhanceOn) ScannerTeal else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // HD Resolution Badge
            Surface(
                shape = CircleShape,
                color = if (isHdOn) ScannerTeal else Color(0x55000000),
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onHdClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "HD",
                        color = if (isHdOn) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            // More Options (...)
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x55000000), CircleShape)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Real-time Document Detection Overlay Canvas
 * Renders the detected 4-point polygon with smooth cyan border and corner anchor points
 */
@Composable
fun DocumentDetectionOverlay(
    corners: List<Offset>,
    state: DetectionState,
    scanMode: ScanMode,
    idCardStep: Int,
    idCardType: IdCardType = IdCardType.BANK_CARD,
    showGrid: Boolean,
    frameAspectRatio: Float = 3f / 4f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_fx")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laser"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 3x3 Grid Guidelines
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.20f)
            drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.dp.toPx())
        }

        if (scanMode == ScanMode.ID_CARD) {
            // ID Card Frame Box (Standard CR-80 1.58 ratio, or Passport 1.42 ratio)
            val cardRatio = if (idCardType == IdCardType.PASSPORT) 1.42f else 1.586f
            val cardW = if (idCardType == IdCardType.PASSPORT) w * 0.88f else w * 0.86f
            val cardH = cardW / cardRatio
            val cardL = (w - cardW) / 2f
            val cardT = (h - cardH) / 2.3f

            val cardRect = Rect(cardL, cardT, cardL + cardW, cardT + cardH)

            // Dim background outside card
            val outerPath = Path().apply {
                addRect(Rect(0f, 0f, w, h))
            }
            val holePath = Path().apply {
                addRoundRect(RoundRect(cardRect, CornerRadius(16.dp.toPx(), 16.dp.toPx())))
            }
            val maskPath = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outerPath, holePath)
            drawPath(maskPath, Color(0x66000000))

            // Card cyan boundary frame
            drawRoundRect(
                color = ScannerTeal,
                topLeft = Offset(cardL, cardT),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Card corner target brackets
            val cornerLen = 28.dp.toPx()
            val bracketColor = ScannerTealBright
            val bracketStroke = 4.dp.toPx()

            // Top-Left
            drawLine(bracketColor, Offset(cardL - 2, cardT + cornerLen), Offset(cardL - 2, cardT), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(cardL - 2, cardT), Offset(cardL + cornerLen, cardT), strokeWidth = bracketStroke)

            // Top-Right
            drawLine(bracketColor, Offset(cardL + cardW + 2 - cornerLen, cardT), Offset(cardL + cardW + 2, cardT), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(cardL + cardW + 2, cardT), Offset(cardL + cardW + 2, cardT + cornerLen), strokeWidth = bracketStroke)

            // Bottom-Left
            drawLine(bracketColor, Offset(cardL - 2, cardT + cardH - cornerLen), Offset(cardL - 2, cardT + cardH), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(cardL - 2, cardT + cardH), Offset(cardL + cornerLen, cardT + cardH), strokeWidth = bracketStroke)

            // Bottom-Right
            drawLine(bracketColor, Offset(cardL + cardW + 2 - cornerLen, cardT + cardH), Offset(cardL + cardW + 2, cardT + cardH), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(cardL + cardW + 2, cardT + cardH), Offset(cardL + cardW + 2, cardT + cardH - cornerLen), strokeWidth = bracketStroke)

        } else if (corners.size == 4 && state != DetectionState.SEARCHING_DOCUMENT) {
            // No corners are drawn while still searching (camera settling or no
            // document found yet) — only once a document is actually detected
            // does the quad + corner overlay appear. See CameraMotionDetector.
            // The PreviewView renders the camera feed with FILL_CENTER (center-crop)
            // scaling. That means the visible on-screen frame is a cropped subset of
            // the full analyzed frame whenever the screen's aspect ratio doesn't
            // exactly match the camera's — true on almost every device. Mapping
            // normalized corners straight onto (w, h) without correcting for that
            // crop is what used to make the detected box drift away from the real
            // document edges. This reproduces the same center-crop so the overlay
            // lines up with what's actually visible in the preview.
            val destAspect = w / h
            var cropXFrac = 0f
            var cropYFrac = 0f
            if (frameAspectRatio > destAspect) {
                // Source is proportionally wider than the screen -> horizontal crop
                cropXFrac = 0.5f * (1f - destAspect / frameAspectRatio)
            } else if (frameAspectRatio < destAspect) {
                // Source is proportionally taller than the screen -> vertical crop
                cropYFrac = 0.5f * (1f - frameAspectRatio / destAspect)
            }
            val xSpan = (1f - 2f * cropXFrac).coerceAtLeast(0.0001f)
            val ySpan = (1f - 2f * cropYFrac).coerceAtLeast(0.0001f)

            fun mapX(nx: Float) = ((nx - cropXFrac) / xSpan) * w
            fun mapY(ny: Float) = ((ny - cropYFrac) / ySpan) * h

            val p0 = Offset(mapX(corners[0].x), mapY(corners[0].y))
            val p1 = Offset(mapX(corners[1].x), mapY(corners[1].y))
            val p2 = Offset(mapX(corners[2].x), mapY(corners[2].y))
            val p3 = Offset(mapX(corners[3].x), mapY(corners[3].y))

            val quadPath = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }

            val strokeColor = when (state) {
                DetectionState.DOCUMENT_STABLE -> Color(0xFF007AFF)
                DetectionState.DOCUMENT_DETECTED -> Color(0xFF38BDF8)
                else -> Color.White.copy(alpha = 0.35f)
            }

            val strokeWidth = if (state == DetectionState.DOCUMENT_STABLE) 2.5.dp.toPx() else 2.0.dp.toPx()

            // Draw Document Outline Polygon
            drawPath(
                path = quadPath,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = if (state == DetectionState.SEARCHING_DOCUMENT) PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) else null
                )
            )

            // Fill with subtle translucent blue wash if detected
            if (state == DetectionState.DOCUMENT_DETECTED || state == DetectionState.DOCUMENT_STABLE) {
                drawPath(
                    path = quadPath,
                    color = Color(0xFF007AFF).copy(alpha = if (state == DetectionState.DOCUMENT_STABLE) 0.10f else 0.05f)
                )
            }

            // Draw Edge Midpoint Handles (Blue & White style)
            if (state == DetectionState.DOCUMENT_DETECTED || state == DetectionState.DOCUMENT_STABLE) {
                val midpoints = listOf(
                    Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f),
                    Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f),
                    Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f),
                    Offset((p3.x + p0.x) / 2f, (p3.y + p0.y) / 2f)
                )
                midpoints.forEach { mid ->
                    drawCircle(
                        color = Color(0xFF007AFF).copy(alpha = 0.40f),
                        radius = 4.5.dp.toPx(),
                        center = mid
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = mid
                    )
                    drawCircle(
                        color = Color(0xFF1E2022),
                        radius = 1.2.dp.toPx(),
                        center = mid
                    )
                }
            }

            // Draw Corner Anchors (4 vertices - Blue Scanner Look)
            val pointList = listOf(p0, p1, p2, p3)
            val anchorRadius = if (state == DetectionState.DOCUMENT_STABLE) 7.5.dp.toPx() else 6.5.dp.toPx()

            pointList.forEach { pt ->
                // Outer glow / accent ring
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.40f),
                    radius = anchorRadius + 3.5.dp.toPx(),
                    center = pt
                )
                // Dark outer border ring
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = anchorRadius + 1.2.dp.toPx(),
                    center = pt
                )
                // Solid center anchor (White circle)
                drawCircle(
                    color = Color.White,
                    radius = anchorRadius,
                    center = pt
                )
                // Distinct inner dot
                drawCircle(
                    color = Color(0xFF007AFF),
                    radius = 2.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * Segmented Pill: [ Single | Batch ]
 */
@Composable
fun SegmentedScanModeSelector(
    selectedMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(0x991E2022),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isSingle = (selectedMode == ScanMode.SINGLE)
            val isBatch = (selectedMode == ScanMode.BATCH)

            // Single Tab
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isSingle) Color(0xFF4B5563) else Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { onModeSelected(ScanMode.SINGLE) }
            ) {
                Text(
                    text = "Single",
                    color = if (isSingle) Color.White else Color(0xFF9CA3AF),
                    fontWeight = if (isSingle) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

            // Batch Tab
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isBatch) Color(0xFF4B5563) else Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { onModeSelected(ScanMode.BATCH) }
            ) {
                Text(
                    text = "Batch",
                    color = if (isBatch) Color.White else Color(0xFF9CA3AF),
                    fontWeight = if (isBatch) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Horizontal Mode Bar: Scan | ID Cards | Translate
 */
@Composable
fun HorizontalFeatureBar(
    selectedFeature: ScannerFeatureMode,
    onFeatureSelected: (ScannerFeatureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        ScannerFeatureMode.SCAN,
        ScannerFeatureMode.ID_CARDS
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { feature ->
            val isSelected = (feature == selectedFeature)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onFeatureSelected(feature) }
            ) {
                Text(
                    text = if (feature == ScannerFeatureMode.ID_CARDS) "ID Card" else feature.label,
                    color = if (isSelected) ScannerTeal else Color(0xFFB0B0B0),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Cyan Indicator bar under active tab
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .background(ScannerTeal, RoundedCornerShape(2.dp))
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

/**
 * Bottom Controls Area:
 * [All Features (grid icon + label)]  (⚪ Shutter Button)  [Import Images | Files]
 */
@Composable
fun BottomScannerControls(
    isShutterBusy: Boolean,
    batchPageCount: Int,
    isBatchMode: Boolean,
    lastCapturedThumbnail: String?,
    onAllFeaturesClick: () -> Unit,
    onShutterClick: () -> Unit,
    onImportImagesClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onFinishBatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shutterScale = if (isPressed) 0.92f else 1.0f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: "All Features" with Grid Icon & Clean Label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onAllFeaturesClick() }
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "All Features",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All Features",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Center: Circular Shutter Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .scale(shutterScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onShutterClick() }
        ) {
            // Outer Teal Ring (78dp)
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(3.5.dp, ScannerTeal),
                modifier = Modifier.size(78.dp)
            ) {}

            // Inner Crisp White Circle (62dp)
            Surface(
                shape = CircleShape,
                color = if (isShutterBusy) Color.LightGray else Color.White,
                modifier = Modifier.size(62.dp)
            ) {
                if (isShutterBusy) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = ScannerTeal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Right side: Either Batch Finish Stack OR Import Icons
        if (isBatchMode && batchPageCount > 0) {
            // Batch Preview & Finish Button
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier
                    .clickable { onFinishBatchClick() }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E2022),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ScannerTeal),
                    modifier = Modifier.size(52.dp)
                ) {
                    if (lastCapturedThumbnail != null) {
                        AsyncImage(
                            model = File(lastCapturedThumbnail),
                            contentDescription = "Last Scanned Page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ScannerTeal)
                        }
                    }
                }

                // Badge with count
                Surface(
                    shape = CircleShape,
                    color = ScannerTeal,
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = 4.dp, y = (-4).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$batchPageCount",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Right: Import Images & Import Files
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import Images (Gallery)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onImportImagesClick() }
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Import Images",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Images",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Import Files (PDF / Docs)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onImportFilesClick() }
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Import Files",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Files",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * All Features Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeaturesBottomSheet(
    onDismiss: () -> Unit,
    onFeatureSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF181A1C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "All Scanning Features",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val featureList = listOf(
                FeatureItem("Scan to PDF", Icons.Default.PictureAsPdf, "Multi-page searchable PDF"),
                FeatureItem("ID Card", Icons.Default.CreditCard, "2-Sided ID & Passport scan"),
                FeatureItem("Translate", Icons.Default.Translate, "Instant document translation"),
                FeatureItem("Batch Scan", Icons.Default.DocumentScanner, "Multi-page rapid scanning"),
                FeatureItem("Import Gallery", Icons.Default.Image, "Process existing photos"),
                FeatureItem("Import Files", Icons.Default.FolderOpen, "Import PDF / Docs")
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(featureList) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF24272B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFeatureSelected(item.title) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ScannerTeal.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(item.icon, contentDescription = item.title, tint = ScannerTeal, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FeatureItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val subtitle: String
)
