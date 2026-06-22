package com.secondserve.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import org.orbitmvi.orbit.compose.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("← Retour") }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is StatsUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is StatsUiState.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.message,
                    modifier = Modifier.padding(16.dp)
                )
            }

            is StatsUiState.Content -> StatsContent(
                stats = s.stats,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun StatsContent(stats: AggregatedStats, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            StatsCard(title = "Win rate global") {
                if (stats.winRateGlobal != null) {
                    Text("${stats.victories} victoires / ${stats.completedMatchSessions} matchs")
                    Text(
                        "${(stats.winRateGlobal * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    Text("Aucun match terminé")
                }
            }
        }

        item {
            StatsCard(title = "Par surface") {
                if (stats.winRateBySurface.isEmpty()) {
                    Text("Aucune donnée")
                } else {
                    stats.winRateBySurface.forEach { surfaceStat ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(surfaceStat.surface)
                            if (surfaceStat.winRate != null) {
                                Text("${(surfaceStat.winRate * 100).roundToInt()}% (${surfaceStat.matchCount} matchs)")
                            } else {
                                Text("Données insuffisantes")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item {
            StatsCard(title = "Séquence active") {
                when (val streak = stats.activeStreak) {
                    is ActiveStreak.Victories -> Text("${streak.count} victoire(s) consécutive(s)")
                    is ActiveStreak.Defeats -> Text("${streak.count} défaite(s) consécutive(s)")
                    null -> Text("Aucune séquence")
                }
            }
        }

        item {
            StatsCard(title = "Sessions") {
                Text("Matchs : ${stats.totalMatchSessions} | Entraînements : ${stats.totalTrainingSessions}")
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
