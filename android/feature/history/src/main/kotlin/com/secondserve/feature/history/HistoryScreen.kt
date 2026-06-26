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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SurfaceConstants
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionDateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAddRetroSession: () -> Unit,
    onNavigateToStats: (() -> Unit)? = null,
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
                title = {
                    Text(
                        "Historique",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (onNavigateToStats != null) {
                        IconButton(onClick = onNavigateToStats) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = "Statistiques"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddRetroSession,
                modifier = Modifier.semantics { contentDescription = "Ajouter un match passé" }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Ajouter")
            }
        }
    ) { padding ->
        when (val s = state) {
            is HistoryUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is HistoryUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            is HistoryUiState.Content -> {
                if (s.sessions.isEmpty()) {
                    EmptyHistoryState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(s.sessions) { session ->
                            SessionItem(
                                session = session,
                                onClick = { viewModel.onSessionClicked(session.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "📋", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Aucune session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tes matchs et entraînements apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SessionItem(session: Session, onClick: () -> Unit) {
    val isVictory = session.result == "VICTORY"
    val isDefeat = session.result == "DEFEAT"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Date + surface
                Text(
                    text = buildString {
                        append(session.formattedDate())
                        val surface = SurfaceConstants.DISPLAY_NAMES[session.surface] ?: session.surface
                        if (surface.isNotBlank()) append("  ·  $surface")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Score
                Text(
                    text = session.scoreText ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Adversaire ou contexte
                val subtitle = buildString {
                    session.opponent?.let { append("vs $it") }
                    session.competitionType?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append(it)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge résultat ou statut
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    isVictory -> ResultBadge(text = "V", isPositive = true)
                    isDefeat -> ResultBadge(text = "D", isPositive = false)
                }
                val statusLabel = session.statusBadge()
                if (statusLabel != null) {
                    Badge { Text(statusLabel) }
                }
            }
        }
    }
}

@Composable
private fun ResultBadge(text: String, isPositive: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isPositive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun Session.statusBadge(): String? = when (status) {
    SessionStatus.ACTIVE -> "En cours"
    SessionStatus.INTERRUPTED -> "Interrompue"
    SessionStatus.PLANNED -> "Planifié"
    SessionStatus.CANCELLED -> "Annulé"
    SessionStatus.COMPLETED -> null
}

private fun Session.formattedDate(): String = formatDate(createdAt, sessionDateFormat)
