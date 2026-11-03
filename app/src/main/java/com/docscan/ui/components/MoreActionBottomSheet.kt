package com.docscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.ui.theme.rememberAppThemePalette

private val DestructiveColor = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreActionBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    // Primary Actions
    onSignClick: () -> Unit,
    onWatermarkClick: () -> Unit,
    onSendToPcClick: () -> Unit,
    onPdfToImagesClick: () -> Unit,
    onPrintClick: () -> Unit,
    // Convert Section
    onToWordClick: () -> Unit,
    onToExcelClick: () -> Unit,
    // Smart Tools Section
    onExtractTextClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onCompressClick: () -> Unit,
    onReadModeClick: () -> Unit,
    onCaseSummaryClick: () -> Unit,
    // Manage Section
    onManagePagesClick: () -> Unit,
    onLockClick: () -> Unit,
    onMergeFilesClick: () -> Unit,
    onCopyMoveClick: () -> Unit,
    // Send Section
    onEmailToMyselfClick: () -> Unit,
    // Save to Device Section
    onSaveToDevicePdfClick: (() -> Unit)? = null,
    onSaveToGalleryClick: (() -> Unit)? = null,
    // Delete Action
    onDeleteClick: () -> Unit
) {
    val palette = rememberAppThemePalette()
    val isDark = palette.isDark
    val sheetBackground = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val headerTitleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val closeButtonBg = if (isDark) Color(0xFF2E2E32) else Color(0xFFF1F5F9)
    val closeButtonTint = if (isDark) Color.White else Color(0xFF334155)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = Modifier.testTag("more_action_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More",
                    color = headerTitleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(closeButtonBg)
                        .testTag("close_more_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = closeButtonTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PROFESSIONAL DOCUMENT EDITING — direct full-screen editors.
            SectionTitle(title = "Professional edit")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfessionalEditCard(
                    title = "Add Signature",
                    subtitle = "Draw, type, image or stamp • place on any page",
                    icon = Icons.Default.Draw,
                    gradient = Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))),
                    onClick = {
                        onDismiss()
                        onSignClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ProfessionalEditCard(
                    title = "Add Watermark",
                    subtitle = "Text or image • opacity, rotation & page range",
                    icon = Icons.Default.BrandingWatermark,
                    gradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))),
                    onClick = {
                        onDismiss()
                        onWatermarkClick()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // QUICK ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PrimaryActionItem(
                    label = "Send to PC",
                    icon = Icons.Default.QrCode,
                    gradient = Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF10B981))),
                    onClick = {
                        onDismiss()
                        onSendToPcClick()
                    }
                )

                PrimaryActionItem(
                    label = "PDF to Images",
                    icon = Icons.Default.PhotoLibrary,
                    gradient = Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFF59E0B))),
                    onClick = {
                        onDismiss()
                        onPdfToImagesClick()
                    }
                )

                PrimaryActionItem(
                    label = "Print",
                    icon = Icons.Default.Print,
                    gradient = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6))),
                    onClick = {
                        onDismiss()
                        onPrintClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONVERT SECTION
            SectionTitle(title = "Convert")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolGridItem(
                    label = "To Word",
                    icon = Icons.Default.Description,
                    iconTint = Color(0xFF38BDF8),
                    onClick = {
                        onDismiss()
                        onToWordClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "To Excel",
                    icon = Icons.Default.TableChart,
                    iconTint = Color(0xFF34D399),
                    onClick = {
                        onDismiss()
                        onToExcelClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SMART TOOLS SECTION
            SectionTitle(title = "Smart Tools")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolGridItem(
                    label = "Extract Text",
                    icon = Icons.Default.TextFields,
                    iconTint = Color(0xFF60A5FA),
                    onClick = {
                        onDismiss()
                        onExtractTextClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Translate",
                    icon = Icons.Default.Translate,
                    iconTint = Color(0xFFA78BFA),
                    onClick = {
                        onDismiss()
                        onTranslateClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Compress",
                    icon = Icons.Default.Compress,
                    iconTint = Color(0xFFFBBF24),
                    onClick = {
                        onDismiss()
                        onCompressClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Read Mode",
                    icon = Icons.Default.MenuBook,
                    iconTint = Color(0xFF4ADE80),
                    onClick = {
                        onDismiss()
                        onReadModeClick()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolGridItem(
                    label = "Case Summary",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = Color(0xFFF472B6),
                    onClick = {
                        onDismiss()
                        onCaseSummaryClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // MANAGE SECTION
            SectionTitle(title = "Manage")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolGridItem(
                    label = "Manage Pages",
                    icon = Icons.Default.Layers,
                    iconTint = Color(0xFF38BDF8),
                    onClick = {
                        onDismiss()
                        onManagePagesClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Lock",
                    icon = Icons.Default.Lock,
                    iconTint = Color(0xFFF87171),
                    onClick = {
                        onDismiss()
                        onLockClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Merge Files",
                    icon = Icons.Default.CallMerge,
                    iconTint = Color(0xFF818CF8),
                    onClick = {
                        onDismiss()
                        onMergeFilesClick()
                    },
                    modifier = Modifier.weight(1f)
                )
                ToolGridItem(
                    label = "Copy/Move",
                    icon = Icons.Default.DriveFileMove,
                    iconTint = Color(0xFFFBBF24),
                    onClick = {
                        onDismiss()
                        onCopyMoveClick()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SEND & SAVE SECTION
            SectionTitle(title = "Save & Share")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSaveToDevicePdfClick != null) {
                    ToolGridItem(
                        label = "Save PDF",
                        icon = Icons.Default.Download,
                        iconTint = Color(0xFF2DD4BF),
                        onClick = {
                            onDismiss()
                            onSaveToDevicePdfClick()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (onSaveToGalleryClick != null) {
                    ToolGridItem(
                        label = "Save Photos",
                        icon = Icons.Default.PhotoLibrary,
                        iconTint = Color(0xFFFBBF24),
                        onClick = {
                            onDismiss()
                            onSaveToGalleryClick()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                ToolGridItem(
                    label = "Email Doc",
                    icon = Icons.Default.Email,
                    iconTint = Color(0xFF38BDF8),
                    onClick = {
                        onDismiss()
                        onEmailToMyselfClick()
                    },
                    modifier = Modifier.weight(1f)
                )

                if (onSaveToDevicePdfClick == null || onSaveToGalleryClick == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = if (isDark) Color(0xFF2C2C30) else Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // DELETE ACTION (Centered Destructive Action)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onDeleteClick()
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = DestructiveColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete",
                    color = DestructiveColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfessionalEditCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = rememberAppThemePalette().isDark
    val bg = if (isDark) Color(0xFF26282D) else Color(0xFFF8FAFC)
    val titleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subColor = if (isDark) Color(0xFFB6BCC8) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = titleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = subColor,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    val isDark = rememberAppThemePalette().isDark
    Text(
        text = title,
        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF64748B),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PrimaryActionItem(
    label: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    val isDark = rememberAppThemePalette().isDark
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolGridItem(
    label: String,
    icon: ImageVector,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = rememberAppThemePalette().isDark
    val containerBg = if (isDark) {
        Color(0xFF2B2B2E)
    } else {
        if (iconTint != Color.White) iconTint.copy(alpha = 0.12f) else Color(0xFFF1F5F9)
    }
    val effectiveTint = if (isDark) {
        iconTint
    } else {
        if (iconTint == Color.White) Color(0xFF0F766E) else iconTint
    }
    val textColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(containerBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = effectiveTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
