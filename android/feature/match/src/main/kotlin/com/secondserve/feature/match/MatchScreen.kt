package com.secondserve.feature.match

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondserve.core.ui.components.CoachingCard
import com.secondserve.core.ui.components.GamecastPlayerRow
import com.secondserve.core.ui.components.GamecastTable
import com.secondserve.core.ui.components.LiveChip
import com.secondserve.core.ui.components.StatBar
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import kotlinx.coroutines.delay
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
    val colors = LocalBroadcastColors.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is MatchSideEffect.SessionClosed -> onSessionClosed()
            is MatchSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val opponentName = state.opponentName ?: "Adversaire"
            val setNumber = (score?.completedSets?.size ?: 0) + 1
            val elapsedMinutes = if (state.sessionStartedAt > 0) {
                ((nowMillis - state.sessionStartedAt) / 60_000L).coerceAtLeast(0L)
            } else null

            // Header : LIVE + "Set n · mm min" + fermeture
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveChip()
                Text(
                    text = buildString {
                        append("Set $setNumber")
                        if (elapsedMinutes != null) append(" · $elapsedMinutes min")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(colors.panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = viewModel::onCloseRequested, enabled = !state.isClosing) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Terminer la session",
                            tint = colors.muted
                        )
                    }
                }
            }

            score?.let { liveScore ->
                val (pointsA, pointsB) = liveScore.currentPointsDisplay()
                GamecastTable(
                    rows = listOf(
                        GamecastPlayerRow(
                            name = "Vous",
                            isYou = true,
                            setScores = liveScore.completedSets.map { it.gamesA },
                            currentGames = liveScore.currentSetGamesA,
                            leadingInCurrentSet = liveScore.currentSetGamesA > liveScore.currentSetGamesB,
                            points = pointsA
                        ),
                        GamecastPlayerRow(
                            name = opponentName,
                            isYou = false,
                            setScores = liveScore.completedSets.map { it.gamesB },
                            currentGames = liveScore.currentSetGamesB,
                            leadingInCurrentSet = liveScore.currentSetGamesB > liveScore.currentSetGamesA,
                            points = pointsB
                        )
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "DÉROULÉ · SET $setNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.faint
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.currentSetGameLog.forEach { winner ->
                            GameResultBox(won = winner == Player.A, modifier = Modifier.weight(1f))
                        }
                        if (!liveScore.isMatchOver) {
                            GameResultBox(won = null, modifier = Modifier.weight(1f))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Momentum",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.muted
                        )
                        StatBar(
                            progress = state.momentumPercent / 100f,
                            color = colors.lime,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${state.momentumPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.lime
                        )
                    }
                }
            }

            key(state.coachingAdviceSeq) {
                AnimatedVisibility(
                    visibleState = remember { MutableTransitionState(false) }
                        .apply { targetState = state.coachingAdvice != null },
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    state.coachingAdvice?.let { advice ->
                        CoachingCard(label = "Conseil coaching", text = advice.text)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
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
private fun GameResultBox(won: Boolean?, modifier: Modifier = Modifier) {
    val colors = LocalBroadcastColors.current
    when (won) {
        true -> Box(
            modifier = modifier
                .height(28.dp)
                .background(colors.lime.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "V", style = MaterialTheme.typography.labelMedium, color = colors.lime)
        }
        false -> Box(
            modifier = modifier
                .height(28.dp)
                .background(colors.panelHigh, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "E", style = MaterialTheme.typography.labelMedium, color = colors.faint)
        }
        null -> Box(
            modifier = modifier.height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "•", style = MaterialTheme.typography.labelMedium, color = colors.faint)
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
                                    MaterialTheme.colorScheme.secondary
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
            Button(onClick = onConfirm) { Text("Confirmer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
