package com.secondserve.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
