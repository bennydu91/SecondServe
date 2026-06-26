package com.secondserve.wear.presentation.match

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ScoreScreen(
    onClose: () -> Unit = {},
    viewModel: ScoreViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    var showCancelConfirm by remember { mutableStateOf(false) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ScoreSideEffect.Close -> onClose()
        }
    }

    when {
        showCancelConfirm -> CancelConfirmScreen(
            onConfirm = {
                viewModel.cancelMatchOver()
                showCancelConfirm = false
            },
            onDismiss = { showCancelConfirm = false }
        )
        state.score.isMatchOver -> MatchOverScreen(
            score = state.score,
            onCancelRequest = { showCancelConfirm = true },
            onCloseRequest = { viewModel.requestClose() }
        )
        else -> ScoreScreenContent(
            state = state,
            onPointA = { viewModel.recordPoint(Player.A) },
            onPointB = { viewModel.recordPoint(Player.B) },
            onUndo = { viewModel.undo() },
            phoneConnected = state.phoneConnected
        )
    }
}

@Composable
private fun MatchOverScreen(
    score: MatchScore,
    onCancelRequest: () -> Unit,
    onCloseRequest: () -> Unit
) {
    val setsA = score.completedSets.count { it.gamesA > it.gamesB }
    val setsB = score.completedSets.count { it.gamesB > it.gamesA }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Fin du match",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$setsA  —  $setsB",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "SETS",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onCloseRequest) {
                Text("Terminer", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onCancelRequest) {
                Text("Annuler", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CancelConfirmScreen(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Annuler le\ndernier point ?",
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Oui")
        }
        Spacer(modifier = Modifier.height(6.dp))
        FilledTonalButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Non")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoreScreenContent(
    state: ScoreUiState,
    onPointA: () -> Unit,
    onPointB: () -> Unit,
    onUndo: () -> Unit,
    phoneConnected: Boolean = true
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Zone de tap A (moitié gauche)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .combinedClickable(
                    onClick = { onPointA() },
                    onLongClick = { if (state.canUndo) onUndo() }
                )
        )

        // Zone de tap B (moitié droite)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .combinedClickable(
                    onClick = { onPointB() },
                    onLongClick = { if (state.canUndo) onUndo() }
                )
        )

        // Contenu central (par-dessus les zones de tap, pointer-events pass-through)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ScoreDisplay(score = state.score)

            if (state.canUndo) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "↩ Long press",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Indicateur déconnexion en bas
        if (!phoneConnected) {
            Text(
                text = "⚠ Déconnecté",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun ScoreDisplay(score: MatchScore) {
    val setsA = score.completedSets.count { it.gamesA > it.gamesB }
    val setsB = score.completedSets.count { it.gamesB > it.gamesA }
    val (pA, pB) = score.currentPointsDisplay()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Sets — petit, informatif
        Text(
            text = "$setsA  —  $setsB  SETS",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Jeux — dominant, le plus grand
        Text(
            text = "${score.currentSetGamesA}  —  ${score.currentSetGamesB}",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "JEUX",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Points — grand, accent couleur
        Text(
            text = "$pA  —  $pB",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        when {
            score.isSuperTieBreak -> Text(
                text = "Super TB",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            score.isTieBreak -> Text(
                text = "Tie-break",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            score.isDeuce -> Text(
                text = "Égalité",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun MatchScore.currentPointsDisplay(): Pair<String, String> = when {
    isSuperTieBreak || isTieBreak -> Pair(
        tieBreakPointsA.toString(),
        tieBreakPointsB.toString()
    )
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
