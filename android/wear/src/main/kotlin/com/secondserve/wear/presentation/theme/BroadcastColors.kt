package com.secondserve.wear.presentation.theme

import androidx.compose.ui.graphics.Color

// Tokens couleur du design system "Broadcast" (handoff SecondServe UX/UI redesign),
// dupliqués depuis :core:ui — cf. Fonts.kt pour la raison de la duplication.
object BroadcastColors {
    val void = Color(0xFF0C0D0F)
    val panel = Color(0xFF16181C)
    val panelHigh = Color(0xFF131518)
    val line = Color(0xFF24272D)
    val text = Color(0xFFF2F3F0)
    val muted = Color(0xFF8A8F98)
    val faint = Color(0xFF6B7079)
    val lime = Color(0xFFC8FF3D)
    val limeDim = Color(0xFFA3D62F)
    val onLime = Color(0xFF0C0D0F)
    val hot = Color(0xFFFF5C7A)
    val hotDim = Color(0xFFCC4A62)
    val data = Color(0xFF4EA8FF)
    val dataDim = Color(0xFF3E86CC)
    val defeatBg = Color(0xFFFF5C7A).copy(alpha = 0.14f)
}
