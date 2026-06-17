package com.secondserve.domain.model

enum class MatchFormat { BEST_OF_1, BEST_OF_3 }

enum class ThirdSetRule {
    FULL_ADVANTAGE,
    SUPER_TIE_BREAK_10,
    SHORT_DECISIVE_SET
}

data class SessionFormat(
    val matchFormat: MatchFormat,
    val thirdSetRule: ThirdSetRule = ThirdSetRule.FULL_ADVANTAGE
)
