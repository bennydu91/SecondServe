package com.secondserve.wear.presentation.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun StartMatchScreen(
    onStartLocal: (matchFormat: MatchFormat, thirdSetRule: ThirdSetRule) -> Unit,
    viewModel: StartMatchViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is StartMatchSideEffect.StartLocal ->
                onStartLocal(effect.matchFormat, effect.thirdSetRule)
            is StartMatchSideEffect.StartRemote ->
                // Phone was notified — navigation will be triggered by WearNavGraph
                // when the phone responds with a start_session event
                Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Nouveau match",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Format selector — filled = selected, tonal = not selected
        SelectChip(
            label = "1 set",
            selected = state.matchFormat == MatchFormat.BEST_OF_1,
            onClick = { viewModel.selectFormat(MatchFormat.BEST_OF_1) }
        )
        SelectChip(
            label = "3 sets",
            selected = state.matchFormat == MatchFormat.BEST_OF_3,
            onClick = { viewModel.selectFormat(MatchFormat.BEST_OF_3) }
        )

        // Third-set rule — visible only for BEST_OF_3
        if (state.matchFormat == MatchFormat.BEST_OF_3) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "3e set :",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SelectChip(
                label = "Avantage",
                selected = state.thirdSetRule == ThirdSetRule.FULL_ADVANTAGE,
                onClick = { viewModel.selectThirdSetRule(ThirdSetRule.FULL_ADVANTAGE) }
            )
            SelectChip(
                label = "Super TB",
                selected = state.thirdSetRule == ThirdSetRule.SUPER_TIE_BREAK_10,
                onClick = { viewModel.selectThirdSetRule(ThirdSetRule.SUPER_TIE_BREAK_10) }
            )
            SelectChip(
                label = "Set court",
                selected = state.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET,
                onClick = { viewModel.selectThirdSetRule(ThirdSetRule.SHORT_DECISIVE_SET) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Confirmation button — disabled while waiting for phone response
        Button(
            onClick = { viewModel.confirmStart() },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.isLoading) "En attente…" else "Démarrer",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 11.sp)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 11.sp)
        }
    }
}
