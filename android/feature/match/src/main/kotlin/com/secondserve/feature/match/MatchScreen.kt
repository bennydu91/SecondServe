package com.secondserve.feature.match

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiveIndicator()

            score?.let { liveScore ->
                LiveScoreDisplay(score = liveScore)
            }

            AnimatedVisibility(
                visible = state.coachingAdvice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                state.coachingAdvice?.let { advice ->
                    CoachingAdviceCard(text = advice.text)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = viewModel::onCloseRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isClosing,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = if (state.isClosing) "Clôture en cours…" else "Terminer la session",
                    style = MaterialTheme.typography.titleSmall
                )
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
private fun LiveIndicator() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(8.dp)
            ) {}
            Text(
                text = "EN COURS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun LiveScoreDisplay(score: MatchScore) {
    val setsA = score.completedSets.count { it.gamesA > it.gamesB }
    val setsB = score.completedSets.count { it.gamesB > it.gamesA }
    val (pointsA, pointsB) = score.currentPointsDisplay()
    val pointsLabel = when {
        score.isSuperTieBreak -> "SUPER TIE-BREAK"
        score.isTieBreak -> "TIE-BREAK"
        score.isDeuce -> "ÉGALITÉ"
        else -> "POINTS"
    }
    val gamesA = score.currentSetGamesA
    val gamesB = score.currentSetGamesB

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Labels VOUS / EUX
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "VOUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "EUX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$setsA",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (setsA > setsB) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "SETS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )
            Text(
                text = "$setsB",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (setsB > setsA) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // JEUX — la star de l'écran (80sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$gamesA",
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = if (gamesA > gamesB) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "—",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.outlineVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$gamesB",
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = if (gamesB > gamesA) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "JEUX",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Points
        if (score.isDeuce) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ÉGALITÉ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pointsA,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = pointsB,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = pointsLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun CoachingAdviceCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Conseil coaching",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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
