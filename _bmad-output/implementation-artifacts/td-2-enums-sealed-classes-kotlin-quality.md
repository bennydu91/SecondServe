---
baseline_commit: 4d97181
---

# Story TD-2 : Enums & Sealed Classes — Qualité Kotlin

Status: ready-for-dev

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

- [ ] **T1 — Créer `AxisSuggestionStatus` dans le domain**
  - [ ] T1.1 Créer le fichier `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestionStatus.kt` :
    ```kotlin
    package com.secondserve.domain.model
    
    enum class AxisSuggestionStatus {
        PENDING, ACCEPTED, IGNORED
    }
    ```

- [ ] **T2 — Mettre à jour `AxisSuggestion` domain model**
  - [ ] T2.1 Dans `android/domain/src/main/kotlin/com/secondserve/domain/model/AxisSuggestion.kt`, changer :
    ```kotlin
    // Avant
    val status: String = "PENDING"
    // Après
    val status: AxisSuggestionStatus = AxisSuggestionStatus.PENDING
    ```

- [ ] **T3 — Mettre à jour `AxisSuggestionEntity`**
  - [ ] T3.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/entity/AxisSuggestionEntity.kt` :
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

- [ ] **T4 — Mettre à jour `AxisSuggestionDao`**
  - [ ] T4.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/dao/AxisSuggestionDao.kt`, les queries SQL restent avec des string literals (Room query SQL) — pas de changement nécessaire :
    ```kotlin
    @Query("SELECT * FROM axis_suggestions WHERE status = 'PENDING' ORDER BY generated_at DESC")
    ```

- [ ] **T5 — Mettre à jour `WorkAxisRepositoryImpl`**
  - [ ] T5.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`, remplacer les usages de status String par `AxisSuggestionStatus` :
    - Chercher `status = "ACCEPTED"`, `status = "IGNORED"`, `status = "PENDING"` dans ce fichier
    - Remplacer par `status = AxisSuggestionStatus.ACCEPTED.name` (pour les entités) ou `status = AxisSuggestionStatus.ACCEPTED` (pour le domain)

---

### BLOC B — `NotificationFrequency` enum dans le domain

- [ ] **T6 — Créer `NotificationFrequency` dans le domain**
  - [ ] T6.1 Créer le fichier `android/domain/src/main/kotlin/com/secondserve/domain/notification/NotificationFrequency.kt` :
    ```kotlin
    package com.secondserve.domain.notification
    
    enum class NotificationFrequency {
        DAILY, EVERY_2_DAYS, WEEKLY, DISABLED
    }
    ```

- [ ] **T7 — Mettre à jour `PlayerDataStore`**
  - [ ] T7.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/PlayerDataStore.kt`, changer :
    ```kotlin
    // Avant
    prefs.getString(KEY_NOTIF_FREQUENCY, "DAILY") ?: "DAILY"
    // Après : stocker le name() de l'enum en SharedPreferences
    NotificationFrequency.valueOf(prefs.getString(KEY_NOTIF_FREQUENCY, "DAILY") ?: "DAILY")
    ```
  - [ ] T7.2 Adapter les fonctions getFrequency() / saveFrequency() pour travailler avec `NotificationFrequency`

- [ ] **T8 — Mettre à jour `NotificationRepositoryImpl`**
  - [ ] T8.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/NotificationRepositoryImpl.kt`, remplacer le `when` sur String par un `when` sur `NotificationFrequency` :
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

- [ ] **T9 — Mettre à jour `SettingsViewModel`**
  - [ ] T9.1 Dans `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsViewModel.kt`, changer le type de `frequency` dans l'état :
    ```kotlin
    // Avant
    val frequency: String = "DAILY"
    // Après
    val frequency: NotificationFrequency = NotificationFrequency.DAILY
    ```

- [ ] **T10 — Mettre à jour `SettingsScreen`**
  - [ ] T10.1 Dans `android/feature/profile/src/main/kotlin/com/secondserve/feature/profile/SettingsScreen.kt`, adapter la map des labels :
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

- [ ] **T11 — `HistoryUiState.kt`**
  - [ ] T11.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/HistoryUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : HistoryUiState()
    // Après
    data object Loading : HistoryUiState()
    ```

- [ ] **T12 — `SessionDetailUiState.kt`**
  - [ ] T12.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/SessionDetailUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : SessionDetailUiState()
    // Après
    data object Loading : SessionDetailUiState()
    ```

- [ ] **T13 — `StatsUiState.kt`**
  - [ ] T13.1 Dans `android/feature/history/src/main/kotlin/com/secondserve/feature/history/StatsUiState.kt`, changer :
    ```kotlin
    // Avant
    object Loading : StatsUiState()
    // Après
    data object Loading : StatsUiState()
    ```

- [ ] **T14 — Vérifier les autres sealed class**
  - [ ] T14.1 Rechercher `^    object ` dans tous les fichiers `*UiState.kt` et `*SideEffect.kt` du projet
  - [ ] T14.2 Appliquer `data object` à chaque occurrence trouvée dans une sealed class

---

### BLOC D — `isChangeover()` en extension function + when exhaustif

- [ ] **T15 — Extraire `isChangeover()` et supprimer `else -> false`**
  - [ ] T15.1 Dans `android/wear/src/main/kotlin/com/secondserve/wear/presentation/match/ScoreViewModel.kt`, localiser le bloc `val changeover = when(event) { ... else -> false }`
  - [ ] T15.2 Remplacer par une extension function privée :
    ```kotlin
    private fun EngineEvent.isChangeover(): Boolean = when (this) {
        is EngineEvent.SetWon -> this.changeover
        is EngineEvent.MatchOver -> false
        // Ajouter tous les autres sous-types de EngineEvent explicitement
        // Le compilateur signalera si un cas est manquant
        else -> false  // À supprimer une fois tous les cas couverts
    }
    ```
  - [ ] T15.3 Lire `EngineEvent` sealed class pour lister tous les sous-types et les couvrir explicitement dans le `when`, puis supprimer le `else`
  - [ ] T15.4 Utiliser `event.isChangeover()` dans le code appelant

---

### BLOC E — Compilation & tests

- [ ] **T16 — Vérifier la compilation**
  - [ ] T16.1 `./gradlew :domain:compileDebugKotlin :data:compileDebugKotlin :feature:profile:compileDebugKotlin :wear:compileDebugKotlin`
  - [ ] T16.2 Corriger toutes les erreurs de compilation (les usages non migrés des anciens String literals)

- [ ] **T17 — Lancer les tests unitaires**
  - [ ] T17.1 `./gradlew testDebugUnitTest` — tous les tests doivent passer

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
