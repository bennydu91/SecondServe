package com.secondserve.feature.history

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.SurfaceConstants
import com.secondserve.domain.model.ThirdSetRule
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRetroSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddRetroSessionViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is AddRetroSessionSideEffect.SessionCreated -> onNavigateBack()
            is AddRetroSessionSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onMatchDateSelected(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un match passé") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("← Retour") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Surface *", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfaceConstants.ALL.forEach { surface ->
                    FilterChip(
                        selected = state.selectedSurface == surface,
                        onClick = { viewModel.onSurfaceSelected(surface) },
                        label = { Text(SurfaceConstants.DISPLAY_NAMES[surface] ?: surface) }
                    )
                }
            }

            Text("Format *", style = MaterialTheme.typography.titleMedium)
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

            if (state.selectedMatchFormat == MatchFormat.BEST_OF_3) {
                Text("Règle 3e set *", style = MaterialTheme.typography.titleMedium)
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

            Text("Résultat *", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedResult == "VICTORY",
                    onClick = { viewModel.onResultSelected("VICTORY") }
                )
                Text("Victoire")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.selectedResult == "DEFEAT",
                    onClick = { viewModel.onResultSelected("DEFEAT") }
                )
                Text("Défaite")
            }

            Text("Date du match *", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    state.matchDateMillis?.let { dateFormat.format(Date(it)) }
                        ?: "Sélectionner une date"
                )
            }

            OutlinedTextField(
                value = state.scoreText,
                onValueChange = viewModel::onScoreTextChanged,
                label = { Text("Score final (ex : 6-3, 4-6, 7-5)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Informations optionnelles", style = MaterialTheme.typography.titleMedium)
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

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}
