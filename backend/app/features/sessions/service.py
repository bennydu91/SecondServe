import json
from app.features.sessions.repository import SessionRepository
from app.features.sessions.schemas import (
    SessionCreateRequest,
    SessionResponse,
    SessionsResponse,
    ScoreSeedRequest,
)
from app.features.monitoring.events import emit_event
from app.shared.exceptions import SecondServeException


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

    async def update_score_seed(self, session_id: int, request: ScoreSeedRequest) -> SessionResponse:
        session = await self.repository.update_score_seed(session_id, json.dumps(request.model_dump()))
        if session is None:
            raise SecondServeException(
                error_code="SESSION_NOT_FOUND", message="Session introuvable", status_code=404
            )
        return SessionResponse.model_validate(session)
