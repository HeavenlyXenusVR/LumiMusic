package com.lumisound.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Accent defaults to the bridge's own default `theme_color` (#EC4079) so a fresh
 * install looks like the account it is about to sign into; an import overrides it
 * with whatever that account actually has set.
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

private val Ink = Color(0xFF08070C)
private val Surface1 = Color(0xFF12111A)
private val Surface2 = Color(0xFF1B1A25)

@Composable
fun LumiMusicTheme(
    accentHex: String? = null,
    content: @Composable () -> Unit,
) {
    val accent = parseAccent(accentHex)
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent.copy(alpha = 0.75f),
            background = Ink,
            onBackground = Color(0xFFF3F1F8),
            surface = Surface1,
            onSurface = Color(0xFFF3F1F8),
            surfaceVariant = Surface2,
            onSurfaceVariant = Color(0xFFB4AFC4),
            outline = Color(0xFF3A3747),
        )
    } else {
        lightColorScheme(primary = accent, secondary = accent.copy(alpha = 0.75f))
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 15.sp),
            bodySmall = TextStyle(fontSize = 13.sp),
            labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
