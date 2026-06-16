# Deferred Work

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
