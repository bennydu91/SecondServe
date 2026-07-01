package com.secondserve.wear.presentation.match

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.secondserve.wear.presentation.theme.BarlowSemiCondensed
import com.secondserve.wear.presentation.theme.BroadcastColors
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
            onSwap = { viewModel.swapLast() },
            phoneConnected = state.phoneConnected,
            opponentLabel = sanitizeOpponentLabel(state.opponentName)
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
                text = "VICTOIRE",
                fontFamily = BarlowSemiCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                color = BroadcastColors.lime
            )
            Text(
                text = "$setsA  —  $setsB",
                fontFamily = BarlowSemiCondensed,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "SETS",
                fontFamily = BarlowSemiCondensed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
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
                Text("Annuler", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ScoreScreenContent(
    state: ScoreUiState,
    onPointA: () -> Unit,
    onPointB: () -> Unit,
    onUndo: () -> Unit,
    onSwap: () -> Unit,
    phoneConnected: Boolean = true,
    opponentLabel: String = FALLBACK_OPPONENT_LABEL
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Zone de tap A (moitié gauche) — un tap = un point, correction via les boutons dédiés.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .clickable(onClick = onPointA)
        )

        // Zone de tap B (moitié droite)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .clickable(onClick = onPointB)
        )

        // Labels de zone — VOUS toujours en accent, adversaire neutre (pass-through, pas cliquables).
        Text(
            text = "VOUS",
            fontFamily = BarlowSemiCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = BroadcastColors.lime,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
        )
        Text(
            text = opponentLabel,
            fontFamily = BarlowSemiCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )

        // Contenu central (par-dessus les zones de tap, pointer-events pass-through)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ScoreDisplay(score = state.score)

            Spacer(modifier = Modifier.height(10.dp))

            // Boutons de correction permanents — toujours à portée, jamais cachés derrière un
            // geste. Grisés (non cliquables) tant qu'aucun point n'a été marqué.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundActionButton(glyph = "↩", enabled = state.canUndo, onClick = onUndo)
                RoundActionButton(glyph = "⇄", enabled = state.canUndo, onClick = onSwap)
            }
        }

        // Indicateur déconnexion en bas — badge pilule, la montre continue de scorer hors ligne.
        if (!phoneConnected) {
            OfflineBadge(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun RoundActionButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
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
            fontFamily = BarlowSemiCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Jeux — dominant, le plus grand. VOUS en lime (accent unique du design system).
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${score.currentSetGamesA}",
                fontFamily = BarlowSemiCondensed,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BroadcastColors.lime,
                textAlign = TextAlign.Center
            )
            Text(
                text = "  —  ${score.currentSetGamesB}",
                fontFamily = BarlowSemiCondensed,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "JEUX",
            fontFamily = BarlowSemiCondensed,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Points — grand, accent "data" (bleu), distinct du lime réservé au joueur actif.
        Text(
            text = "$pA  —  $pB",
            fontFamily = BarlowSemiCondensed,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BroadcastColors.data,
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

@Composable
private fun OfflineBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "offline-pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offline-pulse-alpha"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(BroadcastColors.defeatBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BroadcastColors.hot.copy(alpha = dotAlpha))
        )
        Text(
            text = "Hors ligne · sauvegarde locale",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = BroadcastColors.hot
        )
    }
}

// Nom générique affiché quand l'adversaire n'a pas été renseigné (démarrage rapide depuis la
// montre, mode dégradé hors-ligne) ou en cas de saisie invalide côté téléphone.
internal const val FALLBACK_OPPONENT_LABEL = "ADVERSAIRE"

// Longueur max avant troncature : le cadran est petit et une chaîne corrompue ou anormalement
// longue (bug de saisie, payload DataLayer malformé) ne doit jamais casser la mise en page.
internal const val MAX_OPPONENT_LABEL_LENGTH = 14

internal fun sanitizeOpponentLabel(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return FALLBACK_OPPONENT_LABEL
    return if (trimmed.length > MAX_OPPONENT_LABEL_LENGTH) {
        trimmed.take(MAX_OPPONENT_LABEL_LENGTH - 1) + "…"
    } else {
        trimmed
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
