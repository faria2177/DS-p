package com.docscan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.util.ThemeManager

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmeraldLight,
    onPrimary = Color(0xFF003831),
    primaryContainer = PrimaryEmeraldDark,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = TertiaryAmber,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = TertiaryAmber,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

data class AppThemePalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val searchBg: Color,
    val primaryAccent: Color,
    val topBarBg: Color,
    val bottomBarBg: Color
)

@Composable
fun rememberAppThemePalette(): AppThemePalette {
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val isDark = ThemeManager.isDarkThemeActive(themeMode)
    return if (isDark) {
        AppThemePalette(
            isDark = true,
            background = Color(0xFF181A20),      // Soft ash dark canvas instead of full black
            surface = Color(0xFF22252E),         // Ash surface
            card = Color(0xFF22252E),            // Soft ash card background
            cardBorder = Color(0xFF2F3442),      // Ash card border
            textPrimary = Color(0xFFFFFFFF),     // Bright clear white text
            textSecondary = Color(0xFFA0AEC0),   // Clean ash secondary text
            textMuted = Color(0xFF718096),       // Readable muted text
            divider = Color(0xFF2B2F3D),         // Subtle ash divider
            searchBg = Color(0xFF2A2E3B),        // Ash search box
            primaryAccent = Color(0xFF00C48C),   // Emerald teal accent
            topBarBg = Color(0xFF181A20),
            bottomBarBg = Color(0xFF1D2028)
        )
    } else {
        AppThemePalette(
            isDark = false,
            background = Color(0xFFF8FAFC),      // Standard clean off-white
            surface = Color(0xFFFFFFFF),         // Pure white surface
            card = Color(0xFFFFFFFF),            // Clean white card
            cardBorder = Color(0xFFE2E8F0),      // Crisp subtle light border
            textPrimary = Color(0xFF0F172A),     // Crisp dark navy/slate text
            textSecondary = Color(0xFF334155),   // Clear secondary text
            textMuted = Color(0xFF64748B),       // Clear muted text
            divider = Color(0xFFE2E8F0),         // Clean light divider
            searchBg = Color(0xFFFFFFFF),        // White search field
            primaryAccent = Color(0xFF0F766E),   // Modern teal
            topBarBg = Color(0xFFFFFFFF),
            bottomBarBg = Color(0xFFFFFFFF)
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false, // Use our signature theme for authentic CamScanner look
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
