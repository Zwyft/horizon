package com.coparenting.chronicle.horizon.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Navy40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Navy90,
    onPrimaryContainer = Navy10,
    secondary = Teal40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Amber40,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = Amber80,
    background = Surface,
    surface = Surface,
    surfaceVariant = SurfaceVar
)

private val DarkColors = darkColorScheme(
    primary = Navy80,
    onPrimary = Navy20,
    primaryContainer = Navy30,
    onPrimaryContainer = Navy90,
    secondary = Teal80,
    onSecondary = Color(0xFF003738),
    secondaryContainer = Color(0xFF004F50),
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF5F2B00)
)

@Composable
fun HorizonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HorizonTypography,
        content = content
    )
}
