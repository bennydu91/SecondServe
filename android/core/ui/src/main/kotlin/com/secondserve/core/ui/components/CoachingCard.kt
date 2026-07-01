package com.secondserve.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Carte "conseil coaching" : bordure gauche 3dp lime + icône ✦.
 * Réutilisée par Match live, Détail de session, ... (cf. README "Composants récurrents").
 */
@Composable
fun CoachingCard(
    label: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.card - 2.dp),
        color = colors.panelHigh
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(colors.lime)
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "✦", color = colors.lime, style = MaterialTheme.typography.titleLarge)
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
