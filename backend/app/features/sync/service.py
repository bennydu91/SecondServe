"""Session sync push/delete. Single-writer assumption: client_id is only
meaningful as long as exactly one device (the phone) pushes sessions. If a
second device ever pushes sessions independently (e.g. a tablet, or a
reinstalled app with a fresh local DB), its client_id sequence could again
collide with the phone's. Not handled here — see
docs/superpowers/plans/2026-07-05-fix-sync-session-id-collision.md."""
import logging
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.sync.schemas import SyncPushRequest, SyncPushResponse
from app.features.sessions.models import SessionModel
from app.features.monitoring.events import emit_event

logger = logging.getLogger(__name__)


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def push(self, request: SyncPushRequest) -> SyncPushResponse:
        synced = 0
        for session_dto in request.sessions:
            await self._upsert_session(session_dto)
            synced += 1
        for client_id in request.deleted_session_ids:
            await self._delete_session(client_id)
        await self.db.flush()
        logger.info("SyncService: %d sessions upserted, %d deleted", synced, len(request.deleted_session_ids))
        return SyncPushResponse(synced_sessions=synced)

    async def _get_by_client_id(self, client_id: int) -> SessionModel | None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.client_id == client_id)
        )
        return result.scalar_one_or_none()

    async def _delete_session(self, client_id: int) -> None:
        existing = await self._get_by_client_id(client_id)
        if existing is not None:
            emit_event("match.ended", {"session_id": existing.id})
            await self.db.delete(existing)
            logger.info("SyncService: session client_id=%d (server id=%d) supprimée", client_id, existing.id)

    async def _upsert_session(self, dto) -> None:
        existing = await self._get_by_client_id(dto.client_id)
        if existing is None:
            model = SessionModel(
                client_id=dto.client_id,
                surface=dto.surface,
                match_format=dto.match_format,
                third_set_rule=dto.third_set_rule,
                opponent=dto.opponent,
                competition_type=dto.competition_type,
                tournament=dto.tournament,
                status=dto.status,
                session_type=dto.session_type,
                result=dto.result,
                score_text=dto.score_text,
                feeling_rating=dto.feeling_rating,
                feeling_comment=dto.feeling_comment,
                created_at=dto.created_at,
                updated_at=dto.updated_at,
                scheduled_at=dto.scheduled_at,
                first_serve_percent_self=dto.first_serve_percent_self,
                first_serve_percent_opponent=dto.first_serve_percent_opponent,
                winners_self=dto.winners_self,
                winners_opponent=dto.winners_opponent,
            )
            self.db.add(model)
        else:
            # last-write-wins sur updated_at (NFR-S4)
            if dto.updated_at >= existing.updated_at:
                existing.status = dto.status
                existing.result = dto.result
                existing.score_text = dto.score_text
                existing.feeling_rating = dto.feeling_rating
                existing.feeling_comment = dto.feeling_comment
                existing.updated_at = dto.updated_at
                existing.scheduled_at = dto.scheduled_at
                existing.first_serve_percent_self = dto.first_serve_percent_self
                existing.first_serve_percent_opponent = dto.first_serve_percent_opponent
                existing.winners_self = dto.winners_self
                existing.winners_opponent = dto.winners_opponent
