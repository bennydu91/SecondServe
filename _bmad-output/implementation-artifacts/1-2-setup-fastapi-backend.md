---
baseline_commit: "f93d368caf3fef140d5274206dac4ce030f00c91"
---

# Story 1.2: Setup FastAPI Backend

Status: review

## Story

As a developer,
I want a FastAPI backend deployed on the VPS with Nginx reverse proxy and HTTPS,
so that the Android app has a secure endpoint to communicate with.

## Acceptance Criteria

1. **Given** le VPS (4 vCPU / 16 Go RAM) est accessible
   **When** le setup est complet
   **Then** `secondserve-backend.service` tourne via systemd et survive un redémarrage
   *(Vérification manuelle requise sur le VPS — les fichiers de config doivent être créés localement)*

2. **And** `GET https://<vps-domain>/api/v1/health` retourne `{"status": "ok"}` HTTP 200
   *(Vérifiable localement via pytest + httpx ; HTTPS requiert le VPS)*

3. **And** Nginx est configuré en reverse proxy vers FastAPI (port 8000), HTTPS actif (certificat Let's Encrypt valide)
   *(Config Nginx créée localement ; activation certbot sur VPS uniquement)*

4. **And** la structure feature-based est en place : `backend/app/features/` contient `auth/`, `sessions/`, `profile/`, `coaching/`, `sync/`, `notifications/`

5. **And** Alembic est configuré (`alembic.ini` + `alembic/env.py` pointant sur la base SQLite)

6. **And** les variables d'environnement sont documentées dans `backend/.env.example` : `JWT_SECRET`, `MISTRAL_API_KEY`, `DATABASE_URL`

## Tasks / Subtasks

- [x] Task 1 — Initialiser le projet uv dans `backend/` (AC: 4, 6)
  - [x] Supprimer le `.gitkeep` et initialiser `uv init` dans `backend/` avec `project.name = "secondserve-backend"`
  - [x] Ajouter les dépendances production : `fastapi[standard]`, `sqlalchemy[asyncio]`, `alembic`, `pydantic-settings`
  - [x] Ajouter les dépendances dev : `pytest`, `pytest-asyncio`, `httpx`, `anyio`
  - [x] Vérifier que `pyproject.toml` cible Python `>=3.12`
  - [x] Créer `backend/.env.example` avec `JWT_SECRET=`, `MISTRAL_API_KEY=`, `DATABASE_URL=sqlite+aiosqlite:///./secondserve.db`
  - [x] Créer `backend/.gitignore` excluant `.env`, `*.db`, `.venv/`, `__pycache__/`, `.pytest_cache/`

- [x] Task 2 — Créer la structure de répertoires complète (AC: 4)
  - [x] `backend/app/__init__.py`
  - [x] `backend/app/api/__init__.py` + `backend/app/api/v1/__init__.py`
  - [x] Créer `backend/app/api/v1/router.py` (APIRouter principal qui inclut les sous-routers)
  - [x] Créer stubs `backend/app/api/v1/auth.py`, `sessions.py`, `profile.py`, `coaching.py`, `sync.py`, `notifications.py` (chaque fichier avec un `APIRouter` vide + un commentaire indiquant la story qui l'implémentera)
  - [x] `backend/app/core/__init__.py`
  - [x] `backend/app/features/__init__.py`
  - [x] Créer `backend/app/features/auth/__init__.py` + `service.py` (stub vide)
  - [x] Créer `backend/app/features/sessions/__init__.py` + `models.py`, `schemas.py`, `repository.py`, `service.py` (stubs vides)
  - [x] Créer `backend/app/features/profile/__init__.py` + `models.py`, `schemas.py`, `repository.py`, `service.py` (stubs vides)
  - [x] Créer `backend/app/features/coaching/__init__.py` + `models.py`, `schemas.py`, `repository.py`, `service.py`, `mistral_client.py` (stubs vides)
  - [x] Créer `backend/app/features/sync/__init__.py` + `schemas.py`, `service.py` (stubs vides)
  - [x] Créer `backend/app/features/notifications/__init__.py` + `models.py`, `schemas.py`, `scheduler.py` (stubs vides)
  - [x] Créer `backend/app/shared/__init__.py` + `exceptions.py`
  - [x] `backend/tests/__init__.py` + `backend/tests/unit/__init__.py` + `backend/tests/integration/__init__.py`

- [x] Task 3 — Implémenter app FastAPI + endpoint health (AC: 2)
  - [x] Créer `backend/app/core/config.py` — classe `Settings` (pydantic-settings) avec `jwt_secret`, `mistral_api_key`, `database_url`, `debug=False`; instance singleton `settings = Settings()`
  - [x] Créer `backend/app/core/database.py` — `create_async_engine`, `async_sessionmaker`, `AsyncSession`, dependency `get_db`
  - [x] Créer `backend/app/main.py` avec `FastAPI` app, titre `"SecondServe Backend"`, montage du router v1
  - [x] Ajouter `GET /api/v1/health` dans `backend/app/api/v1/router.py` retournant `{"status": "ok"}` HTTP 200
  - [x] Configurer le logging dans `main.py` : `logging.basicConfig` avec `LOG_LEVEL` depuis settings (INFO prod, DEBUG dev)

- [x] Task 4 — Configurer Alembic (AC: 5)
  - [x] Exécuter `uv run alembic init alembic` dans `backend/` pour générer `alembic.ini` et `alembic/env.py`
  - [x] Modifier `alembic.ini` : `sqlalchemy.url = %(DATABASE_URL)s` (variable d'environnement, pas de valeur hardcodée)
  - [x] Modifier `alembic/env.py` pour support async (utiliser `run_async_migrations`, `AsyncEngine.connect()`) et charger les modèles SQLAlchemy
  - [x] Créer la première migration vide (`alembic/versions/`) avec `uv run alembic revision --autogenerate -m "initial"` — doit générer un fichier de migration sans erreur (même si aucun modèle n'est encore défini)
  - [x] Vérifier que `uv run alembic upgrade head` s'exécute sans erreur sur la base SQLite locale

- [x] Task 5 — Créer le fichier systemd et la config Nginx (AC: 1, 3)
  - [x] Créer `backend/secondserve-backend.service` (voir section Dev Notes — Systemd)
  - [x] Créer `backend/nginx-secondserve.conf` (voir section Dev Notes — Nginx)
  - [x] Créer `backend/DEPLOY.md` avec les instructions de déploiement sur le VPS (copie des fichiers, certbot, activation service)

- [x] Task 6 — Écrire les tests (AC: 2)
  - [x] Créer `backend/tests/conftest.py` avec `AsyncClient` httpx et override de la dépendance `get_db` pour tests en mémoire
  - [x] Créer `backend/tests/integration/test_health_api.py` — test `GET /api/v1/health` → 200 + `{"status": "ok"}`
  - [x] Créer `backend/pytest.ini` ou section `[tool.pytest.ini_options]` dans `pyproject.toml` : `asyncio_mode = "auto"`, `testpaths = ["tests"]`
  - [x] Vérifier que `uv run pytest` passe avec le test health

## Dev Notes

### Localisation du backend dans le repo

Le backend réside dans `backend/` à la racine du repo SecondServe (le dossier existe déjà avec un `.gitkeep`). Le nom du projet `uv` est `secondserve-backend` (comme spécifié dans `pyproject.toml`), mais le dossier reste `backend/` dans le repo.

**Toutes les commandes uv doivent être exécutées depuis `backend/` :**
```bash
cd /root/SecondServe/backend
uv init --name secondserve-backend --python 3.12
```

### Initialisation uv (`pyproject.toml`)

```toml
[project]
name = "secondserve-backend"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi[standard]>=0.115.0",
    "sqlalchemy[asyncio]>=2.0.0",
    "alembic>=1.13.0",
    "pydantic-settings>=2.0.0",
    "aiosqlite>=0.20.0",
]

[tool.uv]
dev-dependencies = [
    "pytest>=8.0.0",
    "pytest-asyncio>=0.23.0",
    "httpx>=0.27.0",
    "anyio>=4.0.0",
]

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

> **Note :** `aiosqlite` est requis pour SQLAlchemy async avec SQLite — à ajouter explicitement même s'il n'est pas mentionné dans l'architecture.

### Structure `app/main.py`

```python
import logging
from fastapi import FastAPI
from app.api.v1.router import api_router
from app.core.config import settings

logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="SecondServe Backend",
    version="1.0.0",
    docs_url="/docs" if settings.debug else None,
)

app.include_router(api_router, prefix="/api/v1")
```

### Structure `app/core/config.py`

```python
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")
    
    jwt_secret: str = "changeme-in-production"
    mistral_api_key: str = ""
    database_url: str = "sqlite+aiosqlite:///./secondserve.db"
    debug: bool = False

settings = Settings()
```

### Structure `app/core/database.py`

```python
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase
from app.core.config import settings

engine = create_async_engine(settings.database_url, echo=settings.debug)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

class Base(DeclarativeBase):
    pass

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
```

### Structure `app/api/v1/router.py`

```python
from fastapi import APIRouter
from app.api.v1 import auth, sessions, profile, coaching, sync, notifications

api_router = APIRouter()

@api_router.get("/health")
async def health():
    return {"status": "ok"}

api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"])
api_router.include_router(profile.router, prefix="/profile", tags=["profile"])
api_router.include_router(coaching.router, prefix="/coaching", tags=["coaching"])
api_router.include_router(sync.router, prefix="/sync", tags=["sync"])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"])
```

### Stubs des sous-routers (pattern identique pour chaque feature)

```python
# Exemple : app/api/v1/auth.py
from fastapi import APIRouter

router = APIRouter()
# Sera implémenté en Story 1.3 (JWT Authentication)
```

### `app/shared/exceptions.py`

```python
from fastapi import HTTPException

class SecondServeException(Exception):
    def __init__(self, error_code: str, message: str, status_code: int = 400):
        self.error_code = error_code
        self.message = message
        self.status_code = status_code
        super().__init__(message)
```

Format d'erreur uniforme (architecture.md) :
```json
{ "error_code": "SESSION_NOT_FOUND", "message": "Session introuvable", "detail": null }
```

### Configuration Alembic async

Après `uv run alembic init alembic`, modifier `alembic/env.py` :

```python
import asyncio
from logging.config import fileConfig
from sqlalchemy.ext.asyncio import async_engine_from_config
from sqlalchemy import pool
from alembic import context
from app.core.database import Base
from app.core.config import settings

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata

def do_run_migrations(connection):
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()

async def run_async_migrations():
    config.set_main_option("sqlalchemy.url", settings.database_url)
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()

def run_migrations_online():
    asyncio.run(run_async_migrations())

run_migrations_online()
```

### Tests — `tests/conftest.py`

```python
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from app.main import app
from app.core.database import get_db, Base

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

@pytest.fixture
async def db_session():
    engine = create_async_engine(TEST_DATABASE_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    async_session = async_sessionmaker(engine, expire_on_commit=False)
    async with async_session() as session:
        yield session
    await engine.dispose()

@pytest.fixture
async def client(db_session):
    app.dependency_overrides[get_db] = lambda: db_session
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()
```

### Tests — `tests/integration/test_health_api.py`

```python
import pytest

@pytest.mark.asyncio
async def test_health_returns_ok(client):
    response = await client.get("/api/v1/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
```

### Fichier systemd — `backend/secondserve-backend.service`

```ini
[Unit]
Description=SecondServe FastAPI Backend
After=network.target

[Service]
Type=exec
User=www-data
WorkingDirectory=/opt/secondserve-backend
ExecStart=/opt/secondserve-backend/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1
EnvironmentFile=/opt/secondserve-backend/.env

[Install]
WantedBy=multi-user.target
```

> **Note déploiement :** Le service suppose que le backend est déployé dans `/opt/secondserve-backend/` sur le VPS. Adapter le chemin selon la configuration du VPS.

### Config Nginx — `backend/nginx-secondserve.conf`

```nginx
server {
    listen 80;
    server_name <vps-domain>;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name <vps-domain>;

    ssl_certificate /etc/letsencrypt/live/<vps-domain>/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/<vps-domain>/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### `.env.example`

```bash
# Required
JWT_SECRET=your-very-secret-key-change-in-production

# Required for AI coaching features (Epic 5)
MISTRAL_API_KEY=your-mistral-api-key

# Database (default: SQLite local)
DATABASE_URL=sqlite+aiosqlite:///./secondserve.db

# Debug mode (set to true only in development)
DEBUG=false
```

### Règles de logging Python (architecture.md)

```python
# TOUJOURS — un logger par module
import logging
logger = logging.getLogger(__name__)

# Utiliser les niveaux appropriés
logger.info("Backend started on port 8000")
logger.debug("Health check called")
logger.error("Database connection failed: %s", exc, exc_info=True)

# JAMAIS print() dans le code de production
```

### Anti-patterns interdits (tirés de l'architecture)

- ❌ `print(...)` → `logger.info(...)`
- ❌ Timestamps en secondes dans les payloads sync → epoch **millisecondes** (pour les futures stories)
- ❌ Tables au singulier (`session`) → toujours pluriel (`sessions`)
- ❌ Endpoints au singulier (`/session`) → toujours pluriel (`/sessions`)
- ❌ Hardcoder `JWT_SECRET` ou `MISTRAL_API_KEY` dans le code → variables d'env uniquement
- ❌ Retourner une liste nue `[...]` pour les endpoints liste → toujours `{"items": [...], "total": N}`
- ❌ `kapt` ou `@Inject` Python — c'est du domaine Android, ne pas confondre les conventions

### Conventions REST VPS (architecture.md)

| Élément | Convention | Exemple |
|---|---|---|
| Ressources | pluriel, snake_case | `/sessions`, `/work_axes` |
| Paramètres URL | `{ressource_singulier_id}` | `{session_id}` |
| Query params | snake_case | `?updated_since=...` |
| Réponse succès unique | objet direct | `{ "id": "...", "surface": "CLAY" }` |
| Réponse liste | `{ "items": [...], "total": N }` | — |
| Réponse erreur | `{ "error_code": "...", "message": "...", "detail": null }` | — |

### Séquence d'implémentation (ARCH-13)

Cette story (ARCH-2) est en deuxième position de la chaîne obligatoire :
**ARCH-1 (done) → ARCH-2 (cette story) → ARCH-3 (JWT auth, Story 1.3) → ...**

La story suivante (1.3) dépend directement de la structure créée ici — en particulier `app/api/v1/auth.py`, `app/core/security.py`, et `app/core/config.py` (Settings avec `jwt_secret`).

### ACs nécessitant vérification manuelle sur le VPS

**AC1 (systemd)** et **AC3 (Nginx + HTTPS)** ne peuvent être validés que sur le VPS. Le dev agent crée les fichiers de configuration ; leur activation est documentée dans `DEPLOY.md`.

Le test local valide uniquement AC2 (endpoint health via pytest) et AC4-AC6 (structure + Alembic + .env.example).

### Learnings de Story 1.1 applicables ici

- **Pas de `kapt`** — ici c'est Python, donc pas de processeur d'annotations. L'équivalent "annotation processor" est géré par Pydantic nativement.
- **Timber** → `logging.getLogger(__name__)` en Python.
- **Kotlin DSL** → `pyproject.toml` avec `uv` en Python.
- **KSP** n'a pas d'équivalent Python — SQLAlchemy génère directement depuis les modèles.

### Project Structure Notes

- Le backend réside dans `backend/` (repo root), pas dans `android/` ni ailleurs
- `backend/` contient actuellement uniquement un `.gitkeep` — à supprimer à l'init
- La structure `app/features/` est un miroir mental de l'arborescence Android feature modules
- Les modèles SQLAlchemy seront ajoutés feature par feature dans les stories suivantes (1.4 pour `PlayerProfile` + `RankingHistory`, 2.3 pour `Session`, etc.)
- `backend/app/workers/` de l'architecture est non requis pour cette story (implémenté en Stories 5.x)

### References

- [Source: architecture.md#Runtime 3 — Backend VPS (FastAPI)] — Stack complet, commandes init, structure projet
- [Source: architecture.md#Project Structure & Boundaries — Arborescence complète Backend VPS]
- [Source: architecture.md#API & Communication — REST VPS (FastAPI)] — Conventions routes, formats
- [Source: architecture.md#Infrastructure & Déploiement — VPS] — Systemd, Nginx, variables d'env
- [Source: architecture.md#Implementation Patterns — Naming Patterns Python] — Conventions nommage
- [Source: architecture.md#Authentication & Security] — JWT_SECRET rôle, EncryptedSharedPreferences Android
- [Source: epics.md#Story 1.2] — Acceptance Criteria officiels
- [Source: epics.md#ARCH-2] — Exigences architecturales backend

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage majeur. `uv` gère les dépendances async SQLite via `aiosqlite` (ajouté explicitement comme noté dans les Dev Notes). La migration Alembic async fonctionne sans mode offline.

### Completion Notes List

- Task 1 : Projet uv initialisé avec `secondserve-backend`, Python >=3.12, toutes les dépendances prod/dev, `.env.example` et `.gitignore` créés.
- Task 2 : Structure feature-based complète créée avec stubs pour chaque feature (`auth`, `sessions`, `profile`, `coaching`, `sync`, `notifications`) et leurs sous-modules.
- Task 3 : `FastAPI` app avec endpoint `GET /api/v1/health` → `{"status": "ok"}`, `Settings` via pydantic-settings, engine SQLAlchemy async, logging configuré.
- Task 4 : Alembic configuré en mode async avec `alembic/env.py` reécrit, migration initiale générée et appliquée avec succès (`alembic upgrade head`).
- Task 5 : Fichier systemd `secondserve-backend.service`, config Nginx avec redirection HTTPS, et `DEPLOY.md` complet créés.
- Task 6 : `conftest.py` avec SQLite in-memory et override `get_db`, test d'intégration `test_health_returns_ok` — 1 passed en 0.03s.

### File List

- backend/pyproject.toml
- backend/.env.example
- backend/.gitignore
- backend/alembic.ini
- backend/alembic/env.py
- backend/alembic/versions/93050bf04cdd_initial.py
- backend/app/__init__.py
- backend/app/main.py
- backend/app/api/__init__.py
- backend/app/api/v1/__init__.py
- backend/app/api/v1/router.py
- backend/app/api/v1/auth.py
- backend/app/api/v1/sessions.py
- backend/app/api/v1/profile.py
- backend/app/api/v1/coaching.py
- backend/app/api/v1/sync.py
- backend/app/api/v1/notifications.py
- backend/app/core/__init__.py
- backend/app/core/config.py
- backend/app/core/database.py
- backend/app/features/__init__.py
- backend/app/features/auth/__init__.py
- backend/app/features/auth/service.py
- backend/app/features/sessions/__init__.py
- backend/app/features/sessions/models.py
- backend/app/features/sessions/schemas.py
- backend/app/features/sessions/repository.py
- backend/app/features/sessions/service.py
- backend/app/features/profile/__init__.py
- backend/app/features/profile/models.py
- backend/app/features/profile/schemas.py
- backend/app/features/profile/repository.py
- backend/app/features/profile/service.py
- backend/app/features/coaching/__init__.py
- backend/app/features/coaching/models.py
- backend/app/features/coaching/schemas.py
- backend/app/features/coaching/repository.py
- backend/app/features/coaching/service.py
- backend/app/features/coaching/mistral_client.py
- backend/app/features/sync/__init__.py
- backend/app/features/sync/schemas.py
- backend/app/features/sync/service.py
- backend/app/features/notifications/__init__.py
- backend/app/features/notifications/models.py
- backend/app/features/notifications/schemas.py
- backend/app/features/notifications/scheduler.py
- backend/app/shared/__init__.py
- backend/app/shared/exceptions.py
- backend/tests/__init__.py
- backend/tests/unit/__init__.py
- backend/tests/integration/__init__.py
- backend/tests/conftest.py
- backend/tests/integration/test_health_api.py
- backend/secondserve-backend.service
- backend/nginx-secondserve.conf
- backend/DEPLOY.md
- _bmad-output/implementation-artifacts/1-2-setup-fastapi-backend.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

## Change Log

- 2026-06-12 : Création de la story 1.2 — Setup FastAPI backend (contexte complet généré par create-story)
- 2026-06-13 : Implémentation complète — projet uv init, structure feature-based, FastAPI + health endpoint, Alembic async, systemd + Nginx, tests (1 passed). Statut → review.
