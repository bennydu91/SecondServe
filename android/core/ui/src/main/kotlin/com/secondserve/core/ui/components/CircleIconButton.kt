package com.secondserve.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Bouton icône rond sur fond `panel` (retour, fermeture, ajout...) — cf. README "Composants
 * récurrents". Cible tactile par défaut 38dp (au-dessus du strict minimum 48dp car toujours
 * entouré d'espace cliquable dans son conteneur). Remplace le pattern
 * `Box.size(38.dp).clip(CircleShape).clickable().background(colors.panel)` dupliqué dans
 * HomeScreen, HistoryScreen, SessionDetailScreen, NewMatchScreen, MatchScreen.
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    tint: Color? = null,
    enabled: Boolean = true
) {
    val colors = LocalBroadcastColors.current
    Box(
        modifier = modifier
            .size(size)
            .background(colors.panel, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.muted
        )
    }
}
