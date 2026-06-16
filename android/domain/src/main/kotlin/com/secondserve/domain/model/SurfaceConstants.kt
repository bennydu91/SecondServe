package com.secondserve.domain.model

object SurfaceConstants {
    const val CLAY = "CLAY"
    const val GRASS = "GRASS"
    const val HARD = "HARD"
    const val CARPET = "CARPET"

    val ALL = listOf(CLAY, GRASS, HARD, CARPET)

    val DISPLAY_NAMES = mapOf(
        CLAY to "Terre battue",
        GRASS to "Gazon",
        HARD to "Dur",
        CARPET to "Carpet"
    )
}
