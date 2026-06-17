import logging
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse
from app.features.sessions.service import SessionService

logger = logging.getLogger(__name__)
router = APIRouter()


def get_session_service(db: AsyncSession = Depends(get_db)) -> SessionService:
    return SessionService(SessionRepository(db))


@router.post("", response_model=SessionResponse, status_code=201)
async def create_session(
    request: SessionCreateRequest,
    service: SessionService = Depends(get_session_service)
):
    return await service.create_session(request)
