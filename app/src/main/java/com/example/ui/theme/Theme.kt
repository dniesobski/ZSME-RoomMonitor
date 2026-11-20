package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF04242E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = EmeraldFreeDark,
    onSecondary = Color(0xFF03291A),
    secondaryContainer = EmeraldFreeContainerDark,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberSoonDark,
    onTertiary = Color(0xFF2D1902),
    tertiaryContainer = AmberSoonContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF451212),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = EmeraldFree,
    onSecondary = Color.White,
    secondaryContainer = EmeraldFreeContainer,
    onSecondaryContainer = OnEmeraldFreeContainer,
    tertiary = AmberSoon,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoonContainer,
    onTertiaryContainer = OnAmberSoonContainer,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = CoralAlert,
    errorContainer = CoralAlertContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun WolneSaleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
