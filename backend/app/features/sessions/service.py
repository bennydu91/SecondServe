from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse
from app.features.monitoring.events import emit_event


class SessionService:
    def __init__(self, repository: SessionRepository):
        self.repository = repository

    async def create_session(self, request: SessionCreateRequest) -> SessionResponse:
        session = await self.repository.create(request)
        response = SessionResponse.model_validate(session)
        emit_event("match.started", {"session_id": session.id, "status": session.status})
        return response
