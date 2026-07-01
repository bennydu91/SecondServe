package com.secondserve.feature.coaching

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondserve.core.ui.components.CoachingCard
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.BroadcastSpacing
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.domain.model.CoachingAnalysis
import org.orbitmvi.orbit.compose.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val synthDateFormat = SimpleDateFormat("dd MMM", Locale.FRANCE)

@Composable
fun CoachingScreen(
    viewModel: CoachingViewModel = hiltViewModel()
) {
    val state by viewModel.collectAsState()
    val colors = LocalBroadcastColors.current

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.lime)
        }
        return
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = BroadcastSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BroadcastSpacing.md)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Coaching",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.lime)
            }
        }

        item {
            Text(
                text = "SYNTHÈSE MULTI-MATCHS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.faint,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        item {
            state.synthesis?.let { synth ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(BroadcastRadius.card),
                    color = colors.panelHigh
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(colors.lime.copy(alpha = 0.08f), colors.panelHigh),
                                    start = Offset(0f, 0f),
                                    end = Offset(300f, 400f)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = synth.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text.copy(alpha = 0.92f)
                            )
                            Spacer(Modifier.height(14.dp))
                            val dateStr = if (synth.generatedAt > 0L) synthDateFormat.format(Date(synth.generatedAt)) else "date inconnue"
                            Text(
                                text = "${synth.sessionCount} matchs · généré le $dateStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted
                            )
                        }
                    }
                }
            } ?: Text(
                text = "Aucune synthèse disponible. Générez-en une ci-dessous.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            if (state.synthesisInProgress) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.lime, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.generateNow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(BroadcastRadius.input),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.lime)
                ) {
                    Text("Regénérer la synthèse", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        state.error?.let { err ->
            item {
                Text(text = err, color = colors.hot, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.analyses.isNotEmpty()) {
            item {
                Text(
                    text = "ANALYSES POST-MATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                )
            }
            items(state.analyses) { analysis ->
                AnalysisItem(analysis)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AnalysisItem(analysis: CoachingAnalysis) {
    val colors = LocalBroadcastColors.current
    val dateStr = if (analysis.generatedAt > 0L) synthDateFormat.format(Date(analysis.generatedAt)) else "date inconnue"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.input),
        color = colors.panelHigh
    ) {
        Column(Modifier.padding(BroadcastSpacing.lg)) {
            Text(
                text = analysis.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(BroadcastSpacing.sm))
            Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = colors.faint)
        }
    }
}
