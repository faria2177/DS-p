package com.docscan.ui.components

import com.docscan.util.ThemeManager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.data.model.IdCardType

private val EmeraldTeal = Color(0xFF00C896)
private val ChipDarkBg: Color get() = ThemeManager.themeColor(Color(0xFF26282D), Color(0xFFF1F5F9))

/**
 * ID Cards intro overlay matching CamScanner / reference design.
 * Features:
 * - A4 Paper simulation card with "A4 paper example" badge
 * - Diagonal security watermark ("For xxx purpose only")
 * - Dynamic card mockups (General, ID Card, Driver License, Passport, Bank Card)
 * - Informative description with "Learn more >" modal
 * - Horizontal chips for ID card types
 * - "Make it now" primary CTA button
 */
@Composable
fun IdCardIntroOverlay(
    selectedType: IdCardType,
    onTypeSelected: (IdCardType) -> Unit,
    onMakeItNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLearnMoreSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. A4 Paper Example Simulation Card
        Surface(
            modifier = Modifier
                .width(260.dp)
                .aspectRatio(1f / 1.32f)
                .shadow(16.dp, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ID Card Sample Content based on selected type
                AnimatedContent(
                    targetState = selectedType,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "card_preview_anim"
                ) { type ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 34.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (type) {
                            IdCardType.BANK_CARD -> {
                                BankCardFrontMockup(modifier = Modifier.fillMaxWidth(0.92f))
                                BankCardBackMockup(modifier = Modifier.fillMaxWidth(0.92f))
                            }
                            IdCardType.ID_CARD -> {
                                NationalIdFrontMockup(modifier = Modifier.fillMaxWidth(0.92f))
                                NationalIdBackMockup(modifier = Modifier.fillMaxWidth(0.92f))
                            }
                            IdCardType.DRIVER_LICENSE -> {
                                DriverLicenseFrontMockup(modifier = Modifier.fillMaxWidth(0.92f))
                                DriverLicenseBackMockup(modifier = Modifier.fillMaxWidth(0.92f))
                            }
                            IdCardType.PASSPORT -> {
                                PassportMockup(modifier = Modifier.fillMaxWidth(0.95f))
                            }
                            IdCardType.GENERAL -> {
                                GeneralIdFrontMockup(modifier = Modifier.fillMaxWidth(0.92f))
                                GeneralIdBackMockup(modifier = Modifier.fillMaxWidth(0.92f))
                            }
                        }
                    }
                }

                // Top-Left "A4 paper example" badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0x99424248),
                    modifier = Modifier
                        .padding(start = 10.dp, top = 10.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "A4 paper example",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Subtitle Description with "Learn more >"
        val annotatedString = buildAnnotatedString {
            append("Create and share ID copies for various situations, including banking, administration, and more. ")
            pushStringAnnotation(tag = "learn_more", annotation = "learn_more")
            withStyle(
                style = SpanStyle(
                    color = EmeraldTeal,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.None
                )
            ) {
                append("Learn more >")
            }
            pop()
        }

        Text(
            text = annotatedString,
            color = Color(0xFFD4D4D8),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showLearnMoreSheet = true
                }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Horizontal Selector Chips
        val chips = listOf(
            IdCardType.GENERAL,
            IdCardType.ID_CARD,
            IdCardType.DRIVER_LICENSE,
            IdCardType.PASSPORT,
            IdCardType.BANK_CARD
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chips.forEach { chipType ->
                val isSelected = (chipType == selectedType)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color.White else ChipDarkBg,
                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)) else null,
                    modifier = Modifier
                        .height(38.dp)
                        .clickable { onTypeSelected(chipType) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = chipType.title,
                            color = if (isSelected) Color(0xFF101316) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. "Make it now" Primary Action Button
        Button(
            onClick = onMakeItNow,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldTeal,
                contentColor = Color(0xFF041E15)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .shadow(8.dp, RoundedCornerShape(26.dp), spotColor = EmeraldTeal)
        ) {
            Text(
                text = "Make it now",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Learn More Modal Bottom Sheet
    if (showLearnMoreSheet) {
        IdCardLearnMoreSheet(onDismiss = { showLearnMoreSheet = false })
    }
}

// -------------------------------------------------------------------------------------------------
// CARD MOCKUP VECTOR COMPONENTS
// -------------------------------------------------------------------------------------------------

@Composable
fun BankCardFrontMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF154388)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A4D98), Color(0xFF0E2C60), Color(0xFF091E44))
                    )
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Credit card",
                        color = Color(0xCCFFFFFF),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    )
                }

                // EMV Chip + Contactless Wave
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color(0xFFD4AF37),
                        modifier = Modifier
                            .width(18.dp)
                            .height(13.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(0.5.dp, Color(0xFF9E7800), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("))))", color = Color(0x88FFFFFF), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }

                // Card Number & Name
                Column {
                    Text(
                        "0123  4567  8901  2345",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CARDHOLDER NAME", color = Color(0xAAFFFFFF), fontSize = 5.sp)
                        Text("05/28", color = Color(0xAAFFFFFF), fontSize = 5.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BankCardBackMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFE2E8F0)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            // Magnetic Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color(0xFF1E293B))
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Signature & CVV Panel
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                ) {
                    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.padding(end = 4.dp)) {
                        Text("Authorized Signature", color = Color.Gray, fontSize = 4.sp)
                    }
                }
                Surface(
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Gray),
                    modifier = Modifier
                        .width(22.dp)
                        .height(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("123", color = Color.Black, fontSize = 5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "For customer service call bank 24/7",
                color = Color.DarkGray,
                fontSize = 4.sp,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
fun NationalIdFrontMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NATIONAL ID CARD",
                    color = Color(0xFF0F766E),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF047857), CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Body (Photo + Chip + Info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF94A3B8),
                    modifier = Modifier
                        .width(26.dp)
                        .height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("PHOTO", color = Color.White, fontSize = 5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Name: JOHN DOE", color = Color(0xFF0F172A), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                    Text("Father: ROBERT DOE", color = Color(0xFF475569), fontSize = 5.sp)
                    Text("DOB: 15 JAN 1994", color = Color(0xFF475569), fontSize = 5.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("NID: 1994 2819 0928 11", color = Color(0xFFDC2626), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NationalIdBackMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Address: House 12, Road 4, Sector 7, Dhaka", color = Color(0xFF334155), fontSize = 5.sp)
            Text("Blood Group: O+ | Issue Date: 12/03/2021", color = Color(0xFF334155), fontSize = 5.sp)

            // Barcode
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = 45
                    val step = size.width / barCount
                    for (i in 0 until barCount) {
                        if (i % 2 == 0 || i % 5 == 0) {
                            drawLine(
                                color = Color.Black,
                                start = Offset(i * step, 0f),
                                end = Offset(i * step, size.height),
                                strokeWidth = if (i % 3 == 0) 2.5f else 1.2f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverLicenseFrontMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFAF5FF),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE9D5FF))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Color(0xFF6B21A8))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("DRIVER LICENSE", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFA855F7),
                    modifier = Modifier
                        .width(24.dp)
                        .height(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("PHOTO", color = Color.White, fontSize = 5.sp)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("DL: DL-9284-8291", color = Color.Black, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                    Text("Class: MOTORCYCLE / CAR", color = Color.DarkGray, fontSize = 5.sp)
                    Text("EXP: 10/2030", color = Color(0xFF9333EA), fontSize = 5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DriverLicenseBackMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFAF5FF),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE9D5FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Restrictions: NONE | Endorsements: FULL", color = Color.DarkGray, fontSize = 5.sp)
            // 2D Barcode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0xFFE2E8F0))
            ) {
                Text("PDF417 2D BARCODE", color = Color.Gray, fontSize = 5.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun PassportMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.42f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFEF9C3),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCA8A04))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PASSPORT", color = Color(0xFF854D0E), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Text("TYPE: P", color = Color(0xFF854D0E), fontSize = 6.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFFEAB308),
                    modifier = Modifier
                        .width(28.dp)
                        .height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("PHOTO", color = Color.White, fontSize = 5.sp)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Surname: DOE", color = Color.Black, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                    Text("Given Name: JANE", color = Color.Black, fontSize = 6.sp)
                    Text("Passport No: EF928401", color = Color.Red, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }
            }
            // MRZ Lines
            Surface(color = Color(0x33000000), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    Text("P<BGDDOE<<JANE<<<<<<<<<<<<<<<<<<<<<<<<<<", fontFamily = FontFamily.Monospace, fontSize = 4.sp, color = Color.Black)
                    Text("EF928401<2BGD9401155F3010158<<<<<<<<<<<<04", fontFamily = FontFamily.Monospace, fontSize = 4.sp, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun GeneralIdFrontMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF94A3B8))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFF64748B),
                modifier = Modifier
                    .width(26.dp)
                    .height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ID", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text("IDENTITY CARD", color = Color(0xFF0F172A), fontSize = 6.sp, fontWeight = FontWeight.Bold)
                Text("CARD ID: #8492048", color = Color(0xFF475569), fontSize = 5.sp)
                Text("VALID THRU: 12/2029", color = Color(0xFF0284C7), fontSize = 5.sp)
            }
        }
    }
}

@Composable
fun GeneralIdBackMockup(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(1.586f)
            .shadow(4.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF94A3B8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Property of organization. If found please return.", color = Color.DarkGray, fontSize = 5.sp)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Black)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// LEARN MORE MODAL SHEET
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardLearnMoreSheet(onDismiss: () -> Unit) {
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID & Certificate Copy Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val benefits = listOf(
                Pair(Icons.Default.Security, "Anti-Fraud Security Watermark" to "Prevent unauthorized misuse by embedding customizable diagonal watermark text like 'For Banking Verification Only' across your copies."),
                Pair(Icons.Default.Print, "Standard 1:1 A4 Printing Scale" to "Automatically scales and aligns both Front & Back sides onto a standard A4/Letter sheet for instant physical printing or PDF export."),
                Pair(Icons.Default.CreditCard, "Universal Card & Passport Presets" to "Supports National ID (NID), Driver's License, Credit/Debit Cards, Passports, and Certificates with automated aspect ratio guides.")
            )

            benefits.forEach { (icon, texts) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldTeal.copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = EmeraldTeal, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(texts.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(texts.second, color = Color(0xFFB0B0B8), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldTeal,
                    contentColor = Color(0xFF041E15)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        }
    }
}
