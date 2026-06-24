---
baseline_commit: 4d97181
---

# Story TD-4 : Intégrité des Données & Résilience

Status: ready-for-dev

## Story

As a player,
I want session scores synced to the backend, invalid dates shown with a fallback, synthesis history bounded in size, and AI generation protected by a timeout,
So that my data is correct and the app doesn't freeze waiting for an unresponsive server.

## Context

**Problème 1 — `scoreText` absent de `SyncSessionDto`**
Le score d'un match (ex : "6-3 7-5") est stocké dans `SessionEntity.score_text` et dans le domain `Session.scoreText`, mais n'est **jamais inclus** dans `SyncSessionDto` lors de la synchronisation avec le VPS. Le backend ne reçoit donc jamais le score. Les analyses post-match de l'Epic 5 utilisent déjà `session.scoreText` côté VPS — si la colonne `score_text` n'est pas peuplée côté serveur, les analyses afficheront "inconnu".

**Problème 2 — `createdAt = 0L` affiche "01/01/1970"**
Si une session a `createdAt = 0` (données corrompues ou initialisées par défaut), `HistoryScreen` et `SessionDetailScreen` affichent "01/01/1970" sans message d'erreur. Un guard simple affiche un fallback lisible.

**Problème 3 — Table `coaching_syntheses` sans purge**
`CoachingSynthesisDao.insert()` utilise `OnConflictStrategy.REPLACE` sur un `id` AUTOINCREMENT, ce qui insère une nouvelle ligne à chaque synthèse (pas de remplacement). La table grossit sans borne. Ajouter une purge après insertion : conserver les N dernières synthèses (recommandé : 10).

**Problème 4 — Pas de `withTimeout` sur `vpsMistralEngine.generate()` dans `generateNow()`**
Si le VPS ne répond pas, le spinner `synthesisInProgress` reste actif indéfiniment. L'utilisateur ne peut ni annuler ni relancer. Un `withTimeout(30_000L)` permet de sortir avec une erreur claire.

Source : deferred items 4-1 (`scoreText`), 5-3 D2/D3, 5-3 D2 (`withTimeout`).

## Acceptance Criteria

1. **Given** une session est synchronisée avec le VPS
   **When** `SyncWorker` envoie `SyncPushRequest`
   **Then** le champ `score_text` est inclus dans `SyncSessionDto` si non null

2. **Given** une session a `createdAt = 0L` (données corrompues)
   **When** l'historique affiche cette session
   **Then** la date affiche "Date inconnue" au lieu de "01/01/1970"

3. **Given** `CoachingSynthesisDao.insert()` est appelé
   **When** plus de 10 synthèses existent en base
   **Then** les synthèses les plus anciennes sont supprimées pour maintenir au maximum 10 entrées

4. **Given** `generateNow()` est appelé et le VPS ne répond pas
   **When** 30 secondes s'écoulent sans réponse
   **Then** le spinner s'arrête et un message d'erreur "Délai dépassé — réessayez" est affiché

## Tasks / Subtasks

---

### BLOC A — `scoreText` dans `SyncSessionDto`

- [ ] **T1 — Ajouter `score_text` dans `SyncSessionDto`**
  - [ ] T1.1 Dans `android/data/src/main/kotlin/com/secondserve/data/remote/api/dto/SyncDto.kt`, ajouter dans `SyncSessionDto` :
    ```kotlin
    @Json(name = "score_text") val scoreText: String? = null
    ```

- [ ] **T2 — Mettre à jour le mapper `Session.toSyncDto()`**
  - [ ] T2.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/Mappers.kt`, localiser la fonction `Session.toSyncDto()` (ou `SessionEntity.toSyncDto()`) et ajouter :
    ```kotlin
    scoreText = scoreText,
    ```

- [ ] **T3 — Mettre à jour le backend pour accepter `score_text`**
  - [ ] T3.1 Dans `backend/app/features/sync/schemas.py`, localiser `SessionSyncRequest` (ou équivalent) et ajouter :
    ```python
    score_text: Optional[str] = None
    ```
  - [ ] T3.2 Dans `backend/app/features/sync/service.py` (ou `repository.py`), passer `score_text` lors de l'upsert de la session :
    ```python
    session.score_text = data.score_text
    ```
  - [ ] T3.3 Vérifier que la colonne `score_text` existe dans le modèle SQLAlchemy `Session` côté backend. Si absente, ajouter une migration Alembic :
    ```python
    # Dans une nouvelle migration alembic
    op.add_column('sessions', sa.Column('score_text', sa.String(), nullable=True))
    ```

---

### BLOC B — Guard `createdAt = 0L`

- [ ] **T4 — `HistoryScreen.kt` — fallback "Date inconnue"**
  - [ ] T4.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryScreen.kt`, modifier la fonction `Session.formattedDate()` :
    ```kotlin
    private fun Session.formattedDate(): String =
        if (createdAt <= 0L) "Date inconnue"
        else sessionDateFormat.format(Date(createdAt))
    ```

- [ ] **T5 — `SessionDetailScreen.kt` — fallback "Date inconnue"**
  - [ ] T5.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailScreen.kt`, ligne ~151 :
    ```kotlin
    // Avant
    DetailRow("Date", sessionDetailDateFormat.format(Date(session.createdAt)))
    // Après
    DetailRow(
        "Date",
        if (session.createdAt <= 0L) "Date inconnue"
        else sessionDetailDateFormat.format(Date(session.createdAt))
    )
    ```

---

### BLOC C — Purge des synthèses après insertion

- [ ] **T6 — Ajouter une requête de purge dans `CoachingSynthesisDao`**
  - [ ] T6.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/CoachingSynthesisDao.kt`, ajouter :
    ```kotlin
    @Query("""
        DELETE FROM coaching_syntheses
        WHERE id NOT IN (
            SELECT id FROM coaching_syntheses
            ORDER BY generated_at DESC
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldBeyond(keepCount: Int)
    ```

- [ ] **T7 — Appeler la purge après chaque insertion dans `CoachingRepositoryImpl`**
  - [ ] T7.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`, localiser `saveSynthesis()` et ajouter après le `dao.insert()` :
    ```kotlin
    synthesisDao.deleteOldBeyond(keepCount = 10)
    ```
  - [ ] T7.2 Vérifier que cet appel est dans une transaction ou accepter le comportement non-transactionnel (purge peut rater sans affecter l'insert)

---

### BLOC D — `withTimeout` dans `generateNow()`

- [ ] **T8 — Ajouter `withTimeout` dans `CoachingViewModel.generateNow()`**
  - [ ] T8.1 Dans `android/feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/CoachingViewModel.kt`, ajouter l'import :
    ```kotlin
    import kotlinx.coroutines.TimeoutCancellationException
    import kotlinx.coroutines.withTimeout
    ```
  - [ ] T8.2 Entourer l'appel `vpsMistralEngine.generate(prompt)` avec `withTimeout` :
    ```kotlin
    val result = try {
        withTimeout(30_000L) {
            vpsMistralEngine.generate(prompt)
        }
    } catch (e: TimeoutCancellationException) {
        reduce { state.copy(error = "Délai dépassé — réessayez") }
        return@intent
    }
    when (result) {
        is AppResult.Success -> { ... }
        is AppResult.Error -> reduce { state.copy(error = "Génération échouée — vérifiez la connexion") }
        AppResult.Loading -> Unit
    }
    ```

---

### BLOC E — Compilation & validation

- [ ] **T9 — Compiler le projet Android**
  - [ ] T9.1 `./gradlew :data:compileDebugKotlin :feature:coaching:compileDebugKotlin :feature:history:compileDebugKotlin`

- [ ] **T10 — Lancer les tests unitaires**
  - [ ] T10.1 `./gradlew testDebugUnitTest` — tous les tests doivent passer

## Dev Notes

- Pour T3.3 (backend `score_text`) : vérifier d'abord si la colonne existe déjà dans `backend/app/features/sessions/models.py` avant de créer une migration Alembic
- Pour T7 (purge synthèses) : le `keepCount = 10` est arbitraire — ajustable selon les besoins. La valeur 10 représente ~3-4 mois de synthèse hebdomadaire
- `withTimeout(30_000L)` correspond au timeout recommandé pour une requête Mistral. La valeur peut être ajustée selon les benchmarks observés sur le VPS
- `TimeoutCancellationException` est une sous-classe de `CancellationException` — elle NE doit PAS être catchée par les blocs génériques `catch (e: Exception)` après la correction de TD-1

## Deferred items adressés

- `4-1` — `scoreText` absent du `SyncSessionDto`
- `4-1` — `createdAt = 0L` affiche "01/01/1970"
- `5-3 D3` — Table `coaching_syntheses` sans politique de purge
- `5-3 D2` — Aucun timeout sur `vpsMistralEngine.generate()` dans `generateNow()`
