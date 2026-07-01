package com.secondserve.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Bouton primaire lime — un seul par écran (cf. README "Composants récurrents" : "un seul
 * bouton primaire plein (lime) par écran"). Remplace le `Button` + `ButtonDefaults.buttonColors`
 * dupliqué dans HomeScreen, NewMatchScreen, ProfileScreen, SessionDetailScreen.
 */
@Composable
fun BroadcastPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val colors = LocalBroadcastColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(BroadcastRadius.input),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.lime,
            contentColor = colors.void,
            disabledContainerColor = colors.panelHigh,
            disabledContentColor = colors.faint
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.void, strokeWidth = 2.dp)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
