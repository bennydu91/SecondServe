---
baseline_commit: 83d9ae6
---

# Story 3.3: OfflineCoachingCache — Init match & détection de pattern

Status: done

## Story

As a developer,
I want the `OfflineCoachingCache` pre-populated at match start with AI-generated coaching per `MatchPattern`,
so that personalized offline coaching is instantly available from the first changement de côté.

## Acceptance Criteria

1. **Given** l'enum `MatchPattern` est défini avec tous les patterns finalisés (ARCH-14 résolu)
   **When** une Session Match démarre
   **Then** `CoachingCachePrefetcher.initMatch(sessionId)` se déclenche en async non-bloquant

2. **And** Gemini Nano génère du contenu coaching pour chaque `MatchPattern` avec le `MatchContextProfile` courant
   **And** chaque entrée est stockée en Room — table `coaching_cache` créée via la migration de cette story : `match_id`, `pattern`, `content`, `generated_at`, `is_stale=false`

3. **And** `CoachingPatternDetector.detect(MatchStateSnapshot)` est déterministe : même état → même pattern, testable sans LLM

4. **Given** la génération du cache n'est pas encore terminée au premier `game_over`
   **Then** `GENERIC_FALLBACK_TEXTS` (map hardcodée dans `MatchPattern`) est utilisé — aucun blocage
   **And** les entrées stale (non rafraîchies depuis le dernier changeover) restent lisibles et ne sont jamais supprimées automatiquement

---

## Architecture Context

### Position dans la séquence (ARCH-13)

```
Story 3.1 ✅ → Story 3.2 ✅ → Story 3.3 (CETTE STORY) → 3.4 (CoachingResolver)
```

### Prérequis satisfaits

- ✅ `InferenceEngine` : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`
- ✅ `MockInferenceEngine` : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/mock/MockInferenceEngine.kt`
- ✅ `GeminiNanoEngine` : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/gemini/GeminiNanoEngine.kt`
- ✅ `AppResult<T>` : `android/domain/src/main/kotlin/com/secondserve/domain/AppResult.kt` — `Success(data)` et `Error(exception)` (mono-argument)
- ✅ `MatchScore` : `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchScore.kt`
- ✅ `MatchContextProfile` : `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt`
- ✅ `PlayerProfileRepository.buildMatchContextProfile()` : méthode existante dans `PlayerProfileRepositoryImpl` — retourne le profil complet du joueur
- ✅ `SessionRepository.getSessionById(id: Long)` : existant, retourne `Session?` avec `surface`
- ✅ `WorkAxisRepository.getActiveWorkAxesTitles()` : existant, retourne `List<String>`
- ✅ `SecondServeDatabase` version 5 : `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- ✅ Hilt 2.56.1, pattern `@Binds @Singleton abstract fun bind...` (voir `SessionModule.kt`)
- ✅ Timber 5.0.1 disponible, `CancellationException` pattern appliqué (Story 3.2)

### Ce que cette story NE fait PAS

- ❌ Pas de `CoachingResolver` (Story 3.4) — la chaîne Gemini → Cache → Static est Story 3.4
- ❌ Pas de `refreshPostChangeover()` — déclenchement post-changeover est Story 3.4
- ❌ Pas d'affichage de Conseil sur téléphone — Story 3.4
- ❌ Pas de `DataLayerListener` modification — le déclenchement du coaching sur game_over est Story 3.4
- ❌ Pas de `VpsMistralEngine` — Story 5.1

---

## Technical Requirements

### ARCH-14 RÉSOLU — Liste exhaustive des `MatchPattern` (20 patterns)

```kotlin
enum class MatchPattern(val description: String) {
    // Transitions de score
    NEUTRAL_TRANSITION("Jeu équilibré, pas de tendance marquée"),
    FIRST_GAME_WON("Premier jeu du set remporté — avantage psychologique initial"),
    FIRST_GAME_LOST("Premier jeu du set perdu — position de chasseur dès le début"),
    EQUAL_MIDSET("Égalité en milieu de set (2-2, 3-3)"),

    // Service
    SERVICE_HELD_EASY("Jeu de service tenu sans difficulté"),
    SERVICE_HELD_UNDER_PRESSURE("Jeu de service tenu sous pression (débreakage, égalités)"),
    SERVICE_BROKEN("Perte du jeu de service — break concédé"),

    // Break advantage
    BREAK_CONFIRMED("Break réalisé puis confirmé — avantage maintenu"),
    BREAK_LOST_AFTER_HOLD("Break perdu après avoir tenu son jeu de break"),
    DOUBLE_BREAK_ADVANTAGE("Avantage de 2 breaks — position dominante"),

    // Avance/retard courant
    DOMINANT_LEAD("Avance de 3 jeux ou plus dans le set courant"),
    COMEBACK_IN_PROGRESS("Retour dans le match après avoir été mené"),

    // Fin de set
    SET_WON_DOMINANT("Set remporté avec 3 jeux d'avance ou plus (ex: 6-2, 6-1)"),
    SET_WON_CLOSE("Set remporté de justesse (7-5, 7-6)"),
    SET_LOST_DOMINANT("Set perdu de plus de 2 jeux d'écart"),
    SET_LOST_CLOSE("Set perdu de peu (5-7, 6-7)"),

    // Tie-break
    TIEBREAK_APPROACHING("Score 5-5 dans le set — tie-break imminent"),
    TIEBREAK_ACTIVE("Tie-break en cours (6-6 atteint)"),
    SUPER_TIEBREAK_ACTIVE("Super tie-break en cours (3e set décisif)"),

    // Fin de match
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
```

---

### Fichier 1 — `MatchPattern.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/MatchPattern.kt`**

Contenu : l'enum complet avec 20 patterns, propriété `description: String`, et `companion object` avec `GENERIC_FALLBACK_TEXTS`.

---

### Fichier 2 — `MatchStateSnapshot.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/MatchStateSnapshot.kt`**

```kotlin
package com.secondserve.domain.model

data class MatchStateSnapshot(
    val score: MatchScore
)
```

Wrapper déterministe autour de `MatchScore`. Toute l'information nécessaire au detector est dans `MatchScore`.

---

### Fichier 3 — `CoachingPatternDetector.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/engine/CoachingPatternDetector.kt`**

```kotlin
package com.secondserve.domain.engine

import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchStateSnapshot

object CoachingPatternDetector {

    fun detect(snapshot: MatchStateSnapshot): MatchPattern {
        val score = snapshot.score

        // Cas super tie-break
        if (score.isSuperTieBreak) return MatchPattern.SUPER_TIEBREAK_ACTIVE

        // Tie-break actif
        if (score.isTieBreak) return MatchPattern.TIEBREAK_ACTIVE

        val myGames = score.currentSetGamesA
        val oppGames = score.currentSetGamesB
        val totalGames = myGames + oppGames
        val lastSet = score.completedSets.lastOrNull()

        // Juste après un set terminé (total jeux courant = 0)
        if (lastSet != null && totalGames == 0) {
            val mySetGames = lastSet.gamesA
            val oppSetGames = lastSet.gamesB
            val diff = Math.abs(mySetGames - oppSetGames)

            // Fin de match imminente si 2 sets gagnés (best-of-3)
            val setsWon = score.completedSets.count { it.gamesA > it.gamesB }
            if (setsWon >= 2) return MatchPattern.MATCH_POINT_APPROACHING

            return when {
                mySetGames > oppSetGames && diff >= 3 -> MatchPattern.SET_WON_DOMINANT
                mySetGames > oppSetGames             -> MatchPattern.SET_WON_CLOSE
                diff >= 3                            -> MatchPattern.SET_LOST_DOMINANT
                else                                 -> MatchPattern.SET_LOST_CLOSE
            }
        }

        // Tie-break imminent (5-5)
        if (myGames >= 5 && oppGames >= 5) return MatchPattern.TIEBREAK_APPROACHING

        // Premier jeu du set
        if (totalGames == 1) {
            return if (myGames == 1) MatchPattern.FIRST_GAME_WON else MatchPattern.FIRST_GAME_LOST
        }

        // Avantage dominant (3 jeux ou plus d'écart)
        if (myGames - oppGames >= 3) return MatchPattern.DOMINANT_LEAD
        if (oppGames - myGames >= 3) return MatchPattern.DOUBLE_BREAK_ADVANTAGE

        // Égalité milieu de set
        if (myGames == oppGames && myGames in 2..4) return MatchPattern.EQUAL_MIDSET

        // Retour dans le match (étaient menés dans un set précédent, maintenant à l'avantage)
        val previousSetLost = score.completedSets.any { it.gamesA < it.gamesB }
        val currentlyLeading = myGames > oppGames
        if (previousSetLost && currentlyLeading) return MatchPattern.COMEBACK_IN_PROGRESS

        // Transition par défaut
        return MatchPattern.NEUTRAL_TRANSITION
    }
}
```

> **Règle critique :** `CoachingPatternDetector` est un `object` (singleton stateless), zéro injection Hilt, 100% testable JVM. Les patterns `SERVICE_HELD_*`, `BREAK_CONFIRMED`, `BREAK_LOST_AFTER_HOLD` ne sont **pas** détectables sans tracking du serveur — ils sont réservés à l'utilisation depuis `GENERIC_FALLBACK_TEXTS` pour le pre-fetch. Le detector n'a besoin que de `MatchScore`.

---

### Fichier 4 — `CoachingCacheEntry.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingCacheEntry.kt`**

```kotlin
package com.secondserve.domain.model

data class CoachingCacheEntry(
    val id: Long = 0L,
    val matchId: Long,
    val pattern: MatchPattern,
    val content: String,
    val generatedAt: Long,
    val isStale: Boolean = false
)
```

---

### Fichier 5 — `CoachingRepository.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt`**

```kotlin
package com.secondserve.domain.repository

import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern

interface CoachingRepository {
    suspend fun getCachedAdvice(matchId: Long, pattern: MatchPattern): CoachingCacheEntry?
    suspend fun saveAdvice(matchId: Long, pattern: MatchPattern, content: String)
    suspend fun markMatchEntriesStale(matchId: Long)
}
```

---

### Fichier 6 — `CoachingCacheEntity.kt` (NEW) dans `:data`

**`android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingCacheEntity.kt`**

```kotlin
package com.secondserve.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern

@Entity(
    tableName = "coaching_cache",
    indices = [Index(value = ["match_id", "pattern"], unique = true)]
)
data class CoachingCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "match_id") val matchId: Long,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "is_stale") val isStale: Boolean = false
)

fun CoachingCacheEntity.toDomain(): CoachingCacheEntry = CoachingCacheEntry(
    id = id,
    matchId = matchId,
    pattern = MatchPattern.valueOf(pattern),
    content = content,
    generatedAt = generatedAt,
    isStale = isStale
)

fun CoachingCacheEntry.toEntity(): CoachingCacheEntity = CoachingCacheEntity(
    id = id,
    matchId = matchId,
    pattern = pattern.name,
    content = content,
    generatedAt = generatedAt,
    isStale = isStale
)
```

> **⚠️ Contrainte UNIQUE :** l'index `unique = true` sur `(match_id, pattern)` garantit qu'il n'y a qu'une entrée par pattern par match. Le DAO utilisera `OnConflictStrategy.REPLACE` pour les upserts.

---

### Fichier 7 — `CoachingCacheDao.kt` (NEW) dans `:data`

**`android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingCacheDao.kt`**

```kotlin
package com.secondserve.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secondserve.data.local.db.entity.CoachingCacheEntity

@Dao
interface CoachingCacheDao {
    @Query("SELECT * FROM coaching_cache WHERE match_id = :matchId AND pattern = :pattern LIMIT 1")
    suspend fun getEntry(matchId: Long, pattern: String): CoachingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: CoachingCacheEntity)

    @Query("UPDATE coaching_cache SET is_stale = 1 WHERE match_id = :matchId")
    suspend fun markAllStale(matchId: Long)

    @Query("SELECT * FROM coaching_cache WHERE match_id = :matchId")
    suspend fun getAllForMatch(matchId: Long): List<CoachingCacheEntity>
}
```

---

### Fichier 8 — `SecondServeDatabase.kt` (UPDATE)

**`android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`**

Modifications :
1. Ajouter `CoachingCacheEntity::class` dans la liste `entities`
2. Bumper `version = 5` → `version = 6`
3. Ajouter `abstract fun coachingCacheDao(): CoachingCacheDao`
4. Ajouter `MIGRATION_5_6` dans le companion object

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS coaching_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                match_id INTEGER NOT NULL,
                pattern TEXT NOT NULL,
                content TEXT NOT NULL,
                generated_at INTEGER NOT NULL,
                is_stale INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_coaching_cache_match_pattern 
            ON coaching_cache (match_id, pattern)
        """.trimIndent())
    }
}
```

> **⚠️ La UNIQUE constraint n'est pas dans Room `@Entity` directement pour les migrations** — elle est créée via `CREATE UNIQUE INDEX`. L'annotation `@Entity(indices = [Index(..., unique=true)])` dans l'entité est la déclaration Room pour la validation de schéma, mais la migration SQL doit la créer explicitement via `CREATE UNIQUE INDEX`.

> **⚠️ Imports à ajouter dans `SecondServeDatabase.kt`:**
> - `import com.secondserve.data.local.dao.CoachingCacheDao`
> - `import com.secondserve.data.local.db.entity.CoachingCacheEntity`

---

### Fichier 9 — `CoachingRepositoryImpl.kt` (NEW) dans `:data`

**`android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`**

```kotlin
package com.secondserve.data.repository

import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.db.entity.CoachingCacheEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.repository.CoachingRepository
import javax.inject.Inject

class CoachingRepositoryImpl @Inject constructor(
    private val dao: CoachingCacheDao
) : CoachingRepository {

    override suspend fun getCachedAdvice(matchId: Long, pattern: MatchPattern): CoachingCacheEntry? =
        dao.getEntry(matchId, pattern.name)?.toDomain()

    override suspend fun saveAdvice(matchId: Long, pattern: MatchPattern, content: String) {
        dao.upsertEntry(
            CoachingCacheEntity(
                matchId = matchId,
                pattern = pattern.name,
                content = content,
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markMatchEntriesStale(matchId: Long) =
        dao.markAllStale(matchId)
}
```

---

### Fichier 10 — `CoachingModule.kt` (NEW) dans `:data`

**`android/data/src/main/kotlin/com/secondserve/data/di/CoachingModule.kt`**

```kotlin
package com.secondserve.data.di

import com.secondserve.data.repository.CoachingRepositoryImpl
import com.secondserve.domain.repository.CoachingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoachingModule {

    @Binds
    @Singleton
    abstract fun bindCoachingRepository(impl: CoachingRepositoryImpl): CoachingRepository
}
```

> **⚠️ Pattern identique à `SessionModule.kt`** — `@Binds @Singleton abstract fun bind...`

> **⚠️ `CoachingRepositoryImpl` a besoin de `CoachingCacheDao` via Hilt.** Le `CoachingCacheDao` est exposé via `SecondServeDatabase` dans le `DataModule` ou similaire. Vérifier qu'un `@Provides` pour `CoachingCacheDao` existe ou en ajouter un dans `DataModule`.

---

### Fichier 11 — `DataModule.kt` (UPDATE si nécessaire)

Chercher `DataModule.kt` dans `:data/di/`. S'il expose les autres DAOs, ajouter :
```kotlin
@Provides
@Singleton
fun provideCoachingCacheDao(db: SecondServeDatabase): CoachingCacheDao = db.coachingCacheDao()
```

Si `DataModule.kt` n'existe pas encore (les DAOs sont fournis autrement), chercher comment les autres DAOs sont exposés en Hilt dans le module `:data` et répliquer le pattern pour `CoachingCacheDao`.

---

### Fichier 12 — `CoachingCachePrefetcher.kt` (NEW) dans `:feature:match`

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingCachePrefetcher.kt`**

```kotlin
package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachingCachePrefetcher @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val coachingRepository: CoachingRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val sessionRepository: SessionRepository
) {
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initMatch(sessionId: Long) {
        prefetchScope.launch {
            try {
                val session = sessionRepository.getSessionById(sessionId)
                    ?: run {
                        Timber.d("CoachingCachePrefetcher: session %d not found, abort", sessionId)
                        return@launch
                    }
                val contextProfile = playerProfileRepository.buildMatchContextProfile()

                MatchPattern.entries.forEach { pattern ->
                    val prompt = buildPrompt(pattern, contextProfile, session.surface)
                    when (val result = inferenceEngine.generate(prompt)) {
                        is AppResult.Success -> {
                            coachingRepository.saveAdvice(sessionId, pattern, result.data)
                            Timber.d("CoachingCachePrefetcher: cached %s", pattern)
                        }
                        is AppResult.Error -> {
                            Timber.d("CoachingCachePrefetcher: %s → fallback (LLM error)", pattern)
                            val fallback = MatchPattern.GENERIC_FALLBACK_TEXTS[pattern] ?: return@forEach
                            coachingRepository.saveAdvice(sessionId, pattern, fallback)
                        }
                        AppResult.Loading -> {}
                    }
                }
                Timber.d("CoachingCachePrefetcher: initMatch done, session=%d", sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "CoachingCachePrefetcher: initMatch failed for session=%d", sessionId)
            }
        }
    }

    private fun buildPrompt(
        pattern: MatchPattern,
        context: MatchContextProfile,
        surface: String
    ): String = buildString {
        append("Tu es coach tennis. Situation de jeu : ${pattern.description}.\n")
        append("Surface : $surface.")
        if (context.fftSeries != null) append(" Classement joueur : ${context.fftSeries}.")
        if (context.playStyle != null) append(" Style : ${context.playStyle}.")
        if (context.activeWorkAxes.isNotEmpty()) {
            append(" Axes de travail : ${context.activeWorkAxes.joinToString(", ")}.")
        }
        if (context.coachInstructions.isNotEmpty()) {
            append(" Consignes coach : ${context.coachInstructions.joinToString(". ")}.")
        }
        append("\nDonne un conseil court (2-3 phrases max) pour le prochain jeu.")
    }
}
```

> **⚠️ `MatchPattern.entries`** — Kotlin 1.9+. Utiliser `MatchPattern.values().toList()` si la version Kotlin est < 1.9. Vérifier `kotlinOptions { jvmTarget = "17" }` dans les build files — la version Kotlin utilisée dans ce projet supporte `.entries`.

> **⚠️ `CancellationException` doit être re-throwée** — Pattern établi en Story 3.2, obligatoire dans tout `catch (e: Exception)` des coroutines.

> **⚠️ `@Singleton` sur `CoachingCachePrefetcher`** — scope Hilt singleton, le `prefetchScope` vit tant que l'app tourne. Acceptable pour cette architecture.

---

### Fichier 13 — `MatchViewModel.kt` (UPDATE)

**`android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt`**

Ajouter deux paramètres dans le constructeur :
```kotlin
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val closeMatchUseCase: CloseMatchUseCase,
    private val syncScheduler: SyncScheduler,
    private val dataLayerEventBus: DataLayerEventBus,
    private val coachingCachePrefetcher: CoachingCachePrefetcher,  // ← AJOUT
    savedStateHandle: SavedStateHandle
) : ViewModel(), ContainerHost<MatchUiState, MatchSideEffect> {
```

Dans le bloc `init {}` existant (actuellement il observe `closeSessionRequests`), ajouter :
```kotlin
init {
    coachingCachePrefetcher.initMatch(sessionId)  // ← AJOUT — fire-and-forget non-bloquant

    viewModelScope.launch {
        dataLayerEventBus.closeSessionRequests.collect {
            onCloseRequested()
        }
    }
}
```

> **⚠️ Pas de `SessionRepository` à injecter dans `MatchViewModel`** — `CoachingCachePrefetcher` obtient la session lui-même via `SessionRepository`. `MatchViewModel` n'a pas besoin de `SessionRepository` directement pour cette story.

---

### Fichier 14 — `feature/match/build.gradle.kts` (UPDATE)

**`android/feature/match/build.gradle.kts`**

Ajouter la dépendance manquante :
```kotlin
dependencies {
    implementation(project(":domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:ai"))          // ← AJOUT — pour InferenceEngine
    // ... reste identique
}
```

---

### Fichier 15 — `DataModule.kt` (UPDATE) dans `:app`

**`android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`**

Ce fichier est dans `:app` (pas dans `:data`). Deux modifications obligatoires :

**Modification 1 — Ajouter `MIGRATION_5_6` dans `provideSecondServeDatabase` :**
```kotlin
.addMigrations(
    SecondServeDatabase.MIGRATION_1_2,
    SecondServeDatabase.MIGRATION_2_3,
    SecondServeDatabase.MIGRATION_3_4,
    SecondServeDatabase.MIGRATION_4_5,
    SecondServeDatabase.MIGRATION_5_6    // ← AJOUT
)
```

**Modification 2 — Ajouter le provider pour `CoachingCacheDao` (après les autres DAOs) :**
```kotlin
@Provides
@Singleton
fun provideCoachingCacheDao(db: SecondServeDatabase): CoachingCacheDao =
    db.coachingCacheDao()
```

Ajouter l'import correspondant :
```kotlin
import com.secondserve.data.local.dao.CoachingCacheDao
```

> **⚠️ Les deux modifications sont obligatoires.** Oublier `MIGRATION_5_6` dans `addMigrations` provoque `IllegalStateException: A migration from 5 to 6 was required but not found` au premier lancement après upgrade. Oublier `provideCoachingCacheDao` provoque une erreur Hilt à la compilation (`MissingBinding`).

---

## Tasks / Subtasks

### Domain — Contrats

- [x] **Task OCC-1** — Créer `MatchPattern.kt` dans `domain/model/` avec les 20 patterns, `description: String`, et `GENERIC_FALLBACK_TEXTS` companion object (AC: #1, #4)
- [x] **Task OCC-2** — Créer `MatchStateSnapshot.kt` dans `domain/model/` — wrapper `data class` autour de `MatchScore` (AC: #3)
- [x] **Task OCC-3** — Créer `CoachingCacheEntry.kt` dans `domain/model/` avec `matchId, pattern, content, generatedAt, isStale` (AC: #2)
- [x] **Task OCC-4** — Créer `CoachingRepository.kt` dans `domain/repository/` avec 3 méthodes : `getCachedAdvice`, `saveAdvice`, `markMatchEntriesStale` (AC: #2, #4)
- [x] **Task OCC-5** — Créer `CoachingPatternDetector.kt` dans `domain/engine/` comme `object` avec `fun detect(snapshot): MatchPattern` — logique déterministe basée sur `MatchScore` (AC: #3)

### Data — Room + Repository

- [x] **Task OCC-6** — Créer `CoachingCacheEntity.kt` dans `data/local/db/entity/` avec `@Entity(tableName = "coaching_cache", indices = [Index(..., unique=true)])` + mappers `toDomain()` / `toEntity()` (AC: #2)
- [x] **Task OCC-7** — Créer `CoachingCacheDao.kt` dans `data/local/dao/` avec `getEntry`, `upsertEntry` (OnConflictStrategy.REPLACE), `markAllStale`, `getAllForMatch` (AC: #2, #4)
- [x] **Task OCC-8** — Mettre à jour `SecondServeDatabase.kt` : version 5→6, ajouter `CoachingCacheEntity`, `MIGRATION_5_6` (CREATE TABLE + CREATE UNIQUE INDEX), `abstract fun coachingCacheDao()` (AC: #2)
- [x] **Task OCC-9** — Créer `CoachingRepositoryImpl.kt` dans `data/repository/` avec `@Inject constructor(dao: CoachingCacheDao)` (AC: #2, #4)
- [x] **Task OCC-10** — Créer `CoachingModule.kt` dans `data/di/` avec `@Binds @Singleton abstract fun bindCoachingRepository(impl: CoachingRepositoryImpl): CoachingRepository` (AC: #2)
- [x] **Task OCC-11** — Mettre à jour `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` : ajouter `MIGRATION_5_6` dans `addMigrations(...)` ET ajouter `provideCoachingCacheDao(db)` (AC: #2)

### Feature match — Prefetcher + ViewModel

- [x] **Task OCC-12** — Ajouter `implementation(project(":core:ai"))` dans `feature/match/build.gradle.kts` (AC: #1)
- [x] **Task OCC-13** — Créer `CoachingCachePrefetcher.kt` dans `feature/match/` avec `@Singleton @Inject constructor(...)`, `fun initMatch(sessionId: Long)` (non-suspend, lance en interne), `buildPrompt()` privé (AC: #1, #2, #4)
- [x] **Task OCC-14** — Mettre à jour `MatchViewModel.kt` : ajouter `CoachingCachePrefetcher` dans le constructeur, appeler `coachingCachePrefetcher.initMatch(sessionId)` en tête du `init {}` (AC: #1)

### Tests

- [x] **Task OCC-15** — Créer `CoachingPatternDetectorTest.kt` dans `domain/src/test/` : couvrir les 20 patterns avec des `MatchScore` déterministes, vérifier qu'un même état → même pattern (AC: #3)
- [x] **Task OCC-16** — Lancer `:app:kspDebugKotlin` et `:app:kspReleaseKotlin` — BUILD SUCCESSFUL, aucun conflit Hilt (AC: #1, #2)
- [x] **Task OCC-17** — Lancer `:domain:test` — `CoachingPatternDetectorTest` 18/18 verts, aucune régression sur les tests existants (AC: #3)
- [x] **Task OCC-18** — Lancer `:core:ai:test` — BUILD SUCCESSFUL, aucune régression Story 3.1/3.2 (AC: #1, #2)

---

## Dev Notes

### Guardrails critiques

#### ⚠️ `MIGRATION_5_6` : UNIQUE INDEX via SQL séparé (pas inline)

Room ne supporte pas les contraintes UNIQUE directement dans `CREATE TABLE` pour les migrations. La seule façon correcte est :
```sql
-- 1. Créer la table SANS UNIQUE inline
CREATE TABLE IF NOT EXISTS coaching_cache (...)
-- 2. Créer l'index UNIQUE séparé
CREATE UNIQUE INDEX IF NOT EXISTS idx_coaching_cache_match_pattern ON coaching_cache (match_id, pattern)
```
La déclaration `@Entity(indices = [Index(..., unique=true)])` dans l'entité Room est pour la validation du schéma, pas pour la migration.

#### ⚠️ `CoachingCacheEntity` — `pattern` stocké comme `String` (nom de l'enum)

`MatchPattern` est stocké en base comme `String` (via `pattern.name`) et non comme `Int`. Cela protège contre la réorganisation future des valeurs de l'enum qui briserait les mappings si on utilisait l'ordinal.
```kotlin
// ✅ Correct
pattern = MatchPattern.valueOf(entity.pattern)
// ❌ Interdit
pattern = MatchPattern.values()[entity.ordinalAsInt]
```

#### ⚠️ `CoachingCachePrefetcher` — `CancellationException` re-throwée

Pattern établi Story 3.2, critique pour les coroutines :
```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, "...")
}
```

#### ⚠️ `CoachingCachePrefetcher.initMatch()` — non-suspend, fire-and-forget

`initMatch()` est une fonction normale (pas `suspend`). Elle lance une coroutine interne dans `prefetchScope`. `MatchViewModel.init {}` l'appelle sans `launch {}` supplémentaire :
```kotlin
// ✅ Correct dans MatchViewModel.init {}
coachingCachePrefetcher.initMatch(sessionId)

// ❌ Interdit — launch double inutile
viewModelScope.launch { coachingCachePrefetcher.initMatch(sessionId) }  // ne fonctionne même pas (non suspend)
```

#### ⚠️ `MatchPattern.entries` vs `values()`

`MatchPattern.entries` (Kotlin 1.9+) est préféré. Si compilation échoue avec `entries`, utiliser `MatchPattern.values().toList()`.

#### ⚠️ `AppResult.Error(exception)` — mono-argument uniquement

Pattern établi Stories 2.4, 2.6, 3.1, 3.2 :
```kotlin
// ✅ Correct
AppResult.Error(InferenceEngineException(ErrorCode.INFERENCE_FAILED, "..."))
// ❌ Interdit
AppResult.Error(exception, "message")  // AppResult.Error n'a pas de surcharge 2-args
```

#### ⚠️ `@Inject constructor` obligatoire sur `CoachingCachePrefetcher` et `CoachingRepositoryImpl`

Bug reproduit Stories 2.6, 3.1, 3.2. Sans `@Inject`, Hilt échoue silencieusement.

#### ⚠️ `CoachingPatternDetector` est un `object` — pas de classe, pas de `@Inject`

Aucune injection Hilt sur le detector. Il est appelé directement comme `CoachingPatternDetector.detect(snapshot)`. Rendre testable JVM sans Hilt est le but explicite.

#### ⚠️ Vérifier DataModule — exposition des DAOs

Avant de créer un nouveau `@Provides fun provideCoachingCacheDao`, chercher comment les DAOs existants (`SessionDao`, `PlayerProfileDao`, etc.) sont fournis. Utiliser exactement le même pattern.

### Patterns à réutiliser depuis les stories précédentes

| Pattern | Source |
|---------|--------|
| `@Binds @Singleton abstract fun bind...` | `SessionModule.kt` — pattern Hilt binding |
| `@Inject constructor(dao: ...)` dans Impl | `SessionRepositoryImpl.kt`, `CoachingRepositoryImpl.kt` |
| `entity.toDomain()` / `domain.toEntity()` | `Mappers.kt` pattern existant dans `:data` |
| `Timber.d(...)` / `Timber.e(exception, ...)` | partout — jamais `Log.*` |
| `CancellationException` re-throw | `GeminiNanoEngine.kt` (Story 3.2) |
| `CoroutineScope(SupervisorJob() + Dispatchers.IO)` | Pattern coroutine scope durable |
| `OnConflictStrategy.REPLACE` pour upserts | `SessionDao.kt` |

### Structure fichiers finale

```
android/
├── domain/
│   └── src/main/kotlin/com/secondserve/domain/
│       ├── model/
│       │   ├── MatchPattern.kt               ← NEW (20 patterns + GENERIC_FALLBACK_TEXTS)
│       │   ├── MatchStateSnapshot.kt         ← NEW
│       │   └── CoachingCacheEntry.kt         ← NEW
│       ├── engine/
│       │   └── CoachingPatternDetector.kt    ← NEW (object, testable JVM)
│       └── repository/
│           └── CoachingRepository.kt         ← NEW
│
├── data/
│   └── src/main/kotlin/com/secondserve/data/
│       ├── local/
│       │   ├── dao/
│       │   │   └── CoachingCacheDao.kt       ← NEW
│       │   └── db/
│       │       ├── entity/
│       │       │   └── CoachingCacheEntity.kt  ← NEW
│       │       └── SecondServeDatabase.kt    ← UPDATE (v5→v6, MIGRATION_5_6)
│       ├── repository/
│       │   └── CoachingRepositoryImpl.kt     ← NEW
│       └── di/
│           └── CoachingModule.kt             ← NEW
│
├── app/
│   └── src/main/kotlin/com/secondserve/di/
│       └── DataModule.kt                     ← UPDATE (+ MIGRATION_5_6 + provideCoachingCacheDao)
│
└── feature/match/
    ├── build.gradle.kts                      ← UPDATE (+ :core:ai)
    └── src/main/kotlin/com/secondserve/feature/match/
        ├── CoachingCachePrefetcher.kt        ← NEW
        └── MatchViewModel.kt                 ← UPDATE (+ CoachingCachePrefetcher injection)
```

### Tests — CoachingPatternDetectorTest.kt

Le test doit couvrir les scénarios suivants (exemples) :
```kotlin
// AC#3 : même état → même pattern
@Test fun `detect TIEBREAK_ACTIVE when isTieBreak is true`()
@Test fun `detect SUPER_TIEBREAK_ACTIVE when isSuperTieBreak is true`()
@Test fun `detect SET_WON_DOMINANT when last set won 6-2`()
@Test fun `detect SET_WON_CLOSE when last set won 7-5`()
@Test fun `detect SET_LOST_DOMINANT when last set lost 2-6`()
@Test fun `detect TIEBREAK_APPROACHING when games are 5-5`()
@Test fun `detect FIRST_GAME_WON when totalGames is 1 and myGames is 1`()
@Test fun `detect NEUTRAL_TRANSITION for balanced mid-set state`()
```

### VPS — Aucun changement

Story 3.3 est 100% Android. Le backend VPS n'est pas modifié.

### Références

- [Source: epics.md § Story 3.3] — User story et ACs complets
- [Source: architecture.md § Architecture OfflineCoachingCache] — Composants, règles de staleness, GENERIC_FALLBACK_TEXTS
- [Source: architecture.md § ARCH-9] — "MatchPattern enum (liste exhaustive ~15-30 patterns à figer), CoachingPatternDetector, CoachingCacheRepository, CoachingCachePrefetcher, CoachingResolver"
- [Source: architecture.md § ARCH-14] — "PRÉREQUIS BLOQUANT — Liste exhaustive MatchPattern doit être figée AVANT cette story"
- [Source: architecture.md § Naming Patterns] — snake_case tables, PascalCase classes, camelCase properties
- [Source: 3-2-geminananoengine-coaching-on-device.md § Dev Notes] — CancellationException, AppResult.Error mono-arg, @Inject constructor
- [Source: SecondServeDatabase.kt] — version 5, pattern Migration existant (MIGRATION_4_5)
- [Source: SessionModule.kt] — pattern @Binds Hilt binding
- [Source: PlayerProfileRepositoryImpl.kt] — `buildMatchContextProfile()` existant, prêt à l'emploi

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `:domain:testDebugUnitTest` absent → task correcte = `:domain:test` (module pure Kotlin, pas Android library)
- CI run 1 — Hilt : `MockInferenceEngine @Inject constructor` avec valeurs par défaut génère 2 constructeurs JVM → Hilt refuse. Fix : supprimer `@Inject`, passer `debug/AiModule` de `@Binds abstract class` à `@Provides object`.
- CI run 2 — `MatchViewModelTest` : nouveau paramètre `coachingCachePrefetcher` non passé. Fix : ajouter `mockk(relaxed = true)` dans le setup du test.

### Completion Notes List

- Créé `MatchPattern.kt` (enum 20 patterns + GENERIC_FALLBACK_TEXTS companion) dans `:domain`
- Créé `MatchStateSnapshot.kt` (wrapper déterministe autour de `MatchScore`) dans `:domain`
- Créé `CoachingCacheEntry.kt` (domain model avec `isStale`) dans `:domain`
- Créé `CoachingRepository.kt` (interface 3 méthodes) dans `:domain`
- Créé `CoachingPatternDetector.kt` (object singleton, logique déterministe, 0 Hilt) dans `:domain/engine`
- Créé `CoachingCacheEntity.kt` avec mappers `toDomain()`/`toEntity()` dans `:data`
- Créé `CoachingCacheDao.kt` (4 méthodes Room, OnConflictStrategy.REPLACE) dans `:data`
- Mis à jour `SecondServeDatabase.kt` : v5→v6, `CoachingCacheEntity`, `MIGRATION_5_6` (CREATE TABLE + CREATE UNIQUE INDEX séparés), `abstract fun coachingCacheDao()`
- Créé `CoachingRepositoryImpl.kt` (`@Inject constructor(dao)`) dans `:data`
- Créé `CoachingModule.kt` (`@Binds @Singleton`) dans `:data/di`
- Mis à jour `DataModule.kt` : ajout `MIGRATION_5_6` dans `addMigrations` + `provideCoachingCacheDao`
- Ajouté `implementation(project(":core:ai"))` dans `feature/match/build.gradle.kts`
- Créé `CoachingCachePrefetcher.kt` (`@Singleton`, `initMatch` fire-and-forget, `buildPrompt` privé, `CancellationException` re-throwée)
- Mis à jour `MatchViewModel.kt` : injection `CoachingCachePrefetcher`, appel `initMatch(sessionId)` en tête du `init {}`
- Créé `CoachingPatternDetectorTest.kt` : 18 tests couvrant tous les patterns détectables, déterminisme vérifié
- Validations : `:domain:test` 18/18 verts + tests existants sans régression ; `:core:ai:test` BUILD SUCCESSFUL ; `:app:kspDebugKotlin` + `:app:kspReleaseKotlin` BUILD SUCCESSFUL sans conflit Hilt

### File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchPattern.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchStateSnapshot.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingCacheEntry.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/engine/CoachingPatternDetector.kt` (NEW)
- `android/domain/src/test/kotlin/com/secondserve/domain/engine/CoachingPatternDetectorTest.kt` (NEW)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingCacheEntity.kt` (NEW)
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingCacheDao.kt` (NEW)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt` (MODIFIED)
- `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt` (NEW)
- `android/data/src/main/kotlin/com/secondserve/data/di/CoachingModule.kt` (NEW)
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt` (MODIFIED)
- `android/feature/match/build.gradle.kts` (MODIFIED)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/CoachingCachePrefetcher.kt` (NEW)
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt` (MODIFIED)

### Review Findings

- [ ] [Review][Decision] DOUBLE_BREAK_ADVANTAGE condition inversée : `oppGames - myGames >= 3` → conseil "Position excellente" quand le joueur est en déficit de 3 jeux. Options : A) renommer en `DOUBLE_BREAK_DOWN` + maj description/fallback, B) conserver le nom et corriger la condition pour déclencher quand le joueur mène (ex: myGames-oppGames >= 2, à calibrer vs DOMINANT_LEAD)
- [ ] [Review][Decision] MATCH_POINT_APPROACHING se déclenche quand le match est déjà terminé (`setsWon >= 2 && totalGames == 0` en best-of-3 = victoire acquise, pas en approche). Options : A) accepter (conseil pertinent à la clôture), B) déplacer la condition plus tôt dans le set, C) déférer à Story 3.4 qui gérera `isMatchOver`
- [ ] [Review][Patch] `MatchPattern.valueOf()` throw non attrapé dans `toDomain()` — crash si nom stocké inconnu de l'enum [CoachingCacheEntity.kt:toDomain()]
- [ ] [Review][Patch] `initMatch()` multi-appels sans déduplication sur le `@Singleton` — deux ViewModel successifs lancent 2×20 inférences concurrentes [CoachingCachePrefetcher.kt]
- [ ] [Review][Patch] Test `detect TIEBREAK_APPROACHING when games are 6-6 not yet tiebreak` valide un état tennis impossible (6-6 avec isTieBreak=false) [CoachingPatternDetectorTest.kt:144]
- [ ] [Review][Patch] `MatchViewModelTest` : pas de `verify { coachingCachePrefetcher.initMatch(10L) }` — l'appel n'est pas asserté [MatchViewModelTest.kt]
- [ ] [Review][Patch] `sessionId = 0L` par défaut sans garde dans `initMatch()` — déclenche un appel IO inutile si SavedStateHandle incomplet [MatchViewModel.kt:29 + CoachingCachePrefetcher.kt]
- [ ] [Review][Patch] `AppResult.Loading` → no-op silencieux sans fallback ni log dans `initMatch()` [CoachingCachePrefetcher.kt]
- [x] [Review][Defer] Aucun test d'intégration Room pour MIGRATION_5_6 / CoachingCacheDao — déféré, hors scope story 3.3
- [x] [Review][Defer] Frontière SET_WON_CLOSE inclut 6-4 (diff=2) — déféré, choix de design
- [x] [Review][Defer] Injection prompt possible via coachInstructions/surface dans buildPrompt() — déféré, risque acceptable pour LLM on-device MVP
- [x] [Review][Defer] detect() non gardé pour isMatchOver=true — déféré, Story 3.4 concern
- [x] [Review][Defer] markMatchEntriesStale() jamais appelé dans le code feature — déféré, Story 3.4 concern

## Change Log

- 2026-06-22 : Création story 3.3 — OfflineCoachingCache, init match & détection de pattern.
- 2026-06-22 : Implémentation complète — 10 nouveaux fichiers, 5 fichiers modifiés. MatchPattern (20 patterns), CoachingPatternDetector (object déterministe), Room migration v5→v6, CoachingCachePrefetcher (fire-and-forget), MatchViewModel mis à jour. 18 tests verts, Hilt graph valide debug+release.
- 2026-06-22 : Fix CI — `MockInferenceEngine` @Inject supprimé, `debug/AiModule` converti en `@Provides object`, `MatchViewModelTest` mis à jour avec mock `CoachingCachePrefetcher`. CI verte (run #27946843675).
