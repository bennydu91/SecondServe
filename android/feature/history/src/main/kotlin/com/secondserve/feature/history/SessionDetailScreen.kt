package com.secondserve.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.BroadcastTextField
import com.secondserve.core.ui.components.CircleIconButton
import com.secondserve.core.ui.components.CoachingCard
import com.secondserve.core.ui.components.DualStatBar
import com.secondserve.core.ui.components.ResultBadge
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SurfaceConstants
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionDetailDateFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.FRANCE)

@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current
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
                    colors = ButtonDefaults.buttonColors(containerColor = colors.hot)
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

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(snackbarHostState)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = BroadcastSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircleIconButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour"
            )
            val title = (state as? SessionDetailUiState.Content)?.session?.opponent?.let { "vs $it" }
                ?: "Détail de la session"
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }

        when (val s = state) {
            is SessionDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.lime)
            }
            is SessionDetailUiState.Error -> Text(
                text = s.message,
                color = colors.hot,
                modifier = Modifier.padding(18.dp)
            )
            is SessionDetailUiState.Content -> SessionDetailContent(
                session = s.session,
                advices = s.advices,
                analysis = s.analysis,
                onDeletePlanned = { showDeleteConfirm = true },
                onSaveMatchStats = viewModel::updateMatchStats
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
    onSaveMatchStats: (Int?, Int?, Int?, Int?) -> Unit
) {
    val colors = LocalBroadcastColors.current
    val isVictory = session.result == "VICTORY"
    val isDefeat = session.result == "DEFEAT"
    var showStatsEditor by remember { mutableStateOf(false) }

    if (showStatsEditor) {
        MatchStatsEditorDialog(
            session = session,
            onDismiss = { showStatsEditor = false },
            onSave = { a, b, c, d ->
                onSaveMatchStats(a, b, c, d)
                showStatsEditor = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(BroadcastRadius.table),
                color = colors.panelHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        isVictory -> ResultBadge(
                            label = "VICTOIRE",
                            background = colors.victoryBg,
                            foreground = colors.lime,
                            shape = RoundedCornerShape(BroadcastRadius.pill),
                            contentPadding = 12.dp
                        )
                        isDefeat -> ResultBadge(
                            label = "DÉFAITE",
                            background = colors.defeatBg,
                            foreground = colors.hot,
                            shape = RoundedCornerShape(BroadcastRadius.pill),
                            contentPadding = 12.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = session.scoreText ?: "—",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, fontFeatureSettings = "tnum"),
                        color = colors.lime
                    )
                    Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
                    Text(
                        text = buildString {
                            append(SurfaceConstants.DISPLAY_NAMES[session.surface] ?: session.surface)
                            session.tournament?.let { append(" · $it") }
                            append(" · ")
                            append(formatDate(session.createdAt, sessionDetailDateFormat))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            }
        }

        if (session.status == SessionStatus.PLANNED) {
            item {
                OutlinedButton(
                    onClick = onDeletePlanned,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.hot)
                ) {
                    Text("Annuler ce match planifié")
                }
            }
        }

        if (session.status == SessionStatus.COMPLETED) {
            item {
                MatchStatsSection(session = session, onEditClick = { showStatsEditor = true })
            }
        }

        if (session.feelingRating != null || session.feelingComment != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BroadcastRadius.card),
                    color = colors.panelHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Ressenti", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            session.feelingRating?.let { rating ->
                                Text(
                                    text = "★".repeat(rating) + "☆".repeat(5 - rating),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.lime
                                )
                            }
                        }
                        session.feelingComment?.let {
                            Text(
                                text = "« $it »",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                modifier = Modifier.padding(start = BroadcastSpacing.md)
                            )
                        }
                    }
                }
            }
        }

        if (analysis != null) {
            item {
                CoachingCard(label = "Analyse post-match", text = analysis.content)
            }
        }

        if (advices.isNotEmpty()) {
            item {
                Text(
                    text = "CONSEILS COACHING",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.faint
                )
            }
            items(advices) { advice ->
                CoachingCard(label = advice.pattern.name, text = advice.content)
            }
        }
    }
}

@Composable
private fun MatchStatsSection(session: Session, onEditClick: () -> Unit) {
    val colors = LocalBroadcastColors.current
    val hasStats = session.firstServePercentSelf != null || session.firstServePercentOpponent != null ||
        session.winnersSelf != null || session.winnersOpponent != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.panelHigh
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "STATISTIQUES DU MATCH", style = MaterialTheme.typography.labelSmall, color = colors.faint)
                TextButton(onClick = onEditClick) {
                    Text(if (hasStats) "Modifier" else "Ajouter", color = colors.lime)
                }
            }
            if (hasStats) {
                Spacer(modifier = Modifier.height(BroadcastSpacing.md))
                if (session.firstServePercentSelf != null || session.firstServePercentOpponent != null) {
                    StatRow(
                        label = "1re balle",
                        valueA = session.firstServePercentSelf,
                        valueB = session.firstServePercentOpponent,
                        suffix = "%"
                    )
                    Spacer(modifier = Modifier.height(BroadcastSpacing.md))
                }
                if (session.winnersSelf != null || session.winnersOpponent != null) {
                    StatRow(
                        label = "Coups gagnants",
                        valueA = session.winnersSelf,
                        valueB = session.winnersOpponent,
                        suffix = ""
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, valueA: Int?, valueB: Int?, suffix: String) {
    val colors = LocalBroadcastColors.current
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${valueA ?: 0}$suffix", style = MaterialTheme.typography.bodyMedium, color = colors.lime)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(text = "${valueB ?: 0}$suffix", style = MaterialTheme.typography.bodyMedium, color = colors.data)
        }
        Spacer(modifier = Modifier.height(6.dp))
        DualStatBar(
            valueA = (valueA ?: 0).toFloat(),
            valueB = (valueB ?: 0).toFloat(),
            colorA = colors.lime,
            colorB = colors.data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MatchStatsEditorDialog(
    session: Session,
    onDismiss: () -> Unit,
    onSave: (Int?, Int?, Int?, Int?) -> Unit
) {
    val colors = LocalBroadcastColors.current
    var firstServeSelf by remember { mutableStateOf(session.firstServePercentSelf?.toString() ?: "") }
    var firstServeOpponent by remember { mutableStateOf(session.firstServePercentOpponent?.toString() ?: "") }
    var winnersSelf by remember { mutableStateOf(session.winnersSelf?.toString() ?: "") }
    var winnersOpponent by remember { mutableStateOf(session.winnersOpponent?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statistiques du match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BroadcastTextField(
                    value = firstServeSelf,
                    onValueChange = { firstServeSelf = it.filter { c -> c.isDigit() } },
                    label = "1re balle % (vous)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                BroadcastTextField(
                    value = firstServeOpponent,
                    onValueChange = { firstServeOpponent = it.filter { c -> c.isDigit() } },
                    label = "1re balle % (adversaire)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                BroadcastTextField(
                    value = winnersSelf,
                    onValueChange = { winnersSelf = it.filter { c -> c.isDigit() } },
                    label = "Coups gagnants (vous)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                BroadcastTextField(
                    value = winnersOpponent,
                    onValueChange = { winnersOpponent = it.filter { c -> c.isDigit() } },
                    label = "Coups gagnants (adversaire)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        firstServeSelf.toIntOrNull(),
                        firstServeOpponent.toIntOrNull(),
                        winnersSelf.toIntOrNull(),
                        winnersOpponent.toIntOrNull()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.lime, contentColor = colors.void)
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
