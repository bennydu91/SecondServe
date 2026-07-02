import asyncio
import json
import logging
from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.security import verify_jwt
from app.features.live_sharing.broadcast import broadcaster
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.schemas import (
    CreateShareRequest,
    CreateShareResponse,
    LiveScoreUpdateRequest,
    LiveSnapshotResponse,
)
from app.features.live_sharing.service import LiveSharingService

logger = logging.getLogger(__name__)

router = APIRouter()


def get_live_sharing_service(db: AsyncSession = Depends(get_db)) -> LiveSharingService:
    return LiveSharingService(MatchShareRepository(db))


# NOTE ordre des routes : "/shares" et "/sessions/{id}/score" sont des routes POST distinctes
# des routes publiques GET "/{token}" et "/{token}/stream" — pas de collision possible car
# les méthodes HTTP diffèrent, mais on les déclare avant par lisibilité.

@router.post("/shares", response_model=CreateShareResponse, dependencies=[Depends(verify_jwt)])
async def create_share(
    request: CreateShareRequest,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    return await service.create_share(request)


@router.post(
    "/sessions/{session_id}/score",
    status_code=204,
    dependencies=[Depends(verify_jwt)],
)
async def push_score(
    session_id: int,
    request: LiveScoreUpdateRequest,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    await service.push_score(session_id, request)


@router.get("/{token}", response_model=LiveSnapshotResponse)
async def get_snapshot(
    token: str,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    return await service.get_snapshot(token)


@router.get("/{token}/stream")
async def stream_snapshot(
    token: str,
    request: Request,
    service: LiveSharingService = Depends(get_live_sharing_service),
):
    initial = await service.get_snapshot(token)  # lève 404/410 avant tout abonnement
    queue = broadcaster.subscribe(token)

    async def event_generator():
        try:
            yield f"data: {initial.model_dump_json()}\n\n"
            while True:
                if await request.is_disconnected():
                    break
                try:
                    snapshot = await asyncio.wait_for(queue.get(), timeout=15)
                    yield f"data: {json.dumps(snapshot)}\n\n"
                except asyncio.TimeoutError:
                    yield ": keep-alive\n\n"
        finally:
            broadcaster.unsubscribe(token, queue)

    return StreamingResponse(event_generator(), media_type="text/event-stream")
