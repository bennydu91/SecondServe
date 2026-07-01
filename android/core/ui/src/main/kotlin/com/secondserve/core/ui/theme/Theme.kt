package com.secondserve.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Design system "Broadcast" — dark-first, verrouillé (pas de dynamic color, pas de variante
// light mobile : cf. design_handoff_secondserve/README.md). Le mapping M3 sert uniquement à
// donner des couleurs par défaut cohérentes aux composants Material (Button, Card, ...) ;
// les écrans qui suivent fidèlement les tokens du handoff doivent lire LocalBroadcastColors.
private val BroadcastColorScheme = darkColorScheme(
    primary = BroadcastDarkColors.lime,
    onPrimary = BroadcastDarkColors.onLime,
    primaryContainer = BroadcastDarkColors.victoryBg,
    onPrimaryContainer = BroadcastDarkColors.lime,

    secondary = BroadcastDarkColors.data,
    onSecondary = BroadcastDarkColors.void,
    secondaryContainer = BroadcastDarkColors.panelHigh,
    onSecondaryContainer = BroadcastDarkColors.data,

    tertiary = BroadcastDarkColors.data,
    onTertiary = BroadcastDarkColors.void,
    tertiaryContainer = BroadcastDarkColors.panelHigh,
    onTertiaryContainer = BroadcastDarkColors.data,

    error = BroadcastDarkColors.hot,
    onError = BroadcastDarkColors.void,
    errorContainer = BroadcastDarkColors.defeatBg,
    onErrorContainer = BroadcastDarkColors.hot,

    background = BroadcastDarkColors.void,
    onBackground = BroadcastDarkColors.text,
    surface = BroadcastDarkColors.void,
    onSurface = BroadcastDarkColors.text,
    surfaceVariant = BroadcastDarkColors.panelHigh,
    onSurfaceVariant = BroadcastDarkColors.muted,

    outline = BroadcastDarkColors.line,
    outlineVariant = BroadcastDarkColors.line,
    scrim = Color(0xFF000000),
    inverseSurface = BroadcastDarkColors.text,
    inverseOnSurface = BroadcastDarkColors.void,
    inversePrimary = BroadcastDarkColors.onLime
)

@Composable
fun SecondServeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBroadcastColors provides BroadcastDarkColors) {
        MaterialTheme(
            colorScheme = BroadcastColorScheme,
            typography = SecondServeTypography,
            content = content
        )
    }
}
