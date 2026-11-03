package com.docscan.ui.components.textedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.util.AiOrchestrator
import com.docscan.util.textedit.BengaliTextSupport
import kotlinx.coroutines.launch

/**
 * Full-screen, touch-friendly editor for OCR text inside Edit PDF.
 *
 * The editor intentionally stays lightweight: it reuses the existing OCR/style
 * estimator and in-place renderer, so no additional heavy PDF dependency is needed.
 */
@Composable
fun InPlaceTextEditorDialog(
    initialText: String,
    initialTextColor: Color,
    initialFontSizeSp: Float,
    initialIsBold: Boolean,
    initialIsItalic: Boolean,
    initialIsUnderline: Boolean,
    initialFontFamily: String,
    initialAlignment: String = "LEFT",
    isPreviouslyEdited: Boolean = false,
    onDismiss: () -> Unit,
    onApplyEdit: (
        newText: String,
        textColor: Color,
        fontSizeSp: Float,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        fontFamily: String,
        alignment: String
    ) -> Unit,
    onEraseFromDocument: () -> Unit,
    onResetToOriginal: (() -> Unit)? = null
) {
    var editedText by remember(initialText) { mutableStateOf(initialText) }
    var selectedColor by remember(initialTextColor) { mutableStateOf(initialTextColor) }
    var fontSize by remember(initialFontSizeSp) {
        mutableFloatStateOf(initialFontSizeSp.coerceIn(6f, 72f))
    }
    var isBold by remember(initialIsBold) { mutableStateOf(initialIsBold) }
    var isItalic by remember(initialIsItalic) { mutableStateOf(initialIsItalic) }
    var isUnderline by remember(initialIsUnderline) { mutableStateOf(initialIsUnderline) }
    var fontFamilyType by remember(initialFontFamily, initialText) {
        mutableStateOf(BengaliTextSupport.normalizedFamily(initialText, initialFontFamily))
    }
    var alignment by remember(initialAlignment) { mutableStateOf(initialAlignment.uppercase()) }
    var isAiProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val accent = Color(0xFF23D5A4)
    val background = Color(0xFF0D0F12)
    val panel = Color(0xFF171A1F)
    val panelStrong = Color(0xFF1D2127)
    val border = Color(0xFF343A43)
    val primaryText = Color(0xFFF4F7FB)
    val secondaryText = Color(0xFF98A2B3)
    val danger = Color(0xFFFF5C68)

    val presetColors = remember(initialTextColor) {
        listOf(
            initialTextColor,
            Color.Black,
            Color(0xFF6B7280),
            Color.White,
            Color(0xFFEF4444),
            Color(0xFF2563EB),
            Color(0xFF10B981),
            Color(0xFFF59E0B),
            Color(0xFF7C3AED)
        ).distinct()
    }

    val composeFontFamily = when (fontFamilyType) {
        "SERIF" -> FontFamily.Serif
        "MONO" -> FontFamily.Monospace
        "BENGALI", "BANGLA" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
    val composeTextAlign = when (alignment) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.Right
        else -> TextAlign.Left
    }

    fun apply() {
        if (editedText.isBlank()) return
        onApplyEdit(
            editedText,
            selectedColor,
            fontSize,
            isBold,
            isItalic,
            isUnderline,
            fontFamilyType,
            alignment
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("screen_inplace_text_editor")
        ) {
            // Top app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryText
                        )
                    }
                    Column {
                        Text(
                            "Edit Text",
                            color = primaryText,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Text and font replacement",
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = accent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .clickable(enabled = editedText.isNotBlank()) { apply() }
                        .testTag("btn_apply_edit_text_top")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF23272E))
            )

            // Scrollable editor body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Auto font match summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0F2A25),
                    border = BorderStroke(1.dp, Color(0xFF1E8069)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accent.copy(alpha = 0.10f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Aa", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Auto Font Match", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                "${prettyFamily(fontFamilyType)} • ${formatSize(initialFontSizeSp)} sp • auto-fit to original text box",
                                color = Color(0xFFB6C4D3),
                                fontSize = 11.5.sp
                            )
                        }
                        if (isPreviouslyEdited && onResetToOriginal != null) {
                            IconButton(onClick = onResetToOriginal) {
                                Icon(Icons.Default.RestartAlt, "Restore original", tint = Color(0xFFF5B942))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Text field
                SectionTitle("Text Content", secondaryText)
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = editedText,
                    onValueChange = {
                        editedText = it
                        if (BengaliTextSupport.containsBangla(it) && fontFamilyType != "BENGALI") {
                            fontFamilyType = "BENGALI"
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = selectedColor,
                        fontSize = fontSize.coerceIn(10f, 28f).sp,
                        fontFamily = composeFontFamily,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                        textAlign = composeTextAlign
                    ),
                    trailingIcon = {
                        if (editedText.isNotEmpty()) {
                            IconButton(onClick = { editedText = "" }) {
                                Icon(Icons.Default.Clear, "Clear", tint = secondaryText)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        cursorColor = accent,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = border,
                        focusedContainerColor = panel,
                        unfocusedContainerColor = panel
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 92.dp, max = 150.dp)
                        .testTag("input_edit_text_content")
                )

                Spacer(Modifier.height(18.dp))

                // AI tools
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    SectionTitle("AI Quick Actions", primaryText)
                }
                Spacer(Modifier.height(8.dp))
                if (isAiProcessing) {
                    Surface(color = panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            Text("AI is improving the selected text…", color = secondaryText, fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiActionChip("Fix Typo", accent, panelStrong, border) {
                            runAiAction(context, coroutineScope, editedText, "fix all spelling and OCR typos precisely", { isAiProcessing = it }) { editedText = it }
                        }
                        AiActionChip("Reformat", accent, panelStrong, border) {
                            runAiAction(context, coroutineScope, editedText, "clean up formatting and spacing", { isAiProcessing = it }) { editedText = it }
                        }
                        AiActionChip("বাংলা শুদ্ধ করুন", accent, panelStrong, border) {
                            runAiAction(
                                context,
                                coroutineScope,
                                editedText,
                                "Correct Bengali spelling, punctuation and OCR mistakes. Preserve names, numbers, dates and meaning. Return only the corrected text.",
                                { isAiProcessing = it }
                            ) { editedText = it }
                        }
                        AiActionChip("বাংলায় অনুবাদ", accent, panelStrong, border) {
                            runAiAction(context, coroutineScope, editedText, "translate to Bengali", { isAiProcessing = it }) { editedText = it }
                        }
                        AiActionChip("To English", accent, panelStrong, border) {
                            runAiAction(context, coroutineScope, editedText, "translate to English", { isAiProcessing = it }) { editedText = it }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Family + size
                SectionTitle("Font", secondaryText)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontChoice("Sans", "DEFAULT", fontFamilyType, accent, panelStrong, border, Modifier.width(96.dp)) { fontFamilyType = it }
                    FontChoice("বাংলা", "BENGALI", fontFamilyType, accent, panelStrong, border, Modifier.width(96.dp)) { fontFamilyType = it }
                    FontChoice("Serif", "SERIF", fontFamilyType, accent, panelStrong, border, Modifier.width(96.dp)) { fontFamilyType = it }
                    FontChoice("Mono", "MONO", fontFamilyType, accent, panelStrong, border, Modifier.width(96.dp)) { fontFamilyType = it }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text Size", color = secondaryText, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    StepButton("−", panelStrong, border) { fontSize = (fontSize - 0.5f).coerceAtLeast(6f) }
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 6f..72f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    StepButton("+", panelStrong, border) { fontSize = (fontSize + 0.5f).coerceAtMost(72f) }
                    Text(
                        "${formatSize(fontSize)} sp",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Style and alignment
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Style", secondaryText)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StyleButton("B", isBold, accent, panelStrong, border, FontWeight.Bold) { isBold = !isBold }
                            StyleButton("I", isItalic, accent, panelStrong, border, FontWeight.Normal, true) { isItalic = !isItalic }
                            StyleButton("U", isUnderline, accent, panelStrong, border, FontWeight.Normal, false, true) { isUnderline = !isUnderline }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Alignment", secondaryText)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlignButton("LEFT", alignment, Icons.Default.FormatAlignLeft, accent, panelStrong, border) { alignment = it }
                            AlignButton("CENTER", alignment, Icons.Default.FormatAlignCenter, accent, panelStrong, border) { alignment = it }
                            AlignButton("RIGHT", alignment, Icons.Default.FormatAlignRight, accent, panelStrong, border) { alignment = it }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SectionTitle("Text Color", secondaryText)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { color ->
                        val selected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) accent else Color(0xFF6B7280),
                                    CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Small live preview: keeps editing simple without duplicating the whole PDF canvas.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("PREVIEW", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = editedText.ifBlank { " " },
                            color = selectedColor,
                            fontFamily = composeFontFamily,
                            fontSize = fontSize.coerceIn(10f, 28f).sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                            textAlign = composeTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = panel,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(initialText))
                            android.widget.Toast.makeText(context, "Original text copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = secondaryText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Copy original text", color = primaryText, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // Fixed bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111318))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = danger.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, danger.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onEraseFromDocument() }
                        .testTag("btn_erase_text_from_doc")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = danger, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = panelStrong,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Cancel", color = primaryText, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (editedText.isNotBlank()) accent else Color(0xFF334155),
                    modifier = Modifier
                        .weight(1.45f)
                        .height(48.dp)
                        .clickable(enabled = editedText.isNotBlank()) { apply() }
                        .testTag("btn_apply_edit_text")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AiActionChip(
    label: String,
    accent: Color,
    background: Color,
    border: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color(0xFFE7ECF3), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FontChoice(
    label: String,
    key: String,
    selected: String,
    accent: Color,
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val active = selected == key || (key == "DEFAULT" && selected == "SANS")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) accent.copy(alpha = 0.12f) else background,
        border = BorderStroke(1.dp, if (active) accent else border),
        modifier = modifier
            .height(44.dp)
            .clickable { onSelect(key) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) accent else Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StepButton(label: String, background: Color, border: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.size(42.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StyleButton(
    label: String,
    active: Boolean,
    accent: Color,
    background: Color,
    border: Color,
    weight: FontWeight,
    italic: Boolean = false,
    underline: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (active) accent.copy(alpha = 0.12f) else background,
        border = BorderStroke(1.dp, if (active) accent else border),
        modifier = Modifier.size(48.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) accent else Color.White,
                fontSize = 17.sp,
                fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
            )
        }
    }
}

@Composable
private fun AlignButton(
    key: String,
    selected: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    background: Color,
    border: Color,
    onSelect: (String) -> Unit
) {
    val active = selected == key
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (active) accent.copy(alpha = 0.12f) else background,
        border = BorderStroke(1.dp, if (active) accent else border),
        modifier = Modifier.size(48.dp).clickable { onSelect(key) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, key, tint = if (active) accent else Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

private fun prettyFamily(value: String): String = when (value.uppercase()) {
    "BENGALI", "BANGLA" -> "বাংলা / Bangla"
    "SERIF" -> "Serif"
    "MONO" -> "Mono"
    else -> "Sans"
}

private fun formatSize(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun runAiAction(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sourceText: String,
    instruction: String,
    setLoading: (Boolean) -> Unit,
    onResult: (String) -> Unit
) {
    if (sourceText.isBlank()) return
    setLoading(true)
    scope.launch {
        try {
            val improved = AiOrchestrator.editTextAi(sourceText, instruction, context)
            if (improved.isNotBlank()) onResult(improved)
        } finally {
            setLoading(false)
        }
    }
}
