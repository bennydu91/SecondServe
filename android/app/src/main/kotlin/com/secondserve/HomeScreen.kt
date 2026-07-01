package com.secondserve

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondserve.core.ui.components.MatchListItem
import com.secondserve.core.ui.components.MatchResultBadge
import com.secondserve.core.ui.theme.LocalBroadcastColors
import com.secondserve.core.ui.theme.forSurfaceKey
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SurfaceConstants

@Composable
fun HomeScreen(
    onNavigateToNewMatch: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToCoaching: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalBroadcastColors.current

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.lime)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header : logo + wordmark + avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(colors.lime, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(colors.void, CircleShape)
                    )
                }
                Text(
                    text = "SECONDSERVE",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.text
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToProfile)
                    .background(colors.panel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profil",
                    tint = colors.lime,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = uiState.firstName?.let { "Prêt à jouer, $it ?" } ?: "Prêt à jouer ?",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, letterSpacing = (-0.6).sp),
            fontWeight = FontWeight.Bold,
            color = colors.text
        )

        Button(
            onClick = onNavigateToNewMatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.lime,
                contentColor = colors.void
            )
        ) {
            Text(
                text = "+  Nouveau match",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.winRatePercent != null || uiState.activeStreakCount != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.winRatePercent?.let { rate ->
                    StatTile(value = "$rate%", label = "Win rate", modifier = Modifier.weight(1f))
                }
                uiState.activeStreakCount?.let { count ->
                    StatTile(
                        value = "$count",
                        label = "Victoires d'affilée",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToHistory),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DERNIERS MATCHS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.faint
                )
                Text(
                    text = "Voir tout ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            if (uiState.recentMatchSessions.isEmpty()) {
                EmptyMatchesCard()
            } else {
                uiState.recentMatchSessions.forEach { session ->
                    MatchListItem(
                        accentColor = colors.forSurfaceKey(session.surface),
                        scoreOrTitle = session.scoreText ?: "—",
                        meta = buildString {
                            append(SurfaceConstants.DISPLAY_NAMES[session.surface] ?: session.surface)
                            append(" · vs ")
                            append(session.opponent ?: "Adversaire")
                        },
                        badge = when (session.result) {
                            "VICTORY" -> MatchResultBadge.VICTORY
                            "DEFEAT" -> MatchResultBadge.DEFEAT
                            else -> MatchResultBadge.NONE
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.panelHigh,
            onClick = onNavigateToCoaching
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.lime,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ta synthèse de coaching t'attend",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "Conseils basés sur tes derniers matchs",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                Text(text = "›", color = colors.faint, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.panelHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
                color = colors.lime
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun EmptyMatchesCard() {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.panelHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "🎾", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Premier match",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                text = "Lance ton premier match pour voir tes données ici.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
        }
    }
}
