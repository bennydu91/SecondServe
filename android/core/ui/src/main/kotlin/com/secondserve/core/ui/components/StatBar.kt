package com.secondserve.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Barre comparative horizontale — jamais de camembert (cf. README "Composants récurrents").
 * Un seul segment coloré représentant [progress] (0f..1f), le reste en couleur neutre.
 */
@Composable
fun StatBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val colors = LocalBroadcastColors.current
    val clamped = progress.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.panelHigh)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(color)
            )
        }
    }
}

/**
 * Barre comparative duale : deux segments proportionnels à [valueA]/[valueB] (jamais un camembert).
 * Utilisée pour les stats face-à-face (1re balle, coups gagnants, ...).
 */
@Composable
fun DualStatBar(
    valueA: Float,
    valueB: Float,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val total = (valueA + valueB).takeIf { it > 0f } ?: 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight((valueA / total).coerceAtLeast(0.001f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colorA)
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight((valueB / total).coerceAtLeast(0.001f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(100.dp))
                .background(colorB)
        )
    }
}
