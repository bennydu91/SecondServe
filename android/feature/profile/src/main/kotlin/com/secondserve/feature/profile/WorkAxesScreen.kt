package com.secondserve.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.secondserve.domain.model.MAX_WORK_AXES
import com.secondserve.domain.model.WorkAxis
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkAxesScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkAxesViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAxis by remember { mutableStateOf<WorkAxis?>(null) }
    var newTitle by remember { mutableStateOf("") }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is WorkAxesSideEffect.WorkAxisCreated -> {
                showCreateDialog = false
                newTitle = ""
            }
            is WorkAxesSideEffect.WorkAxisUpdated -> {
                editingAxis = null
                newTitle = ""
            }
            WorkAxesSideEffect.WorkAxisDeleted -> {}
            is WorkAxesSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Axes de travail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.isAtMaxCapacity) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Maximum $MAX_WORK_AXES axes actifs atteint")
                        }
                    } else {
                        showCreateDialog = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un axe")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                if (state.isAtMaxCapacity) {
                    Text(
                        text = "Maximum $MAX_WORK_AXES axes actifs atteint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = "${state.workAxes.size}/$MAX_WORK_AXES axes actifs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(state.workAxes, key = { it.id }) { axis ->
                WorkAxisCard(
                    axis = axis,
                    onEdit = { editingAxis = axis; newTitle = axis.title },
                    onDelete = { viewModel.deleteWorkAxis(axis.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newTitle = "" },
            title = { Text("Nouvel axe de travail") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { if (it.length <= 200) newTitle = it },
                    label = { Text("Description de l'axe") },
                    supportingText = { Text("${newTitle.length}/200") },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createWorkAxis(newTitle) },
                    enabled = newTitle.isNotBlank() && !state.isSaving
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newTitle = "" }) { Text("Annuler") }
            }
        )
    }

    editingAxis?.let { axis ->
        AlertDialog(
            onDismissRequest = { editingAxis = null; newTitle = "" },
            title = { Text("Modifier l'axe") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { if (it.length <= 200) newTitle = it },
                    label = { Text("Description de l'axe") },
                    supportingText = { Text("${newTitle.length}/200") },
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateWorkAxis(axis.id, newTitle) },
                    enabled = newTitle.isNotBlank() && !state.isSaving
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { editingAxis = null; newTitle = "" }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun WorkAxisCard(
    axis: WorkAxis,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = axis.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
