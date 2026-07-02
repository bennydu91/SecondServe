import secrets
import time
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.live_sharing.models import MatchShareModel

# Filet de sécurité pour les partages abandonnés (session jamais clôturée :
# app tuée, session laissée ouverte, etc.) dont expires_at reste NULL indéfiniment.
ABANDONED_SHARE_TTL_MS = 7 * 24 * 60 * 60 * 1000


class MatchShareRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_session(self, session_id: int) -> MatchShareModel | None:
        result = await self.db.execute(
            select(MatchShareModel).where(MatchShareModel.session_id == session_id)
        )
        return result.scalar_one_or_none()

    async def get_by_token(self, token: str) -> MatchShareModel | None:
        result = await self.db.execute(
            select(MatchShareModel).where(MatchShareModel.token == token)
        )
        return result.scalar_one_or_none()

    async def create(self, session_id: int) -> MatchShareModel:
        share = MatchShareModel(
            token=secrets.token_urlsafe(16),
            session_id=session_id,
            created_at=int(time.time() * 1000),
            expires_at=None,
            score_snapshot=None,
        )
        self.db.add(share)
        await self.db.flush()
        return share

    async def update_snapshot(
        self, share: MatchShareModel, snapshot_json: str, expires_at: int | None
    ) -> None:
        share.score_snapshot = snapshot_json
        share.expires_at = expires_at
        await self.db.flush()

    async def delete_expired(self, now_ms: int) -> int:
        result = await self.db.execute(
            select(MatchShareModel).where(
                or_(
                    (MatchShareModel.expires_at.is_not(None))
                    & (MatchShareModel.expires_at < now_ms),
                    (MatchShareModel.expires_at.is_(None))
                    & (MatchShareModel.created_at < now_ms - ABANDONED_SHARE_TTL_MS),
                )
            )
        )
        expired = list(result.scalars().all())
        for share in expired:
            await self.db.delete(share)
        await self.db.flush()
        return len(expired)
