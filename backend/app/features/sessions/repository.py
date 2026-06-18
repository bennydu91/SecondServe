from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.sessions.models import SessionModel
from app.features.sessions.schemas import SessionCreateRequest


class SessionRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, request: SessionCreateRequest) -> SessionModel:
        session = SessionModel(
            surface=request.surface,
            match_format=request.match_format,
            third_set_rule=request.third_set_rule,
            opponent=request.opponent,
            competition_type=request.competition_type,
            tournament=request.tournament,
            status="ACTIVE",
            session_type="MATCH",
            result=None,
            created_at=request.created_at,
            updated_at=request.created_at
        )
        self.db.add(session)
        await self.db.flush()
        return session

    async def get_by_id(self, session_id: int) -> SessionModel | None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.id == session_id)
        )
        return result.scalar_one_or_none()

    async def get_all(self) -> list[SessionModel]:
        result = await self.db.execute(
            select(SessionModel).order_by(SessionModel.created_at.desc())
        )
        return list(result.scalars().all())


    async def get_by_id(self, session_id: int) -> SessionModel | None:
        result = await self.db.execute(
            select(SessionModel).where(SessionModel.id == session_id)
        )
        return result.scalar_one_or_none()

    async def get_all(self) -> list[SessionModel]:
        result = await self.db.execute(
            select(SessionModel).order_by(SessionModel.created_at.desc())
        )
        return list(result.scalars().all())
