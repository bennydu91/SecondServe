package com.secondserve.feature.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun MatchScreen(
    onSessionClosed: () -> Unit,
    viewModel: MatchViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val score by viewModel.currentScore.collectAsStateWithLifecycle(initialValue = MatchScore())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is MatchSideEffect.SessionClosed -> onSessionClosed()
            is MatchSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Match en cours", style = MaterialTheme.typography.headlineMedium)

            score?.let { LiveScoreCard(it) }

            state.coachingAdvice?.let { advice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Conseil changement de côté",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = advice.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::onCloseRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isClosing
            ) {
                Text(if (state.isClosing) "Clôture en cours…" else "Clôturer la session")
            }
        }
    }

    if (state.showCloseDialog) {
        CloseSessionDialog(
            feelingRating = state.feelingRating,
            feelingComment = state.feelingComment,
            onRatingSelected = viewModel::onFeelingRatingSelected,
            onCommentChanged = viewModel::onFeelingCommentChanged,
            onConfirm = viewModel::confirmClose,
            onDismiss = viewModel::onCloseDialogDismissed
        )
    }
}

@Composable
private fun LiveScoreCard(score: MatchScore) {
    val setsA = score.completedSets.count { it.gamesA > it.gamesB }
    val setsB = score.completedSets.count { it.gamesB > it.gamesA }
    val (pointsA, pointsB) = score.currentPointsDisplay()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$setsA — $setsB",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = "${score.currentSetGamesA} — ${score.currentSetGamesB}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Jeux",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = "$pointsA — $pointsB",
                style = MaterialTheme.typography.titleLarge
            )
            val label = when {
                score.isSuperTieBreak -> "Super Tie-break"
                score.isTieBreak -> "Tie-break"
                score.isDeuce -> "Égalité"
                else -> "Points"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun MatchScore.currentPointsDisplay(): Pair<String, String> = when {
    isSuperTieBreak || isTieBreak -> Pair(tieBreakPointsA.toString(), tieBreakPointsB.toString())
    isDeuce -> Pair("Ég.", "Ég.")
    else -> Pair(currentGamePointsA.toDisplay(), currentGamePointsB.toDisplay())
}

private fun GamePoint.toDisplay(): String = when (this) {
    GamePoint.ZERO -> "0"
    GamePoint.FIFTEEN -> "15"
    GamePoint.THIRTY -> "30"
    GamePoint.FORTY -> "40"
    GamePoint.ADVANTAGE -> "Avt"
}

@Composable
private fun CloseSessionDialog(
    feelingRating: Int?,
    feelingComment: String,
    onRatingSelected: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clôturer la session ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cette action est définitive. Le score final sera enregistré.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Ressenti (optionnel)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { onRatingSelected(star) }) {
                            Text(
                                text = if ((feelingRating ?: 0) >= star) "★" else "☆",
                                fontSize = 20.sp,
                                color = if ((feelingRating ?: 0) >= star)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = feelingComment,
                    onValueChange = onCommentChanged,
                    label = { Text("Commentaire (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confirmer la clôture") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
