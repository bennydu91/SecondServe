package com.secondserve.feature.history

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
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.Session
import org.orbitmvi.orbit.compose.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionDetailDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail de la session") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("← Retour")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is SessionDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            is SessionDetailUiState.Error -> Text(
                text = s.message,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            is SessionDetailUiState.Content -> SessionDetailContent(
                session = s.session,
                advices = s.advices,
                analysis = s.analysis,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun SessionDetailContent(
    session: Session,
    advices: List<CoachingCacheEntry>,
    analysis: CoachingAnalysis?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Session", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Date", sessionDetailDateFormat.format(Date(session.createdAt)))
                    DetailRow("Surface", session.surface)
                    DetailRow("Format", session.format.matchFormat.name)
                    session.opponent?.let { DetailRow("Adversaire", it) }
                    session.competitionType?.let { DetailRow("Compétition", it) }
                    session.tournament?.let { DetailRow("Tournoi", it) }
                    DetailRow("Score", session.scoreText ?: "—")
                    DetailRow("Résultat", session.resultLabel())
                    session.feelingRating?.let { DetailRow("Ressenti", "$it/5") }
                    session.feelingComment?.let { DetailRow("Commentaire", it) }
                }
            }
        }

        if (analysis != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Analyse IA post-match", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(analysis.content, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Générée le ${sessionDetailDateFormat.format(Date(analysis.generatedAt))}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    "Analyse IA en cours de génération...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (advices.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Conseils coaching", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            items(advices) { advice ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = advice.pattern.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = advice.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
