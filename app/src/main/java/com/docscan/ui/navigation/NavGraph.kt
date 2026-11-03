package com.docscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docscan.data.model.ScanMode
import com.docscan.ui.screens.AddWatermarkScreen
import com.docscan.ui.screens.CameraScanScreen
import com.docscan.ui.screens.CompressPdfScreen
import com.docscan.ui.screens.CropFilterScreen
import com.docscan.ui.screens.CropScreen
import com.docscan.ui.screens.DocumentDetailScreen
import com.docscan.ui.screens.DocumentEditTextScreen
import com.docscan.ui.screens.DocumentLockScreen
import com.docscan.ui.screens.DocumentPreviewScreen
import com.docscan.ui.screens.EditPdfScreen
import com.docscan.ui.screens.ActiveEditorTool
import com.docscan.ui.screens.ExtractPdfPagesScreen
import com.docscan.ui.screens.ExtractTextScreen
import com.docscan.ui.screens.HomeScreen
import com.docscan.ui.screens.NidScanSetupScreen
import com.docscan.ui.screens.ImageResizerScreen
import com.docscan.ui.screens.MergeFilesScreen
import com.docscan.ui.screens.PassportPhotoMakerScreen
import com.docscan.ui.screens.PdfToExcelScreen
import com.docscan.ui.screens.PdfToImageScreen
import com.docscan.ui.screens.PdfToLongImageScreen
import com.docscan.ui.screens.PdfToWordScreen
import com.docscan.ui.screens.PhotoBackgroundRemoverScreen
import com.docscan.ui.screens.OpenCvCropScreen
import com.docscan.ui.screens.WordReaderScreen
import com.docscan.ui.screens.ExcelReaderScreen
import com.docscan.ui.screens.AIChatScreen
import com.docscan.ui.viewmodel.AIChatViewModel
import com.docscan.ui.screens.ReorderPagesScreen
import com.docscan.ui.screens.RotatePagesScreen
import com.docscan.ui.screens.SignPdfEditorScreen
import com.docscan.ui.screens.SignPdfSelectionScreen
import com.docscan.ui.screens.SinglePageEditorScreen
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.ExternalFileRouter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import android.net.Uri
import java.io.File

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ExtractPdfPages : Screen("extract_pdf_pages")
    object ExtractPdfPagesDoc : Screen("extract_pdf_pages/{docId}") {
        fun createRoute(docId: Long) = "extract_pdf_pages/$docId"
    }
    object ExtractText : Screen("extract_text")
    object ExtractTextDoc : Screen("extract_text/{docId}?tab={tab}") {
        fun createRoute(docId: Long, tab: Int = 0) = "extract_text/$docId?tab=$tab"
    }
    object AddWatermark : Screen("add_watermark")
    object AddWatermarkDoc : Screen("add_watermark/{docId}") {
        fun createRoute(docId: Long) = "add_watermark/$docId"
    }
    object ReorderPages : Screen("reorder_pages")
    object ReorderDocumentPages : Screen("reorder_document/{docId}") {
        fun createRoute(docId: Long) = "reorder_document/$docId"
    }
    object RotatePdf : Screen("rotate_pdf")
    object RotatePdfDoc : Screen("rotate_pdf/{docId}") {
        fun createRoute(docId: Long) = "rotate_pdf/$docId"
    }
    object MergeFiles : Screen("merge_files?docId={docId}") {
        fun createRoute(docId: Long? = null) = if (docId != null) "merge_files?docId=$docId" else "merge_files"
    }
    object CompressPdf : Screen("compress_pdf?docId={docId}") {
        fun createRoute(docId: Long? = null) = if (docId != null) "compress_pdf?docId=$docId" else "compress_pdf"
    }
    object SignPdf : Screen("sign_pdf")
    object SignPdfEditor : Screen("sign_pdf_editor/{docId}") {
        fun createRoute(docId: Long) = "sign_pdf_editor/$docId"
    }
    object EditPdf : Screen("edit_pdf")
    object ToWord : Screen("to_word")
    object ToWordDoc : Screen("to_word/{docId}") {
        fun createRoute(docId: Long) = "to_word/$docId"
    }
    object ToExcel : Screen("to_excel")
    object ToExcelDoc : Screen("to_excel/{docId}") {
        fun createRoute(docId: Long) = "to_excel/$docId"
    }
    object PdfToImage : Screen("pdf_to_image")
    object PdfToImageDoc : Screen("pdf_to_image/{docId}") {
        fun createRoute(docId: Long) = "pdf_to_image/$docId"
    }
    object PdfToLongImage : Screen("pdf_to_long_image")
    object PdfToLongImageDoc : Screen("pdf_to_long_image/{docId}") {
        fun createRoute(docId: Long) = "pdf_to_long_image/$docId"
    }
    object NidSetup : Screen("nid_setup")
    object Camera : Screen("camera")
    object CameraAppend : Screen("camera/{docId}") {
        fun createRoute(docId: Long) = "camera/$docId"
    }
    object Crop : Screen("crop")
    object CropAppend : Screen("crop/{docId}") {
        fun createRoute(docId: Long) = "crop/$docId"
    }
    object DocumentPreview : Screen("document_preview")
    object DocumentDetail : Screen("detail/{docId}") {
        fun createRoute(docId: Long) = "detail/$docId"
    }
    object DocumentEditText : Screen("document_edit_text/{docId}") {
        fun createRoute(docId: Long) = "document_edit_text/$docId"
    }
    object SinglePageEditor : Screen("single_page_editor/{pageId}?tool={tool}") {
        fun createRoute(pageId: Long, tool: String = "none") = "single_page_editor/$pageId?tool=$tool"
    }
    object PassportPhotoMaker : Screen("passport_photo_maker")
    object ImageResizer : Screen("image_resizer")
    object PhotoBackgroundRemover : Screen("photo_background_remover")
    object DocumentLock : Screen("document_lock?docId={docId}") {
        fun createRoute(docId: Long? = null): String = if (docId != null) "document_lock?docId=$docId" else "document_lock"
    }
    object WordReader : Screen("word_reader?filePath={filePath}&uri={uri}&title={title}") {
        fun createRoute(filePath: String? = null, uri: String? = null, title: String? = null): String {
            val ep = java.net.URLEncoder.encode(filePath ?: "", "UTF-8")
            val eu = java.net.URLEncoder.encode(uri ?: "", "UTF-8")
            val et = java.net.URLEncoder.encode(title ?: "", "UTF-8")
            return "word_reader?filePath=$ep&uri=$eu&title=$et"
        }
    }
    object ExcelReader : Screen("excel_reader?filePath={filePath}&uri={uri}&title={title}") {
        fun createRoute(filePath: String? = null, uri: String? = null, title: String? = null): String {
            val ep = java.net.URLEncoder.encode(filePath ?: "", "UTF-8")
            val eu = java.net.URLEncoder.encode(uri ?: "", "UTF-8")
            val et = java.net.URLEncoder.encode(title ?: "", "UTF-8")
            return "excel_reader?filePath=$ep&uri=$eu&title=$et"
        }
    }
    object AIChat : Screen("ai_chat?docId={docId}") {
        fun createRoute(docId: Long? = null): String = if (docId != null) "ai_chat?docId=$docId" else "ai_chat"
    }
    object OpenCvCrop : Screen("opencv_crop?docId={docId}&pageIndex={pageIndex}&imageUri={imageUri}") {
        fun createRoute(docId: Long? = null, pageIndex: Int = 0, imageUri: String? = null): String {
            val d = docId?.toString() ?: ""
            val u = if (!imageUri.isNullOrBlank()) java.net.URLEncoder.encode(imageUri, "UTF-8") else ""
            return "opencv_crop?docId=$d&pageIndex=$pageIndex&imageUri=$u"
        }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: ScannerViewModel
) {
    val incomingRoute by ExternalFileRouter.incomingRoute.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isOpeningPdf by remember { mutableStateOf(false) }
    var openingPdfTitle by remember { mutableStateOf("") }
    var unsupportedInfo by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingRoute) {
        val route = incomingRoute ?: return@LaunchedEffect
        when (route) {
            is ExternalFileRouter.RouteDestination.OpenPdf -> {
                ExternalFileRouter.consumeRoute()
                isOpeningPdf = true
                openingPdfTitle = route.title
                coroutineScope.launch {
                    try {
                        val fileUri = if (route.cachedFilePath.isNotBlank()) {
                            Uri.fromFile(File(route.cachedFilePath))
                        } else {
                            route.uri
                        }
                        val importedDoc = viewModel.importDocumentFromDevice(listOf(fileUri), "EXTERNAL_PDF")
                        isOpeningPdf = false
                        if (importedDoc != null) {
                            navController.navigate(Screen.DocumentDetail.createRoute(importedDoc.id))
                        } else {
                            errorMessage = "Could not parse pages from \"${route.title}\". The PDF may be corrupted or encrypted."
                        }
                    } catch (e: Exception) {
                        isOpeningPdf = false
                        errorMessage = "Error opening PDF: ${e.localizedMessage}"
                    }
                }
            }
            is ExternalFileRouter.RouteDestination.OpenWord -> {
                ExternalFileRouter.consumeRoute()
                navController.navigate(
                    Screen.WordReader.createRoute(
                        filePath = route.cachedFilePath.ifBlank { null },
                        uri = route.uri.toString(),
                        title = route.title
                    )
                )
            }
            is ExternalFileRouter.RouteDestination.OpenExcel -> {
                ExternalFileRouter.consumeRoute()
                navController.navigate(
                    Screen.ExcelReader.createRoute(
                        filePath = route.cachedFilePath.ifBlank { null },
                        uri = route.uri.toString(),
                        title = route.title
                    )
                )
            }
            is ExternalFileRouter.RouteDestination.OpenImage -> {
                ExternalFileRouter.consumeRoute()
                isOpeningPdf = true
                openingPdfTitle = route.title
                coroutineScope.launch {
                    try {
                        val importedDoc = viewModel.importDocumentFromDevice(listOf(route.uri), "EXTERNAL_IMG")
                        isOpeningPdf = false
                        if (importedDoc != null) {
                            navController.navigate(Screen.DocumentDetail.createRoute(importedDoc.id))
                        }
                    } catch (e: Exception) {
                        isOpeningPdf = false
                        errorMessage = "Error opening image: ${e.localizedMessage}"
                    }
                }
            }
            is ExternalFileRouter.RouteDestination.Unsupported -> {
                ExternalFileRouter.consumeRoute()
                unsupportedInfo = Pair(route.fileName, route.mimeType)
            }
            is ExternalFileRouter.RouteDestination.Error -> {
                ExternalFileRouter.consumeRoute()
                errorMessage = route.message
            }
        }
    }

    if (isOpeningPdf) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "Opening PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Importing pages into Doc Scanner...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (unsupportedInfo != null) {
        AlertDialog(
            onDismissRequest = { unsupportedInfo = null },
            title = { Text("Unsupported File Format", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${unsupportedInfo?.first}\" cannot be opened by Doc Scanner.\n\nDoc Scanner supports:\n• PDF Documents (.pdf)\n• Word Documents (.docx)\n• Excel Spreadsheets (.xlsx, .csv)"
                )
            },
            confirmButton = {
                TextButton(onClick = { unsupportedInfo = null }) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Unable to Open File", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Home Screen
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                },
                onNavigateToNidSetup = {
                    navController.navigate(Screen.NidSetup.route)
                },
                onNavigateToCrop = {
                    navController.navigate(Screen.Crop.route)
                },
                onNavigateToDocumentPreview = {
                    navController.navigate(Screen.DocumentPreview.route)
                },
                onNavigateToDocumentDetail = { docId ->
                    // If this document has a previously exported Word/Excel file attached,
                    // reopen it with the app's own reader (like CamScanner) instead of the
                    // plain scanned-page detail view.
                    val doc = viewModel.documentsList.value.firstOrNull { it.id == docId }
                    when {
                        !doc?.wordFilePath.isNullOrBlank() -> {
                            navController.navigate(
                                Screen.WordReader.createRoute(filePath = doc?.wordFilePath, title = doc?.title)
                            )
                        }
                        !doc?.excelFilePath.isNullOrBlank() -> {
                            navController.navigate(
                                Screen.ExcelReader.createRoute(filePath = doc?.excelFilePath, title = doc?.title)
                            )
                        }
                        else -> {
                            navController.navigate(Screen.DocumentDetail.createRoute(docId))
                        }
                    }
                },
                onNavigateToMergeFiles = {
                    navController.navigate(Screen.MergeFiles.route)
                },
                onNavigateToSignPdf = {
                    navController.navigate(Screen.SignPdf.route)
                },
                onNavigateToCompressPdf = {
                    navController.navigate(Screen.CompressPdf.route)
                },
                onNavigateToReorderPages = {
                    navController.navigate(Screen.ReorderPages.route)
                },
                onNavigateToReorderDoc = { docId ->
                    navController.navigate(Screen.ReorderDocumentPages.createRoute(docId))
                },
                onNavigateToRotatePdf = {
                    navController.navigate(Screen.RotatePdf.route)
                },
                onNavigateToRotatePdfDoc = { docId ->
                    navController.navigate(Screen.RotatePdfDoc.createRoute(docId))
                },
                onNavigateToAddWatermark = {
                    navController.navigate(Screen.AddWatermark.route)
                },
                onNavigateToAddWatermarkDoc = { docId ->
                    navController.navigate(Screen.AddWatermarkDoc.createRoute(docId))
                },
                onNavigateToExtractPdfPages = {
                    navController.navigate(Screen.ExtractPdfPages.route)
                },
                onNavigateToExtractPdfPagesDoc = { docId ->
                    navController.navigate(Screen.ExtractPdfPagesDoc.createRoute(docId))
                },
                onNavigateToExtractText = {
                    navController.navigate(Screen.ExtractText.route)
                },
                onNavigateToExtractTextDoc = { docId ->
                    navController.navigate(Screen.ExtractTextDoc.createRoute(docId))
                },
                onNavigateToPassportPhoto = {
                    navController.navigate(Screen.PassportPhotoMaker.route)
                },
                onNavigateToToWord = {
                    navController.navigate(Screen.ToWord.route)
                },
                onNavigateToToWordDoc = { docId ->
                    navController.navigate(Screen.ToWordDoc.createRoute(docId))
                },
                onNavigateToToExcel = {
                    navController.navigate(Screen.ToExcel.route)
                },
                onNavigateToToExcelDoc = { docId ->
                    navController.navigate(Screen.ToExcelDoc.createRoute(docId))
                },
                onNavigateToPdfToImages = {
                    navController.navigate(Screen.PdfToImage.route)
                },
                onNavigateToPdfToImagesDoc = { docId ->
                    navController.navigate(Screen.PdfToImageDoc.createRoute(docId))
                },
                onNavigateToPdfToLongImage = {
                    navController.navigate(Screen.PdfToLongImage.route)
                },
                onNavigateToPdfToLongImageDoc = { docId ->
                    navController.navigate(Screen.PdfToLongImageDoc.createRoute(docId))
                },
                onNavigateToWordReader = { filePath ->
                    navController.navigate(Screen.WordReader.createRoute(filePath = filePath))
                },
                onNavigateToExcelReader = { filePath ->
                    navController.navigate(Screen.ExcelReader.createRoute(filePath = filePath))
                },
                onNavigateToImageResizer = {
                    navController.navigate(Screen.ImageResizer.route)
                },
                onNavigateToPhotoBackgroundRemover = {
                    navController.navigate(Screen.PhotoBackgroundRemover.route)
                },
                onNavigateToEditPdf = {
                    navController.navigate(Screen.EditPdf.route)
                },
                onNavigateToLock = { docId ->
                    navController.navigate(Screen.DocumentLock.createRoute(docId))
                },
                onNavigateToAiChat = { docId ->
                    navController.navigate(Screen.AIChat.createRoute(docId))
                },
                onNavigateToOpenCvCrop = {
                    navController.navigate(Screen.OpenCvCrop.createRoute())
                }
            )
        }

        // PDF Extract / Extract PDF Pages Root Selection Screen (State 1)
        composable(Screen.ExtractPdfPages.route) {
            ExtractPdfPagesScreen(
                viewModel = viewModel,
                initialDocId = null,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // PDF Extract / Extract PDF Pages for specific document (State 2 & 3)
        composable(
            route = Screen.ExtractPdfPagesDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            ExtractPdfPagesScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Extract Text & OCR Studio Screen (Root)
        composable(Screen.ExtractText.route) {
            ExtractTextScreen(
                viewModel = viewModel,
                initialDocId = null,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        // Extract Text & OCR Studio Screen for specific document
        composable(
            route = Screen.ExtractTextDoc.route,
            arguments = listOf(
                navArgument("docId") { type = NavType.LongType },
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            ExtractTextScreen(
                viewModel = viewModel,
                initialDocId = docId,
                initialTab = tab,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        // Add Watermark Root Selection Screen (State 1)
        composable(Screen.AddWatermark.route) {
            AddWatermarkScreen(
                viewModel = viewModel,
                initialDocId = null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Add Watermark Editor for a specific document (State 2 & 3)
        composable(
            route = Screen.AddWatermarkDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            AddWatermarkScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Reorder Pages Root Screen (Screen A: Document List & Device Import)
        composable(Screen.ReorderPages.route) {
            ReorderPagesScreen(
                viewModel = viewModel,
                initialDocId = null,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Reorder Pages for specific document (Screen B)
        composable(
            route = Screen.ReorderDocumentPages.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            ReorderPagesScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Rotate PDF Root Screen (Screen A: Document List & Device Import)
        composable(Screen.RotatePdf.route) {
            RotatePagesScreen(
                viewModel = viewModel,
                initialDocId = null,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Rotate PDF for specific document (Screen B)
        composable(
            route = Screen.RotatePdfDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            RotatePagesScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Compress PDF 3-Screen Workflow
        composable(
            route = Screen.CompressPdf.route,
            arguments = listOf(navArgument("docId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")?.toLongOrNull()
            CompressPdfScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId))
                }
            )
        }

        // Sign PDF File Selection Workflow (Screen 1)
        composable(Screen.SignPdf.route) {
            SignPdfSelectionScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSignEditor = { docId ->
                    navController.navigate(Screen.SignPdfEditor.createRoute(docId))
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        // Sign PDF Editor Workflow (Screens 2, 3, and 4)
        composable(
            route = Screen.SignPdfEditor.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            SignPdfEditorScreen(
                documentId = docId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSavedAndOpenDoc = { newDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(newDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Merge Files Dedicated Workflow (Screens 1, 2, and 3)
        composable(
            route = Screen.MergeFiles.route,
            arguments = listOf(navArgument("docId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")?.toLongOrNull()
            MergeFilesScreen(
                viewModel = viewModel,
                initialDocId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMergeCompleted = { newDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(newDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Reference-style NID Front/Back landing screen
        composable(Screen.NidSetup.route) {
            NidScanSetupScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
                onSave = {
                    viewModel.finalizeNidSetup {
                        navController.navigate(Screen.DocumentPreview.route) {
                            popUpTo(Screen.NidSetup.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Camera Screen (New Scan)
        composable(Screen.Camera.route) {
            CameraScanScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    if (viewModel.pendingSignAfterScan.value) {
                        viewModel.pendingSignAfterScan.value = false
                    }
                    if (viewModel.isNidSetupCapture.value) {
                        viewModel.cancelNidSetupSideCapture()
                    }
                    navController.popBackStack()
                },
                onNavigateToCrop = {
                    navController.navigate(Screen.Crop.route)
                },
                onNavigateToDocumentPreview = {
                    navController.navigate(Screen.DocumentPreview.route)
                },
                onNidSideCaptured = {
                    navController.popBackStack(Screen.NidSetup.route, inclusive = false)
                }
            )
        }

        // Camera Screen (Append to existing Document)
        composable(
            route = Screen.CameraAppend.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            CameraScanScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCrop = {
                    navController.navigate(Screen.CropAppend.createRoute(docId))
                },
                onNavigateToDocumentPreview = {
                    navController.navigate(Screen.DocumentPreview.route)
                }
            )
        }

        // Crop Screen (Reference Screenshot 1: 4-Corner / Edge Handles, Left, Right, Auto Crop, All, Confirm)
        composable(Screen.Crop.route) {
            CropScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConfirmCrop = {
                    if (viewModel.scanMode.value == ScanMode.ID_CARD) {
                        // NID card was opened from the Front/Back review screen. Return to the
                        // existing preview instead of pushing a second preview route (or exposing Home).
                        val returnedToPreview = navController.popBackStack(
                            Screen.DocumentPreview.route,
                            inclusive = false
                        )
                        if (!returnedToPreview) {
                            navController.navigate(Screen.DocumentPreview.route) {
                                launchSingleTop = true
                            }
                        }
                    } else if (viewModel.pendingSignAfterScan.value) {
                        // Sign tool -> Scan: save the scan, then continue directly to the Sign editor.
                        viewModel.saveScannedDocument { docId ->
                            viewModel.pendingSignAfterScan.value = false
                            navController.navigate(Screen.SignPdfEditor.createRoute(docId)) {
                                popUpTo(Screen.SignPdf.route) { inclusive = false }
                            }
                        }
                    } else {
                        // Normal scan flow: save and open document details.
                        viewModel.saveScannedDocument { docId ->
                            navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                },
                onRetake = { pageIndex ->
                    viewModel.currentCropPageIndex.value = pageIndex
                    navController.navigate(Screen.Camera.route)
                }
            )
        }

        // OpenCV Precision Crop & Corner Adjustment Screen
        composable(
            route = Screen.OpenCvCrop.route,
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("pageIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("imageUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val docIdStr = backStackEntry.arguments?.getString("docId")
            val docId = docIdStr?.toLongOrNull()
            val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
            val encodedUri = backStackEntry.arguments?.getString("imageUri")
            val imageUri = if (!encodedUri.isNullOrBlank()) {
                try {
                    java.net.URLDecoder.decode(encodedUri, "UTF-8")
                } catch (e: Exception) {
                    encodedUri
                }
            } else null

            OpenCvCropScreen(
                viewModel = viewModel,
                initialDocId = docId,
                initialPageIndex = pageIndex,
                initialImageUri = imageUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSavedDocument = { savedDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(savedDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Document Preview Screen (Reference Screenshot 2 & 3: White Document Sheet, Front & Back Cards, Filter, Watermark, Compare)
        composable(Screen.DocumentPreview.route) {
            DocumentPreviewScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCrop = { pageIndex ->
                    viewModel.currentCropPageIndex.value = pageIndex
                    navController.navigate(Screen.Crop.route)
                },
                onRetake = { pageIndex ->
                    viewModel.currentCropPageIndex.value = pageIndex
                    navController.navigate(Screen.Camera.route)
                },
                onSaved = { savedDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(savedDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Crop & Filter Screen (Append to existing document)
        composable(
            route = Screen.CropAppend.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            CropFilterScreen(
                viewModel = viewModel,
                targetDocumentId = docId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onFinishAndOpenDoc = { finishedDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(finishedDocId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // Document Details & Multi-page Management Screen
        composable(
            route = Screen.DocumentDetail.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            DocumentDetailScreen(
                documentId = docId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCameraForAppend = { targetDocId ->
                    navController.navigate(Screen.CameraAppend.createRoute(targetDocId))
                },
                onNavigateToCrop = {
                    navController.navigate(Screen.CropAppend.createRoute(docId))
                },
                onNavigateToDocumentEditText = { targetDocId ->
                    navController.navigate(Screen.DocumentEditText.createRoute(targetDocId))
                },
                onNavigateToSinglePageEditor = { targetPageId ->
                    navController.navigate(Screen.SinglePageEditor.createRoute(targetPageId))
                },
                onNavigateToSignPdf = { targetDocId ->
                    navController.navigate(Screen.SignPdfEditor.createRoute(targetDocId))
                },
                onNavigateToAddWatermark = { targetDocId ->
                    navController.navigate(Screen.AddWatermarkDoc.createRoute(targetDocId))
                },
                onNavigateToToWord = { targetDocId ->
                    navController.navigate(Screen.ToWordDoc.createRoute(targetDocId))
                },
                onNavigateToToExcel = { targetDocId ->
                    navController.navigate(Screen.ToExcelDoc.createRoute(targetDocId))
                },
                onNavigateToExtractText = { targetDocId ->
                    navController.navigate(Screen.ExtractTextDoc.createRoute(targetDocId, tab = 0))
                },
                onNavigateToExtractPages = { targetDocId ->
                    navController.navigate(Screen.ExtractPdfPagesDoc.createRoute(targetDocId))
                },
                onNavigateToTranslate = { targetDocId ->
                    navController.navigate(Screen.ExtractTextDoc.createRoute(targetDocId, tab = 2))
                },
                onNavigateToCompress = { targetDocId ->
                    navController.navigate(Screen.CompressPdf.createRoute(targetDocId))
                },
                onNavigateToLock = { targetDocId ->
                    navController.navigate(Screen.DocumentLock.createRoute(targetDocId))
                },
                onNavigateToMerge = { targetDocId ->
                    navController.navigate(Screen.MergeFiles.createRoute(targetDocId))
                },
                onNavigateToManagePages = { targetDocId ->
                    navController.navigate(Screen.ReorderDocumentPages.createRoute(targetDocId))
                },
                onNavigateToPdfToImages = { targetDocId ->
                    navController.navigate(Screen.PdfToImageDoc.createRoute(targetDocId))
                },
                onNavigateToAiChat = { targetDocId ->
                    navController.navigate(Screen.AIChat.createRoute(targetDocId))
                },
                onNavigateToPdfToLongImage = { targetDocId ->
                    navController.navigate(Screen.PdfToLongImageDoc.createRoute(targetDocId))
                }
            )
        }

        // Dedicated full-page, multi-page Edit Text document editor
        composable(
            route = Screen.DocumentEditText.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val editDocumentId = backStackEntry.arguments?.getLong("docId") ?: 0L
            DocumentEditTextScreen(
                documentId = editDocumentId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Dedicated Single Page Editor Screen
        composable(
            route = Screen.SinglePageEditor.route,
            arguments = listOf(
                navArgument("pageId") { type = NavType.LongType },
                navArgument("tool") { type = NavType.StringType; defaultValue = "none" }
            )
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getLong("pageId") ?: 0L
            val toolName = backStackEntry.arguments?.getString("tool") ?: "none"
            val initialTool = when (toolName) {
                "edit_text" -> ActiveEditorTool.EDIT_TEXT
                "smart_erase" -> ActiveEditorTool.SMART_ERASE
                else -> ActiveEditorTool.NONE
            }
            SinglePageEditorScreen(
                pageId = pageId,
                viewModel = viewModel,
                initialTool = initialTool,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDocumentEditText = { docId ->
                    navController.navigate(Screen.DocumentEditText.createRoute(docId))
                }
            )
        }

        // Professional Edit PDF workspace
        composable(Screen.EditPdf.route) {
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            EditPdfScreen(
                viewModel = viewModel,
                allDocuments = documents,
                onBack = { navController.popBackStack() },
                onEditTextAndFont = { targetDocId ->
                    navController.navigate(Screen.DocumentEditText.createRoute(targetDocId))
                },
                onSign = { docId ->
                    navController.navigate(Screen.SignPdfEditor.createRoute(docId))
                },
                onWatermark = { docId ->
                    navController.navigate(Screen.AddWatermarkDoc.createRoute(docId))
                },
                onReorder = { docId ->
                    navController.navigate(Screen.ReorderDocumentPages.createRoute(docId))
                },
                onRotate = { docId ->
                    navController.navigate(Screen.RotatePdfDoc.createRoute(docId))
                },
                onExtractPages = { docId ->
                    navController.navigate(Screen.ExtractPdfPagesDoc.createRoute(docId))
                },
                onAiChat = { docId ->
                    navController.navigate(Screen.AIChat.createRoute(docId))
                }
            )
        }

        // Modern 300 DPI Studio Passport Photo Maker Screen
        composable(Screen.PassportPhotoMaker.route) {
            PassportPhotoMakerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // To Word Dedicated Workflow (Tools -> To Word)
        composable(Screen.ToWord.route) {
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            PdfToWordScreen(
                viewModel = viewModel,
                initialDocument = null,
                allDocuments = documents,
                onNavigateToScan = {
                    navController.navigate(Screen.Camera.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // To Word Dedicated Workflow for specific Document
        composable(
            route = Screen.ToWordDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            var initialDoc by remember(docId) { mutableStateOf<com.docscan.data.model.DocumentEntity?>(null) }
            LaunchedEffect(docId) {
                initialDoc = viewModel.getDocumentDirect(docId)
            }
            PdfToWordScreen(
                viewModel = viewModel,
                initialDocument = initialDoc,
                allDocuments = documents,
                onNavigateToScan = {
                    navController.navigate(Screen.Camera.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // To Excel Dedicated Workflow (State 1: Selection & Device Import)
        composable(Screen.ToExcel.route) {
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            PdfToExcelScreen(
                viewModel = viewModel,
                initialDocument = null,
                allDocuments = documents,
                onNavigateToScan = {
                    navController.navigate(Screen.Camera.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // To Excel Dedicated Workflow for specific Document
        composable(
            route = Screen.ToExcelDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            val initialDoc = documents.firstOrNull { it.id == docId }
            PdfToExcelScreen(
                viewModel = viewModel,
                initialDocument = initialDoc,
                allDocuments = documents,
                onNavigateToScan = {
                    navController.navigate(Screen.Camera.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // PDF to Images Dedicated Workflow (State 1: Selection & Device Import)
        composable(Screen.PdfToImage.route) {
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            PdfToImageScreen(
                viewModel = viewModel,
                initialDocument = null,
                allDocuments = documents,
                onNavigateToDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // PDF to Images Dedicated Workflow for specific Document
        composable(
            route = Screen.PdfToImageDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            val initialDoc = documents.firstOrNull { it.id == docId }
            PdfToImageScreen(
                viewModel = viewModel,
                initialDocument = initialDoc,
                allDocuments = documents,
                onNavigateToDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // PDF to Long Image Dedicated Workflow (State 1: Selection & Device Import)
        composable(Screen.PdfToLongImage.route) {
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            PdfToLongImageScreen(
                viewModel = viewModel,
                initialDocument = null,
                allDocuments = documents,
                onNavigateToDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // PDF to Long Image Dedicated Workflow for specific Document
        composable(
            route = Screen.PdfToLongImageDoc.route,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
            val documents by viewModel.documentsList.collectAsStateWithLifecycle()
            val initialDoc = documents.firstOrNull { it.id == docId }
            PdfToLongImageScreen(
                viewModel = viewModel,
                initialDocument = initialDoc,
                allDocuments = documents,
                onNavigateToDocumentDetail = { targetDocId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(targetDocId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Advanced Word Reader Screen
        composable(
            route = Screen.WordReader.route,
            arguments = listOf(
                navArgument("filePath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawPath = backStackEntry.arguments?.getString("filePath")
            val rawUri = backStackEntry.arguments?.getString("uri")
            val rawTitle = backStackEntry.arguments?.getString("title")

            val filePath = if (!rawPath.isNullOrBlank()) java.net.URLDecoder.decode(rawPath, "UTF-8") else null
            val uri = if (!rawUri.isNullOrBlank()) java.net.URLDecoder.decode(rawUri, "UTF-8") else null
            val title = if (!rawTitle.isNullOrBlank()) java.net.URLDecoder.decode(rawTitle, "UTF-8") else null

            WordReaderScreen(
                initialFilePath = filePath,
                initialUriString = uri,
                initialTitle = title,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Advanced Excel Reader Screen
        composable(
            route = Screen.ExcelReader.route,
            arguments = listOf(
                navArgument("filePath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawPath = backStackEntry.arguments?.getString("filePath")
            val rawUri = backStackEntry.arguments?.getString("uri")
            val rawTitle = backStackEntry.arguments?.getString("title")

            val filePath = if (!rawPath.isNullOrBlank()) java.net.URLDecoder.decode(rawPath, "UTF-8") else null
            val uri = if (!rawUri.isNullOrBlank()) java.net.URLDecoder.decode(rawUri, "UTF-8") else null
            val title = if (!rawTitle.isNullOrBlank()) java.net.URLDecoder.decode(rawTitle, "UTF-8") else null

            ExcelReaderScreen(
                initialFilePath = filePath,
                initialUriString = uri,
                initialTitle = title,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Image Resizer Studio Screen
        composable(Screen.ImageResizer.route) {
            ImageResizerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Photo Background Remover Screen (AI & Local Engine)
        composable(Screen.PhotoBackgroundRemover.route) {
            PhotoBackgroundRemoverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Document Lock Screen (Tools -> Lock)
        composable(
            route = Screen.DocumentLock.route,
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val docIdArg = backStackEntry.arguments?.getLong("docId") ?: -1L
            val initialDocId = if (docIdArg > 0L) docIdArg else null
            DocumentLockScreen(
                viewModel = viewModel,
                initialDocId = initialDocId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDocumentDetail = { docId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // AI Chat Assistant (Local On-Device & Cloud AI)
        composable(
            route = Screen.AIChat.route,
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val docIdStr = backStackEntry.arguments?.getString("docId")
            val docId = docIdStr?.toLongOrNull()
            val aiChatViewModel: AIChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            AIChatScreen(
                viewModel = aiChatViewModel,
                initialDocId = docId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
