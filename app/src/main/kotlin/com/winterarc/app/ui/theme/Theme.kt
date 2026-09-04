package com.winterarc.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.winterarc.core.Accent

/**
 * WINTER ARC.
 *
 * Three colours, three jobs, and no overlap between them:
 *
 *  - NIGHT GREEN is the ground. Every surface is built from it, so the app has one continuous
 *    body rather than a set of unrelated panels.
 *  - GOLD is meaning. It marks exactly one thing per view -- the number being chased, or the
 *    action to take next. The moment a second thing is gold, neither is findable at arm's
 *    length in a gym.
 *  - NIGHT BLUE is depth and cool contrast: the far end of gradients, and the identity colour
 *    for pushing work, so the training days are distinguishable at a glance.
 *
 * The app is dark-only, deliberately. It is used under gym lighting, often late, and a light
 * theme would cost the identity while buying nothing.
 */
object W {
    // -- ground ----------------------------------------------------------------------------
    val Void = Color(0xFF04100E)          // deepest -- behind everything
    val Night = Color(0xFF071C17)         // base night green
    val NightHi = Color(0xFF0B2A21)       // raised night green (cards)
    val NightTop = Color(0xFF10382B)      // the lit edge of a raised surface
    val Blue = Color(0xFF071726)          // night blue
    val BlueHi = Color(0xFF0D2740)
    val BlueTop = Color(0xFF17436B)

    // -- lines -----------------------------------------------------------------------------
    val Line = Color(0xFF17372C)          // hairline on green
    val LineSoft = Color(0x2624543F)
    val LineBlue = Color(0xFF16324D)

    // -- gold ------------------------------------------------------------------------------
    val Gold = Color(0xFFE3BC6B)
    val GoldBright = Color(0xFFF7E1A8)
    val GoldDeep = Color(0xFFB08A3C)
    val GoldFilm = Color(0x1FE3BC6B)      // tinted fill behind gold content
    val GoldEdge = Color(0x59E3BC6B)      // gold hairline

    // -- ink -------------------------------------------------------------------------------
    val Ink = Color(0xFFECF4EF)
    val Muted = Color(0xFF8CA79A)
    val Faint = Color(0xFF5A7166)
    val Ghost = Color(0xFF37493F)

    // -- semantic --------------------------------------------------------------------------
    val Good = Color(0xFF4FD1A0)
    val Warn = Color(0xFFE8B14C)
    val Bad = Color(0xFFE8695F)
    val Cyan = Color(0xFF56A8D8)

    /** The colour a routine is identified by, so a training day is recognisable before reading. */
    fun accent(accent: Accent): Color = when (accent) {
        Accent.GOLD -> Gold
        Accent.GREEN -> Good
        Accent.BLUE -> Cyan
    }
}

/**
 * Gradients carry the depth in this design.
 *
 * A card is never a flat fill: it runs from a slightly lit top edge down into the ground, which
 * is what makes a surface read as raised rather than as a rectangle of a different colour.
 */
object Grad {
    val screen = Brush.verticalGradient(
        0f to W.Night,
        0.45f to W.Void,
        1f to Color(0xFF05141F),          // the ground cools into night blue at the bottom
    )

    val card = Brush.verticalGradient(listOf(W.NightHi, W.Night))

    val cardLit = Brush.verticalGradient(listOf(W.NightTop, W.NightHi))

    val blueCard = Brush.verticalGradient(listOf(W.BlueHi, W.Blue))

    val goldFill = Brush.horizontalGradient(listOf(W.GoldDeep, W.Gold, W.GoldBright))

    val goldSoft = Brush.verticalGradient(listOf(Color(0x2BE3BC6B), Color(0x08E3BC6B)))

    /** The hairline that reads as light catching the top edge of a raised surface. */
    val edge = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x00FFFFFF)))

    fun accentWash(color: Color): Brush = Brush.verticalGradient(
        listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.02f)),
    )

    fun fade(color: Color): Brush = Brush.verticalGradient(
        listOf(color.copy(alpha = 0.36f), color.copy(alpha = 0.0f)),
    )
}

/**
 * The type scale.
 *
 * Built on one idea: enormous numerals against very small, wide-tracked labels. A figure read
 * mid-set has to land from arm's length, and the contrast in scale is what creates hierarchy
 * without needing a second colour to do it.
 */
private val WinterType = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-3).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-2).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp,
    ),
    // The small wide-tracked label that names every figure in the app.
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp,
    ),
)

private val Scheme = darkColorScheme(
    primary = W.Gold,
    onPrimary = W.Void,
    primaryContainer = W.GoldFilm,
    onPrimaryContainer = W.GoldBright,
    secondary = W.Cyan,
    onSecondary = W.Void,
    secondaryContainer = W.BlueHi,
    onSecondaryContainer = W.Ink,
    tertiary = W.Good,
    onTertiary = W.Void,
    background = W.Void,
    onBackground = W.Ink,
    surface = W.NightHi,
    onSurface = W.Ink,
    surfaceVariant = W.Night,
    onSurfaceVariant = W.Muted,
    surfaceContainer = W.NightHi,
    surfaceContainerHigh = W.NightTop,
    surfaceContainerHighest = W.NightTop,
    outline = W.Line,
    outlineVariant = W.LineSoft,
    error = W.Bad,
    onError = W.Ink,
    scrim = Color(0xCC02090A),
)

@Composable
fun WinterArcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = WinterType, content = content)
}
