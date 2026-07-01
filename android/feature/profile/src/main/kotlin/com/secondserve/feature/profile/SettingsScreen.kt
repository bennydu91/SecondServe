package com.secondserve.feature.profile

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondserve.core.ui.components.BroadcastPrimaryButton
import com.secondserve.core.ui.components.BroadcastSectionCard
import com.secondserve.core.ui.components.CircleIconButton
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.domain.notification.NotificationFrequency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FREQUENCY_OPTIONS = listOf(
    NotificationFrequency.DAILY to "Quotidien",
    NotificationFrequency.EVERY_2_DAYS to "Tous les 2 jours",
    NotificationFrequency.WEEKLY to "Hebdomadaire",
    NotificationFrequency.DISABLED to "Désactivé"
)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalBroadcastColors.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BroadcastSpacing.lg)
            .padding(top = 10.dp, bottom = BroadcastSpacing.xl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.lg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircleIconButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour"
            )
            Text(
                text = "Paramètres",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }

        BroadcastSectionCard(title = "Fréquence des notifications") {
            Column(verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.xs)) {
                FREQUENCY_OPTIONS.forEach { (key, label) ->
                    FrequencyOption(
                        label = label,
                        selected = uiState.frequency == key,
                        onClick = { viewModel.onFrequencyChanged(key) }
                    )
                }
            }
        }

        BroadcastSectionCard(title = "Mode silencieux") {
            if (uiState.silentModeUntil > 0L) {
                Text(
                    text = "Actif jusqu'au ${dateFormat.format(Date(uiState.silentModeUntil))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.lime
                )
                Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
                BroadcastPrimaryButton(
                    text = "Désactiver",
                    onClick = { viewModel.onSilentModeCleared() }
                )
            } else {
                Text(
                    text = "Inactif",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(BroadcastSpacing.sm))
                BroadcastPrimaryButton(
                    text = "Choisir une date",
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val picked = Calendar.getInstance().apply {
                                    set(year, month, day, 23, 59, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                if (picked.timeInMillis > System.currentTimeMillis()) {
                                    viewModel.onSilentModeUntilChanged(picked.timeInMillis)
                                }
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun FrequencyOption(label: String, selected: Boolean, onClick: () -> Unit) {
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
