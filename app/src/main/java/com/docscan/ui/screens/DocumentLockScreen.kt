package com.docscan.ui.screens

import com.docscan.util.ThemeManager

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docscan.data.model.DocumentEntity
import com.docscan.security.DocumentLockManager
import com.docscan.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Design Tokens for Lock Experience
private val LockDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF121418), Color(0xFFF8FAFC))
private val LockCardBg: Color get() = ThemeManager.themeColor(Color(0xFF1B1E26), Color(0xFFFFFFFF))
private val LockCardSelectedBg: Color get() = ThemeManager.themeColor(Color(0xFF222834), Color(0xFFCCFBF1))
private val LockInputBg: Color get() = ThemeManager.themeColor(Color(0xFF252932), Color(0xFFF1F5F9))
private val LockAccentTeal = Color(0xFF00BFA5)
private val LockTextPrimary: Color get() = ThemeManager.themeColor(Color(0xFFFFFFFF), Color(0xFF0F172A))
private val LockTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9CA3AF), Color(0xFF475569))
private val LockErrorRed = Color(0xFFEF4444)
private val DeviceIconBg: Color get() = ThemeManager.themeColor(Color(0xFF2563EB), Color(0xFFF1F5F9))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentLockScreen(
    viewModel: ScannerViewModel,
    initialDocId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDocumentDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    val lockedDocIds by DocumentLockManager.lockedDocIds.collectAsStateWithLifecycle()

    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var isImportingFromDevice by remember { mutableStateOf(false) }
    var showLockBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DocumentLockManager.init(context)
    }

    LaunchedEffect(documents, initialDocId) {
        if (initialDocId != null && selectedDocument == null) {
            val doc = documents.firstOrNull { it.id == initialDocId }
            if (doc != null) {
                selectedDocument = doc
                showLockBottomSheet = true
            }
        }
    }

    // SAF Document Picker for Device Import
    val deviceFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingFromDevice = true
            scope.launch {
                try {
                    val importedDoc = viewModel.importDocumentFromDeviceForExtract(listOf(uri))
                    isImportingFromDevice = false
                    if (importedDoc != null) {
                        selectedDocument = importedDoc
                        showLockBottomSheet = true
                        Toast.makeText(context, "Document imported. Set a password to lock it.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not load document from device", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    isImportingFromDevice = false
                    Toast.makeText(context, "Error importing: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = LockDarkBg,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("lock_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LockTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LockDarkBg
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(LockDarkBg)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Header Section with Title, Subtitle, and Illustration
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    LockHeaderSection()
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 2. "Create or Import" Section
                item {
                    Text(
                        text = "Create or Import",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LockTextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LockCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                deviceFilePickerLauncher.launch(
                                    arrayOf("application/pdf", "image/*")
                                )
                            }
                            .testTag("device_import_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Device Icon matching reference UI
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DeviceIconBg,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = "Device",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Device",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = LockTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 3. "Select from This App" Section
                item {
                    Text(
                        text = "Select from This App",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LockTextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (documents.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = LockCardBg.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = LockTextSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No documents found",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LockTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap Device above to import and protect any PDF or image.",
                                    fontSize = 13.sp,
                                    color = LockTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(documents, key = { it.id }) { doc ->
                        val isLocked = lockedDocIds.contains(doc.id)
                        val isSelected = selectedDocument?.id == doc.id

                        LockDocumentRowItem(
                            document = doc,
                            isLocked = isLocked,
                            isSelected = isSelected,
                            onClick = {
                                selectedDocument = doc
                                showLockBottomSheet = true
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // Loading overlay for device import
            if (isImportingFromDevice) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LockCardBg,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = LockAccentTeal,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Importing document...",
                                color = LockTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Lock Document Bottom Sheet
    if (showLockBottomSheet && selectedDocument != null) {
        LockDocumentModalBottomSheet(
            document = selectedDocument!!,
            isAlreadyLocked = lockedDocIds.contains(selectedDocument!!.id),
            onDismiss = {
                showLockBottomSheet = false
            },
            onLockSuccess = { _ ->
                showLockBottomSheet = false
                selectedDocument = null
                Toast.makeText(context, "Document locked successfully", Toast.LENGTH_SHORT).show()
                if (initialDocId != null) {
                    onNavigateBack()
                }
            }
        )
    }
}

/**
 * Top Header Section matching the reference screenshot:
 * Large Title "Lock", descriptive subtitle, and minimal right-hand document & password dots illustration.
 */
@Composable
private fun LockHeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: Title & Subtitle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = "Lock",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LockTextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Encrypt with a password to protect your documents.",
                fontSize = 13.5.sp,
                color = LockTextSecondary,
                lineHeight = 19.sp
            )
        }

        // Right Illustration: Minimal Document Card with Password Dots Badge
        LockHeaderIllustration(
            modifier = Modifier
                .size(width = 96.dp, height = 90.dp)
        )
    }
}

/**
 * Custom vector illustration matching the reference screenshot design:
 * Miniature document sheet with header, content grid/lines, and prominent dark floating badge with password dots.
 */
@Composable
private fun LockHeaderIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val docWidth = size.width * 0.72f
        val docHeight = size.height * 0.95f
        val docLeft = size.width * 0.22f
        val docTop = size.height * 0.02f

        // Document background card (soft off-white)
        drawRoundRect(
            color = Color(0xFFECEFF4),
            topLeft = Offset(docLeft, docTop),
            size = Size(docWidth, docHeight),
            cornerRadius = CornerRadius(8.dp.toPx())
        )

        // Document Header Bar (subtle teal/gray accent)
        drawRoundRect(
            color = Color(0xFF00BFA5).copy(alpha = 0.75f),
            topLeft = Offset(docLeft + 6.dp.toPx(), docTop + 6.dp.toPx()),
            size = Size(docWidth - 12.dp.toPx(), 7.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        // Document Mock lines / table cells
        val startY = docTop + 18.dp.toPx()
        val lineSpacing = 6.5.dp.toPx()
        for (i in 0..7) {
            val lineWidth = if (i % 3 == 0) docWidth * 0.75f else docWidth * 0.6f
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(docLeft + 6.dp.toPx(), startY + i * lineSpacing),
                size = Size(lineWidth, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }

        // Floating Password Dots Badge (Dark rounded pill in top-left foreground)
        val badgeWidth = size.width * 0.65f
        val badgeHeight = 24.dp.toPx()
        val badgeLeft = 0f
        val badgeTop = size.height * 0.12f

        // Badge shadow / glow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(badgeLeft + 1.dp.toPx(), badgeTop + 2.dp.toPx()),
            size = Size(badgeWidth, badgeHeight),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Badge body
        drawRoundRect(
            color = Color(0xFF181B22),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(badgeWidth, badgeHeight),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // 6 Password dots inside badge (••••••)
        val dotRadius = 2.2.dp.toPx()
        val dotStartX = badgeLeft + 10.dp.toPx()
        val dotSpacing = (badgeWidth - 20.dp.toPx()) / 5f
        val dotCenterY = badgeTop + badgeHeight / 2f

        for (i in 0 until 6) {
            drawCircle(
                color = Color.White,
                radius = dotRadius,
                center = Offset(dotStartX + i * dotSpacing, dotCenterY)
            )
        }
    }
}

/**
 * Single document row in the "Select from This App" list matching the reference image.
 */
@Composable
private fun LockDocumentRowItem(
    document: DocumentEntity,
    isLocked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val formattedDate = remember(document.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
        sdf.format(Date(document.createdAt)).lowercase()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) LockCardSelectedBg else LockCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("doc_row_${document.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Thumbnail with Lock overlay if locked
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF262B35)),
                contentAlignment = Alignment.Center
            ) {
                if (document.thumbnailPath.isNotBlank() && File(document.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(document.thumbnailPath),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Small PDF badge at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .background(Color(0xFFEF4444), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "PDF",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Lock status overlay icon
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = LockAccentTeal,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details: Name & Date/Page
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LockTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 12.5.sp,
                        color = LockTextSecondary
                    )

                    Text(
                        text = "  |  ",
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563)
                    )

                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = LockTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    Text(
                        text = "${document.pageCount} ${if (document.pageCount > 1) "Pages" else "Page"}",
                        fontSize = 12.5.sp,
                        color = LockTextSecondary
                    )
                }
            }

            if (isLocked) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = LockAccentTeal.copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Locked",
                        color = LockAccentTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Lock Document Bottom Sheet matching the right half of the reference screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockDocumentModalBottomSheet(
    document: DocumentEntity,
    isAlreadyLocked: Boolean,
    onDismiss: () -> Unit,
    onLockSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val passwordFocusRequester = remember { FocusRequester() }

    var currentPassword by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var passwordTouched by remember { mutableStateOf(false) }
    var confirmTouched by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    // Validation logic
    val isPasswordLengthValid = password.trim().length >= 6
    val isPasswordMatching = password.trim() == confirmPassword.trim() && confirmPassword.isNotBlank()
    val isCurrentPasswordValid = !isAlreadyLocked || currentPassword.isNotBlank()
    val isFormValid = isCurrentPasswordValid && isPasswordLengthValid && isPasswordMatching

    LaunchedEffect(Unit) {
        passwordFocusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1B1E26),
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFF4B5563),
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
            ) {}
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = if (isAlreadyLocked) "Update Lock Password" else "Lock Document",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LockTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle matching reference screenshot
            Text(
                text = if (isAlreadyLocked)
                    "Verify the current password before setting a new one. The document will be locked immediately after the update."
                else
                    "When locked, a password is required to view this document. The document becomes protected immediately.",
                fontSize = 13.5.sp,
                color = LockTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isAlreadyLocked) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        inlineError = null
                    },
                    placeholder = {
                        Text(
                            text = "Current password",
                            color = Color(0xFF6B7280),
                            fontSize = 15.sp
                        )
                    },
                    visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                            Icon(
                                imageVector = if (isCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isCurrentPasswordVisible) "Hide current password" else "Show current password",
                                tint = if (isCurrentPasswordVisible) LockAccentTeal else Color(0xFF6B7280)
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LockInputBg,
                        unfocusedContainerColor = LockInputBg,
                        focusedTextColor = LockTextPrimary,
                        unfocusedTextColor = LockTextPrimary,
                        focusedBorderColor = LockAccentTeal,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = LockAccentTeal
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_current_password_input")
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // New password
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordTouched = true
                    inlineError = null
                },
                placeholder = {
                    Text(
                        text = if (isAlreadyLocked) "New password" else "Enter password",
                        color = Color(0xFF6B7280),
                        fontSize = 15.sp
                    )
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = if (isPasswordVisible) LockAccentTeal else Color(0xFF6B7280)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LockInputBg,
                    unfocusedContainerColor = LockInputBg,
                    focusedTextColor = LockTextPrimary,
                    unfocusedTextColor = LockTextPrimary,
                    focusedBorderColor = LockAccentTeal,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = LockAccentTeal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
                    .testTag("lock_password_input")
            )

            // Password length hint/validation
            if (passwordTouched && password.isNotBlank() && !isPasswordLengthValid) {
                Text(
                    text = "Password must be at least 6 characters",
                    color = LockErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Field 2: Confirm password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmTouched = true
                    inlineError = null
                },
                placeholder = {
                    Text(
                        text = "Confirm password",
                        color = Color(0xFF6B7280),
                        fontSize = 15.sp
                    )
                },
                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                        Icon(
                            imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password",
                            tint = if (isConfirmPasswordVisible) LockAccentTeal else Color(0xFF6B7280)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LockInputBg,
                    unfocusedContainerColor = LockInputBg,
                    focusedTextColor = LockTextPrimary,
                    unfocusedTextColor = LockTextPrimary,
                    focusedBorderColor = LockAccentTeal,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = LockAccentTeal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_confirm_password_input")
            )

            // Passwords match validation
            if (confirmTouched && confirmPassword.isNotBlank() && !isPasswordMatching) {
                Text(
                    text = "Passwords do not match",
                    color = LockErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 4.dp)
                )
            }

            if (inlineError != null) {
                Text(
                    text = inlineError ?: "",
                    color = LockErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons: [ Cancel ] and [ Lock ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cancel Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2D3240)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("lock_sheet_cancel_btn")
                ) {
                    Text(
                        text = "Cancel",
                        color = LockTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Lock Button
                Button(
                    onClick = {
                        if (!isFormValid || isProcessing) return@Button
                        isProcessing = true
                        focusManager.clearFocus()

                        if (isAlreadyLocked && !DocumentLockManager.verifyPassword(context, document.id, currentPassword)) {
                            isProcessing = false
                            inlineError = "Current password is incorrect"
                            return@Button
                        }

                        val success = DocumentLockManager.lockDocument(
                            context = context,
                            docId = document.id,
                            password = password
                        )
                        isProcessing = false

                        if (success) {
                            onLockSuccess(document.id)
                        } else {
                            inlineError = "Encryption failed. Please try again."
                        }
                    },
                    enabled = isFormValid && !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LockAccentTeal,
                        disabledContainerColor = Color(0xFF1E3A34)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("lock_sheet_confirm_btn")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isAlreadyLocked) "Update" else "Lock",
                            color = if (isFormValid) Color.White else Color(0xFF6B7280),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
