package com.secondserve.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Pill de sélection de surface : pleine couleur quand sélectionné, contour neutre sinon.
 * Cf. README "Composants récurrents" — "Chip surface : pill couleur de surface".
 */
@Composable
fun SurfaceChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) color else colors.panel,
        border = if (selected) null else BorderStroke(1.dp, colors.line)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.void else colors.text.copy(alpha = 0.8f)
        )
    }
}
