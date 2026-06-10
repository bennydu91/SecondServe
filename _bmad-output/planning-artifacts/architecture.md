---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - "_bmad-output/planning-artifacts/prds/prd-SecondServe-2026-06-08/prd.md"
  - "_bmad-output/planning-artifacts/prds/prd-SecondServe-2026-06-08/addendum.md"
  - "_bmad-output/brainstorming/brainstorming-session-2026-06-08-1430.md"
workflowType: 'architecture'
lastStep: 8
status: 'complete'
completedAt: '2026-06-09'
project_name: 'SecondServe'
user_name: 'Benny'
date: '2026-06-09'
---

# Architecture Decision Document

_Ce document se construit collaborativement, étape par étape. Les sections sont ajoutées au fil des décisions architecturales prises ensemble._

---

## Project Context Analysis

### Requirements Overview

**Functional Requirements : 16 FRs en 4 features**

| Feature | FRs | Résumé |
|---|---|---|
| Mode Match — Coaching temps réel | FR-1 à FR-6 | Démarrage session, score Pixel Watch, coaching changement de côté, Gemini Nano, affichage téléphone, clôture |
| Suivi & Historique | FR-7 à FR-9 | Historique sessions, stats agrégées (win rate, surfaces), saisie rétrospective |
| Coaching IA hors match | FR-10 à FR-13 | Analyse post-match, synthèse multi-matchs (≥3), axes de travail, notifications push |
| Profil joueur | FR-14 à FR-16 | Classement FFT (historique), style de jeu inféré, consignes coach humain |

**Non-Functional Requirements — contraintes architecturales fortes :**

| ID | Contrainte | Impact architectural |
|---|---|---|
| NFR-P1 | Conseil Gemini Nano ≤ 3s | Prompt minimal, inférence on-device, pas de round-trip réseau |
| NFR-P2 | Score Pixel Watch ≤ 500ms | Logique score locale sur la montre, pas de relay téléphone |
| NFR-P3 | Historique (<200 sessions) ≤ 1s | Index Room optimisé, requêtes paginées |
| NFR-OFF1 | Mode Match 100% offline | Gemini Nano + OfflineCoachingCache, aucune dépendance réseau en match |
| NFR-OFF3 | Queue offline → sync auto | WorkManager, retry policy, idempotence des opérations |
| NFR-C3 | Licence FFT jamais transmise à Mistral | Payload Mistral limité par design : ranking + noms adversaires uniquement |
| NFR-UX4 *(ajout)* | Coaching pré-calculé à `game_over` | Gemini Nano déclenché en background à la fin du jeu, tap = révélation, pas requête |

**Scale & Complexity :**

- Domaine primaire : Android mobile + Wear OS wearable + IA hybride (on-device/cloud) + Backend VPS
- Complexité : **Élevée** — 3 runtimes distincts, coordination watch/phone/serveur, offline-first comme mode par défaut en match
- Composants architecturaux estimés : ~12 modules (score engine, AI context builder, DataLayer bridge, offline coaching cache, sync queue, Mistral client, Gemini Nano adapter, Room repositories, FastAPI backend, notification scheduler, player profile state, coaching resolver)

### Technical Constraints & Dependencies

- **Plateforme cible unique :** Pixel 9 Pro (API 35) + Pixel Watch (Wear OS 4+) — aucune fragmentation à gérer
- **Android AICore / Gemini Nano :** API stable (ML Kit Generative AI APIs), disponible sur Pixel 9 Pro — *non expérimental, confirmé sur developer.android.com/ai/gemini-nano*. Pas de mise à jour automatique sans intervention manuelle sur le Play Store.
- **Mistral API :** Appels réseau, latence cible ≤ 10s (NFR-P4), payload limité par design (ranking + noms adversaires)
- **Wearable DataLayer :** Communication Bluetooth, portée ~10m. En usage réel (montre au poignet, téléphone dans le sac sur le court), risque de coupure nul. Bouton retry sur la montre en cas d'incident.
- **FFT :** Aucune API publique — saisie manuelle uniquement
- **VPS personnel :** 4 vCPU / 16 Go RAM, mono-utilisateur, pas de scalabilité horizontale requise

### Source de vérité — principe fondamental

**La Pixel Watch est la source de vérité unique du score pendant un match actif.**

Le téléphone est un récepteur passif : il reçoit le score via DataLayer et l'utilise comme contexte de coaching uniquement. Il ne maintient pas d'état de score indépendant, ne réconcilie pas, n'infère pas. Si aucune donnée DataLayer n'est reçue, il attend.

Conséquence directe : le `ScoreRepository` côté téléphone est un cache read-only. Aucune logique de réconciliation n'est requise.

### Cross-Cutting Concerns Identifiés

1. **Construction du contexte IA (prompt context)** — Le Profil joueur, les Axes de travail et l'historique récent alimentent Gemini Nano ET Mistral API. La logique de sérialisation est partagée et doit rester cohérente. Payload Mistral = ranking FFT + noms adversaires uniquement (PII par design).

2. **Gestion offline & sync queue** — Toute opération d'écriture (session, score, profil) passe par la queue WorkManager. Affecte tous les modules de données.

3. **Communication Watch ↔ Téléphone (DataLayer)** — Contrat unidirectionnel principal : `Watch → Phone: ScoreEvent`, `Phone → Watch: CoachingResult`. Schéma de messages à définir (protobuf ou JSON, champs : timestamp, score_snapshot / mantra_id, generated_text, ttl).

4. **OfflineCoachingCache** — Cache Room AI-généré, keyed par `MatchPattern`. Deux cycles d'invocation distincts : init-match (tous patterns, async non-bloquant) et post-changeover (patterns probables uniquement, background). `CoachingResolver` = unique point de contrôle lecture/écriture du cache. Voir détail section dédiée ci-dessous.

5. **Moteur de règles tennis (TennisScoreEngine)** — Logique de score (points, jeux, sets, tie-break, super tie-break, changements de côté, correction de point) : module isolé sur la Watch, testable unitairement. Automate à états finis avec historique permettant la correction de point (annulation de transition d'état).

6. **Isolation PII** — Numéro de licence FFT stocké localement uniquement, jamais inclus dans aucun appel sortant. Payload Mistral limité par design.

### Architecture OfflineCoachingCache — Détail

Le fallback offline n'est pas une bibliothèque statique. C'est un cache dynamique de contenu AI-généré, structuré par patterns de match prédéfinis.

**Principe :**
- À la création du match (avant le premier point), l'IA génère du contenu coaching pour chaque `MatchPattern` → stocké en cache Room local
- Après chaque changeover (une fois le coaching online délivré, quand le jeu reprend), le cache est rafraîchi en background pour les patterns probables selon l'état courant du match
- À l'affichage fallback : `PatternDetector.detect(state)` → lookup O(1) dans le cache

**MatchPattern — liste fermée (~15-30 patterns, versionnée dans l'app) :**

Exemples : `SERVICE_HELD_UNDER_PRESSURE`, `BREAK_CONFIRMED`, `BREAK_LOST_AFTER_HOLD`, `SET_WON_DOMINANT`, `SET_LOST_CLOSE`, `DOUBLE_FAULT_CLUSTER`, `TIEBREAK_APPROACHING`, `MATCH_POINT_APPROACHING`, `NEUTRAL_TRANSITION`, etc.

*La définition exhaustive de cette liste est un prérequis architectural — à figer avant l'implémentation du cache.*

**Composants :**

| Composant | Responsabilité |
|---|---|
| `CoachingPatternDetector` | `MatchStateSnapshot → MatchPattern` courant |
| `CoachingCacheRepository` | Persistence Room (match_id, pattern, content, generated_at, is_stale) |
| `CoachingContentGenerator` | Interface LLM — `generateForPatterns(patterns, context)` |
| `CoachingCachePrefetcher` | Orchestre init + refresh post-changeover |
| `CoachingResolver` | **Point unique** de décision online vs cache, point unique d'écriture cache |

**Règles de staleness :** un pattern non rafraîchi reste lisible (marqué stale, jamais supprimé). Pas de retry agressif — on accepte un cache légèrement vieux plutôt que de bloquer.

**Dernier filet :** `GENERIC_FALLBACK_TEXTS: Map<MatchPattern, String>` hardcodé dans les ressources — retourné si le cache est vide pour un pattern donné.

**Deux budgets d'invocation LLM distincts :**
- Init-match : génération complète, tous patterns, latence acceptable, async
- Post-changeover : refresh incrémental, patterns probables uniquement, non-bloquant strict

### CI & Testabilité

- **InferenceEngine** exposé comme interface avec deux implémentations : `GeminiNanoEngine` (prod) et `MockInferenceEngine` (test/CI)
- L'émulateur Android ne supporte pas AICore — les tests d'intégration Gemini Nano nécessitent un device physique Pixel 9 Pro; les tests unitaires et CI utilisent `MockInferenceEngine`
- **TennisScoreEngine** : module isolé, 100% testable unitairement sans device (automate à états finis, logique pure)
- **CoachingPatternDetector** : déterministe — même `MatchStateSnapshot` → même `MatchPattern`, testable sans LLM

---

## Starter Template Evaluation

### Domaine primaire

Native Mobile multi-runtime (Android + Wear OS) + Backend API Python.
Pas de starter CLI web applicable — le "starter" est la configuration initiale des 3 projets/modules.

### Runtime 1 — Android (app principale)

**Template :** Android Studio — Empty Activity (Jetpack Compose) en projet multi-module Gradle Kotlin DSL

**Décisions architecturales établies :**

- **Language & Runtime :** Kotlin, API 35 (minSdk 35), Compose BOM 2026.05.00
- **Architecture :** MVI (Orbit) pour modules à état complexe (Match Mode, OfflineCoachingCache) ; MVVM pour modules simples (Historique, Profil). Orbit MVI retenu pour sa compatibilité progressive MVVM → MVI.
- **UI :** Jetpack Compose + Material 3, pas de XML
- **Persistence :** Room avec KSP, Kotlin Coroutines + Flow
- **Background :** WorkManager (sync queue, refresh post-changeover)
- **DI :** Hilt
- **Tests :** JUnit 5 + Turbine (Flow testing) + MockK

**Structure modules Gradle :**

```
:app          → point d'entrée Android, navigation top-level
:wear         → Wear OS companion (Compose for Wear)
:domain       → logique métier pure (TennisScoreEngine, MatchContextProfile,
                CoachingResolver) — zéro dépendance Android
:data         → Room, repositories, WorkManager, DataLayer client
:core:ui      → composants Compose partagés, thème Material 3
:core:ai      → InferenceEngine interface + GeminiNanoEngine + MistralClient
```

### Runtime 2 — Wear OS (companion)

**Template :** module `:wear` dans le même projet multi-module — Android Studio "Wear OS Blank Activity"

**Décisions architecturales établies :**

- **UI :** Wear Compose Material 3 v1.6.2 (`androidx.wear.compose:compose-material3`)
- **Navigation :** `SwipeDismissableNavHost` (Wear OS — pas de NavHost standard)
- **Listes :** `TransformingLazyColumn` (scaling + transparency natifs écran rond)
- **Communication :** Wearable DataLayer API (DataClient) — Bluetooth uniquement V1
- **Architecture :** MVI (Orbit) — même pattern que le module Match Mode Android

### Runtime 3 — Backend VPS (FastAPI)

**Template :** structure feature/domain-based (recommandation production 2026)

**Commande d'initialisation :**

```bash
uv init secondserve-backend
uv add fastapi[standard] sqlalchemy[asyncio] alembic pydantic-settings
```

**Structure projet :**

```
secondserve-backend/
├── app/
│   ├── main.py
│   ├── api/v1/           → routes HTTP (sessions, profile, coaching, sync)
│   ├── core/             → settings, sécurité, config
│   ├── db/               → SQLAlchemy models, session, Alembic migrations
│   ├── features/
│   │   ├── sessions/     → router, service, repository, schemas
│   │   ├── profile/
│   │   ├── coaching/     → appels Mistral API, stockage analyses
│   │   └── sync/         → delta sync, résolution de conflits
│   └── workers/          → tâches background (génération analyses Mistral)
├── tests/
├── alembic/
└── pyproject.toml
```

- **Language :** Python 3.12+, FastAPI + Pydantic v2
- **DB :** SQLite via SQLAlchemy async (mono-utilisateur, pas de PostgreSQL requis)
- **Migrations :** Alembic
- **Tests :** pytest + httpx (async test client)
- **Déploiement :** Systemd service sur VPS — Docker non requis (mono-utilisateur, simplicité prioritaire)

### Note d'initialisation

Les 3 setups constituent les premières stories d'implémentation :
1. Setup projet Android multi-module (Gradle Kotlin DSL + modules skeleton)
2. Setup module Wear OS companion
3. Setup backend FastAPI

---

## Core Architectural Decisions

### Tableau des décisions

| # | Décision | Choix | Rationale |
|---|---|---|---|
| D1 | Appels Mistral API | App → VPS → Mistral | Clé API sécurisée côté serveur, remplaçable sans rebuild, contrôle des coûts centralisé |
| D2 | Auth Android ↔ VPS | JWT Token (EncryptedSharedPreferences) | Révocable sans rebuild, adapté mono-utilisateur |
| D3 | Format DataLayer | JSON | Lisible, debuggable, zéro dépendance supplémentaire, suffisant pour payloads légers |
| D4 | Notifications | WorkManager local + VPS APScheduler | Zéro FCM, offline-safe, contenu Mistral injecté si réseau disponible |
| D5 | Modèle Mistral | mistral-small-latest | Latence ~2-4s, NFR-P4 ≤10s largement respecté, coût minimal |

### Décisions différées (Post-MVP)

| Sujet | Raison du report |
|---|---|
| Upgrade mistral-large pour synthèses | Réévaluation sur usage réel |
| FCM / notifications push serveur | Non nécessaire — WorkManager suffit en V1 |

---

### Data Architecture

**Room (Android local)**
- SQLite via Room + KSP
- Tables principales : `Session`, `Point`, `CoachingCache`, `WorkAxis`, `PlayerProfile`, `RankingHistory`, `SyncQueue`
- Index optimisés pour stats (`win_rate` by surface = filtre `session_type=MATCH AND surface=X`)
- Migrations via Room `Migration` objects — pas de `fallbackToDestructiveMigration` en production

**VPS (SQLite via SQLAlchemy async)**
- Mêmes entités que côté Android
- Alembic pour les migrations
- Source de vérité pour les données synchronisées (last-write-wins sur `updated_at`)

**Sync delta**
- Chaque entité porte `updated_at` (epoch ms) et `sync_version`
- `SyncWorker` WorkManager : contrainte `NetworkType.CONNECTED` (data mobile accepté)
- Queue locale offline : table `SyncQueue` Room, rejouée au retour réseau

---

### Authentication & Security

**JWT Android ↔ VPS**
- Premier lancement : `POST /api/v1/auth/init` → VPS génère et retourne un JWT signé
- Token stocké dans `EncryptedSharedPreferences` (Android Keystore backed)
- Header `Authorization: Bearer <token>` sur tous les appels REST
- Middleware `JWTBearer` sur toutes les routes `/api/v1/**` sauf `/auth/init`
- Secret JWT : variable d'environnement `JWT_SECRET` sur le VPS

**PII**
- Numéro de licence FFT : `EncryptedSharedPreferences` uniquement, jamais transmis
- Payload Mistral via VPS : `{fft_ranking, opponent_name, surface, format, coaching_context}` — pas d'identifiant personnel

---

### API & Communication

**REST VPS (FastAPI)**
- Versioning : `/api/v1/`
- Format : JSON, Pydantic v2
- Erreurs : schéma uniforme `{error_code, message, detail}`
- Routes principales :
  - `POST /auth/init` — initialisation JWT
  - `POST /sync/push` — delta sync Android → VPS
  - `GET /sync/pull` — delta sync VPS → Android
  - `POST /coaching/analyze` — analyse post-match (VPS → Mistral)
  - `POST /coaching/patterns` — génération coaching cache init (VPS → Mistral)
  - `GET /coaching/{session_id}` — récupérer analyse générée
  - `GET /notifications/pending` — récupérer événements coaching pré-match générés par APScheduler

**DataLayer Watch ↔ Phone (JSON)**

Paths et schémas :
```
/secondserve/score_event
  { "type": "SCORE_EVENT", "ts": 1234567890, "score": { "sets": [], "current_game": "40-30", "server": "SELF" } }

/secondserve/game_over
  { "type": "GAME_OVER", "ts": 1234567890, "score_snapshot": { ... } }

/secondserve/coaching_result
  { "type": "COACHING_RESULT", "ts": 1234567890, "text": "...", "source": "GEMINI|CACHE|STATIC", "ttl": 90 }
```

---

### Notifications (D4 révisée)

| Mécanisme | Usage |
|---|---|
| WorkManager `OneTimeWorkRequest` | Rappel avant match planifié — délai calculé à la création du match |
| WorkManager `PeriodicWorkRequest` | Conseil du jour (fréquence configurable par l'utilisateur) |
| VPS APScheduler + polling | Génération contenu coaching pré-match via Mistral, injecté dans la notification si réseau disponible |

Prérequis : création d'un match accepte une date/heure future (match planifié).

---

### Infrastructure & Déploiement

**VPS**
- FastAPI via Systemd (`secondserve-backend.service`)
- Nginx reverse proxy + HTTPS Let's Encrypt
- Variables d'environnement : `JWT_SECRET`, `MISTRAL_API_KEY`, `DATABASE_URL`
- APScheduler intégré à FastAPI pour les tâches planifiées (notifications pré-match, synthèses)
- Logs : `logging` Python rotatif + Systemd journal

**Android APK**
- Distribution : sideload direct (pas de Play Store V1)
- Build Gradle release signé localement
- Pas de CI/CD automatisé (projet solo)

**Mistral (côté VPS)**
- Modèle : `mistral-small-latest`
- Client : `httpx` async
- Timeout : 15s, 1 retry sur timeout

---

### Séquence d'implémentation (dépendances)

1. Setup multi-module Android (Gradle Kotlin DSL + modules skeleton)
2. Setup FastAPI + JWT auth + Nginx
3. TennisScoreEngine (`:domain`) — automate états finis
4. DataLayer bridge (`:data`) — contrat JSON Watch↔Phone
5. Room schema + repositories (`:data`)
6. GeminiNano InferenceEngine (`:core:ai`)
7. OfflineCoachingCache + CoachingResolver
8. VPS routing Mistral — endpoints coaching
9. WorkManager SyncWorker
10. Notifications WorkManager + VPS APScheduler

---

## Implementation Patterns & Consistency Rules

### Points de conflit identifiés : 10 catégories adressées

---

### Naming Patterns

**Conventions base de données (Room + SQLAlchemy)**

| Élément | Convention | Exemple |
|---|---|---|
| Tables | snake_case, pluriel | `sessions`, `work_axes`, `ranking_history` |
| Colonnes | snake_case | `session_type`, `updated_at`, `is_stale` |
| Clés étrangères | `{table_singulier}_id` | `session_id`, `match_id` |
| Index | `idx_{table}_{colonne}` | `idx_sessions_surface` |
| Migrations Room | `Migration_{from}_{to}` | `Migration_1_2` |

**Conventions REST VPS (FastAPI)**

| Élément | Convention | Exemple |
|---|---|---|
| Ressources | pluriel, snake_case | `/sessions`, `/work_axes` |
| Paramètres URL | `{ressource_singulier_id}` | `{session_id}`, `{profile_id}` |
| Query params | snake_case | `?updated_since=...` |
| Sous-ressources | imbriquées | `/sessions/{session_id}/coaching` |

**Conventions Kotlin (Android + Wear OS)**

| Élément | Convention | Exemple |
|---|---|---|
| Classes / data classes / sealed classes | PascalCase | `MatchScreenState`, `CoachingResult` |
| Interfaces | PascalCase | `InferenceEngine`, `CoachingRepository` |
| Fonctions / propriétés | camelCase | `detectPattern()`, `currentScore` |
| Constantes | SCREAMING_SNAKE_CASE | `MAX_WORK_AXES`, `DATAPATH_SCORE_EVENT` |
| Fichiers de classes | PascalCase | `MatchViewModel.kt` |
| Fichiers singletons / utils / extensions | camelCase | `dateUtils.kt`, `flowExtensions.kt` |
| Modules Gradle | kebab-case | `:core-ui`, `:core-ai` |
| Packages | lowercase | `com.secondserve.domain.model` |

**Conventions Python (FastAPI)**

| Élément | Convention | Exemple |
|---|---|---|
| Fonctions / variables / paramètres | snake_case | `get_session()`, `session_id` |
| Classes | PascalCase | `SessionSchema`, `CoachingService` |
| Constantes module | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Fichiers / modules | snake_case | `coaching_service.py`, `session_router.py` |

---

### Format Patterns

**Réponses API REST — format direct**

```json
// Objet unique
{ "id": "abc123", "surface": "CLAY", "created_at": "2026-06-09" }

// Liste
{ "items": [...], "total": 42 }

// Erreur
{ "error_code": "SESSION_NOT_FOUND", "message": "Session introuvable", "detail": null }
```

- Pas de wrapper `{ "data": ... }` — réponse directe
- Listes toujours dans `{ "items": [...], "total": N }`
- `error_code` en SCREAMING_SNAKE_CASE, `message` en français (user-facing)
- HTTP codes : 200 succès, 201 création, 400 validation, 401 auth, 404 not found, 500 erreur serveur

**Formats dates / timestamps**

| Contexte | Format | Exemple |
|---|---|---|
| Payloads sync (`updated_at`, `ts`) | Epoch millisecondes (Long) | `1749470400000` |
| Dates métier affichées (date de match) | ISO 8601 date seule | `"2026-06-09"` |
| Timestamps logs/audit | ISO 8601 avec timezone | `"2026-06-09T14:30:00Z"` |

**JSON DataLayer Watch ↔ Phone**

- Champ `type` : SCREAMING_SNAKE_CASE (`"SCORE_EVENT"`, `"GAME_OVER"`, `"COACHING_RESULT"`)
- Champ `ts` : epoch ms (Long)
- Champ `source` coaching : `"GEMINI"` | `"CACHE"` | `"STATIC"`

---

### Communication Patterns

**États MVI (Orbit) — règle hybride**

```kotlin
// États mutuellement exclusifs → sealed class
sealed class MatchUiState {
    object Idle : MatchUiState()
    object Loading : MatchUiState()
    data class Active(val score: MatchScore, val phase: MatchPhase) : MatchUiState()
    data class Error(val message: String) : MatchUiState()
}

// États partiels / composites → data class avec defaults
data class CoachingUiState(
    val isLoading: Boolean = false,
    val analyses: List<CoachingAnalysis> = emptyList(),
    val synthesis: CoachingSynthesis? = null,
    val error: String? = null
)
```

**Side Effects Orbit — convention `{Action}{Sujet}` PascalCase**

```kotlin
sealed class MatchSideEffect {
    data class ShowCoachingAdvice(val text: String) : MatchSideEffect()
    object NavigateToSummary : MatchSideEffect()
    data class ShowError(val message: String) : MatchSideEffect()
}
```

---

### Process Patterns

**Gestion des erreurs Android — sealed Result\<T\>**

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Throwable,
        val message: String,
        val errorCode: ErrorCode? = null
    ) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

enum class ErrorCode {
    NETWORK_UNAVAILABLE, AUTH_EXPIRED, INFERENCE_FAILED,
    SYNC_CONFLICT, SESSION_NOT_FOUND
}
```

- Tous les appels réseau / IA retournent `Result<T>` — jamais d'exception non catchée
- Les ViewModels exposent `StateFlow<Result<T>>` ou `StateFlow<UiState>` — jamais les deux
- Les `Error` loggent via Timber en DEBUG, affichent `message` à l'utilisateur

**Logging**

```kotlin
// Android — Timber (tag automatique, désactivé en release)
Timber.d("CoachingCache init: %d patterns generated", count)
Timber.e(exception, "InferenceEngine failed, falling back to cache")
// JAMAIS Log.d() / Log.e() directement
```

```python
# Python VPS — un logger par module
import logging
logger = logging.getLogger(__name__)
logger.info("Coaching analysis generated: session_id=%s", session_id)
logger.error("Mistral API failed: %s", exc, exc_info=True)
# LOG_LEVEL=INFO en prod, DEBUG en dev
```

---

### Enforcement

**Tout agent IA DOIT :**

- Respecter les conventions de nommage ci-dessus pour le runtime concerné
- Retourner `Result<T>` pour tout appel réseau / IA côté Android
- Utiliser `Timber` (jamais `Log.*` direct) côté Android
- Utiliser `logging.getLogger(__name__)` côté Python (jamais `print()`)
- Utiliser epoch ms pour `updated_at` / `ts`, ISO 8601 pour les dates métier affichées
- Préfixer les paths DataLayer avec `/secondserve/`
- Envelopper les listes REST dans `{ "items": [...], "total": N }`

**Anti-patterns interdits :**

- ❌ `Log.d("TAG", message)` → `Timber.d(message)`
- ❌ `print(...)` Python → `logger.info(...)`
- ❌ Timestamps en secondes dans les payloads sync → epoch **millisecondes**
- ❌ Tables Room au singulier (`session`) → toujours pluriel (`sessions`)
- ❌ Endpoints REST au singulier (`/session`) → toujours pluriel (`/sessions`)
- ❌ `try/catch` sans `Result.Error` → tout appel réseau/IA retourne `Result<T>`
- ❌ `UiState` nullable → initialiser avec des defaults non-null

---

## Project Structure & Boundaries

### Arborescence complète — Android (multi-module)

```
SecondServe/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── gradle.properties
│
├── app/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/secondserve/
│       ├── SecondServeApp.kt
│       ├── MainActivity.kt
│       └── navigation/
│           └── AppNavGraph.kt
│
├── wear/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/secondserve/wear/
│       ├── WearApp.kt
│       ├── WearActivity.kt
│       └── presentation/
│           ├── match/
│           │   ├── ScoreScreen.kt
│           │   ├── ScoreViewModel.kt
│           │   ├── CoachingScreen.kt
│           │   └── CoachingViewModel.kt
│           └── theme/
│               ├── WearTheme.kt
│               └── WearColor.kt
│
├── domain/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/secondserve/domain/
│       │   ├── model/
│       │   │   ├── Session.kt
│       │   │   ├── MatchScore.kt
│       │   │   ├── MatchPattern.kt
│       │   │   ├── MatchContextProfile.kt
│       │   │   ├── CoachingCacheEntry.kt
│       │   │   ├── PlayerProfile.kt
│       │   │   ├── WorkAxis.kt
│       │   │   ├── RankingEntry.kt
│       │   │   └── Result.kt
│       │   ├── engine/
│       │   │   ├── TennisScoreEngine.kt
│       │   │   └── CoachingPatternDetector.kt
│       │   ├── usecase/
│       │   │   ├── match/
│       │   │   │   ├── StartMatchUseCase.kt
│       │   │   │   ├── RecordPointUseCase.kt
│       │   │   │   └── CloseMatchUseCase.kt
│       │   │   ├── coaching/
│       │   │   │   ├── GetCoachingAdviceUseCase.kt
│       │   │   │   ├── RefreshCoachingCacheUseCase.kt
│       │   │   │   └── GeneratePostMatchAnalysisUseCase.kt
│       │   │   ├── profile/
│       │   │   │   └── UpdatePlayerProfileUseCase.kt
│       │   │   └── sync/
│       │   │       └── SyncDataUseCase.kt
│       │   └── repository/
│       │       ├── SessionRepository.kt
│       │       ├── CoachingRepository.kt
│       │       ├── PlayerProfileRepository.kt
│       │       └── SyncRepository.kt
│       └── test/kotlin/com/secondserve/domain/
│           ├── engine/
│           │   └── TennisScoreEngineTest.kt
│           └── usecase/
│               └── RecordPointUseCaseTest.kt
│
├── data/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/secondserve/data/
│       │   ├── local/
│       │   │   ├── db/
│       │   │   │   ├── SecondServeDatabase.kt
│       │   │   │   └── entity/
│       │   │   │       ├── SessionEntity.kt
│       │   │   │       ├── PointEntity.kt
│       │   │   │       ├── CoachingCacheEntity.kt
│       │   │   │       ├── WorkAxisEntity.kt
│       │   │   │       ├── PlayerProfileEntity.kt
│       │   │   │       ├── RankingHistoryEntity.kt
│       │   │   │       └── SyncQueueEntity.kt
│       │   │   └── dao/
│       │   │       ├── SessionDao.kt
│       │   │       ├── CoachingCacheDao.kt
│       │   │       ├── PlayerProfileDao.kt
│       │   │       └── SyncQueueDao.kt
│       │   ├── remote/
│       │   │   ├── api/
│       │   │   │   ├── VpsApiService.kt
│       │   │   │   └── dto/
│       │   │   │       ├── SessionDto.kt
│       │   │   │       ├── CoachingDto.kt
│       │   │   │       └── SyncDto.kt
│       │   │   └── auth/
│       │   │       └── JwtTokenStore.kt
│       │   ├── wearable/
│       │   │   ├── DataLayerClient.kt
│       │   │   └── DataLayerListener.kt
│       │   ├── worker/
│       │   │   ├── SyncWorker.kt
│       │   │   ├── CoachingCacheWorker.kt
│       │   │   └── NotificationWorker.kt
│       │   └── repository/
│       │       ├── SessionRepositoryImpl.kt
│       │       ├── CoachingRepositoryImpl.kt
│       │       ├── PlayerProfileRepositoryImpl.kt
│       │       └── SyncRepositoryImpl.kt
│       └── test/kotlin/com/secondserve/data/
│           └── repository/
│               └── SessionRepositoryTest.kt
│
├── core/
│   ├── ui/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/secondserve/core/ui/
│   │       ├── theme/
│   │       │   ├── Theme.kt
│   │       │   ├── Color.kt
│   │       │   └── Typography.kt
│   │       └── components/
│   │           ├── ScoreDisplay.kt
│   │           ├── CoachingCard.kt
│   │           ├── SessionListItem.kt
│   │           └── LoadingIndicator.kt
│   └── ai/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/kotlin/com/secondserve/core/ai/
│           │   ├── InferenceEngine.kt
│           │   ├── gemini/
│           │   │   └── GeminiNanoEngine.kt
│           │   ├── vps/
│           │   │   └── VpsMistralEngine.kt
│           │   └── mock/
│           │       └── MockInferenceEngine.kt
│           └── test/kotlin/com/secondserve/core/ai/
│               └── MockInferenceEngineTest.kt
│
└── feature/
    ├── match/
    │   ├── build.gradle.kts
    │   └── src/main/kotlin/com/secondserve/feature/match/
    │       ├── MatchScreen.kt
    │       ├── MatchViewModel.kt
    │       ├── CoachingResolver.kt
    │       ├── MatchContextProfileBuilder.kt
    │       ├── MatchContextProfileUpdater.kt
    │       └── CoachingCachePrefetcher.kt
    ├── history/
    │   └── src/main/kotlin/com/secondserve/feature/history/
    │       ├── HistoryScreen.kt
    │       ├── HistoryViewModel.kt
    │       └── StatsScreen.kt
    ├── coaching/
    │   └── src/main/kotlin/com/secondserve/feature/coaching/
    │       ├── CoachingScreen.kt
    │       └── CoachingViewModel.kt
    └── profile/
        └── src/main/kotlin/com/secondserve/feature/profile/
            ├── ProfileScreen.kt
            └── ProfileViewModel.kt
```

### Arborescence complète — Backend VPS (FastAPI)

```
secondserve-backend/
├── pyproject.toml
├── .env.example
├── .env
├── alembic.ini
├── alembic/
│   ├── env.py
│   └── versions/
├── app/
│   ├── main.py
│   ├── api/
│   │   └── v1/
│   │       ├── router.py
│   │       ├── auth.py
│   │       ├── sessions.py
│   │       ├── profile.py
│   │       ├── coaching.py
│   │       ├── sync.py
│   │       └── notifications.py
│   ├── core/
│   │   ├── config.py
│   │   ├── security.py
│   │   └── database.py
│   ├── features/
│   │   ├── auth/
│   │   │   └── service.py
│   │   ├── sessions/
│   │   │   ├── models.py
│   │   │   ├── schemas.py
│   │   │   ├── repository.py
│   │   │   └── service.py
│   │   ├── profile/
│   │   │   ├── models.py
│   │   │   ├── schemas.py
│   │   │   ├── repository.py
│   │   │   └── service.py
│   │   ├── coaching/
│   │   │   ├── models.py
│   │   │   ├── schemas.py
│   │   │   ├── repository.py
│   │   │   ├── service.py
│   │   │   └── mistral_client.py
│   │   ├── sync/
│   │   │   ├── schemas.py
│   │   │   └── service.py
│   │   └── notifications/
│   │       ├── models.py
│   │       ├── schemas.py
│   │       └── scheduler.py
│   └── shared/
│       └── exceptions.py
├── tests/
│   ├── conftest.py
│   ├── unit/
│   │   ├── test_coaching_service.py
│   │   ├── test_sync_service.py
│   │   └── test_security.py
│   └── integration/
│       ├── test_sessions_api.py
│       ├── test_coaching_api.py
│       └── test_sync_api.py
└── secondserve-backend.service
```

### Mapping FRs → Composants

| Feature | FRs | Android | Wear OS | Backend VPS |
|---|---|---|---|---|
| Mode Match | FR-1 à FR-6 | `:feature:match`, `:domain/engine/TennisScoreEngine` | `:wear/presentation/match/` | — |
| Suivi & Historique | FR-7 à FR-9 | `:feature:history`, `:data/local/dao/SessionDao` | — | `features/sessions/` |
| Coaching IA | FR-10 à FR-13 | `:feature:coaching`, `:core:ai` | — | `features/coaching/`, `features/notifications/` |
| Profil joueur | FR-14 à FR-16 | `:feature:profile`, `:data/local/dao/PlayerProfileDao` | — | `features/profile/` |

### Frontières architecturales

**Watch → Phone (DataLayer)**
- Réception : `:data/wearable/DataLayerListener.kt`
- Envoi coaching : `:data/wearable/DataLayerClient.kt`
- Paths : `/secondserve/score_event`, `/secondserve/game_over`, `/secondserve/coaching_result`

**Phone → VPS (REST/JWT)**
- Client : `:data/remote/api/VpsApiService.kt` + `JwtTokenStore.kt`
- Toutes les routes préfixées `/api/v1/`

**Phone → Gemini Nano (on-device)**
- `:core:ai/gemini/GeminiNanoEngine.kt`
- Déclenché par `CoachingResolver` dans `:feature:match`

**VPS → Mistral (cloud)**
- `features/coaching/mistral_client.py`
- Jamais appelé depuis l'app Android directement

### Flux de données principaux

**Match Mode (temps réel)**
```
Wear OS tap → DataLayerListener (score_event)
  → TennisScoreEngine → MatchContextProfileUpdater
  → game_over → CoachingResolver
    → GeminiNanoEngine (background)
    → coaching_result → DataLayerClient → Watch
    → [post-changeover] CoachingCacheWorker (refresh background)
```

**Sync post-match**
```
CloseMatchUseCase → SyncQueueDao
  → SyncWorker (WorkManager, NetworkType.CONNECTED)
    → VpsApiService.syncPush() → /api/v1/sync/push
    → POST /api/v1/coaching/analyze → mistral_client → analyse VPS
```

---

## Architecture Validation Results

### Cohérence — ✅ Validée

**Compatibilité des décisions :**
- Kotlin API 35 + Compose BOM 2026.05.00 + Wear Compose Material3 1.6.2 — stack homogène, aucun conflit de version
- Room + KSP + Hilt + WorkManager + Coroutines/Flow — combinaison standard Android, zéro incompatibilité
- MVI Orbit + Jetpack Compose — conçu pour cette combinaison
- FastAPI + SQLAlchemy async + Alembic + Pydantic v2 — stack Python production-ready cohérent
- JWT Android + EncryptedSharedPreferences — compatibles, standard sécurité Android

**Cohérence des patterns :**
- `SCREAMING_SNAKE_CASE` uniforme : constantes Kotlin, constantes Python, types d'events DataLayer
- `snake_case` uniforme : colonnes Room et colonnes SQLAlchemy
- Epoch ms uniforme : `updated_at` Room ET `ts` DataLayer ET champs sync VPS
- JSON uniforme : DataLayer ET REST API VPS
- `sealed Result<T>` Android ↔ `{error_code, message}` VPS — frontières cohérentes

**Alignement structure :**
- `:domain` sans dépendance Android → tests unitaires 100% JVM, pas de device requis
- `:core:ai` avec `InferenceEngine` interface → `MockInferenceEngine` pour CI
- `CoachingResolver` dans `:feature:match` → point de contrôle unique, pas de duplication
- Structure feature-based VPS miroir mental de l'architecture Android

---

### Couverture des FRs — ✅ Complète

| FR | Composant(s) architectural(ux) | Statut |
|---|---|---|
| FR-1 démarrage session | `StartMatchUseCase`, `SessionRepositoryImpl` | ✅ |
| FR-2 score Pixel Watch | `TennisScoreEngine`, `ScoreScreen`/`ViewModel` (`:wear`) | ✅ |
| FR-3 bouton Conseil changement de côté | `CoachingResolver`, `CoachingScreen` (`:wear`) | ✅ |
| FR-4 génération Gemini Nano | `GeminiNanoEngine` (`:core:ai`) | ✅ |
| FR-5 affichage enrichi téléphone | `CoachingCard` (`:core:ui`), `MatchScreen` | ✅ |
| FR-6 clôture session | `CloseMatchUseCase`, `SyncQueueDao` | ✅ |
| FR-7 historique sessions | `HistoryScreen`, `SessionDao` | ✅ |
| FR-8 stats agrégées | `StatsScreen`, `SessionDao` (index `idx_sessions_surface`) | ✅ |
| FR-9 saisie manuelle rétroactive | `StartMatchUseCase`, `SessionRepositoryImpl` | ✅ |
| FR-10 analyse post-match | `GeneratePostMatchAnalysisUseCase`, VPS `/coaching/analyze` | ✅ |
| FR-11 synthèse multi-matchs | `CoachingViewModel`, VPS `/coaching/analyze` (seuil 3) | ✅ |
| FR-12 axes de travail | `WorkAxis` (`:domain`), `ProfileViewModel` | ✅ |
| FR-13 notifications coaching | `NotificationWorker`, VPS `scheduler.py` (APScheduler) | ✅ |
| FR-14 classement FFT | `RankingEntry`, `ProfileScreen` | ✅ |
| FR-15 style de jeu inféré | `PlayerProfile`, `ProfileViewModel` | ✅ |
| FR-16 données profil complémentaires | `PlayerProfile`, `PlayerProfileRepositoryImpl` | ✅ |

**Couverture NFRs :**

| NFR | Mécanisme architectural | Statut |
|---|---|---|
| NFR-P1 Conseil ≤3s | Pré-calculé à `game_over`, `GeminiNanoEngine` on-device | ✅ |
| NFR-P2 Score Watch ≤500ms | `TennisScoreEngine` sur Watch, pas de relay téléphone | ✅ |
| NFR-P3 Historique ≤1s | `SessionDao` + index Room optimisés | ✅ |
| NFR-P4 Mistral ≤10s | `mistral-small-latest` (~2-4s) + timeout 15s VPS | ✅ |
| NFR-OFF1 Mode Match 100% offline | `GeminiNanoEngine` + `OfflineCoachingCache` + `GENERIC_FALLBACK_TEXTS` | ✅ |
| NFR-OFF2/OFF3 Offline data + sync queue | Room local + `SyncWorker` WorkManager | ✅ |
| NFR-C1/C2/C3 Confidentialité | Mono-user, VPS perso, PII payload design | ✅ |
| NFR-UX1/UX2/UX3/UX4 UX Match | `CoachingScreen` Wear OS + pré-calcul + guards ViewModel | ✅ |
| NFR-PLT1/PLT2/PLT3 Plateforme | Kotlin API 35, Wear OS 4+, FastAPI Python 3.12+ | ✅ |

---

### Analyse des gaps

**Gap important (non bloquant pour l'architecture globale, bloquant pour la story OfflineCoachingCache uniquement) :**
- **Liste exhaustive des `MatchPattern`** — les exemples sont documentés (~15-30 patterns) mais la liste définitive n'est pas encore figée. Doit être finalisée avant la story `OfflineCoachingCache`.

**Gaps nice-to-have :**
- Schéma SQL détaillé des entités Room — à produire en story "Setup Room schema"
- Format des prompts Gemini Nano / Mistral — à définir en story "InferenceEngine implementation"
- UX de correction de point (undo) côté Watch — relève de l'implémentation, pas de l'architecture

---

### Checklist de complétude

**Requirements Analysis**
- [x] Contexte projet analysé en profondeur
- [x] Complexité et échelle évaluées (élevée, 3 runtimes)
- [x] Contraintes techniques identifiées
- [x] Cross-cutting concerns cartographiés (6 concerns)

**Architectural Decisions**
- [x] Décisions critiques documentées avec versions (D1-D5)
- [x] Stack technique complètement spécifiée
- [x] Patterns d'intégration définis (DataLayer JSON, REST JWT, VPS→Mistral)
- [x] Considérations de performance adressées (NFR-P1 à P4)

**Implementation Patterns**
- [x] Conventions de nommage établies (DB, REST, Kotlin, Python)
- [x] Patterns de structure définis (modules Gradle, feature-based VPS)
- [x] Patterns de communication spécifiés (DataLayer JSON, MVI Orbit, sealed Result)
- [x] Patterns de processus documentés (error handling, logging Timber/logging)

**Project Structure**
- [x] Structure de répertoires complète définie
- [x] Frontières des composants établies
- [x] Points d'intégration cartographiés
- [x] Mapping FRs → structure complet (FR-1 à FR-16)

---

### Statut de préparation

**Statut global : READY WITH MINOR GAPS**

Les 16 items de la checklist sont validés. Le gap `MatchPattern` ne bloque pas le démarrage — toutes les stories setup, TennisScoreEngine, DataLayer, Room et InferenceEngine peuvent démarrer immédiatement.

**Niveau de confiance : Élevé**

**Points forts :**
- `:domain` pur Kotlin → testabilité maximale sans device
- NFR-UX4 (pré-calcul à `game_over`) transforme une contrainte de latence en avantage UX
- `InferenceEngine` interface → CI sans device physique
- `CoachingResolver` unique point de contrôle coaching
- PII résolu par design (payload minimal)
- Watch = source de vérité unique → classe entière de bugs éliminée

**Axes d'amélioration futurs (V2+) :**
- Modèle ML dédié pour le style de jeu
- Standalone Wi-Fi Pixel Watch
- Upgrade `mistral-large` si synthèses V1 insuffisantes

---

### Handoff Implémentation

**Commandes d'initialisation :**
```bash
# Android — Android Studio → New Project → Empty Activity (Jetpack Compose)
# → configurer multi-module Gradle Kotlin DSL

# Backend
uv init secondserve-backend
uv add fastapi[standard] sqlalchemy[asyncio] alembic pydantic-settings
```

**Séquence d'implémentation :**
1. Setup multi-module Android + Setup FastAPI + JWT
2. `TennisScoreEngine` (`:domain`) — fondation testable
3. DataLayer bridge (`:data/wearable/`)
4. Room schema + repositories (`:data/local/`)
5. `GeminiNanoEngine` (`:core:ai`)
6. `OfflineCoachingCache` + `CoachingResolver` *(après liste `MatchPattern` figée)*
7. VPS routing Mistral (`features/coaching/mistral_client.py`)
8. `SyncWorker` + `NotificationWorker` (WorkManager)
