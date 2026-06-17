# Deferred Work

## Deferred from: code review of 2-1-tennisscoreengine-automate-a-etats-finis — Passe 2 (2026-06-17)

- **Couplage structurel `winner` dans `awardSet` (D5)** — Le match winner est pris du paramètre `winner` (gagnant du dernier set), pas recalculé depuis `setsWonA/B`. Identique à D4 de la passe 1. Correct pour tout flux actuel, risque de régression sur refactoring futur. [`TennisScoreEngine.kt:awardSet`]

## Deferred from: code review of 2-1-tennisscoreengine-automate-a-etats-finis (2026-06-17)

- **`MatchOver` ne transporte pas de signal changeover** — Quand un point termine à la fois le set et le match, seul `MatchOver` est émis. Story 2.5 (DataLayer bridge) a besoin du signal changeover pour déclencher `game_over`. Il faudra soit ajouter `changeover: Boolean` à `MatchOver`, soit émettre `SetWon` puis `MatchOver` en séquence. [`TennisScoreEngine.kt:awardSet`]
- **Changeover au début d'un nouveau set non modélisé** — En tennis ATP/WTA, les joueurs changent de côté au début de chaque set (si nécessaire). L'engine calcule le changeover uniquement sur la parité du total jeux dans le set terminé, pas selon le protocole complet ATP. À traiter dans Story 2.5 ou lors de l'implémentation du changeover Watch. [`TennisScoreEngine.kt:awardSet`]
- **Combinaison invalide `BEST_OF_1 + SHORT_DECISIVE_SET` non protégée** — `isFinalSet()` retourne toujours `true` pour BEST_OF_1, activant silencieusement les règles SHORT_DECISIVE_SET sur l'unique set. Aucune validation du format à la construction. Latent, non utilisé en pratique. [`TennisScoreEngine.kt:isFinalSet`, `SessionFormat.kt`]
- **Couplage structurel `winner` dans `awardSet`** — Le match winner est pris du paramètre `winner` (le gagnant du dernier set), pas recalculé depuis `setsWonA/B`. Correct pour tout flux actuel, mais risque de régression si un futur refactoring fait diverger le paramètre de la réalité des sets. [`TennisScoreEngine.kt:awardSet`]

## Deferred from: code review of 1-6-axes-de-travail-crud-de-base — Passe 2 (2026-06-17)

- **`deleteWorkAxis` retourne `AppResult.Success` pour un id inexistant** — Room `DELETE WHERE id = :id` est un no-op silencieux si aucune ligne ne correspond. Le ViewModel émet `WorkAxisDeleted` pour un axe qui n'existait pas. Aucun test Android ne couvre ce cas (contrairement au backend qui a `test_delete_nonexistent_axis`). Acceptable en usage single-user où le ViewModel expose uniquement des axes existants. [`android/data/.../WorkAxisRepositoryImpl.kt:54`]
- **Pas de dialog de confirmation avant suppression** — Le bouton "Supprimer" dans `WorkAxisCard` déclenche immédiatement `deleteWorkAxis()` sans confirmation. Opération irréversible depuis l'UI. Hors AC, amélioration UX à planifier. [`android/feature/profile/.../WorkAxesScreen.kt:WorkAxisCard`]
- **`created_at` envoyé dans PUT VPS mais ignoré côté serveur** — Android envoie `created_at: existing.createdAt` dans les requêtes PUT, mais `WorkAxisRepository.update()` côté VPS n'utilise que le `title`. Le champ est accepté par le schéma Pydantic `WorkAxisRequest` commun POST/PUT mais est superflu en PUT. Nécessite un DTO séparé `WorkAxisUpdateRequest` si la spec évolue. [`android/data/.../WorkAxisRepositoryImpl.kt:45`, `backend/app/features/work_axes/repository.py:update()`]
- **`localId` retourné par `dao.insert()` ignoré** — `dao.insert(entity)` retourne l'ID autoincrement Room, non utilisé. Pas de mapping Android ID → VPS ID. Acceptable tant qu'il n'y a pas de réconciliation multi-device. [`android/data/.../WorkAxisRepositoryImpl.kt:29`]
- **`CancellationException` swallowée dans les repositories** — Les blocs `try/catch (e: Exception)` dans `createWorkAxis`, `updateWorkAxis`, `deleteWorkAxis` capturent `CancellationException`. Problème systémique présent dans tous les repositories du projet. Requiert de rethrow CancellationException ou d'utiliser `catch (e: Exception) { if (e is CancellationException) throw e; ... }`. [`android/data/.../WorkAxisRepositoryImpl.kt`]
- **Status 422 pour limite métier (`MAX_WORK_AXES_REACHED`) confond sémantiquement avec 422 Pydantic** — FastAPI retourne déjà 422 pour les erreurs Pydantic avec un body `{"detail": [...]}` structuré différemment. `MAX_WORK_AXES_REACHED` retourne aussi 422 mais avec `{"detail": {"error_code": "...", "message": "..."}}`. Le client Android catch toute exception en fire-and-forget et ne différencie pas. Requiert 409 Conflict pour séparer les erreurs métier des erreurs de validation. Décision architecture à uniformiser sur toutes les features. [`backend/app/features/work_axes/service.py:create()`]

## Deferred from: code review of 1-6-axes-de-travail-crud-de-base (2026-06-17)

- **TOCTOU dans `WorkAxisService.create()` count check VPS** — `count()` et `create()` sont deux opérations séparées sans verrouillage. Sous concurrence (peu probable dans un contexte single-user), deux requêtes simultanées peuvent insérer plus de 3 axes. Même pattern que `update_profile_details()`. Requiert `SELECT FOR UPDATE` ou contrainte unique si multi-user. [`backend/app/features/work_axes/service.py:create()`]
- **Race condition Android — check `isAtMaxCapacity` non atomique** — L'état Flow peut ne pas refléter un insert en cours entre la vérification ViewModel et l'insert Room. Pattern inhérent à l'architecture Flow/Orbit; room insérera le 4e localement avant que le Flow émette, VPS rejettera. Acceptable pour un usage single-user séquentiel. [`android/feature/profile/.../WorkAxesViewModel.kt`]
- **`created_at` non contraint UNIQUE côté VPS** — Si deux axes sont créés en moins d'1ms, le même epoch ms est envoyé au VPS pour deux lignes distinctes. La réconciliation future par `created_at` serait ambiguë. À traiter si une synchronisation multi-device est ajoutée. [`backend/app/features/work_axes/models.py`]
- **Pas d'empty-state UI dans `WorkAxesScreen`** — Quand la liste est vide, seul le texte "0/3 axes actifs" s'affiche. Pas d'illustration ni de texte d'invitation à créer. Amélioration UX non spécifiée dans les AC. [`android/feature/profile/.../WorkAxesScreen.kt`]
- **Records VPS orphelins sur échec réseau lors d'un delete** — Si Room supprime localement mais que le DELETE VPS échoue (réseau absent), les axes existent encore sur le VPS. À la reconnexion, la limite de 3 côté VPS sera atteinte avant le côté Android. À résoudre avec une file de sync offline. Pattern fire-and-forget documenté dans spec, hors scope Story 1.6. [`android/data/.../repository/WorkAxisRepositoryImpl.kt`]

## Deferred from: code review of 1-5-profil-joueur-style-de-jeu-donnees-complementaires — Passe 2 (2026-06-16)

- **Ordre non déterministe `selectedSurfaces.toList()`** — `toSet()` sur `List<String>` produit un `LinkedHashSet` (ordre d'insertion stable en session), mais les toggles de `FilterChip` utilisent `SnapshotStateSet` dont l'ordre d'itération n'est pas garanti par contrat. Le CSV stocké peut différer entre sessions pour la même sélection logique. Cosmétique, pas d'impact fonctionnel. [`android/feature/profile/.../ProfileScreen.kt:PlayStyleSection`]
- **`LaunchedEffect` écrase les éditions en cours lors d'un rechargement** — Si `loadProfile()` se termine pendant que l'utilisateur saisit dans les champs surfaces/style/instructions, les `LaunchedEffect` déclenchés par les nouveaux états ViewModel écrasent silencieusement les valeurs locales en cours d'édition. Nécessite un suivi "dirty" ou une désactivation du `LaunchedEffect` après première saisie. [`android/feature/profile/.../ProfileScreen.kt:PlayStyleSection`]
- **Save hors-ligne peut effacer play_style/preferred_surfaces sur le VPS** — `saveProfileDetails()` envoie tous les champs depuis l'état UI courant (potentiellement null si `loadProfile()` a échoué). Contrairement à `saveRanking()` qui lit depuis Room pour préserver les champs existants, `saveProfileDetails()` fait confiance à l'état UI pour tous les champs. Pattern offline-first pré-existant. [`android/data/.../repository/PlayerProfileRepositoryImpl.kt`]
- **`EncryptedSharedPreferences` exceptions post-init non catchées dans ViewModel** — `saveFftLicenseNumber()`/`getFftLicenseNumber()` sont appelées de manière synchrone dans des blocs Orbit `intent {}` sans try/catch. Un reset du KeyStore Android (factory reset protection, root, certains OS upgrades) peut lever une `GeneralSecurityException` hors du catch d'initialisation, crashant le coroutine scope. Pattern identique à `JwtTokenStore.kt`, acceptable pour MVP. [`android/data/.../local/PlayerDataStore.kt`, `android/feature/profile/.../ProfileViewModel.kt`]

## Deferred from: code review of 1-5-profil-joueur-style-de-jeu-donnees-complementaires (2026-06-16)

- **Race read-modify-write dans `saveRanking()`/`saveProfileDetails()`** — Les deux méthodes font un `getProfile()` puis `upsertProfile()` sans transaction englobante. En cas d'appels concurrents, la deuxième écriture peut écraser la première. Pattern identique au `saveRanking()` de Story 1.4, à corriger via un DAO `UPDATE SET` ciblé ou un Mutex au niveau repository. [`android/data/.../repository/PlayerProfileRepositoryImpl.kt`]
- **`loadProfile()` lit Room uniquement — divergence fresh-install** — `getProfile()` ne consulte jamais le VPS. Après réinstallation, les données profil (style de jeu, surfaces, consignes) sauvegardées sur le serveur ne sont pas récupérées jusqu'à la prochaine saisie utilisateur. Architecture offline-first intentionnelle depuis Story 1.4 — à reconsidérer si un "sync on install" est ajouté. [`android/data/.../repository/PlayerProfileRepositoryImpl.kt`]
- **`PlayStyleConstants` dupliqué Android (Kotlin) / backend (Python)** — Aucune source de vérité partagée. Si un style est ajouté d'un côté, l'autre peut rester désynchronisé sans erreur de compilation. À adresser avec une génération OpenAPI ou un contrat partagé. [`android/.../model/PlayStyleConstants.kt`, `backend/app/features/profile/schemas.py`]
- **Race concurrent PUT /profile/details backend** — `update_profile_details()` fait un SELECT puis un UPDATE en mémoire sans verrouillage ligne. Deux requêtes simultanées peuvent s'écraser mutuellement (last-writer-wins). Requiert `SELECT FOR UPDATE` ou UPSERT atomique. Pré-existant dans Story 1.4. [`backend/app/features/profile/repository.py`]
- **`apply()` async dans `saveFftLicense()`** — Cohérent avec `JwtTokenStore.kt`. Risque : si le process est tué juste après `reduce {}`, le state reflète la nouvelle licence mais l'écriture chiffée peut ne pas être commitée. Acceptable pour MVP. [`android/feature/profile/.../ProfileViewModel.kt`]

## Deferred from: code review of 1-4-classement-fft-saisie-et-historique — Passe 2 (2026-06-16)

- **Skew de timestamp backend dans `ProfileRepository`** — `upsert_profile_ranking` et `insert_ranking_history` capturent chacun `now = int(time.time() * 1000)` indépendamment. Skew de quelques ms possible entre `PlayerProfile.updated_at` et `RankingHistory.recorded_at`. Cosmétique, pas de correction prioritaire. [`backend/app/features/profile/repository.py`]

## Deferred from: code review of 1-4-classement-fft-saisie-et-historique (2026-06-16)

- **`runBlocking` dans `TokenAuthenticator` + race condition `reauthenticate()` non mutex** — Pré-existant Story 1.3. Deadlock potentiel si le pool OkHttp est saturé par plusieurs 401 simultanés. À corriger avec `Mutex` dans Story 1.3 ou au moment d'un audit sécurité. [`android/data/.../api/TokenAuthenticator.kt`, `android/data/.../auth/AuthRepository.kt`]
- **`saveToken()` utilise `apply()` async** — Pré-existant Story 1.3. Token peut ne pas être persisté avant la prochaine lecture sur device lent. À remplacer par `commit()` avec gestion d'erreur. [`android/data/.../security/JwtTokenStore.kt`]
- **Room schema JSON version 1 non committé** — Nécessite un build Android pour générer `android/data/schemas/com.secondserve.data.local.db.SecondServeDatabase/1.json`. À committer après le premier build réussi pour permettre les tests de migration CI futurs.
- **`navController.popBackStack()` sur la seule destination** — Navigation provisoire Story 1.4. La destination "profile" est la seule dans `NavHost` ; `popBackStack()` est un no-op silencieux. À corriger quand l'AppNavGraph sera complété avec les routes de l'Epic 2+. [`android/app/.../navigation/AppNavGraph.kt`]

## Deferred from: patch review of 1-3-jwt-authentication-android-vps (2026-06-16)

- **`reauthenticate()` non protégée par mutex → ré-auth concurrente** — Plusieurs 401 simultanés peuvent déclencher plusieurs `POST /auth/init` et des races sur `saveToken()`. Risque négligeable pour MVP mono-utilisateur. [`android/data/src/main/kotlin/com/secondserve/data/remote/auth/AuthRepository.kt`]
- **`@app.on_event("startup")` deprecated dans FastAPI** — Fonctionnel actuellement ; migrer vers `lifespan` context manager lors du prochain upgrade FastAPI. [`backend/app/main.py`]

## Deferred from: code review of 1-3-jwt-authentication-android-vps (2026-06-15)

- **`JWTManager` instancié à chaque requête (pas de singleton)** — Impact performance négligeable pour MVP mono-utilisateur. À refactoriser si le volume augmente. [`backend/app/core/security.py`]
- **`VpsApiServiceTest` teste la réflexion plutôt que le comportement Retrofit** — Tests smoke sans valeur réelle. À remplacer par des tests MockWebServer quand l'infra de test le permettra. [`android/data/src/test/.../VpsApiServiceTest.kt`]

## Deferred from: code review of 1-2-setup-fastapi-backend (2026-06-13)

- **`os.environ.setdefault` fragile avec imports anticipés** — Fonctionnel en usage pytest standard ; à surveiller si pytest-xdist est adopté. [`backend/tests/conftest.py`]
- **Commit automatique dans `get_db` pour 4xx sans exception** — Pattern FastAPI standard acceptable pour le scaffold ; à réévaluer quand de vraies routes avec logique métier partielle seront implémentées. [`backend/app/core/database.py`]
- **`Base.metadata` vide sans imports de modèles dans `alembic/env.py`** — Par design, commentaire explicite dans le code ; à compléter story par story au fur et à mesure des modèles SQLAlchemy. [`backend/alembic/env.py`]
- **`settings = Settings()` singleton module-level** — Pattern pydantic-settings standard ; à refactoriser en `@lru_cache` si des besoins de test multi-config émergent. [`backend/app/core/config.py`]
- **`mistral_api_key` vide sans validation au démarrage** — Validation intentionnellement absente car Epic 5 (MistralEngine) non encore implémentée. [`backend/app/core/config.py`]
- **`asyncio.run()` dans `run_migrations_online` incompatible event loop active** — Alembic est un outil CLI ; ne pas appeler programmatiquement depuis un contexte async. [`backend/alembic/env.py`]
- **CI sans test `alembic downgrade`** — Migration initiale vide (pass) ; à ajouter dans la CI quand les premières vraies migrations seront créées. [`.github/workflows/ci-backend.yml`]
- **`/health` sans DB check** — Scaffold acceptable ; à améliorer en ajoutant `SELECT 1` sur la DB quand des services persistants seront actifs. [`backend/app/api/v1/router.py`]

## Deferred from: code review of 1-1-setup-android-multi-module-gradle (2026-06-12)

- **Orbit 9.0.0 existence** — Vérifier que `org.orbit-mvi:orbit-core:9.0.0` est disponible sur Maven Central (version au-delà du knowledge cutoff du reviewer). [`android/gradle/libs.versions.toml:10`]
- **hilt-navigation-compose 1.2.0 vs Navigation 2.9.0** — Confirmer la compatibilité runtime entre ces deux versions avant d'implémenter la navigation. [`android/gradle/libs.versions.toml:8,17`]
- **domain.Result sans variante Loading** — Décision de design : ajouter `Loading` avant d'implémenter les ViewModels Orbit pour éviter des wrappers incohérents entre feature modules. [`android/domain/src/main/kotlin/com/secondserve/domain/Result.kt`]
- **Wear Compose foundation (1.5.0) / material3 (1.6.2) version skew** — Vérifier dans les release notes Wear Compose que 1.5.0 est la bonne version de foundation pour material3 1.6.2. [`android/gradle/libs.versions.toml:13-14`]
- **allowBackup sans règles de backup** — Définir `android:dataExtractionRules` ou `android:fullBackupContent` avant la mise en production, surtout une fois Room ajouté. [`android/app/src/main/AndroidManifest.xml:7`]
- **InferenceEngine sans binding Hilt** — `AppModule` vide, aucun `@Binds` pour `InferenceEngine`. Toute injection déclenchera `[Dagger/MissingBinding]`. À adresser en Story 3.1. [`android/app/src/main/kotlin/com/secondserve/di/AppModule.kt`]
- **Permissions Wear manquantes** — `BODY_SENSORS` et `ACTIVITY_RECOGNITION` absents du manifest Wear. À ajouter dans les stories concernant la capture de données capteurs. [`android/wear/src/main/AndroidManifest.xml`]
