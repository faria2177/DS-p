package com.docscan.ui.remover.model

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.Color

/**
 * Supported AI processing providers
 */
enum class BgRemovalProvider(val displayName: String, val badge: String) {
    HYBRID_AI("Hybrid AI (Smart)", "⚡"),
    GEMINI("Google Gemini 2.5 Pro", "✨"),
    CLAUDE("Anthropic Claude 3.5", "🟣"),
    LOCAL_PROCESSING("On-Device Native", "📱")
}

/**
 * Detected subject categories for tailored edge processing
 */
enum class SubjectCategory(val displayName: String, val isHairOrFur: Boolean) {
    PERSON("Person / Portrait", true),
    PET_ANIMAL("Pet / Animal", true),
    PRODUCT("E-commerce Product", false),
    DOCUMENT("Document / Card", false),
    VEHICLE("Vehicle", false),
    FURNITURE("Furniture / Interior", false),
    GENERAL_OBJECT("Object", false)
}

/**
 * Processing stages shown in the premium progress UI
 */
enum class BgRemovalStage(val title: String, val progress: Float) {
    IDLE("Ready", 0f),
    ANALYZING("Analyzing image...", 0.15f),
    DETECTING_SUBJECT("Detecting subject...", 0.35f),
    REMOVING_BACKGROUND("Removing background...", 0.55f),
    REFINING_EDGES("Refining edges...", 0.72f),
    PROCESSING_HAIR("Processing hair details...", 0.88f),
    FINALIZING_TRANSPARENCY("Finalizing transparency...", 0.96f),
    VALIDATING("Validating mask...", 0.98f),
    DONE("Done", 1.0f),
    ERROR("Failed", 0f)
}

/**
 * Background replacement types
 */
sealed class BackgroundStyle {
    object Transparent : BackgroundStyle()
    data class Solid(val color: Color, val name: String) : BackgroundStyle()
    data class Gradient(val colors: List<Color>, val name: String) : BackgroundStyle()
    data class Blur(val radius: Float = 25f) : BackgroundStyle()
    data class CustomPhoto(val bitmap: Bitmap) : BackgroundStyle()
}

/**
 * Manual mask editing mode
 */
enum class ManualBrushMode {
    NONE,
    ERASE,   // Erase remaining background
    RESTORE  // Restore missing subject details
}

/**
 * Auto-crop options with padding percentages
 */
enum class AutoCropPadding(val label: String, val paddingRatio: Float) {
    NONE("Off", 0f),
    PAD_0("0%", 0f),
    PAD_5("5%", 0.05f),
    PAD_10("10%", 0.10f),
    PAD_15("15%", 0.15f)
}

/**
 * Quality validation report
 */
data class MaskQualityReport(
    val qualityScore: Float, // 0.0 to 1.0
    val subjectDetected: Boolean,
    val hasHolesInSubject: Boolean,
    val haloDetected: Boolean,
    val autoRefined: Boolean
)

/**
 * Result data holder for the background removal engine
 */
data class BackgroundRemovalResult(
    val originalBitmap: Bitmap,
    val alphaMask: Bitmap, // 8-bit alpha mask (A8 or ARGB_8888)
    val transparentBitmap: Bitmap, // Subject on transparent background
    val subjectCategory: SubjectCategory,
    val boundingBox: Rect,
    val providerUsed: BgRemovalProvider,
    val qualityReport: MaskQualityReport
)
