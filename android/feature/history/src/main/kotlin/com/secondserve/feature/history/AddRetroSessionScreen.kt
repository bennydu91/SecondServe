package com.secondserve.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.BroadcastPrimaryButton
import com.secondserve.core.ui.components.BroadcastTextField
import com.secondserve.core.ui.components.CircleIconButton
import com.secondserve.core.ui.components.SurfaceChip
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
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
    val colors = LocalBroadcastColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )
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
                }) { Text("OK", color = colors.lime) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(snackbarHostState)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BroadcastSpacing.lg)
                .padding(top = 10.dp, bottom = BroadcastSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircleIconButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                enabled = !state.isLoading
            )
            Text(
                text = "Ajouter un match passé",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BroadcastSpacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.xl)
        ) {
            FieldSection(label = "SURFACE") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
                    SurfaceConstants.ALL.forEach { surface ->
                        SurfaceChip(
                            label = SurfaceConstants.DISPLAY_NAMES[surface] ?: surface,
                            color = colors.forSurfaceKey(surface),
                            selected = state.selectedSurface == surface,
                            onClick = { viewModel.onSurfaceSelected(surface) }
                        )
                    }
                }
            }

            FieldSection(label = "FORMAT") {
                Column(verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
                    RadioOption(
                        label = "1 set",
                        selected = state.selectedMatchFormat == MatchFormat.BEST_OF_1,
                        onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_1) }
                    )
                    RadioOption(
                        label = "3 sets",
                        selected = state.selectedMatchFormat == MatchFormat.BEST_OF_3,
                        onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3) }
                    )
                }
            }

            if (state.selectedMatchFormat == MatchFormat.BEST_OF_3) {
                FieldSection(label = "RÈGLE 3E SET") {
                    Column(verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
                        listOf(
                            ThirdSetRule.FULL_ADVANTAGE to "Avantage complet",
                            ThirdSetRule.SUPER_TIE_BREAK_10 to "Super tie-break à 10",
                            ThirdSetRule.SHORT_DECISIVE_SET to "Set décisif raccourci"
                        ).forEach { (rule, label) ->
                            RadioOption(
                                label = label,
                                selected = state.selectedThirdSetRule == rule,
                                onClick = { viewModel.onThirdSetRuleSelected(rule) }
                            )
                        }
                    }
                }
            }

            FieldSection(label = "RÉSULTAT") {
                Column(verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
                    RadioOption(
                        label = "Victoire",
                        selected = state.selectedResult == "VICTORY",
                        onClick = { viewModel.onResultSelected("VICTORY") }
                    )
                    RadioOption(
                        label = "Défaite",
                        selected = state.selectedResult == "DEFEAT",
                        onClick = { viewModel.onResultSelected("DEFEAT") }
                    )
                }
            }

            FieldSection(label = "DATE DU MATCH") {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BroadcastRadius.input),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    border = BorderStroke(1.dp, colors.line)
                ) {
                    Text(
                        state.matchDateMillis?.let { dateFormat.format(Date(it)) }
                            ?: "Sélectionner une date"
                    )
                }
            }

            FieldSection(label = "SCORE FINAL") {
                BroadcastTextField(
                    value = state.scoreText,
                    onValueChange = viewModel::onScoreTextChanged,
                    placeholder = "ex : 6-3, 4-6, 7-5"
                )
            }

            FieldSection(label = "INFORMATIONS OPTIONNELLES") {
                Column(verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
                    BroadcastTextField(
                        value = state.opponent,
                        onValueChange = viewModel::onOpponentChanged,
                        placeholder = "Adversaire"
                    )
                    BroadcastTextField(
                        value = state.competitionType,
                        onValueChange = viewModel::onCompetitionTypeChanged,
                        placeholder = "Type de compétition"
                    )
                    BroadcastTextField(
                        value = state.tournament,
                        onValueChange = viewModel::onTournamentChanged,
                        placeholder = "Tournoi"
                    )
                }
            }

            BroadcastPrimaryButton(
                text = "Enregistrer",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                isLoading = state.isLoading
            )

            Spacer(modifier = Modifier.height(BroadcastSpacing.lg))
        }
    }
}

@Composable
private fun FieldSection(label: String, content: @Composable () -> Unit) {
    val colors = LocalBroadcastColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.faint)
        content()
    }
}

@Composable
private fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = BroadcastSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BroadcastSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(colors.void, CircleShape)
                .border(
                    width = if (selected) 6.dp else 2.dp,
                    color = if (selected) colors.lime else colors.line,
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.text else colors.muted
        )
    }
}
