package com.secondserve.wear.presentation.match

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@Composable
fun ScoreScreen(
    viewModel: ScoreViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    var showCancelConfirm by remember { mutableStateOf(false) }

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
            onCancelRequest = { showCancelConfirm = true }
        )
        else -> ScoreScreenContent(
            state = state,
            onPointA = { viewModel.recordPoint(Player.A) },
            onPointB = { viewModel.recordPoint(Player.B) },
            onUndo = { viewModel.undo() }
        )
    }
}

@Composable
private fun MatchOverScreen(
    score: MatchScore,
    onCancelRequest: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val setsA = score.completedSets.count { it.gamesA > it.gamesB }
            val setsB = score.completedSets.count { it.gamesB > it.gamesA }
            Text(
                text = "Sets : $setsA — $setsB",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Fin du match",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        FilledTonalButton(
            onClick = onCancelRequest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            Text("Annuler la fin", fontSize = 11.sp)
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
    onUndo: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .combinedClickable(
                    onClick = { onPointA() },
                    onLongClick = { if (state.canUndo) onUndo() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ScoreDisplay(
            score = state.score,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .combinedClickable(
                    onClick = { onPointB() },
                    onLongClick = { if (state.canUndo) onUndo() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ScoreDisplay(
    score: MatchScore,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Sets toujours affichés — AC 1 "en permanence"
        val setsA = score.completedSets.count { it.gamesA > it.gamesB }
        val setsB = score.completedSets.count { it.gamesB > it.gamesA }
        Text(
            text = "Sets : $setsA — $setsB",
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "${score.currentSetGamesA} — ${score.currentSetGamesB}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        val (pA, pB) = score.currentPointsDisplay()
        Text(
            text = "$pA — $pB",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        when {
            score.isSuperTieBreak -> Text(
                text = "Super TB",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            score.isTieBreak -> Text(
                text = "Tie-break",
                fontSize = 11.sp,
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
