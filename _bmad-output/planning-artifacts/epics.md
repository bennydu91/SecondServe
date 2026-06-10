---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - "_bmad-output/planning-artifacts/prds/prd-SecondServe-2026-06-08/prd.md"
  - "_bmad-output/planning-artifacts/prds/prd-SecondServe-2026-06-08/addendum.md"
  - "_bmad-output/planning-artifacts/architecture.md"
---

# SecondServe - Epic Breakdown

## Overview

Ce document contient la décomposition complète en épics et stories de SecondServe, dérivée du PRD, de l'addendum technique et du document d'architecture.

## Requirements Inventory

### Functional Requirements

FR-1: L'utilisateur peut démarrer une Session Match avec surface + format obligatoires (nombre de sets, règle du 3e set : avantage complet / super tie-break à 10 pts / set décisif raccourci) et champs optionnels (adversaire, type compétition, tournoi). Session persistée localement avant le match, sans réseau requis.

FR-2: En Mode Match, la Pixel Watch affiche une interface de saisie de score point par point (Point A / Point B). Le score complet (points du jeu en cours, jeux du set, sets) se met à jour en temps réel selon les règles tennis. Tie-break déclenché à 6-6, super tie-break selon le format configuré en FR-1. Affichage ≤ 500ms après tap.

FR-3 (corrigé): À chaque changement de côté (total des jeux du set = nombre impair), la Watch détecte automatiquement l'événement et envoie un `game_over` via DataLayer. Aucun bouton ni interaction sur la Watch n'est requis pour déclencher le coaching. Si pas de réseau et DataLayer indisponible, timeout 60s puis retour affichage score.

FR-4 (corrigé): Au `game_over`, Gemini Nano génère un Conseil en background (pré-calculé, NFR-UX4). Le Conseil est affiché sur le téléphone en ≤ 3s, ≤ 3 phrases, avec au moins 1 référence au contexte réel (score, surface, Axe de travail actif, historique récent). Fallback : OfflineCoachingCache (lookup par MatchPattern), sinon bibliothèque locale statique (≥ 20 conseils couvrant différentes situations).

FR-5 (corrigé): Le téléphone est l'écran principal de coaching. À chaque `game_over` reçu via DataLayer, le Conseil s'affiche automatiquement (notification ambient ou écran lock-screen) sans déverrouillage ni navigation. Contenu enrichi : Conseil + contexte + raisonnement + 1-2 points d'attention.

FR-6: En fin de match, l'utilisateur saisit le score final et une évaluation rapide (ressenti 1-5 étoiles + commentaire optionnel). La Session est marquée "terminée", sauvegardée localement et mise en queue de Sync. La clôture est accessible depuis la Watch ou le téléphone.

FR-7: L'utilisateur accède à la liste de toutes ses Sessions (matchs et entraînements), triées par date décroissante. Chaque entrée affiche : date, adversaire (si renseigné), surface, score final, résultat (victoire/défaite/non applicable), type de compétition. Les Sessions incomplètes (interrompues) sont visibles avec indicateur de statut.

FR-8: L'app calcule et affiche : win rate global (matchs avec résultat enregistré), win rate par surface (uniquement si ≥ 3 matchs sur cette surface), nombre de Sessions par type, séquence active de victoires ou de défaites. Consultable hors connexion.

FR-9: L'utilisateur peut ajouter une Session pour un match joué sans l'app (saisie rétrospective). Les champs disponibles sont identiques à une session démarrée en temps réel. La session apparaît dans l'historique et est incluse dans les statistiques.

FR-10: À la clôture de chaque Session de type Match, l'app génère automatiquement une analyse post-match via Mistral API (proxied par le VPS) : points forts observés, points faibles mis en évidence, écart avec les Axes de travail actifs, 1-2 recommandations concrètes. Persistée localement, consultable hors connexion après génération. Si réseau absent à la clôture, génération mise en queue et exécutée à la prochaine connexion.

FR-11: L'app génère automatiquement une synthèse coaching transversale quand ≥ 3 nouvelles Sessions Match ont été enregistrées depuis la dernière synthèse. La synthèse identifie des patterns sur la période, l'évolution par rapport aux synthèses précédentes, un axe prioritaire multi-matchs et une recommandation structurée. Générable à la demande même si le seuil de 3 n'est pas atteint. Se distingue visuellement de l'analyse post-match individuelle.

FR-12: L'utilisateur peut créer, modifier et supprimer des Axes de travail (maximum 3 actifs simultanément). L'app propose des axes suggérés par l'IA à partir des Sessions récentes, clairement distingués des axes manuels. Un Axe actif est intégré dans le contexte envoyé à Gemini Nano et Mistral API dès la Session suivante.

FR-13: L'app envoie des notifications push personnalisées : conseil du jour (fréquence configurable : quotidien / tous les 2 jours / hebdomadaire / désactivé) et rappel de préparation avant un match planifié. Chaque notification inclut au moins 1 référence spécifique (surface, Axe de travail, résultat récent). Mode silencieux activable manuellement pour une période définie. Aucune notification si aucune Session depuis 30 jours.

FR-14: L'utilisateur saisit son Classement FFT officiel (série + points) à la demande. Les formats de séries FFT valides sont acceptés (40, 30/5, 30/4, 30/3, 30/2, 30/1, 15/5, 15/4, 15/3, 15/2, 15/1, 4/6, 3/6, 2/6, 1/6). L'historique des classements est conservé et affiché en timeline chronologique. Le classement actuel est intégré dans les prompts IA.

FR-15: L'app infère le Style de jeu (défenseur / attaquant / contre-puncheur / all-court) à partir des données de Sessions après ≥ 10 Sessions enregistrées. La section reste vide ou affiche "données insuffisantes" avant ce seuil. Le Style inféré est affiché sur le profil et amendable manuellement à tout moment. Le Style (inféré ou manuel) est intégré dans les prompts Gemini Nano et Mistral API.

FR-16: L'utilisateur peut renseigner : surfaces de prédilection (préférence déclarée), numéro de licence FFT (optionnel, stocké localement uniquement, jamais transmis à Mistral API ni à tout tiers), et consignes du coach humain via 3 champs texte libre (axe principal, axe secondaire, mauvaises habitudes à corriger). Chaque champ est optionnel et indépendant.

### NonFunctional Requirements

NFR-P1: Conseil Gemini Nano on-device affiché sur le téléphone en ≤ 3 secondes sur Pixel 9 Pro
NFR-P2: Mise à jour du score sur Pixel Watch ≤ 500ms après tap
NFR-P3: Chargement de l'historique (< 200 sessions) ≤ 1 seconde
NFR-P4: Appel Mistral API (synthèse hors match, via VPS) ≤ 10 secondes en 4G/Wi-Fi
NFR-OFF1: Mode Match 100% fonctionnel sans connexion réseau (Gemini Nano on-device + OfflineCoachingCache + bibliothèque statique)
NFR-OFF2: Historique et Profil consultables hors connexion (données locales Room)
NFR-OFF3: Sessions créées hors connexion mises en queue WorkManager, synchronisées automatiquement au retour du réseau
NFR-S1: Stockage local Room (SQLite) sur Android
NFR-S2: Sync bidirectionnelle Android ↔ VPS via API REST
NFR-S3: Sync delta-based : seules les modifications depuis la dernière sync sont transmises (champ `updated_at` epoch ms + `sync_version`)
NFR-S4: Résolution de conflits : last-write-wins sur timestamp serveur
NFR-S5: Données transmises à Mistral API via VPS : profil générique + statistiques uniquement — aucun identifiant personnel (ranking FFT + noms adversaires uniquement)
NFR-C1: Architecture mono-utilisateur — aucune donnée partagée entre utilisateurs
NFR-C2: Données stockées uniquement sur l'appareil et le VPS personnel de l'utilisateur
NFR-C3: Numéro de licence FFT jamais inclus dans les prompts envoyés à Mistral API
NFR-UX1: Interface Pixel Watch : toute action en match accessible en ≤ 1 tap
NFR-UX2: Conseil affiché sur le téléphone : ≤ 3 phrases, lisible en conditions lumineuses extérieures
NFR-UX3: Aucune action ne termine ou interrompt une Session active sans confirmation explicite de l'utilisateur
NFR-UX4: Coaching pré-calculé à `game_over` (tap = révélation, pas requête) — Gemini Nano déclenché en background à la fin du jeu
NFR-PLT1: Android Pixel 9 Pro, Android 15 (API 35) — cible V1 unique
NFR-PLT2: Wear OS Pixel Watch, Wear OS 4+
NFR-PLT3: Kotlin (Android + Wear OS), Python 3.12+ + FastAPI (backend VPS)

### Additional Requirements

ARCH-1: Setup Android multi-module Gradle Kotlin DSL — modules skeleton à créer : :app, :wear, :domain, :data, :core:ui, :core:ai, :feature:match, :feature:history, :feature:coaching, :feature:profile. Versions dans libs.versions.toml, Hilt configuré dans :app.

ARCH-2: Setup Backend FastAPI — initialisation via `uv init secondserve-backend`, dépendances (fastapi[standard], sqlalchemy[asyncio], alembic, pydantic-settings), structure feature-based (auth, sessions, profile, coaching, sync, notifications), systemd service, Nginx reverse proxy + HTTPS Let's Encrypt, variables d'environnement (JWT_SECRET, MISTRAL_API_KEY, DATABASE_URL).

ARCH-3: JWT auth Android ↔ VPS — endpoint `POST /api/v1/auth/init` (génère et retourne un JWT signé), token stocké dans EncryptedSharedPreferences (Android Keystore backed), header `Authorization: Bearer <token>` sur tous les appels REST, middleware JWTBearer sur toutes les routes `/api/v1/**` sauf `/auth/init`.

ARCH-4: Room database schema — 7 entités : Session, Point, CoachingCache, WorkAxis, PlayerProfile, RankingHistory, SyncQueue. Index optimisés pour stats (ex : idx_sessions_surface). Migrations Room (pas de fallbackToDestructiveMigration en production).

ARCH-5: Alembic migrations VPS — même schéma conceptuel que Room, SQLAlchemy async, SQLite. Alembic configuré dans alembic.ini avec env.py.

ARCH-6: TennisScoreEngine dans :domain — automate à états finis (points 0/15/30/40/Avantage/Égalité, jeux, sets, tie-break à 6-6, super tie-break selon format, détection changements de côté, undo dernier point). Zéro dépendance Android, 100% testable JVM. Tests unitaires complets (TennisScoreEngineTest.kt).

ARCH-7: DataLayer bridge Watch ↔ Phone — 2 paths JSON actifs : `/secondserve/score_event` (Watch → Phone) et `/secondserve/game_over` (Watch → Phone). DataLayerClient (envoi) + DataLayerListener (réception) dans :data/wearable/. Note : `/secondserve/coaching_result` (Phone → Watch) supprimé — la Watch ne reçoit plus de coaching (FR-3/FR-4/FR-5 corrigés).

ARCH-8: InferenceEngine interface dans :core:ai + GeminiNanoEngine (production, ML Kit Generative AI APIs) + MockInferenceEngine (tests/CI, sans device physique requis). VpsMistralEngine (appels Mistral via VPS REST).

ARCH-9: OfflineCoachingCache — MatchPattern enum (liste exhaustive ~15-30 patterns à figer avant cette story — PRÉREQUIS BLOQUANT), CoachingPatternDetector (MatchStateSnapshot → MatchPattern, déterministe), CoachingCacheRepository (Room : match_id, pattern, content, generated_at, is_stale), CoachingCachePrefetcher (init-match : tous patterns async ; post-changeover : patterns probables non-bloquant), CoachingResolver (point unique de décision online vs cache vs static).

ARCH-10: WorkManager workers — SyncWorker (delta sync, contrainte NetworkType.CONNECTED, idempotent), CoachingCacheWorker (refresh post-changeover), NotificationWorker (conseil du jour + rappel pré-match).

ARCH-11: VPS APScheduler intégré à FastAPI — génération contenu Mistral pour notifications pré-match planifiées. Endpoint GET `/api/v1/notifications/pending` consommé par polling Android.

ARCH-12: Hilt DI — AppModule, DataModule, AiModule, FeatureModules. Bindings pour toutes les interfaces (InferenceEngine, SessionRepository, CoachingRepository, PlayerProfileRepository, SyncRepository).

ARCH-13: Séquence d'implémentation obligatoire (dépendances) : ARCH-1+ARCH-2 → ARCH-3 → ARCH-6 → ARCH-7 → ARCH-4+ARCH-5 → ARCH-8 → ARCH-9 → VPS Mistral routing → ARCH-10+ARCH-11.

ARCH-14: ⚠️ PRÉREQUIS BLOQUANT — Liste exhaustive MatchPattern (~15-30 patterns) doit être figée AVANT la story ARCH-9. Exemples documentés : SERVICE_HELD_UNDER_PRESSURE, BREAK_CONFIRMED, BREAK_LOST_AFTER_HOLD, SET_WON_DOMINANT, SET_LOST_CLOSE, DOUBLE_FAULT_CLUSTER, TIEBREAK_APPROACHING, MATCH_POINT_APPROACHING, NEUTRAL_TRANSITION.

### UX Design Requirements

*Aucune spec UX séparée — application Android native. Les exigences UX sont couvertes par les NFRs UX (NFR-UX1 à NFR-UX4) et les conséquences testables des FRs.*

### FR Coverage Map

```
FR-1  → Epic 2 — Démarrage session match
FR-2  → Epic 2 — Suivi score Pixel Watch
FR-3  → Epic 2 — Détection changement de côté (game_over DataLayer)
FR-4  → Epic 3 — Génération Conseil Gemini Nano → affichage téléphone
FR-5  → Epic 3 — Affichage Conseil automatique sur téléphone (display principal)
FR-6  → Epic 2 — Clôture session + queue sync
FR-7  → Epic 4 — Historique sessions
FR-8  → Epic 4 — Statistiques agrégées
FR-9  → Epic 4 — Saisie rétrospective
FR-10 → Epic 5 — Analyse post-match (Mistral)
FR-11 → Epic 5 — Synthèse multi-matchs
FR-12 → Epic 5 — Axes de travail
FR-13 → Epic 6 — Notifications contextualisées
FR-14 → Epic 1 — Classement FFT + historique
FR-15 → Epic 1 — Style de jeu inféré
FR-16 → Epic 1 — Profil complémentaire
```

## Epic List

### Epic 1: Fondation & Profil joueur
L'app Android se lance, se connecte au VPS personnel via JWT, et le joueur peut renseigner son profil complet : classement FFT (avec historique), style de jeu, axes de travail et consignes du coach humain. C'est la base de toute personnalisation IA.

**FRs couverts:** FR-14, FR-15, FR-16
**ARCH:** ARCH-1, ARCH-2, ARCH-3, ARCH-4, ARCH-5, ARCH-12

---

### Epic 2: Mode Match — Score & Session
Le joueur peut démarrer un match avec ses paramètres (surface, format, adversaire), suivre le score point par point sur la Pixel Watch (tie-break et super tie-break automatiques), clôturer la session avec ressenti, et la synchroniser avec le VPS. La Watch est la source de vérité unique.

**FRs couverts:** FR-1, FR-2, FR-3, FR-6
**ARCH:** ARCH-6 (TennisScoreEngine), ARCH-7 (DataLayer — 2 paths), ARCH-10 (SyncWorker)

---

### Epic 3: Coaching en match
Au changement de côté détecté par la Watch, un Conseil personnalisé s'affiche automatiquement sur le téléphone en ≤ 3s, sans interaction. Fonctionne 100% offline (OfflineCoachingCache pré-calculé à l'init du match). La Watch reste exclusivement dédiée au score.

**FRs couverts:** FR-4, FR-5
**ARCH:** ARCH-8 (InferenceEngine + GeminiNanoEngine + Mock), ARCH-9 (OfflineCoachingCache — PRÉREQUIS: liste MatchPattern figée avant cette story)

---

### Epic 4: Historique & Statistiques
Le joueur accède à l'historique complet de ses matchs avec statistiques agrégées (win rate global et par surface, séquences). Il peut saisir des matchs joués sans l'app (saisie rétrospective). Toutes les données sont consultables hors connexion.

**FRs couverts:** FR-7, FR-8, FR-9

---

### Epic 5: Coaching IA hors match
Après chaque match, une analyse IA personnalisée est générée automatiquement (Mistral API via VPS). Après 3 matchs, une synthèse transversale est disponible. Le joueur gère ses axes de travail (3 actifs max), avec suggestions IA.

**FRs couverts:** FR-10, FR-11, FR-12
**ARCH:** VPS Mistral routing (features/coaching/mistral_client.py)

---

### Epic 6: Notifications coaching contextualisées
L'app envoie des notifications personnalisées : conseil du jour (fréquence configurable) et rappel de préparation avant un match planifié. Chaque notification référence un élément réel du profil ou de l'historique. Aucune dépendance FCM.

**FRs couverts:** FR-13
**ARCH:** ARCH-10 (NotificationWorker), ARCH-11 (VPS APScheduler)

---

## Epic 1: Fondation & Profil joueur

L'app Android se lance, se connecte au VPS personnel via JWT, et le joueur peut renseigner son profil complet : classement FFT (avec historique), style de jeu, axes de travail et consignes du coach humain. C'est la base de toute personnalisation IA.

### Story 1.1: Setup Android multi-module Gradle

As a developer,
I want the Android project structured as a Gradle multi-module project (Kotlin DSL) with Hilt DI configured,
So that all feature development follows the agreed architecture and modules are independently buildable.

**Acceptance Criteria:**

**Given** the Android project is opened in Android Studio
**When** the Gradle sync completes
**Then** all 10 modules exist with their build.gradle.kts: `:app`, `:wear`, `:domain`, `:data`, `:core:ui`, `:core:ai`, `:feature:match`, `:feature:history`, `:feature:coaching`, `:feature:profile`
**And** `libs.versions.toml` déclare toutes les versions (Compose BOM 2026.05.00, Hilt, Room KSP, Coroutines, Wear Compose Material3 1.6.2, etc.)
**And** Hilt est configuré dans `:app` (`SecondServeApp` annoté `@HiltAndroidApp`)
**And** une `MainActivity` vide se lance sur Pixel 9 Pro (API 35) sans crash
**And** une `WearActivity` vide se lance sur Pixel Watch (Wear OS 4+) sans crash
**And** `:domain` ne contient aucune dépendance Android — module Kotlin pur, tests JVM passent

### Story 1.2: Setup FastAPI backend

As a developer,
I want a FastAPI backend deployed on the VPS with Nginx reverse proxy and HTTPS,
So that the Android app has a secure endpoint to communicate with.

**Acceptance Criteria:**

**Given** le VPS (4 vCPU / 16 Go RAM) est accessible
**When** le setup est complet
**Then** `secondserve-backend.service` tourne via systemd et survive un redémarrage
**And** `GET https://<vps-domain>/api/v1/health` retourne `{"status": "ok"}` HTTP 200
**And** Nginx est configuré en reverse proxy vers FastAPI (port 8000), HTTPS actif (certificat Let's Encrypt valide)
**And** la structure feature-based est en place : `app/features/` contient `auth/`, `sessions/`, `profile/`, `coaching/`, `sync/`, `notifications/`
**And** Alembic est configuré (`alembic.ini` + `alembic/env.py` pointant sur la base SQLite)
**And** les variables d'environnement sont documentées dans `.env.example` : `JWT_SECRET`, `MISTRAL_API_KEY`, `DATABASE_URL`

### Story 1.3: JWT Authentication Android ↔ VPS

As a user,
I want the app to silently authenticate with my VPS on first launch,
So that my personal data stays secure on my private server.

**Acceptance Criteria:**

**Given** l'app se lance pour la première fois (aucun token stocké)
**When** l'app s'initialise
**Then** `POST /api/v1/auth/init` est appelé et retourne un JWT signé (`JWT_SECRET` côté VPS)
**And** le token est stocké dans `EncryptedSharedPreferences` (Android Keystore backed)
**And** les appels suivants incluent le header `Authorization: Bearer <token>`
**Given** un appel API est effectué sans token valide
**When** le middleware `JWTBearer` évalue la requête
**Then** le VPS retourne HTTP 401
**And** l'app détecte le 401 et déclenche une ré-authentification silencieuse
**And** toutes les routes `/api/v1/**` (sauf `/auth/init`) sont protégées par le middleware

### Story 1.4: Classement FFT — Saisie et historique

As a player,
I want to record my official FFT ranking (series + points) and view my progression over time,
So that my AI coaching is calibrated to my actual competition level.

**Acceptance Criteria:**

**Given** je suis sur l'écran Profil
**When** j'ouvre la section "Classement FFT"
**Then** un formulaire accepte une série parmi les valeurs FFT valides : `40, 30/5, 30/4, 30/3, 30/2, 30/1, 15/5, 15/4, 15/3, 15/2, 15/1, 4/6, 3/6, 2/6, 1/6` et un nombre de points entier positif
**And** toute série hors de cette liste est rejetée avec un message d'erreur explicite
**When** je sauvegarde un classement valide
**Then** il apparaît dans la timeline de progression (ordre chronologique, plus récent en premier)
**And** les entités `PlayerProfile` et `RankingHistory` sont créées en Room via la migration de cette story (tables `player_profiles` et `ranking_history`)
**And** la migration Alembic correspondante est appliquée sur le VPS
**And** le classement actuel (série) est visible sur le résumé de l'écran Profil
**And** la série courante est incluse dans le contexte envoyé aux moteurs IA (Gemini Nano et Mistral)

### Story 1.5: Profil joueur — Style de jeu & données complémentaires

As a player,
I want to configure my preferred surfaces, my coach's instructions, and see my inferred playing style,
So that every AI coaching interaction reflects my specific game identity.

**Acceptance Criteria:**

**Given** je suis sur l'écran Profil, section "Style de jeu"
**When** moins de 10 Sessions Match sont enregistrées
**Then** le style affiche "Données insuffisantes (minimum 10 matchs)"
**When** je sélectionne manuellement un style (Défenseur / Attaquant / Contre-puncheur / All-court)
**Then** il est sauvegardé et intégré dans les prompts IA dès la prochaine interaction coaching
**Given** je suis sur la section "Profil complémentaire"
**When** je renseigne l'un des 3 champs consignes coach (axe principal, axe secondaire, mauvaises habitudes)
**Then** chaque champ renseigné est envoyé comme élément distinct dans le contexte IA
**And** les champs vides sont simplement omis du contexte (aucun placeholder envoyé)
**When** j'entre un numéro de licence FFT
**Then** il est stocké dans `EncryptedSharedPreferences` (local uniquement)
**And** il n'est inclus dans aucun payload sortant vers Mistral API ou tout service tiers
**And** les surfaces de prédilection sélectionnées sont reflétées dans les statistiques et recommandations IA

### Story 1.6: Axes de travail — CRUD de base

As a player,
I want to create, edit and delete my work axes (max 3 active simultaneously),
So that my coaching is focused on my current training priorities from the very first match.

**Acceptance Criteria:**

**Given** je suis sur l'écran Axes de travail
**When** je crée un axe avec un texte descriptif
**Then** il apparaît dans la liste des axes actifs
**And** il est inclus dans le contexte IA dès la prochaine interaction coaching (Gemini Nano + Mistral)
**When** je tente de créer un 4e axe actif
**Then** l'app affiche un message d'erreur : "Maximum 3 axes actifs atteint"
**And** la création est bloquée
**When** je modifie un axe existant
**Then** le texte mis à jour est immédiatement utilisé dans les futurs prompts IA
**When** je supprime un axe
**Then** il est retiré de la liste active et n'est plus inclus dans le contexte IA
**And** la table `work_axes` est créée en Room et Alembic via la migration de cette story

> *Note : la suggestion automatique d'axes par l'IA (à partir de l'analyse des Sessions) est couverte en Epic 5 (FR-12 complet).*

---

## Epic 2: Mode Match — Score & Session

Le joueur peut démarrer un match avec ses paramètres, suivre le score point par point sur la Pixel Watch (tie-break et super tie-break automatiques), clôturer la session avec ressenti, et la synchroniser avec le VPS. La Watch est la source de vérité unique.

### Story 2.1: TennisScoreEngine — Automate à états finis

As a developer,
I want a pure Kotlin scoring engine in `:domain` that handles all tennis scoring rules,
So that all score logic is testable without a device and serves as the single source of truth for Watch and Phone.

**Acceptance Criteria:**

**Given** une séquence quelconque de points en entrée
**When** `TennisScoreEngine` les traite
**Then** il calcule correctement : points (0/15/30/40/Avantage/Égalité), jeux, sets
**And** le tie-break se déclenche automatiquement à 6-6 (comptage 0-1-2... jusqu'à ≥7 avec 2 points d'écart)
**And** le super tie-break se déclenche selon le format configuré en session (jusqu'à ≥10 avec 2 points d'écart)
**And** le changement de côté est détecté quand le total de jeux dans le set est impair (événement `game_over`)
**And** l'undo du dernier point annule la dernière transition d'état et restaure l'état précédent
**And** tous les cas de règles sont couverts par `TennisScoreEngineTest.kt` (JVM, aucun device requis)
**And** `:domain` n'a aucune dépendance Android — module Kotlin pur

### Story 2.2: DataLayer Bridge Watch ↔ Phone

As a developer,
I want a Wearable DataLayer bridge that relays score events from Watch to Phone via Bluetooth,
So that the Phone receives match state in real-time without maintaining independent score logic.

**Acceptance Criteria:**

**Given** la Watch et le Phone sont appairés via Bluetooth
**When** la Watch envoie un événement de score
**Then** `DataLayerClient` envoie un message JSON sur le path `/secondserve/score_event` :
`{"type": "SCORE_EVENT", "ts": <epoch_ms>, "score": {...}}`
**When** la Watch détecte un `game_over`
**Then** `DataLayerClient` envoie sur `/secondserve/game_over` :
`{"type": "GAME_OVER", "ts": <epoch_ms>, "score_snapshot": {...}}`
**When** le `DataLayerListener` du Phone reçoit un message
**Then** il parse le JSON et met à jour le `ScoreRepository` (cache read-only côté Phone)
**And** tous les timestamps JSON sont en epoch millisecondes (Long)
**And** le Phone ne maintient aucun état de score indépendant — il ne fait que recevoir et mettre en cache
**And** `DataLayerClient` et `DataLayerListener` sont dans `:data/wearable/`

### Story 2.3: Démarrage de session match

As a player,
I want to start a Match session by configuring its surface and format,
So that the session is tracked and my coaching is contextualized from the first point.

**Acceptance Criteria:**

**Given** je suis sur l'écran d'accueil
**When** je tape "Nouveau match"
**Then** un formulaire s'affiche avec : surface (obligatoire : Terre battue / Gazon / Dur / Carpet), format sets (obligatoire : 1 set / 3 sets), règle 3e set (obligatoire si 3 sets : avantage complet / super tie-break à 10 / set décisif raccourci), adversaire (optionnel, texte libre), type de compétition (optionnel), tournoi (optionnel)
**When** je soumets avec surface + format uniquement (sans les champs optionnels)
**Then** la session est créée et persistée en Room — table `sessions` créée via la migration de cette story
**And** aucune connexion réseau n'est requise pour créer et démarrer une session
**And** la session est accessible dans l'historique même si elle est interrompue sans clôture formelle
**And** le format choisi conditionne la logique de score pour toute la durée de la session
**And** la migration Alembic correspondante (`sessions`) est appliquée sur le VPS

### Story 2.4: Suivi de score sur Pixel Watch — Point par point

As a player,
I want to record each point on my Pixel Watch with the score updating in under 500ms,
So that I never lose track of the score during the match.

**Acceptance Criteria:**

**Given** une Session Match est active
**When** je tape "Point A" ou "Point B" sur la `ScoreScreen` de la Watch
**Then** le score se met à jour correctement : 0→15→30→40→Jeu (ou Avantage/Égalité à 40-40)
**And** l'affichage se met à jour en ≤ 500ms après le tap
**And** l'écran affiche en permanence : points du jeu en cours + jeux du set + sets
**And** un `score_event` est envoyé via DataLayer au Phone après chaque point
**When** le score atteint 6-6 dans un set
**Then** le mode tie-break s'active automatiquement (comptage 0-1-2...)
**When** le format configuré déclenche un super tie-break
**Then** le mode super tie-break s'active automatiquement
**When** je fais un appui long sur le score (undo)
**Then** l'état de score précédent est restauré
**And** un `score_event` corrigé est envoyé au Phone

### Story 2.5: Détection changement de côté & game_over automatique

As a player,
I want the Watch to automatically detect each changement de côté and signal it to the Phone without any tap,
So that coaching is triggered at exactly the right moment with zero friction.

**Acceptance Criteria:**

**Given** une Session Match est active
**When** `TennisScoreEngine` enregistre la fin d'un jeu
**Then** la Watch vérifie si le total de jeux dans le set est impair (changement de côté)
**If** oui : un message `game_over` est envoyé automatiquement via DataLayer au Phone avec le `score_snapshot` complet — aucun tap ni interaction utilisateur requis
**If** non : aucun message `game_over` n'est envoyé, l'écran score reprend immédiatement
**And** la `ScoreScreen` retourne en mode saisie de points après l'envoi, sans bloquer l'UI
**And** le `DataLayerListener` du Phone reçoit le `game_over` et met à jour le `ScoreRepository`
**And** le timeout de 60 secondes sans réponse DataLayer ne bloque pas la Watch — elle continue le suivi de score normalement

### Story 2.6: Clôture de session match & SyncWorker

As a player,
I want to close my match session with the final score and an optional feeling rating, then have it synced to my VPS automatically,
So that my match is recorded and backed up without manual effort.

**Acceptance Criteria:**

**Given** une Session Match est active
**When** je déclenche la clôture (depuis la Watch ou le Phone)
**Then** une confirmation explicite est requise avant toute clôture (NFR-UX3 — aucune clôture accidentelle)
**When** je confirme avec score final uniquement (sans ressenti)
**Then** la session est marquée "terminée" en Room avec résultat (victoire/défaite/nul calculé depuis le score final)
**And** elle apparaît dans l'historique avec statut "terminé" et le score final
**And** une entrée `SyncQueue` est créée — table `sync_queue` + table `points` (log point par point) créées via la migration de cette story
**And** si le réseau est disponible à la clôture, `SyncWorker` se déclenche immédiatement
**And** si le réseau est indisponible, `SyncWorker` réessaie automatiquement dès que `NetworkType.CONNECTED` est satisfait (WorkManager)
**And** les opérations de sync sont idempotentes (un double envoi ne crée pas de doublon côté VPS)

---

## Epic 3: Coaching en match

Au changement de côté détecté par la Watch, un Conseil personnalisé s'affiche automatiquement sur le téléphone en ≤ 3s, sans interaction. Fonctionne 100% offline (OfflineCoachingCache pré-calculé à l'init du match). La Watch reste exclusivement dédiée au score.

### Story 3.1: InferenceEngine interface + MockInferenceEngine

As a developer,
I want an `InferenceEngine` interface with a `MockInferenceEngine` for CI and tests,
So that all coaching logic can be developed and tested without a physical device or AICore support.

**Acceptance Criteria:**

**Given** le module `:core:ai`
**Then** l'interface `InferenceEngine` est définie avec `suspend fun generate(prompt: String): Result<String>`
**And** `MockInferenceEngine` retourne des réponses déterministes configurables via son constructeur (ex : réponse fixe, simulation d'erreur)
**And** Hilt fournit `MockInferenceEngine` en environnement test/CI et `GeminiNanoEngine` en production via un binding module
**And** `MockInferenceEngineTest.kt` valide le comportement du mock
**And** l'émulateur CI n'a aucune dépendance vers AICore — les tests d'intégration passent sans device physique

### Story 3.2: GeminiNanoEngine — Coaching on-device

As a developer,
I want a `GeminiNanoEngine` using the ML Kit Generative AI APIs (Android AICore),
So that coaching advice is generated on-device in ≤ 3s with zero network call.

**Acceptance Criteria:**

**Given** `GeminiNanoEngine` est initialisé
**When** `generate(prompt)` est appelé sur Pixel 9 Pro
**Then** il utilise les ML Kit Generative AI APIs (Android AICore / Gemini Nano stable)
**And** retourne un `Result.Success<String>` en ≤ 3 secondes (NFR-P1)
**Given** Android AICore est indisponible
**When** `generate()` est appelé
**Then** il retourne `Result.Error` avec code `ErrorCode.INFERENCE_FAILED`
**And** Timber loggue l'événement à niveau DEBUG : `"GeminiNanoEngine unavailable, falling back"`
**And** les tests sur device physique (Pixel 9 Pro) valident la latence ≤ 3s
**And** les tests CI utilisent `MockInferenceEngine` (Story 3.1) — aucun test Gemini Nano sur émulateur

> *Note : `VpsMistralEngine` (fallback réseau Mistral) sera implémenté en Epic 5 — le fallback in-match Mistral (FR-4 "si réseau dispo") sera activé à ce moment.*

### Story 3.3: OfflineCoachingCache — Init match & détection de pattern

> ⚠️ **PRÉREQUIS BLOQUANT (ARCH-14) :** La liste exhaustive des `MatchPattern` (~15-30 patterns) doit être figée avant de démarrer cette story.

As a developer,
I want the `OfflineCoachingCache` pre-populated at match start with AI-generated coaching per `MatchPattern`,
So that personalized offline coaching is instantly available from the first changement de côté.

**Acceptance Criteria:**

**Given** l'enum `MatchPattern` est défini avec tous les patterns finalisés (ARCH-14 résolu)
**When** une Session Match démarre
**Then** `CoachingCachePrefetcher.initMatch()` se déclenche en async non-bloquant
**And** Gemini Nano génère du contenu coaching pour chaque `MatchPattern` avec le `MatchContextProfile` courant
**And** chaque entrée est stockée en Room — table `coaching_cache` créée via la migration de cette story : `match_id`, `pattern`, `content`, `generated_at`, `is_stale=false`
**And** `CoachingPatternDetector.detect(MatchStateSnapshot)` est déterministe : même état → même pattern, testable sans LLM
**Given** la génération du cache n'est pas encore terminée au premier `game_over`
**Then** `GENERIC_FALLBACK_TEXTS` (map hardcodée dans les ressources) est utilisé — aucun blocage
**And** les entrées stale (non rafraîchies depuis le dernier changeover) restent lisibles et ne sont jamais supprimées automatiquement

### Story 3.4: CoachingResolver & affichage Conseil sur téléphone

As a player,
I want a personalized coaching advice to appear automatically on my phone at each changement de côté,
So that I get relevant guidance in ≤ 3 seconds without any interaction, even offline.

**Acceptance Criteria:**

**Given** un `game_over` est reçu via DataLayer (Story 2.5)
**When** `CoachingResolver` est invoqué
**Then** il suit la chaîne de priorité :
1. `GeminiNanoEngine` (on-device, primaire) — si disponible et répond en ≤ 3s
2. `OfflineCoachingCache` lookup via `CoachingPatternDetector.detect(state)` — lecture O(1) en Room
3. `GENERIC_FALLBACK_TEXTS` — filet de sécurité final
**And** le Conseil s'affiche automatiquement sur le `MatchScreen` du Phone sans tap ni déverrouillage (NFR-UX4)
**And** l'affichage se produit en ≤ 3 secondes après le `game_over` (NFR-P1)
**And** le Conseil contient ≤ 3 phrases (NFR-UX2)
**And** le Conseil référence au moins un élément de contexte réel : score, surface, `WorkAxis` actif, ou résultat récent (FR-4 personnalisation)
**And** la source est tracée en interne : `GEMINI` | `CACHE` | `STATIC` (Timber DEBUG, non affiché à l'utilisateur)
**Given** le Conseil est affiché
**Then** `CoachingCachePrefetcher.refreshPostChangeover()` se déclenche en background non-bloquant pour rafraîchir les patterns probables selon l'état courant du match

---

## Epic 4: Historique & Statistiques

Le joueur accède à l'historique complet de ses matchs avec statistiques agrégées (win rate, surfaces, séquences). Il peut saisir des matchs joués sans l'app (saisie rétrospective). Toutes les données sont consultables hors connexion.

### Story 4.1: Historique des Sessions

As a player,
I want to browse all my sessions sorted by most recent first with key info visible at a glance,
So that I can review my match history anytime, including offline.

**Acceptance Criteria:**

**Given** je suis sur l'écran Historique
**When** la liste se charge
**Then** toutes les Sessions sont affichées, triées par date décroissante (plus récente en premier)
**And** chaque entrée affiche : date, adversaire (si renseigné), surface, score final (si clôturée), résultat (Victoire / Défaite / N/A), type de compétition (si renseigné)
**And** les Sessions incomplètes (interrompues sans clôture formelle) sont visibles avec un indicateur de statut distinct (ex : "En cours" ou "Interrompue")
**And** le chargement de l'historique avec < 200 Sessions prend ≤ 1 seconde (NFR-P3, index `idx_sessions_surface` Room)
**And** l'historique est entièrement consultable hors connexion (données Room locales — NFR-OFF2)
**When** je tape sur une Session
**Then** un écran de détail affiche toutes ses métadonnées et les Conseils générés pendant ce match (lus depuis Room)

### Story 4.2: Statistiques agrégées

As a player,
I want to see my aggregated match stats (win rate, surface breakdown, streaks),
So that I can track my progression at a glance without needing to count manually.

**Acceptance Criteria:**

**Given** je suis sur l'écran Statistiques
**When** les stats se chargent
**Then** le win rate global est affiché (Sessions Match clôturées avec résultat enregistré uniquement)
**And** le win rate par surface est affiché uniquement si ≥ 3 matchs sur cette surface — sinon "Données insuffisantes"
**And** le nombre de Sessions par type est affiché (Match / Entraînement)
**And** la séquence active de victoires ou de défaites consécutives est affichée
**When** une nouvelle Session Match est clôturée
**Then** toutes les stats se recalculent automatiquement
**And** les statistiques sont consultables hors connexion (calculées depuis Room — NFR-OFF2)
**And** les stats incluent les Sessions saisies manuellement (Story 4.3)

### Story 4.3: Saisie manuelle rétrospective

As a player,
I want to add a match I played without the app by entering it manually,
So that my history and statistics are complete even for matches played before or without SecondServe.

**Acceptance Criteria:**

**Given** je suis sur l'écran Historique
**When** je tape "Ajouter un match passé"
**Then** un formulaire s'affiche avec les mêmes champs qu'une session démarrée en temps réel (FR-1 : surface, format, adversaire, type compétition, tournoi) plus : score final (ex : "6-3, 4-6, 7-5"), résultat (Victoire / Défaite), date du match
**When** je soumets le formulaire
**Then** la session apparaît dans l'historique (Story 4.1) avec les données saisies
**And** elle est incluse dans toutes les statistiques agrégées (Story 4.2)
**And** la session est persistée en Room et mise en queue de Sync (SyncWorker — Story 2.6)
**And** la saisie est accessible depuis l'historique sans passer par le Mode Match
**And** aucune connexion réseau n'est requise pour créer une session rétrospective

---

## Epic 5: Coaching IA hors match

Après chaque match, une analyse IA personnalisée est générée automatiquement (Mistral API via VPS). Après 3 matchs, une synthèse transversale est disponible. Le joueur gère ses axes de travail (3 actifs max), avec suggestions IA.

### Story 5.1: VpsMistralEngine & routing Mistral via VPS

As a developer,
I want a `VpsMistralEngine` that proxies all Mistral API calls through the VPS,
So that the Mistral API key never appears in the Android app.

**Acceptance Criteria:**

**Given** `VpsMistralEngine` est initialisé
**When** `generate(prompt)` est appelé
**Then** il envoie `POST /api/v1/coaching/analyze` au VPS (header JWT)
**And** le VPS appelle `mistral-small-latest` via `mistral_client.py` (httpx async, timeout 15s, 1 retry)
**And** la réponse est retournée à l'app en ≤ 10 secondes (NFR-P4)
**And** la clé Mistral API est stockée uniquement côté VPS (variable d'environnement `MISTRAL_API_KEY` — jamais dans l'APK)
**And** le payload envoyé à Mistral contient uniquement : `fft_ranking` (série), `opponent_name` (si fourni), `surface`, `format`, `coaching_context` — aucun identifiant personnel (NFR-C3, NFR-S5)
**Given** l'appel VPS échoue ou dépasse le timeout
**When** `generate()` retourne
**Then** `Result.Error` est retourné avec `ErrorCode.NETWORK_UNAVAILABLE`
**And** Hilt fournit `VpsMistralEngine` comme binding `InferenceEngine` pour les features hors-match (Coaching, Notifications)
**And** ce moteur active également le fallback Mistral in-match de FR-4 ("si réseau dispo") — chaîne : Gemini → VpsMistral → Cache → Static

### Story 5.2: Analyse post-match individuelle

As a player,
I want an individual AI analysis generated automatically after each match I close,
So that I immediately know what worked and what to focus on before the next session.

**Acceptance Criteria:**

**Given** je clôture une Session Match (Story 2.6)
**When** le réseau est disponible
**Then** `GeneratePostMatchAnalysisUseCase` se déclenche automatiquement
**And** il appelle `VpsMistralEngine` avec : surface, format, log des points, Axes de travail actifs, Profil joueur (série FFT + style)
**And** l'analyse générée contient : points forts observés, points faibles, écart avec les Axes actifs, 1-2 recommandations concrètes
**And** l'analyse référence des données spécifiques de la session (score, surface) — jamais de contenu générique (FR-10)
**And** elle est persistée en Room (table `coaching_analyses`, migration créée dans cette story) et consultable hors connexion
**And** elle est accessible depuis le détail de la Session (Story 4.1)
**Given** le réseau est indisponible à la clôture
**Then** la génération est mise en queue WorkManager et s'exécute au retour du réseau (NFR-OFF3)

### Story 5.3: Synthèse IA multi-matchs

As a player,
I want a multi-match AI synthesis generated automatically after 3 new sessions,
So that I can spot recurring patterns in my game that I can't see match by match.

**Acceptance Criteria:**

**Given** ≥ 3 nouvelles Sessions Match ont été clôturées depuis la dernière synthèse
**When** l'app se reconnecte ou le check background se déclenche
**Then** une synthèse est générée via `VpsMistralEngine` → VPS → Mistral
**And** elle contient : patterns récurrents sur la période, évolution par rapport à la synthèse précédente, axe d'amélioration prioritaire multi-matchs, recommandation structurée
**And** elle référence des données agrégées des sessions concernées — jamais de contenu générique (FR-11)
**And** elle est stockée en Room et consultable hors connexion
**And** elle est visuellement distincte des analyses individuelles dans l'écran Coaching
**When** je tape "Générer maintenant"
**Then** une synthèse est générée à la demande même si le seuil de 3 sessions n'est pas atteint

### Story 5.4: Axes de travail — Suggestions IA

As a player,
I want the app to suggest relevant work axes based on patterns found in my recent analyses,
So that my training focus is grounded in my actual game data, not just intuition.

**Acceptance Criteria:**

**Given** au moins 1 analyse post-match ou synthèse a été générée (Stories 5.2 ou 5.3)
**When** j'ouvre l'écran Axes de travail
**Then** les axes suggérés par l'IA sont affichés, clairement distingués des axes saisis manuellement (indicateur visuel distinct)
**And** les suggestions sont dérivées de l'analyse ou synthèse la plus récente — spécifiques à mes données (pas génériques)
**When** je tape "Accepter" sur un axe suggéré
**Then** il est ajouté à mes axes actifs (si < 3 actifs) et intégré dans le contexte IA dès la prochaine interaction
**When** je tape "Ignorer"
**Then** l'axe est rejeté et ne réapparaît pas
**And** si 3 axes sont déjà actifs, le bouton "Accepter" est désactivé avec le message "Maximum 3 axes actifs atteint"

---

## Epic 6: Notifications coaching contextualisées

L'app envoie des notifications personnalisées : conseil du jour (fréquence configurable) et rappel de préparation avant un match planifié. Chaque notification référence un élément réel du profil ou de l'historique. Aucune dépendance FCM.

### Story 6.1: Conseil du jour — NotificationWorker

As a player,
I want to receive a personalized coaching tip at a configurable frequency,
So that I stay engaged with my game between matches without being spammed.

**Acceptance Criteria:**

**Given** je suis sur l'écran Paramètres, section Notifications
**When** je configure la fréquence
**Then** les options disponibles sont : Quotidien / Tous les 2 jours / Hebdomadaire / Désactivé
**And** un `PeriodicWorkRequest` WorkManager est planifié avec la fréquence choisie
**When** `NotificationWorker` s'exécute
**Then** il génère le contenu via `VpsMistralEngine` si le réseau est disponible, sinon depuis les analyses stockées en Room
**And** la notification inclut au moins une référence spécifique : surface de prédilection, Axe de travail actif, ou résultat récent (FR-13)
**And** si aucune Session n'a été enregistrée depuis 30 jours, aucune notification n'est envoyée
**When** j'active le mode silencieux pour une période définie
**Then** aucune notification n'est envoyée pendant cette période
**And** le mode silencieux se désactive automatiquement à la date de fin configurée

### Story 6.2: Rappel pré-match & APScheduler VPS

As a player,
I want a preparation reminder before a planned match, with AI-generated coaching content,
So that I arrive mentally prepared with relevant tactical focus points.

**Acceptance Criteria:**

**Given** je crée une Session avec une date/heure future (match planifié)
**When** la session est sauvegardée
**Then** un `OneTimeWorkRequest` WorkManager est créé avec un délai calculé pour se déclencher X heures avant le match (configurable, défaut : 2h avant)
**And** si le réseau est disponible au déclenchement, le VPS APScheduler génère le contenu coaching pré-match via Mistral et le stocke
**And** `NotificationWorker` récupère le contenu généré via `GET /api/v1/notifications/pending`
**And** si le réseau est indisponible, un contenu de rappel générique est utilisé en fallback
**And** la notification contient : adversaire (si renseigné), surface, au moins 1 référence spécifique au profil (Axe de travail, classement, historique récent)
**And** si le match planifié est annulé ou la session supprimée, le `WorkRequest` correspondant est annulé
