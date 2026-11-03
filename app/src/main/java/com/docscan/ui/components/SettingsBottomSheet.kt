package com.docscan.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.ui.theme.rememberAppThemePalette
import com.docscan.util.AppPinSecurity
import com.docscan.util.AppThemeMode
import com.docscan.util.ThemeManager
import java.io.File
import java.util.Locale

private val CardBackground: Color @Composable get() = rememberAppThemePalette().card
private val CardBorderColor: Color @Composable get() = rememberAppThemePalette().cardBorder
private val PrimaryTeal = Color(0xFF00C48C)
private val TextPrimary: Color @Composable get() = rememberAppThemePalette().textPrimary
private val TextSecondary: Color @Composable get() = rememberAppThemePalette().textSecondary
private val TextMuted: Color @Composable get() = rememberAppThemePalette().textMuted
private val DividerColor: Color @Composable get() = rememberAppThemePalette().divider
private val GoldBadge = Color(0xFFF59E0B)

/**
 * Helper to calculate device storage info
 */
fun getDeviceStorageInfo(): Triple<String, String, Float> {
    return try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val freeGb = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val usedGb = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

        val fraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.45f

        val usedStr = String.format(Locale.getDefault(), "%.1f GB", usedGb)
        val totalStr = String.format(Locale.getDefault(), "%.1f GB", totalGb)
        val freeStr = String.format(Locale.getDefault(), "%.1f GB", freeGb)

        Triple("$usedStr / $totalStr", freeStr, fraction)
    } catch (e: Exception) {
        Triple("Storage unavailable", "—", 0f)
    }
}

/**
 * Calculates current cache directory size in MB
 */
private fun calculateCacheSize(context: Context): String {
    return try {
        var sizeBytes = 0L
        context.cacheDir?.walkTopDown()?.forEach { file ->
            if (file.isFile) sizeBytes += file.length()
        }
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        String.format(Locale.getDefault(), "%.1f", mb)
    } catch (e: Exception) {
        "0.0"
    }
}

@Composable
fun SettingsTabContent(
    totalDocumentsCount: Int = 0,
    onNavigateToScan: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {}
) {
    SettingsContentInternal(
        isEmbeddedTab = true,
        onDismiss = {},
        onNavigateToScan = onNavigateToScan,
        onNavigateToFiles = onNavigateToFiles,
        totalDocumentsCount = totalDocumentsCount
    )
}

@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    totalDocumentsCount: Int = 0
) {
    SettingsFullScreen(
        onDismiss = onDismiss,
        onNavigateToScan = onNavigateToScan,
        onNavigateToFiles = onNavigateToFiles,
        totalDocumentsCount = totalDocumentsCount
    )
}

@Composable
fun SettingsFullScreen(
    onDismiss: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    totalDocumentsCount: Int = 0
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        SettingsContentInternal(
            isEmbeddedTab = false,
            onDismiss = onDismiss,
            onNavigateToScan = onNavigateToScan,
            onNavigateToFiles = onNavigateToFiles,
            totalDocumentsCount = totalDocumentsCount
        )
    }
}

@Composable
fun SettingsContentInternal(
    isEmbeddedTab: Boolean = false,
    onDismiss: () -> Unit = {},
    onNavigateToScan: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    totalDocumentsCount: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE) }

    // Theme state
    val currentThemeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()

    // User Profile state
    var userName by remember { mutableStateOf(prefs.getString("user_name", "User Account") ?: "User Account") }
    var userEmail by remember { mutableStateOf(prefs.getString("user_email", "user@docscanner.local") ?: "user@docscanner.local") }
    var userAvatarUri by remember { mutableStateOf(prefs.getString("user_avatar_uri", null)) }

    // Scanner Preferences
    var autoAdjustBorders by remember { mutableStateOf(prefs.getBoolean("scan_auto_adjust_borders", true)) }
    var adjustAfterEachScan by remember { mutableStateOf(prefs.getBoolean("scan_adjust_after_each_scan", false)) }
    var saveScansToGallery by remember { mutableStateOf(prefs.getBoolean("scan_save_to_gallery", false)) }
    var shutterSoundEnabled by remember { mutableStateOf(prefs.getBoolean("scan_shutter_sound", true)) }
    val galleryWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            saveScansToGallery = true
            prefs.edit().putBoolean("scan_save_to_gallery", true).apply()
        } else {
            Toast.makeText(context, "Storage permission is needed to save to Gallery on this Android version", Toast.LENGTH_LONG).show()
        }
    }

    // PDF & Document Preferences
    var defaultFileName by remember { mutableStateOf(prefs.getString("doc_default_file_name", "DocScanner_") ?: "DocScanner_") }
    var selectedPdfFormat by remember { mutableStateOf(prefs.getString("doc_pdf_format", "PDF") ?: "PDF") }
    var selectedPageSize by remember { mutableStateOf(prefs.getString("doc_page_size", "A4 (Standard)") ?: "A4 (Standard)") }
    var selectedPdfQuality by remember { mutableStateOf(prefs.getString("doc_pdf_quality", "High (Recommended)") ?: "High (Recommended)") }

    // OCR Preferences
    var localOcrEnabled by remember { mutableStateOf(prefs.getBoolean("ocr_local_extraction", true)) }
    var selectedOcrLanguage by remember { mutableStateOf(prefs.getString("ocr_language", "English") ?: "English") }

    // Security
    var pinLockEnabled by remember { mutableStateOf(prefs.getBoolean("sec_folder_password", false)) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf("change") }

    // Storage info
    var storageInfo by remember { mutableStateOf(getDeviceStorageInfo()) }
    var cacheSizeMb by remember { mutableStateOf(calculateCacheSize(context)) }

    // Active Dialog States
    var showAccountDialog by remember { mutableStateOf(false) }
    var showFileNameDialog by remember { mutableStateOf(false) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showPdfQualityDialog by remember { mutableStateOf(false) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageInfo = getDeviceStorageInfo()
        cacheSizeMb = calculateCacheSize(context)
    }

    val contentModifier = if (isEmbeddedTab) {
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    } else {
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    }

    Column(
        modifier = contentModifier
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = if (isEmbeddedTab) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isEmbeddedTab) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(1.dp, CardBorderColor, CircleShape)
                            .testTag("btn_back_settings")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Preferences & App Configurations",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PrimaryTeal.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "PRO OFFLINE",
                    color = PrimaryTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 1. User Profile Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, CardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAccountDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                        .border(2.dp, PrimaryTeal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (userAvatarUri != null) {
                        AsyncImage(
                            model = userAvatarUri,
                            contentDescription = "User Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Avatar",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = userName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldBadge.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "LOCAL",
                                color = GoldBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = userEmail,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Edit Profile",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Storage & Cache Card (Clean & Compact)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, CardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryTeal.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = "Storage",
                                tint = PrimaryTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "App Storage & Cache",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Cache: $cacheSizeMb MB  •  $totalDocumentsCount Documents",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            try {
                                context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                                cacheSizeMb = calculateCacheSize(context)
                                storageInfo = getDeviceStorageInfo()
                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                cacheSizeMb = calculateCacheSize(context)
                                Toast.makeText(context, "Could not clear all cache files", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Clean Cache",
                            color = PrimaryTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { storageInfo.third },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryTeal,
                    trackColor = Color(0xFF232A36),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 1: SCANNER PREFERENCES
        SectionHeader(text = "SCANNER PREFERENCES")
        SettingsGroupCard {
            ToggleSettingsItem(
                title = "Auto Adjust Borders",
                subtitle = "In-app camera only (ML Kit crops automatically)",
                checked = autoAdjustBorders,
                onCheckedChange = {
                    autoAdjustBorders = it
                    prefs.edit().putBoolean("scan_auto_adjust_borders", it).apply()
                },
                icon = Icons.Outlined.CropFree,
                iconBgColor = Color(0xFF10B981).copy(alpha = 0.12f),
                iconTint = Color(0xFF34D399),
                testTag = "switch_auto_adjust_borders"
            )

            ToggleSettingsItem(
                title = "Adjust Corners After Scan",
                subtitle = "Review and refine corner points after capturing",
                checked = adjustAfterEachScan,
                onCheckedChange = {
                    adjustAfterEachScan = it
                    prefs.edit().putBoolean("scan_adjust_after_each_scan", it).apply()
                },
                icon = Icons.Outlined.Layers,
                iconBgColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                iconTint = Color(0xFF60A5FA),
                testTag = "switch_adjust_after_each_scan"
            )

            ToggleSettingsItem(
                title = "Save Scans to Gallery",
                subtitle = "Save finalized scans to Pictures/Docscanner",
                checked = saveScansToGallery,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        galleryWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveScansToGallery = enabled
                        prefs.edit().putBoolean("scan_save_to_gallery", enabled).apply()
                    }
                },
                icon = Icons.Outlined.Image,
                iconBgColor = Color(0xFFEC4899).copy(alpha = 0.12f),
                iconTint = Color(0xFFF472B6),
                testTag = "switch_save_scans_to_gallery"
            )

            ToggleSettingsItem(
                title = "Camera Shutter Sound",
                subtitle = "Managed by Android / ML Kit; cannot override",
                checked = shutterSoundEnabled,
                onCheckedChange = {
                    shutterSoundEnabled = it
                    prefs.edit().putBoolean("scan_shutter_sound", it).apply()
                },
                icon = Icons.Outlined.CameraAlt,
                iconBgColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
                iconTint = Color(0xFFFBBF24),
                enabled = false,
                showDivider = false,
                testTag = "switch_shutter_sound"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 2: DOCUMENT & PDF DEFAULTS
        SectionHeader(text = "DOCUMENT & PDF DEFAULTS")
        SettingsGroupCard {
            SettingsNavigationRow(
                icon = Icons.Outlined.Edit,
                title = "Default File Name Pattern",
                subtitle = defaultFileName,
                iconBgColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                iconTint = Color(0xFF60A5FA),
                onClick = { showFileNameDialog = true },
                testTag = "item_default_file_name"
            )

            SettingsNavigationRow(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Default Page Size",
                subtitle = selectedPageSize,
                iconBgColor = Color(0xFFF43F5E).copy(alpha = 0.12f),
                iconTint = Color(0xFFFB7185),
                onClick = { showPageSizeDialog = true },
                testTag = "item_page_size"
            )

            SettingsNavigationRow(
                icon = Icons.Outlined.Description,
                title = "PDF Export Quality",
                subtitle = selectedPdfQuality,
                iconBgColor = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                iconTint = Color(0xFFA78BFA),
                showDivider = false,
                onClick = { showPdfQualityDialog = true },
                testTag = "item_pdf_quality"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 3: OCR & TEXT RECOGNITION
        SectionHeader(text = "OCR & TEXT RECOGNITION")
        SettingsGroupCard {
            ToggleSettingsItem(
                title = "On-Device OCR Engine",
                subtitle = "Automatically extract Latin-script text during scanning",
                checked = localOcrEnabled,
                onCheckedChange = {
                    localOcrEnabled = it
                    prefs.edit().putBoolean("ocr_local_extraction", it).apply()
                },
                icon = Icons.Outlined.TextFields,
                iconBgColor = Color(0xFF06B6D4).copy(alpha = 0.12f),
                iconTint = Color(0xFF22D3EE),
                testTag = "switch_ocr_engine"
            )

            SettingsNavigationRow(
                icon = Icons.Outlined.Language,
                title = "Recognition Language (Latin)",
                subtitle = "Latin OCR (auto-detect); preference: $selectedOcrLanguage",
                iconBgColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
                iconTint = Color(0xFFFBBF24),
                showDivider = false,
                onClick = { showOcrLanguageDialog = true },
                testTag = "item_ocr_language"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 4: SECURITY & PRIVACY
        SectionHeader(text = "SECURITY & PRIVACY")
        SettingsGroupCard {
            ToggleSettingsItem(
                title = "App PIN Lock",
                subtitle = if (pinLockEnabled) "Enabled (Protected with 4-digit PIN)" else "Protect documents with a PIN code",
                checked = pinLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showPinSetupDialog = true
                    } else {
                        pendingPinAction = "disable"
                        showPinVerifyDialog = true
                    }
                },
                icon = Icons.Outlined.Lock,
                iconBgColor = Color(0xFF10B981).copy(alpha = 0.12f),
                iconTint = Color(0xFF34D399),
                showDivider = pinLockEnabled,
                testTag = "switch_pin_lock"
            )

            if (pinLockEnabled) {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Lock,
                    title = "Change 4-Digit PIN",
                    subtitle = "Update your existing security passcode",
                    iconBgColor = Color(0xFF10B981).copy(alpha = 0.12f),
                    iconTint = Color(0xFF34D399),
                    showDivider = false,
                    onClick = { pendingPinAction = "change"; showPinVerifyDialog = true },
                    testTag = "item_change_pin"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 5: APPEARANCE
        SectionHeader(text = "APPEARANCE")
        SettingsGroupCard {
            SettingsNavigationRow(
                icon = Icons.Outlined.Palette,
                title = "App Theme",
                subtitle = when (currentThemeMode) {
                    AppThemeMode.LIGHT -> "Light Mode"
                    AppThemeMode.DARK -> "Dark Mode"
                    AppThemeMode.SYSTEM_DEFAULT -> "System Default"
                },
                iconBgColor = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                iconTint = Color(0xFFA78BFA),
                showDivider = false,
                onClick = { showThemeDialog = true },
                testTag = "item_theme"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 6: SUPPORT & ABOUT
        SectionHeader(text = "SUPPORT & ABOUT")
        SettingsGroupCard {
            SettingsNavigationRow(
                icon = Icons.Outlined.HelpOutline,
                title = "User Guide & Quick Tips",
                subtitle = "Learn NID scanning, batch capture, and smart editing",
                iconBgColor = Color(0xFF14B8A6).copy(alpha = 0.12f),
                iconTint = Color(0xFF2DD4BF),
                onClick = { showHelpDialog = true },
                testTag = "item_help"
            )

            SettingsNavigationRow(
                icon = Icons.Outlined.Feedback,
                title = "Send Feedback",
                subtitle = "Share suggestions or report any issue",
                iconBgColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                iconTint = Color(0xFF60A5FA),
                onClick = { showFeedbackDialog = true },
                testTag = "item_feedback"
            )

            SettingsNavigationRow(
                icon = Icons.Outlined.Info,
                title = "About DocScanner",
                subtitle = "Application details and privacy",
                iconBgColor = Color(0xFF06B6D4).copy(alpha = 0.12f),
                iconTint = Color(0xFF22D3EE),
                showDivider = false,
                onClick = { showAboutDialog = true },
                testTag = "item_about"
            )
        }
    }

    // ==================== DIALOGS ====================

    // 1. Account / Profile Edit Dialog
    if (showAccountDialog) {
        var tempName by remember { mutableStateOf(userName) }
        var tempEmail by remember { mutableStateOf(userEmail) }
        var tempAvatarUri by remember { mutableStateOf(userAvatarUri) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                tempAvatarUri = it.toString()
            }
        }

        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PrimaryTeal)
                    Text("Profile Details", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E3440))
                            .border(2.dp, PrimaryTeal, CircleShape)
                            .clickable { imagePickerLauncher.launch(arrayOf("image/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (tempAvatarUri != null) {
                            AsyncImage(
                                model = tempAvatarUri,
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Default Avatar",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(PrimaryTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = "Tap avatar to change picture",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Display Name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Email Address", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        userName = tempName.ifBlank { "User Account" }
                        userEmail = tempEmail.ifBlank { "user@docscanner.local" }
                        userAvatarUri = tempAvatarUri
                        prefs.edit()
                            .putString("user_name", userName)
                            .putString("user_email", userEmail)
                            .putString("user_avatar_uri", userAvatarUri)
                            .apply()
                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                        showAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 2. Default File Name Dialog
    if (showFileNameDialog) {
        var tempName by remember { mutableStateOf(defaultFileName) }
        AlertDialog(
            onDismissRequest = { showFileNameDialog = false },
            containerColor = CardBackground,
            title = { Text("Default File Name Pattern", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "New scans will automatically be named with this prefix and timestamp.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Prefix Pattern", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        defaultFileName = tempName.ifBlank { "DocScanner_" }
                        prefs.edit().putString("doc_default_file_name", defaultFileName).apply()
                        Toast.makeText(context, "Prefix saved", Toast.LENGTH_SHORT).show()
                        showFileNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFileNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 3. Page Size Dialog
    if (showPageSizeDialog) {
        val pageSizes = listOf("A4 (Standard)", "US Letter", "Legal", "Auto / Original")
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            containerColor = CardBackground,
            title = { Text("Default Page Size", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pageSizes.forEach { size ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPageSize = size
                                    prefs.edit().putString("doc_page_size", size).apply()
                                    showPageSizeDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedPageSize == size,
                                onClick = {
                                    selectedPageSize = size
                                    prefs.edit().putString("doc_page_size", size).apply()
                                    showPageSizeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(size, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSizeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 4. PDF Quality Dialog
    if (showPdfQualityDialog) {
        val qualities = listOf(
            "Original HD (Highest quality)",
            "High (Recommended)",
            "Standard (Balanced size)",
            "Compressed (Small file size)"
        )
        AlertDialog(
            onDismissRequest = { showPdfQualityDialog = false },
            containerColor = CardBackground,
            title = { Text("PDF Export Quality", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    qualities.forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPdfQuality = quality
                                    prefs.edit().putString("doc_pdf_quality", quality).apply()
                                    showPdfQualityDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedPdfQuality == quality,
                                onClick = {
                                    selectedPdfQuality = quality
                                    prefs.edit().putString("doc_pdf_quality", quality).apply()
                                    showPdfQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(quality, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPdfQualityDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 5. OCR Language Dialog
    if (showOcrLanguageDialog) {
        val languages = listOf("English", "Spanish (Español)", "French (Français)", "German (Deutsch)")
        AlertDialog(
            onDismissRequest = { showOcrLanguageDialog = false },
            containerColor = CardBackground,
            title = { Text("Primary OCR Language", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("The installed ML Kit Latin OCR detects Latin-script languages automatically. Bengali, Arabic, Chinese and Japanese require separate recognizers and are not supported by this build.", color = TextSecondary, fontSize = 12.sp)
                    languages.forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedOcrLanguage = lang
                                    prefs.edit().putString("ocr_language", lang).apply()
                                    showOcrLanguageDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedOcrLanguage == lang,
                                onClick = {
                                    selectedOcrLanguage = lang
                                    prefs.edit().putString("ocr_language", lang).apply()
                                    showOcrLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOcrLanguageDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 6. PIN Setup Dialog
    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryTeal)
                    Text("Set 4-Digit Security PIN", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a 4-digit PIN to lock and protect confidential documents.", color = TextSecondary, fontSize = 13.sp)

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it },
                        label = { Text("4-Digit PIN", color = TextSecondary) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinConfirm = it },
                        label = { Text("Confirm 4-Digit PIN", color = TextSecondary) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            errorMessage = "PIN must be exactly 4 digits"
                        } else if (pinInput != pinConfirm) {
                            errorMessage = "PIN codes do not match"
                        } else {
                            AppPinSecurity.setPin(context, pinInput)
                            pinLockEnabled = true
                            Toast.makeText(context, "Security PIN successfully configured!", Toast.LENGTH_SHORT).show()
                            showPinSetupDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Save PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showPinVerifyDialog) {
        var enteredPin by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPinVerifyDialog = false },
            containerColor = CardBackground,
            title = { Text("Confirm existing PIN", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { enteredPin = it; invalid = false } },
                        label = { Text("Current 4-digit PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                    )
                    if (invalid) Text("Wrong PIN", color = Color.Red)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (AppPinSecurity.verify(context, enteredPin)) {
                        showPinVerifyDialog = false
                        if (pendingPinAction == "disable") {
                            AppPinSecurity.disable(context)
                            pinLockEnabled = false
                            Toast.makeText(context, "App PIN disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            showPinSetupDialog = true
                        }
                    } else invalid = true
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showPinVerifyDialog = false }) { Text("Cancel") } }
        )
    }

    // 7. Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = CardBackground,
            title = { Text("Choose Theme", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AppThemeMode.LIGHT to "Light Mode (Default)",
                        AppThemeMode.DARK to "Dark Mode",
                        AppThemeMode.SYSTEM_DEFAULT to "System Default"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 8. User Guide / Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = PrimaryTeal)
                    Text("User Guide & Tips", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🪪 NID Card 2-Side Scanning", color = PrimaryTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Select 'NID Card' mode in camera scanner. Capture front side, then back side. The app automatically merges both sides into an A4 sheet.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("✏️ Smart Erase & Edit Text", color = PrimaryTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Open any scanned document, tap Edit to access Magic Eraser, AI Smart Brush, and in-place OCR Text Replacement.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📄 PDF Export & Sharing", color = PrimaryTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Export high-resolution PDFs or individual page images. All documents remain 100% on-device and private.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Got It", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 9. Feedback Dialog
    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        var rating by remember { mutableFloatStateOf(5f) }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Feedback, contentDescription = null, tint = PrimaryTeal)
                    Text("Send Feedback", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rate your experience with DocScanner:", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= rating) Color(0xFFFBBF24) else Color(0xFF475569),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { rating = i.toFloat() }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Write your comments or suggestions...", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (feedbackText.isBlank()) {
                            Toast.makeText(context, "Write your feedback first", Toast.LENGTH_SHORT).show()
                        } else {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Docscanner feedback (${rating.toInt()}/5)")
                                putExtra(Intent.EXTRA_TEXT, "Rating: ${rating.toInt()}/5\n\n$feedbackText")
                            }
                            try {
                                context.startActivity(Intent.createChooser(share, "Send feedback using"))
                                showFeedbackDialog = false
                            } catch (_: Exception) {
                                Toast.makeText(context, "No app available to send feedback", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Submit", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 10. About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = CardBackground,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = PrimaryTeal)
                    Text("About DocScanner", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Docscanner v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("A professional on-device document scanning and PDF management application.", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🔒 Private local document storage", color = PrimaryTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Your local scans remain on this device unless you share or export them. Online AI features may send selected data to their providers.", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ==================== SHARED REUSABLE COMPONENTS ====================

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = TextMuted,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    iconBgColor: Color = PrimaryTeal.copy(alpha = 0.12f),
    iconTint: Color = PrimaryTeal
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(iconBgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ToggleSettingsItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconBgColor: Color = PrimaryTeal.copy(alpha = 0.12f),
    iconTint: Color = PrimaryTeal,
    showDivider: Boolean = true,
    testTag: String = ""
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .testTag(testTag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (icon != null) {
                    SettingsIconBadge(
                        icon = icon,
                        contentDescription = title,
                        iconBgColor = iconBgColor,
                        iconTint = iconTint
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryTeal,
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF222834),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 66.dp else 16.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = DividerColor
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconBgColor: Color = PrimaryTeal.copy(alpha = 0.12f),
    iconTint: Color = PrimaryTeal,
    showDivider: Boolean = true,
    testTag: String = "",
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .testTag(testTag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsIconBadge(
                    icon = icon,
                    contentDescription = title,
                    iconBgColor = iconBgColor,
                    iconTint = iconTint
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = DividerColor
            )
        }
    }
}
