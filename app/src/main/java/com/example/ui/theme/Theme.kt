package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = StudioCyan,
    onPrimary = StudioDarkBg,
    primaryContainer = StudioSurface,
    onPrimaryContainer = StudioCyan,
    secondary = StudioAmber,
    onSecondary = StudioDarkBg,
    secondaryContainer = StudioCardBg,
    onSecondaryContainer = StudioAmber,
    tertiary = StudioNeonGreen,
    background = StudioDarkBg,
    onBackground = TextPrimary,
    surface = StudioChassisDark,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurface,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = StudioRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Pro Audio Hardware consoles are strictly dark themed for optimal visibility and LED contrast
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = StudioDarkBg.toArgb()
            window.navigationBarColor = StudioDarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
