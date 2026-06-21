---
baseline_commit: 2689c7c
---

# Story 3.2: GeminiNanoEngine — Coaching on-device

Status: done

## Story

As a developer,
I want a `GeminiNanoEngine` using the ML Kit Generative AI APIs (Android AICore),
so that coaching advice is generated on-device in ≤ 3s with zero network call.

## Acceptance Criteria

1. **Given** `GeminiNanoEngine` est initialisé
   **When** `generate(prompt)` est appelé sur Pixel 9 Pro
   **Then** il utilise les ML Kit Generative AI APIs (`com.google.mlkit:genai-prompt`)
   **And** retourne un `AppResult.Success<String>` en ≤ 3 secondes (NFR-P1)

2. **Given** Android AICore est indisponible (`checkStatus()` ≠ `FeatureStatus.AVAILABLE`)
   **When** `generate()` est appelé
   **Then** il retourne `AppResult.Error(InferenceEngineException(ErrorCode.INFERENCE_FAILED, ...))`

3. **And** Timber loggue à niveau DEBUG : `"GeminiNanoEngine unavailable, falling back"`
   (string exacte — sera matchée par `CoachingResolver` dans Story 3.4)

4. **And** Hilt fournit `GeminiNanoEngine` en release et `MockInferenceEngine` en debug via `AiModule` split (source sets `src/release/` et `src/debug/`)

5. **And** les tests CI utilisent `MockInferenceEngine` (Story 3.1) — aucun test Gemini Nano sur émulateur

6. **And** les tests sur device physique (Pixel 9 Pro) valident la latence ≤ 3s — test manuel ou instrumenté séparé

---

## Architecture Context

### Position dans la séquence (ARCH-13)

```
Story 3.1 ✅ → Story 3.2 (CETTE STORY) → 3.3 (OfflineCoachingCache) → 3.4 (CoachingResolver)
```

### Dépendances satisfaites

- ✅ `InferenceEngine` interface : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`
  ```kotlin
  interface InferenceEngine {
      suspend fun generate(prompt: String): AppResult<String>
  }
  ```
- ✅ `MockInferenceEngine` : `android/core/ai/src/main/kotlin/com/secondserve/core/ai/mock/MockInferenceEngine.kt`
- ✅ `AiModule.kt` actuel : `android/app/src/main/kotlin/com/secondserve/di/AiModule.kt` — **À SUPPRIMER** (remplacé par split debug/release)
- ✅ `AppResult<T>` : `android/domain/src/main/kotlin/com/secondserve/domain/AppResult.kt` — `AppResult.Success(data)` et `AppResult.Error(exception)` (mono-argument)
- ✅ Hilt 2.56.1, pattern `@Binds @Singleton abstract fun bind...` (voir `DataModule.kt`)
- ✅ Timber 5.0.1 disponible dans `libs.versions.toml`

### Ce que cette story NE fait PAS

- ❌ Pas de `VpsMistralEngine` (Story 5.1)
- ❌ Pas de `CoachingResolver` ni `OfflineCoachingCache` (Stories 3.3/3.4)
- ❌ Pas de migration Room (aucune nouvelle table)
- ❌ Pas de test JVM pour `GeminiNanoEngine` (nécessite device physique avec AICore)
- ❌ Pas de téléchargement/installation du modèle (géré hors scope par l'OS/AICore)

---

## Technical Requirements

### Dépendance ML Kit — libs.versions.toml

**`android/gradle/libs.versions.toml`** — ajouter :

```toml
[versions]
# ... existant ...
mlkitGenai = "1.0.0-beta2"

[libraries]
# ... existant ...
mlkit-genai-prompt = { group = "com.google.mlkit", name = "genai-prompt", version.ref = "mlkitGenai" }
```

---

### Fichier 1 — `build.gradle.kts` (UPDATE) dans `:core:ai`

**`android/core/ai/build.gradle.kts`** — ajouter la dépendance ML Kit :

```kotlin
dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // ML Kit Generative AI — Gemini Nano on-device
    implementation(libs.mlkit.genai.prompt)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

> ⚠️ `timber` doit être ajouté — `GeminiNanoEngine` l'utilise. Vérifier si `libs.timber` est déjà dans `libs.versions.toml` (il l'est : `timber = "5.0.1"`).

---

### Fichier 2 — `ErrorCode.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/ErrorCode.kt`**

```kotlin
package com.secondserve.domain.model

enum class ErrorCode {
    NETWORK_UNAVAILABLE,
    AUTH_EXPIRED,
    INFERENCE_FAILED,
    SYNC_CONFLICT,
    SESSION_NOT_FOUND
}
```

> Ce fichier est spécifié dans l'architecture (section "Gestion des erreurs Android") mais n'existe pas encore. Cette story est la première à requérir `INFERENCE_FAILED`.

---

### Fichier 3 — `InferenceEngineException.kt` (NEW) dans `:domain`

**`android/domain/src/main/kotlin/com/secondserve/domain/model/InferenceEngineException.kt`**

```kotlin
package com.secondserve.domain.model

class InferenceEngineException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
```

> Permet aux callers (`CoachingResolver` en Story 3.4) d'inspecter le code d'erreur :
> `(result.exception as? InferenceEngineException)?.errorCode == ErrorCode.INFERENCE_FAILED`

---

### Fichier 4 — `GeminiNanoEngine.kt` (NEW)

**`android/core/ai/src/main/kotlin/com/secondserve/core/ai/gemini/GeminiNanoEngine.kt`**

```kotlin
package com.secondserve.core.ai.gemini

import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.ErrorCode
import com.secondserve.domain.model.InferenceEngineException
import timber.log.Timber
import javax.inject.Inject

class GeminiNanoEngine @Inject constructor() : InferenceEngine {

    private val model by lazy { Generation.getClient() }

    override suspend fun generate(prompt: String): AppResult<String> {
        return try {
            val status = model.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Timber.d("GeminiNanoEngine unavailable, falling back")
                return AppResult.Error(
                    InferenceEngineException(
                        ErrorCode.INFERENCE_FAILED,
                        "AICore not available — FeatureStatus: $status"
                    )
                )
            }
            val response = model.generateContent(prompt)
            val text = response.candidates.firstOrNull()?.text
                ?: return AppResult.Error(
                    InferenceEngineException(
                        ErrorCode.INFERENCE_FAILED,
                        "GenerateContentResponse returned no candidates"
                    )
                )
            AppResult.Success(text)
        } catch (e: Exception) {
            Timber.d("GeminiNanoEngine unavailable, falling back")
            AppResult.Error(
                InferenceEngineException(ErrorCode.INFERENCE_FAILED, e.message ?: "Unknown error", e)
            )
        }
    }
}
```

**Points critiques :**
- `Generation.getClient()` = factory ML Kit (pas de Context explicite — ML Kit injecte le contexte applicatif via ContentProvider, pattern standard Firebase/ML Kit)
- `model.checkStatus()` suspend — retourne `@FeatureStatus Int` : `AVAILABLE`, `DOWNLOADABLE`, `DOWNLOADING`, `UNAVAILABLE`
- Seul `FeatureStatus.AVAILABLE` permet d'invoquer `generateContent()`
- `response.candidates.firstOrNull()?.text` — `GenerateContentResponse.candidates: List<Candidate>`, `Candidate.text: String`
- `generateContent(prompt)` peut lancer `GenAiException` (sous-classe d'`Exception`) — capturée par le `catch`
- `lazy` : instance créée une seule fois, réutilisée dans toute la durée de vie du singleton Hilt

---

### Fichier 5 — `AiModule.kt` (DELETE + SPLIT)

**ACTION :** Supprimer `android/app/src/main/kotlin/com/secondserve/di/AiModule.kt`

> ⚠️ Ce fichier DOIT être supprimé avant de créer les variants debug/release. S'il reste dans `src/main/`, Hilt verra deux bindings pour `InferenceEngine` et échouera à la compilation (bug observé en Story 2.6).

---

**`android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt`** (NEW) :

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

---

**`android/app/src/release/kotlin/com/secondserve/di/AiModule.kt`** (NEW) :

```kotlin
package com.secondserve.di

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.gemini.GeminiNanoEngine
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
    abstract fun bindInferenceEngine(impl: GeminiNanoEngine): InferenceEngine
}
```

> Les source sets `src/debug/` et `src/release/` sont actifs par défaut dans Android Gradle — aucune configuration supplémentaire dans `app/build.gradle.kts` n'est requise.

---

## Tasks / Subtasks

### Domain — Nouveau contrat d'erreur

- [x] **Task GN-1** — Créer `ErrorCode.kt` dans `domain/src/main/kotlin/com/secondserve/domain/model/` avec les 5 codes définis en architecture
- [x] **Task GN-2** — Créer `InferenceEngineException.kt` dans `domain/src/main/kotlin/com/secondserve/domain/model/` avec `errorCode: ErrorCode`, `message: String`, `cause: Throwable?`

### Dépendance ML Kit

- [x] **Task GN-3** — Ajouter `mlkitGenai = "1.0.0-beta2"` dans `[versions]` de `libs.versions.toml`
- [x] **Task GN-4** — Ajouter `mlkit-genai-prompt = { group = "com.google.mlkit", name = "genai-prompt", version.ref = "mlkitGenai" }` dans `[libraries]` de `libs.versions.toml`
- [x] **Task GN-5** — Ajouter `implementation(libs.mlkit.genai.prompt)` + `implementation(libs.timber)` dans `core/ai/build.gradle.kts`

### GeminiNanoEngine

- [x] **Task GN-6** — Créer le répertoire `core/ai/src/main/kotlin/com/secondserve/core/ai/gemini/`
- [x] **Task GN-7** — Créer `GeminiNanoEngine.kt` avec `@Inject constructor()`, `model by lazy { Generation.getClient() }`, logique `checkStatus()` + `generateContent()` + `AppResult`

### AiModule split

- [x] **Task GN-8** — SUPPRIMER `android/app/src/main/kotlin/com/secondserve/di/AiModule.kt`
- [x] **Task GN-9** — Créer `android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt` (bind `MockInferenceEngine`)
- [x] **Task GN-10** — Créer `android/app/src/release/kotlin/com/secondserve/di/AiModule.kt` (bind `GeminiNanoEngine`)

### Validation

- [x] **Task GN-11** — Lancer `:app:kspDebugKotlin` — BUILD SUCCESSFUL, aucun conflit Hilt en debug
- [x] **Task GN-12** — Lancer `:app:kspReleaseKotlin` — BUILD SUCCESSFUL, aucun conflit Hilt en release
- [x] **Task GN-13** — Lancer `:core:ai:testDebugUnitTest` — 5 tests `MockInferenceEngineTest` toujours verts (aucune régression)
- [ ] **Task GN-14** — (Sur device Pixel 9 Pro) Test manuel : lancer l'app en release, vérifier `generate("Quel conseil pour ce jeu ?")` répond en ≤ 3s

### Review Findings

- [x] [Review][Patch] CancellationException non relancée dans catch générique [`GeminiNanoEngine.kt:37`] — **Appliqué** : ajout `catch (e: CancellationException) { throw e }` avant le catch générique pour préserver l'annulation de coroutine.
- [x] [Review][Defer] `Thread.sleep(50)` dans `ScoreViewModelTest` — pré-existant (commit 80f8577), workaround race condition Orbit/TestDispatcher.
- [x] [Review][Defer] `@Singleton` + cycle de vie `Generation.getClient()` — ML Kit ContentProvider pattern sûr par design, hors scope.
- [x] [Review][Defer] `FeatureStatus` non exhaustif (`DOWNLOADING` vs `UNAVAILABLE`) — différenciation prévue en Story 3.3/3.4.
- [x] [Review][Defer] Pas de `withTimeout(3000)` — AC6 = validation manuelle sur Pixel 9 Pro.

---

## Dev Notes

### Guardrails critiques

#### ⚠️ Supprimer `AiModule.kt` de `src/main/` AVANT de créer les variants

Si `src/main/di/AiModule.kt` coexiste avec `src/debug/di/AiModule.kt` :
```
[Hilt] Multiple bindings for InferenceEngine
```
Bug structurellement identique à celui observé en Story 2.6 avec `SessionRepository`. Supprimer `src/main/` en premier, puis créer les deux variants.

#### ⚠️ `@Inject constructor` sur `GeminiNanoEngine` — obligatoire

Sans `@Inject`, Hilt ne peut pas construire `GeminiNanoEngine` et le `@Binds` dans `src/release/AiModule.kt` échoue :
```
[Hilt] Cannot inject members into non-@Inject or @Provides annotated type
```
Bug reproduit en Story 2.6 (`CloseMatchUseCase.kt`). Ne pas l'oublier.

#### ⚠️ `AppResult.Error(e)` — mono-argument uniquement

Pattern établi depuis Story 2.4, confirmé 2.6, rappelé en 3.1 :
```kotlin
// ✅ Correct
AppResult.Error(InferenceEngineException(ErrorCode.INFERENCE_FAILED, "..."))
// ❌ Interdit
AppResult.Error(exception, "message string")  // AppResult.Error n'a pas de surcharge 2-args
```

#### ⚠️ `Timber.d(...)` — string exacte requise par AC

L'AC 3 impose exactement :
```kotlin
Timber.d("GeminiNanoEngine unavailable, falling back")
```
Cette string sera recherchée dans les logs lors des tests manuels. Ne pas modifier.

#### ⚠️ `GeminiNanoEngine` dans `src/main/` (pas dans `src/release/`)

`GeminiNanoEngine` doit être dans `core/ai/src/main/` — visible en debug ET en release.
Le binding Hilt dans `src/release/AiModule.kt` le référence.
En debug, `GeminiNanoEngine` existe dans le classpath mais n'est pas instancié par Hilt (le binding debug pointe vers `MockInferenceEngine`).

#### ⚠️ Timber non initialisé en release sans Timber.plant()

Vérifier que `SecondServeApp.kt` contient `Timber.plant(Timber.DebugTree())` en debug. En release, `Timber.d(...)` est un no-op si aucun Tree n'est planté — comportement attendu.

#### ⚠️ ML Kit : `Generation.getClient()` ne nécessite pas de Context

ML Kit utilise le pattern ContentProvider pour injecter le contexte applicatif automatiquement. Pas besoin de passer `context` à `Generation.getClient()`. Si une erreur de type `"ML Kit not initialized"` apparaît, vérifier que `google-services.json` est présent ou que ML Kit n'a pas de prérequis Firebase non satisfaits — mais avec `genai-prompt`, ML Kit est standalone (pas de Firebase).

#### ⚠️ `candidates.firstOrNull()` — défensif mais attendu

En pratique, `generateContent()` retourne toujours au moins un `Candidate` si pas d'exception. Le `firstOrNull()` est défensif. Ne pas utiliser `first()` sans null-check — risque de `NoSuchElementException` si le modèle retourne une liste vide.

### Patterns à réutiliser

| Pattern | Source |
|---------|--------|
| `AppResult.Error(exception)` mono-arg | `MockInferenceEngine.kt` |
| `@Binds @Singleton abstract fun bind...` | `DataModule.kt` (pattern @Binds) |
| `@Inject constructor()` | `MockInferenceEngine.kt` |
| `Timber.d("...")` | `DataLayerClient.kt`, `SessionRepositoryImpl.kt` |
| `by lazy { ... }` pour init Singleton | pattern Kotlin standard |

### ML Kit Generative AI — API Reference rapide

```kotlin
// Package
import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
// (GenerateContentResponse et Candidate importés automatiquement)

// Factory
val model: GenerativeModel = Generation.getClient()

// Check status (suspend)
val status: Int = model.checkStatus()
// Valeurs : FeatureStatus.AVAILABLE, UNAVAILABLE, DOWNLOADABLE, DOWNLOADING

// Generate (suspend, peut lancer GenAiException)
val response = model.generateContent("votre prompt")
val text: String = response.candidates.first().text

// Cleanup (si nécessaire en test)
model.close()
```

### Structure fichiers finale

```
android/
├── domain/
│   └── src/main/kotlin/com/secondserve/domain/model/
│       ├── ErrorCode.kt                               ← NEW
│       └── InferenceEngineException.kt               ← NEW
│
├── gradle/
│   └── libs.versions.toml                            ← UPDATE (mlkitGenai version + lib)
│
├── core/ai/
│   ├── build.gradle.kts                              ← UPDATE (mlkit-genai-prompt + timber)
│   └── src/main/kotlin/com/secondserve/core/ai/
│       ├── InferenceEngine.kt                        ← EXISTE, ne pas modifier
│       ├── mock/
│       │   └── MockInferenceEngine.kt                ← EXISTE, ne pas modifier
│       └── gemini/
│           └── GeminiNanoEngine.kt                   ← NEW
│
└── app/
    └── src/
        ├── main/kotlin/com/secondserve/di/
        │   └── AiModule.kt                           ← SUPPRIMER
        ├── debug/kotlin/com/secondserve/di/
        │   └── AiModule.kt                           ← NEW (MockInferenceEngine)
        └── release/kotlin/com/secondserve/di/
            └── AiModule.kt                           ← NEW (GeminiNanoEngine)
```

### Références

- [Source: epics.md § Story 3.2] — User story et ACs
- [Source: architecture.md § ARCH-8] — "GeminiNanoEngine (production, ML Kit Generative AI APIs)"
- [Source: architecture.md § CI & Testabilité] — "tests d'intégration Gemini Nano nécessitent un device physique Pixel 9 Pro"
- [Source: architecture.md § Gestion des erreurs] — `ErrorCode` enum (INFERENCE_FAILED, etc.)
- [Source: 3-1-*.md § Dev Notes] — Bug `@Inject constructor` manquant, bug binding dupliqué `@Provides`+`@Binds`
- [Source: 3-1-*.md § Dev Notes] — "Story 3.2 devra modifier AiModule.kt pour utiliser GeminiNanoEngine en src/release/ et MockInferenceEngine en src/debug/"
- [ML Kit Prompt API docs] — `com.google.mlkit:genai-prompt:1.0.0-beta2`, `Generation.getClient()`, `checkStatus()`, `generateContent()`, `GenerateContentResponse.candidates[].text`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_À remplir par l'agent de développement._

### Completion Notes List

- GN-1/2 : `ErrorCode.kt` et `InferenceEngineException.kt` créés dans `:domain` — premier usage de `INFERENCE_FAILED`.
- GN-3/4/5 : Dépendance `com.google.mlkit:genai-prompt:1.0.0-beta2` ajoutée dans `libs.versions.toml` et `core/ai/build.gradle.kts`.
- GN-6/7 : `GeminiNanoEngine.kt` créé dans `core/ai/gemini/`. Correction critique : `FeatureStatus` est dans `com.google.mlkit.genai.common` (pas `genai.prompt`) — détecté en inspectant le JAR via `javap`.
- GN-8/9/10 : `AiModule.kt` supprimé de `src/main/`, recréé en split `src/debug/` (MockInferenceEngine) et `src/release/` (GeminiNanoEngine).
- GN-11/12 : `:app:kspDebugKotlin` et `:app:kspReleaseKotlin` — BUILD SUCCESSFUL, aucun conflit Hilt.
- GN-13 : 5/5 tests `MockInferenceEngineTest` verts, 0 régression.
- GN-14 : Test manuel sur Pixel 9 Pro requis — hors scope CI, à valider manuellement.

### File List

- `android/domain/src/main/kotlin/com/secondserve/domain/model/ErrorCode.kt` (NEW)
- `android/domain/src/main/kotlin/com/secondserve/domain/model/InferenceEngineException.kt` (NEW)
- `android/gradle/libs.versions.toml` (UPDATED — mlkitGenai version + mlkit-genai-prompt library)
- `android/core/ai/build.gradle.kts` (UPDATED — mlkit.genai.prompt + timber)
- `android/core/ai/src/main/kotlin/com/secondserve/core/ai/gemini/GeminiNanoEngine.kt` (NEW)
- `android/app/src/main/kotlin/com/secondserve/di/AiModule.kt` (DELETED)
- `android/app/src/debug/kotlin/com/secondserve/di/AiModule.kt` (NEW)
- `android/app/src/release/kotlin/com/secondserve/di/AiModule.kt` (NEW)

## Change Log

- 2026-06-20 : Création story 3.2 — GeminiNanoEngine coaching on-device.
- 2026-06-21 : Implémentation complète — GeminiNanoEngine, split AiModule debug/release, ErrorCode, InferenceEngineException. Fix import FeatureStatus (genai.common vs genai.prompt). BUILD SUCCESSFUL debug+release, 5/5 tests verts.
