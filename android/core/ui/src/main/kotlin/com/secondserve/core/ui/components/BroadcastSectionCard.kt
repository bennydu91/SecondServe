package com.secondserve.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.LocalBroadcastColors

/** Titre de forme (identité/réglages) vs titre de carte stat (label discret + icône optionnelle). */
enum class SectionCardTitleStyle { FORM, STAT }

/**
 * Carte de section titrée (`panelHigh`, radius card, padding 20dp) — cf. README "Composants
 * récurrents". Remplace les `SectionCard`/`StatsCard` locales dupliquées dans ProfileScreen et
 * StatsScreen.
 */
@Composable
fun BroadcastSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    titleStyle: SectionCardTitleStyle = SectionCardTitleStyle.FORM,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalBroadcastColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.card),
        color = colors.panelHigh
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            when (titleStyle) {
                SectionCardTitleStyle.FORM -> Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text
                )
                SectionCardTitleStyle.STAT -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                    Text(text = title, style = MaterialTheme.typography.labelSmall, color = colors.muted)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
