package com.secondserve.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.LocalBroadcastColors

/** Une ligne du tableau gamecast : un joueur, ses sets complétés, son jeu courant et ses points. */
data class GamecastPlayerRow(
    val name: String,
    val isYou: Boolean,
    val setScores: List<Int?>,
    val currentGames: Int,
    val leadingInCurrentSet: Boolean,
    val points: String
)

/**
 * Tableau broadcast (JOUEUR / S1 / S2 / JEUX / PTS), une ligne par joueur — cf. écran
 * "Match live · gamecast" du handoff. Toujours 2 colonnes de sets (best-of-3 max).
 */
@Composable
fun GamecastTable(
    rows: List<GamecastPlayerRow>,
    modifier: Modifier = Modifier
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.table),
        color = colors.panelHigh
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "JOUEUR",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.faint
                )
                HeaderCell(text = "S1", color = colors.faint)
                HeaderCell(text = "S2", color = colors.faint)
                HeaderCell(text = "JEUX", color = colors.lime, width = 48.dp)
                HeaderCell(text = "PTS", color = colors.data, width = 46.dp)
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.line
                    )
                }
                PlayerRow(row = row)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, color: Color, width: Dp = 34.dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun PlayerRow(row: GamecastPlayerRow) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (row.isYou) colors.text else colors.text.copy(alpha = 0.75f)
        )
        repeat(2) { i ->
            val value = row.setScores.getOrNull(i)
            Text(
                text = value?.toString() ?: "–",
                modifier = Modifier.width(34.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"),
                color = colors.faint
            )
        }
        Text(
            text = row.currentGames.toString(),
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
            color = if (row.leadingInCurrentSet) colors.lime else colors.text
        )
        Text(
            text = row.points,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"),
            color = colors.data
        )
    }
}
