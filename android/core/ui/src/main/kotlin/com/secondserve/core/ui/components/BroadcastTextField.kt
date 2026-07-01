package com.secondserve.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secondserve.core.ui.theme.BroadcastRadius
import com.secondserve.core.ui.theme.LocalBroadcastColors

/**
 * Champ texte stylé Broadcast (fond `panel`, bordure `lime` au focus) — cf. README
 * "Composants récurrents". Remplace les `OutlinedTextField` stylés manuellement, dupliqués
 * jusqu'ici dans NewMatchScreen, ProfileScreen et SessionDetailScreen.
 */
@Composable
fun BroadcastTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    readOnly: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val colors = LocalBroadcastColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = colors.faint) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BroadcastRadius.input),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.panel,
            unfocusedContainerColor = colors.panel,
            focusedBorderColor = colors.lime,
            unfocusedBorderColor = colors.line,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            focusedLabelColor = colors.lime,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.lime
        )
    )
}
