---
baseline_commit: 3e1474c537860e8c290df76f6c423cc346a2a4ef
---

# Story 3.1: InferenceEngine Interface + MockInferenceEngine

Status: review

## Story

As a developer,
I want an `InferenceEngine` interface with a `MockInferenceEngine` for CI and tests,
so that all coaching logic can be developed and tested without a physical device or AICore support.

## Acceptance Criteria

1. **Given** le module `:core:ai`
   **Then** l'interface `InferenceEngine` est définie avec `suspend fun generate(prompt: String): AppResult<String>`

2. **And** `MockInferenceEngine` retourne des réponses déterministes configurables via son constructeur (ex : réponse fixe, simulation d'erreur)

3. **And** Hilt fournit `MockInferenceEngine` en environnement test/CI et `GeminiNanoEngine` en production via un binding module

4. **And** `MockInferenceEngineTest.kt` valide le comportement du mock

5. **And** l'émulateur CI n'a aucune dépendance vers AICore — les tests d'intégration passent sans device physique

---

## Architecture Context

### Position dans la séquence (ARCH-13)

```
Story 2.6 ✅ → Story 3.1 (CETTE STORY) → 3.2 (GeminiNanoEngine) → 3.3 (OfflineCoachingCache) → 3.4 (CoachingResolver)
```

### Dépendances satisfaites

- ✅ Module `:core:ai` existe avec son `build.gradle.kts` (Hilt, coroutines, dépendance `:domain`)
- ✅ `InferenceEngine.kt` **EXISTE DÉJÀ** dans `:core:ai` avec la signature exacte de la spec (voir ci-dessous) — ne pas le recréer ni le modifier
- ✅ `AppResult<T>` défini dans `:domain` — `AppResult.Success(data)` et `AppResult.Error(exception)` (mono-argument)
- ✅ `DataModule.kt` établit le pattern `@Binds`/`@Provides` Hilt dans `:app/di/`
- ✅ Hilt 2.56.1 configuré dans `libs.versions.toml`

### Ce que cette story NE fait PAS

- ❌ Pas d'implémentation `GeminiNanoEngine` (Story 3.2)
- ❌ Pas de `VpsMistralEngine` (Story 5.1)
- ❌ Pas de `CoachingResolver` ni `OfflineCoachingCache` (Stories 3.3/3.4)
- ❌ Pas de dépendance ML Kit Generative AI / Android AICore dans cette story
- ❌ Pas de migration Room (aucune nouvelle table)

---

## Technical Requirements

### GUARDRAIL CRITIQUE — `InferenceEngine.kt` déjà implémenté

**`android/core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`** existe avec :

```kotlin
package com.secondserve.core.ai

import com.secondserve.domain.AppResult

interface InferenceEngine {
    suspend fun generate(prompt: String): AppResult<String>
}
```

**NE PAS MODIFIER ce fichier.** Il correspond exactement à la spec ARCH-8. AC 1 est déjà satisfait.

---

### Fichier 1 — `MockInferenceEngine.kt` (NEW)

**`android/core/ai/src/main/kotlin/com/secondserve/core/ai/mock/MockInferenceEngine.kt`**

```kotlin
package com.secondserve.core.ai.mock

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import javax.inject.Inject

class MockInferenceEngine @Inject constructor(
    private val fixedResponse: String = DEFAULT_RESPONSE,
    private val simulateError: Boolean = false,
    private val errorMessage: String = "MockInferenceEngine simulated error"
) : InferenceEngine {

    override suspend fun generate(prompt: String): AppResult<String> {
        return if (simulateError) {
            AppResult.Error(RuntimeException(errorMessage))
        } else {
            AppResult.Success(fixedResponse)
        }
    }

    companion object {
        const val DEFAULT_RESPONSE = "Conseil mock : reste concentré sur le prochain point."
    }
}
```

**Comportements attendus :**
- Constructeur sans arguments → retourne `DEFAULT_RESPONSE` en `AppResult.Success`
- `fixedResponse = "texte custom"` → retourne ce texte
- `simulateError = true` → retourne `AppResult.Error(RuntimeException(errorMessage))`
- Déterministe : même entrée → même sortie (prompt ignoré intentionnellement dans le mock)

---

### Fichier 2 — `AiModule.kt` (NEW) dans `:app/di/`

**`android/app/src/main/kotlin/com/secondserve/di/AiModule.kt`**

```kotlin
package com.secondserve.di

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.mock.MockInferenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: MockInferenceEngine): InferenceEngine
}
```

> ⚠️ **Binding temporaire** : `MockInferenceEngine` est fourni comme `InferenceEngine` dans toute la Story 3.1. Story 3.2 remplacera ce binding par `GeminiNanoEngine` en release, `MockInferenceEngine` en debug.

> ⚠️ **Pattern `@Binds` vs `@Provides`** : utiliser `@Binds` (abstract, pas de corps) pour les bindings interface→implémentation. Ne pas dupliquer avec un `@Provides` — cela provoquerait un conflit Hilt à la compilation (bug observé en Story 2.6 avec `SessionRepository`).

> ⚠️ **`@Inject constructor` sur `MockInferenceEngine`** : OBLIGATOIRE pour que le `@Binds` fonctionne. Hilt doit savoir comment construire l'implémentation. Oublier `@Inject` → erreur Hilt à la compilation (bug reproduit en Story 2.6 avec `CloseMatchUseCase`).

---

### Fichier 3 — `MockInferenceEngineTest.kt` (NEW)

**`android/core/ai/src/test/kotlin/com/secondserve/core/ai/MockInferenceEngineTest.kt`**

```kotlin
package com.secondserve.core.ai

import com.secondserve.core.ai.mock.MockInferenceEngine
import com.secondserve.domain.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockInferenceEngineTest {

    @Test
    fun `generate returns default response when no args`() = runTest {
        val mock = MockInferenceEngine()
        val result = mock.generate("any prompt")
        assertIs<AppResult.Success<String>>(result)
        assertEquals(MockInferenceEngine.DEFAULT_RESPONSE, result.data)
    }

    @Test
    fun `generate returns fixed response when configured`() = runTest {
        val expected = "Conseil personnalisé de test"
        val mock = MockInferenceEngine(fixedResponse = expected)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Success<String>>(result)
        assertEquals(expected, result.data)
    }

    @Test
    fun `generate returns error when simulateError is true`() = runTest {
        val mock = MockInferenceEngine(simulateError = true)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Error>(result)
        assertTrue(result.exception is RuntimeException)
    }

    @Test
    fun `generate returns error with custom message when simulateError is true`() = runTest {
        val errorMsg = "Test error message"
        val mock = MockInferenceEngine(simulateError = true, errorMessage = errorMsg)
        val result = mock.generate("any prompt")
        assertIs<AppResult.Error>(result)
        assertEquals(errorMsg, result.exception.message)
    }

    @Test
    fun `generate is deterministic — same prompt returns same response`() = runTest {
        val mock = MockInferenceEngine(fixedResponse = "réponse fixe")
        val result1 = mock.generate("prompt A")
        val result2 = mock.generate("prompt B")
        assertEquals(result1, result2)
    }
}
```

> ⚠️ **JUnit 5** : utiliser `@Test` de `org.junit.jupiter.api.Test` (pas de `@ExtendWith`). Test JVM pur — aucun émulateur ni device requis.

> ⚠️ **`assertIs<T>`** : vient de `kotlin.test`. Pas d'imports ambigus à créer. Si la classe a une fonction locale `assertIs`, supprimer le doublon (bug observé en Story 2.6, `SessionRepositoryImplTest.kt`).

---

### Fichier 4 — `build.gradle.kts` (UPDATE) dans `:core:ai`

**`android/core/ai/build.gradle.kts`**

Ajouter les dépendances de test (même pattern que `:domain`) :

```kotlin
dependencies {
    implementation(project(":domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)

    // Tests JVM (même pattern que :domain)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

> ⚠️ Pas de `testImplementation(libs.mockk)` nécessaire pour `MockInferenceEngineTest` — le mock est du code de production, pas un mock MockK. Ajouter MockK uniquement si les tests en ont besoin.

---

## Tasks / Subtasks

### Interface & Mock

- [x] **Task AI-1** — Vérifier `InferenceEngine.kt` : confirmer que le fichier existant correspond à la spec (AC 1 déjà satisfait — NE PAS MODIFIER)
- [x] **Task AI-2** — Créer `MockInferenceEngine.kt` dans `core/ai/src/main/kotlin/com/secondserve/core/ai/mock/` avec `@Inject constructor`, `fixedResponse`, `simulateError`, `errorMessage`

### Hilt Binding

- [x] **Task AI-3** — Créer `AiModule.kt` dans `app/src/main/kotlin/com/secondserve/di/` avec `@Binds @Singleton` de `MockInferenceEngine → InferenceEngine`

### Tests

- [x] **Task AI-4** — Mettre à jour `core/ai/build.gradle.kts` : ajouter `testImplementation(junit5.api)`, `testRuntimeOnly(junit5.engine + launcher)`, `testImplementation(coroutines.test)` + `tasks.withType<Test> { useJUnitPlatform() }`
- [x] **Task AI-5** — Créer le répertoire `core/ai/src/test/kotlin/com/secondserve/core/ai/`
- [x] **Task AI-6** — Créer `MockInferenceEngineTest.kt` avec les 5 tests : default response, fixed response, error simulation, error message, determinism

### Validation

- [x] **Task AI-7** — Lancer `:core:ai:testDebugUnitTest` — tous les tests doivent passer (pas de device, pas d'AICore)
- [x] **Task AI-8** — Lancer `:app:hiltJavaCompileDebug` (ou `:app:kspDebugKotlin`) — aucun conflit Hilt

---

## Dev Notes

### Guardrails critiques

#### ⚠️ `InferenceEngine.kt` préexistant — ne pas recréer

Ce fichier est dans le build et compilé. Le recréer avec des modifications légères pourrait introduire un conflit de symbol. Vérifier son contenu avant tout, le laisser intact.

#### ⚠️ `@Inject constructor` obligatoire sur `MockInferenceEngine`

Sans `@Inject`, Hilt ne sait pas construire `MockInferenceEngine` et le `@Binds` dans `AiModule` échouera à la compilation Hilt avec une erreur de type :
```
[Hilt] Cannot inject members into non-@Inject or @Provides annotated type
```
Bug reproduit en Story 2.6 (`CloseMatchUseCase.kt:7`). Ne pas l'oublier.

#### ⚠️ `@Binds` uniquement dans `AiModule` — pas de `@Provides` en double

Un `@Provides` et un `@Binds` pour le même type dans des modules installés dans le même composant = erreur Hilt à la compilation :
```
[Hilt] Multiple bindings for the same type
```
Bug reproduit en Story 2.6 (`DataModule.kt` binding `SessionRepository` en double). Utiliser exclusivement `@Binds abstract fun`.

#### ⚠️ `MockInferenceEngine` est dans `:core:ai` (main), PAS dans `src/test/`

`MockInferenceEngine` doit être dans `src/main/` car il sera utilisé dans les tests instrumentés ET comme binding Hilt en Story 3.2 (debug variant). Le placer dans `src/test/` le rendrait invisible aux autres modules.

#### ⚠️ `AppResult.Error(e)` — mono-argument seulement

Pattern établi depuis Story 2.4 et confirmé en 2.6 :
```kotlin
// ✅ Correct
AppResult.Error(RuntimeException(message))
// ❌ Interdit
AppResult.Error(exception, "message string")  // pas de surcharge 2-args
```

#### ⚠️ Story 3.2 remplacera le binding `AiModule`

`AiModule.kt` est temporaire dans son état actuel (uniquement `MockInferenceEngine`). Story 3.2 devra :
- Soit modifier `AiModule.kt` pour utiliser `GeminiNanoEngine` en `src/release/` et `MockInferenceEngine` en `src/debug/`
- Soit restructurer en deux variants de source set

Ne pas anticiper cette structure dans cette story — Story 3.2 s'en chargera.

### Patterns à réutiliser

| Pattern | Source |
|---------|--------|
| `AppResult.Error(e)` mono-arg | `SessionRepositoryImpl.kt:24`, `DataLayerClient.kt` |
| `@Binds @Singleton abstract fun bind...` | `DataModule.kt` (pattern @Binds) |
| Tests JUnit 5 `runTest` | `domain/test/.../TennisScoreEngineTest.kt` |
| `tasks.withType<Test> { useJUnitPlatform() }` | `domain/build.gradle.kts:15` |
| `testImplementation(libs.junit5.api)` + `testRuntimeOnly(libs.junit5.engine)` | `domain/build.gradle.kts:7-9` |

### Structure fichiers finale

```
android/
├── app/
│   └── src/main/kotlin/com/secondserve/di/
│       └── AiModule.kt                        ← NEW
│
└── core/ai/
    ├── build.gradle.kts                       ← UPDATE (test deps)
    └── src/
        ├── main/kotlin/com/secondserve/core/ai/
        │   ├── InferenceEngine.kt             ← EXISTE, ne pas modifier
        │   └── mock/
        │       └── MockInferenceEngine.kt     ← NEW
        └── test/kotlin/com/secondserve/core/ai/
            └── MockInferenceEngineTest.kt     ← NEW (répertoire à créer)
```

### Références

- [Source: epics.md § Story 3.1] — User story et ACs complets
- [Source: architecture.md § ARCH-8] — "InferenceEngine interface dans :core:ai + GeminiNanoEngine (production) + MockInferenceEngine (tests/CI)"
- [Source: architecture.md § CI & Testabilité] — "InferenceEngine exposé comme interface avec deux implémentations"
- [Source: architecture.md § ARCH-12] — "Hilt DI — AiModule, bindings pour InferenceEngine"
- [Source: architecture.md § Project Structure] — Arborescence `:core:ai/mock/MockInferenceEngine.kt`
- [Source: 2-6-*.md § Review Findings] — Bug `@Inject constructor` manquant sur `CloseMatchUseCase` → erreur Hilt
- [Source: 2-6-*.md § Review Findings] — Bug binding dupliqué `@Provides` + `@Binds` sur `SessionRepository`
- [Source: domain/build.gradle.kts] — Pattern test JUnit 5 à reproduire dans `:core:ai`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Erreur pré-existante `javax.inject.Inject` non résolu dans `:domain` — `CloseMatchUseCase.kt` utilisait `@Inject constructor` mais `domain/build.gradle.kts` manquait `compileOnly("javax.inject:javax.inject:1")`. Fix appliqué (hors scope story 3.1 mais bloquant pour la compilation).
- `kotlin.test` manquant dans `:core:ai` — `assertIs`, `assertEquals`, `assertTrue` de `kotlin.test` nécessitent `testImplementation(kotlin("test"))`. Ajouté au `build.gradle.kts`.

### Completion Notes List

- AC 1 : `InferenceEngine.kt` pré-existant vérifié, conforme à la spec ARCH-8, non modifié.
- AC 2 : `MockInferenceEngine` créé avec comportements déterministes (default, fixedResponse, simulateError, errorMessage). `@Inject constructor` inclus pour Hilt.
- AC 3 : `AiModule.kt` créé dans `:app/di/` avec `@Binds @Singleton` unique (pas de `@Provides` dupliqué). `:app:kspDebugKotlin` : BUILD SUCCESSFUL.
- AC 4 : `MockInferenceEngineTest.kt` créé avec 5 tests JUnit 5 + `kotlin.test`. `:core:ai:testDebugUnitTest` : BUILD SUCCESSFUL — 5 tests passent.
- AC 5 : Aucune dépendance AICore ou ML Kit ajoutée. Tests JVM purs, pas de device requis.
- Fix collatéral : `javax.inject:javax.inject:1` ajouté en `compileOnly` dans `:domain` pour résoudre une erreur de compilation préexistante de Story 2.6 (`CloseMatchUseCase.kt`).

### File List

- `android/core/ai/src/main/kotlin/com/secondserve/core/ai/mock/MockInferenceEngine.kt` — NEW
- `android/app/src/main/kotlin/com/secondserve/di/AiModule.kt` — NEW
- `android/core/ai/src/test/kotlin/com/secondserve/core/ai/MockInferenceEngineTest.kt` — NEW
- `android/core/ai/build.gradle.kts` — UPDATED (dépendances test JUnit5 + kotlin-test)
- `android/domain/build.gradle.kts` — UPDATED (fix préexistant : `compileOnly("javax.inject:javax.inject:1")`)

## Change Log

- 2026-06-20 : Implémentation story 3.1 — Création de `MockInferenceEngine`, `AiModule`, `MockInferenceEngineTest`, mise à jour `core/ai/build.gradle.kts`. Fix collatéral `domain/build.gradle.kts` (`javax.inject` manquant depuis Story 2.6).
