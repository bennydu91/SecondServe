# Partage de lien live — Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un bouton « Partager » à l'écran de match qui génère (ou réutilise) un lien public, déclenche la feuille de partage native Android, et pousse l'état complet du score — y compris le déroulé point par point du set en cours — au backend à chaque point marqué, sans jamais bloquer ou dégrader l'expérience de scoring en cas d'échec réseau.

**Architecture:** `TennisScoreEngine` (source de vérité du scoring, exécuté sur la montre) enregistre désormais qui a remporté chaque point du set en cours directement dans `MatchScore.currentSetPointLog` ; ce champ traverse ensuite tout le pipeline existant sans aucune autre modification (protocole Data Layer montre↔téléphone, `ScoreRepository.latestScore`) puisqu'il fait partie de l'objet `MatchScore` sérialisé de bout en bout. Côté téléphone : nouvelle table Room `live_shares` (cache local du lien par session), nouveau repository `LiveShareRepository`/`LiveShareRepositoryImpl` suivant le pattern `WorkAxisRepository`, nouveau use case `ShareMatchUseCase`. Le point d'accroche pour la poussée du score est la boucle `scoreRepository.latestScore.collect { ... }` déjà présente dans `MatchViewModel` (seul endroit qui observe tous les changements de score, qu'ils viennent de la montre ou d'ailleurs).

**Tech Stack:** Kotlin, Room, Retrofit/Moshi, Hilt, Orbit MVI, JUnit5 + MockK (tests existants).

## Global Constraints

- Convention JSON réseau backend : `snake_case` sur le fil (via `@Json(name = "...")`), cohérent avec `SyncDto.kt`/le backend — **différent** du protocole Data Layer montre↔téléphone (`MatchScoreDto`, camelCase), donc pas de réutilisation directe de ce DTO pour les appels au backend.
- Poussée du score au backend : **échec réseau ignoré sans retry**, l'état complet (pas un delta) est renvoyé au point suivant — auto-réparant.
- `currentSetPointLog` = un élément (`Player.A`/`Player.B`) par **point** remporté dans le set en cours, vidé quand le set se termine — pas un log par jeu. À ne pas confondre avec `MatchViewModel.state.currentSetGameLog` (log par **jeu**, utilisé uniquement pour la barre de momentum affichée sur le téléphone, non transmis au backend, non modifié par ce plan).
- Page publique : jamais « Vous » — le nom du joueur principal vient de `PlayerProfileRepository.getProfile().displayName`, avec repli `"Joueur"` (pas `"Vous"`, qui n'a pas de sens pour un spectateur externe).
- Room : version actuelle de `SecondServeDatabase` = 12 → nouvelle migration `MIGRATION_12_13`.

---

### Task 1: Suivi point par point du set en cours (moteur de score + protocole Data Layer)

**Files:**
- Modify: `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt`
- Modify: `android/domain/src/main/kotlin/com/secondserve/domain/engine/TennisScoreEngine.kt`
- Modify: `android/domain/src/test/kotlin/com/secondserve/domain/engine/TennisScoreEngineTest.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/MatchScoreDto.kt`
- Modify: `android/data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt`

**Interfaces:**
- Produces: `MatchScore.currentSetPointLog: List<Player>` (nouveau champ). Traverse automatiquement `TennisScoreEngine` → montre (`ScoreViewModel`, inchangé) → Data Layer (`DataLayerClient`/`MatchScoreDto`, inchangé au-delà du DTO lui-même) → téléphone (`ScoreRepository.latestScore`, inchangé) puisqu'il fait partie de `MatchScore` sérialisé tel quel.

- [ ] **Step 1: Écrire les tests du moteur de score (doivent échouer — le champ n'existe pas)**

Ajouter à `android/domain/src/test/kotlin/com/secondserve/domain/engine/TennisScoreEngineTest.kt`, à la fin de la classe (avant la dernière accolade fermante) :

```kotlin
    @Nested
    inner class PointLogTracking {

        @Test
        fun `recordPoint appends scorer to currentSetPointLog`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.B)
            engine.recordPoint(Player.A)
            assertEquals(listOf(Player.A, Player.B, Player.A), engine.currentScore.currentSetPointLog)
        }

        @Test
        fun `currentSetPointLog resets when the set is won`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            assertTrue(engine.currentScore.currentSetPointLog.isEmpty())
        }

        @Test
        fun `currentSetPointLog is restored by undo`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.B)
            engine.undo()
            assertEquals(listOf(Player.A), engine.currentScore.currentSetPointLog)
        }
    }
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils échouent**

Run: `cd android && ./gradlew :domain:test --tests "com.secondserve.domain.engine.TennisScoreEngineTest"`
Expected: FAIL (erreur de compilation — `currentSetPointLog` n'existe pas encore sur `MatchScore`).

- [ ] **Step 3: Ajouter le champ à `MatchScore`**

Modifier `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt` :

```kotlin
package com.secondserve.domain.model

enum class Player { A, B }

enum class GamePoint { ZERO, FIFTEEN, THIRTY, FORTY, ADVANTAGE }

data class SetResult(val gamesA: Int, val gamesB: Int)

data class MatchScore(
    val completedSets: List<SetResult> = emptyList(),
    val currentSetGamesA: Int = 0,
    val currentSetGamesB: Int = 0,
    val currentSetPointLog: List<Player> = emptyList(),
    val currentGamePointsA: GamePoint = GamePoint.ZERO,
    val currentGamePointsB: GamePoint = GamePoint.ZERO,
    val tieBreakPointsA: Int = 0,
    val tieBreakPointsB: Int = 0,
    val isTieBreak: Boolean = false,
    val isSuperTieBreak: Boolean = false,
    val isMatchOver: Boolean = false,
    val matchWinner: Player? = null
) {
    val isDeuce: Boolean
        get() = !isTieBreak && !isSuperTieBreak &&
                currentGamePointsA == GamePoint.FORTY &&
                currentGamePointsB == GamePoint.FORTY

    val currentSetTotalGames: Int get() = currentSetGamesA + currentSetGamesB
}
```

- [ ] **Step 4: Alimenter et réinitialiser le champ dans `TennisScoreEngine`**

Modifier `android/domain/src/main/kotlin/com/secondserve/domain/engine/TennisScoreEngine.kt` — la fonction `recordPoint` (ajouter l'accumulation juste après la sauvegarde de l'historique, avant le `when` de dispatch) :

```kotlin
    fun recordPoint(scorer: Player): EngineEvent {
        check(!state.isMatchOver) { "Cannot record point: match is over" }
        history.addLast(state.copy())
        state = state.copy(currentSetPointLog = state.currentSetPointLog + scorer)
        return when {
            state.isSuperTieBreak -> processSuperTieBreakPoint(scorer)
            state.isTieBreak -> processTieBreakPoint(scorer)
            else -> processRegularPoint(scorer)
        }
    }
```

Puis `awardSet` (ajouter `currentSetPointLog = emptyList()` au `state.copy(...)` existant, à côté des autres réinitialisations de set) :

```kotlin
    private fun awardSet(winner: Player): EngineEvent {
        val totalGamesInSet = state.currentSetGamesA + state.currentSetGamesB
        val setChangeover = totalGamesInSet % 2 == 1

        val completedSet = SetResult(state.currentSetGamesA, state.currentSetGamesB)
        val newCompletedSets = state.completedSets + completedSet

        val setsWonA = newCompletedSets.count { it.gamesA > it.gamesB }
        val setsWonB = newCompletedSets.count { it.gamesB > it.gamesA }
        val setsToWin = if (format.matchFormat == MatchFormat.BEST_OF_1) 1 else 2

        state = state.copy(
            completedSets = newCompletedSets,
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentSetPointLog = emptyList(),
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false
        )

        if (setsWonA >= setsToWin || setsWonB >= setsToWin) {
            state = state.copy(isMatchOver = true, matchWinner = winner)
            return EngineEvent.MatchOver(state, winner)
        }

        if (format.matchFormat == MatchFormat.BEST_OF_3 &&
            format.thirdSetRule == ThirdSetRule.SUPER_TIE_BREAK_10 &&
            setsWonA == 1 && setsWonB == 1) {
            state = state.copy(isSuperTieBreak = true, tieBreakPointsA = 0, tieBreakPointsB = 0)
            return EngineEvent.SetWon(state, winner, setChangeover)
        }

        return EngineEvent.SetWon(state, winner, setChangeover)
    }
```

(Le reste de `TennisScoreEngine.kt` — `awardGame`, `awardTieBreakGame`, `startTieBreak`, `checkSetWon`, `processRegularPoint`, `processTieBreakPoint`, `processSuperTieBreakPoint`, `isFinalSet` — reste inchangé : ils manipulent `state.copy(...)` sans jamais lister explicitement tous les champs, donc `currentSetPointLog` déjà accumulé par `recordPoint` traverse ces fonctions sans être écrasé, sauf dans `awardSet` où on le remet à zéro explicitement ci-dessus.)

- [ ] **Step 5: Lancer les tests et vérifier qu'ils passent**

Run: `cd android && ./gradlew :domain:test --tests "com.secondserve.domain.engine.TennisScoreEngineTest"`
Expected: tous les tests PASS (existants + 3 nouveaux).

- [ ] **Step 6: Écrire le test du DTO Data Layer (doit échouer — le champ n'existe pas sur le DTO)**

Ajouter à `android/data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt`, à la suite des tests existants :

```kotlin
    @Test
    fun `currentSetPointLog round-trips through DTO`() {
        val original = MatchScore(
            currentSetGamesA = 1,
            currentSetPointLog = listOf(Player.A, Player.A, Player.B)
        )
        val dto = original.toDto()
        val restored = dto.toDomain()
        assertEquals(listOf(Player.A, Player.A, Player.B), restored.currentSetPointLog)
    }
```

Modifier également les deux tests existants qui construisent `MatchScoreDto(...)` directement (`toDomain throws on unknown GamePoint string` et `toDomain throws on unknown Player string`) pour ajouter le nouveau champ requis :

```kotlin
    @Test
    fun `toDomain throws on unknown GamePoint string`() {
        val dto = MatchScoreDto(
            completedSets = emptyList(),
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentSetPointLog = emptyList(),
            currentGamePointsA = "INVALID_POINT",
            currentGamePointsB = "ZERO",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = false,
            matchWinner = null
        )
        assertThrows<IllegalArgumentException> { dto.toDomain() }
    }

    @Test
    fun `toDomain throws on unknown Player string`() {
        val dto = MatchScoreDto(
            completedSets = emptyList(),
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentSetPointLog = emptyList(),
            currentGamePointsA = "ZERO",
            currentGamePointsB = "ZERO",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = true,
            matchWinner = "C"
        )
        assertThrows<IllegalArgumentException> { dto.toDomain() }
    }
```

- [ ] **Step 7: Lancer les tests et vérifier qu'ils échouent**

Run: `cd android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.wearable.dto.MatchScoreDtoTest"`
Expected: FAIL (erreur de compilation — `MatchScoreDto` n'a pas de paramètre `currentSetPointLog`).

- [ ] **Step 8: Ajouter le champ au DTO et aux fonctions de mapping**

Modifier `android/data/src/main/kotlin/com/secondserve/data/wearable/dto/MatchScoreDto.kt` :

```kotlin
package com.secondserve.data.wearable.dto

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SetResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class SetResultDto(
    @Json(name = "gamesA") val gamesA: Int,
    @Json(name = "gamesB") val gamesB: Int
)

@JsonClass(generateAdapter = false)
data class MatchScoreDto(
    @Json(name = "completedSets") val completedSets: List<SetResultDto>,
    @Json(name = "currentSetGamesA") val currentSetGamesA: Int,
    @Json(name = "currentSetGamesB") val currentSetGamesB: Int,
    @Json(name = "currentSetPointLog") val currentSetPointLog: List<String>,
    @Json(name = "currentGamePointsA") val currentGamePointsA: String,
    @Json(name = "currentGamePointsB") val currentGamePointsB: String,
    @Json(name = "tieBreakPointsA") val tieBreakPointsA: Int,
    @Json(name = "tieBreakPointsB") val tieBreakPointsB: Int,
    @Json(name = "isTieBreak") val isTieBreak: Boolean,
    @Json(name = "isSuperTieBreak") val isSuperTieBreak: Boolean,
    @Json(name = "isMatchOver") val isMatchOver: Boolean,
    @Json(name = "matchWinner") val matchWinner: String?
)

fun MatchScore.toDto() = MatchScoreDto(
    completedSets = completedSets.map { SetResultDto(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentSetPointLog = currentSetPointLog.map { it.name },
    currentGamePointsA = currentGamePointsA.name,
    currentGamePointsB = currentGamePointsB.name,
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.name
)

fun MatchScoreDto.toDomain() = MatchScore(
    completedSets = completedSets.map { SetResult(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentSetPointLog = currentSetPointLog.map { Player.valueOf(it) },
    currentGamePointsA = GamePoint.valueOf(currentGamePointsA),
    currentGamePointsB = GamePoint.valueOf(currentGamePointsB),
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.let { Player.valueOf(it) }
)
```

- [ ] **Step 9: Lancer les tests et vérifier qu'ils passent**

Run: `cd android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.wearable.dto.MatchScoreDtoTest"`
Expected: tous les tests PASS (existants + le nouveau round-trip).

- [ ] **Step 10: Lancer la suite complète `:domain` et `:data` pour vérifier l'absence de régression**

Run: `cd android && ./gradlew :domain:test :data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tous les tests PASS (y compris `ScoreViewModelTest`, `DataLayerListener`-related tests s'ils existent, qui construisent `MatchScore(...)` sans préciser `currentSetPointLog` et bénéficient donc de sa valeur par défaut `emptyList()`).

- [ ] **Step 11: Commit**

```bash
git add android/domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt \
        android/domain/src/main/kotlin/com/secondserve/domain/engine/TennisScoreEngine.kt \
        android/domain/src/test/kotlin/com/secondserve/domain/engine/TennisScoreEngineTest.kt \
        android/data/src/main/kotlin/com/secondserve/data/wearable/dto/MatchScoreDto.kt \
        android/data/src/test/kotlin/com/secondserve/data/wearable/dto/MatchScoreDtoTest.kt
git commit -m "feat(android): suivre le scoreur de chaque point du set courant dans MatchScore"
```

---

### Task 2: Persistance locale, DTOs réseau et endpoints Retrofit

**Files:**
- Create: `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/LiveShareEntity.kt`
- Create: `android/data/src/main/kotlin/com/secondserve/data/local/dao/LiveShareDao.kt`
- Create: `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/LiveShareDto.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- Modify: `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt`
- Modify: `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`

**Interfaces:**
- Produces: `LiveShareEntity` (table `live_shares`) ; `LiveShareDao.getBySessionId/insert` ; `CreateShareRequest`/`CreateShareResponse`/`LiveSetResultDto`/`LiveScoreUpdateRequest` (DTOs snake_case, endpoint backend) ; `VpsApiService.createLiveShare/pushLiveScore`.

- [ ] **Step 1: Créer l'entité Room**

`android/data/src/main/kotlin/com/secondserve/data/local/db/entity/LiveShareEntity.kt` :

```kotlin
package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "live_shares",
    indices = [Index(value = ["session_id"], name = "idx_live_shares_session_id", unique = true)]
)
data class LiveShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

- [ ] **Step 2: Créer le DAO**

`android/data/src/main/kotlin/com/secondserve/data/local/dao/LiveShareDao.kt` :

```kotlin
package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secondserve.data.local.db.entity.LiveShareEntity

@Dao
interface LiveShareDao {
    @Query("SELECT * FROM live_shares WHERE session_id = :sessionId")
    suspend fun getBySessionId(sessionId: Long): LiveShareEntity?

    @Insert
    suspend fun insert(entity: LiveShareEntity): Long
}
```

- [ ] **Step 3: Ajouter la migration Room**

Modifier `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` — ajouter l'import de `LiveShareEntity`, l'entité dans `@Database(entities = [...])`, la méthode DAO, et la migration :

```kotlin
import com.secondserve.data.local.dao.LiveShareDao
// ... (imports existants inchangés, ajouter celui-ci à côté des autres import com.secondserve.data.local.dao.*)
import com.secondserve.data.local.db.entity.LiveShareEntity
// ... (ajouter à côté des autres import com.secondserve.data.local.db.entity.*)

@Database(
    entities = [
        PlayerProfileEntity::class,
        RankingHistoryEntity::class,
        WorkAxisEntity::class,
        SessionEntity::class,
        PointEntity::class,
        SyncQueueEntity::class,
        CoachingCacheEntity::class,
        CoachingAnalysisEntity::class,
        CoachingSynthesisEntity::class,
        AxisSuggestionEntity::class,
        LiveShareEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class SecondServeDatabase : RoomDatabase() {
    // ... méthodes abstraites existantes inchangées ...
    abstract fun liveShareDao(): LiveShareDao

    companion object {
        const val DB_NAME = "secondserve_db"

        // ... MIGRATION_1_2 à MIGRATION_11_12 inchangées ...

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS live_shares (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        session_id INTEGER NOT NULL,
                        token TEXT NOT NULL,
                        url TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_live_shares_session_id ON live_shares (session_id)"
                )
            }
        }
    }
}
```

- [ ] **Step 4: Enregistrer le DAO et la migration dans le module Hilt**

Modifier `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` :

Ajouter l'import `import com.secondserve.data.local.dao.LiveShareDao`, ajouter `SecondServeDatabase.MIGRATION_12_13` à la fin de la liste `.addMigrations(...)`, et ajouter la méthode de provision :

```kotlin
    @Provides
    @Singleton
    fun provideLiveShareDao(db: SecondServeDatabase): LiveShareDao =
        db.liveShareDao()
```

- [ ] **Step 5: Créer les DTOs réseau**

`android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/LiveShareDto.kt` :

```kotlin
package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateShareRequest(
    @Json(name = "session_id") val sessionId: Long
)

@JsonClass(generateAdapter = true)
data class CreateShareResponse(
    val token: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class LiveSetResultDto(
    @Json(name = "games_a") val gamesA: Int,
    @Json(name = "games_b") val gamesB: Int
)

@JsonClass(generateAdapter = true)
data class LiveScoreUpdateRequest(
    @Json(name = "completed_sets") val completedSets: List<LiveSetResultDto>,
    @Json(name = "current_set_games_a") val currentSetGamesA: Int,
    @Json(name = "current_set_games_b") val currentSetGamesB: Int,
    @Json(name = "current_set_point_log") val currentSetPointLog: List<String>,
    @Json(name = "current_game_points_a") val currentGamePointsA: String,
    @Json(name = "current_game_points_b") val currentGamePointsB: String,
    @Json(name = "tie_break_points_a") val tieBreakPointsA: Int,
    @Json(name = "tie_break_points_b") val tieBreakPointsB: Int,
    @Json(name = "is_tie_break") val isTieBreak: Boolean,
    @Json(name = "is_super_tie_break") val isSuperTieBreak: Boolean,
    @Json(name = "is_match_over") val isMatchOver: Boolean,
    @Json(name = "match_winner") val matchWinner: String?,
    @Json(name = "player_a_name") val playerAName: String,
    @Json(name = "player_b_name") val playerBName: String,
    val surface: String,
    val tournament: String?,
    @Json(name = "competition_type") val competitionType: String?,
    @Json(name = "started_at") val startedAt: Long
)
```

- [ ] **Step 6: Ajouter les endpoints Retrofit**

Modifier `android/data/src/main/kotlin/com/secondserve/data/remote/api/VpsApiService.kt` — ajouter les imports `CreateShareRequest`, `CreateShareResponse`, `LiveScoreUpdateRequest`, puis dans l'interface :

```kotlin
    @POST("api/v1/live/shares")
    suspend fun createLiveShare(@Body request: CreateShareRequest): CreateShareResponse

    @POST("api/v1/live/sessions/{sessionId}/score")
    suspend fun pushLiveScore(
        @Path("sessionId") sessionId: Long,
        @Body request: LiveScoreUpdateRequest
    )
```

- [ ] **Step 7: Vérifier la compilation**

Run: `cd android && ./gradlew :data:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room génère le schéma pour la version 13, Hilt valide le graphe de dépendances).

- [ ] **Step 8: Commit**

```bash
git add android/data android/app/src/main/kotlin/com/secondserve/di/DataModule.kt
git commit -m "feat(android): ajouter la persistance locale et les endpoints réseau du partage de lien live"
```

---

### Task 3: Repository et use case de partage

**Files:**
- Create: `android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareInfo.kt`
- Create: `android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareContext.kt`
- Create: `android/domain/src/main/kotlin/com/secondserve/domain/repository/LiveShareRepository.kt`
- Create: `android/domain/src/main/kotlin/com/secondserve/domain/usecase/match/ShareMatchUseCase.kt`
- Create: `android/data/src/main/kotlin/com/secondserve/data/repository/LiveShareRepositoryImpl.kt`
- Modify: `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- Test: `android/data/src/test/kotlin/com/secondserve/data/repository/LiveShareRepositoryImplTest.kt`

**Interfaces:**
- Produces: `LiveShareInfo(token: String, url: String)` ; `LiveShareContext(playerAName, playerBName, surface, tournament, competitionType, startedAt)` ; `LiveShareRepository.getOrCreateShare(sessionId): AppResult<LiveShareInfo>` / `getCachedShare(sessionId): LiveShareInfo?` / `pushScore(sessionId, score: MatchScore, context: LiveShareContext)` ; `ShareMatchUseCase(sessionId): AppResult<LiveShareInfo>`.
- Consumes: `LiveShareDao`, `VpsApiService` (Task 2), `MatchScore` (avec `currentSetPointLog`, Task 1), `AppResult` (existants).

- [ ] **Step 1: Créer les modèles de domaine**

`android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareInfo.kt` :

```kotlin
package com.secondserve.domain.model

data class LiveShareInfo(
    val token: String,
    val url: String
)
```

`android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareContext.kt` :

```kotlin
package com.secondserve.domain.model

data class LiveShareContext(
    val playerAName: String,
    val playerBName: String,
    val surface: String,
    val tournament: String?,
    val competitionType: String?,
    val startedAt: Long
)
```

- [ ] **Step 2: Créer l'interface du repository**

`android/domain/src/main/kotlin/com/secondserve/domain/repository/LiveShareRepository.kt` :

```kotlin
package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.model.MatchScore

interface LiveShareRepository {
    suspend fun getOrCreateShare(sessionId: Long): AppResult<LiveShareInfo>
    suspend fun getCachedShare(sessionId: Long): LiveShareInfo?
    suspend fun pushScore(sessionId: Long, score: MatchScore, context: LiveShareContext)
}
```

- [ ] **Step 3: Créer le use case**

`android/domain/src/main/kotlin/com/secondserve/domain/usecase/match/ShareMatchUseCase.kt` :

```kotlin
package com.secondserve.domain.usecase.match

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.repository.LiveShareRepository
import javax.inject.Inject

class ShareMatchUseCase @Inject constructor(
    private val liveShareRepository: LiveShareRepository
) {
    suspend operator fun invoke(sessionId: Long): AppResult<LiveShareInfo> =
        liveShareRepository.getOrCreateShare(sessionId)
}
```

- [ ] **Step 4: Écrire le test du repository (échec réseau toléré + idempotence via cache + transport du point log)**

`android/data/src/test/kotlin/com/secondserve/data/repository/LiveShareRepositoryImplTest.kt` :

```kotlin
package com.secondserve.data.repository

import com.secondserve.data.local.dao.LiveShareDao
import com.secondserve.data.local.db.entity.LiveShareEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.CreateShareResponse
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiveShareRepositoryImplTest {

    private lateinit var dao: LiveShareDao
    private lateinit var vpsApiService: VpsApiService
    private lateinit var repository: LiveShareRepositoryImpl

    private val context = LiveShareContext(
        playerAName = "Benjamin",
        playerBName = "Marceau",
        surface = "CLAY",
        tournament = "Tournoi du club",
        competitionType = "CLUB",
        startedAt = 1000L
    )

    @BeforeEach
    fun setup() {
        dao = mockk()
        vpsApiService = mockk()
        repository = LiveShareRepositoryImpl(dao, vpsApiService)
    }

    @Test
    fun `getOrCreateShare returns cached share without calling API`() = runTest {
        coEvery { dao.getBySessionId(10L) } returns LiveShareEntity(
            id = 1L, sessionId = 10L, token = "abc", url = "https://secondserve.app/live/abc", createdAt = 500L
        )

        val result = repository.getOrCreateShare(10L)

        assertTrue(result is AppResult.Success)
        assertEquals("abc", (result as AppResult.Success).data.token)
        coVerify(exactly = 0) { vpsApiService.createLiveShare(any()) }
    }

    @Test
    fun `getOrCreateShare calls API and caches result when no share exists`() = runTest {
        coEvery { dao.getBySessionId(11L) } returns null
        coEvery { vpsApiService.createLiveShare(any()) } returns CreateShareResponse(
            token = "xyz", url = "https://secondserve.app/live/xyz"
        )
        coEvery { dao.insert(any()) } returns 1L

        val result = repository.getOrCreateShare(11L)

        assertTrue(result is AppResult.Success)
        assertEquals("xyz", (result as AppResult.Success).data.token)
        coVerify(exactly = 1) { dao.insert(match { it.sessionId == 11L && it.token == "xyz" }) }
    }

    @Test
    fun `getOrCreateShare returns Error when API call fails`() = runTest {
        coEvery { dao.getBySessionId(12L) } returns null
        coEvery { vpsApiService.createLiveShare(any()) } throws RuntimeException("network down")

        val result = repository.getOrCreateShare(12L)

        assertTrue(result is AppResult.Error)
    }

    @Test
    fun `pushScore swallows network failures without throwing`() = runTest {
        // Le repository ne fait aucune hypothèse sur l'existence d'un partage actif — c'est au
        // ViewModel de ne l'appeler que lorsque state.shareInfo est non-null (cf. plan ViewModel).
        coEvery { vpsApiService.pushLiveScore(any(), any()) } throws RuntimeException("timeout")

        repository.pushScore(13L, MatchScore(), context = context)

        coVerify(exactly = 1) { vpsApiService.pushLiveScore(eq(13L), any()) }
        // L'absence d'exception levée jusqu'ici est l'assertion : le test échouerait si
        // pushScore laissait remonter l'exception au lieu de la capturer.
    }

    @Test
    fun `pushScore sends currentSetPointLog mapped to A_B strings`() = runTest {
        coEvery { vpsApiService.pushLiveScore(any(), any()) } returns Unit

        repository.pushScore(
            14L,
            MatchScore(currentSetPointLog = listOf(Player.A, Player.B, Player.A)),
            context = context
        )

        coVerify {
            vpsApiService.pushLiveScore(
                eq(14L),
                match { it.currentSetPointLog == listOf("A", "B", "A") && it.playerAName == "Benjamin" }
            )
        }
    }
}
```

- [ ] **Step 5: Lancer les tests (doivent échouer — `LiveShareRepositoryImpl` inexistant)**

Run: `cd android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.repository.LiveShareRepositoryImplTest"`
Expected: FAIL (compilation error — la classe n'existe pas encore).

- [ ] **Step 6: Implémenter le repository**

`android/data/src/main/kotlin/com/secondserve/data/repository/LiveShareRepositoryImpl.kt` :

```kotlin
package com.secondserve.data.repository

import com.secondserve.data.local.dao.LiveShareDao
import com.secondserve.data.local.db.entity.LiveShareEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.CreateShareRequest
import com.secondserve.data.remote.api.dto.LiveScoreUpdateRequest
import com.secondserve.data.remote.api.dto.LiveSetResultDto
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.LiveShareRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveShareRepositoryImpl @Inject constructor(
    private val dao: LiveShareDao,
    private val vpsApiService: VpsApiService
) : LiveShareRepository {

    override suspend fun getOrCreateShare(sessionId: Long): AppResult<LiveShareInfo> {
        dao.getBySessionId(sessionId)?.let {
            return AppResult.Success(LiveShareInfo(token = it.token, url = it.url))
        }
        return try {
            val response = vpsApiService.createLiveShare(CreateShareRequest(sessionId))
            dao.insert(
                LiveShareEntity(
                    sessionId = sessionId,
                    token = response.token,
                    url = response.url,
                    createdAt = System.currentTimeMillis()
                )
            )
            AppResult.Success(LiveShareInfo(token = response.token, url = response.url))
        } catch (e: Exception) {
            Timber.e(e, "LiveShareRepository: création du lien échouée")
            AppResult.Error(e)
        }
    }

    override suspend fun getCachedShare(sessionId: Long): LiveShareInfo? =
        dao.getBySessionId(sessionId)?.let { LiveShareInfo(token = it.token, url = it.url) }

    override suspend fun pushScore(sessionId: Long, score: MatchScore, context: LiveShareContext) {
        try {
            vpsApiService.pushLiveScore(
                sessionId,
                LiveScoreUpdateRequest(
                    completedSets = score.completedSets.map { LiveSetResultDto(it.gamesA, it.gamesB) },
                    currentSetGamesA = score.currentSetGamesA,
                    currentSetGamesB = score.currentSetGamesB,
                    currentSetPointLog = score.currentSetPointLog.map { it.name },
                    currentGamePointsA = score.currentGamePointsA.name,
                    currentGamePointsB = score.currentGamePointsB.name,
                    tieBreakPointsA = score.tieBreakPointsA,
                    tieBreakPointsB = score.tieBreakPointsB,
                    isTieBreak = score.isTieBreak,
                    isSuperTieBreak = score.isSuperTieBreak,
                    isMatchOver = score.isMatchOver,
                    matchWinner = score.matchWinner?.name,
                    playerAName = context.playerAName,
                    playerBName = context.playerBName,
                    surface = context.surface,
                    tournament = context.tournament,
                    competitionType = context.competitionType,
                    startedAt = context.startedAt
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "LiveShareRepository: poussée du score échouée — ignorée (auto-réparant au prochain point)")
        }
    }
}
```

- [ ] **Step 7: Lancer les tests et vérifier qu'ils passent**

Run: `cd android && ./gradlew :data:testDebugUnitTest --tests "com.secondserve.data.repository.LiveShareRepositoryImplTest"`
Expected: 5 tests PASS.

- [ ] **Step 8: Câbler le repository dans le module Hilt**

Modifier `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` — ajouter les imports `LiveShareRepositoryImpl`, `LiveShareRepository`, puis :

```kotlin
    @Provides
    @Singleton
    fun provideLiveShareRepository(
        dao: LiveShareDao,
        vpsApiService: VpsApiService
    ): LiveShareRepository =
        LiveShareRepositoryImpl(dao, vpsApiService)
```

- [ ] **Step 9: Vérifier la compilation complète**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareInfo.kt \
        android/domain/src/main/kotlin/com/secondserve/domain/model/LiveShareContext.kt \
        android/domain/src/main/kotlin/com/secondserve/domain/repository/LiveShareRepository.kt \
        android/domain/src/main/kotlin/com/secondserve/domain/usecase/match/ShareMatchUseCase.kt \
        android/data/src/main/kotlin/com/secondserve/data/repository/LiveShareRepositoryImpl.kt \
        android/data/src/test/kotlin/com/secondserve/data/repository/LiveShareRepositoryImplTest.kt \
        android/app/src/main/kotlin/com/secondserve/di/DataModule.kt
git commit -m "feat(android): repository et use case de création/poussée du lien de partage live"
```

---

### Task 4: Intégration dans MatchViewModel

**Files:**
- Modify: `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt`
- Modify: `android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt`

**Interfaces:**
- Consumes: `ShareMatchUseCase`, `LiveShareRepository`, `PlayerProfileRepository` (existant), `LiveShareContext`, `LiveShareInfo` (Task 3).
- Produces: `MatchUiState.shareInfo`, `MatchUiState.playerDisplayName`, `MatchUiState.surface`, `MatchUiState.tournament`, `MatchUiState.competitionType` ; `MatchViewModel.onShareRequested()` ; `MatchSideEffect.ShareMatch(url: String)`.

- [ ] **Step 1: Étendre le test existant pour couvrir le partage (doit échouer — nouveaux paramètres/comportements inexistants)**

Modifier `android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt` :

Ajouter les imports :

```kotlin
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.repository.LiveShareRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.usecase.match.ShareMatchUseCase
```

Ajouter les champs et mettre à jour `setup()`/la construction du ViewModel :

```kotlin
    private lateinit var liveShareRepository: LiveShareRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var shareMatchUseCase: ShareMatchUseCase
```

Dans `setup()`, avant `viewModel = MatchViewModel(...)` :

```kotlin
        liveShareRepository = mockk(relaxed = true)
        playerProfileRepository = mockk()
        shareMatchUseCase = mockk()

        coEvery { playerProfileRepository.getProfile() } returns AppResult.Success(
            PlayerProfile(
                displayName = "Benjamin",
                club = null,
                currentSeries = null,
                currentPoints = null,
                playStyle = null,
                preferredSurfaces = emptyList(),
                coachInstruction1 = null,
                coachInstruction2 = null,
                coachInstruction3 = null,
                updatedAt = 0L
            )
        )
        coEvery { liveShareRepository.getCachedShare(any()) } returns null
```

Remplacer le bloc `viewModel = MatchViewModel(...)` existant par (trois nouveaux arguments ajoutés après `coachingResolver`, avant `savedStateHandle`) :

```kotlin
        viewModel = MatchViewModel(
            scoreRepository = scoreRepository,
            sessionRepository = sessionRepository,
            closeMatchUseCase = closeMatchUseCase,
            syncScheduler = syncScheduler,
            analysisScheduler = analysisScheduler,
            dataLayerEventBus = dataLayerEventBus,
            coachingCachePrefetcher = coachingCachePrefetcher,
            coachingResolver = coachingResolver,
            liveShareRepository = liveShareRepository,
            playerProfileRepository = playerProfileRepository,
            shareMatchUseCase = shareMatchUseCase,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 10L))
        )
```

Ajouter les tests suivants à la fin de la classe :

```kotlin
    @Test
    fun `onShareRequested creates share and emits ShareMatch side effect`() = runTest {
        coEvery { shareMatchUseCase(10L) } returns AppResult.Success(
            LiveShareInfo(token = "abc", url = "https://secondserve.app/live/abc")
        )

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.ShareMatch }
        }

        viewModel.onShareRequested()
        val effect = sideEffectDeferred.await() as MatchSideEffect.ShareMatch

        assertEquals("https://secondserve.app/live/abc", effect.url)
        val state = viewModel.container.stateFlow.first { it.shareInfo != null }
        assertEquals("abc", state.shareInfo?.token)
    }

    @Test
    fun `onShareRequested emits ShowError when creation fails`() = runTest {
        coEvery { shareMatchUseCase(10L) } returns AppResult.Error(RuntimeException("network down"))

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.ShowError }
        }

        viewModel.onShareRequested()
        sideEffectDeferred.await()

        assertNull(viewModel.container.stateFlow.value.shareInfo)
    }

    @Test
    fun `score change pushes to live share when a share is active`() = runTest {
        coEvery { shareMatchUseCase(10L) } returns AppResult.Success(
            LiveShareInfo(token = "abc", url = "https://secondserve.app/live/abc")
        )
        viewModel.onShareRequested()
        viewModel.container.stateFlow.first { it.shareInfo != null }

        scoreFlow.value = MatchScore(currentSetGamesA = 0, currentSetGamesB = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        scoreFlow.value = MatchScore(currentSetGamesA = 1, currentSetGamesB = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 1) {
            liveShareRepository.pushScore(eq(10L), any(), any())
        }
    }

    @Test
    fun `score change does not push to live share when no share is active`() = runTest {
        scoreFlow.value = MatchScore(currentSetGamesA = 0, currentSetGamesB = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        scoreFlow.value = MatchScore(currentSetGamesA = 1, currentSetGamesB = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            liveShareRepository.pushScore(any(), any(), any())
        }
    }
```

- [ ] **Step 2: Lancer les tests (doivent échouer à la compilation)**

Run: `cd android && ./gradlew :feature:match:testDebugUnitTest --tests "com.secondserve.feature.match.MatchViewModelTest"`
Expected: FAIL — compilation error, `MatchViewModel` n'a pas encore ces paramètres/méthodes.

- [ ] **Step 3: Étendre `MatchUiState` et le constructeur de `MatchViewModel`**

Modifier `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt` — ajouter les imports :

```kotlin
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.repository.LiveShareRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.usecase.match.ShareMatchUseCase
```

Modifier la signature du constructeur (ajouter les trois nouvelles dépendances après `coachingResolver`) :

```kotlin
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val sessionRepository: SessionRepository,
    private val closeMatchUseCase: CloseMatchUseCase,
    private val syncScheduler: SyncScheduler,
    private val analysisScheduler: AnalysisScheduler,
    private val dataLayerEventBus: DataLayerEventBus,
    private val coachingCachePrefetcher: CoachingCachePrefetcher,
    private val coachingResolver: CoachingResolver,
    private val liveShareRepository: LiveShareRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val shareMatchUseCase: ShareMatchUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<MatchUiState, MatchSideEffect> {
```

Étendre `MatchUiState` :

```kotlin
data class MatchUiState(
    val showCloseDialog: Boolean = false,
    val feelingRating: Int? = null,
    val feelingComment: String = "",
    val isClosing: Boolean = false,
    val coachingAdvice: CoachingResult? = null,
    val coachingAdviceSeq: Int = 0,
    val opponentName: String? = null,
    val sessionStartedAt: Long = 0L,
    val currentSetGameLog: List<Player> = emptyList(),
    val momentumPercent: Int = 50,
    val surface: String? = null,
    val tournament: String? = null,
    val competitionType: String? = null,
    val playerDisplayName: String = "Joueur",
    val shareInfo: LiveShareInfo? = null
)
```

Ajouter le side effect :

```kotlin
sealed class MatchSideEffect {
    data object SessionClosed : MatchSideEffect()
    data class ShowError(val message: String) : MatchSideEffect()
    data class ShareMatch(val url: String) : MatchSideEffect()
}
```

- [ ] **Step 4: Charger le contexte (profil, session) et le lien en cache à l'initialisation**

Remplacer le premier bloc `init { ... viewModelScope.launch { val session = ... } }` par :

```kotlin
        viewModelScope.launch {
            val session = sessionRepository.getSessionById(sessionId)
            if (session != null) {
                intent {
                    reduce {
                        state.copy(
                            opponentName = session.opponent,
                            sessionStartedAt = session.createdAt,
                            surface = session.surface,
                            tournament = session.tournament,
                            competitionType = session.competitionType
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            when (val result = playerProfileRepository.getProfile()) {
                is AppResult.Success -> {
                    val displayName = result.data?.displayName
                    if (!displayName.isNullOrBlank()) {
                        intent { reduce { state.copy(playerDisplayName = displayName) } }
                    }
                }
                is AppResult.Error -> Timber.w(result.exception, "MatchViewModel: lecture du profil échouée")
                AppResult.Loading -> {}
            }
        }

        viewModelScope.launch {
            val cached = liveShareRepository.getCachedShare(sessionId)
            if (cached != null) {
                intent { reduce { state.copy(shareInfo = cached) } }
            }
        }
```

- [ ] **Step 5: Ajouter `onShareRequested`**

Ajouter après `onFeelingCommentChanged` :

```kotlin
    fun onShareRequested() = intent {
        when (val result = shareMatchUseCase(sessionId)) {
            is AppResult.Success -> {
                reduce { state.copy(shareInfo = result.data) }
                postSideEffect(MatchSideEffect.ShareMatch(result.data.url))
            }
            is AppResult.Error -> {
                Timber.e(result.exception, "MatchViewModel: création du lien de partage échouée")
                postSideEffect(MatchSideEffect.ShowError("Impossible de créer le lien de partage"))
            }
            AppResult.Loading -> {}
        }
    }
```

- [ ] **Step 6: Pousser le score à chaque changement, si un partage est actif**

Dans le bloc `viewModelScope.launch { var previous: MatchScore? = null; scoreRepository.latestScore.collect { score -> ... } }`, ajouter à la fin du corps du `collect` (après le bloc `if (newLog != currentLog) { ... }` existant, toujours à l'intérieur du `collect`) :

```kotlin
                val currentState = container.stateFlow.value
                currentState.shareInfo?.let {
                    viewModelScope.launch {
                        liveShareRepository.pushScore(
                            sessionId = sessionId,
                            score = score,
                            context = LiveShareContext(
                                playerAName = currentState.playerDisplayName,
                                playerBName = currentState.opponentName ?: "Adversaire",
                                surface = currentState.surface ?: "HARD",
                                tournament = currentState.tournament,
                                competitionType = currentState.competitionType,
                                startedAt = currentState.sessionStartedAt
                            )
                        )
                    }
                }
```

`score.currentSetPointLog` (Task 1) porte déjà le déroulé point par point du set en cours — aucune dérivation locale n'est nécessaire ici, contrairement à `newLog`/`currentSetGameLog` qui reste un concept purement local à ce ViewModel pour la barre de momentum. La poussée est lancée dans une coroutine enfant (`viewModelScope.launch`) plutôt qu'attendue directement dans la boucle `collect`, pour que la latence réseau ne retarde jamais le traitement du point suivant ou la mise à jour de l'UI.

- [ ] **Step 7: Lancer les tests et vérifier qu'ils passent**

Run: `cd android && ./gradlew :feature:match:testDebugUnitTest --tests "com.secondserve.feature.match.MatchViewModelTest"`
Expected: tous les tests PASS (existants + 4 nouveaux).

- [ ] **Step 8: Commit**

```bash
git add android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt \
        android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt
git commit -m "feat(android): déclencher la création du lien et la poussée du score depuis MatchViewModel"
```

---

### Task 5: Bouton « Partager » dans MatchScreen

**Files:**
- Modify: `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchScreen.kt`

**Interfaces:**
- Consumes: `MatchSideEffect.ShareMatch` (Task 4), `viewModel::onShareRequested`.

- [ ] **Step 1: Ajouter les imports nécessaires**

Dans `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchScreen.kt`, ajouter :

```kotlin
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
```

- [ ] **Step 2: Gérer le nouveau side effect**

Modifier le bloc `viewModel.collectSideEffect { effect -> when (effect) { ... } }` — ajouter avant l'accolade fermante un import de contexte juste au-dessus (`val context = LocalContext.current`, à ajouter dans le corps de `MatchScreen` avant `viewModel.collectSideEffect`) puis la nouvelle branche :

```kotlin
    val context = LocalContext.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is MatchSideEffect.SessionClosed -> onSessionClosed()
            is MatchSideEffect.ShowError ->
                scope.launch { snackbarHostState.showSnackbar(effect.message) }
            is MatchSideEffect.ShareMatch -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Suis mon match en direct : ${effect.url}")
                }
                context.startActivity(Intent.createChooser(sendIntent, "Partager le match"))
            }
        }
    }
```

- [ ] **Step 3: Ajouter le bouton dans le header**

Modifier la `Row` du header (celle contenant `LiveChip()`, le texte "Set n · mm min" et le `CircleIconButton` de fermeture) pour ajouter le bouton Partager entre le texte et le bouton de fermeture :

```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveChip()
                Text(
                    text = buildString {
                        append("Set $setNumber")
                        if (elapsedMinutes != null) append(" · $elapsedMinutes min")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleIconButton(
                        onClick = viewModel::onShareRequested,
                        icon = Icons.Filled.Share,
                        contentDescription = "Partager le match"
                    )
                    CircleIconButton(
                        onClick = viewModel::onCloseRequested,
                        icon = Icons.Filled.Close,
                        contentDescription = "Terminer la session",
                        enabled = !state.isClosing
                    )
                }
            }
```

- [ ] **Step 4: Vérifier la compilation**

Run: `cd android && ./gradlew :feature:match:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Test manuel sur émulateur/appareil**

Lancer l'app (`cd android && ./gradlew :app:installDebug` sur un appareil connecté), démarrer un match, taper le bouton Partager (icône à côté du bouton de fermeture) : la feuille de partage Android doit s'ouvrir avec un texte contenant un lien `https://.../live/...`. Marquer plusieurs points (depuis la montre) et vérifier dans les logs (`adb logcat | grep LiveShareRepository`) qu'aucune erreur n'apparaît lors de la poussée du score.

- [ ] **Step 6: Commit**

```bash
git add android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchScreen.kt
git commit -m "feat(android): ajouter le bouton Partager à l'écran de match"
```
