package com.coparenting.chronicle.horizon.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary              = Brand40,
    onPrimary            = Color.White,
    primaryContainer     = Brand95,
    onPrimaryContainer   = Brand20,
    secondary            = Sky40,
    onSecondary          = Color.White,
    secondaryContainer   = Sky90,
    onSecondaryContainer = Sky30,
    tertiary             = Amber40,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFFEF3C7),
    onTertiaryContainer  = Color(0xFF92400E),
    background           = Slate50,
    onBackground         = Slate900,
    surface              = Color.White,
    onSurface            = Slate900,
    surfaceVariant       = Slate100,
    onSurfaceVariant     = Slate600,
    outline              = Slate300,
    outlineVariant       = Slate200,
)

private val DarkColors = darkColorScheme(
    primary              = Brand80,
    onPrimary            = Brand20,
    primaryContainer     = Brand30,
    onPrimaryContainer   = Brand90,
    secondary            = Sky80,
    onSecondary          = Sky30,
    secondaryContainer   = Color(0xFF0C3E5A),
    onSecondaryContainer = Sky90,
    tertiary             = Amber80,
    onTertiary           = Color(0xFF783A00),
    tertiaryContainer    = Color(0xFF9C4E00),
    onTertiaryContainer  = Color(0xFFFEF3C7),
    background           = Slate950,
    onBackground         = Slate100,
    surface              = Slate900,
    onSurface            = Slate100,
    surfaceVariant       = Slate800,
    onSurfaceVariant     = Slate400,
    outline              = Slate600,
    outlineVariant       = Slate700,
)

@Composable
fun HorizonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HorizonTypography,
        content = content
    )
}
