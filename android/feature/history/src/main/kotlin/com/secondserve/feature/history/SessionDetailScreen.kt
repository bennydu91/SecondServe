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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is SessionDetailSideEffect.SessionDeleted -> onNavigateBack()
            is SessionDetailSideEffect.ShowError -> coroutineScope.launch {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Annuler ce match ?") },
            text = { Text("Cette action supprimera le match planifié et annulera le rappel.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onDeletePlanned = { showDeleteConfirm = true },
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
    onDeletePlanned: () -> Unit,
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
                    DetailRow("Date", formatDate(session.createdAt, sessionDetailDateFormat))
                    DetailRow("Surface", session.surface)
                    DetailRow("Format", session.format.matchFormat.name)
                    session.opponent?.let { DetailRow("Adversaire", it) }
                    session.competitionType?.let { DetailRow("Compétition", it) }
                    session.tournament?.let { DetailRow("Tournoi", it) }
                    DetailRow("Score", session.scoreText ?: "—")
                    DetailRow("Résultat", session.resultLabel())
                    session.feelingRating?.let { DetailRow("Ressenti", "$it/5") }
                    session.feelingComment?.let { DetailRow("Commentaire", it) }
                    session.scheduledAt?.let {
                        DetailRow("Match planifié", formatDate(it, sessionDetailDateFormat))
                    }
                }
            }
        }

        if (session.status == SessionStatus.PLANNED) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDeletePlanned,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Annuler ce match planifié")
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
