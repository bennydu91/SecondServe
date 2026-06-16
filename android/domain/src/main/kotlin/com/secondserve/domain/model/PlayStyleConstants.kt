package com.secondserve.domain.model

object PlayStyleConstants {
    const val DEFENSIVE = "DEFENSIVE"
    const val OFFENSIVE = "OFFENSIVE"
    const val COUNTERPUNCHER = "COUNTERPUNCHER"
    const val ALL_COURT = "ALL_COURT"

    val ALL = listOf(DEFENSIVE, OFFENSIVE, COUNTERPUNCHER, ALL_COURT)

    val DISPLAY_NAMES = mapOf(
        DEFENSIVE to "Défenseur",
        OFFENSIVE to "Attaquant",
        COUNTERPUNCHER to "Contre-puncheur",
        ALL_COURT to "All-court"
    )
}
