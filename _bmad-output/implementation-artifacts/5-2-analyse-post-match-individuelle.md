---
baseline_commit: 91bf278d42fc59845b9a6ed8cc7b170f2c9b08c3
---

# Story 5.2: Analyse post-match individuelle

Status: done

## Story

As a player,
I want an individual AI analysis generated automatically after each match I close,
so that I immediately know what worked and what to focus on before the next session.

## Acceptance Criteria

1. **Given** je clôture une Session Match (Story 2.6)
   **When** le réseau est disponible
   **Then** `GeneratePostMatchAnalysisUseCase` se déclenche automatiquement
   **And** il appelle `VpsMistralEngine` avec : surface, format, score, résultat, nombre de points gagnés/perdus, Axes de travail actifs, Profil joueur (série FFT + style + instructions coaching)
   **And** l'analyse générée contient : points forts observés, points faibles, écart avec les Axes actifs, 1-2 recommandations concrètes
   **And** l'analyse référence des données spécifiques de la session (score, surface) — jamais de contenu générique (FR-10, NFR-C3)
   **And** elle est persistée en Room (table `coaching_analyses`, migration 7→8 créée dans cette story) et consultable hors connexion
   **And** elle est accessible depuis le détail de la Session (Story 4.1)

2. **Given** le réseau est indisponible à la clôture
   **Then** la génération est mise en queue WorkManager avec contrainte `NetworkType.CONNECTED` et s'exécute au retour du réseau (NFR-OFF3)

## Tasks / Subtasks

- [x] **T1 — Domain model `CoachingAnalysis`** (AC: 1, 2)
  - [x] T1.1 Créer `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingAnalysis.kt` : `data class CoachingAnalysis(val id: Long = 0L, val sessionId: Long, val content: String, val generatedAt: Long)`

- [x] **T2 — Mise à jour `CoachingRepository` interface** (AC: 1)
  - [x] T2.1 Ajouter dans `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt` :
    - `suspend fun saveAnalysis(sessionId: Long, content: String): AppResult<CoachingAnalysis>`
    - `suspend fun getAnalysisForSession(sessionId: Long): CoachingAnalysis?`
    - `fun observeAnalysisForSession(sessionId: Long): Flow<CoachingAnalysis?>`

- [x] **T3 — `CoachingAnalysisEntity` + `CoachingAnalysisDao`** (AC: 1)
  - [x] T3.1 Créer `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingAnalysisEntity.kt` :
    - `@Entity(tableName = "coaching_analyses", foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = CASCADE)], indices = [Index(value = ["session_id"], unique = true)])`
    - Champs : `id LONG PK autoGenerate`, `session_id LONG`, `content TEXT`, `generated_at LONG`
    - Extension fun `toDomain()` et `CoachingAnalysis.toEntity()`
  - [x] T3.2 Créer `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt` :
    - `@Insert(OnConflictStrategy.REPLACE) suspend fun insert(entity): Long`
    - `@Query("SELECT * FROM coaching_analyses WHERE session_id = :sessionId LIMIT 1") suspend fun getBySessionId(sessionId: Long): CoachingAnalysisEntity?`
    - `@Query("SELECT * FROM coaching_analyses WHERE session_id = :sessionId LIMIT 1") fun observeBySessionId(sessionId: Long): Flow<CoachingAnalysisEntity?>`

- [x] **T4 — Migration Room 7→8 + mise à jour `SecondServeDatabase`** (AC: 1)
  - [x] T4.1 Incrémenter `version = 8` dans `@Database`
  - [x] T4.2 Ajouter `CoachingAnalysisEntity::class` à la liste entities
  - [x] T4.3 Ajouter `abstract fun coachingAnalysisDao(): CoachingAnalysisDao`
  - [x] T4.4 Ajouter `MIGRATION_7_8` :
    ```sql
    CREATE TABLE IF NOT EXISTS coaching_analyses (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        session_id INTEGER NOT NULL,
        content TEXT NOT NULL,
        generated_at INTEGER NOT NULL,
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
    )
    CREATE UNIQUE INDEX IF NOT EXISTS idx_coaching_analyses_session ON coaching_analyses (session_id)
    ```
  - [x] T4.5 Dans `DataModule.kt` : ajouter `MIGRATION_7_8` dans `.addMigrations(...)` + provider `CoachingAnalysisDao`

- [x] **T5 — Mise à jour `CoachingRepositoryImpl`** (AC: 1)
  - [x] T5.1 Injecter `CoachingAnalysisDao` dans le constructeur
  - [x] T5.2 Implémenter `saveAnalysis` : insert `CoachingAnalysisEntity`, retourner `AppResult.Success(entity.toDomain())`
  - [x] T5.3 Implémenter `getAnalysisForSession` : `dao.getBySessionId(sessionId)?.toDomain()`
  - [x] T5.4 Implémenter `observeAnalysisForSession` : `dao.observeBySessionId(sessionId).map { it?.toDomain() }`

- [x] **T6 — `AnalysisScheduler` interface + `AnalysisSchedulerImpl`** (AC: 1, 2)
  - [x] T6.1 Créer `android/domain/src/main/kotlin/com/secondserve/domain/analysis/AnalysisScheduler.kt` : `interface AnalysisScheduler { fun schedule(sessionId: Long) }`
  - [x] T6.2 Créer `android/data/src/main/kotlin/com/secondserve/data/worker/AnalysisSchedulerImpl.kt` : enqueue `PostMatchAnalysisWorker` avec `inputData = workDataOf(KEY_SESSION_ID to sessionId)`, `ExistingWorkPolicy.REPLACE`, contrainte `NetworkType.CONNECTED`, nom unique `"post_match_analysis_$sessionId"`
  - [x] T6.3 Ajouter dans `DataModule.kt` : `@Provides @Singleton fun provideAnalysisScheduler(@ApplicationContext context: Context): AnalysisScheduler = AnalysisSchedulerImpl(context)`

- [x] **T7 — `PostMatchAnalysisWorker`** (AC: 1, 2)
  - [x] T7.1 Ajouter `implementation(project(":core:ai"))` dans `android/data/build.gradle.kts`
  - [x] T7.2 Créer `android/data/src/main/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorker.kt` :
    - `@HiltWorker class PostMatchAnalysisWorker @AssistedInject constructor(@Assisted context, @Assisted params, sessionRepository, playerProfileRepository, workAxisRepository, coachingRepository, @VpsMistralEngine vpsMistralEngine)`
    - `doWork()` : récupère session, profil + axes via `playerProfileRepository.buildMatchContextProfile()`, points via `getPointSummaryForSession(sessionId)`, construit prompt, appelle `vpsMistralEngine.generate(prompt)`, sur `AppResult.Success` → `coachingRepository.saveAnalysis(sessionId, content)` → `Result.success()`, sur erreur → `Result.retry()`
    - Constante `companion object { const val KEY_SESSION_ID = "session_id" }`
  - [x] T7.3 Ajouter méthode `getPointSummaryForSession(sessionId: Long): Pair<Int, Int>` dans `SessionRepository` interface (retourne `selfPoints to opponentPoints`) + implémenter dans `SessionRepositoryImpl` avec `@Query("SELECT scorer, COUNT(*) as cnt FROM points WHERE session_id = :sessionId GROUP BY scorer")` dans un nouveau `getPointCountsByScorer` dans `SessionDao`

- [x] **T8 — Mise à jour `MatchViewModel`** (AC: 1, 2)
  - [x] T8.1 Injecter `AnalysisScheduler` dans le constructeur `MatchViewModel`
  - [x] T8.2 Dans `confirmClose()`, après `AppResult.Success` (après `syncScheduler.scheduleImmediate()`), appeler `analysisScheduler.schedule(sessionId)`
  - [x] T8.3 Mettre à jour `MatchViewModelTest` : ajouter `mock(AnalysisScheduler)` dans le constructeur

- [x] **T9 — Mise à jour `SessionDetailViewModel` + `SessionDetailUiState`** (AC: 1)
  - [x] T9.1 `SessionDetailUiState.Content` : ajouter `val analysis: CoachingAnalysis? = null`
  - [x] T9.2 `SessionDetailViewModel.load()` : appeler `coachingRepository.getAnalysisForSession(sessionId)` et inclure dans `Content`
  - [x] T9.3 Ajouter `import kotlinx.coroutines.flow.Flow` dans CoachingRepository si manquant

- [x] **T10 — Mise à jour `SessionDetailScreen`** (AC: 1)
  - [x] T10.1 Dans `SessionDetailContent`, après la section "Conseils coaching" (ou avant), si `analysis != null` : afficher une `Card` avec label "Analyse IA post-match" + le contenu de l'analyse
  - [x] T10.2 Si `analysis == null` : afficher un `Text("Analyse IA en cours de génération...")` grisé pour indiquer que c'est prévu

- [x] **T11 — Tests** (AC: 1, 2)
  - [x] T11.1 `android/data/src/test/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorkerTest.kt` : via `TestListenableWorkerBuilder` — succès (vérifie `coachingRepository.saveAnalysis` appelé), erreur VPS (vérifie `Result.retry()`), session introuvable (vérifie `Result.failure()`)
  - [x] T11.2 Ajouter tests dans `CoachingRepositoryImplTest` (ou créer si absent) : `saveAnalysis`, `getAnalysisForSession`, `observeAnalysisForSession`

## Dev Notes

### Architecture critique — Chaîne de dépendances

`:data` ne dépend PAS de `:core:ai` actuellement. **T7.1 est obligatoire** : ajouter `implementation(project(":core:ai"))` dans `android/data/build.gradle.kts`.

`:feature:match` dépend de `:domain` uniquement (pas `:data`). `MatchViewModel` ne peut injecter `WorkManager` directement. Le pattern `AnalysisScheduler` (interface `:domain`, impl `:data`) est identique à `SyncScheduler`/`SyncSchedulerImpl` — **suivre ce pattern exactement**.

`:feature:coaching` a un `build.gradle.kts` existant mais **aucun fichier .kt** — ne pas y créer de fichiers dans cette story (les use cases sont dans `:domain`, le worker dans `:data`).

### Patterns existants à suivre

**WorkManager Worker (`SyncWorker` comme modèle exact) :**
```kotlin
@HiltWorker
class PostMatchAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val workAxisRepository: WorkAxisRepository,
    private val coachingRepository: CoachingRepository,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : CoroutineWorker(context, params) {
    companion object { const val KEY_SESSION_ID = "session_id" }
    ...
}
```

**AnalysisSchedulerImpl (`SyncSchedulerImpl` comme modèle exact) :**
```kotlin
class AnalysisSchedulerImpl(private val context: Context) : AnalysisScheduler {
    override fun schedule(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<PostMatchAnalysisWorker>()
            .setInputData(workDataOf(PostMatchAnalysisWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("post_match_analysis_$sessionId", ExistingWorkPolicy.REPLACE, request)
    }
}
```

### @VpsMistralEngine qualifier

Déjà défini dans Story 5.1 : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt`.

`AiModule` (release) fournit déjà `@VpsMistralEngine VpsMistralEngine`. `PostMatchAnalysisWorker` l'injecte avec `@VpsMistralEngine InferenceEngine` — fonctionne via Hilt SingletonComponent.

### Conformité NFR-C3/FR-10 — Construction du prompt

Le prompt est construit dans `PostMatchAnalysisWorker.doWork()`. **PII à exclure absolument** : numéro de licence FFT (jamais transmis au VPS). Utiliser `MatchContextProfile` via `playerProfileRepository.buildMatchContextProfile()` — cette méthode respecte déjà NFR-C3.

Format de prompt recommandé (adapte en français pour Mistral) :
```
Tu es un coach tennis. Analyse ce match de façon concrète et personnalisée.

Match :
- Surface : {session.surface}
- Format : {session.format.matchFormat.name}
- Score : {session.scoreText ?: "inconnu"}
- Résultat : {session.result}
- Points : {selfPoints} gagnés / {opponentPoints} perdus

Profil joueur :
- Classement FFT : {profile.fftSeries ?: "non renseigné"}
- Style de jeu : {profile.playStyle ?: "non renseigné"}
- Axes de travail actifs : {profile.activeWorkAxes.joinToString(", ").ifEmpty { "aucun" }}
{if coachInstructions.isNotEmpty() : "- Instructions coaching : {coachInstructions.joinToString("; ")}"}

Fournis une analyse structurée : points forts observés dans ce match, points faibles, écart avec les axes de travail, et 1-2 recommandations concrètes. Cite le score et la surface. Sois précis, pas générique.
```

Paramètres Mistral : `max_tokens: 400` (augmenter vs 200 de l'analyze endpoint — l'analyse post-match est plus longue qu'un conseil in-match). **Attention** : le backend envoie déjà `max_tokens: 200` fixé dans `mistral_client.py`. Il faut soit :
- Passer `max_tokens` dans le prompt payload (non, l'API actuelle ne l'expose pas)
- **Créer un nouvel endpoint backend `POST /api/v1/coaching/analyze-extended`** avec `max_tokens: 400`
- **OU** modifier le worker pour passer le prompt directement en incluant une instruction de longueur

**Recommandation** : pour cette story, ajouter dans le prompt "Réponse en 4-6 phrases maximum." comme auto-limitation. Évite de modifier le backend. L'endpoint `/analyze` existant est suffisant.

### Points count dans le prompt

Ajouter dans `SessionDao` :
```kotlin
@Query("SELECT scorer, COUNT(*) as cnt FROM points WHERE session_id = :sessionId GROUP BY scorer")
suspend fun getPointCountsByScorer(sessionId: Long): List<ScorerCount>

@MapColumn(columnName = "scorer")  // ou data class ScorerCount(val scorer: String, val cnt: Int)
data class ScorerCount(val scorer: String, val cnt: Int)
```

Dans `SessionRepository` interface : `suspend fun getPointSummaryForSession(sessionId: Long): Pair<Int, Int>` (selfPoints, opponentPoints).
Dans `SessionRepositoryImpl` : query + mapper "SELF" → first, "OPPONENT" → second.

Si la table `points` est vide pour une session rétroactive (Story 4.3), retourner `Pair(0, 0)` — le prompt reste cohérent via le scoreText.

### Migration Room — Attention exportSchema

Le module `:data` a `arg("room.schemaLocation", "$projectDir/schemas")` dans `build.gradle.kts`. Lors du build, Room génère automatiquement le JSON de schéma v8 dans `android/data/schemas/`. **Ne pas modifier ces fichiers manuellement** — Room les génère.

### SessionDetailViewModel — chargement de l'analyse

`coachingRepository.getAnalysisForSession(sessionId)` est `suspend` → appeler dans le bloc `intent { }` existant. Si `null`, afficher l'indicateur "en cours" (voir T10.2). Ne pas bloquer le chargement si l'analyse est null (le worker peut ne pas avoir encore tourné).

### SessionDetailScreen — affichage de l'analyse

Insérer **avant** les "Conseils coaching" (cache in-match) ou dans une section distincte. L'analyse post-match est contextuelle à la session entière ; les conseils in-match sont des patterns par changement de côté. Visuellement distingués.

```kotlin
// Dans SessionDetailContent, après la Card session :
s.analysis?.let { analysis ->
    item {
        Spacer(Modifier.height(8.dp))
        Text("Analyse IA post-match", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(analysis.content, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Générée le ${sessionDetailDateFormat.format(Date(analysis.generatedAt))}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
} ?: item {
    Text(
        "Analyse IA en cours de génération...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
```

### MatchViewModelTest — mise à jour obligatoire

`MatchViewModel` a 7 paramètres actuellement. Après T8, il en aura 8. Vérifier `MatchViewModelTest.kt` et ajouter `mock<AnalysisScheduler>()` dans le constructeur. Ne pas oublier de mocker `.schedule(any())` pour les tests de `confirmClose()`.

### `AppResult` dans CoachingRepository

`AppResult` est dans `com.secondserve.domain.AppResult`. Import existant — pas de nouveau sealed class à créer.

### Pas de changements backend requis

`VpsMistralEngine.generate(prompt)` appelle déjà `POST /api/v1/coaching/analyze` (implémenté Story 5.1). La réponse texte est directement persistée en Room. **Aucune modification backend requise pour cette story.**

`GET /coaching/{session_id}` (mentionné dans l'architecture) est pour Story 5.3+ (récupération serveur).

### Project Structure Notes

**Fichiers NOUVEAUX :**
```
android/domain/src/main/kotlin/com/secondserve/domain/
  ├── model/CoachingAnalysis.kt                  (T1.1)
  └── analysis/AnalysisScheduler.kt              (T6.1)

android/data/src/main/kotlin/com/secondserve/data/
  ├── local/db/entity/CoachingAnalysisEntity.kt  (T3.1)
  ├── local/dao/CoachingAnalysisDao.kt           (T3.2)
  └── worker/
      ├── PostMatchAnalysisWorker.kt             (T7.2)
      └── AnalysisSchedulerImpl.kt              (T6.2)

android/data/src/test/kotlin/com/secondserve/data/worker/
  └── PostMatchAnalysisWorkerTest.kt             (T11.1)
```

**Fichiers MODIFIÉS :**
```
android/data/build.gradle.kts                    (T7.1) — ajouter :core:ai
android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt  (T2.1)
android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt   (T7.3)
android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt       (T4.x)
android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt               (T7.3)
android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt  (T5.x)
android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt   (T7.3)
android/app/src/main/kotlin/com/secondserve/di/DataModule.kt                            (T4.5, T6.3)
android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt   (T8.x)
android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt (T8.3)
android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt   (T9.1)
android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailViewModel.kt (T9.2)
android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt    (T10.x)
```

### References

- `VpsMistralEngine` et `@VpsMistralEngine` qualifier : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/vps/VpsMistralEngine.kt`, `android/core/ai/src/main/kotlin/com/secondserve/core/ai/di/InferenceEngineQualifiers.kt`
- `InferenceEngine` interface : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`
- `SyncWorker` (modèle exact pour PostMatchAnalysisWorker) : `android/data/src/main/kotlin/com/secondserve/data/worker/SyncWorker.kt`
- `SyncSchedulerImpl` (modèle exact pour AnalysisSchedulerImpl) : `android/data/src/main/kotlin/com/secondserve/data/worker/SyncSchedulerImpl.kt`
- `CoachingCacheEntity` (modèle pour CoachingAnalysisEntity) : `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingCacheEntity.kt`
- `SecondServeDatabase` (version courante 7, migrations 1-7) : `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `DataModule` (providers Room + SyncScheduler) : `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `MatchViewModel` (trigger clôture) : `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt`
- `SessionDetailScreen/ViewModel/UiState` : `android/feature/history/src/main/kotlin/com/secondserve/feature/history/`
- `PlayerProfileRepository.buildMatchContextProfile()` : `android/domain/src/main/kotlin/com/secondserve/domain/repository/PlayerProfileRepository.kt`
- `MatchContextProfile` : `android/domain/src/main/kotlin/com/secondserve/domain/model/MatchContextProfile.kt`
- Backend endpoint analyze (existant, Story 5.1) : `backend/app/api/v1/coaching.py`
- Epics — Story 5.2, FR-10, NFR-OFF3, NFR-C3 : `_bmad-output/planning-artifacts/epics.md#Story 5.2`
- Architecture — FR-10, CoachingUiState, GeneratePostMatchAnalysisUseCase, WorkManager : `_bmad-output/planning-artifacts/architecture.md`

### Review Findings

- [x] [Review][Decision] **D1 — One-shot load vs reactive observe dans SessionDetailViewModel** — Résolu : utilisation de `observeAnalysisForSession()` (Flow) dans `SessionDetailViewModel.load()`. [`SessionDetailViewModel.kt`]
- [x] [Review][Decision] **D2 — ExistingWorkPolicy.REPLACE vs KEEP pour PostMatchAnalysisWorker** — Résolu : `ExistingWorkPolicy.KEEP` appliqué. [`AnalysisSchedulerImpl.kt`]
- [x] [Review][Decision] **D3 — OnConflictStrategy.REPLACE vs IGNORE pour la persistance de l'analyse** — Résolu : `OnConflictStrategy.IGNORE` appliqué + gestion du cas `-1L` dans `saveAnalysis`. [`CoachingAnalysisDao.kt`, `CoachingRepositoryImpl.kt`]

- [x] [Review][Patch] **P1 — saveAnalysis() return value ignoré** — Résolu : vérification du `AppResult` retourné, `Result.retry()` sur erreur DB. [`PostMatchAnalysisWorker.kt`]
- [x] [Review][Patch] **P2 — buildMatchContextProfile() et getPointSummaryForSession() sans try-catch** — Résolu : try-catch individuels, `Result.failure()` sur exception. [`PostMatchAnalysisWorker.kt`]
- [x] [Review][Patch] **P3 — AppResult.Loading retourne Result.retry()** — Résolu : `AppResult.Loading` → `Result.failure()`. [`PostMatchAnalysisWorker.kt`]
- [x] [Review][Patch] **P4 — TestPostMatchAnalysisWorkerHelper duplique la logique** — Résolu : suppression du helper, extraction de `runWork(sessionId)`, tests sur le vrai worker. [`PostMatchAnalysisWorker.kt`, `PostMatchAnalysisWorkerTest.kt`]
- [x] [Review][Patch] **P5 — Aucun test vérifie que schedule() n'est PAS appelé quand confirmClose() échoue** — Résolu. [`MatchViewModelTest.kt`]
- [x] [Review][Patch] **P6 — SessionDetailViewModelTest sans test analysis != null** — Résolu : test `Content state includes analysis when present` ajouté. [`SessionDetailViewModelTest.kt`]
- [x] [Review][Patch] **P7 — PostMatchAnalysisWorkerTest sans vérification des champs du prompt** — Résolu : test `buildPrompt includes all required AC1 fields` ajouté avec `slot<String>()`. [`PostMatchAnalysisWorkerTest.kt`]
- [x] [Review][Patch] **P8 — ScorerCount défini dans SessionDao.kt** — Résolu : déplacé dans `ScorerCount.kt` dédié. [`SessionDao.kt`, `ScorerCount.kt`]

- [x] [Review][Defer] **Df1 — Aucun cap explicite sur le nombre de retries WorkManager** [`AnalysisSchedulerImpl.kt`] — deferred, pre-existing (WorkManager gère nativement via backoff exponentiel)
- [x] [Review][Defer] **Df2 — Injection de prompt via les champs session/profil non sanitisés** [`PostMatchAnalysisWorker.kt`] — deferred, pre-existing (app mono-utilisateur V1, risque minimal)
- [x] [Review][Defer] **Df3 — Valeurs scorer "SELF"/"OPPONENT" non vérifiées à l'écriture** [`SessionRepositoryImpl.kt`] — deferred, pre-existing (contrainte d'architecture existante des stories 2.x)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Fix `SessionRepositoryImplTest` : `withTransaction` en Room 2.7.1 est dans `RoomDatabaseKt__RoomDatabase_androidKt` (pas `RoomDatabaseKt`). Le `coAnswers` utilisait `firstArg` (le receveur) au lieu de `secondArg` (le lambda). Fix : `mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")` + `secondArg<suspend () -> Any?>().invoke()`.
- `SessionDetailViewModelTest` : après ajout de `getAnalysisForSession` dans `load()`, les tests existants ne mockaient pas ce call → MockKException. Fix : ajouter `coEvery { coachingRepository.getAnalysisForSession(10L) } returns null` dans les 2 tests concernés.
- `useJUnitPlatform()` manquant dans `:data` — ajouté dans `data/build.gradle.kts`, permet l'exécution des tests JUnit 5 (77 tests découverts vs 0 avant).

### Completion Notes List

- Tous les AC couverts. Worker `PostMatchAnalysisWorker` déclenché via `AnalysisScheduler.schedule()` après `confirmClose()`. Contrainte `NetworkType.CONNECTED` assure le déclenchement hors-ligne (AC 2).
- `workAxisRepository` non injecté directement dans le worker : les axes actifs sont déjà inclus dans `buildMatchContextProfile()` via `PlayerProfileRepository`.
- Suite de tests complète : 77 tests `:data` (+ 14 `:feature:match`, 3 `:feature:history`, 12 `:domain`), 0 échec.

### File List

**Nouveaux fichiers :**
- `android/domain/src/main/kotlin/com/secondserve/domain/model/CoachingAnalysis.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/analysis/AnalysisScheduler.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/CoachingAnalysisEntity.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingAnalysisDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorker.kt`
- `android/data/src/main/kotlin/com/secondserve/data/worker/AnalysisSchedulerImpl.kt`
- `android/data/src/test/kotlin/com/secondserve/data/worker/PostMatchAnalysisWorkerTest.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/CoachingRepositoryImplTest.kt`

**Fichiers modifiés :**
- `android/data/build.gradle.kts`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/CoachingRepository.kt`
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/SessionRepository.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
- `android/data/src/main/kotlin/com/secondserve/data/local/dao/SessionDao.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt`
- `android/app/src/main/kotlin/com/secondserve/di/DataModule.kt`
- `android/feature/match/src/main/kotlin/com/secondserve/feature/match/MatchViewModel.kt`
- `android/feature/match/src/test/kotlin/com/secondserve/feature/match/MatchViewModelTest.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailViewModel.kt`
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt`
- `android/feature/history/src/test/kotlin/com/secondserve/feature/history/SessionDetailViewModelTest.kt`
