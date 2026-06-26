# Monitoring & Dashboard — SecondServe

**Date :** 2026-06-26
**Statut :** Approuvé

## Contexte

SecondServe tourne sur un VPS Hostinger KVM 4 (FastAPI + nginx). Il n'y a actuellement aucune visibilité sur la santé du backend, les erreurs, ou les événements métier (sessions de match, appels IA, synchro montre). Ce spec décrit un système de logging complet avec un dashboard maison hébergé sur le même VPS.

## Objectifs

- Logger toutes les requêtes HTTP (temps de réponse, codes d'erreur, endpoints)
- Capturer automatiquement les erreurs et exceptions Python
- Tracer les événements métier clés (match, IA, Wear OS)
- Exposer un dashboard web sur `/monitor`, protégé par HTTP Basic Auth
- Prévoir un endpoint pour recevoir des events Android dans le futur

## Architecture

```
Nginx (HTTPS :443)
  /monitor/*  → Basic Auth → FastAPI :8000
  /api/*      → (inchangé) → FastAPI :8000

FastAPI :8000
  ├── RequestLoggingMiddleware  →  monitor.db (request_logs)
  ├── MonitoringLogHandler      →  monitor.db (error_logs)
  └── features/monitoring/
        ├── GET  /monitor           → dashboard HTML
        ├── GET  /monitor/api/stats
        ├── GET  /monitor/api/requests
        ├── GET  /monitor/api/errors
        └── POST /monitor/api/events  ← stub Android (futur)
```

La base `monitor.db` est une SQLite distincte de la base métier. Elle est gérée indépendamment.

## Modèle de données (`monitor.db`)

### `request_logs`
| Colonne | Type | Description |
|---|---|---|
| id | INTEGER PK | auto-increment |
| timestamp | DATETIME | heure UTC |
| method | TEXT | GET, POST, etc. |
| path | TEXT | chemin sans query string |
| status_code | INTEGER | code HTTP réponse |
| response_time | INTEGER | durée en ms |
| ip | TEXT | IP client |

### `error_logs`
| Colonne | Type | Description |
|---|---|---|
| id | INTEGER PK | auto-increment |
| timestamp | DATETIME | heure UTC |
| level | TEXT | WARNING, ERROR, CRITICAL |
| logger | TEXT | nom du module Python |
| message | TEXT | message du log |
| traceback | TEXT | nullable — stack trace complète |

### `business_events`
| Colonne | Type | Description |
|---|---|---|
| id | INTEGER PK | auto-increment |
| timestamp | DATETIME | heure UTC |
| event_type | TEXT | ex. `match.started`, `ai.call`, `wear.sync` |
| payload | TEXT | JSON arbitraire |
| source | TEXT | `backend` ou `android` (futur) |

**Rétention :** purge automatique des entrées > 30 jours, via le scheduler existant (`features/notifications/scheduler.py`).

## Composants

### `RequestLoggingMiddleware`

Middleware ASGI branché dans `main.py`. Mesure le temps de réponse de chaque requête et insère une ligne dans `request_logs`. Exclut les routes `/monitor/*` pour éviter le bruit. Opération non bloquante : écriture asynchrone.

### `MonitoringLogHandler`

Handler Python (`logging.Handler`) branché sur le logger root au démarrage de l'app. Intercepte les niveaux `WARNING` et supérieurs. Capture le message, le nom du logger, et le traceback si disponible. Aucune modification du code existant requise.

### `emit_event(event_type, payload)`

Fonction utilitaire async à appeler manuellement aux points clés :

```python
await emit_event("match.started", {"session_id": "...", "format": "3sets"})
await emit_event("ai.call", {"provider": "mistral", "latency_ms": 1240})
await emit_event("wear.sync", {"direction": "phone→watch"})
```

Points d'instrumentation initiaux :
- `features/sessions/` — start/end session
- `features/coaching/` — appel Mistral
- `features/sync/` — synchro Wear OS

### `router.py` — endpoints monitoring

| Route | Description |
|---|---|
| `GET /monitor` | Sert `monitor.html` via `FileResponse` |
| `GET /monitor/api/stats?window=1h\|24h` | KPIs : nb requêtes, taux erreur, temps moyen, uptime |
| `GET /monitor/api/requests?window=1h\|24h` | Série temporelle + top endpoints |
| `GET /monitor/api/errors?limit=50` | Dernières erreurs avec traceback |
| `GET /monitor/api/events?window=24h` | Comptage par type d'événement |
| `POST /monitor/api/events` | Réception events Android (stub — 501 pour l'instant) |

### Dashboard (`monitor.html`)

Page HTML unique avec vanilla JS + Chart.js (CDN). Aucun build tool.

**Sections :**
1. **KPIs** — 4 cartes : nb requêtes, taux erreur, temps moyen de réponse, uptime (% de requêtes non-5xx sur la fenêtre)
2. **Graphe barres** — requêtes par heure sur la fenêtre sélectionnée
3. **Top endpoints** + **comptage événements métier** (deux colonnes)
4. **Journal d'erreurs** — dernières erreurs, traceback dépliable au clic

**Comportement :**
- Toggle `1h / 24h` en haut à droite, change toutes les stats simultanément
- Auto-refresh toutes les 60 secondes
- Bouton refresh manuel

## Nginx — configuration

Ajout d'un bloc `location /monitor` dans `nginx-secondserve.conf` :

```nginx
location /monitor {
    auth_basic "SecondServe Monitor";
    auth_basic_user_file /etc/nginx/.htpasswd-monitor;
    proxy_pass http://127.0.0.1:8000;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Le fichier `.htpasswd-monitor` est créé sur le VPS avec `htpasswd`. Il n'est pas versionné.

## Structure de fichiers

```
backend/app/features/monitoring/
├── __init__.py
├── database.py      # engine SQLite monitor.db, session factory
├── middleware.py    # RequestLoggingMiddleware
├── log_handler.py  # MonitoringLogHandler
├── models.py       # tables SQLAlchemy (request_logs, error_logs, business_events)
├── service.py      # queries : stats, top endpoints, erreurs, events
├── router.py       # routes /monitor et /monitor/api/*
└── monitor.html    # dashboard (servi via FileResponse depuis router.py)
```

## Intégration dans `main.py`

```python
# Ajouts dans main.py
from app.features.monitoring.middleware import RequestLoggingMiddleware
from app.features.monitoring.log_handler import MonitoringLogHandler
from app.features.monitoring.router import monitor_router

app.add_middleware(RequestLoggingMiddleware)
app.include_router(monitor_router)  # pas de prefix /api/v1
logging.getLogger().addHandler(MonitoringLogHandler())
```

## Évolutions prévues

- **Android events (futur)** : activer le `POST /monitor/api/events` avec authentification par token, appeler depuis l'app Kotlin aux crashs et événements UI critiques.
- **Alertes (futur)** : notif push si taux d'erreur > seuil ou si le backend est injoignable depuis > N minutes.

## Hors scope (V1)

- Métriques système (CPU, RAM, disque) — le VPS a son propre panel Hostinger
- Logs Wear OS côté client
- Historique > 30 jours
- Export CSV/JSON
