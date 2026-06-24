---
baseline_commit: 4d97181
---

# Story TD-1 : Sécurité des Repositories — CancellationException & race condition profil

Status: ready-for-dev

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

- [ ] **T1 — `SessionRepositoryImpl.kt`** — Rethrow CancellationException
  - [ ] T1.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/SessionRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [ ] T1.2 Appliquer sur les 4 occurrences de `catch (e: Exception)` dans ce fichier

- [ ] **T2 — `PlayerProfileRepositoryImpl.kt`** — Rethrow CancellationException
  - [ ] T2.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [ ] T2.2 Appliquer sur les 4+ occurrences (inclure les blocs imbriqués dans `saveRanking()` et `saveProfileDetails()`)

- [ ] **T3 — `WorkAxisRepositoryImpl.kt`** — Rethrow CancellationException
  - [ ] T3.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`, pour chaque bloc `catch (e: Exception)`, ajouter en première instruction :
    ```kotlin
    if (e is CancellationException) throw e
    ```
  - [ ] T3.2 Appliquer sur les ~8 occurrences dans ce fichier
  - [ ] T3.3 Note : le `catch` inline dans `hasPendingSuggestions()` (`catchAll → false`) est traité séparément — ne pas oublier celui-là :
    ```kotlin
    // Avant
    try { suggestionDao.countPending() > 0 } catch (e: Exception) { false }
    // Après
    try { suggestionDao.countPending() > 0 } catch (e: Exception) { if (e is CancellationException) throw e; false }
    ```

- [ ] **T4 — `CoachingRepositoryImpl.kt`** — Rethrow CancellationException
  - [ ] T4.1 Lire le fichier `android/data/src/main/kotlin/com/secondserve/data/repository/CoachingRepositoryImpl.kt`
  - [ ] T4.2 Identifier tous les blocs `catch (e: Exception)` et ajouter le rethrow

- [ ] **T5 — `NotificationRepositoryImpl.kt`** — Rethrow CancellationException
  - [ ] T5.1 Lire le fichier `android/data/src/main/kotlin/com/secondserve/data/repository/NotificationRepositoryImpl.kt`
  - [ ] T5.2 Identifier tous les blocs `catch (e: Exception)` et ajouter le rethrow

---

### BLOC B — Mutex pour saveRanking() / saveProfileDetails()

- [ ] **T6 — Ajouter un Mutex dans `PlayerProfileRepositoryImpl`**
  - [ ] T6.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`, ajouter l'import et la propriété :
    ```kotlin
    import kotlinx.coroutines.sync.Mutex
    import kotlinx.coroutines.sync.withLock
    
    // Dans la classe PlayerProfileRepositoryImpl :
    private val profileWriteMutex = Mutex()
    ```
  - [ ] T6.2 Entourer le bloc read-modify-write dans `saveRanking()` avec le mutex :
    ```kotlin
    override suspend fun saveRanking(series: String, points: Int): AppResult<Unit> = try {
        val result = profileWriteMutex.withLock {
            val now = System.currentTimeMillis()
            val current = dao.getProfile()
            dao.upsertProfile(
                PlayerProfileEntity(
                    id = 1,
                    currentSeries = series,
                    currentPoints = points,
                    playStyle = current?.playStyle,
                    preferredSurfaces = current?.preferredSurfaces,
                    coachInstruction1 = current?.coachInstruction1,
                    coachInstruction2 = current?.coachInstruction2,
                    coachInstruction3 = current?.coachInstruction3,
                    updatedAt = now
                )
            )
        }
        // ... suite (appel VPS) inchangée
    ```
  - [ ] T6.3 Entourer le bloc read-modify-write dans `saveProfileDetails()` avec le même mutex :
    ```kotlin
    override suspend fun saveProfileDetails(...): AppResult<Unit> = try {
        profileWriteMutex.withLock {
            val now = System.currentTimeMillis()
            val current = dao.getProfile()
            dao.upsertProfile(
                PlayerProfileEntity(
                    id = 1,
                    currentSeries = current?.currentSeries,
                    currentPoints = current?.currentPoints,
                    playStyle = playStyle,
                    // ... reste des champs
                )
            )
        }
        // ... suite (appel VPS) inchangée
    ```

---

### BLOC C — Vérification & Tests

- [ ] **T7 — Lancer les tests unitaires data layer**
  - [ ] T7.1 `./gradlew :data:testDebugUnitTest` — tous les tests doivent passer
  - [ ] T7.2 Si des tests échouent à cause du rethrow CancellationException, vérifier que ces tests simulent des annulations de coroutines (et adapter le mock si nécessaire)

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
