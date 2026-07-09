package com.athletic.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Colores semánticos del tema actual (incluyen acento efectivo y su contraste). */
data class AppColors(
    val bg: Color,
    val surface: Color,
    val track: Color,
    val textPrimary: Color,
    val textDim: Color,
    val textFaded: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

private fun darkBase(accent: Color, onAccent: Color) = AppColors(
    bg = BG,
    surface = SURFACE,
    track = TRACK,
    textPrimary = Color.White,
    textDim = TEXT_DIM,
    textFaded = TEXT_FADED,
    accent = accent,
    onAccent = onAccent,
    isDark = true,
)

private fun lightBase(accent: Color, onAccent: Color) = AppColors(
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    track = Color(0xFFE9ECEF),
    textPrimary = Color(0xFF1A1C1E),
    textDim = Color(0xFF5A6065),
    textFaded = Color(0xFF9AA0A3),
    accent = accent,
    onAccent = onAccent,
    isDark = false,
)

private val DEFAULT_DARK = darkBase(Color(DEFAULT_ACCENT), ON_ACCENT)

val LocalAppColors = staticCompositionLocalOf { DEFAULT_DARK }

/** Acceso a los colores semánticos del tema actual. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun AthleticTheme(
    accent: Long = DEFAULT_ACCENT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) {
        darkBase(Color(accent), ON_ACCENT)
    } else {
        lightBase(Color(accent), Color.White)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !appColors.isDark
            controller.isAppearanceLightNavigationBars = !appColors.isDark
        }
    }
    val scheme = if (appColors.isDark) {
        darkColorScheme(
            background = appColors.bg,
            surface = appColors.surface,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    } else {
        lightColorScheme(
            background = appColors.bg,
            surface = appColors.surface,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    }
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
