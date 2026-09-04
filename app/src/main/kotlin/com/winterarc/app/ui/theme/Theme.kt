package com.winterarc.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * WINTER ARC palette.
 *
 * Night green carries the surface hierarchy; gold is reserved for numbers, primary actions
 * and anything the user needs to find at a glance while training. Night blue appears only
 * as a secondary accent (rest days, informational states) so gold keeps its meaning — if
 * everything is highlighted, nothing is.
 *
 * The app is dark-only by design: it is used in a gym, often in low light, and a light theme
 * would undermine the identity for no practical gain.
 */
object WinterArcColors {
    // Night green — the dominant surface family.
    val NightDeep = Color(0xFF061512)      // app background
    val NightSurface = Color(0xFF0C2119)   // cards
    val NightElevated = Color(0xFF123024)  // raised cards, sheets
    val NightBorder = Color(0xFF1D4735)    // hairlines and outlines
    val NightTimer = Color(0xFF0A2A1D)     // full-screen rest timer ground

    // Gold — accent, numbers, primary actions.
    val Gold = Color(0xFFD9B25F)
    val GoldBright = Color(0xFFF2CE7A)     // large display numerals
    val GoldDim = Color(0xFF8A7038)        // disabled / de-emphasised gold
    val GoldOverlay = Color(0x1AD9B25F)    // tinted fills

    // Night blue — secondary, used sparingly.
    val NightBlue = Color(0xFF16304D)
    val NightBlueBright = Color(0xFF4F87C4)

    // Neutrals.
    val White = Color(0xFFF2F5F2)
    val Muted = Color(0xFF9BB0A5)
    val Faint = Color(0xFF5E7268)

    // Semantic.
    val Success = Color(0xFF5FBF8F)
    val Warning = Color(0xFFE0A94A)
    val Danger = Color(0xFFD9635F)
}

private val WinterArcScheme = darkColorScheme(
    primary = WinterArcColors.Gold,
    onPrimary = WinterArcColors.NightDeep,
    primaryContainer = WinterArcColors.GoldOverlay,
    onPrimaryContainer = WinterArcColors.GoldBright,
    secondary = WinterArcColors.NightBlueBright,
    onSecondary = WinterArcColors.White,
    secondaryContainer = WinterArcColors.NightBlue,
    onSecondaryContainer = WinterArcColors.White,
    tertiary = WinterArcColors.Success,
    onTertiary = WinterArcColors.NightDeep,
    background = WinterArcColors.NightDeep,
    onBackground = WinterArcColors.White,
    surface = WinterArcColors.NightSurface,
    onSurface = WinterArcColors.White,
    surfaceVariant = WinterArcColors.NightElevated,
    onSurfaceVariant = WinterArcColors.Muted,
    outline = WinterArcColors.NightBorder,
    outlineVariant = WinterArcColors.NightBorder,
    error = WinterArcColors.Danger,
    onError = WinterArcColors.White,
)

/**
 * Type scale tuned for use mid-set: large numerals, generous weight, tabular figures where
 * a value updates in place so the layout does not jitter.
 */
private val WinterArcTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp,
    ),
)

val LocalWinterArcColors = staticCompositionLocalOf { WinterArcColors }

@Composable
fun WinterArcTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWinterArcColors provides WinterArcColors) {
        MaterialTheme(
            colorScheme = WinterArcScheme,
            typography = WinterArcTypography,
            content = content,
        )
    }
}
