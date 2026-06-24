---
baseline_commit: 4d97181
---

# Story TD-5 : Contrats API & Backend Hardening

Status: ready-for-dev

## Story

As a developer,
I want HTTP error codes to be semantically correct, Mistral misconfiguration to be detected early, and the FastAPI startup lifecycle to use the modern API,
So that API consumers can distinguish business errors from validation errors and operational issues are diagnosed quickly.

## Context

**Problème 1 — 422 pour erreur métier (MAX_WORK_AXES_REACHED)**
FastAPI retourne déjà 422 pour les erreurs de validation Pydantic avec un body `{"detail": [...]}` structuré. `WorkAxisService.create()` utilise aussi 422 pour la limite métier de 3 axes, mais avec un body différent `{"error_code": "...", "message": "..."}`. Un client API ne peut pas distinguer les deux types d'erreur par le code HTTP. La correction : utiliser 409 Conflict pour la limite métier.

**Problème 2 — `MISTRAL_API_KEY` vide → erreur opaque 401**
Si `settings.mistral_api_key = ""`, le client Mistral fait une requête avec `Authorization: Bearer ` et reçoit un 401 → `MISTRAL_ERROR`. Un opérateur en production ne peut pas distinguer "mauvaise clé" de "clé absente". Ajouter une guard clause dans le service ou l'endpoint pour retourner `MISTRAL_NOT_CONFIGURED` immédiatement.

**Problème 3 — `@app.on_event("startup"/"shutdown")` deprecated**
FastAPI >= 0.95.0 a déprécié `@app.on_event()` en faveur du `lifespan` context manager. Fonctionnel actuellement mais émettra des warnings de dépréciation lors du prochain upgrade FastAPI.

**PII — `buildMatchContextProfile()` : vérifié, conforme NFR-C3/S5**
Audit réalisé : `buildMatchContextProfile()` ne retourne que `fftSeries` (classement, ex: "15/2"), `playStyle`, `preferredSurfaces`, `coachInstructions`, `activeWorkAxes`. La **licence FFT** est stockée dans `EncryptedSharedPreferences` via `PlayerDataStore` et n'est **jamais** incluse dans le contexte profil envoyé au VPS. Aucune correction nécessaire.

Source : deferred items 1-6 p2 (422→409), 5-1 (MISTRAL_API_KEY), 1-3 patch (`lifespan`), 5-1 (PII check).

## Acceptance Criteria

1. **Given** l'utilisateur a déjà 3 axes de travail et tente d'en créer un 4e
   **When** `POST /api/v1/work-axes` est appelé
   **Then** le backend retourne HTTP 409 Conflict avec `{"error_code": "MAX_WORK_AXES_REACHED", "message": "..."}`

2. **Given** `MISTRAL_API_KEY` est vide (`""`) dans les settings
   **When** `POST /api/v1/coaching/analyze` est appelé
   **Then** le backend retourne immédiatement `{"error_code": "MISTRAL_NOT_CONFIGURED", "message": "Mistral API key not configured"}` avec HTTP 503

3. **Given** FastAPI démarre
   **When** le serveur s'initialise
   **Then** aucun warning de dépréciation `@app.on_event` n'est émis dans les logs

## Tasks / Subtasks

---

### BLOC A — 422 → 409 pour MAX_WORK_AXES_REACHED

- [ ] **T1 — Modifier `work_axes/service.py`**
  - [ ] T1.1 Dans `backend/app/features/work_axes/service.py`, changer :
    ```python
    # Avant
    raise HTTPException(
        status_code=422,
        detail={
            "error_code": "MAX_WORK_AXES_REACHED",
            "message": f"Maximum {MAX_WORK_AXES} axes actifs atteint"
        }
    )
    # Après : utiliser SecondServeException pour cohérence avec le handler global
    from app.shared.exceptions import SecondServeException
    raise SecondServeException(
        error_code="MAX_WORK_AXES_REACHED",
        message=f"Maximum {MAX_WORK_AXES} axes actifs atteint",
        status_code=409
    )
    ```
  - [ ] T1.2 Vérifier que `SecondServeException` est importé dans ce fichier (ou ajouter l'import)
  - [ ] T1.3 Supprimer l'import `HTTPException` si désormais inutilisé dans ce fichier

- [ ] **T2 — Adapter le client Android**
  - [ ] T2.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/WorkAxisRepositoryImpl.kt`, vérifier comment le code d'erreur est géré côté Android
  - [ ] T2.2 Si le client catch une `HttpException` et vérifie `errorCode == 422`, mettre à jour pour vérifier `409` :
    ```kotlin
    // Chercher dans WorkAxisRepositoryImpl.kt les comparaisons de code HTTP
    // Typiquement : if (e.code() == 422 && ...) → if (e.code() == 409 && ...)
    ```
  - [ ] T2.3 Si le client est fire-and-forget (pas de vérification du code), aucune modification nécessaire

- [ ] **T3 — Tests backend existants**
  - [ ] T3.1 Dans `backend/tests/`, chercher les tests qui assertent `status_code=422` pour `MAX_WORK_AXES_REACHED` :
    ```bash
    grep -rn "MAX_WORK_AXES\|422" backend/tests/
    ```
  - [ ] T3.2 Mettre à jour ces tests pour asserter `status_code=409`

---

### BLOC B — Guard `MISTRAL_API_KEY` vide

- [ ] **T4 — Ajouter guard dans `coaching/service.py`**
  - [ ] T4.1 Dans `backend/app/features/coaching/service.py`, ajouter la guard clause :
    ```python
    from app.shared.exceptions import SecondServeException
    
    async def analyze(prompt: str, api_key: str) -> str:
        if not api_key:
            raise SecondServeException(
                error_code="MISTRAL_NOT_CONFIGURED",
                message="Mistral API key not configured",
                status_code=503
            )
        return await mistral_client.generate(prompt, api_key)
    ```

- [ ] **T5 — Ajouter la même guard dans le scheduler des notifications**
  - [ ] T5.1 Dans `backend/app/features/notifications/scheduler.py`, localiser l'appel `generate_pending_for_upcoming(db, settings.mistral_api_key)` et vérifier que le service appelé gère le cas `api_key = ""`
  - [ ] T5.2 Si le scheduler appelle directement `mistral_client.generate()` ou `service.analyze()`, la guard T4 couvre déjà ce chemin

---

### BLOC C — Migration `@app.on_event` → `lifespan`

- [ ] **T6 — Refactoriser `main.py` avec `contextlib.asynccontextmanager`**
  - [ ] T6.1 Dans `backend/app/main.py`, remplacer :
    ```python
    # Avant
    @app.on_event("startup")
    async def startup_event() -> None:
        from app.core.security import JWTManager
        JWTManager(settings.jwt_secret)
        start_scheduler()

    @app.on_event("shutdown")
    async def shutdown_event() -> None:
        stop_scheduler()
    ```
    Par :
    ```python
    # Après
    from contextlib import asynccontextmanager
    
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # startup
        from app.core.security import JWTManager
        JWTManager(settings.jwt_secret)
        start_scheduler()
        yield
        # shutdown
        stop_scheduler()
    
    app = FastAPI(
        title="SecondServe Backend",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs" if settings.debug else None,
        redoc_url="/redoc" if settings.debug else None,
        openapi_url="/openapi.json" if settings.debug else None,
    )
    ```
  - [ ] T6.2 Vérifier que `stop_scheduler()` est importé dans `main.py` (chercher l'import existant de `start_scheduler, stop_scheduler`)

---

### BLOC D — PII audit (documentation)

- [ ] **T7 — Documenter la conformité NFR-C3/S5 dans le code**
  - [ ] T7.1 Dans `android/data/src/main/kotlin/com/secondserve/data/repository/PlayerProfileRepositoryImpl.kt`, à la fin de la fonction `buildMatchContextProfile()`, ajouter un commentaire :
    ```kotlin
    // NFR-C3/S5: fftLicenseNumber is excluded — stored in EncryptedSharedPreferences only,
    // never included in VPS-bound context.
    ```
  - [ ] T7.2 Ce commentaire sert de marqueur d'audit pour les reviews de sécurité futures

---

### BLOC E — Tests & validation

- [ ] **T8 — Tests backend**
  - [ ] T8.1 `cd backend && python -m pytest tests/ -v` — tous les tests doivent passer

- [ ] **T9 — Vérification runtime**
  - [ ] T9.1 Démarrer le backend localement : `cd backend && uvicorn app.main:app --reload`
  - [ ] T9.2 Vérifier dans les logs qu'aucun warning `DeprecationWarning` n'est émis au démarrage

## Dev Notes

- `SecondServeException` est déjà géré par le handler global dans `main.py` qui retourne `{"error_code": ..., "message": ..., "detail": ...}` — c'est plus cohérent que `HTTPException` directe
- Pour le `lifespan` : s'assurer que `app = FastAPI(...)` est défini APRÈS la fonction `lifespan` (Python ne supporte pas la forward reference ici)
- Le changement 422→409 est techniquement un breaking change pour les clients Android — mais comme le client Android est fire-and-forget sur cette erreur (T2.2), l'impact est minimal
- La guard `MISTRAL_NOT_CONFIGURED` (T4) est particulièrement utile lors du premier déploiement si la variable d'environnement `MISTRAL_API_KEY` est oubliée

## Deferred items adressés

- `1-6 p2` — Status 422 pour limite métier → 409 Conflict
- `5-1` — `MISTRAL_API_KEY` vide → erreur opaque → guard `MISTRAL_NOT_CONFIGURED`
- `1-3 patch` — `@app.on_event("startup")` deprecated → migration vers `lifespan`
- `5-1` — `buildMatchContextProfile()` PII verification → confirmé conforme, documenté
