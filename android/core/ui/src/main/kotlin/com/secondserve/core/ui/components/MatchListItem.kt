package com.secondserve.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondserve.core.ui.theme.LocalBroadcastColors

enum class MatchResultBadge { VICTORY, DEFEAT, NONE }

/**
 * Ligne de liste "match/entraînement" : barre couleur-surface + score Barlow + méta + badge V/D.
 * Réutilisée par l'Accueil (derniers matchs) et l'Historique — cf. README "Composants récurrents".
 */
@Composable
fun MatchListItem(
    accentColor: Color,
    scoreOrTitle: String,
    meta: String,
    badge: MatchResultBadge,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
    badgeColorOverride: Color? = null
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.panelHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scoreOrTitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = colors.text
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            when (badge) {
                MatchResultBadge.VICTORY -> ResultBadge(
                    label = badgeLabel ?: "V",
                    background = badgeColorOverride?.copy(alpha = 0.14f) ?: colors.victoryBg,
                    foreground = badgeColorOverride ?: colors.victoryText
                )
                MatchResultBadge.DEFEAT -> ResultBadge(
                    label = badgeLabel ?: "D",
                    background = badgeColorOverride?.copy(alpha = 0.14f) ?: colors.defeatBg,
                    foreground = badgeColorOverride ?: colors.defeatText
                )
                MatchResultBadge.NONE -> if (badgeLabel != null) {
                    ResultBadge(
                        label = badgeLabel,
                        background = badgeColorOverride?.copy(alpha = 0.14f) ?: colors.indoorBadgeBg,
                        foreground = badgeColorOverride ?: colors.indoor
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBadge(label: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground
        )
    }
}
