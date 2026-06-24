import logging
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.sync.schemas import SyncPushRequest, SyncPushResponse
from app.features.sessions.models import SessionModel

logger = logging.getLogger(__name__)


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def push(self, request: SyncPushRequest) -> SyncPushResponse:
        synced = 0
        for session_dto in request.sessions:
            await self._upsert_session(session_dto)
            synced += 1
        for session_id in request.deleted_session_ids:
            await self._delete_session(session_id)
        await self.db.flush()
        logger.info("SyncService: %d sessions upserted, %d deleted", synced, len(request.deleted_session_ids))
        return SyncPushResponse(synced_sessions=synced)

    async def _delete_session(self, session_id: int) -> None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.id == session_id)
        )
        existing = result.scalar_one_or_none()
        if existing is not None:
            await self.db.delete(existing)
            logger.info("SyncService: session %d supprimée (cascade: pending_notifications)", session_id)

    async def _upsert_session(self, dto) -> None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.id == dto.client_id)
        )
        existing = result.scalar_one_or_none()
        if existing is None:
            model = SessionModel(
                id=dto.client_id,
                surface=dto.surface,
                match_format=dto.match_format,
                third_set_rule=dto.third_set_rule,
                opponent=dto.opponent,
                competition_type=dto.competition_type,
                tournament=dto.tournament,
                status=dto.status,
                session_type=dto.session_type,
                result=dto.result,
                feeling_rating=dto.feeling_rating,
                feeling_comment=dto.feeling_comment,
                created_at=dto.created_at,
                updated_at=dto.updated_at,
                scheduled_at=dto.scheduled_at,
            )
            self.db.add(model)
        else:
            # last-write-wins sur updated_at (NFR-S4)
            if dto.updated_at >= existing.updated_at:
                existing.status = dto.status
                existing.result = dto.result
                existing.feeling_rating = dto.feeling_rating
                existing.feeling_comment = dto.feeling_comment
                existing.updated_at = dto.updated_at
                existing.scheduled_at = dto.scheduled_at
