package com.secondserve.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Palette tennis — vert émeraude (herbe), jaune (balle), bleu (court dur)
private val TennisGreen = Color(0xFF52D68A)
private val TennisGreenDark = Color(0xFF006B36)
private val TennisYellow = Color(0xFFE8C73E)
private val TennisBlueCourt = Color(0xFF7BCFFB)

private val DarkColorScheme = darkColorScheme(
    primary = TennisGreen,
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF7FFDB5),

    secondary = TennisYellow,
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF554400),
    onSecondaryContainer = Color(0xFFFFE178),

    tertiary = TennisBlueCourt,
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF004D6A),
    onTertiaryContainer = Color(0xFFBDE9FF),

    error = Color(0xFFFFB3BA),
    onError = Color(0xFF680020),
    errorContainer = Color(0xFF93002F),
    onErrorContainer = Color(0xFFFFDADE),

    background = Color(0xFF0B160E),
    onBackground = Color(0xFFDCE9DC),
    surface = Color(0xFF0B160E),
    onSurface = Color(0xFFDCE9DC),
    surfaceVariant = Color(0xFF1C2B1E),
    onSurfaceVariant = Color(0xFF9EB5A1),

    outline = Color(0xFF45644A),
    outlineVariant = Color(0xFF2B3D2E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDCE9DC),
    inverseOnSurface = Color(0xFF192B1B),
    inversePrimary = TennisGreenDark
)

private val LightColorScheme = lightColorScheme(
    primary = TennisGreenDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7FFDB5),
    onPrimaryContainer = Color(0xFF00210D),

    secondary = Color(0xFF6A5C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE178),
    onSecondaryContainer = Color(0xFF201A00),

    tertiary = Color(0xFF00677F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDE9FF),
    onTertiaryContainer = Color(0xFF001F2A),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADE),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF5FBF2),
    onBackground = Color(0xFF161D17),
    surface = Color(0xFFF5FBF2),
    onSurface = Color(0xFF161D17),
    surfaceVariant = Color(0xFFD6E8D8),
    onSurfaceVariant = Color(0xFF3D5240),

    outline = Color(0xFF6D8970),
    outlineVariant = Color(0xFFBACABC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C3229),
    inverseOnSurface = Color(0xFFECF3EC),
    inversePrimary = TennisGreen
)

@Composable
fun SecondServeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecondServeTypography,
        content = content
    )
}
