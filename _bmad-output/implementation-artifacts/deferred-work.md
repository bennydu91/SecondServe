# Deferred Work

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
