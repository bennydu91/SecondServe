import time
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.points.models import PointModel


class PointRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all_for_session(self, session_id: int) -> list[PointModel]:
        result = await self.db.execute(
            select(PointModel)
            .where(PointModel.session_id == session_id)
            .order_by(PointModel.sequence_num.asc())
        )
        return list(result.scalars().all())

    async def create(self, session_id: int, scorer: str, context: str) -> PointModel:
        result = await self.db.execute(
            select(func.max(PointModel.sequence_num)).where(PointModel.session_id == session_id)
        )
        max_seq = result.scalar_one_or_none()
        point = PointModel(
            session_id=session_id,
            scorer=scorer,
            context=context,
            sequence_num=(max_seq or 0) + 1,
            recorded_at=int(time.time() * 1000),
        )
        self.db.add(point)
        await self.db.flush()
        return point

    async def delete_last(self, session_id: int) -> bool:
        result = await self.db.execute(
            select(PointModel)
            .where(PointModel.session_id == session_id)
            .order_by(PointModel.sequence_num.desc())
            .limit(1)
        )
        last = result.scalar_one_or_none()
        if last is None:
            return False
        await self.db.delete(last)
        await self.db.flush()
        return True
