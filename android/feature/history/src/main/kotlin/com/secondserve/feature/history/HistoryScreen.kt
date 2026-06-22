package com.secondserve.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
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
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is HistorySideEffect.NavigateToDetail -> onNavigateToDetail(effect.sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("← Retour")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is HistoryUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            is HistoryUiState.Error -> Text(
                text = s.message,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            is HistoryUiState.Content -> {
                if (s.sessions.isEmpty()) {
                    Text(
                        text = "Aucune session enregistrée",
                        modifier = Modifier.padding(padding).padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.sessions) { session ->
                            SessionItem(
                                session = session,
                                onClick = { viewModel.onSessionClicked(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(session: Session, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.formattedDate(),
                    style = MaterialTheme.typography.bodyMedium
                )
                val badge = session.statusBadge()
                if (badge != null) {
                    Badge { Text(badge) }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.surface,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.scoreText ?: "—",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = session.resultLabel(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            session.opponent?.let {
                Text(
                    text = "vs $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            session.competitionType?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun Session.statusBadge(): String? = when (status) {
    SessionStatus.ACTIVE -> "En cours"
    SessionStatus.INTERRUPTED -> "Interrompue"
    SessionStatus.COMPLETED -> null
}

private fun Session.formattedDate(): String =
    sessionDateFormat.format(Date(createdAt))
