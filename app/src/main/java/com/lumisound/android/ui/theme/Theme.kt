package com.lumisound.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Accent defaults to the bridge's own default `theme_color` so a fresh install looks
 * like the account it is about to sign into; an import replaces it with whatever that
 * account actually has set, which is the same value Lumisound themes itself with.
 */
const val DEFAULT_ACCENT = "#EC4079"

fun parseAccent(hex: String?): Color = try {
    val cleaned = (hex ?: DEFAULT_ACCENT).removePrefix("#")
    val value = cleaned.toLong(16)
    when (cleaned.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color(0xFFEC4079)
    }
} catch (e: Exception) {
    Color(0xFFEC4079)
}

/**
 * The few values Material's own scheme has no slot for: the page gradient, the hairline
 * that separates rows without drawing a full divider, and the accent's own soft wash used
 * behind selected things.
 */
data class LumiPalette(
    val accent: Color,
    val pageTop: Color,
    val pageBottom: Color,
    val elevatedSurface: Color,
    val hairline: Color,
    val accentWash: Color,
) {
    val pageBrush: Brush get() = Brush.verticalGradient(listOf(pageTop, pageBottom))
}

val LocalLumiPalette = staticCompositionLocalOf {
    LumiPalette(
        accent = Color(0xFFEC4079),
        pageTop = Color(0xFF14121C),
        pageBottom = Color(0xFF08070C),
        elevatedSurface = Color(0xFF1A1825),
        hairline = Color(0x14FFFFFF),
        accentWash = Color(0x1FEC4079),
    )
}

@Composable
fun LumiMusicTheme(
    accentHex: String? = null,
    content: @Composable () -> Unit,
) {
    val accent = parseAccent(accentHex)
    // Dark always, for now. The light palette below exists but has never been looked at on
    // a real screen, and shipping a half-designed second surface to whoever happens to have
    // light mode on is worse than having one surface that was actually designed.
    @Suppress("UNUSED_VARIABLE")
    val systemDark = isSystemInDarkTheme()
    val dark = true

    // Deep navy rather than neutral black, matching Lumisound's own surfaces, lifted
    // slightly toward the accent's hue at the top so artwork has something to sit on and
    // the accent reads as part of the surface instead of a sticker on it.
    val palette = if (dark) {
        LumiPalette(
            accent = accent,
            pageTop = accent.copy(alpha = 0.09f).compositeOver(Color(0xFF111528)),
            pageBottom = Color(0xFF080A14),
            elevatedSurface = Color(0xFF1A1F33),
            hairline = Color(0x14FFFFFF),
            accentWash = accent.copy(alpha = 0.16f),
        )
    } else {
        LumiPalette(
            accent = accent,
            pageTop = accent.copy(alpha = 0.08f).compositeOver(Color(0xFFFDFBFF)),
            pageBottom = Color(0xFFF6F4F9),
            elevatedSurface = Color(0xFFFFFFFF),
            hairline = Color(0x14000000),
            accentWash = accent.copy(alpha = 0.12f),
        )
    }

    val colors = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.22f).compositeOver(Color(0xFF14121C)),
            onPrimaryContainer = Color(0xFFF6F2FA),
            secondary = accent.copy(alpha = 0.8f),
            background = palette.pageBottom,
            // Lumisound's own text tokens, so the two apps read as one family.
            onBackground = Color(0xFFF7FAFC),
            surface = palette.elevatedSurface,
            onSurface = Color(0xFFF7FAFC),
            surfaceVariant = Color(0xFF232942),
            onSurfaceVariant = Color(0xFFCBD5E0),
            outline = Color(0xFF3A4260),
            outlineVariant = Color(0xFF262C42),
            error = Color(0xFFFC8181),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = palette.pageBottom,
            surface = palette.elevatedSurface,
            onSurfaceVariant = Color(0xFF5C5670),
        )
    }

    CompositionLocalProvider(
        LocalLumiPalette provides palette,
        // Material3 defaults LocalContentColor to BLACK and relies on a Surface to provide
        // the right one. This app draws its screens straight onto a gradient with no
        // Surface in between, so every Text that did not pass an explicit colour -- section
        // titles, screen headings -- was rendering black on a near-black page. Providing it
        // at the theme means a composable never has to know what it is sitting on.
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            typography = Typography(
                displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
                titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
                titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp),
                bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            ),
            content = content,
        )
    }
}

/**
 * Per-section icon tints.
 *
 * Lumisound gives each section header its own colour rather than repeating the accent, which
 * is most of what makes a long scrolling screen readable as sections instead of one list.
 */
object SectionTint {
    val Recent = Color(0xFF4FD1C5)
    val Library = Color(0xFF63B3ED)
    val Favorites = Color(0xFFF687B3)
    val Offline = Color(0xFF9F7AEA)
    val Device = Color(0xFFF6AD55)
    val Playlists = Color(0xFF68D391)
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}
