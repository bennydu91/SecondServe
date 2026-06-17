import logging
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse

logger = logging.getLogger(__name__)


class SessionService:
    def __init__(self, repository: SessionRepository):
        self.repository = repository

    async def create_session(self, request: SessionCreateRequest) -> SessionResponse:
        session = await self.repository.create(request)
        return SessionResponse.model_validate(session)
