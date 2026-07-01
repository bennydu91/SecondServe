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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.CircleIconButton
import com.secondserve.core.ui.components.MatchListItem
import com.secondserve.core.ui.components.MatchResultBadge
import com.secondserve.core.ui.components.SurfaceChip
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.SurfaceConstants
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.FRANCE)

private enum class HistoryFilter(val label: String) {
    ALL("Tous"),
    MATCHES("Matchs"),
    TRAININGS("Entraînements")
}

@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAddRetroSession: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is HistorySideEffect.NavigateToDetail -> onNavigateToDetail(effect.sessionId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = BroadcastSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historique",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
            CircleIconButton(
                onClick = onNavigateToAddRetroSession,
                icon = Icons.Filled.Add,
                contentDescription = "Ajouter un match passé"
            )
        }

        when (val s = state) {
            is HistoryUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.lime)
            }

            is HistoryUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.message,
                    modifier = Modifier.padding(BroadcastSpacing.lg),
                    color = colors.hot
                )
            }

            is HistoryUiState.Content -> {
                val filtered = when (filter) {
                    HistoryFilter.ALL -> s.sessions
                    HistoryFilter.MATCHES -> s.sessions.filter { it.sessionType == SessionType.MATCH }
                    HistoryFilter.TRAININGS -> s.sessions.filter { it.sessionType == SessionType.TRAINING }
                }
                if (filtered.isEmpty()) {
                    EmptyHistoryState(modifier = Modifier.fillMaxSize())
                } else {
                    val grouped = filtered.groupBy { monthFormat.format(Date(it.createdAt)).replaceFirstChar { c -> c.uppercase() } }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)
                            ) {
                                HistoryFilter.entries.forEach { f ->
                                    SurfaceChip(
                                        label = f.label,
                                        color = colors.lime,
                                        selected = filter == f,
                                        onClick = { filter = f }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(BroadcastSpacing.xs))
                        }
                        grouped.forEach { (month, sessions) ->
                            item {
                                Text(
                                    text = month.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.faint,
                                    modifier = Modifier.padding(vertical = BroadcastSpacing.xs)
                                )
                            }
                            items(sessions) { session ->
                                HistorySessionItem(
                                    session = session,
                                    onClick = { viewModel.onSessionClicked(session.id) }
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(BroadcastSpacing.sm)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    val colors = LocalBroadcastColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "📋", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Aucune session",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                text = "Tes matchs et entraînements apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HistorySessionItem(session: Session, onClick: () -> Unit) {
    val colors = LocalBroadcastColors.current
    val isTraining = session.sessionType == SessionType.TRAINING
    val meta = buildString {
        append(SurfaceConstants.DISPLAY_NAMES[session.surface] ?: session.surface)
        if (isTraining) {
            append(" · ")
            append(session.formattedDate())
        } else {
            session.opponent?.let { append(" · vs $it") }
            append(" · ")
            append(session.formattedDate())
        }
    }
    MatchListItem(
        accentColor = if (isTraining) colors.indoor else colors.forSurfaceKey(session.surface),
        scoreOrTitle = if (isTraining) "Entraînement" else session.scoreText ?: "—",
        meta = meta,
        badge = when {
            isTraining -> MatchResultBadge.NONE
            session.result == "VICTORY" -> MatchResultBadge.VICTORY
            session.result == "DEFEAT" -> MatchResultBadge.DEFEAT
            else -> MatchResultBadge.NONE
        },
        badgeLabel = if (isTraining) "ENTR." else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

private fun Session.formattedDate(): String = formatDate(createdAt, SimpleDateFormat("d MMM", Locale.FRANCE))
