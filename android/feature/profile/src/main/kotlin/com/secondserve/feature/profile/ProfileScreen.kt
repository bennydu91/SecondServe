package com.secondserve.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.domain.constants.FftConstants
import com.secondserve.domain.model.RankingEntry
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.util.Date

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRENCH) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ProfileSideEffect.RankingSaved -> {
                scope.launch { snackbarHostState.showSnackbar("Classement enregistré") }
            }
            is ProfileSideEffect.ShowError -> {
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RankingSummaryCard(
                        currentSeries = state.currentSeries,
                        currentPoints = state.currentPoints
                    )
                }
                item {
                    RankingInputSection(
                        isSaving = state.isSaving,
                        onSave = viewModel::saveRanking
                    )
                }
                item {
                    Text(
                        text = "Historique",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    HorizontalDivider()
                }
                items(state.rankingHistory) { entry ->
                    RankingHistoryItem(entry = entry, dateFormat = dateFormat)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RankingSummaryCard(currentSeries: String?, currentPoints: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Classement actuel", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (currentSeries != null) {
                Text(text = "Série : $currentSeries", style = MaterialTheme.typography.bodyLarge)
                if (currentPoints != null) {
                    Text(text = "Points : $currentPoints", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    text = "Aucun classement enregistré",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingInputSection(
    isSaving: Boolean,
    onSave: (String, Int) -> Unit
) {
    var selectedSeries by remember { mutableStateOf(FftConstants.VALID_SERIES.first()) }
    var pointsText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Saisir un classement", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedSeries,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Série FFT") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    FftConstants.VALID_SERIES.forEach { series ->
                        DropdownMenuItem(
                            text = { Text(series) },
                            onClick = {
                                selectedSeries = series
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pointsText,
                onValueChange = { pointsText = it.filter { c -> c.isDigit() } },
                label = { Text("Points") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val points = pointsText.toIntOrNull() ?: 0
                    onSave(selectedSeries, points)
                },
                enabled = !isSaving && pointsText.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun RankingHistoryItem(entry: RankingEntry, dateFormat: java.text.SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = entry.series, style = MaterialTheme.typography.bodyMedium)
        Text(text = "${entry.points} pts", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = dateFormat.format(Date(entry.recordedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
