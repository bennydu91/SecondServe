package com.secondserve.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

// Design system "Broadcast" — même identité que le téléphone (cf. SecondServe Design System.dc.html
// et :core:ui/theme/Theme.kt) : fond quasi noir, accent lime unique, Barlow Semi Condensed pour
// les scores/labels, Space Grotesk pour l'interface.
private val WearColorScheme = ColorScheme(
    primary = BroadcastColors.lime,
    primaryDim = BroadcastColors.limeDim,
    onPrimary = BroadcastColors.onLime,
    primaryContainer = BroadcastColors.lime.copy(alpha = 0.14f),
    onPrimaryContainer = BroadcastColors.lime,

    secondary = BroadcastColors.data,
    secondaryDim = BroadcastColors.dataDim,
    onSecondary = BroadcastColors.void,
    secondaryContainer = BroadcastColors.panelHigh,
    onSecondaryContainer = BroadcastColors.data,

    tertiary = BroadcastColors.data,
    tertiaryDim = BroadcastColors.dataDim,
    onTertiary = BroadcastColors.void,
    tertiaryContainer = BroadcastColors.panelHigh,
    onTertiaryContainer = BroadcastColors.data,

    surfaceContainerLow = BroadcastColors.void,
    surfaceContainer = BroadcastColors.panel,
    surfaceContainerHigh = BroadcastColors.panelHigh,
    onSurface = BroadcastColors.text,
    onSurfaceVariant = BroadcastColors.muted,

    outline = BroadcastColors.line,
    outlineVariant = BroadcastColors.line,

    background = BroadcastColors.void,
    onBackground = BroadcastColors.text,

    error = BroadcastColors.hot,
    errorDim = BroadcastColors.hotDim,
    onError = BroadcastColors.void,
    errorContainer = BroadcastColors.defeatBg,
    onErrorContainer = BroadcastColors.hot
)

private val WearTypography = Typography(
    bodyLarge = Typography().bodyLarge.copy(fontFamily = SpaceGrotesk),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = SpaceGrotesk),
    bodySmall = Typography().bodySmall.copy(fontFamily = SpaceGrotesk),
    labelLarge = Typography().labelLarge.copy(fontFamily = SpaceGrotesk),
    labelMedium = Typography().labelMedium.copy(fontFamily = SpaceGrotesk),
    labelSmall = Typography().labelSmall.copy(fontFamily = SpaceGrotesk),
    displayLarge = Typography().displayLarge.copy(fontFamily = BarlowSemiCondensed),
    displayMedium = Typography().displayMedium.copy(fontFamily = BarlowSemiCondensed),
    displaySmall = Typography().displaySmall.copy(fontFamily = BarlowSemiCondensed),
    titleLarge = Typography().titleLarge.copy(fontFamily = BarlowSemiCondensed),
    titleMedium = Typography().titleMedium.copy(fontFamily = BarlowSemiCondensed),
    titleSmall = Typography().titleSmall.copy(fontFamily = BarlowSemiCondensed)
)

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        typography = WearTypography,
        content = content
    )
}
