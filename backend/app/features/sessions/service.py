from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import SessionCreateRequest, SessionResponse, SessionsResponse
from app.features.monitoring.events import emit_event


class SessionService:
    def __init__(self, repository: SessionRepository):
        self.repository = repository

    async def create_session(self, request: SessionCreateRequest) -> SessionResponse:
        session = await self.repository.create(request)
        response = SessionResponse.model_validate(session)
        emit_event("match.started", {"session_id": session.id, "status": session.status})
        return response

    async def list_sessions(self) -> SessionsResponse:
        sessions = await self.repository.get_all()
        items = [SessionResponse.model_validate(s) for s in sessions]
        return SessionsResponse(items=items, total=len(items))
