package com.secondserve.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens couleur du design system "Broadcast" (handoff SecondServe UX/UI redesign).
 * Valeurs verrouillées — cf. design_handoff_secondserve/README.md, section "Design Tokens".
 */
data class BroadcastColors(
    val void: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val lime: Color,
    val onLime: Color,
    val hot: Color,
    val data: Color,
    val clay: Color,
    val hard: Color,
    val grass: Color,
    val indoor: Color,
    val victoryBg: Color,
    val victoryText: Color,
    val defeatBg: Color,
    val defeatText: Color,
    val indoorBadgeBg: Color
)

val BroadcastDarkColors = BroadcastColors(
    void = Color(0xFF0C0D0F),
    panel = Color(0xFF16181C),
    panelHigh = Color(0xFF131518),
    line = Color(0xFF24272D),
    text = Color(0xFFF2F3F0),
    muted = Color(0xFF8A8F98),
    faint = Color(0xFF6B7079),
    lime = Color(0xFFC8FF3D),
    onLime = Color(0xFF0C0D0F),
    hot = Color(0xFFFF5C7A),
    data = Color(0xFF4EA8FF),
    clay = Color(0xFFE0703F),
    hard = Color(0xFF3E8EF0),
    grass = Color(0xFF4FB477),
    indoor = Color(0xFFA97CF0),
    victoryBg = Color(0xFFC8FF3D).copy(alpha = 0.14f),
    victoryText = Color(0xFFC8FF3D),
    defeatBg = Color(0xFFFF5C7A).copy(alpha = 0.14f),
    defeatText = Color(0xFFFF5C7A),
    indoorBadgeBg = Color(0xFFA97CF0).copy(alpha = 0.14f)
)

val LocalBroadcastColors = staticCompositionLocalOf { BroadcastDarkColors }

/**
 * Couleur d'accent pour une clé de surface de court (valeurs de
 * `com.secondserve.domain.model.SurfaceConstants` : CLAY/HARD/GRASS/CARPET). Dupliquée en
 * littéraux plutôt que dépendre du module domain, pour garder core:ui sans dépendance métier.
 */
fun BroadcastColors.forSurfaceKey(key: String?): Color = when (key) {
    "CLAY" -> clay
    "HARD" -> hard
    "GRASS" -> grass
    "CARPET" -> indoor
    else -> faint
}
