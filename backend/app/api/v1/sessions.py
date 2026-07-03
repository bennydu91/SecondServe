from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
)
from app.features.sessions.service import SessionService

router = APIRouter()


def get_session_service(db: AsyncSession = Depends(get_db)) -> SessionService:
    return SessionService(SessionRepository(db))


@router.get("", response_model=SessionsResponse)
async def list_sessions(service: SessionService = Depends(get_session_service)):
    return await service.list_sessions()


@router.post("", response_model=SessionResponse, status_code=201)
async def create_session(
    request: SessionCreateRequest,
    service: SessionService = Depends(get_session_service)
):
    return await service.create_session(request)


@router.put("/{session_id}/score-seed", response_model=SessionResponse)
async def update_score_seed(
    session_id: int,
    request: ScoreSeedRequest,
    service: SessionService = Depends(get_session_service),
):
    return await service.update_score_seed(session_id, request)
