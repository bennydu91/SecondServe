# Deferred Work

## Deferred from: code review of 1-1-setup-android-multi-module-gradle (2026-06-12)

- **Orbit 9.0.0 existence** — Vérifier que `org.orbit-mvi:orbit-core:9.0.0` est disponible sur Maven Central (version au-delà du knowledge cutoff du reviewer). [`android/gradle/libs.versions.toml:10`]
- **hilt-navigation-compose 1.2.0 vs Navigation 2.9.0** — Confirmer la compatibilité runtime entre ces deux versions avant d'implémenter la navigation. [`android/gradle/libs.versions.toml:8,17`]
- **domain.Result sans variante Loading** — Décision de design : ajouter `Loading` avant d'implémenter les ViewModels Orbit pour éviter des wrappers incohérents entre feature modules. [`android/domain/src/main/kotlin/com/secondserve/domain/Result.kt`]
- **Wear Compose foundation (1.5.0) / material3 (1.6.2) version skew** — Vérifier dans les release notes Wear Compose que 1.5.0 est la bonne version de foundation pour material3 1.6.2. [`android/gradle/libs.versions.toml:13-14`]
- **allowBackup sans règles de backup** — Définir `android:dataExtractionRules` ou `android:fullBackupContent` avant la mise en production, surtout une fois Room ajouté. [`android/app/src/main/AndroidManifest.xml:7`]
- **InferenceEngine sans binding Hilt** — `AppModule` vide, aucun `@Binds` pour `InferenceEngine`. Toute injection déclenchera `[Dagger/MissingBinding]`. À adresser en Story 3.1. [`android/app/src/main/kotlin/com/secondserve/di/AppModule.kt`]
- **Permissions Wear manquantes** — `BODY_SENSORS` et `ACTIVITY_RECOGNITION` absents du manifest Wear. À ajouter dans les stories concernant la capture de données capteurs. [`android/wear/src/main/AndroidManifest.xml`]

## Deferred from: code review of 1-2-setup-fastapi-backend (2026-06-13)

- **Systemd hardening absent** — Ajouter `NoNewPrivileges=true`, `ProtectSystem=strict`, `PrivateTmp=true`, `ReadWritePaths=/opt/secondserve-backend` au service. [`backend/secondserve-backend.service`]
- **CORSMiddleware absent** — Ajouter `CORSMiddleware` si un frontend web est un jour ajouté (actuellement mobile direct, non requis). [`backend/app/main.py`]
- **anyio backend non épinglé** — Risque de flakiness future si la version anyio change et que le backend bascule. Épingler `anyio_backends = ["asyncio"]` dans pyproject.toml. [`backend/pyproject.toml`]
- **Singletons module-level `engine` / `AsyncSessionLocal`** — Tout code contournant `get_db` et important `AsyncSessionLocal` directement utilisera la DB prod même en test. Pattern à documenter. [`backend/app/core/database.py`]
- **`mistral_api_key` vide par défaut** — Ajouter un log WARNING au démarrage si la clé est vide, pour alerter en cas de misconfiguration lors de l'implémentation Epic 5. [`backend/app/core/config.py`]
