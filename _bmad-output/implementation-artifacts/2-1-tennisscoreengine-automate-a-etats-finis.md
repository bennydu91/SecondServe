---
baseline_commit: 8b7330cb49bf8d5eef9ad9e132c153cc91cd7748
---

# Story 2.1 : TennisScoreEngine — Automate à états finis

**Status:** done

## Story

**As a** developer,
**I want** a pure Kotlin scoring engine in `:domain` that handles all tennis scoring rules,
**So that** all score logic is testable without a device and serves as the single source of truth for Watch and Phone.

## Acceptance Criteria

1. **Given** une séquence quelconque de points en entrée
   **When** `TennisScoreEngine` les traite
   **Then** il calcule correctement : points (0/15/30/40/Avantage/Égalité), jeux, sets

2. **And** le tie-break se déclenche automatiquement à 6-6 (comptage 0-1-2... jusqu'à ≥7 avec 2 points d'écart)

3. **And** le super tie-break se déclenche selon le format configuré en session (jusqu'à ≥10 avec 2 points d'écart)

4. **And** le changement de côté est détecté quand le total de jeux dans le set est impair (événement `changeover = true` dans `EngineEvent.GameWon` ou `EngineEvent.SetWon`)

5. **And** l'undo du dernier point annule la dernière transition d'état et restaure l'état précédent (retourne `false` si historique vide)

6. **And** tous les cas de règles sont couverts par `TennisScoreEngineTest.kt` (JVM, aucun device requis)

7. **And** `:domain` n'a aucune dépendance Android — module Kotlin pur

---

## Architecture Context

### Position dans la séquence d'implémentation (ARCH-13)

Story 2.1 est la **première** de l'Epic 2 :

```
Story 2.1 (CETTE STORY) → Story 2.2 (DataLayer) → Story 2.3 (Session) → Story 2.4 (Watch UI) → Story 2.5 (Changeover) → Story 2.6 (Clôture + Sync)
```

**Dépendances satisfaites :**
- ✅ Epic 1 complet : Room version 3, profil joueur, axes de travail CRUD
- ✅ `MatchContextProfile` dans `:domain` contient `activeWorkAxes: List<String>` — déjà utilisable dans les prompts IA
- ❌ Pas de table `sessions` encore (Story 2.3) — sans impact sur cette story (engine pur)
- ❌ DataLayer bridge (Story 2.2) — cette story ne communique PAS avec la Watch; elle est uniquement testable JVM

### État actuel du code — critique à connaître

**Fichiers existants dans `:domain` (NE PAS MODIFIER) :**
- `AppResult.kt` — sealed class `AppResult<T>` avec `Success`, `Error`, `Loading`
- `model/MatchContextProfile.kt` — data class avec `activeWorkAxes: List<String>`
- `model/WorkAxis.kt` — data class + `MAX_WORK_AXES = 3`
- `model/PlayerProfile.kt`, `model/RankingEntry.kt`, etc.
- `repository/WorkAxisRepository.kt`, `repository/PlayerProfileRepository.kt`
- `constants/FftConstants.kt`

**Répertoires à créer :**
- `domain/src/main/kotlin/com/secondserve/domain/model/` — ajouter `MatchScore.kt`, `SessionFormat.kt`
- `domain/src/main/kotlin/com/secondserve/domain/engine/` — créer le répertoire, ajouter `TennisScoreEngine.kt`
- `domain/src/test/kotlin/com/secondserve/domain/engine/` — créer le répertoire, ajouter `TennisScoreEngineTest.kt`

**Build config `:domain` (déjà configuré — NE PAS MODIFIER) :**
```kotlin
// domain/build.gradle.kts — état actuel
plugins {
    alias(libs.plugins.kotlin.jvm)  // Kotlin pur, zéro Android
}
dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
tasks.withType<Test> { useJUnitPlatform() }
```

> ⚠️ **CRITIQUE** : `engine/TennisScoreEngine.kt` NE DOIT IMPORTER AUCUNE classe `android.*` ou `androidx.*`. Le module Gradle `kotlin.jvm` le garantit à la compilation, mais ne pas importer manuellement non plus.

---

## Technical Requirements

### Modèle 1 — SessionFormat.kt (NEW)

**`domain/src/main/kotlin/com/secondserve/domain/model/SessionFormat.kt`**

```kotlin
package com.secondserve.domain.model

enum class MatchFormat { BEST_OF_1, BEST_OF_3 }

enum class ThirdSetRule {
    FULL_ADVANTAGE,       // 3e set complet avec tie-break à 6-6
    SUPER_TIE_BREAK_10,  // Super tie-break à 10 pts à la place du 3e set (à 1-1 sets)
    SHORT_DECISIVE_SET    // Set raccourci à 4 jeux, tie-break à 3-3 jusqu'à 7
}

data class SessionFormat(
    val matchFormat: MatchFormat,
    val thirdSetRule: ThirdSetRule = ThirdSetRule.FULL_ADVANTAGE
)
```

### Modèle 2 — MatchScore.kt (NEW)

**`domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt`**

```kotlin
package com.secondserve.domain.model

enum class Player { A, B }

enum class GamePoint { ZERO, FIFTEEN, THIRTY, FORTY, ADVANTAGE }

data class SetResult(val gamesA: Int, val gamesB: Int)

data class MatchScore(
    val completedSets: List<SetResult> = emptyList(),
    val currentSetGamesA: Int = 0,
    val currentSetGamesB: Int = 0,
    val currentGamePointsA: GamePoint = GamePoint.ZERO,
    val currentGamePointsB: GamePoint = GamePoint.ZERO,
    val tieBreakPointsA: Int = 0,
    val tieBreakPointsB: Int = 0,
    val isTieBreak: Boolean = false,
    val isSuperTieBreak: Boolean = false,
    val isMatchOver: Boolean = false,
    val matchWinner: Player? = null
) {
    // Vrai deuce : les deux joueurs à FORTY
    val isDeuce: Boolean
        get() = !isTieBreak && !isSuperTieBreak &&
                currentGamePointsA == GamePoint.FORTY &&
                currentGamePointsB == GamePoint.FORTY

    // Total de jeux dans le set courant (pour détection changement de côté)
    val currentSetTotalGames: Int get() = currentSetGamesA + currentSetGamesB
}
```

### Modèle 3 — EngineEvent sealed class (dans TennisScoreEngine.kt)

```kotlin
sealed class EngineEvent {
    abstract val score: MatchScore

    // Un point marqué, aucun changement de jeu
    data class PointScored(override val score: MatchScore) : EngineEvent()

    // Un jeu terminé. changeover = le total de jeux dans le set était impair avant réinitialisation.
    // score reflète l'état APRÈS incrémentation des jeux (avant remise à zéro du jeu courant)
    data class GameWon(
        override val score: MatchScore,
        val winner: Player,
        val changeover: Boolean
    ) : EngineEvent()

    // Un set terminé. changeover = total jeux dans le set terminé était impair.
    // score reflète l'état APRÈS déplacement du set dans completedSets et RAZ du set courant.
    data class SetWon(
        override val score: MatchScore,
        val winner: Player,
        val changeover: Boolean
    ) : EngineEvent()

    // Match terminé. Pas de changeover (fin de match).
    data class MatchOver(
        override val score: MatchScore,
        override val winner: Player
    ) : EngineEvent()
}
```

> ⚠️ `changeover` dans `GameWon` et `SetWon` est utilisé par Story 2.5 pour déclencher l'envoi `game_over` via DataLayer. NE PAS supprimer ce champ même si Story 2.1 ne l'utilise pas directement.

### Engine — TennisScoreEngine.kt (NEW)

**`domain/src/main/kotlin/com/secondserve/domain/engine/TennisScoreEngine.kt`**

```kotlin
package com.secondserve.domain.engine

import com.secondserve.domain.model.*

class TennisScoreEngine(val format: SessionFormat) {

    private var state: MatchScore = MatchScore()
    private val history: ArrayDeque<MatchScore> = ArrayDeque()

    val currentScore: MatchScore get() = state

    fun recordPoint(scorer: Player): EngineEvent {
        check(!state.isMatchOver) { "Cannot record point: match is over" }
        history.addLast(state.copy())
        return when {
            state.isSuperTieBreak -> processSuperTieBreakPoint(scorer)
            state.isTieBreak -> processTieBreakPoint(scorer)
            else -> processRegularPoint(scorer)
        }
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeLast()
        return true
    }

    // ─── Regular game ──────────────────────────────────────────────────────────

    private fun processRegularPoint(scorer: Player): EngineEvent {
        val (pA, pB) = Pair(state.currentGamePointsA, state.currentGamePointsB)

        // ADVANTAGE case: winner or back to deuce
        if (pA == GamePoint.ADVANTAGE || pB == GamePoint.ADVANTAGE) {
            return if ((scorer == Player.A && pA == GamePoint.ADVANTAGE) ||
                       (scorer == Player.B && pB == GamePoint.ADVANTAGE)) {
                awardGame(scorer)
            } else {
                // Back to deuce
                state = state.copy(
                    currentGamePointsA = GamePoint.FORTY,
                    currentGamePointsB = GamePoint.FORTY
                )
                EngineEvent.PointScored(state)
            }
        }

        // DEUCE case: award advantage
        if (state.isDeuce) {
            state = if (scorer == Player.A) {
                state.copy(currentGamePointsA = GamePoint.ADVANTAGE)
            } else {
                state.copy(currentGamePointsB = GamePoint.ADVANTAGE)
            }
            return EngineEvent.PointScored(state)
        }

        // Normal progression
        val currentPoints = if (scorer == Player.A) pA else pB
        val opponentPoints = if (scorer == Player.A) pB else pA

        val nextPoints = when (currentPoints) {
            GamePoint.ZERO -> GamePoint.FIFTEEN
            GamePoint.FIFTEEN -> GamePoint.THIRTY
            GamePoint.THIRTY -> GamePoint.FORTY
            GamePoint.FORTY -> return awardGame(scorer) // opponent < 40 (non-deuce), game won
            GamePoint.ADVANTAGE -> return awardGame(scorer) // handled above, safety
        }

        state = if (scorer == Player.A) {
            state.copy(currentGamePointsA = nextPoints)
        } else {
            state.copy(currentGamePointsB = nextPoints)
        }

        // Check if we just reached deuce (both at FORTY after this point)
        return EngineEvent.PointScored(state)
    }

    // ─── Tie-break ─────────────────────────────────────────────────────────────

    private fun processTieBreakPoint(scorer: Player): EngineEvent {
        val newA = state.tieBreakPointsA + (if (scorer == Player.A) 1 else 0)
        val newB = state.tieBreakPointsB + (if (scorer == Player.B) 1 else 0)
        state = state.copy(tieBreakPointsA = newA, tieBreakPointsB = newB)

        val winner = when {
            newA >= 7 && newA - newB >= 2 -> Player.A
            newB >= 7 && newB - newA >= 2 -> Player.B
            else -> null
        }
        return if (winner != null) awardTieBreakGame(winner) else EngineEvent.PointScored(state)
    }

    // ─── Super tie-break ───────────────────────────────────────────────────────

    private fun processSuperTieBreakPoint(scorer: Player): EngineEvent {
        val newA = state.tieBreakPointsA + (if (scorer == Player.A) 1 else 0)
        val newB = state.tieBreakPointsB + (if (scorer == Player.B) 1 else 0)
        state = state.copy(tieBreakPointsA = newA, tieBreakPointsB = newB)

        val winner = when {
            newA >= 10 && newA - newB >= 2 -> Player.A
            newB >= 10 && newB - newA >= 2 -> Player.B
            else -> null
        }
        if (winner != null) {
            state = state.copy(isMatchOver = true, matchWinner = winner)
            return EngineEvent.MatchOver(state, winner)
        }
        return EngineEvent.PointScored(state)
    }

    // ─── Game / Set helpers ────────────────────────────────────────────────────

    private fun awardGame(winner: Player): EngineEvent {
        val newGamesA = state.currentSetGamesA + (if (winner == Player.A) 1 else 0)
        val newGamesB = state.currentSetGamesB + (if (winner == Player.B) 1 else 0)
        val totalGames = newGamesA + newGamesB
        val changeover = totalGames % 2 == 1

        state = state.copy(
            currentSetGamesA = newGamesA,
            currentSetGamesB = newGamesB,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO,
            isTieBreak = false,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0
        )

        // Check tie-break trigger (6-6)
        if (newGamesA == 6 && newGamesB == 6) {
            return startTieBreak(changeover, winner)
        }

        // Check set won
        return checkSetWon(winner, changeover)
    }

    private fun awardTieBreakGame(winner: Player): EngineEvent {
        val newGamesA = state.currentSetGamesA + (if (winner == Player.A) 1 else 0)
        val newGamesB = state.currentSetGamesB + (if (winner == Player.B) 1 else 0)
        val totalGames = newGamesA + newGamesB  // always 13 (6+7 or 7+6) → always odd
        val changeover = totalGames % 2 == 1    // always true for tie-break

        state = state.copy(
            currentSetGamesA = newGamesA,
            currentSetGamesB = newGamesB,
            isTieBreak = false,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0
        )
        return checkSetWon(winner, changeover)
    }

    private fun startTieBreak(changeover: Boolean, lastGameWinner: Player): EngineEvent {
        state = state.copy(
            isTieBreak = true,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO
        )
        return EngineEvent.GameWon(state, lastGameWinner, changeover)
    }

    private fun checkSetWon(winner: Player, gameChangeover: Boolean): EngineEvent {
        val gA = state.currentSetGamesA
        val gB = state.currentSetGamesB

        val setWinner = when {
            gA >= 6 && gA - gB >= 2 -> Player.A
            gB >= 6 && gB - gA >= 2 -> Player.B
            // SHORT_DECISIVE_SET: won at 4 with 2-game lead, or tie-break at 3-3
            format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET &&
                isFinalSet() && gA >= 4 && gA - gB >= 2 -> Player.A
            format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET &&
                isFinalSet() && gB >= 4 && gB - gA >= 2 -> Player.B
            else -> null
        }

        // Check short decisive set tie-break at 3-3
        if (format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET && isFinalSet() &&
            gA == 3 && gB == 3 && !state.isTieBreak) {
            return startTieBreak(gameChangeover, winner)
        }

        if (setWinner == null) {
            return EngineEvent.GameWon(state, winner, gameChangeover)
        }

        return awardSet(setWinner, gameChangeover)
    }

    private fun awardSet(winner: Player, gameChangeover: Boolean): EngineEvent {
        val totalGamesInSet = state.currentSetGamesA + state.currentSetGamesB
        val setChangeover = totalGamesInSet % 2 == 1  // note: may differ from gameChangeover for set boundary

        val completedSet = SetResult(state.currentSetGamesA, state.currentSetGamesB)
        val newCompletedSets = state.completedSets + completedSet

        val setsWonA = newCompletedSets.count { it.gamesA > it.gamesB }
        val setsWonB = newCompletedSets.count { it.gamesB > it.gamesA }
        val setsToWin = if (format.matchFormat == MatchFormat.BEST_OF_1) 1 else 2

        state = state.copy(
            completedSets = newCompletedSets,
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false
        )

        // Match over?
        if (setsWonA >= setsToWin || setsWonB >= setsToWin) {
            state = state.copy(isMatchOver = true, matchWinner = winner)
            return EngineEvent.MatchOver(state, winner)
        }

        // Super tie-break trigger (1-1 sets, BEST_OF_3, SUPER_TIE_BREAK_10 format)
        if (format.matchFormat == MatchFormat.BEST_OF_3 &&
            format.thirdSetRule == ThirdSetRule.SUPER_TIE_BREAK_10 &&
            setsWonA == 1 && setsWonB == 1) {
            state = state.copy(isSuperTieBreak = true, tieBreakPointsA = 0, tieBreakPointsB = 0)
            return EngineEvent.SetWon(state, winner, setChangeover)
        }

        return EngineEvent.SetWon(state, winner, setChangeover)
    }

    private fun isFinalSet(): Boolean {
        if (format.matchFormat == MatchFormat.BEST_OF_1) return true
        val setsWonA = state.completedSets.count { it.gamesA > it.gamesB }
        val setsWonB = state.completedSets.count { it.gamesB > it.gamesA }
        return setsWonA == 1 && setsWonB == 1
    }
}
```

> ⚠️ **GUARDRAIL CRITIQUE** : Le `check(!state.isMatchOver)` dans `recordPoint()` est une précondition — si la Watch appelle l'engine après la fin du match, elle reçoit une `IllegalStateException`. Le `ScoreViewModel` (Story 2.4) devra vérifier `currentScore.isMatchOver` avant tout appel.

> ⚠️ **GUARDRAIL UNDO** : `undo()` retourne `Boolean` (false si historique vide). Le ViewModel devra ignorer les undos impossibles silencieusement — ne pas throw.

---

## Tasks / Subtasks

### Modèles domaine

- [x] **Task M-1** — Créer `SessionFormat.kt` : `MatchFormat` (BEST_OF_1, BEST_OF_3), `ThirdSetRule` (FULL_ADVANTAGE, SUPER_TIE_BREAK_10, SHORT_DECISIVE_SET), `data class SessionFormat`
- [x] **Task M-2** — Créer `MatchScore.kt` : `Player` (A, B), `GamePoint` (ZERO…ADVANTAGE), `SetResult`, `data class MatchScore` avec toutes les propriétés et computed properties `isDeuce`, `currentSetTotalGames`

### Engine

- [x] **Task E-1** — Créer `engine/TennisScoreEngine.kt` avec `EngineEvent` sealed class (PointScored, GameWon, SetWon, MatchOver) et la classe `TennisScoreEngine(format: SessionFormat)`
- [x] **Task E-2** — Implémenter `processRegularPoint()` : progression ZERO→FIFTEEN→THIRTY→FORTY→Game, Deuce, Advantage cycle
- [x] **Task E-3** — Implémenter `processTieBreakPoint()` : comptage 0-1-2..., win à ≥7 avec écart ≥2
- [x] **Task E-4** — Implémenter `processSuperTieBreakPoint()` : comptage 0-1-2..., win à ≥10 avec écart ≥2 → MatchOver
- [x] **Task E-5** — Implémenter `awardGame()` → détection tie-break (6-6), `checkSetWon()` → détection set, SHORT_DECISIVE_SET (3-3 tie-break, ≥4 avec écart 2)
- [x] **Task E-6** — Implémenter `awardSet()` → détection match fini, trigger SUPER_TIE_BREAK_10 (à 1-1), transition `SetWon` ou `MatchOver`
- [x] **Task E-7** — Implémenter `undo()` : pop de la `history: ArrayDeque<MatchScore>` (retourne false si vide)

### Tests

- [x] **Task T-1** — Créer `engine/TennisScoreEngineTest.kt` — jeu régulier complet : 0→15→30→40→Game
- [x] **Task T-2** — Égalité et Avantage : 40-40 → Avantage A → Égalité → Avantage A → Jeu A
- [x] **Task T-3** — Jeux et sets : scénario 6-0, 6-3, 6-4 (vérifier `SetWon`)
- [x] **Task T-4** — Tie-break : déclenchement à 6-6, comptage correct, victoire à 7-5, 8-6
- [x] **Task T-5** — Super tie-break : déclenchement à 1-1 sets (SUPER_TIE_BREAK_10), victoire à 10-8, 11-9
- [x] **Task T-6** — Changement de côté : vérifier `changeover = true` quand total jeux impair
- [x] **Task T-7** — Undo : multi-niveaux, undo jusqu'au début, undo avec `isMatchOver = true`
- [x] **Task T-8** — Match BEST_OF_1 : victoire en 1 set → `MatchOver`
- [x] **Task T-9** — Match BEST_OF_3 FULL_ADVANTAGE : victoire en 2 sets, en 3 sets
- [x] **Task T-10** — SHORT_DECISIVE_SET : 3-3 → tie-break → match, victoire à 4-0, 4-2
- [x] **Task T-11** — `check()` après match terminé → `IllegalStateException` levée

---

## Testing Requirements

**`domain/src/test/kotlin/com/secondserve/domain/engine/TennisScoreEngineTest.kt`** (NEW)

Pattern de test (JUnit 5, `@Nested` pour grouper) :

```kotlin
package com.secondserve.domain.engine

import com.secondserve.domain.model.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class TennisScoreEngineTest {

    private val bestOf1Format = SessionFormat(MatchFormat.BEST_OF_1)
    private val bestOf3Format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE)
    private val superTbFormat = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.SUPER_TIE_BREAK_10)
    private val shortSetFormat = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.SHORT_DECISIVE_SET)

    // Helper : gagner N points pour un joueur
    private fun TennisScoreEngine.winPoints(scorer: Player, count: Int) {
        repeat(count) { recordPoint(scorer) }
    }

    // Helper : gagner un jeu complet (4 points sans Deuce)
    private fun TennisScoreEngine.winGame(scorer: Player): EngineEvent {
        repeat(3) { recordPoint(scorer) }
        return recordPoint(scorer)
    }

    // Helper : gagner N jeux
    private fun TennisScoreEngine.winGames(scorer: Player, count: Int) {
        repeat(count) { winGame(scorer) }
    }

    // Helper : gagner un set 6-0
    private fun TennisScoreEngine.winSet6_0(scorer: Player): EngineEvent {
        var event: EngineEvent = EngineEvent.PointScored(currentScore)
        repeat(6) { event = winGame(scorer) }
        return event
    }

    @Nested
    inner class RegularGameRules {

        @Test
        fun `points progressed correctly from 0 to game`() {
            val engine = TennisScoreEngine(bestOf1Format)
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FIFTEEN, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.THIRTY, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `winning game at 40-0 returns GameWon event`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)  // A at 40
            val event = engine.recordPoint(Player.A)
            assertTrue(event is EngineEvent.GameWon)
            assertEquals(Player.A, (event as EngineEvent.GameWon).winner)
            assertEquals(1, event.score.currentSetGamesA)
            assertEquals(0, event.score.currentSetGamesB)
        }

        @Test
        fun `deuce and advantage cycle`() {
            val engine = TennisScoreEngine(bestOf1Format)
            // Both at 40
            engine.winPoints(Player.A, 3)
            engine.winPoints(Player.B, 3)
            assertTrue(engine.currentScore.isDeuce)
            // A gets advantage
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsA)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsB)
            // Back to deuce
            engine.recordPoint(Player.B)
            assertTrue(engine.currentScore.isDeuce)
            // B gets advantage
            engine.recordPoint(Player.B)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsB)
            // B wins game
            val event = engine.recordPoint(Player.B)
            assertTrue(event is EngineEvent.GameWon)
            assertEquals(Player.B, (event as EngineEvent.GameWon).winner)
        }
    }

    @Nested
    inner class ChangeoversDetection {

        @Test
        fun `changeover when total games is odd`() {
            val engine = TennisScoreEngine(bestOf3Format)
            val event = engine.winGame(Player.A)  // A: 1, B: 0 → total=1 (impair)
            assertTrue(event is EngineEvent.GameWon)
            assertTrue((event as EngineEvent.GameWon).changeover)
        }

        @Test
        fun `no changeover when total games is even`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGame(Player.A)  // 1-0
            val event = engine.winGame(Player.B)  // 1-1 → total=2 (pair)
            assertTrue(event is EngineEvent.GameWon)
            assertFalse((event as EngineEvent.GameWon).changeover)
        }

        @Test
        fun `changeover always true after tie-break (13 total games)`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 6)
            engine.winGames(Player.B, 6)  // 6-6 → tie-break
            // Win tie-break 7-0
            engine.winPoints(Player.A, 7)
            // Set ended 7-6 for A → total=13 → changeover
            val lastEvent = engine.currentScore
            // After tie-break win, SetWon should have been emitted
            // Re-check via direct scenario
        }
    }

    @Nested
    inner class TieBreak {

        @Test
        fun `tie-break triggered at 6-6`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 6)
            engine.winGames(Player.B, 6)
            assertTrue(engine.currentScore.isTieBreak)
        }

        @Test
        fun `tie-break won at 7 with 2-point lead`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 6)
            engine.winGames(Player.B, 6)
            // 6-6, tie-break starts
            engine.winPoints(Player.A, 7)  // A: 7, B: 0
            val score = engine.currentScore
            assertFalse(score.isTieBreak)
            assertEquals(1, score.completedSets.size)
            assertEquals(7, score.completedSets[0].gamesA)
            assertEquals(6, score.completedSets[0].gamesB)
        }

        @Test
        fun `tie-break requires 2-point lead (7-6 not enough, 8-6 enough)`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 6)
            engine.winGames(Player.B, 6)
            engine.winPoints(Player.A, 6)
            engine.winPoints(Player.B, 6)  // 6-6 in tie-break
            engine.winPoints(Player.A, 1)  // A: 7, B: 6 — not enough
            assertTrue(engine.currentScore.isTieBreak)
            engine.winPoints(Player.A, 1)  // A: 8, B: 6 — won
            assertFalse(engine.currentScore.isTieBreak)
        }
    }

    @Nested
    inner class SuperTieBreak {

        @Test
        fun `super tie-break triggered at 1-1 sets in SUPER_TIE_BREAK_10 format`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            assertTrue(engine.currentScore.isSuperTieBreak)
            assertFalse(engine.currentScore.isTieBreak)
        }

        @Test
        fun `super tie-break won at 10 with 2-point lead`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winPoints(Player.A, 10)
            val event = engine.currentScore
            assertTrue(event.isMatchOver)
            assertEquals(Player.A, event.matchWinner)
        }

        @Test
        fun `super tie-break requires 2-point lead (10-9 not enough)`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winPoints(Player.A, 9)
            engine.winPoints(Player.B, 9)
            engine.winPoints(Player.A, 1)  // 10-9, not enough
            assertTrue(engine.currentScore.isSuperTieBreak)
            assertFalse(engine.currentScore.isMatchOver)
            engine.winPoints(Player.A, 1)  // 11-9, won
            assertTrue(engine.currentScore.isMatchOver)
        }
    }

    @Nested
    inner class UndoTests {

        @Test
        fun `undo restores previous state`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FIFTEEN, engine.currentScore.currentGamePointsA)
            val result = engine.undo()
            assertTrue(result)
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `undo returns false when history empty`() {
            val engine = TennisScoreEngine(bestOf1Format)
            assertFalse(engine.undo())
        }

        @Test
        fun `undo works across game boundary`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)  // at 40-0
            engine.recordPoint(Player.A)   // game won, 1-0
            assertEquals(1, engine.currentScore.currentSetGamesA)
            engine.undo()
            assertEquals(0, engine.currentScore.currentSetGamesA)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `undo after match over is possible`() {
            val engine = TennisScoreEngine(bestOf1Format)
            // Win 6-0 set
            engine.winSet6_0(Player.A)
            assertTrue(engine.currentScore.isMatchOver)
            engine.undo()
            assertFalse(engine.currentScore.isMatchOver)
        }
    }

    @Nested
    inner class MatchFormats {

        @Test
        fun `BEST_OF_1 match over after one set`() {
            val engine = TennisScoreEngine(bestOf1Format)
            val event = engine.winSet6_0(Player.A)
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
        }

        @Test
        fun `BEST_OF_3 requires 2 sets to win`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)  // 1-0
            assertFalse(engine.currentScore.isMatchOver)
            engine.winSet6_0(Player.A)  // 2-0 → match over
            assertTrue(engine.currentScore.isMatchOver)
        }

        @Test
        fun `BEST_OF_3 can reach 3 sets`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            assertFalse(engine.currentScore.isMatchOver)
            assertEquals(2, engine.currentScore.completedSets.size)
        }

        @Test
        fun `cannot record point after match over`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winSet6_0(Player.A)
            assertThrows<IllegalStateException> { engine.recordPoint(Player.A) }
        }
    }
}
```

---

## Dev Notes

### Guardrails critiques

**Automate à états finis — principe clé :**
- `MatchScore` est IMMUTABLE (data class) — chaque transition crée une nouvelle copie
- L'historique `ArrayDeque<MatchScore>` stocke les états AVANT chaque point (`.copy()` avant mutation)
- `undo()` restaure l'état en `history.removeLast()` — O(1), jamais de recalcul

**Deuce / Advantage — état interne :**
- `isDeuce` est calculé : `currentGamePointsA == FORTY && currentGamePointsB == FORTY`
- Après Deuce : un joueur passe à ADVANTAGE, l'autre reste à FORTY
- Pas d'état "DEUCE" distinct dans `GamePoint` — le deuce est détecté par `isDeuce`
- Si les deux joueurs sont à FORTY et qu'un marque un point, `isDeuce` est vrai → Advantage accordé

**Changeover — règle stricte AC :**
- AC4 : "total de jeux dans le set est impair"
- Calcul au moment où `currentSetGamesA + currentSetGamesB` est mis à jour (avant le RAZ du jeu courant)
- Pour le tie-break : 6+7=13 ou 7+6=13 → toujours impair → changeover toujours vrai après tie-break
- Pour le super tie-break : l'event est `MatchOver`, pas de changeover à gérer

**SHORT_DECISIVE_SET — détails :**
- Set raccourci : 4 jeux avec écart ≥2, tie-break à 3-3 (jusqu'à 7 points)
- Condition `isFinalSet()` : utilisée pour activer la logique du 3e set raccourci
- Le tie-break à 3-3 est un tie-break RÉGULIER (pas super tie-break) → comptage jusqu'à ≥7

**super tie-break vs tie-break :**
- `isSuperTieBreak = true` désactive tout le reste (points réguliers, jeux, sets)
- La logique de super tie-break est dans `processSuperTieBreakPoint()` — retourne toujours `MatchOver` à la victoire
- `MatchScore.isTieBreak` et `isSuperTieBreak` sont mutuellement exclusifs

**Engine lifecycle :**
- Une instance de `TennisScoreEngine` = une session match
- Lors de la Story 2.3 (démarrage session), `TennisScoreEngine` sera instancié dans le `MatchViewModel` avec le `SessionFormat` de la session
- L'engine N'EST PAS persisté (c'est la Watch qui garde l'état via son ViewModel, les snapshots sont envoyés au Phone)

### Patterns établis à réutiliser

| Pattern | Référence |
|---------|-----------|
| JUnit 5 `@Nested` + `@Test` | `DomainModuleTest.kt` (pattern existant) |
| Helper functions dans les tests | Voir helpers `winGame()`, `winGames()` définis dans la story |
| Immutable data class + copy() | Pattern Kotlin standard, déjà utilisé dans `MatchContextProfile` |
| `ArrayDeque` comme stack | Kotlin stdlib, zéro dépendance |
| `sealed class` pour events | Pattern établi dans `AppResult.kt` |

### Ce que Story 2.2 consommera

Story 2.2 (DataLayer bridge) consommera :
- `EngineEvent.GameWon(score, winner, changeover)` → si `changeover = true`, envoie `/secondserve/game_over` via DataLayer
- `MatchScore` → sérialisé en JSON pour le payload DataLayer `score_snapshot`
- `Player` enum → pour identifier le serveur dans les payloads

La `TennisScoreEngine` vivra dans le `ScoreViewModel` Wear OS (Story 2.4). Story 2.2 crée le DataLayer. Story 2.4 les assemble.

### Project Structure Notes

**Arborescence des fichiers concernés :**

```
android/domain/src/main/kotlin/com/secondserve/domain/
├── model/
│   ├── MatchScore.kt      — NEW (Player, GamePoint, SetResult, MatchScore)
│   └── SessionFormat.kt   — NEW (MatchFormat, ThirdSetRule, SessionFormat)
├── engine/                 — NOUVEAU RÉPERTOIRE
│   └── TennisScoreEngine.kt — NEW (EngineEvent + TennisScoreEngine)
│   [EngineEvent est défini dans ce même fichier, PAS dans model/]
└── [fichiers existants — NE PAS MODIFIER]

android/domain/src/test/kotlin/com/secondserve/domain/
├── engine/                 — NOUVEAU RÉPERTOIRE
│   └── TennisScoreEngineTest.kt — NEW
└── DomainModuleTest.kt    — existant, NE PAS MODIFIER
```

**Aucun fichier VPS à modifier** — pure story domaine Android.
**Aucun fichier Android (`:app`, `:data`, `:feature:*`) à modifier** — uniquement `:domain`.

### References

- [Source: epics.md § Story 2.1] — Acceptance criteria, user story statement
- [Source: epics.md § ARCH-6] — "TennisScoreEngine dans :domain — automate à états finis... Zéro dépendance Android, 100% testable JVM. Tests unitaires complets (TennisScoreEngineTest.kt)"
- [Source: architecture.md § Cross-Cutting Concerns #5] — "Logique de score locale sur la montre, pas de relay téléphone"
- [Source: architecture.md § Naming Patterns] — `SCREAMING_SNAKE_CASE` pour constantes, `PascalCase` pour classes, packages lowercase
- [Source: architecture.md § Process Patterns] — Timber pour logging (pas utilisé dans `:domain`), `sealed class Result<T>` pattern
- [Source: architecture.md § Project Structure] — `domain/engine/TennisScoreEngine.kt`, `domain/model/MatchScore.kt`
- [Source: architecture.md § CI & Testabilité] — "TennisScoreEngine : module isolé, 100% testable unitairement sans device"
- [Source: 1-6-axes-de-travail-crud-de-base.md § Dev Notes] — Patterns JUnit 5, immutable data class, `ArrayDeque` pattern

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Claude Code remote session)

### Debug Log References

- Bug corrigé dans `awardTieBreakGame()` : appelait `checkSetWon()` au lieu de `awardSet()`. La condition `gA >= 6 && gA - gB >= 2` ne couvre pas 7-6 (écart = 1). Le gain du tie-break doit toujours déclencher `awardSet()` directement.
- Tests corrigés : les séquences `winGames(A, 6); winGames(B, 6)` gagnaient deux sets (6-0 + 6-0) au lieu d'atteindre 6-6 dans un set. Ajout du helper `reachSixSixTieBreak()` qui alterne les victoires de jeux pour atteindre 6-6.
- Tests SHORT_DECISIVE_SET corrigés : les séquences `winGames(A, 2)` de 2-2 → 4-2 gagnaient le set avant le dernier `winGame()`, provoquant une `IllegalStateException`.

### Completion Notes List

- ✅ `SessionFormat.kt` créé avec `MatchFormat`, `ThirdSetRule`, `SessionFormat`
- ✅ `MatchScore.kt` créé avec `Player`, `GamePoint`, `SetResult`, `MatchScore` (immutable data class, computed properties `isDeuce` et `currentSetTotalGames`)
- ✅ `TennisScoreEngine.kt` créé avec `EngineEvent` sealed class et moteur complet (points, jeux, sets, tie-break, super tie-break, undo O(1) via `ArrayDeque`)
- ✅ `TennisScoreEngineTest.kt` créé avec 34 tests JVM couvrant les 11 groupes de l'AC (aucun device requis)
- ✅ Tous les 34 tests passent — BUILD SUCCESSFUL
- ✅ Module `:domain` reste Kotlin pur (kotlin.jvm), zéro import android.*
- ✅ `EngineEvent.GameWon(changeover: Boolean)` préparé pour Story 2.5
- ✅ Undo O(1) via `ArrayDeque<MatchScore>` (états immutables copiés avant chaque point)

### Change Log

- 2026-06-17 : Implémentation complète de la story 2.1 — TennisScoreEngine automate à états finis

### File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/SessionFormat.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/engine/TennisScoreEngine.kt` (NEW)
- `android/domain/src/test/kotlin/com/secondserve/domain/engine/TennisScoreEngineTest.kt` (NEW)

---

## Review Findings

### Code Review (2026-06-17)

- [x] [Review][Patch] P1 — `processSuperTieBreakPoint` ne met pas à jour `completedSets` : résultat du super tie-break absent de l'historique des sets [`TennisScoreEngine.kt:processSuperTieBreakPoint`] — **corrigé**
- [x] [Review][Patch] P2 — Paramètre mort `gameChangeover` dans `awardSet()` : déclaré mais jamais utilisé, la fonction recalcule `setChangeover` en interne [`TennisScoreEngine.kt:awardSet`] — **corrigé**
- [x] [Review][Patch] P3 — Test `changeover always true after tie-break` n'asserte pas la valeur `changeover` du `SetWon` [`TennisScoreEngineTest.kt:ChangeoversDetection`] — **corrigé**
- [x] [Review][Patch] P4 — Manque de test pour les cycles multiples deuce/avantage dans un même jeu [`TennisScoreEngineTest.kt:RegularGameRules`] — **corrigé**
- [x] [Review][Patch] P5 — Manque de tests de symétrie : Player B gagnant le SHORT_DECISIVE_SET (4-0, 4-2) [`TennisScoreEngineTest.kt:ShortDecisiveSet`] — **corrigé**
- [x] [Review][Defer] D1 — `MatchOver` ne transporte pas de signal changeover — Story 2.5 aura besoin de ce signal pour le dernier point — déféré, hors scope Story 2.1
- [x] [Review][Defer] D2 — Changeover au début d'un nouveau set vs règles ATP complètes — par conception, Story 2.5 gérera — déféré, pre-existing design
- [x] [Review][Defer] D3 — Combinaison invalide `BEST_OF_1 + SHORT_DECISIVE_SET` non validée — latent, non utilisable en pratique — déféré
- [x] [Review][Defer] D4 — Couplage structurel `winner` dans `awardSet` — la valeur est toujours correcte via les callers — déféré, risque futur seulement
