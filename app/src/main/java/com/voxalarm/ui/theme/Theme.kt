package com.voxalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VoxBlack = Color(0xFF08090D)
val VoxCard = Color(0xFF171920)
val VoxCardHigh = Color(0xFF1F222B)
val VoxOutline = Color(0xFF2A2E3A)
val VoxText = Color(0xFFF2F4F8)
val VoxTextDim = Color(0xFF9AA0AE)
val VoxTextFaint = Color(0xFF5E6472)

data class NeonColor(val name: String, val color: Color, val partner: Color)

/** Accent palette – every alarm picks one, and the app accent comes from here too. */
object NeonPalette {
    val colors = listOf(
        NeonColor("Turquoise", Color(0xFF00F5D4), Color(0xFF2D9CFF)),
        NeonColor("Pink", Color(0xFFFF2E97), Color(0xFFB967FF)),
        NeonColor("Violet", Color(0xFFB967FF), Color(0xFF2D9CFF)),
        NeonColor("Blue", Color(0xFF2D9CFF), Color(0xFF00F5D4)),
        NeonColor("Orange", Color(0xFFFF8A00), Color(0xFFFF2E97)),
        NeonColor("Lime", Color(0xFFA6FF00), Color(0xFF00F5D4)),
    )

    operator fun get(index: Int): NeonColor = colors[Math.floorMod(index, colors.size)]
    fun argb(index: Int): Int = get(index).color.toArgb()
    fun gradient(index: Int): Brush = get(index).let { Brush.horizontalGradient(listOf(it.color, it.partner)) }
}

/** Current app accent, chosen in Settings. */
val LocalAccent = staticCompositionLocalOf { NeonPalette[0] }

private val VoxTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin, fontSize = 76.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 52.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun VoxTheme(accentIndex: Int = 0, content: @Composable () -> Unit) {
    val accent = NeonPalette[accentIndex]
    val scheme = darkColorScheme(
        primary = accent.color,
        onPrimary = VoxBlack,
        primaryContainer = accent.color.copy(alpha = 0.18f),
        onPrimaryContainer = accent.color,
        secondary = accent.partner,
        onSecondary = VoxBlack,
        tertiary = Color(0xFFFF2E97),
        background = VoxBlack,
        onBackground = VoxText,
        surface = VoxBlack,
        onSurface = VoxText,
        surfaceVariant = VoxCard,
        onSurfaceVariant = VoxTextDim,
        surfaceContainer = VoxCard,
        surfaceContainerHigh = VoxCardHigh,
        surfaceContainerHighest = VoxCardHigh,
        surfaceContainerLow = VoxCard,
        surfaceContainerLowest = VoxBlack,
        outline = VoxOutline,
        outlineVariant = VoxOutline,
        error = Color(0xFFFF5370),
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = VoxTypography, content = content)
    }
}
