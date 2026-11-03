package com.docscan.ui.components

import com.docscan.util.ThemeManager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.docscan.security.DocumentLockManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.docscan.util.DocxExporter
import com.docscan.util.ExcelExporter
import com.docscan.util.GeminiAiService
import com.docscan.util.FileUtils
import com.docscan.data.model.DocumentEntity
import com.docscan.data.model.PageEntity
import com.docscan.util.PdfExportConfig
import java.util.Locale

private val DarkDialogBg: Color get() = ThemeManager.themeColor(Color(0xFF242426), Color(0xFFFFFFFF))
private val DarkCardBg: Color get() = ThemeManager.themeColor(Color(0xFF2F2F33), Color(0xFFF1F5F9))
private val TealAccent = Color(0xFF00BFA5)
private val DarkTextColor: Color get() = ThemeManager.themeColor(Color(0xFFE2E8F0), Color(0xFF0F172A))
private val DarkTextSecondary: Color get() = ThemeManager.themeColor(Color(0xFF9E9E9E), Color(0xFF475569))

// ==================== TAGS MANAGER DIALOG ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsManagerDialog(
    initialTags: String,
    onDismiss: () -> Unit,
    onSaveTags: (String) -> Unit
) {
    var tagsList by remember {
        mutableStateOf(
            if (initialTags.isBlank()) emptyList<String>()
            else initialTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
    var newTagInput by remember { mutableStateOf("") }
    val presetSuggestions = listOf("Invoice", "ID Card", "Receipt", "Contract", "Tax", "Personal", "Work", "Medical")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Manage Document Tags",
                        color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input new tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("Add custom tag...", color = DarkTextSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFF454545)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newTagInput.trim()
                            if (trimmed.isNotEmpty() && !tagsList.contains(trimmed)) {
                                tagsList = tagsList + trimmed
                                newTagInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Selected Tags:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                if (tagsList.isEmpty()) {
                    Text("No tags added yet", color = Color(0xFF6B7280), fontSize = 12.sp)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tagsList.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = TealAccent.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(tag, color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = TealAccent,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                tagsList = tagsList.filter { it != tag }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Suggested Tags:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetSuggestions.forEach { suggestion ->
                        val isSelected = tagsList.contains(suggestion)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) TealAccent.copy(alpha = 0.3f) else DarkCardBg,
                            modifier = Modifier.clickable {
                                tagsList = if (isSelected) tagsList.filter { it != suggestion }
                                else tagsList + suggestion
                            }
                        ) {
                            Text(
                                "+ $suggestion",
                                color = if (isSelected) TealAccent else DarkTextColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveTags(tagsList.joinToString(", "))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Tags", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== SEND TO PC DIALOG ====================
@Composable
fun SendToPcDialog(
    docTitle: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val transferUrl = "http://192.168.1.108:8080/doc/${docTitle.replace(" ", "_")}.pdf"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Send to PC", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Transfer QR Code",
                        tint = Color.Black,
                        modifier = Modifier.size(110.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Connect PC and Phone to same Wi-Fi",
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Open the link below in your PC browser to instantly download:",
                    color = DarkTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            transferUrl,
                            color = TealAccent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PC Link", transferUrl))
                                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Download Document on PC: $docTitle")
                            putExtra(Intent.EXTRA_TEXT, "Download '$docTitle' on your PC via local Wi-Fi: $transferUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PC Link"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Web Link", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== TRANSLATE DIALOG ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateDialog(
    extractedText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val languages = listOf("Bengali", "English", "Spanish", "Hindi", "Arabic", "French", "German", "Japanese", "Chinese", "Russian")
    var selectedLang by remember { mutableStateOf("Bengali") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }

    LaunchedEffect(extractedText, selectedLang) {
        if (extractedText.isNotBlank()) {
            isTranslating = true
            val result = GeminiAiService.translateTextAi(extractedText, selectedLang)
            translatedText = result
            isTranslating = false
        } else {
            translatedText = "No document text available to translate."
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = TealAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("✨ AI Smart Translate", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Target Language:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedLang == lang) TealAccent.copy(alpha = 0.25f) else DarkCardBg,
                            border = if (selectedLang == lang) androidx.compose.foundation.BorderStroke(1.dp, TealAccent) else null,
                            modifier = Modifier.clickable { selectedLang = lang }
                        ) {
                            Text(
                                lang,
                                color = if (selectedLang == lang) TealAccent else DarkTextColor,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    if (isTranslating) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("AI Translating into $selectedLang...", color = DarkTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                translatedText,
                                color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Translated Text", translatedText))
                            Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Translated Document ($selectedLang)")
                                putExtra(Intent.EXTRA_TEXT, translatedText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Translation"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==================== AI TO WORD DIALOG ====================
@Composable
fun AiToWordDialog(
    docTitle: String,
    page: PageEntity,
    onDismiss: () -> Unit,
    onExportDocx: () -> Unit
) {
    val context = LocalContext.current
    var formattedDocText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(page) {
        isProcessing = true
        val bmp = FileUtils.loadBitmap(page.processedImagePath) ?: FileUtils.loadBitmap(page.originalImagePath)
        formattedDocText = if (bmp != null) {
            GeminiAiService.convertToWordAi(bmp, page.extractedText, context = context, accuracyFirst = true)
        } else {
            page.extractedText ?: ""
        }
        isProcessing = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("✨ AI Convert to Word", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("AI structured Microsoft Word layout formatted with headings, bullet points, and tables:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    if (isProcessing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Gemini accuracy pass 1/2 + visual verification...", color = DarkTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                            Text(formattedDocText, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Word Content", formattedDocText))
                            Toast.makeText(context, "Copied formatted Word text", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Text", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onExportDocx()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export .DOCX", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==================== AI TO EXCEL DIALOG ====================
@Composable
fun AiToExcelDialog(
    docTitle: String,
    page: PageEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var csvText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(page) {
        isProcessing = true
        val bmp = FileUtils.loadBitmap(page.processedImagePath) ?: FileUtils.loadBitmap(page.originalImagePath)
        csvText = if (bmp != null) {
            GeminiAiService.convertToExcelAi(bmp, page.extractedText, context = context, accuracyFirst = true)
        } else {
            val text = page.extractedText ?: ""
            "Index,Field,Value\n1,Text,\"${text.replace("\"", "\"\"")}\""
        }
        isProcessing = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("✨ AI Convert to Excel", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Gemini Vision reconstructs and verifies tables before export:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    if (isProcessing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF34D399), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("AI Extracting Table Data...", color = DarkTextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                csvText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Excel CSV Data", csvText))
                            Toast.makeText(context, "Copied CSV table to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                            coroutineScope.launch {
                                val xlsxFile = ExcelExporter.generateXlsxFromCsv(context, docTitle, csvText)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (xlsxFile != null) {
                                        ExcelExporter.shareXlsx(context, xlsxFile)
                                    } else {
                                        Toast.makeText(context, "Failed to create .xlsx file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export .XLSX", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


// ==================== COMPRESS DIALOG ====================
@Composable
fun CompressDialog(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onCompressAndShare: (PdfExportConfig) -> Unit
) {
    var compressionLevel by remember { mutableIntStateOf(1) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Compress, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compress Document", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val options = listOf(
                    Triple("Standard Quality", "~30% reduction (Best visual clarity)", 85),
                    Triple("Recommended", "~60% reduction (Balanced size & detail)", 60),
                    Triple("Maximum Compression", "~80% reduction (Smallest file size)", 35)
                )

                options.forEachIndexed { index, (title, desc, _) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (compressionLevel == index) TealAccent.copy(alpha = 0.15f) else DarkCardBg,
                        border = if (compressionLevel == index) androidx.compose.foundation.BorderStroke(1.5.dp, TealAccent) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { compressionLevel = index }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = compressionLevel == index,
                                onClick = { compressionLevel = index },
                                colors = RadioButtonDefaults.colors(selectedColor = TealAccent, unselectedColor = DarkTextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(title, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(desc, color = DarkTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val quality = options[compressionLevel].third
                        val config = PdfExportConfig(
                            title = "${doc.title}_compressed",
                            quality = quality
                        )
                        onCompressAndShare(config)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Compress & Export PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== READ MODE DIALOG ====================
@Composable
fun ReadModeDialog(
    docTitle: String,
    extractedText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fontSize by remember { mutableFloatStateOf(16f) }
    var colorTheme by remember { mutableIntStateOf(0) }
    var isSpeaking by remember { mutableStateOf(false) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val (bgColor, textColor) = when (colorTheme) {
        1 -> Pair(Color(0xFFFBF0D9), Color(0xFF3E2723))
        2 -> Pair(Color(0xFFFFFFFF), Color(0xFF1E293B))
        else -> Pair(Color(0xFF18181B), Color(0xFFF1F5F9))
    }

    Dialog(
        onDismissRequest = {
            tts?.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        docTitle,
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isSpeaking) {
                                    tts?.stop()
                                    isSpeaking = false
                                } else {
                                    if (extractedText.isNotBlank()) {
                                        tts?.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, "read_aloud")
                                        isSpeaking = true
                                    } else {
                                        Toast.makeText(context, "No text to read", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Read Aloud",
                                tint = TealAccent
                            )
                        }

                        IconButton(onClick = {
                            tts?.stop()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Dark", "Sepia", "Light").forEachIndexed { idx, name ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (colorTheme == idx) TealAccent else Color(0x33888888),
                                modifier = Modifier.clickable { colorTheme = idx }
                            ) {
                                Text(
                                    name,
                                    color = if (colorTheme == idx) Color.Black else textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A-", color = textColor, fontSize = 12.sp, modifier = Modifier.clickable {
                            if (fontSize > 12f) fontSize -= 2f
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("A+", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            if (fontSize < 28f) fontSize += 2f
                        })
                    }
                }

                HorizontalDivider(color = Color(0x22888888), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (extractedText.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No OCR text detected on this document yet.", color = textColor.copy(alpha = 0.6f))
                        }
                    } else {
                        Text(
                            text = extractedText,
                            color = textColor,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5).sp
                        )
                    }
                }
            }
        }
    }
}

// ==================== CASE SUMMARY DIALOG ====================
@Composable
fun CaseSummaryDialog(
    docTitle: String,
    extractedText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val summaryContent = remember(extractedText) {
        if (extractedText.isBlank()) {
            "No text content found to summarize. Please ensure document pages contain visible text."
        } else {
            val lines = extractedText.lines().filter { it.isNotBlank() }
            val words = extractedText.split("\\s+".toRegex()).size

            buildString {
                appendLine("📄 EXECUTIVE SUMMARY:")
                appendLine("Document contains approximately $words words across detected sections.")
                appendLine()
                appendLine("🔑 KEY HIGHLIGHTS:")
                lines.take(4).forEach { line ->
                    appendLine("• ${line.trim()}")
                }
                appendLine()
                appendLine("📊 DETECTED DATA POINTS:")
                val numbersAndDates = Regex("\\b(\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}|\\$\\d+|\\d+[,.]\\d+)\\b").findAll(extractedText).map { it.value }.take(5).toList()
                if (numbersAndDates.isNotEmpty()) {
                    appendLine("• Key Figures/Dates: ${numbersAndDates.joinToString(", ")}")
                } else {
                    appendLine("• General text document structure.")
                }
                appendLine()
                appendLine("✅ STATUS: Document reviewed and indexed on-device.")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Case Summary", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            summaryContent,
                            color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Case Summary", summaryContent))
                            Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Summary: $docTitle")
                                putExtra(Intent.EXTRA_TEXT, summaryContent)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Summary"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==================== MERGE FILES DIALOG ====================
@Composable
fun MergeFilesDialog(
    currentDocId: Long,
    allDocuments: List<DocumentEntity>,
    onDismiss: () -> Unit,
    onMergeWith: (Long) -> Unit
) {
    val candidates = allDocuments.filter { it.id != currentDocId }
    var selectedMergeId by remember { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Merge Documents", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Select a document to append its pages into this one:", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                if (candidates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No other documents found to merge.", color = DarkTextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(candidates) { docItem ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedMergeId == docItem.id) TealAccent.copy(alpha = 0.2f) else DarkCardBg,
                                border = if (selectedMergeId == docItem.id) androidx.compose.foundation.BorderStroke(1.dp, TealAccent) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedMergeId = docItem.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(docItem.title, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${docItem.pageCount} page(s)", color = DarkTextSecondary, fontSize = 11.sp)
                                    }
                                    if (selectedMergeId == docItem.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = TealAccent)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        selectedMergeId?.let { onMergeWith(it) }
                        onDismiss()
                    },
                    enabled = selectedMergeId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Merge Selected Document", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== MOVE FOLDER DIALOG ====================
@Composable
fun MoveFolderDialog(
    currentFolder: String,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    val folders = listOf("All", "Business", "Personal", "Receipts", "Tax", "ID Cards")
    var selectedFolder by remember { mutableStateOf(currentFolder) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Move to Folder", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                folders.forEach { folder ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedFolder == folder) TealAccent.copy(alpha = 0.2f) else DarkCardBg,
                        border = if (selectedFolder == folder) androidx.compose.foundation.BorderStroke(1.dp, TealAccent) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedFolder = folder }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = if (selectedFolder == folder) TealAccent else DarkTextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder, color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (selectedFolder == folder) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = TealAccent)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onMove(selectedFolder)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm Move", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== LOCK DOCUMENT DIALOG ====================
@Composable
fun LockDocumentDialog(
    docTitle: String,
    docId: Long? = null,
    onDismiss: () -> Unit,
    onLockChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLocked = remember(docId, context) {
        if (docId != null) DocumentLockManager.isDocumentLocked(context, docId) else false
    }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isLocked) "Document Protection" else "Lock Document",
                    color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isLocked)
                        "This document is password protected. Verify the current password to change it or remove protection."
                    else
                        "Encrypt $docTitle with a password to prevent unauthorized viewing or sharing.",
                    color = DarkTextSecondary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLocked) {
                    // Unlock / Remove Protection option
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = {
                            oldPassword = it
                            errorMessage = null
                        },
                        placeholder = { Text("Current password", color = DarkTextSecondary, fontSize = 13.sp) },
                        visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = DarkTextSecondary
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFF454545)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showChangePassword) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            placeholder = { Text("New password (min 6 chars)", color = DarkTextSecondary, fontSize = 13.sp) },
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = Color(0xFF454545)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMessage = null },
                            placeholder = { Text("Confirm new password", color = DarkTextSecondary, fontSize = 13.sp) },
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                                focusedBorderColor = TealAccent,
                                unfocusedBorderColor = Color(0xFF454545)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (showChangePassword) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showChangePassword = false; errorMessage = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3240)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (docId == null) return@Button
                                    if (!DocumentLockManager.verifyPassword(context, docId, oldPassword)) {
                                        errorMessage = "Incorrect current password"
                                        return@Button
                                    }
                                    if (password.trim().length < 6) {
                                        errorMessage = "New password must be at least 6 characters"
                                        return@Button
                                    }
                                    if (password.trim() != confirmPassword.trim()) {
                                        errorMessage = "New passwords do not match"
                                        return@Button
                                    }
                                    val success = DocumentLockManager.lockDocument(context, docId, password)
                                    if (success) {
                                        Toast.makeText(context, "Password updated. Document locked.", Toast.LENGTH_SHORT).show()
                                        onLockChanged()
                                        onDismiss()
                                    } else {
                                        errorMessage = "Could not update password"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text("Update Password", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3240)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (docId != null) {
                                        val success = DocumentLockManager.removePassword(context, docId, oldPassword)
                                        if (success) {
                                            Toast.makeText(context, "Password protection removed", Toast.LENGTH_SHORT).show()
                                            onLockChanged()
                                            onDismiss()
                                        } else {
                                            errorMessage = "Incorrect current password"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.25f)
                            ) {
                                Text("Remove Lock", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                if (oldPassword.isBlank()) {
                                    errorMessage = "Enter the current password first"
                                } else if (!DocumentLockManager.verifyPassword(context, docId ?: return@OutlinedButton, oldPassword)) {
                                    errorMessage = "Incorrect current password"
                                } else {
                                    showChangePassword = true
                                    errorMessage = null
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change Password", color = TealAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Set New Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        placeholder = { Text("Enter password (min 6 chars)", color = DarkTextSecondary, fontSize = 13.sp) },
                        visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = DarkTextSecondary
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFF454545)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        placeholder = { Text("Confirm password", color = DarkTextSecondary, fontSize = 13.sp) },
                        visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFF454545)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3240)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (password.trim().length < 6) {
                                    errorMessage = "Password must be at least 6 characters"
                                    return@Button
                                }
                                if (password.trim() != confirmPassword.trim()) {
                                    errorMessage = "Passwords do not match"
                                    return@Button
                                }

                                if (docId != null) {
                                    val success = DocumentLockManager.lockDocument(context, docId, password)
                                    if (success) {
                                        Toast.makeText(context, "Document locked successfully", Toast.LENGTH_SHORT).show()
                                        onLockChanged()
                                        onDismiss()
                                    } else {
                                        errorMessage = "Lock encryption failed. Try again."
                                    }
                                } else {
                                    Toast.makeText(context, "Document locked", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lock", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Word (.docx) Export Dialog
 */
@Composable
fun WordExportDialog(
    docTitle: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onShareDocx: (DocxExporter.DocxExportConfig) -> Unit,
    onOpenDocx: (DocxExporter.DocxExportConfig) -> Unit
) {
    var title by remember { mutableStateOf(docTitle) }
    var includeOcrText by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text("Export to Word", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("$pageCount ${if (pageCount == 1) "page" else "pages"} • Microsoft Word (.docx)", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Name
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Word File Name", color = DarkTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        unfocusedTextColor = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)),
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFF454545)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Content Options
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { includeOcrText = !includeOcrText }
                        .padding(vertical = 4.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = includeOcrText,
                        onCheckedChange = { includeOcrText = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Include OCR Extracted Text", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { includeImages = !includeImages }
                        .padding(vertical = 4.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = includeImages,
                        onCheckedChange = { includeImages = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Include Page Scan Images", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val config = DocxExporter.DocxExportConfig(
                                title = title.ifBlank { docTitle },
                                includeFormattedText = includeOcrText,
                                embedPageImages = includeImages
                            )
                            onOpenDocx(config)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open File", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val config = DocxExporter.DocxExportConfig(
                                title = title.ifBlank { docTitle },
                                includeFormattedText = includeOcrText,
                                embedPageImages = includeImages
                            )
                            onShareDocx(config)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share DOCX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Modern Excel (.xlsx) Export Dialog
 */
@Composable
fun ExcelExportDialog(
    docTitle: String,
    onDismiss: () -> Unit,
    onShareExcel: () -> Unit,
    onOpenExcel: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF15803D),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text("Export to Excel", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Spreadsheet (.xlsx / CSV)", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Convert detected tables and OCR text from \"$docTitle\" into a structured Excel spreadsheet.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onOpenExcel()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open File", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onShareExcel()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share XLSX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Modern PDF to Images Export Dialog
 */
@Composable
fun PdfToImagesDialog(
    docTitle: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onShareImages: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEA580C),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text("PDF to Images", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("$pageCount ${if (pageCount == 1) "image" else "images"} from \"$docTitle\"", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose whether to share the high-resolution scanned page images or save them to your device Gallery.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveToGallery()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save to Gallery", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onShareImages()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Images", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog before saving document images to gallery
 */
@Composable
fun SaveToGalleryConfirmDialog(
    docTitle: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirmSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkDialogBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD97706),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text("Save to Gallery", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Pictures/DocScanner", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Do you want to save all $pageCount scanned page image(s) from \"$docTitle\" to your device Photos / Gallery?",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = ThemeManager.themeColor(Color.White, Color(0xFF0F172A)), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onConfirmSave()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

