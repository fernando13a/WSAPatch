package com.ironmind.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * IronMind is a dark-only, pure-black experience. Gold is the primary accent, cyan the
 * secondary — see the UI/UX brief (glassmorphism over #000000).
 */
private val IronMindColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    secondary = Cyan,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = GlassFill,
    onSurfaceVariant = TextMuted,
    outline = GlassBorder,
)

@Composable
fun IronMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IronMindColorScheme,
        typography = Typography,
        shapes = IronMindShapes,
        content = content,
    )
}
