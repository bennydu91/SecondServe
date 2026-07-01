package com.secondserve.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.StatBar
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
import com.secondserve.domain.model.SurfaceConstants
import kotlin.math.roundToInt
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current

    when (val s = state) {
        is StatsUiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = colors.lime) }

        is StatsUiState.Error -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = s.message,
                modifier = Modifier.padding(16.dp),
                color = colors.hot
            )
        }

        is StatsUiState.Content -> StatsContent(stats = s.stats)
    }
}

@Composable
private fun StatsContent(stats: AggregatedStats) {
    val colors = LocalBroadcastColors.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Statistiques",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item { WinRateCard(stats = stats) }
        item { SurfaceStatsCard(stats = stats) }
        item { BottomTilesRow(stats = stats) }
    }
}

@Composable
private fun WinRateCard(stats: AggregatedStats) {
    val colors = LocalBroadcastColors.current
    StatsCard(title = "WIN RATE GLOBAL", icon = "🏆") {
        if (stats.winRateGlobal != null) {
            val percent = (stats.winRateGlobal * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp, fontFeatureSettings = "tnum"),
                    color = colors.lime
                )
                Text(
                    text = "${stats.victories} V · ${stats.completedMatchSessions - stats.victories} D · ${stats.completedMatchSessions} matchs",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            StatBar(progress = stats.winRateGlobal, color = colors.lime, height = 10.dp)
        } else {
            Text(
                text = "Aucun match terminé pour l'instant.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun SurfaceStatsCard(stats: AggregatedStats) {
    val colors = LocalBroadcastColors.current
    StatsCard(title = "PAR SURFACE") {
        if (stats.winRateBySurface.isEmpty()) {
            Text(
                text = "Aucune donnée de surface disponible.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                stats.winRateBySurface.forEach { surfaceStat ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = SurfaceConstants.DISPLAY_NAMES[surfaceStat.surface] ?: surfaceStat.surface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.text
                            )
                            Text(
                                text = if (surfaceStat.winRate != null) {
                                    "${(surfaceStat.winRate * 100).roundToInt()}%  ·  ${surfaceStat.matchCount} matchs"
                                } else {
                                    "Données insuffisantes"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        if (surfaceStat.winRate != null) {
                            StatBar(
                                progress = surfaceStat.winRate,
                                color = colors.forSurfaceKey(surfaceStat.surface)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTilesRow(stats: AggregatedStats) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsCard(title = "🔥 Séquence", modifier = Modifier.weight(1f)) {
            when (val streak = stats.activeStreak) {
                is ActiveStreak.Victories -> Column {
                    Text(
                        text = "${streak.count}",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
                        color = colors.lime
                    )
                    Text(text = "victoires", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                is ActiveStreak.Defeats -> Column {
                    Text(
                        text = "${streak.count}",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
                        color = colors.hot
                    )
                    Text(text = "défaites", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                null -> Text(
                    text = "Aucune séquence",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
        StatsCard(title = "Volume", modifier = Modifier.weight(1f)) {
            Column {
                Text(
                    text = "${stats.totalMatchSessions + stats.totalTrainingSessions}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
                    color = colors.text
                )
                Text(
                    text = "${stats.totalMatchSessions} matchs · ${stats.totalTrainingSessions} entr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.panelHigh
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
