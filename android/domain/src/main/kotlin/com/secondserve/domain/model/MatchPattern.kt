package com.secondserve.domain.model

enum class MatchPattern(val description: String) {
    NEUTRAL_TRANSITION("Jeu équilibré, pas de tendance marquée"),
    FIRST_GAME_WON("Premier jeu du set remporté — avantage psychologique initial"),
    FIRST_GAME_LOST("Premier jeu du set perdu — position de chasseur dès le début"),
    EQUAL_MIDSET("Égalité en milieu de set (2-2, 3-3)"),

    SERVICE_HELD_EASY("Jeu de service tenu sans difficulté"),
    SERVICE_HELD_UNDER_PRESSURE("Jeu de service tenu sous pression (débreakage, égalités)"),
    SERVICE_BROKEN("Perte du jeu de service — break concédé"),

    BREAK_CONFIRMED("Break réalisé puis confirmé — avantage maintenu"),
    BREAK_LOST_AFTER_HOLD("Break perdu après avoir tenu son jeu de break"),
    DOUBLE_BREAK_ADVANTAGE("Avantage de 2 breaks — position dominante"),

    DOMINANT_LEAD("Avance de 3 jeux ou plus dans le set courant"),
    COMEBACK_IN_PROGRESS("Retour dans le match après avoir été mené"),

    SET_WON_DOMINANT("Set remporté avec 3 jeux d'avance ou plus (ex: 6-2, 6-1)"),
    SET_WON_CLOSE("Set remporté de justesse (7-5, 7-6)"),
    SET_LOST_DOMINANT("Set perdu de plus de 2 jeux d'écart"),
    SET_LOST_CLOSE("Set perdu de peu (5-7, 6-7)"),

    TIEBREAK_APPROACHING("Score 5-5 dans le set — tie-break imminent"),
    TIEBREAK_ACTIVE("Tie-break en cours (6-6 atteint)"),
    SUPER_TIEBREAK_ACTIVE("Super tie-break en cours (3e set décisif)"),

    MATCH_POINT_APPROACHING("Position favorable pour conclure le match");

    companion object {
        val GENERIC_FALLBACK_TEXTS: Map<MatchPattern, String> = mapOf(
            NEUTRAL_TRANSITION to "Restez concentré sur votre jeu. Construisez chaque point méthodiquement.",
            FIRST_GAME_WON to "Excellent début ! Maintenez cette intensité dès le premier point.",
            FIRST_GAME_LOST to "Réajustez votre attention. Ce jeu perdu est déjà derrière vous.",
            EQUAL_MIDSET to "Le match est ouvert. Le prochain jeu peut faire basculer l'équilibre.",
            SERVICE_HELD_EASY to "Service solide. Continuez à imposer votre rythme sur votre engagement.",
            SERVICE_HELD_UNDER_PRESSURE to "Bravo d'avoir résisté. Votre mental fait la différence dans les moments clés.",
            SERVICE_BROKEN to "Restez calme. Concentrez-vous sur le jeu adverse — cherchez vos opportunités.",
            BREAK_CONFIRMED to "Break confirmé ! Continuez à presser, ne relâchez pas la pression.",
            BREAK_LOST_AFTER_HOLD to "Le break est perdu mais la partie n'est pas finie. Retrouvez vos automatismes.",
            DOUBLE_BREAK_ADVANTAGE to "Position excellente. Jouez simple, laissez votre adversaire prendre des risques.",
            DOMINANT_LEAD to "Grosse avance acquise. Maintenez votre concentration sans chercher le spectaculaire.",
            COMEBACK_IN_PROGRESS to "Bravo pour ce retour ! Votre adversaire est maintenant sous pression.",
            SET_WON_DOMINANT to "Set maîtrisé. Démarrez le suivant avec la même agressivité.",
            SET_WON_CLOSE to "Set arraché ! Votre mental est votre atout — gardez cette combativité.",
            SET_LOST_DOMINANT to "Refaites-vous. Identifiez ce qui n'a pas fonctionné et ajustez votre tactique.",
            SET_LOST_CLOSE to "Si proche ! Ce résultat prouve que vous avez le niveau. Continuez à presser.",
            TIEBREAK_APPROACHING to "Tie-break en vue. Concentrez-vous sur chaque point, pas sur le score global.",
            TIEBREAK_ACTIVE to "Tie-break : chaque point compte double. Jouez vos coups les plus sûrs en premier.",
            SUPER_TIEBREAK_ACTIVE to "Super tie-break décisif. Allez chercher chaque point avec la même intensité.",
            MATCH_POINT_APPROACHING to "Vous êtes proche de la victoire. Jouez votre jeu, restez dans l'instant présent."
        )
    }
}
