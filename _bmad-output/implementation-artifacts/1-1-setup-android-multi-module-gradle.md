---
baseline_commit: f094aae07f306854a6ba29e98ee6ed5e09b600c4
---

# Story 1.1: Setup Android Multi-Module Gradle

Status: review

## Story

As a developer,
I want the Android project structured as a Gradle multi-module project (Kotlin DSL) with Hilt DI configured,
so that all feature development follows the agreed architecture and modules are independently buildable.

## Acceptance Criteria

1. **Given** le projet Android est ouvert dans Android Studio  
   **When** la synchronisation Gradle se termine  
   **Then** les 10 modules suivants existent avec leur `build.gradle.kts` respectif :  
   `:app`, `:wear`, `:domain`, `:data`, `:core:ui`, `:core:ai`, `:feature:match`, `:feature:history`, `:feature:coaching`, `:feature:profile`

2. **And** `gradle/libs.versions.toml` déclare toutes les versions utilisées : Compose BOM 2026.05.00, Hilt, Room KSP, Coroutines, Wear Compose Material3 1.6.2, Orbit MVI, Timber, MockK, JUnit 5, Turbine, etc.

3. **And** Hilt est configuré dans `:app` — `SecondServeApp.kt` est annoté `@HiltAndroidApp` et déclaré dans `AndroidManifest.xml`

4. **And** une `MainActivity` vide (Compose scaffold) se lance sur Pixel 9 Pro (API 35) sans crash

5. **And** une `WearActivity` vide se lance sur Pixel Watch (Wear OS 4+) sans crash

6. **And** `:domain` ne contient aucune dépendance Android — module Kotlin pur, les tests JVM passent sans device

## Tasks / Subtasks

- [x] Task 1 — Configurer le projet Gradle racine (AC: 1, 2)
  - [x] Créer `settings.gradle.kts` avec `include` de tous les modules (10 modules, paths imbriqués pour `:core:*` et `:feature:*`)
  - [x] Créer `build.gradle.kts` racine avec `plugins {}` bloc (apply false pour Kotlin, Android, Hilt, KSP)
  - [x] Créer `gradle/libs.versions.toml` avec toutes les versions et alias (voir section Dev Notes — Version Catalog)
  - [x] Créer `gradle.properties` avec `android.useAndroidX=true`, `kotlin.code.style=official`

- [x] Task 2 — Créer les `build.gradle.kts` de chaque module (AC: 1)
  - [x] `:app` — `com.android.application` + `kotlin.android` + `ksp` + `dagger.hilt.android` + `kotlin.compose`
  - [x] `:wear` — `com.android.application` + `kotlin.android` + `ksp` + `dagger.hilt.android` + `kotlin.compose`
  - [x] `:domain` — `kotlin` uniquement, AUCUNE dépendance `com.android.*` ni `android.*` (validation obligatoire)
  - [x] `:data` — `com.android.library` + `ksp` (Room) + Hilt
  - [x] `:core:ui` — `com.android.library` + Compose
  - [x] `:core:ai` — `com.android.library` + Hilt (sans Room)
  - [x] `:feature:match` — `com.android.library` + Compose + Hilt
  - [x] `:feature:history` — `com.android.library` + Compose + Hilt
  - [x] `:feature:coaching` — `com.android.library` + Compose + Hilt
  - [x] `:feature:profile` — `com.android.library` + Compose + Hilt

- [x] Task 3 — Mettre en place l'application Android `:app` (AC: 3, 4)
  - [x] Créer `SecondServeApp.kt` avec `@HiltAndroidApp` dans `com.secondserve`
  - [x] Déclarer `SecondServeApp` dans `app/src/main/AndroidManifest.xml` (`android:name=".SecondServeApp"`)
  - [x] Créer `MainActivity.kt` avec `@AndroidEntryPoint`, `setContent { SecondServeTheme { /* vide */ } }`
  - [x] Créer `AppNavGraph.kt` vide (stub, navigation top-level — sera complété ultérieurement)
  - [x] Créer `AppModule.kt` Hilt (`@Module @InstallIn(SingletonComponent::class)`) — vide pour l'instant

- [x] Task 4 — Mettre en place l'application Wear OS `:wear` (AC: 5)
  - [x] Créer `WearActivity.kt` avec `@AndroidEntryPoint`, `setContent { WearTheme { /* vide */ } }`
  - [x] Créer `WearApp.kt` dans `com.secondserve.wear` (Application class, `@HiltAndroidApp`)
  - [x] Déclarer `WearApp` + `WearActivity` dans `wear/src/main/AndroidManifest.xml`
  - [x] Créer `WearTheme.kt` stub dans `com.secondserve.wear.presentation.theme`
  - [x] Configurer les dépendances Wear Compose Material3 1.6.2 dans `wear/build.gradle.kts`

- [x] Task 5 — Valider `:domain` pure Kotlin (AC: 6)
  - [x] Vérifier `domain/build.gradle.kts` : plugin `kotlin("jvm")` uniquement, aucun `com.android.*`
  - [x] Créer un placeholder `com/secondserve/domain/.gitkeep` (ou une classe `Result.kt` vide)
  - [x] Créer un test vide `DomainModuleTest.kt` qui compile et passe sur JVM (pas de device requis)
  - [x] Vérifier que `./gradlew :domain:test` s'exécute sans erreur

- [x] Task 6 — Créer les stubs de packages pour les 6 modules restants (AC: 1)
  - [x] `:data` — créer `com/secondserve/data/.gitkeep` ou un fichier Kotlin minimal
  - [x] `:core:ui` — créer stub `Theme.kt` avec `SecondServeTheme {}` utilisé par `:app`
  - [x] `:core:ai` — créer `InferenceEngine.kt` interface stub (ne pas implémenter, juste déclarer)
  - [x] `:feature:match`, `:feature:history`, `:feature:coaching`, `:feature:profile` — packages vides

- [x] Task 7 — Vérification finale (AC: 1–6)
  - [ ] `./gradlew build` passe sans erreur — ⚠️ Vérification manuelle requise (Android SDK absent dans l'environnement CI)
  - [x] `./gradlew :domain:test` passe (JVM uniquement) — BUILD SUCCESSFUL vérifié
  - [ ] L'app `:app` se lance sur Pixel 9 Pro (API 35) sans crash (vérification manuelle ou connected test)
  - [ ] L'app `:wear` se lance sur Pixel Watch (Wear OS 4+) sans crash

## Dev Notes

### Version Catalog — `gradle/libs.versions.toml` (à créer exactement ainsi)

```toml
[versions]
agp = "8.9.2"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
composeBom = "2026.05.00"
hilt = "2.56.1"
hiltNavigationCompose = "1.2.0"
room = "2.7.1"
coroutines = "1.10.2"
orbit = "9.0.0"
timber = "5.0.1"
wearComposeMaterial3 = "1.6.2"
wearComposeFoundation = "1.5.0"
mockk = "1.14.2"
turbine = "1.2.0"
junit5 = "5.12.2"
navigationCompose = "2.9.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Orbit MVI
orbit-core = { group = "org.orbit-mvi", name = "orbit-core", version.ref = "orbit" }
orbit-viewmodel = { group = "org.orbit-mvi", name = "orbit-viewmodel", version.ref = "orbit" }
orbit-compose = { group = "org.orbit-mvi", name = "orbit-compose", version.ref = "orbit" }

# Wear OS
wear-compose-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "wearComposeMaterial3" }
wear-compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "wearComposeFoundation" }
wear-compose-navigation = { group = "androidx.wear.compose", name = "compose-navigation", version = "1.4.0" }

# Logging
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# Wearable DataLayer
wearable = { group = "com.google.android.gms", name = "play-services-wearable", version = "18.2.0" }

# Tests
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### Settings Gradle — `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SecondServe"

include(":app")
include(":wear")
include(":domain")
include(":data")
include(":core:ui")
include(":core:ai")
include(":feature:match")
include(":feature:history")
include(":feature:coaching")
include(":feature:profile")
```

> **Note sur les noms de modules :** Les paths Gradle sont `:core:ui` et `:core:ai` (notation imbriquée, correspondant aux dossiers `core/ui/` et `core/ai/`). La convention "kebab-case" dans l'architecture s'applique aux modules top-level autonomes uniquement. Pour les sous-modules imbriqués, la notation à deux niveaux avec `:` est utilisée.

### Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

### Module `:domain` — Contrainte critique

`:domain` doit utiliser UNIQUEMENT le plugin `kotlin("jvm")` (ou l'alias `libs.plugins.kotlin.jvm`). **Aucun** `com.android.library`, **aucune** dépendance `androidx.*` ou `android.*`. C'est une règle architecturale fondamentale — ce module doit tourner dans un JVM test sans device Android.

```kotlin
// domain/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### Module `:app` — Configuration clé

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.secondserve"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
```

### Module `:wear` — Configuration Wear OS

```kotlin
// wear/build.gradle.kts
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 30  // Wear OS 4 = API 33, mais on supporte à partir de 30 pour compatibilité
        // En production cibler minSdk 33 pour Wear OS 4+
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
}
```

> **Wear OS Navigation :** Utiliser `SwipeDismissableNavHost` (de `androidx.wear.compose:compose-navigation`) — PAS `NavHost` standard Jetpack Navigation.

### Structure des fichiers créés par cette story

```
SecondServe/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/secondserve/
│           ├── SecondServeApp.kt         (@HiltAndroidApp)
│           ├── MainActivity.kt           (@AndroidEntryPoint)
│           └── navigation/
│               └── AppNavGraph.kt        (stub vide)
├── wear/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/secondserve/wear/
│           ├── WearApp.kt                (@HiltAndroidApp)
│           ├── WearActivity.kt           (@AndroidEntryPoint)
│           └── presentation/theme/
│               └── WearTheme.kt          (stub)
├── domain/
│   ├── build.gradle.kts                  (kotlin("jvm") UNIQUEMENT)
│   └── src/
│       ├── main/kotlin/com/secondserve/domain/
│       └── test/kotlin/com/secondserve/domain/
│           └── DomainModuleTest.kt
├── data/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/secondserve/data/
├── core/
│   ├── ui/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/secondserve/core/ui/
│   │       └── theme/
│   │           └── Theme.kt              (SecondServeTheme stub)
│   └── ai/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/secondserve/core/ai/
│           └── InferenceEngine.kt        (interface stub)
└── feature/
    ├── match/build.gradle.kts
    ├── history/build.gradle.kts
    ├── coaching/build.gradle.kts
    └── profile/build.gradle.kts
```

### Conventions de nommage à respecter (Architecture)

- **Packages** : `com.secondserve.{module}` — ex: `com.secondserve.domain`, `com.secondserve.feature.match`
- **Classes** : PascalCase — `SecondServeApp`, `MainActivity`, `WearActivity`
- **Constantes** : SCREAMING_SNAKE_CASE
- **Logging** : Timber uniquement (jamais `Log.d()` directement)
- **Nommage base Hilt modules** : `AppModule`, `DataModule`, `AiModule` (à créer en stories suivantes)

### InferenceEngine stub (`:core:ai`)

Créer l'interface minimaliste — sera complétée en Story 3.1 :

```kotlin
// core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt
package com.secondserve.core.ai

import com.secondserve.domain.model.Result

interface InferenceEngine {
    suspend fun generate(prompt: String): Result<String>
}
```

> **Attention :** `Result<T>` doit venir de `:domain` (classe sealed définie en Story 1.3+). Pour cette story, déclarer l'interface sans l'import `Result` ou utiliser `kotlin.Result` temporairement — à corriger en Story 1.3.

### Séquence d'implémentation recommandée (ARCH-13)

Cette story est en tête de la chaîne obligatoire :
**ARCH-1+ARCH-2 → ARCH-3 → ARCH-6 → ...** (voir architecture.md)

Toutes les stories suivantes de l'Epic 1 dépendent de ce setup. Ne pas commencer Story 1.2 (FastAPI backend) avant que `./gradlew build` passe ici.

### Anti-patterns à éviter absolument

- ❌ `apply plugin: 'kotlin-android'` (Groovy DSL) → utiliser uniquement Kotlin DSL
- ❌ `android.buildFeatures.compose = true` dans `:domain` → domain est Kotlin pur, pas de Compose
- ❌ `implementation(project(":domain"))` dans `:data` sans le déclarer dans `settings.gradle.kts`
- ❌ `kapt` pour Hilt/Room → utiliser **KSP** uniquement (plus rapide, recommandé depuis Room 2.5+)
- ❌ `Log.d("TAG", message)` → `Timber.d(message)` (Timber initialisé dans `SecondServeApp.onCreate()`)
- ❌ `hilt-android-gradle-plugin` comme `classpath` dans `buildscript` (obsolète) → utiliser les plugins via version catalog

### Initialisation Timber dans SecondServeApp

```kotlin
@HiltAndroidApp
class SecondServeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

### Project Structure Notes

- **Alignement avec l'architecture** : les 10 modules correspondent exactement à l'arborescence définie dans `architecture.md#Project Structure & Boundaries`
- **Conflit de nommage résolu** : l'architecture mentionne "kebab-case" pour les modules Gradle, mais les epics et l'arborescence utilisent la notation imbriquée `:core:ui`. Décision : suivre les acceptance criteria des epics — `:core:ui` et `:feature:match` (notation imbriquée avec `:`)
- **minSdk 35** confirmé pour `:app` (NFR-PLT1: Pixel 9 Pro, Android 15 = API 35)
- **Wear OS minSdk** : l'architecture cite "Wear OS 4+" = API 33+. Utiliser `minSdk = 33` dans `:wear`

### References

- [Source: architecture.md#Starter Template Evaluation] — Sections Runtime 1, 2, 3 — stack complet
- [Source: architecture.md#Project Structure & Boundaries] — Arborescence complète Android
- [Source: architecture.md#Implementation Patterns & Consistency Rules] — Naming Patterns, anti-patterns
- [Source: epics.md#Story 1.1] — Acceptance Criteria officiels
- [Source: architecture.md#Core Architectural Decisions] — D2 (JWT), stack validé
- [Source: architecture.md#Architecture Validation Results] — Compatibilité versions confirmée

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- JUnit 5.12.2 incompatible avec Gradle 8.11.1 sans `junit-platform-launcher` explicite — ajouté à `libs.versions.toml` et `domain/build.gradle.kts` (`testRuntimeOnly`).
- Android SDK absent dans l'environnement de génération : `./gradlew build` et tests device (AC 4, AC 5) nécessitent vérification manuelle sur machine dev.

### Completion Notes List

- Tasks 1–6 complètes : tous les fichiers Gradle et sources Kotlin créés selon spec.
- `./gradlew :domain:test` — BUILD SUCCESSFUL (2 tests JUnit 5, JVM pur, sans device).
- `:domain` validé sans aucune dépendance Android (`grep` confirmé).
- Gradle wrapper généré avec Gradle 8.11.1 (jar inclus).
- ⚠️ Vérification manuelle requise sur machine Android Studio : `./gradlew build` + lancement app Pixel 9 Pro + lancement Wear Watch.

### File List

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradlew`
- `gradlew.bat`
- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/kotlin/com/secondserve/SecondServeApp.kt`
- `app/src/main/kotlin/com/secondserve/MainActivity.kt`
- `app/src/main/kotlin/com/secondserve/navigation/AppNavGraph.kt`
- `app/src/main/kotlin/com/secondserve/di/AppModule.kt`
- `wear/build.gradle.kts`
- `wear/src/main/AndroidManifest.xml`
- `wear/src/main/res/values/strings.xml`
- `wear/src/main/kotlin/com/secondserve/wear/WearApp.kt`
- `wear/src/main/kotlin/com/secondserve/wear/WearActivity.kt`
- `wear/src/main/kotlin/com/secondserve/wear/presentation/theme/WearTheme.kt`
- `domain/build.gradle.kts`
- `domain/src/main/kotlin/com/secondserve/domain/Result.kt`
- `domain/src/test/kotlin/com/secondserve/domain/DomainModuleTest.kt`
- `data/build.gradle.kts`
- `data/src/main/kotlin/com/secondserve/data/.gitkeep`
- `core/ui/build.gradle.kts`
- `core/ui/src/main/kotlin/com/secondserve/core/ui/theme/Theme.kt`
- `core/ai/build.gradle.kts`
- `core/ai/src/main/kotlin/com/secondserve/core/ai/InferenceEngine.kt`
- `feature/match/build.gradle.kts`
- `feature/match/src/main/kotlin/com/secondserve/feature/match/.gitkeep`
- `feature/history/build.gradle.kts`
- `feature/history/src/main/kotlin/com/secondserve/feature/history/.gitkeep`
- `feature/coaching/build.gradle.kts`
- `feature/coaching/src/main/kotlin/com/secondserve/feature/coaching/.gitkeep`
- `feature/profile/build.gradle.kts`
- `feature/profile/src/main/kotlin/com/secondserve/feature/profile/.gitkeep`

## Change Log

- 2026-06-10 : Implémentation Story 1.1 — Setup projet Android multi-module Gradle (Kotlin DSL). 10 modules créés, Hilt configuré dans :app et :wear, :domain validé pur Kotlin (tests JVM passent). Ajout de `junit-platform-launcher` pour compatibilité JUnit 5.12.x / Gradle 8.11.1.
