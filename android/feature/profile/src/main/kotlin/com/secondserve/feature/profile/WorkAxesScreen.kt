package com.secondserve.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.domain.model.AxisSuggestion
import com.secondserve.domain.model.MAX_WORK_AXES
import com.secondserve.domain.model.WorkAxis
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun WorkAxesScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkAxesViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAxis by remember { mutableStateOf<WorkAxis?>(null) }
    var createTitle by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is WorkAxesSideEffect.WorkAxisCreated -> {
                showCreateDialog = false
                createTitle = ""
            }
            is WorkAxesSideEffect.WorkAxisUpdated -> {
                editingAxis = null
                editTitle = ""
            }
            WorkAxesSideEffect.WorkAxisDeleted -> {}
            WorkAxesSideEffect.SuggestionAccepted -> {}
            is WorkAxesSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
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
                contentDescription = "Retour"
            )
            Text(
                text = "Axes de travail",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BroadcastSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)
        ) {
            item {
                if (state.isAtMaxCapacity) {
                    Text(
                        text = "Maximum $MAX_WORK_AXES axes actifs atteint",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.hot,
                        modifier = Modifier.padding(vertical = BroadcastSpacing.sm)
                    )
                } else {
                    Text(
                        text = "${state.workAxes.size}/$MAX_WORK_AXES axes actifs",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = BroadcastSpacing.sm)
                    )
                }
            }

            if (state.isGeneratingSuggestions || state.pendingSuggestions.isNotEmpty()) {
                item {
                    Text(
                        "SUGGESTIONS IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.faint,
                        modifier = Modifier.padding(top = BroadcastSpacing.md, bottom = BroadcastSpacing.xs)
                    )
                }
                if (state.isGeneratingSuggestions) {
                    item {
                        CircularProgressIndicator(
                            color = colors.lime,
                            modifier = Modifier.padding(vertical = BroadcastSpacing.sm)
                        )
                    }
                } else {
                    items(state.pendingSuggestions, key = { "suggestion_${it.id}" }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            isAcceptDisabled = state.isAtMaxCapacity,
                            onAccept = { viewModel.acceptSuggestion(suggestion.id) },
                            onIgnore = { viewModel.ignoreSuggestion(suggestion.id) }
                        )
                    }
                }
                state.suggestionsError?.let { err ->
                    item {
                        Text(err, color = colors.hot, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = BroadcastSpacing.sm)) }
            }

            items(state.workAxes, key = { it.id }) { axis ->
                WorkAxisCard(
                    axis = axis,
                    onEdit = { editingAxis = axis; editTitle = axis.title },
                    onDelete = { viewModel.deleteWorkAxis(axis.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(BroadcastSpacing.xxl))
                BroadcastPrimaryButton(
                    text = if (state.isAtMaxCapacity) "Limite atteinte" else "+  Nouvel axe",
                    enabled = !state.isAtMaxCapacity,
                    onClick = {
                        if (state.isAtMaxCapacity) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Maximum $MAX_WORK_AXES axes actifs atteint")
                            }
                        } else {
                            showCreateDialog = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(BroadcastSpacing.lg))
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; createTitle = "" },
            title = { Text("Nouvel axe de travail") },
            text = {
                BroadcastTextField(
                    value = createTitle,
                    onValueChange = { if (it.length <= 200) createTitle = it },
                    label = "Description de l'axe",
                    supportingText = "${createTitle.length}/200"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createWorkAxis(createTitle) },
                    enabled = createTitle.isNotBlank() && !state.isSaving
                ) { Text("Créer", color = colors.lime) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; createTitle = "" }) { Text("Annuler") }
            }
        )
    }

    editingAxis?.let { axis ->
        AlertDialog(
            onDismissRequest = { editingAxis = null; editTitle = "" },
            title = { Text("Modifier l'axe") },
            text = {
                BroadcastTextField(
                    value = editTitle,
                    onValueChange = { if (it.length <= 200) editTitle = it },
                    label = "Description de l'axe",
                    supportingText = "${editTitle.length}/200"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateWorkAxis(axis.id, editTitle) },
                    enabled = editTitle.isNotBlank() && !state.isSaving
                ) { Text("Enregistrer", color = colors.lime) }
            },
            dismissButton = {
                TextButton(onClick = { editingAxis = null; editTitle = "" }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: AxisSuggestion,
    isAcceptDisabled: Boolean,
    onAccept: () -> Unit,
    onIgnore: () -> Unit
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.input),
        color = colors.panelHigh
    ) {
        Column(Modifier.padding(BroadcastSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Suggestion IA",
                    tint = colors.lime,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(BroadcastSpacing.sm))
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(BroadcastSpacing.sm))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onIgnore) { Text("Ignorer", color = colors.muted) }
                Spacer(Modifier.width(BroadcastSpacing.xs))
                TextButton(onClick = onAccept, enabled = !isAcceptDisabled) {
                    Text("Accepter", color = if (isAcceptDisabled) colors.faint else colors.lime)
                }
            }
            if (isAcceptDisabled) {
                Text(
                    "Maximum $MAX_WORK_AXES axes actifs atteint",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.hot
                )
            }
        }
    }
}

@Composable
private fun WorkAxisCard(
    axis: WorkAxis,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.input),
        color = colors.panelHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BroadcastSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = axis.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = colors.muted)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = colors.hot
                )
            }
        }
    }
}
