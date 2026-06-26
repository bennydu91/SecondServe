# backend/app/features/monitoring/router.py
import json
import secrets
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse, JSONResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring import service as svc

monitor_router = APIRouter()
_security = HTTPBasic()
_MONITOR_HTML = Path(__file__).parent / "monitor.html"


def _require_auth(credentials: HTTPBasicCredentials = Depends(_security)):
    ok = secrets.compare_digest(credentials.username, settings.monitor_user) and \
         secrets.compare_digest(credentials.password, settings.monitor_password)
    if not ok:
        raise HTTPException(status_code=401, headers={"WWW-Authenticate": "Basic"})


async def get_monitor_db():
    async with MonitoringSessionLocal() as session:
        yield session


class MonitoringEventPayload(BaseModel):
    event_type: str
    payload: dict = {}
    source: str = "android"
    timestamp: int | None = None  # epoch ms côté client (ignoré pour l'instant)


@monitor_router.get("/monitor", dependencies=[Depends(_require_auth)])
async def dashboard():
    return FileResponse(_MONITOR_HTML, media_type="text/html")


@monitor_router.get("/monitor/api/stats", dependencies=[Depends(_require_auth)])
async def api_stats(
    window: int = Query(default=1, ge=1, le=168),
    db: AsyncSession = Depends(get_monitor_db),
):
    return await svc.get_stats(db, window)


@monitor_router.get("/monitor/api/requests", dependencies=[Depends(_require_auth)])
async def api_requests(
    window: int = Query(default=1, ge=1, le=168),
    db: AsyncSession = Depends(get_monitor_db),
):
    return {
        "by_hour": await svc.get_requests_by_hour(db, window),
        "top_endpoints": await svc.get_top_endpoints(db, window, limit=10),
    }


@monitor_router.get("/monitor/api/errors", dependencies=[Depends(_require_auth)])
async def api_errors(
    limit: int = Query(default=50, ge=1, le=200),
    db: AsyncSession = Depends(get_monitor_db),
):
    return await svc.get_recent_errors(db, limit)


@monitor_router.get("/monitor/api/events", dependencies=[Depends(_require_auth)])
async def api_events(
    window: int = Query(default=24, ge=1, le=168),
    db: AsyncSession = Depends(get_monitor_db),
):
    return await svc.get_events_summary(db, window)


@monitor_router.post("/monitor/api/events", status_code=201)
async def receive_event(
    payload: MonitoringEventPayload,
    db: AsyncSession = Depends(get_monitor_db),
):
    """Réception d'un event Android/Wear (erreur immédiate). Auth : JWT géré par JwtInterceptor."""
    await svc.insert_business_event(db, payload.event_type, payload.payload, payload.source)
    return {"status": "ok"}


@monitor_router.post("/monitor/api/events/batch", status_code=201)
async def receive_event_batch(
    events: list[MonitoringEventPayload],
    db: AsyncSession = Depends(get_monitor_db),
):
    """Réception d'un batch d'events Android/Wear (métier batchés). Auth : JWT."""
    for e in events:
        await svc.insert_business_event(db, e.event_type, e.payload, e.source)
    return {"status": "ok", "count": len(events)}
