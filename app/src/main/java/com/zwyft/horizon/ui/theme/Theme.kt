package com.zwyft.horizon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary       = md_theme_dark_primary,
    onPrimary     = md_theme_dark_onPrimary,
    primaryContainer    = md_theme_dark_primaryContainer,
    onPrimaryContainer  = md_theme_dark_onPrimaryContainer,
    secondary     = md_theme_dark_secondary,
    onSecondary   = md_theme_dark_onSecondary,
    secondaryContainer  = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_darkOnSecondaryContainer,
    background    = md_theme_dark_background,
    onBackground  = md_theme_dark_onBackground,
    surface       = md_theme_dark_surface,
    onSurface     = md_theme_dark_onSurface,
    error         = md_theme_dark_error,
    onError       = md_theme_dark_onError,
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary       = md_theme_light_primary,
    onPrimary     = md_theme_light_onPrimary,
    primaryContainer    = md_theme_light_primaryContainer,
    onPrimaryContainer  = md_theme_light_onPrimaryContainer,
    secondary     = md_theme_light_secondary,
    onSecondary   = md_theme_light_onSecondary,
    secondaryContainer  = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_lightOnSecondaryContainer,
    background    = md_theme_light_background,
    onBackground  = md_theme_light_onBackground,
    surface       = md_theme_light_surface,
    onSurface     = md_theme_light_onSurface,
    error         = md_theme_light_error,
    onError       = md_theme_light_onError,
)

@Composable
fun HorizonTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(
        color = colorScheme.background,
        darkIcons = !darkTheme
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
