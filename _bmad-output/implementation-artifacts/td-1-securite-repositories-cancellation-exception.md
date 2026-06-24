---
baseline_commit: 4d97181
---

# Story TD-1 : Sécurité des Repositories — CancellationException & race condition profil

Status: done

## Story

As a developer,
I want repositories to rethrow CancellationException and protect profile write operations with a Mutex,
So that coroutine cancellations propagate correctly and concurrent profile saves don't silently overwrite each other.

## Context

**Problème 1 — CancellationException swallowée (systémique)**
Tous les `catch (e: Exception)` dans les repositories capturent aussi `CancellationException`, qui est le signal de Kotlin pour annuler les coroutines. En avalant cette exception, la coroutine ne se termine pas, ce qui peut bloquer le scope parent indéfiniment (notamment sur l'annulation ViewModel au départ d'écran).

Fichiers concernés :
- `SessionRepositoryImpl.kt` — 4 occurrences
- `PlayerProfileRepositoryImpl.kt` — 4 occurrences
- `WorkAxisRepositoryImpl.kt` — 8 occurrences
- `CoachingRepositoryImpl.kt` — à vérifier
- `NotificationRepositoryImpl.kt` — à vérifier

**Problème 2 — Race read-modify-write dans `saveRanking()` et `saveProfileDetails()`**
Les deux méthodes font `dao.getProfile()` puis `dao.upsertProfile()` sans transaction atomique. Si elles sont appelées concurrentiellement (theorie : NetworkCallback + action utilisateur simultanés), la deuxième écriture peut écraser la mise à jour de la première. Solution : `Mutex` au niveau repository.

Source : deferred items 1-5 p2, 1-6 p2.

## Acceptance Criteria

1. **Given** une coroutine qui utilise un repository est annulée (ex: ViewModel cleared)
   **When** l'exception `CancellationException` remonte dans un `catch (e: Exception)`
   **Then** elle est rethrowée immédiatement, sans être wrappée dans `AppResult.Error`

2. **Given** `saveRanking()` et `saveProfileDetails()` sont appelées quasi-simultanément
   **When** les deux tentent un `getProfile()` → `upsertProfile()`
   **Then** les opérations sont sérialisées par un `Mutex`, sans perte de données

3. **Given** les modifications sont appliquées dans tous les repositories
   **When** les tests unitaires existants sont exécutés
   **Then** tous les tests passent sans modification

## Tasks / Subtasks

---

### BLOC A — Fix CancellationException dans tous les repositories

- [x] **T1 — `SessionRepositoryImpl.kt`** — Rethrow CancellationException
  - [x] T1.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [x] T1.2 Appliquer sur les 4 occurrences de `catch (e: Exception)` dans ce fichier

- [x] **T2 — `PlayerProfileRepositoryImpl.kt`** — Rethrow CancellationException
  - [x] T2.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [x] T2.2 Appliquer sur les 4+ occurrences (inclure les blocs imbriqués dans `saveRanking()` et `saveProfileDetails()`)

- [x] **T3 — `WorkAxisRepositoryImpl.kt`** — Rethrow CancellationException
  - [x] T3.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [x] T3.2 Appliquer sur les ~8 occurrences dans ce fichier
  - [x] T3.3 Note : le `catch` inline dans `hasPendingSuggestions()` (`catchAll → false`) est traité séparément — ne pas oublier celui-là :
    ```kotlin
    // Avant
    try { suggestionDao.countPending() > 0 } catch (e: Exception) { false }
    // Après
    try { suggestionDao.countPending() > 0 } catch (e: Exception) { if (e is CancellationException) throw e; false }
    ```

- [x] **T4 — `CoachingRepositoryImpl.kt`** — Rethrow CancellationException
  - [x] T4.1 Lire le fichier `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
  - [x] T4.2 Identifier tous les blocs `catch (e: Exception)` et ajouter le rethrow

- [x] **T5 — `NotificationRepositoryImpl.kt`** — Rethrow CancellationException
  - [x] T5.1 Lire le fichier `android/data/src/main/kotlin/com/secondserve/data/repository/NotificationRepositoryImpl.kt`
  - [x] T5.2 Identifier tous les blocs `catch (e: Exception)` et ajouter le rethrow

---

### BLOC B — Mutex pour saveRanking() / saveProfileDetails()

- [x] **T6 — Ajouter un Mutex dans `PlayerProfileRepositoryImpl`**
  - [x] T6.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`, ajouter l'import et la propriété :
    ```kotlin
    import kotlinx.coroutines.sync.Mutex
    import kotlinx.coroutines.sync.withLock
    
    // Dans la classe PlayerProfileRepositoryImpl :
    private val profileWriteMutex = Mutex()
    ```
  - [x] T6.2 Entourer le bloc read-modify-write dans `saveRanking()` avec le mutex
  - [x] T6.3 Entourer le bloc read-modify-write dans `saveProfileDetails()` avec le même mutex

---

### BLOC C — Vérification & Tests

- [x] **T7 — Lancer les tests unitaires data layer**
  - [x] T7.1 `./gradlew :data:testDebugUnitTest` — tous les tests passent
  - [x] T7.2 Aucun test existant n'a été cassé par les modifications

## Dev Notes

- L'import nécessaire pour le rethrow : `import kotlinx.coroutines.CancellationException` (si pas déjà présent)
- Alternative plus idiomatique Kotlin pour les blocs courts :
  ```kotlin
  } catch (e: Exception) {
      if (e is CancellationException) throw e
      Timber.e(e, "...")
      AppResult.Error(e)
  }
  ```
- Pour le Mutex : `kotlinx-coroutines-core` est déjà une dépendance transitive — pas de nouveau import Gradle nécessaire
- Ne pas modifier la logique métier des blocs catch — uniquement ajouter le rethrow en première ligne

## Deferred items adressés

- `1-6 p2` — `CancellationException` swallowée dans les repositories
- `1-5 p2` — Race read-modify-write dans `saveRanking()`/`saveProfileDetails()`

## Dev Agent Record

### Implementation Notes

**BLOC A — CancellationException rethrow (T1–T5)**
- `SessionRepositoryImpl.kt` : 4 blocs `catch (e: Exception)` corrigés (createSession, createCompletedSession, deleteSession, closeSession)
- `PlayerProfileRepositoryImpl.kt` : 5 blocs corrigés (getProfile, saveRanking inner VPS, saveRanking outer, saveProfileDetails inner VPS, saveProfileDetails outer)
- `WorkAxisRepositoryImpl.kt` : 13 blocs corrigés (createWorkAxis ×2, updateWorkAxis ×2, deleteWorkAxis ×2, hasPendingSuggestions inline, generateAndSaveSuggestions ×3, acceptSuggestion, ignoreSuggestion, hasCoachingData)
- `CoachingRepositoryImpl.kt` : 2 blocs corrigés (saveAnalysis, saveSynthesis)
- `NotificationRepositoryImpl.kt` : aucun bloc `catch (e: Exception)` — pas de modification

**BLOC B — Mutex (T6)**
- Ajout de `private val profileWriteMutex = Mutex()` dans `PlayerProfileRepositoryImpl`
- `saveRanking` : le bloc `getProfile()` + `saveProfileAndHistory()` est entouré d'un `profileWriteMutex.withLock { }` ; l'appel VPS reste en dehors du verrou
- `saveProfileDetails` : le bloc `getProfile()` + `upsertProfile()` est entouré du même mutex ; l'appel VPS reste en dehors du verrou

**Tests ajoutés (TDD red-green)**
- 12 tests CancellationException (4 Session + 3 PlayerProfile + 4 WorkAxis + 2 Coaching)
- 1 test mutex pour `saveProfileDetails reads updated profile written by saveRanking`
- Tous les 65 tests existants + 14 nouveaux passent sans régression

### Completion Notes

Story TD-1 complète. Tous les AC satisfaits :
- AC1 : CancellationException propagée dans tous les repositories
- AC2 : saveRanking/saveProfileDetails sérialisées par Mutex
- AC3 : 0 régression sur la suite existante

## File List

- `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`
- `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/SessionRepositoryImplTest.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImplTest.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImplTest.kt`
- `android/data/src/test/kotlin/com/secondserve/data/repository/CoachingRepositoryImplTest.kt`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Review Findings

#### Patch
- [x] [Review][Patch] Tests CE manquants dans WorkAxisRepositoryImpl : acceptSuggestion outer catch, generateAndSaveSuggestions (×3 catch sites), hasCoachingData, hasPendingSuggestions inline — 6 catch blocks patché mais non couverts [WorkAxisRepositoryImpl.kt + WorkAxisRepositoryImplTest.kt]
- [x] [Review][Patch] Tests CE manquants dans PlayerProfileRepositoryImpl : VPS inner catch de saveRanking, VPS inner catch de saveProfileDetails, dao.saveProfileAndHistory à l'intérieur du withLock, dao.upsertProfile à l'intérieur du withLock — 4 paths non couverts [PlayerProfileRepositoryImpl.kt + PlayerProfileRepositoryImplTest.kt]
- [x] [Review][Patch] Vérifier que PlayerProfileRepositoryImpl est bien `@Singleton` dans le graphe Hilt — confirmé @Singleton dans DataModule.kt, dismiss [PlayerProfileRepositoryImpl.kt]

#### Defer
- [x] [Review][Defer] Mutex test séquentiel — `saveProfileDetails reads updated profile written by saveRanking via Mutex` ne teste pas la concurrence réelle [PlayerProfileRepositoryImplTest.kt] — deferred, complexité test concurrents
- [x] [Review][Defer] `buildMatchContextProfile()` propage les exceptions DAO sans try/catch — incohérence avec le pattern AppResult du reste de la classe [PlayerProfileRepositoryImpl.kt] — deferred, pré-existant
- [x] [Review][Defer] `saveAnalysis` branche IGNORE : retourne `entity` avec `id=0` si `getBySessionId` renvoie null après un conflit [CoachingRepositoryImpl.kt] — deferred, pré-existant
- [x] [Review][Defer] `deleteSession` non-atomique : notification annulée même si `syncQueueDao.insert` échoue [SessionRepositoryImpl.kt] — deferred, pré-existant
- [x] [Review][Defer] `acceptSuggestion` — race TOCTOU sur `MAX_WORK_AXES` sans Mutex partagé avec `createWorkAxis` [WorkAxisRepositoryImpl.kt] — deferred, pré-existant
- [x] [Review][Defer] `CoachingRepositoryImpl` — `getCachedAdvice`, `saveAdvice`, `markMatchEntriesStale` etc. sans try/catch, incohérents avec le pattern AppResult [CoachingRepositoryImpl.kt] — deferred, pré-existant
- [x] [Review][Defer] `generateAndSaveSuggestions` — `getAllTitles` retourne `emptyList()` sur erreur DAO non-CE, suggestions générées sans contexte des axes existants [WorkAxisRepositoryImpl.kt] — deferred, pré-existant

## Change Log

- Ajout du rethrow `CancellationException` dans tous les `catch (e: Exception)` des 4 repositories (2026-06-24)
- Ajout d'un `Mutex` dans `PlayerProfileRepositoryImpl` pour sérialiser `saveRanking`/`saveProfileDetails` (2026-06-24)
- 14 nouveaux tests unitaires (TDD) — 0 régression (2026-06-24)
