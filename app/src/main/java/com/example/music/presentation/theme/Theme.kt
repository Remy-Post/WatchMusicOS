package com.example.music.presentation.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

/** One UI Watch palette: true black for AMOLED, soft whites, signature blue. */
object WatchColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2A2A2D)
    val OnSurface = Color(0xFFFAFAFA)
    val OnSurfaceVariant = Color(0xFFB4B4B8)
    val OneUiBlue = Color(0xFF3E91FF)
    val Heart = Color(0xFFFF4B5C)
    val RingTrack = Color(0x33FFFFFF)
}

/** Accent pulled from the current cover; animates when the song changes. */
val LocalAccent = staticCompositionLocalOf { WatchColors.OneUiBlue }

@Composable
fun MusicTheme(
    accent: Color = WatchColors.OneUiBlue,
    content: @Composable () -> Unit,
) {
    val animatedAccent by animateColorAsState(accent, tween(600), label = "accent")
    val colors = ColorScheme(
        primary = animatedAccent,
        primaryDim = animatedAccent.copy(alpha = 0.8f),
        primaryContainer = WatchColors.SurfaceHigh,
        onPrimary = Color.Black,
        onPrimaryContainer = WatchColors.OnSurface,
        secondary = WatchColors.OnSurfaceVariant,
        // Horologist's volume level indicator draws with secondaryDim.
        secondaryDim = animatedAccent,
        surfaceContainerLow = WatchColors.Surface,
        surfaceContainer = WatchColors.Surface,
        surfaceContainerHigh = WatchColors.SurfaceHigh,
        onSurface = WatchColors.OnSurface,
        onSurfaceVariant = WatchColors.OnSurfaceVariant,
        background = WatchColors.Background,
        onBackground = WatchColors.OnSurface,
        error = WatchColors.Heart,
    )
    // FontFamily.Default resolves to the watch's system font: SamsungOne on Galaxy watches.
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(defaultFontFamily = FontFamily.Default),
    ) {
        CompositionLocalProvider(LocalAccent provides animatedAccent, content = content)
    }
}
