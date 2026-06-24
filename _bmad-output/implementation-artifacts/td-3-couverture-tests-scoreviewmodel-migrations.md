---
baseline_commit: 4d97181
---

# Story TD-3 : Couverture Tests — ScoreViewModel & Migrations Room

Status: ready-for-dev

## Story

As a developer,
I want missing ScoreViewModel test cases added and Room migration tests created,
So that regressions in changeover logic and schema evolution are caught before production.

## Context

**Problème 1 — Gaps de test ScoreViewModel (stories 2.4, 2.5)**

Trois comportements importants ne sont pas assertés :
1. `sendGameOver` ne doit PAS être appelé lors d'un `undo()` — si `undo()` est accidentellement modifié pour appeler `sendGameOver`, aucun test ne le détecterait
2. `sendGameOver` ne doit PAS être appelé lors d'un `MatchOver` — comportement documenté won't-fix dans la spec mais non asserté
3. Le chemin tie-break 7-6 (`awardTieBreakGame → awardSet → SetWon(changeover=true)`) n'est pas couvert

**Problème 2 — Aucun test `MigrationTestHelper` (stories 3.3, 4.1, 5.3)**

11 migrations (v1 → v11) n'ont aucune couverture instrumentée. Une migration SQL incorrecte peut corrompre les données sans erreur détectable avant production.

Les migrations à risque prioritaire (identifiées en code review) :
- `MIGRATION_5_6` (CREATE TABLE coaching_cache + CREATE UNIQUE INDEX)
- `MIGRATION_6_7` (ALTER TABLE — non idempotente)
- `MIGRATION_8_9` (identifiée comme non testée en review 5.3)
- `MIGRATION_10_11` (ALTER TABLE sessions ADD COLUMN scheduled_at)

Source : deferred items 2-5 (passe 1 et 2), 3-3, 4-1, 5-3 D6.

## Acceptance Criteria

1. **Given** le ViewModel est dans un état en cours de match
   **When** `undo()` est appelé
   **Then** `dataLayerClient.sendGameOver()` n'est PAS appelé

2. **Given** le match se termine (MatchOver)
   **When** l'engine émet `MatchOver`
   **Then** `dataLayerClient.sendGameOver()` n'est PAS appelé

3. **Given** un set se termine par tie-break 7-6
   **When** le dernier point du tie-break est enregistré
   **Then** `sendGameOver` est appelé (changeover à la fin du set)

4. **Given** les migrations Room sont appliquées séquentiellement
   **When** chaque migration est exécutée via `MigrationTestHelper`
   **Then** le schéma résultant est identique au schéma généré par Room à la version cible

## Tasks / Subtasks

---

### BLOC A — Tests ScoreViewModel manquants

- [ ] **T1 — Test : `sendGameOver` NOT called on undo**
  - [ ] T1.1 Dans `android/wear/src/test/kotlin/com/secondserve/wear/presentation/match/ScoreViewModelTest.kt`, ajouter après les tests existants :
    ```kotlin
    @Test
    fun `sendGameOver NOT called when undo is performed`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (changeover → sendGameOver called once)
        repeat(4) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }

        // Undo un point dans le jeu suivant (pas de game over)
        vm.recordPoint(Player.A)
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        vm.undo()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO }
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()

        // sendGameOver doit rester à 1 appel (pas d'appel supplémentaire lors de l'undo)
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }
    ```

- [ ] **T2 — Test : `sendGameOver` NOT called on MatchOver**
  - [ ] T2.1 Ajouter dans le même fichier :
    ```kotlin
    @Test
    fun `sendGameOver NOT called when match is over (MatchOver event)`() = runTest {
        val vm = createViewModel()
        // Simuler une victoire au match le plus rapide : BEST_OF_1, gagner 6-0
        // (format par défaut : BEST_OF_3, donc il faut gagner 2 sets)
        // Set 1 : A gagne 6-0 (6 jeux à love)
        repeat(6 * 4) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.completedSets.size == 1 }
        // Set 2 : A gagne 6-0
        repeat(6 * 4) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.isMatchOver }
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()

        // Le dernier jeu du match entraîne MatchOver, pas un changeover supplémentaire
        // Nombre de game_over = nombre de jeux avec changeover (total impair)
        // Set 1 : jeux 1, 3, 5 (total 1, 3, 5 → impair) → 3 changeover
        // Set 2 : jeux 7, 9, 11 (total global 7, 9, 11 → impair) → 3 changeover
        // Total : 6 game_over (pas de game_over sur le MatchOver lui-même)
        // Note: ajuster les comptes selon la logique exacte de changeover
        val state = vm.container.stateFlow.value
        assertTrue(state.isMatchOver)
        // Vérifier qu'aucun game_over n'a été envoyé pour le dernier point qui a mis fin au match
        // (c'est le comportement won't-fix documenté dans la spec)
        // Le test principal : sendGameOver count = jeux avec changeover SEULEMENT
        val expectedChangeovers = 6 // à ajuster selon le format exact
        coVerify(exactly = expectedChangeovers) { dataLayerClient.sendGameOver(any()) }
    }
    ```
    - [ ] T2.2 Ajuster `expectedChangeovers` en exécutant le test une première fois et en observant le compte réel — le but est d'asserter que ce nombre ne change pas si MatchOver commence à déclencher sendGameOver

- [ ] **T3 — Test : tie-break 7-6 (changeover via `awardTieBreakGame`)**
  - [ ] T3.1 Ajouter dans le même fichier :
    ```kotlin
    @Test
    fun `game_over sent when set ends with tie-break 7-6 (SetWon via awardTieBreakGame)`() = runTest {
        val vm = createViewModel()
        // Amener le score à 6-6 pour déclencher le tie-break
        // Format par défaut : BEST_OF_3, FULL_ADVANTAGE, troisième set = MATCH_TIE_BREAK
        // Set à 6-6 → tie-break
        // Score séquence rapide : A et B alternent les jeux jusqu'à 6-6
        // Jeux 1-6 : alternés pour minimiser les points (A gagne 1,3,5 ; B gagne 2,4,6)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 1 → 1-0, total=1 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 2 → 1-1, total=2 (pair → pas changeover)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 3 → 2-1, total=3 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 4 → 2-2, total=4 (pair → pas changeover)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 5 → 3-2, total=5 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 6 → 3-3, total=6 (pair → pas changeover)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 7 → 4-3, total=7 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 8 → 4-4, total=8 (pair → pas changeover)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 9 → 5-4, total=9 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 10 → 5-5, total=10 (pair → pas changeover)
        repeat(4) { vm.recordPoint(Player.A) } // jeu 11 → 6-5, total=11 (impair → changeover)
        repeat(4) { vm.recordPoint(Player.B) } // jeu 12 → 6-6, total=12 (pair → pas changeover)
        // À 6-6 → tie-break : jouer 7 points pour A (tie-break à 7-0)
        // Nombre de game_over jusqu'ici : 6 (jeux à total impair : 1,3,5,7,9,11)
        vm.container.stateFlow.first { it.score.currentSetGamesA == 6 && it.score.currentSetGamesB == 6 }
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
        val gameOverCountBefore = mutableListOf<Unit>()

        // Tie-break : A gagne 7-0 → SetWon(changeover=true car total_jeux devient 13, impair)
        repeat(7) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.completedSets.size == 1 }
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()

        // sendGameOver doit être appelé pour le tie-break (total = 13, impair → changeover)
        // 6 (jeux normaux) + 1 (tie-break) = 7 sendGameOver au total
        coVerify(exactly = 7) { dataLayerClient.sendGameOver(any()) }
    }
    ```

---

### BLOC B — Tests de migration Room (androidTest)

- [ ] **T4 — Ajouter `room-testing` dans `data/build.gradle.kts`**
  - [ ] T4.1 Dans `android/data/build.gradle.kts`, ajouter dans la section `dependencies` :
    ```kotlin
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation(libs.junit4)
    androidTestImplementation("androidx.test:runner:1.6.2")
    ```
  - [ ] T4.2 Ajouter l'alias dans `libs.versions.toml` si nécessaire :
    ```toml
    room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
    ```

- [ ] **T5 — Configurer `exportSchema` dans Room**
  - [ ] T5.1 Dans `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`, vérifier que `exportSchema = true` est présent dans `@Database`. Si absent, l'ajouter :
    ```kotlin
    @Database(
        entities = [...],
        version = 11,
        exportSchema = true
    )
    ```
  - [ ] T5.2 Dans `android/data/build.gradle.kts`, vérifier que le chemin de schema est configuré pour le KSP :
    ```kotlin
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    ```
  - [ ] T5.3 Si des fichiers JSON de schema n'existent pas dans `android/data/schemas/`, les générer via `./gradlew :data:kspDebugKotlin`

- [ ] **T6 — Créer `SecondServeDatabaseMigrationTest` (androidTest)**
  - [ ] T6.1 Créer le fichier `android/data/src/androidTest/kotlin/com/secondserve/data/SecondServeDatabaseMigrationTest.kt` :
    ```kotlin
    package com.secondserve.data
    
    import androidx.room.testing.MigrationTestHelper
    import androidx.test.ext.junit.runners.AndroidJUnit4
    import androidx.test.platform.app.InstrumentationRegistry
    import com.secondserve.data.local.db.SecondServeDatabase
    import org.junit.Rule
    import org.junit.Test
    import org.junit.runner.RunWith
    import java.io.IOException
    
    @RunWith(AndroidJUnit4::class)
    class SecondServeDatabaseMigrationTest {
    
        private val TEST_DB = "migration-test"
    
        @get:Rule
        val helper: MigrationTestHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SecondServeDatabase::class.java
        )
    
        @Test
        @Throws(IOException::class)
        fun migrate5To6() {
            helper.createDatabase(TEST_DB, 5).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 6, true,
                SecondServeDatabase.MIGRATION_5_6
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrate6To7() {
            helper.createDatabase(TEST_DB, 6).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 7, true,
                SecondServeDatabase.MIGRATION_6_7
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrate7To8() {
            helper.createDatabase(TEST_DB, 7).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 8, true,
                SecondServeDatabase.MIGRATION_7_8
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrate8To9() {
            helper.createDatabase(TEST_DB, 8).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 9, true,
                SecondServeDatabase.MIGRATION_8_9
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrate9To10() {
            helper.createDatabase(TEST_DB, 9).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 10, true,
                SecondServeDatabase.MIGRATION_9_10
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrate10To11() {
            helper.createDatabase(TEST_DB, 10).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 11, true,
                SecondServeDatabase.MIGRATION_10_11
            )
        }
    
        @Test
        @Throws(IOException::class)
        fun migrateAll() {
            helper.createDatabase(TEST_DB, 5).apply { close() }
            helper.runMigrationsAndValidate(
                TEST_DB, 11, true,
                SecondServeDatabase.MIGRATION_5_6,
                SecondServeDatabase.MIGRATION_6_7,
                SecondServeDatabase.MIGRATION_7_8,
                SecondServeDatabase.MIGRATION_8_9,
                SecondServeDatabase.MIGRATION_9_10,
                SecondServeDatabase.MIGRATION_10_11
            )
        }
    }
    ```
  - [ ] T6.2 Vérifier que les objets `MIGRATION_*` dans `SecondServeDatabase.kt` sont `companion object val` (accessibles statiquement) et non des variables locales

- [ ] **T7 — Rendre les MIGRATION_* accessibles en `companion object`**
  - [ ] T7.1 Lire `android/data/src/main/kotlin/com/secondserve/data/local/db/SecondServeDatabase.kt`
  - [ ] T7.2 Vérifier que les `val MIGRATION_X_Y = ...` sont bien dans le `companion object` de `SecondServeDatabase`
  - [ ] T7.3 Si non, les déplacer dans le `companion object`

---

### BLOC C — Exécution & validation

- [ ] **T8 — Lancer les tests unitaires ScoreViewModel**
  - [ ] T8.1 `./gradlew :wear:testDebugUnitTest` — tous les tests doivent passer
  - [ ] T8.2 Ajuster le test T2 (`expectedChangeovers`) si le premier run révèle un count différent de l'estimé

- [ ] **T9 — Lancer les tests de migration (device/emulateur requis)**
  - [ ] T9.1 `./gradlew :data:connectedAndroidTest` sur un émulateur ou device connecté
  - [ ] T9.2 En cas d'échec d'un test de migration, corriger la migration SQL correspondante dans `SecondServeDatabase.kt`

## Dev Notes

- Les tests de migration (`MigrationTestHelper`) sont des **androidTest** — ils nécessitent un émulateur ou device physique. Ils ne s'exécutent pas en JVM unit test.
- Pour que `MigrationTestHelper` fonctionne, Room a besoin des fichiers JSON de schéma dans `android/data/schemas/`. Ces fichiers doivent être générés et committés.
- Le test `migrateAll` est le plus important : il valide le chemin de migration depuis v5 (première version avec données réelles) jusqu'à v11 (actuelle).
- Si des migrations 1→4 n'ont pas de schéma JSON sauvegardé, sauter `migrateAll` depuis v1 — commencer depuis v5.
- Pour T2 (`MatchOver`), le compte exact de `sendGameOver` dépend du format BEST_OF_3 : ajuster après le premier run.

## Deferred items adressés

- `2-5` — Pas de test `sendGameOver NOT called on undo/MatchOver`
- `2-5` — Pas de test tie-break 7-6 (`SetWon` changeover via `awardTieBreakGame`)
- `3-3` — Aucun test d'intégration Room pour MIGRATION_5_6
- `4-1` — Paths de migration v1→v6 non testés via `MigrationTestHelper`
- `5-3 D6` — Migration 8→9 non couverte par un test `MigrationTestHelper`
