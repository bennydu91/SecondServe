package com.secondserve.feature.coaching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.secondserve.domain.model.CoachingAnalysis
import org.orbitmvi.orbit.compose.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val synthDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachingScreen(
    onNavigateBack: () -> Unit,
    viewModel: CoachingViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coaching IA") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("← Retour") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Synthèse multi-matchs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                item {
                    state.synthesis?.let { synth ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(synth.content, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                val dateStr = if (synth.generatedAt > 0L) synthDateFormat.format(Date(synth.generatedAt)) else "date inconnue"
                                Text(
                                    "${synth.sessionCount} matchs · $dateStr",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    } ?: Text(
                        "Aucune synthèse disponible. Générez-en une ci-dessous.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    if (state.synthesisInProgress) {
                        CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.generateNow() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Générer maintenant") }
                    }
                }
                state.error?.let { err ->
                    item {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (state.analyses.isNotEmpty()) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Analyses post-match", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.analyses) { analysis ->
                        AnalysisItem(analysis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisItem(analysis: CoachingAnalysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(analysis.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            val dateStr = if (analysis.generatedAt > 0L) synthDateFormat.format(Date(analysis.generatedAt)) else "date inconnue"
            Text(
                dateStr,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
