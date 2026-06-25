package com.secondserve.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WearColorScheme = ColorScheme(
    primary = Color(0xFF52D68A),
    primaryDim = Color(0xFF3AB070),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF7FFDB5),

    secondary = Color(0xFFE8C73E),
    secondaryDim = Color(0xFFB89D2E),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF554400),
    onSecondaryContainer = Color(0xFFFFE178),

    tertiary = Color(0xFF7BCFFB),
    tertiaryDim = Color(0xFF5BA0C8),
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF004D6A),
    onTertiaryContainer = Color(0xFFBDE9FF),

    surfaceContainerLow = Color(0xFF080F0A),
    surfaceContainer = Color(0xFF0B160E),
    surfaceContainerHigh = Color(0xFF1C2B1E),
    onSurface = Color(0xFFDCE9DC),
    onSurfaceVariant = Color(0xFF9EB5A1),

    outline = Color(0xFF45644A),
    outlineVariant = Color(0xFF2B3D2E),

    background = Color(0xFF0B160E),
    onBackground = Color(0xFFDCE9DC),

    error = Color(0xFFFFB3BA),
    errorDim = Color(0xFFCC7070),
    onError = Color(0xFF680020),
    errorContainer = Color(0xFF93002F),
    onErrorContainer = Color(0xFFFFDADE)
)

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content
    )
}
