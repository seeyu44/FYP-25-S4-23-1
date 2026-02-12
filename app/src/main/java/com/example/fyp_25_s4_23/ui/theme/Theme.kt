package com.example.fyp_25_s4_23.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DeepfakeDarkColorScheme = darkColorScheme(
    primary = CyanPoint,
    secondary = CyanVariant,
    tertiary = NavyLight,
    background = NavyDark,
    surface = NavyDark,
    onPrimary = NavyDark,
    onSecondary = NavyDark,
    onSurface = Color.White,
    onBackground = Color.White,
    error = Color(0xFFFF5252)
)

@Composable
fun FYP25S423Theme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NavyDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DeepfakeDarkColorScheme,
        typography = Typography,
        content = content
    )
}