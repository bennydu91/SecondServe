package com.secondserve.feature.match

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.SurfaceChip
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
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
    val colors = LocalBroadcastColors.current

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
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onNavigateBack)
                        .background(colors.panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = colors.muted
                    )
                }
                Text(
                    text = "Nouveau match",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            }

            FieldSection(label = "SURFACE") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatOption(
                        label = "1 set",
                        selected = state.selectedMatchFormat == MatchFormat.BEST_OF_1,
                        onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_1) },
                        modifier = Modifier.weight(1f)
                    )
                    FormatOption(
                        label = "3 sets",
                        selected = state.selectedMatchFormat == MatchFormat.BEST_OF_3,
                        onClick = { viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.selectedMatchFormat == MatchFormat.BEST_OF_3) {
                FieldSection(label = "RÈGLE 3E SET") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThirdSetRule.FULL_ADVANTAGE to "Avantage complet",
                            ThirdSetRule.SUPER_TIE_BREAK_10 to "Super tie-break à 10",
                            ThirdSetRule.SHORT_DECISIVE_SET to "Set décisif raccourci"
                        ).forEach { (rule, label) ->
                            RuleOption(
                                label = label,
                                selected = state.selectedThirdSetRule == rule,
                                onClick = { viewModel.onThirdSetRuleSelected(rule) }
                            )
                        }
                    }
                }
            }

            FieldSection(label = "ADVERSAIRE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BroadcastTextField(
                        value = state.opponent,
                        onValueChange = viewModel::onOpponentChanged,
                        placeholder = "Nom de l'adversaire"
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Planifier pour plus tard",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text.copy(alpha = 0.85f)
                )
                Switch(
                    checked = state.isScheduled,
                    onCheckedChange = { viewModel.onScheduledToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.void,
                        checkedTrackColor = colors.lime,
                        uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.panelHigh,
                        uncheckedBorderColor = colors.line
                    )
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
                        color = colors.lime
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

            Spacer(modifier = Modifier.weight(1f))

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = colors.lime,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = viewModel::startMatch,
                    enabled = state.canStartMatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.lime,
                        contentColor = colors.void,
                        disabledContainerColor = colors.panelHigh,
                        disabledContentColor = colors.faint
                    )
                ) {
                    Text(
                        text = if (state.isScheduled) "Planifier le match" else "Démarrer le match",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
private fun FormatOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalBroadcastColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.lime.copy(alpha = 0.1f) else colors.panel,
                RoundedCornerShape(12.dp)
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, colors.lime, RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(1.dp, colors.line, RoundedCornerShape(12.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) colors.lime else colors.text.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RuleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalBroadcastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.panelHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.void)
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

@Composable
private fun BroadcastTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val colors = LocalBroadcastColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.faint) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.panel,
            unfocusedContainerColor = colors.panel,
            focusedBorderColor = colors.lime,
            unfocusedBorderColor = colors.line,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            cursorColor = colors.lime
        )
    )
}
