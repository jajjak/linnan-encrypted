package com.linnan.encrypted.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Always dark: this app's identity is a black+gold "luxury" surface, independent of
// the system's light/dark setting.
private val LinnanColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = ObsidianBlack,
    primaryContainer = GoldMuted,
    onPrimaryContainer = IvoryText,
    secondary = GoldBright,
    onSecondary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = IvoryText,
    surface = CharcoalSurface,
    onSurface = IvoryText,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = SubtleText,
    error = ErrorRed,
    onError = ObsidianBlack
)

@Composable
fun LinnanEncryptedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ObsidianBlack.toArgb()
            window.navigationBarColor = ObsidianBlack.toArgb()
            WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = LinnanColorScheme,
        typography = Typography,
        content = content
    )
}
