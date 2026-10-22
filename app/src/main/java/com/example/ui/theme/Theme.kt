package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppPalette = staticCompositionLocalOf { getPaletteColors(AppPalette.CYBER_CYAN) }

@Composable
fun MyApplicationTheme(
    palette: AppPalette = AppPalette.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val colors = remember(palette) { getPaletteColors(palette) }

    val colorScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = Color(0xFF00262C),
            primaryContainer = colors.primaryDim,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondary,
            onSecondary = Color(0xFF00291E),
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.statusWarning,
            onTertiary = Color(0xFF281900),
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.statusAlert,
            onError = Color.White,
            outline = colors.cardBorder
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.primaryDim,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondary,
            onSecondary = Color.White,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.statusWarning,
            onTertiary = Color.Black,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.statusAlert,
            onError = Color.White,
            outline = colors.cardBorder
        )
    }

    CompositionLocalProvider(LocalAppPalette provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
