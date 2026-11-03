package com.docscan.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.ui.remover.engine.BackgroundRemovalEngine
import com.docscan.ui.remover.model.BgRemovalProvider
import com.docscan.util.FileUtils
import com.docscan.util.PassportFaceAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ==================== DATA STRUCTURES ====================

data class PassportCountryPreset(
    val id: String,
    val countryName: String,
    val flagEmoji: String,
    val docType: String,
    val widthMm: Float,
    val heightMm: Float,
    val description: String,
    val defaultBgColor: Color = Color.White
) {
    val aspectRatio: Float get() = widthMm / heightMm
    val widthPx300Dpi: Int get() = (widthMm * 300f / 25.4f).roundToInt()
    val heightPx300Dpi: Int get() = (heightMm * 300f / 25.4f).roundToInt()
}

enum class PassportFilterType(val displayName: String, val desc: String) {
    ORIGINAL("Original", "Natural camera colors"),
    STUDIO_BRIGHT("Studio Light", "Brightens face & soft shadows"),
    CRYSTAL_CLEAR("Crystal Clear", "Crisp contrast for printing"),
    WARM_NATURAL("Warm Tone", "Soft natural skin glow"),
    BW_OFFICIAL("Black & White", "Standard B&W for exams/documents")
}

data class PassportBgOption(
    val id: String,
    val label: String,
    val color: Color,
    val desc: String
)

enum class SheetPaperSize(val displayName: String, val widthMm: Float, val heightMm: Float, val isSingle: Boolean = false) {
    SINGLE("Single Photo", 0f, 0f, isSingle = true),
    A4("A4 Sheet", 210f, 297f),
    PHOTO_4X6("4x6\" Photo Paper (4R)", 101.6f, 152.4f),
    PHOTO_5X7("5x7\" Photo Paper (5R)", 127f, 177.8f),
    US_LETTER("US Letter", 215.9f, 279.4f)
}

enum class CutGuideStyle(val displayName: String) {
    DOTTED_SCISSORS("Dotted + Scissors ✂️"),
    FINE_BORDER("Thin Border"),
    NO_BORDER("No Lines")
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportPhotoMakerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePalette = rememberAppThemePalette()
    val passportBackgroundEngine = remember { BackgroundRemovalEngine(context.applicationContext) }

    // ---------------- State Holders ----------------
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedFace by remember { mutableStateOf<PassportFaceAnalyzer.Result?>(null) }
    var isAnalyzingFace by remember { mutableStateOf(false) }
    var transparentPortraitBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRemovingPortraitBackground by remember { mutableStateOf(false) }
    var isLoadingImage by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportSuccessDialogInfo by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // filePath to isPdf

    // Studio Editing Tab
    var currentTab by remember { mutableIntStateOf(0) } // 0: Size, 1: Crop & Align, 2: Background & Filter, 3: Print Sheet
    val tabTitles = listOf("1. Size Preset", "2. Biometric Crop", "3. Color & BG", "4. Print Sheet")

    // Presets List
    val presets = remember {
        listOf(
            PassportCountryPreset("bd_pass", "Bangladesh", "🇧🇩", "Passport / NID / Visa", 35f, 45f, "Standard 35x45 mm (7:9 ratio)", Color.White),
            PassportCountryPreset("us_visa", "USA (United States)", "🇺🇸", "Visa / Passport / DV", 51f, 51f, "2x2 inch (51x51 mm) Square", Color.White),
            PassportCountryPreset("in_pass", "India", "🇮🇳", "Passport / Visa / PAN", 35f, 45f, "35x45 mm (White/Light Background)", Color.White),
            PassportCountryPreset("in_oci", "India OCI / Visa", "🇮🇳", "OCI / Visa Square", 51f, 51f, "2x2 inch (51x51 mm)", Color.White),
            PassportCountryPreset("uk_schengen", "UK & Schengen / EU", "🇬🇧", "Passport / Schengen Visa", 35f, 45f, "35x45 mm ICAO Standard", Color(0xFFF3F4F6)),
            PassportCountryPreset("ca_visa", "Canada", "🇨🇦", "Passport / Visa / PR", 50f, 70f, "50x70 mm (Large Visa Size)", Color.White),
            PassportCountryPreset("sa_gulf", "Saudi Arabia / Gulf", "🇸🇦", "Umrah / Hajj / Iqama", 40f, 60f, "40x60 mm (White Background)", Color.White),
            PassportCountryPreset("ae_dubai", "UAE / Dubai", "🇦🇪", "Tourist & Residence Visa", 43f, 55f, "43x55 mm Standard", Color.White),
            PassportCountryPreset("my_pass", "Malaysia", "🇲🇾", "Passport & Entry Visa", 35f, 50f, "35x50 mm (Blue/White)", Color(0xFFD6E4FF)),
            PassportCountryPreset("sg_pass", "Singapore", "🇸🇬", "Passport / NRIC / IC", 35f, 45f, "35x45 mm White BG", Color.White),
            PassportCountryPreset("au_pass", "Australia", "🇦🇺", "Passport / Visa", 35f, 45f, "35x45 mm Light Plain BG", Color(0xFFF9FAFB)),
            PassportCountryPreset("stamp_std", "Stamp Size", "🪪", "Standard Stamp Size", 25f, 30f, "25x30 mm (School / Job Form)", Color.White),
            PassportCountryPreset("stamp_mini", "Stamp Size (Mini)", "🪪", "Small Attestation Stamp", 20f, 25f, "20x25 mm", Color.White),
            PassportCountryPreset("custom", "Custom Size", "⚙️", "Custom Dimension (mm)", 35f, 45f, "Enter custom Width & Height", Color.White)
        )
    }

    var selectedPreset by remember { mutableStateOf(presets[0]) }
    var customWidthMm by remember { mutableFloatStateOf(35f) }
    var customHeightMm by remember { mutableFloatStateOf(45f) }

    // Transform State (Crop, Pan, Zoom, Straighten)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var straightenAngle by remember { mutableFloatStateOf(0f) } // -20° to +20°
    var rotationSteps by remember { mutableIntStateOf(0) } // 0, 90, 180, 270
    var isFlippedHorizontally by remember { mutableStateOf(false) }
    var showBiometricGuide by remember { mutableStateOf(true) }

    // Color & Background Settings
    val bgOptions = remember {
        listOf(
            PassportBgOption("white", "Pure White", Color.White, "Official Standard"),
            PassportBgOption("soft_blue", "Studio Soft Blue", Color(0xFFD6E4FF), "Asian Visas & Malaysia"),
            PassportBgOption("cyan_blue", "Light Sky Blue", Color(0xFF93C5FD), "Standard Blue"),
            PassportBgOption("royal_blue", "Royal Blue", Color(0xFF2563EB), "Official Blue"),
            PassportBgOption("off_white", "Light Gray", Color(0xFFF1F5F9), "European / UK"),
            PassportBgOption("red", "Crimson Red", Color(0xFFDC2626), "Indonesia / China Visa"),
            PassportBgOption("original", "Original", Color.Transparent, "Keep natural photo background")
        )
    }
    var selectedBgOption by remember { mutableStateOf(bgOptions[0]) }
    var selectedFilter by remember { mutableStateOf(PassportFilterType.STUDIO_BRIGHT) }
    var brightnessAdj by remember { mutableFloatStateOf(0.05f) } // -0.5 to +0.5
    var contrastAdj by remember { mutableFloatStateOf(1.1f) } // 0.5 to 2.0
    var saturationAdj by remember { mutableFloatStateOf(1.05f) } // 0.0 to 2.0

    // Sheet Print Settings
    var selectedPaperSize by remember { mutableStateOf(SheetPaperSize.A4) }
    var photoCountOption by remember { mutableIntStateOf(8) } // 1, 2, 4, 6, 8, 12, 16, 24, 32
    var cutGuideStyle by remember { mutableStateOf(CutGuideStyle.DOTTED_SCISSORS) }
    var includeDateStamp by remember { mutableStateOf(false) }
    var applicantNameText by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }

    // Previews & Processed Bitmap
    var finalSinglePassportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var printableSheetPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Activity Launchers for Gallery & Camera
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoadingImage = true
            scope.launch(Dispatchers.IO) {
                val loaded = FileUtils.loadBitmapsFromUri(context, it).firstOrNull()
                withContext(Dispatchers.Main) {
                    if (loaded != null) {
                        sourceBitmap = loaded
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                        straightenAngle = 0f
                        rotationSteps = 0
                        isFlippedHorizontally = false
                    }
                    isLoadingImage = false
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            sourceBitmap = bmp
            zoomScale = 1.0f
            panOffsetX = 0f
            panOffsetY = 0f
            straightenAngle = 0f
            rotationSteps = 0
            isFlippedHorizontally = false
        }
    }

    fun applyAutoFaceAlignment(face: PassportFaceAnalyzer.Result) {
        val src = sourceBitmap ?: return
        val targetW = if (selectedPreset.id == "custom") {
            (customWidthMm * 300f / 25.4f).roundToInt().coerceAtLeast(100)
        } else selectedPreset.widthPx300Dpi
        val targetH = if (selectedPreset.id == "custom") {
            (customHeightMm * 300f / 25.4f).roundToInt().coerceAtLeast(100)
        } else selectedPreset.heightPx300Dpi

        // Aim for a passport-style face box: roughly 52% of the photo height with
        // the eye line around 42% from the top. This still remains user-adjustable.
        val baseScale = max(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val desiredScale = (targetH * 0.52f / face.bounds.height().coerceAtLeast(1)).coerceAtLeast(baseScale * 0.80f)
        zoomScale = (desiredScale / baseScale).coerceIn(0.8f, 3.5f)

        val faceCenterX = face.bounds.exactCenterX()
        val faceCenterY = face.bounds.exactCenterY()
        val eyeCenterY = if (face.leftEye != null && face.rightEye != null) {
            (face.leftEye.y + face.rightEye.y) / 2f
        } else {
            face.bounds.top + face.bounds.height() * 0.42f
        }

        val scaledFaceCenterX = (faceCenterX - src.width / 2f) * desiredScale
        val scaledEyeY = (eyeCenterY - src.height / 2f) * desiredScale
        val desiredX = targetW / 2f
        val desiredEyeY = targetH * 0.42f

        panOffsetX = ((desiredX - targetW / 2f - scaledFaceCenterX) / 2.5f)
            .coerceIn(-targetW.toFloat(), targetW.toFloat())
        panOffsetY = ((desiredEyeY - targetH / 2f - scaledEyeY) / 2.5f)
            .coerceIn(-targetH.toFloat(), targetH.toFloat())

        // Correct small camera roll automatically; large rotations remain manual.
        straightenAngle = (-face.headEulerZ).coerceIn(-12f, 12f)
        rotationSteps = 0
    }

    // On-device ML Kit face detection. It runs after a real photo is loaded and
    // automatically applies a biometric starting crop; the user can fine-tune it.
    LaunchedEffect(sourceBitmap) {
        val src = sourceBitmap ?: run {
            detectedFace = null
            transparentPortraitBitmap = null
            return@LaunchedEffect
        }

        // 1) Biometric face/eye analysis.
        isAnalyzingFace = true
        val result = withContext(Dispatchers.Default) {
            PassportFaceAnalyzer.detectPrimaryFace(src)
        }
        detectedFace = result
        isAnalyzingFace = false
        if (result != null) applyAutoFaceAlignment(result)

        // 2) Create an on-device transparent portrait so official white/blue/red
        // backgrounds actually replace the photographed background rather than
        // merely being painted behind an opaque photo.
        isRemovingPortraitBackground = true
        transparentPortraitBitmap = runCatching {
            passportBackgroundEngine.removeBackground(
                bitmap = src,
                providerChoice = BgRemovalProvider.LOCAL_PROCESSING,
                keepNaturalShadow = false,
                onStageChanged = { }
            ).transparentBitmap
        }.getOrNull()
        isRemovingPortraitBackground = false
    }

    // Re-fit the detected face when the country/photo-size preset changes.
    LaunchedEffect(selectedPreset, customWidthMm, customHeightMm) {
        detectedFace?.let { applyAutoFaceAlignment(it) }
    }

    // Auto-update rendered passport photo on parameter change
    LaunchedEffect(
        sourceBitmap,
        transparentPortraitBitmap,
        selectedPreset,
        customWidthMm,
        customHeightMm,
        zoomScale,
        panOffsetX,
        panOffsetY,
        straightenAngle,
        rotationSteps,
        isFlippedHorizontally,
        selectedBgOption,
        selectedFilter,
        brightnessAdj,
        contrastAdj,
        saturationAdj,
        includeDateStamp,
        applicantNameText
    ) {
        val original = sourceBitmap ?: return@LaunchedEffect
        val src = if (selectedBgOption.id == "original") {
            original
        } else {
            transparentPortraitBitmap ?: original
        }
        withContext(Dispatchers.Default) {
            val effW = if (selectedPreset.id == "custom") (customWidthMm * 300f / 25.4f).roundToInt() else selectedPreset.widthPx300Dpi
            val effH = if (selectedPreset.id == "custom") (customHeightMm * 300f / 25.4f).roundToInt() else selectedPreset.heightPx300Dpi
            
            val rendered = renderSinglePassportPhoto(
                src = src,
                targetW = effW,
                targetH = effH,
                zoom = zoomScale,
                panX = panOffsetX,
                panY = panOffsetY,
                angle = straightenAngle + (rotationSteps * 90f),
                flipH = isFlippedHorizontally,
                bgColor = selectedBgOption.color,
                filter = selectedFilter,
                brightness = brightnessAdj,
                contrast = contrastAdj,
                saturation = saturationAdj,
                nameStamp = if (includeDateStamp) applicantNameText.ifBlank { "Name / Date" } else null
            )
            finalSinglePassportBitmap = rendered
        }
    }

    // Auto-update printable sheet preview when sheet settings change
    LaunchedEffect(
        finalSinglePassportBitmap,
        selectedPaperSize,
        photoCountOption,
        cutGuideStyle,
        selectedPreset,
        customWidthMm,
        customHeightMm
    ) {
        val singleBmp = finalSinglePassportBitmap ?: return@LaunchedEffect
        if (selectedPaperSize.isSingle) {
            printableSheetPreviewBitmap = singleBmp
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val effW = if (selectedPreset.id == "custom") (customWidthMm * 300f / 25.4f).roundToInt() else selectedPreset.widthPx300Dpi
            val effH = if (selectedPreset.id == "custom") (customHeightMm * 300f / 25.4f).roundToInt() else selectedPreset.heightPx300Dpi

            val sheetBmp = renderPrintableSheet(
                singlePhoto = singleBmp,
                paperSize = selectedPaperSize,
                photoCount = photoCountOption,
                guideStyle = cutGuideStyle,
                photoAspect = effW.toFloat() / effH.toFloat()
            )
            printableSheetPreviewBitmap = sheetBmp
        }
    }

    // ---------------- UI Scaffold ----------------
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (themePalette.isDark) Color(0xFF13151B) else Color(0xFFF4F6F9))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR
        Surface(
            color = if (themePalette.isDark) Color(0xFF1E212B) else Color.White,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (themePalette.isDark) Color.White else Color(0xFF1E293B)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Passport Photo Maker",
                                color = if (themePalette.isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00C48C).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "300 DPI Studio",
                                    color = Color(0xFF00C48C),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${selectedPreset.flagEmoji} ${selectedPreset.countryName} (${if (selectedPreset.id == "custom") "${customWidthMm.toInt()}x${customHeightMm.toInt()} mm" else "${selectedPreset.widthMm.toInt()}x${selectedPreset.heightMm.toInt()} mm"})",
                            color = if (themePalette.isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick Change Photo Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Photo",
                            tint = Color(0xFF00C48C)
                        )
                    }
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Pick Photo",
                            tint = Color(0xFF3B82F6)
                        )
                    }
                }
            }
        }

        // STEP TABS ROW
        Surface(
            color = if (themePalette.isDark) Color(0xFF181A22) else Color(0xFFECEFF4),
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF00C48C),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                        color = Color(0xFF00C48C),
                        height = 3.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentTab == index) Color(0xFF00C48C) else (if (themePalette.isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                            )
                        }
                    )
                }
            }
        }

        // MAIN CONTENT AREA (Preview Canvas on Top, Interactive Controls on Bottom)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // UPPER SECTION: LIVE PREVIEW & INTERACTIVE WORKSPACE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .background(if (themePalette.isDark) Color(0xFF0F1015) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (currentTab == 3 && printableSheetPreviewBitmap != null && !selectedPaperSize.isSingle) {
                    // PRINTABLE SHEET PREVIEW (Zoomable Sheet Layout with Page Margins)
                    SheetPreviewCanvas(
                        sheetBitmap = printableSheetPreviewBitmap!!,
                        paperSize = selectedPaperSize,
                        photoCount = photoCountOption,
                        isDark = themePalette.isDark
                    )
                } else {
                    // SINGLE PASSPORT PHOTO INTERACTIVE CROP CANVAS
                    val effectiveAspect = if (selectedPreset.id == "custom") {
                        customWidthMm / customHeightMm
                    } else {
                        selectedPreset.aspectRatio
                    }

                    InteractiveCropCanvas(
                        sourceBitmap = sourceBitmap,
                        renderedBitmap = finalSinglePassportBitmap,
                        aspectRatio = effectiveAspect,
                        zoomScale = zoomScale,
                        panX = panOffsetX,
                        panY = panOffsetY,
                        straightenAngle = straightenAngle + (rotationSteps * 90f),
                        showBiometricGuide = showBiometricGuide,
                        onTransform = { scaleFactor, panChange ->
                            zoomScale = (zoomScale * scaleFactor).coerceIn(0.6f, 4.0f)
                            panOffsetX += panChange.x
                            panOffsetY += panChange.y
                        },
                        isDark = themePalette.isDark
                    )
                }

                // Top Floating Guide Badge
                if (currentTab != 3 && showBiometricGuide) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00C48C), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when {
                                    isAnalyzingFace -> "Detecting face & eye line…"
                                    isRemovingPortraitBackground -> "Refining portrait background…"
                                    detectedFace != null -> "AI face alignment + clean background ready"
                                    sourceBitmap == null -> "Select a clear front-facing photo"
                                    else -> "Face not detected • align manually inside the guide"
                                },
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // LOWER SECTION: DEDICATED TAB CONTROLS
            Surface(
                color = if (themePalette.isDark) Color(0xFF1E212B) else Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentTab) {
                        0 -> {
                            // TAB 0: SIZE & COUNTRY PRESETS
                            SizePresetSelectionSection(
                                presets = presets,
                                selectedPreset = selectedPreset,
                                onSelectPreset = { selectedPreset = it },
                                customWidthMm = customWidthMm,
                                onCustomWidthChange = { customWidthMm = it },
                                customHeightMm = customHeightMm,
                                onCustomHeightChange = { customHeightMm = it },
                                isDark = themePalette.isDark
                            )
                        }
                        1 -> {
                            // TAB 1: BIOMETRIC CROP & TRANSFORM CONTROLS
                            CropAndTransformSection(
                                zoomScale = zoomScale,
                                onZoomChange = { zoomScale = it },
                                straightenAngle = straightenAngle,
                                onStraightenChange = { straightenAngle = it },
                                showBiometricGuide = showBiometricGuide,
                                onToggleBiometricGuide = { showBiometricGuide = it },
                                onRotate90 = { rotationSteps = (rotationSteps + 1) % 4 },
                                onFlipHorizontal = { isFlippedHorizontally = !isFlippedHorizontally },
                                faceDetected = detectedFace != null,
                                isAnalyzingFace = isAnalyzingFace,
                                onAutoAlignFace = { detectedFace?.let { applyAutoFaceAlignment(it) } },
                                onResetTransform = {
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                    straightenAngle = 0f
                                    rotationSteps = 0
                                    isFlippedHorizontally = false
                                },
                                isDark = themePalette.isDark
                            )
                        }
                        2 -> {
                            // TAB 2: BACKGROUND & STUDIO LIGHTING
                            BackgroundAndFilterSection(
                                bgOptions = bgOptions,
                                selectedBgOption = selectedBgOption,
                                onSelectBg = { selectedBgOption = it },
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                brightness = brightnessAdj,
                                onBrightnessChange = { brightnessAdj = it },
                                contrast = contrastAdj,
                                onContrastChange = { contrastAdj = it },
                                saturation = saturationAdj,
                                onSaturationChange = { saturationAdj = it },
                                includeDateStamp = includeDateStamp,
                                onToggleDateStamp = { includeDateStamp = it },
                                applicantName = applicantNameText,
                                onEditNameClick = { showNameDialog = true },
                                isDark = themePalette.isDark
                            )
                        }
                        3 -> {
                            // TAB 3: PRINT SHEET & EXPORT SETTINGS
                            PrintSheetSettingsSection(
                                selectedPaperSize = selectedPaperSize,
                                onSelectPaperSize = { selectedPaperSize = it },
                                photoCount = photoCountOption,
                                onSelectPhotoCount = { photoCountOption = it },
                                cutGuideStyle = cutGuideStyle,
                                onSelectCutGuide = { cutGuideStyle = it },
                                selectedPreset = selectedPreset,
                                isDark = themePalette.isDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // BOTTOM ACTION BAR (Next Tab / Export / Save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentTab < 3) {
                            OutlinedButton(
                                onClick = {
                                    if (currentTab > 0) currentTab--
                                },
                                enabled = currentTab > 0,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.7f)
                                    .height(48.dp),
                                border = BorderStroke(1.dp, if (themePalette.isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                            ) {
                                Text("Previous", color = if (themePalette.isDark) Color(0xFFE2E8F0) else Color(0xFF334155), fontSize = 13.sp)
                            }

                            Button(
                                onClick = { currentTab++ },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (currentTab == 2) "Go to Print Sheet →" else "Next Step →",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            // EXPORT ACTIONS (Save HD Image & Export PDF)
                            Button(
                                onClick = {
                                    val bitmapToSave = if (selectedPaperSize.isSingle) {
                                        finalSinglePassportBitmap
                                    } else {
                                        printableSheetPreviewBitmap ?: finalSinglePassportBitmap
                                    } ?: return@Button

                                    isExporting = true
                                    scope.launch(Dispatchers.IO) {
                                        val prefix = if (selectedPaperSize.isSingle) "PASSPORT_PHOTO" else "PASSPORT_SHEET"
                                        val savedPath = FileUtils.saveBitmapToDocStorage(context, bitmapToSave, prefix)
                                        withContext(Dispatchers.Main) {
                                            isExporting = false
                                            Toast.makeText(context, "Saved image to storage!", Toast.LENGTH_SHORT).show()
                                            exportSuccessDialogInfo = Pair(savedPath, false)
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save HD Image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val bitmapToSave = if (selectedPaperSize.isSingle) {
                                        finalSinglePassportBitmap
                                    } else {
                                        printableSheetPreviewBitmap ?: finalSinglePassportBitmap
                                    } ?: return@Button

                                    isExporting = true
                                    scope.launch(Dispatchers.IO) {
                                        val pdfPath = exportPassportToPdf(
                                            context = context,
                                            bitmap = bitmapToSave,
                                            paperSize = selectedPaperSize,
                                            title = "${selectedPreset.countryName}_Passport_Photo"
                                        )
                                        withContext(Dispatchers.Main) {
                                            isExporting = false
                                            if (pdfPath != null) {
                                                Toast.makeText(context, "Printable PDF ready!", Toast.LENGTH_SHORT).show()
                                                exportSuccessDialogInfo = Pair(pdfPath, true)
                                            } else {
                                                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Printable PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- Name & Date Dialog ----------------
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(applicantNameText) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name & Date Stamp", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Some embassies require the applicant's name and photo capture date printed on the bottom margin.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Applicant Full Name") },
                        placeholder = { Text("e.g. MD. JAHID HASAN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        applicantNameText = tempName
                        includeDateStamp = tempName.isNotBlank()
                        showNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C))
                ) {
                    Text("Apply Stamp", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ---------------- Export Success Dialog ----------------
    exportSuccessDialogInfo?.let { (filePath, isPdf) ->
        val file = File(filePath)
        AlertDialog(
            onDismissRequest = { exportSuccessDialogInfo = null },
            icon = {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF00C48C),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isPdf) "PDF Ready for Printing!" else "Passport Photo Saved!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your passport photo has been generated at 300 DPI high resolution standard.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (themePalette.isDark) Color(0xFF22252E) else Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = file.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareFile(context, file, isPdf)
                        exportSuccessDialogInfo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C48C))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Print", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exportSuccessDialogInfo = null }) {
                    Text("Done")
                }
            }
        )
    }
}

// ==================== TAB 0: SIZE PRESET COMPONENT ====================

@Composable
fun SizePresetSelectionSection(
    presets: List<PassportCountryPreset>,
    selectedPreset: PassportCountryPreset,
    onSelectPreset: (PassportCountryPreset) -> Unit,
    customWidthMm: Float,
    onCustomWidthChange: (Float) -> Unit,
    customHeightMm: Float,
    onCustomHeightChange: (Float) -> Unit,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Document / Country Standard:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
            Text(
                text = "${presets.size} Presets",
                fontSize = 11.sp,
                color = Color(0xFF00C48C),
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                val isSelected = selectedPreset.id == preset.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF00C48C).copy(alpha = 0.14f) else (if (isDark) Color(0xFF22252E) else Color(0xFFF8FAFC)),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) Color(0xFF00C48C) else (if (isDark) Color(0xFF2F3442) else Color(0xFFE2E8F0))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.flagEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = preset.countryName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${preset.docType} • ${preset.description}",
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF00C48C) else (if (isDark) Color(0xFF2E3240) else Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = if (preset.id == "custom") "Custom" else "${preset.widthMm.toInt()}x${preset.heightMm.toInt()} mm",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else (if (isDark) Color.White else Color(0xFF334155)),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Custom Size Input Fields if Custom is Selected
        if (selectedPreset.id == "custom") {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Specify Dimensions (Millimeters):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customWidthMm.toString(),
                            onValueChange = { onCustomWidthChange(it.toFloatOrNull() ?: customWidthMm) },
                            label = { Text("Width (mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeightMm.toString(),
                            onValueChange = { onCustomHeightChange(it.toFloatOrNull() ?: customHeightMm) },
                            label = { Text("Height (mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ==================== TAB 1: BIOMETRIC CROP COMPONENT ====================

@Composable
fun CropAndTransformSection(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    straightenAngle: Float,
    onStraightenChange: (Float) -> Unit,
    showBiometricGuide: Boolean,
    onToggleBiometricGuide: (Boolean) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    faceDetected: Boolean,
    isAnalyzingFace: Boolean,
    onAutoAlignFace: () -> Unit,
    onResetTransform: () -> Unit,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick Action Buttons (Rotate 90, Flip, Reset, Guide Toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRotate90,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rotate 90°", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onFlipHorizontal,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onResetTransform,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", fontSize = 11.sp)
            }
        }

        // One-tap biometric auto alignment using ML Kit face + eye landmarks.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Color(0xFF172A28) else Color(0xFFE9FBF5),
            border = BorderStroke(1.dp, Color(0xFF00C48C).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = faceDetected && !isAnalyzingFace, onClick = onAutoAlignFace)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAnalyzingFace) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00C48C)
                    )
                } else {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color(0xFF00C48C), modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Face & Eye Alignment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (faceDetected) "Re-center face, eye-line and small head tilt automatically"
                        else "Load a clear front-facing portrait to enable",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                if (faceDetected && !isAnalyzingFace) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00C48C))
                }
            }
        }

        // Biometric Oval Guide Switch
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showBiometricGuide) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFF00C48C),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Biometric Head Guide Overlay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Shows ICAO 70-80% face & eye alignment lines", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                Switch(
                    checked = showBiometricGuide,
                    onCheckedChange = onToggleBiometricGuide,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00C48C))
                )
            }
        }

        // Zoom Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom / Scale:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.1fx", zoomScale), fontSize = 12.sp, color = Color(0xFF00C48C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = zoomScale,
                onValueChange = onZoomChange,
                valueRange = 0.8f..3.5f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00C48C), activeTrackColor = Color(0xFF00C48C))
            )
        }

        // Straighten Angle Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Straighten Angle:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.1f°", straightenAngle), fontSize = 12.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = straightenAngle,
                onValueChange = onStraightenChange,
                valueRange = -20f..20f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
            )
        }
    }
}

// ==================== TAB 2: BACKGROUND & FILTERS ====================

@Composable
fun BackgroundAndFilterSection(
    bgOptions: List<PassportBgOption>,
    selectedBgOption: PassportBgOption,
    onSelectBg: (PassportBgOption) -> Unit,
    selectedFilter: PassportFilterType,
    onSelectFilter: (PassportFilterType) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    includeDateStamp: Boolean,
    onToggleDateStamp: (Boolean) -> Unit,
    applicantName: String,
    onEditNameClick: () -> Unit,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Background Color Selector
        Text("Background Color:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(bgOptions) { option ->
                val isSelected = selectedBgOption.id == option.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF00C48C).copy(alpha = 0.15f) else (if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFF00C48C) else Color.Transparent),
                    modifier = Modifier
                        .clickable { onSelectBg(option) }
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (option.color == Color.Transparent) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = option.color,
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                                modifier = Modifier.size(16.dp)
                            ) {}
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(option.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        // Studio Lighting Presets
        Text("Studio Lighting & Filter Presets:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(PassportFilterType.values()) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF00C48C) else (if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                    modifier = Modifier.clickable { onSelectFilter(filter) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filter.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else (if (isDark) Color.White else Color(0xFF0F172A))
                        )
                        Text(
                            text = filter.desc,
                            fontSize = 9.sp,
                            color = if (isSelected) Color.Black.copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                }
            }
        }

        // Manual Sliders (Brightness, Contrast, Saturation)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brightness Enhancement:", fontSize = 12.sp)
                Text("${(brightness * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF00C48C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -0.3f..0.4f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00C48C), activeTrackColor = Color(0xFF00C48C))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Print Contrast:", fontSize = 12.sp)
                Text("${(contrast * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.7f..1.6f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
            )
        }

        // Name / Date Stamp Option
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Name & Date Imprint Stamp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (applicantName.isNotBlank()) "Stamped: $applicantName" else "Optional text imprint on bottom margin",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (includeDateStamp) {
                        IconButton(onClick = onEditNameClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = "Edit Name", tint = Color(0xFF00C48C))
                        }
                    }
                    Switch(
                        checked = includeDateStamp,
                        onCheckedChange = {
                            onToggleDateStamp(it)
                            if (it && applicantName.isBlank()) {
                                onEditNameClick()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00C48C))
                    )
                }
            }
        }
    }
}

// ==================== TAB 3: PRINT SHEET SETTINGS ====================

@Composable
fun PrintSheetSettingsSection(
    selectedPaperSize: SheetPaperSize,
    onSelectPaperSize: (SheetPaperSize) -> Unit,
    photoCount: Int,
    onSelectPhotoCount: (Int) -> Unit,
    cutGuideStyle: CutGuideStyle,
    onSelectCutGuide: (CutGuideStyle) -> Unit,
    selectedPreset: PassportCountryPreset,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Paper Size Selection
        Text("Print Paper / Output Format:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SheetPaperSize.values()) { paper ->
                val isSelected = selectedPaperSize == paper
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF00C48C) else (if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                    modifier = Modifier.clickable { onSelectPaperSize(paper) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = paper.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else (if (isDark) Color.White else Color(0xFF0F172A))
                        )
                        Text(
                            text = if (paper.isSingle) "1x High-Res Digital" else "${paper.widthMm.toInt()}x${paper.heightMm.toInt()} mm",
                            fontSize = 9.sp,
                            color = if (isSelected) Color.Black.copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                }
            }
        }

        if (!selectedPaperSize.isSingle) {
            // Photo Quantity Selection
            Text("Number of Photos per Sheet:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val counts = listOf(2, 4, 6, 8, 12, 16, 24, 32)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(counts) { count ->
                    val isSelected = photoCount == count
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF00C48C).copy(alpha = 0.2f) else (if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00C48C) else Color.Transparent),
                        modifier = Modifier.clickable { onSelectPhotoCount(count) }
                    ) {
                        Text(
                            text = "$count Copies",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00C48C) else (if (isDark) Color.White else Color(0xFF0F172A)),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Cut Guidelines Style
            Text("Cutting Line Guides:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CutGuideStyle.values().forEach { guide ->
                    val isSelected = cutGuideStyle == guide
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF00C48C).copy(alpha = 0.15f) else (if (isDark) Color(0xFF22252E) else Color(0xFFF1F5F9)),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00C48C) else Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectCutGuide(guide) }
                    ) {
                        Text(
                            text = guide.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== INTERACTIVE CANVAS & CROP PREVIEW ====================

@Composable
fun InteractiveCropCanvas(
    sourceBitmap: Bitmap?,
    renderedBitmap: Bitmap?,
    aspectRatio: Float,
    zoomScale: Float,
    panX: Float,
    panY: Float,
    straightenAngle: Float,
    showBiometricGuide: Boolean,
    onTransform: (scaleFactor: Float, panChange: Offset) -> Unit,
    isDark: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        // Calculate card size preserving target passport aspect ratio
        val (cardW, cardH) = if (maxWidthPx / maxHeightPx > aspectRatio) {
            Pair(maxHeightPx * aspectRatio * 0.88f, maxHeightPx * 0.88f)
        } else {
            Pair(maxWidthPx * 0.88f, (maxWidthPx * 0.88f) / aspectRatio)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 10.dp,
            color = Color.White,
            modifier = Modifier
                .size(cardW.dp / 2.6f, cardH.dp / 2.6f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onTransform(zoom, pan)
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (renderedBitmap != null) {
                    Image(
                        bitmap = renderedBitmap.asImageBitmap(),
                        contentDescription = "Passport Photo Rendered",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color(0xFF00C48C), modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select or capture a portrait", color = Color(0xFF475569), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }

                // BIOMETRIC OVAL AND EYE LINE GUIDES OVERLAY
                if (showBiometricGuide) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Biometric Oval Guide (ICAO: 70-80% of frame)
                        val ovalW = w * 0.62f
                        val ovalH = h * 0.72f
                        val ovalLeft = (w - ovalW) / 2f
                        val ovalTop = h * 0.12f

                        // Head outline oval
                        drawOval(
                            color = Color(0xFF00C48C),
                            topLeft = Offset(ovalLeft, ovalTop),
                            size = Size(ovalW, ovalH),
                            style = Stroke(width = 2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                        )

                        // Eye Level Line (Approx 55% from bottom)
                        val eyeY = ovalTop + (ovalH * 0.42f)
                        drawLine(
                            color = Color(0xFF3B82F6).copy(alpha = 0.85f),
                            start = Offset(ovalLeft + 15f, eyeY),
                            end = Offset(ovalLeft + ovalW - 15f, eyeY),
                            strokeWidth = 2f
                        )

                        // Chin Baseline Line (Approx 85% from top)
                        val chinY = ovalTop + ovalH
                        drawLine(
                            color = Color(0xFFF59E0B).copy(alpha = 0.85f),
                            start = Offset(ovalLeft + 25f, chinY),
                            end = Offset(ovalLeft + ovalW - 25f, chinY),
                            strokeWidth = 2f
                        )

                        // Center Vertical Symmetry Line
                        drawLine(
                            color = Color(0xFF00C48C).copy(alpha = 0.4f),
                            start = Offset(w / 2f, 10f),
                            end = Offset(w / 2f, h - 10f),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }
        }
    }
}

// ==================== SHEET PREVIEW CANVAS ====================

@Composable
fun SheetPreviewCanvas(
    sheetBitmap: Bitmap,
    paperSize: SheetPaperSize,
    photoCount: Int,
    isDark: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val sheetAspect = paperSize.widthMm / paperSize.heightMm

        val (sheetW, sheetH) = if (maxWidthPx / maxHeightPx > sheetAspect) {
            Pair(maxHeightPx * sheetAspect * 0.9f, maxHeightPx * 0.9f)
        } else {
            Pair(maxWidthPx * 0.9f, (maxWidthPx * 0.9f) / sheetAspect)
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 12.dp,
            color = Color.White,
            modifier = Modifier
                .size(sheetW.dp / 2.6f, sheetH.dp / 2.6f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Image(
                bitmap = sheetBitmap.asImageBitmap(),
                contentDescription = "Printable Sheet Layout",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ==================== IMAGE RENDERING & PRINT ENGINE ====================

/**
 * High-performance 300 DPI single passport photo renderer.
 */
fun renderSinglePassportPhoto(
    src: Bitmap,
    targetW: Int,
    targetH: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
    angle: Float,
    flipH: Boolean,
    bgColor: Color,
    filter: PassportFilterType,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    nameStamp: String?
): Bitmap {
    val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    // 1. Draw Background if solid color selected
    if (bgColor != Color.Transparent) {
        val bgPaint = Paint().apply {
            color = AndroidColor.argb(
                (bgColor.alpha * 255).toInt(),
                (bgColor.red * 255).toInt(),
                (bgColor.green * 255).toInt(),
                (bgColor.blue * 255).toInt()
            )
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), bgPaint)
    }

    // 2. Setup Image Paint with Filter & Color Adjustments
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        val colorMatrix = ColorMatrix()

        when (filter) {
            PassportFilterType.BW_OFFICIAL -> {
                colorMatrix.setSaturation(0f)
            }
            PassportFilterType.STUDIO_BRIGHT -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.12f, 0f, 0f, 0f, 25f,
                    0f, 1.12f, 0f, 0f, 25f,
                    0f, 0f, 1.12f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            PassportFilterType.CRYSTAL_CLEAR -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, -10f,
                    0f, 0f, 1.2f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            PassportFilterType.WARM_NATURAL -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 15f,
                    0f, 1.05f, 0f, 0f, 10f,
                    0f, 0f, 0.95f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(cm)
            }
            else -> {}
        }

        // Apply custom sliders
        if (brightness != 0f || contrast != 1f || saturation != 1f) {
            val adjCm = ColorMatrix()
            adjCm.setSaturation(saturation)

            val bOffset = brightness * 255f
            val cScale = contrast
            val cOffset = (1f - cScale) * 128f / 2f + bOffset

            val customMatrix = ColorMatrix(floatArrayOf(
                cScale, 0f, 0f, 0f, cOffset,
                0f, cScale, 0f, 0f, cOffset,
                0f, 0f, cScale, 0f, cOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            adjCm.postConcat(customMatrix)
            colorMatrix.postConcat(adjCm)
        }

        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }

    // 3. Matrix Transformation (Scale, Rotate, Pan, Flip)
    val matrix = Matrix()
    val baseScale = max(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat()) * zoom

    // Center pivot
    matrix.postTranslate(-src.width / 2f, -src.height / 2f)
    if (flipH) {
        matrix.postScale(-1f, 1f)
    }
    matrix.postScale(baseScale, baseScale)
    matrix.postRotate(angle)
    matrix.postTranslate((targetW / 2f) + panX * 2.5f, (targetH / 2f) + panY * 2.5f)

    canvas.drawBitmap(src, matrix, imagePaint)

    // 4. Optional Name / Date Bottom Margin Imprint
    if (!nameStamp.isNullOrBlank()) {
        val stampHeight = targetH * 0.09f
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, targetH - stampHeight, targetW.toFloat(), targetH.toFloat(), stampPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textSize = stampHeight * 0.35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("$nameStamp • $dateStr", targetW / 2f, targetH - (stampHeight * 0.35f), textPaint)
    }

    return outBitmap
}

/**
 * Builds standard 300 DPI Printable Grid Sheet (A4 or 4x6" Paper).
 */
fun renderPrintableSheet(
    singlePhoto: Bitmap,
    paperSize: SheetPaperSize,
    photoCount: Int,
    guideStyle: CutGuideStyle,
    photoAspect: Float
): Bitmap {
    val sheetW = (paperSize.widthMm * 300f / 25.4f).roundToInt().coerceAtLeast(1200)
    val sheetH = (paperSize.heightMm * 300f / 25.4f).roundToInt().coerceAtLeast(1800)

    val sheetBmp = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheetBmp)
    canvas.drawColor(AndroidColor.WHITE)

    val cols = when {
        photoCount <= 2 -> 2
        photoCount <= 4 -> 2
        photoCount <= 8 -> 4
        photoCount <= 12 -> 4
        photoCount <= 16 -> 4
        photoCount <= 24 -> 6
        else -> 8
    }
    val rows = (photoCount + cols - 1) / cols

    val marginX = (sheetW * 0.05f).roundToInt()
    val marginY = (sheetH * 0.05f).roundToInt()
    val availableW = sheetW - (2 * marginX)
    val availableH = sheetH - (2 * marginY)

    val maxCellW = availableW / cols
    val maxCellH = availableH / rows

    val cellW: Int
    val cellH: Int
    if (maxCellW.toFloat() / maxCellH.toFloat() > photoAspect) {
        cellH = (maxCellH * 0.90f).roundToInt()
        cellW = (cellH * photoAspect).roundToInt()
    } else {
        cellW = (maxCellW * 0.90f).roundToInt()
        cellH = (cellW / photoAspect).roundToInt()
    }

    val scaledPhoto = Bitmap.createScaledBitmap(singlePhoto, cellW, cellH, true)

    val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        if (guideStyle == CutGuideStyle.DOTTED_SCISSORS) {
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
    }

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.DKGRAY
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    canvas.drawText("PASSPORT PHOTO STUDIO • 300 DPI PRINT SHEET • ${paperSize.displayName}", sheetW / 2f, marginY * 0.6f, headerPaint)

    var drawnCount = 0
    val totalGridW = cols * (cellW + 24)
    val startX = (sheetW - totalGridW) / 2 + 12
    val totalGridH = rows * (cellH + 24)
    val startY = marginY + 40

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (drawnCount >= photoCount) break

            val left = startX + c * (cellW + 24)
            val top = startY + r * (cellH + 24)

            canvas.drawBitmap(scaledPhoto, left.toFloat(), top.toFloat(), null)

            if (guideStyle != CutGuideStyle.NO_BORDER) {
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + cellW).toFloat(),
                    (top + cellH).toFloat(),
                    cutPaint
                )
            }

            drawnCount++
        }
    }

    return sheetBmp
}

/**
 * Creates high-quality sample portrait bitmap for initial display.
 */
fun createSamplePortraitPlaceholder(): Bitmap {
    val w = 600
    val h = 800
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Background gradient tint
    paint.color = AndroidColor.rgb(240, 244, 248)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    // Body / Shoulders
    paint.color = AndroidColor.rgb(44, 62, 80)
    canvas.drawOval(RectF(100f, 500f, 500f, 950f), paint)

    // Neck
    paint.color = AndroidColor.rgb(230, 185, 155)
    canvas.drawRect(250f, 400f, 350f, 520f, paint)

    // Head / Face
    canvas.drawOval(RectF(180f, 160f, 420f, 460f), paint)

    // Hair
    paint.color = AndroidColor.rgb(30, 30, 30)
    canvas.drawOval(RectF(170f, 130f, 430f, 260f), paint)

    // Eyes
    paint.color = AndroidColor.rgb(50, 50, 50)
    canvas.drawCircle(250f, 290f, 14f, paint)
    canvas.drawCircle(350f, 290f, 14f, paint)

    // Smile
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawArc(RectF(260f, 360f, 340f, 400f), 0f, 180f, false, paint)

    return bmp
}

/**
 * Exports Bitmap to standard 300 DPI Printable PDF Document.
 */
fun exportPassportToPdf(context: Context, bitmap: Bitmap, paperSize: SheetPaperSize, title: String): String? {
    val pdfDocument = PdfDocument()
    val pdfDir = FileUtils.getPdfsDir(context)
    val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val pdfFile = File(pdfDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

    try {
        val (pageW, pageH) = if (paperSize.isSingle) {
            Pair(595, 842) // A4 default for single
        } else {
            val wPt = (paperSize.widthMm * 72f / 25.4f).roundToInt()
            val hPt = (paperSize.heightMm * 72f / 25.4f).roundToInt()
            Pair(wPt, hPt)
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val destRect = RectF(0f, 0f, pageW.toFloat(), pageH.toFloat())
        canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

        pdfDocument.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return pdfFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        pdfDocument.close()
        return null
    }
}

/**
 * Shares image or PDF via Android Intent.
 */
fun shareFile(context: Context, file: File, isPdf: Boolean) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isPdf) "application/pdf" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Passport Photo"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
    }
}
