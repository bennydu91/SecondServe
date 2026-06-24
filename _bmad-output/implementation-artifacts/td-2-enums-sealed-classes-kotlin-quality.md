---
baseline_commit: 4d97181
---

# Story TD-2 : Enums & Sealed Classes — Qualité Kotlin

Status: done

## Story

As a developer,
I want status literals replaced by enums and `object` singletons replaced by `data object`,
So that the compiler enforces exhaustive pattern matching and code intent is unambiguous.

## Context

**Problème 1 — `AxisSuggestion.status` raw String**
Les valeurs `"PENDING"`, `"ACCEPTED"`, `"IGNORED"` sont des string literals dispersées dans `AxisSuggestion.kt`, `AxisSuggestionEntity.kt`, `AxisSuggestionDao.kt`, et `WorkAxisRepositoryImpl.kt`. Aucun enum ne garantit la cohérence.

**Problème 2 — Frequency strings magiques dans les notifications**
`"DAILY"`, `"EVERY_2_DAYS"`, `"WEEKLY"`, `"DISABLED"` sont dupliqués en literals dans `PlayerDataStore.kt`, `NotificationRepositoryImpl.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`. Même pattern risqué.

**Problème 3 — `object` au lieu de `data object` dans les sealed classes**
Kotlin 1.9+ recommande `data object` pour les singletons dans les sealed classes : `toString()` retourne le nom de la classe, `equals()` et `hashCode()` sont corrects, facilitant les assertions dans les tests. Fichiers concernés : `HistoryUiState.kt`, `SessionDetailUiState.kt`, `StatsUiState.kt`.

**Problème 4 — `else -> false` supprime l'exhaustivité Kotlin**
Dans `ScoreViewModel.kt`, le `when` de détection changeover utilise `else -> false`, ce qui absorbe silencieusement tout nouveau `EngineEvent` futur sans erreur de compilation. La spec prescrit `private fun EngineEvent.isChangeover(): Boolean`.

Source : deferred items 5-4 W7, 6-1 W5, 4-1, 2-5.

## Acceptance Criteria

1. **Given** un développeur lit `AxisSuggestion.status`
   **When** il assigne ou compare la valeur
   **Then** il utilise `AxisSuggestionStatus.PENDING` (enum) et le compilateur garantit l'exhaustivité

2. **Given** un développeur lit `NotificationFrequency`
   **When** il traite la fréquence
   **Then** il utilise `NotificationFrequency.DAILY` (enum) et toute valeur inconnue est une erreur de compilation

3. **Given** un sealed class singleton (ex: `Loading`) est utilisé dans un test
   **When** on l'affiche via `toString()` ou on l'assert avec `assertEquals`
   **Then** le résultat est prévisible (`"Loading"` et `true`)

4. **Given** un nouveau `EngineEvent` est ajouté dans l'engine
   **When** `isChangeover()` ne gère pas ce nouveau cas
   **Then** le compilateur émet une erreur `'when' expression must be exhaustive`

## Tasks / Subtasks

---

### BLOC A — `AxisSuggestionStatus` enum dans le domain

- [x] **T1 — Créer `AxisSuggestionStatus` dans le domain**
  - [x] T1.1 Créer le fichier `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestionStatus.kt` :
    ```kotlin
    package com.secondserve.domain.model
    
    enum class AxisSuggestionStatus {
        PENDING, ACCEPTED, IGNORED
    }
    ```

- [x] **T2 — Mettre à jour `AxisSuggestion` domain model**
  - [x] T2.1 Dans `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestion.kt`, changer :
    ```kotlin
    // Avant
    val status: String = "PENDING"
    // Après
    val status: AxisSuggestionStatus = AxisSuggestionStatus.PENDING
    ```

- [x] **T3 — Mettre à jour `AxisSuggestionEntity`**
  - [x] T3.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/AxisSuggestionEntity.kt` :
    - Ajouter un `TypeConverter` Room pour `AxisSuggestionStatus` → String, ou utiliser une `@ColumnInfo` avec le nom de l'enum comme string :
    ```kotlin
    // Option recommandée : TypeConverter dans SecondServeDatabase
    // Dans AxisSuggestionEntity, changer le type en String pour la DB et convertir dans le mapper
    @ColumnInfo(name = "status") val status: String = "PENDING"
    ```
    - Dans le mapper `toDomain()` :
    ```kotlin
    status = AxisSuggestionStatus.valueOf(status)
    ```
    - Dans le mapper `toEntity()` :
    ```kotlin
    status = status.name
    ```

- [x] **T4 — Mettre à jour `AxisSuggestionDao`**
  - [x] T4.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/AxisSuggestionDao.kt`, les queries SQL restent avec des string literals (Room query SQL) — pas de changement nécessaire :
    ```kotlin
    @Query("SELECT * FROM axis_suggestions WHERE status = 'PENDING' ORDER BY generated_at DESC")
    ```

- [x] **T5 — Mettre à jour `WorkAxisRepositoryImpl`**
  - [x] T5.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`, remplacer les usages de status String par `AxisSuggestionStatus` :
    - Chercher `status = "ACCEPTED"`, `status = "IGNORED"`, `status = "PENDING"` dans ce fichier
    - Remplacer par `status = AxisSuggestionStatus.ACCEPTED.name` (pour les entités) ou `status = AxisSuggestionStatus.ACCEPTED` (pour le domain)

---

### BLOC B — `NotificationFrequency` enum dans le domain

- [x] **T6 — Créer `NotificationFrequency` dans le domain**
  - [x] T6.1 Créer le fichier `android/domain/src/main/kotlin/com/secondserve/domain/notification/NotificationFrequency.kt` :
    ```kotlin
    package com.secondserve.domain.notification
    
    enum class NotificationFrequency {
        DAILY, EVERY_2_DAYS, WEEKLY, DISABLED
    }
    ```

- [x] **T7 — Mettre à jour `PlayerDataStore`**
  - [x] T7.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/PlayerDataStore.kt`, changer :
    ```kotlin
    // Avant
    prefs.getString(KEY_NOTIF_FREQUENCY, "DAILY") ?: "DAILY"
    // Après : stocker le name() de l'enum en SharedPreferences
    NotificationFrequency.valueOf(prefs.getString(KEY_NOTIF_FREQUENCY, "DAILY") ?: "DAILY")
    ```
  - [x] T7.2 Adapter les fonctions getFrequency() / saveFrequency() pour travailler avec `NotificationFrequency`

- [x] **T8 — Mettre à jour `NotificationRepositoryImpl`**
  - [x] T8.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/NotificationRepositoryImpl.kt`, remplacer le `when` sur String par un `when` sur `NotificationFrequency` :
    ```kotlin
    // Avant
    when (frequency) {
        "DAILY" -> notificationScheduler.scheduleDaily()
        "EVERY_2_DAYS" -> notificationScheduler.scheduleEvery2Days()
        "WEEKLY" -> notificationScheduler.scheduleWeekly()
        "DISABLED" -> notificationScheduler.cancel()
    }
    // Après
    when (frequency) {
        NotificationFrequency.DAILY -> notificationScheduler.scheduleDaily()
        NotificationFrequency.EVERY_2_DAYS -> notificationScheduler.scheduleEvery2Days()
        NotificationFrequency.WEEKLY -> notificationScheduler.scheduleWeekly()
        NotificationFrequency.DISABLED -> notificationScheduler.cancel()
    }
    ```

- [x] **T9 — Mettre à jour `SettingsViewModel`**
  - [x] T9.1 Dans `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsViewModel.kt`, changer le type de `frequency` dans l'état :
    ```kotlin
    // Avant
    val frequency: String = "DAILY"
    // Après
    val frequency: NotificationFrequency = NotificationFrequency.DAILY
    ```

- [x] **T10 — Mettre à jour `SettingsScreen`**
  - [x] T10.1 Dans `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsScreen.kt`, adapter la map des labels :
    ```kotlin
    // Avant
    val frequencyOptions = mapOf(
        "DAILY" to "Quotidien",
        "EVERY_2_DAYS" to "Tous les 2 jours",
        "WEEKLY" to "Hebdomadaire",
        "DISABLED" to "Désactivé"
    )
    // Après
    val frequencyOptions = mapOf(
        NotificationFrequency.DAILY to "Quotidien",
        NotificationFrequency.EVERY_2_DAYS to "Tous les 2 jours",
        NotificationFrequency.WEEKLY to "Hebdomadaire",
        NotificationFrequency.DISABLED to "Désactivé"
    )
    ```

---

### BLOC C — `data object` dans les sealed classes

- [x] **T11 — `HistoryUiState.kt`**
  - [x] T11.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : HistoryUiState()
    // Après
    data object Loading : HistoryUiState()
    ```

- [x] **T12 — `SessionDetailUiState.kt`**
  - [x] T12.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : SessionDetailUiState()
    // Après
    data object Loading : SessionDetailUiState()
    ```

- [x] **T13 — `StatsUiState.kt`**
  - [x] T13.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : StatsUiState()
    // Après
    data object Loading : StatsUiState()
    ```

- [x] **T14 — Vérifier les autres sealed class**
  - [x] T14.1 Rechercher `^    object ` dans tous les fichiers `*UiState.kt` et `*SideEffect.kt` du projet
  - [x] T14.2 Appliquer `data object` à chaque occurrence trouvée dans une sealed class

---

### BLOC D — `isChangeover()` en extension function + when exhaustif

- [x] **T15 — Extraire `isChangeover()` et supprimer `else -> false`**
  - [x] T15.1 Dans `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt`, localiser le bloc `val changeover = when(event) { ... else -> false }`
  - [x] T15.2 Remplacer par une extension function privée :
    ```kotlin
    private fun EngineEvent.isChangeover(): Boolean = when (this) {
        is EngineEvent.SetWon -> this.changeover
        is EngineEvent.MatchOver -> false
        // Ajouter tous les autres sous-types de EngineEvent explicitement
        // Le compilateur signalera si un cas est manquant
        else -> false  // À supprimer une fois tous les cas couverts
    }
    ```
  - [x] T15.3 Lire `EngineEvent` sealed class pour lister tous les sous-types et les couvrir explicitement dans le `when`, puis supprimer le `else`
  - [x] T15.4 Utiliser `event.isChangeover()` dans le code appelant

---

### BLOC E — Compilation & tests

- [x] **T16 — Vérifier la compilation**
  - [x] T16.1 `./gradlew :domain:compileDebugKotlin :data:compileDebugKotlin :feature:profile:compileDebugKotlin :wear:compileDebugKotlin`
  - [x] T16.2 Corriger toutes les erreurs de compilation (les usages non migrés des anciens String literals)

- [x] **T17 — Lancer les tests unitaires**
  - [x] T17.1 `./gradlew testDebugUnitTest` — tous les tests doivent passer

## Dev Notes

- Les types `AxisSuggestionStatus` et `NotificationFrequency` sont dans `:domain` — tous les modules peuvent les importer
- `data object` requiert Kotlin 1.9+ — vérifier `libs.versions.toml` que la version Kotlin est ≥ 1.9 avant de modifier
- Pour `AxisSuggestionEntity` : Room ne supporte pas nativement les enums sans TypeConverter. La solution la plus simple est de garder le stockage en `String` dans l'entité et de convertir dans les mappers `toDomain()`/`toEntity()` — pas de migration DB nécessaire (les valeurs stockées sont déjà les mêmes strings)
- Pour `NotificationFrequency` : les valeurs en SharedPreferences sont déjà des strings `"DAILY"`, `"EVERY_2_DAYS"`, etc. — `NotificationFrequency.valueOf()` les lit correctement

## Deferred items adressés

- `5-4 W7` — `AxisSuggestion.status` raw String → enum
- `6-1 W5` — Frequency strings magiques → enum
- `4-1` — `object` → `data object` dans sealed classes
- `2-5` — `else -> false` supprime l'exhaustivité Kotlin + `isChangeover()` extension function

## Dev Agent Record

### Implementation Plan

**BLOC A** : Création de `AxisSuggestionStatus` dans `:domain`. `AxisSuggestion.status` passe de `String` à `AxisSuggestionStatus`. `AxisSuggestionEntity` conserve `status: String` pour Room (pas de migration DB), avec conversion dans `toDomain()`/`toEntity()`. `WorkAxisRepositoryImpl` utilise `.name` pour les appels DAO. La `private toDomain()` locale dans `WorkAxisRepositoryImpl` également corrigée.

**BLOC B** : Création de `NotificationFrequency` dans `:domain:notification`. `PlayerDataStore` expose directement `NotificationFrequency`. `NotificationRepository` interface mise à jour. `NotificationRepositoryImpl.setFrequency()` utilise un `when` exhaustif sans `else`. `SettingsViewModel` et `SettingsScreen` migrent vers l'enum typé.

**BLOC C** : 4 singletons convertis en `data object` : `HistoryUiState.Loading`, `SessionDetailUiState.Loading`, `StatsUiState.Loading`, `AddRetroSessionSideEffect.SessionCreated`. Kotlin 2.1.0 confirmé (≥ 1.9).

**BLOC D** : Extension function privée `EngineEvent.isChangeover()` couvrant exhaustivement les 4 sous-types (`PointScored`, `GameWon`, `SetWon`, `MatchOver`) sans `else`.

### Completion Notes

Tous les 4 blocs implémentés. `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, 222 tests passent sans régression. Compilation `:domain`, `:data`, `:feature:profile`, `:wear` OK.

## File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestionStatus.kt` (créé)
- `android/domain/src/main/kotlin/com/secondserve/domain/notification/NotificationFrequency.kt` (créé)
- `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestion.kt` (modifié)
- `android/domain/src/main/kotlin/com/secondserve/domain/repository/NotificationRepository.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/AxisSuggestionEntity.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/local/PlayerDataStore.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt` (modifié)
- `android/data/src/main/kotlin/com/secondserve/data/repository/NotificationRepositoryImpl.kt` (modifié)
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsViewModel.kt` (modifié)
- `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsScreen.kt` (modifié)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryUiState.kt` (modifié)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt` (modifié)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsUiState.kt` (modifié)
- `android/feature/history/src/main/kotlin/com/secondserve/feature/history/AddRetroSessionUiState.kt` (modifié)
- `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt` (modifié)

## Change Log

- 2026-06-24 : TD-2 implémenté — 2 enums créés (`AxisSuggestionStatus`, `NotificationFrequency`), 4 singletons convertis en `data object`, `isChangeover()` extension function exhaustive sans `else`, 15 fichiers modifiés, 222 tests verts.

### Review Findings

- [x] [Review][Patch] `NotificationFrequency.valueOf()` crash sur string inconnue en SharedPreferences [`PlayerDataStore.kt:43`]
- [x] [Review][Patch] `AxisSuggestionStatus.valueOf()` crash sur ligne DB corrompue [`AxisSuggestionEntity.kt:19`, `WorkAxisRepositoryImpl.kt:180`]
- [x] [Review][Patch] Duplicate `toDomain()` privée dans `WorkAxisRepositoryImpl` masque l'extension de l'entity [`WorkAxisRepositoryImpl.kt:176`]
- [x] [Review][Patch] `object SessionClosed` et `object SessionDeleted` non convertis en `data object` [`MatchViewModel.kt:113`, `SessionDetailViewModel.kt:16`]
- [x] [Review][Defer] SQL `'PENDING'` literal dans `AxisSuggestionDao` sans lien compile-time vers `AxisSuggestionStatus.PENDING.name` [`AxisSuggestionDao.kt:12`] — deferred, pre-existing (T4.1 accepté par spec)
- [x] [Review][Defer] `SettingsUiState.frequency` initialisé à `DAILY` en dur avant la lecture du repository (flash UI incorrect) [`SettingsViewModel.kt:25`] — deferred, pre-existing
- [x] [Review][Defer] Contrat de sérialisation `.name`/`valueOf` : renommage d'un enum = breaking change silencieux — deferred, pre-existing
- [x] [Review][Defer] `isChangeover()` sans test unitaire pour la branche `MatchOver → false` [`ScoreViewModel.kt`] — deferred, test coverage
