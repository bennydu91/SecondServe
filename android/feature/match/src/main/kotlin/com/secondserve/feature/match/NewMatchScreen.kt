package com.secondserve.feature.match

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.SurfaceConstants
import com.secondserve.domain.model.ThirdSetRule
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMatchScreen(
    onSessionStarted: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: NewMatchViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is NewMatchSideEffect.SessionStarted -> onSessionStarted(effect.sessionId)
            is NewMatchSideEffect.SessionPlanned -> onNavigateBack()
            is NewMatchSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Nouveau match", style = MaterialTheme.typography.headlineMedium)

            // Surface
            Text(text = "Surface *", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfaceConstants.ALL.forEach { surface ->
                    FilterChip(
                        selected = state.selectedSurface == surface,
                        onClick = { viewModel.onSurfaceSelected(surface) },
                        label = {
                            Text(SurfaceConstants.DISPLAY_NAMES[surface] ?: surface)
                        }
                    )
                }
            }

            // Format sets
            Text(text = "Format *", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedMatchFormat == MatchFormat.BEST_OF_1,
                    onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_1) }
                )
                Text("1 set")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedMatchFormat == MatchFormat.BEST_OF_3,
                    onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3) }
                )
                Text("3 sets")
            }

            // Règle 3e set (visible uniquement si BEST_OF_3)
            if (state.selectedMatchFormat == MatchFormat.BEST_OF_3) {
                Text(text = "Règle 3e set *", style = MaterialTheme.typography.titleMedium)
                listOf(
                    ThirdSetRule.FULL_ADVANTAGE to "Avantage complet",
                    ThirdSetRule.SUPER_TIE_BREAK_10 to "Super tie-break à 10",
                    ThirdSetRule.SHORT_DECISIVE_SET to "Set décisif raccourci"
                ).forEach { (rule, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.selectedThirdSetRule == rule,
                            onClick = { viewModel.onThirdSetRuleSelected(rule) }
                        )
                        Text(label)
                    }
                }
            }

            // Champs optionnels
            Text(text = "Informations optionnelles", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.opponent,
                onValueChange = viewModel::onOpponentChanged,
                label = { Text("Adversaire") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.competitionType,
                onValueChange = viewModel::onCompetitionTypeChanged,
                label = { Text("Type de compétition") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.tournament,
                onValueChange = viewModel::onTournamentChanged,
                label = { Text("Tournoi") },
                modifier = Modifier.fillMaxWidth()
            )

            // Planification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Planifier pour plus tard",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = state.isScheduled,
                    onCheckedChange = { viewModel.onScheduledToggled(it) }
                )
            }

            if (state.isScheduled) {
                val context = LocalContext.current
                val calendar = remember { Calendar.getInstance() }
                val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

                val scheduledAt = state.scheduledAt
                if (scheduledAt != null) {
                    Text(
                        text = "Match planifié : ${fmt.format(Date(scheduledAt))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                TextButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                calendar.set(year, month, day)
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                                        calendar.set(Calendar.MINUTE, minute)
                                        calendar.set(Calendar.SECOND, 0)
                                        val selected = calendar.timeInMillis
                                        if (selected > System.currentTimeMillis()) {
                                            viewModel.onScheduledAtChanged(selected)
                                        }
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            datePicker.minDate = System.currentTimeMillis() + 60_000L
                        }.show()
                    }
                ) {
                    Text(if (state.scheduledAt != null) "Changer la date/heure" else "Choisir la date/heure")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = viewModel::startMatch,
                    enabled = state.canStartMatch,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isScheduled) "Planifier le match" else "Démarrer le match")
                }
            }
        }
    }
}
