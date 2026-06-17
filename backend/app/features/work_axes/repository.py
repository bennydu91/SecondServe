import time
import logging
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.features.work_axes.models import WorkAxis

logger = logging.getLogger(__name__)


class WorkAxisRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_all(self) -> list[WorkAxis]:
        result = await self.db.execute(select(WorkAxis).order_by(WorkAxis.created_at.asc()))
        return list(result.scalars().all())

    async def count(self) -> int:
        result = await self.db.execute(select(func.count()).select_from(WorkAxis))
        return result.scalar_one()

    async def create(self, title: str, created_at: int) -> WorkAxis:
        now = int(time.time() * 1000)
        axis = WorkAxis(title=title, created_at=created_at, updated_at=now)
        self.db.add(axis)
        await self.db.flush()
        return axis

    async def get_by_id(self, axis_id: int) -> WorkAxis | None:
        result = await self.db.execute(select(WorkAxis).where(WorkAxis.id == axis_id))
        return result.scalar_one_or_none()

    async def update(self, axis_id: int, title: str) -> WorkAxis | None:
        axis = await self.get_by_id(axis_id)
        if axis:
            axis.title = title
            axis.updated_at = int(time.time() * 1000)
            await self.db.flush()
        return axis

    async def delete(self, axis_id: int) -> bool:
        axis = await self.get_by_id(axis_id)
        if axis:
            await self.db.delete(axis)
            await self.db.flush()
            return True
        return False
