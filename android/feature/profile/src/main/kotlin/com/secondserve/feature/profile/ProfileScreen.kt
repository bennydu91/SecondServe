package com.secondserve.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.BroadcastPrimaryButton
import com.secondserve.core.ui.components.BroadcastSectionCard
import com.secondserve.core.ui.components.BroadcastTextField
import com.secondserve.core.ui.components.SurfaceChip
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
import com.secondserve.domain.constants.FftConstants
import com.secondserve.domain.model.PlayStyleConstants
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.model.SurfaceConstants
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onNavigateToWorkAxes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRENCH) }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ProfileSideEffect.RankingSaved -> scope.launch { snackbarHostState.showSnackbar("Classement enregistré") }
            is ProfileSideEffect.ProfileDetailsSaved -> scope.launch { snackbarHostState.showSnackbar("Profil mis à jour") }
            is ProfileSideEffect.IdentitySaved -> scope.launch { snackbarHostState.showSnackbar("Identité mise à jour") }
            is ProfileSideEffect.ShowError -> scope.launch { snackbarHostState.showSnackbar(effect.message) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(color = colors.lime, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = BroadcastSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.padding(bottom = BroadcastSpacing.xs)
                    )
                }

                item {
                    ProfileHeaderCard(
                        displayName = state.displayName,
                        club = state.club,
                        currentSeries = state.currentSeries,
                        currentPoints = state.currentPoints
                    )
                }

                item {
                    IdentitySection(
                        currentDisplayName = state.displayName,
                        currentClub = state.club,
                        isSaving = state.isIdentitySaving,
                        onSave = viewModel::saveIdentity
                    )
                }

                item {
                    RankingInputSection(
                        isSaving = state.isSaving,
                        onSave = viewModel::saveRanking
                    )
                }

                if (state.rankingHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = "HISTORIQUE DE CLASSEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.faint
                        )
                    }
                    items(state.rankingHistory) { entry ->
                        RankingHistoryItem(entry = entry, dateFormat = dateFormat)
                    }
                }

                item {
                    PlayStyleSection(
                        matchSessionCount = state.matchSessionCount,
                        currentPlayStyle = state.playStyle,
                        isSaving = state.isDetailsSaving,
                        currentSurfaces = state.preferredSurfaces,
                        coachInstruction1 = state.coachInstruction1,
                        coachInstruction2 = state.coachInstruction2,
                        coachInstruction3 = state.coachInstruction3,
                        onSaveDetails = viewModel::saveProfileDetails
                    )
                }

                item {
                    FftLicenseSection(
                        currentLicense = state.fftLicenseNumber,
                        onSaveLicense = viewModel::saveFftLicense
                    )
                }

                item {
                    SettingsList(
                        onNavigateToWorkAxes = onNavigateToWorkAxes,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProfileHeaderCard(displayName: String?, club: String?, currentSeries: String?, currentPoints: Int?) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.card),
        color = colors.panelHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(BroadcastSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.lime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = colors.void)
            }
            Column {
                Text(
                    text = displayName ?: "Nom non renseigné",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                if (club != null) {
                    Text(text = club, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                Text(
                    text = if (currentSeries != null) "Classé $currentSeries" else "Classement non renseigné",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                if (currentPoints != null) {
                    Text(
                        text = "$currentPoints points",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentitySection(
    currentDisplayName: String?,
    currentClub: String?,
    isSaving: Boolean,
    onSave: (String?, String?) -> Unit
) {
    val colors = LocalBroadcastColors.current
    var nameText by remember { mutableStateOf(currentDisplayName ?: "") }
    var clubText by remember { mutableStateOf(currentClub ?: "") }

    LaunchedEffect(currentDisplayName) { nameText = currentDisplayName ?: "" }
    LaunchedEffect(currentClub) { clubText = currentClub ?: "" }

    BroadcastSectionCard(title = "Identité") {
        BroadcastTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = "Nom affiché",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        BroadcastTextField(
            value = clubText,
            onValueChange = { clubText = it },
            label = "Club",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.md))
        BroadcastPrimaryButton(
            text = "Enregistrer",
            onClick = { onSave(nameText.ifBlank { null }, clubText.ifBlank { null }) },
            enabled = !isSaving,
            isLoading = isSaving
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingInputSection(isSaving: Boolean, onSave: (String, Int) -> Unit) {
    val colors = LocalBroadcastColors.current
    var selectedSeries by remember { mutableStateOf(FftConstants.VALID_SERIES.first()) }
    var pointsText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    BroadcastSectionCard(title = "Saisir un classement") {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            BroadcastTextField(
                value = selectedSeries,
                onValueChange = {},
                readOnly = true,
                label = "Série FFT",
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                FftConstants.VALID_SERIES.forEach { series ->
                    DropdownMenuItem(
                        text = { Text(series) },
                        onClick = { selectedSeries = series; expanded = false }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        BroadcastTextField(
            value = pointsText,
            onValueChange = { pointsText = it.filter { c -> c.isDigit() } },
            label = "Points",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.md))
        BroadcastPrimaryButton(
            text = "Enregistrer",
            onClick = { onSave(selectedSeries, pointsText.toIntOrNull() ?: 0) },
            enabled = !isSaving && pointsText.toIntOrNull() != null,
            isLoading = isSaving
        )
    }
}

@Composable
private fun RankingHistoryItem(entry: RankingEntry, dateFormat: java.text.SimpleDateFormat) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BroadcastSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = entry.series, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        Text(text = "${entry.points} pts", style = MaterialTheme.typography.bodyMedium, color = colors.text)
        Text(
            text = dateFormat.format(Date(entry.recordedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayStyleSection(
    matchSessionCount: Int,
    currentPlayStyle: String?,
    isSaving: Boolean,
    currentSurfaces: List<String>,
    coachInstruction1: String?,
    coachInstruction2: String?,
    coachInstruction3: String?,
    onSaveDetails: (String?, List<String>, String?, String?, String?) -> Unit
) {
    val colors = LocalBroadcastColors.current
    var selectedStyle by remember { mutableStateOf(currentPlayStyle) }
    var expandedStyle by remember { mutableStateOf(false) }
    var selectedSurfaces by remember { mutableStateOf(currentSurfaces.toSet()) }
    var instruction1 by remember { mutableStateOf(coachInstruction1 ?: "") }
    var instruction2 by remember { mutableStateOf(coachInstruction2 ?: "") }
    var instruction3 by remember { mutableStateOf(coachInstruction3 ?: "") }

    LaunchedEffect(currentSurfaces) { selectedSurfaces = currentSurfaces.toSet() }
    LaunchedEffect(currentPlayStyle) { selectedStyle = currentPlayStyle }
    LaunchedEffect(coachInstruction1) { instruction1 = coachInstruction1 ?: "" }
    LaunchedEffect(coachInstruction2) { instruction2 = coachInstruction2 ?: "" }
    LaunchedEffect(coachInstruction3) { instruction3 = coachInstruction3 ?: "" }

    BroadcastSectionCard(title = "Style de jeu") {
        if (matchSessionCount < 10 && selectedStyle == null) {
            Text(
                text = "Données insuffisantes (minimum 10 matchs)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
            Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        }

        ExposedDropdownMenuBox(expanded = expandedStyle, onExpandedChange = { expandedStyle = !expandedStyle }) {
            BroadcastTextField(
                value = selectedStyle?.let { PlayStyleConstants.DISPLAY_NAMES[it] } ?: "Sélectionner un style",
                onValueChange = {},
                readOnly = true,
                label = "Style de jeu",
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStyle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expandedStyle, onDismissRequest = { expandedStyle = false }) {
                PlayStyleConstants.ALL.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(PlayStyleConstants.DISPLAY_NAMES[style] ?: style) },
                        onClick = { selectedStyle = style; expandedStyle = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(BroadcastSpacing.lg))
        Text(text = "SURFACES DE PRÉDILECTION", style = MaterialTheme.typography.labelSmall, color = colors.faint)
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BroadcastSpacing.sm)) {
            SurfaceConstants.ALL.forEach { surface ->
                val selected = surface in selectedSurfaces
                SurfaceChip(
                    label = SurfaceConstants.DISPLAY_NAMES[surface] ?: surface,
                    color = colors.forSurfaceKey(surface),
                    selected = selected,
                    onClick = {
                        selectedSurfaces = if (selected) selectedSurfaces - surface else selectedSurfaces + surface
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(BroadcastSpacing.lg))
        Text(text = "CONSIGNES DU COACH", style = MaterialTheme.typography.labelSmall, color = colors.faint)
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))

        BroadcastTextField(
            value = instruction1,
            onValueChange = { if (it.length <= 500) instruction1 = it },
            label = "Axe principal du coach",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        BroadcastTextField(
            value = instruction2,
            onValueChange = { if (it.length <= 500) instruction2 = it },
            label = "Axe secondaire du coach",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
        BroadcastTextField(
            value = instruction3,
            onValueChange = { if (it.length <= 500) instruction3 = it },
            label = "Mauvaises habitudes à corriger",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(BroadcastSpacing.md))
        BroadcastPrimaryButton(
            text = "Enregistrer le profil",
            onClick = {
                onSaveDetails(
                    selectedStyle,
                    selectedSurfaces.toList(),
                    instruction1.ifBlank { null },
                    instruction2.ifBlank { null },
                    instruction3.ifBlank { null }
                )
            },
            enabled = !isSaving,
            isLoading = isSaving
        )
    }
}

@Composable
private fun FftLicenseSection(currentLicense: String?, onSaveLicense: (String) -> Unit) {
    val colors = LocalBroadcastColors.current
    var licenseText by remember { mutableStateOf(currentLicense ?: "") }
    LaunchedEffect(currentLicense) { licenseText = currentLicense ?: "" }

    BroadcastSectionCard(title = "Licence FFT") {
        BroadcastTextField(
            value = licenseText,
            onValueChange = { licenseText = it.filter { c -> c.isDigit() } },
            label = "Numéro de licence",
            supportingText = "Stocké localement uniquement",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(BroadcastSpacing.md))
        BroadcastPrimaryButton(
            text = "Enregistrer la licence",
            onClick = { if (licenseText.isNotBlank()) onSaveLicense(licenseText) },
            enabled = licenseText.isNotBlank()
        )
    }
}

@Composable
private fun SettingsList(onNavigateToWorkAxes: () -> Unit, onNavigateToSettings: () -> Unit) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.card),
        color = colors.panelHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(label = "Gérer mes axes de travail", onClick = onNavigateToWorkAxes)
            HorizontalDivider(color = colors.line)
            SettingsRow(label = "Paramètres", onClick = onNavigateToSettings)
        }
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = BroadcastSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.text.copy(alpha = 0.9f))
        Text(text = "›", style = MaterialTheme.typography.titleLarge, color = colors.faint)
    }
}
