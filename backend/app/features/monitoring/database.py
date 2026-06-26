# backend/app/features/monitoring/database.py
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase

from app.core.config import settings

_monitor_engine = create_async_engine(settings.monitor_db_url, echo=False)
MonitoringSessionLocal = async_sessionmaker(_monitor_engine, expire_on_commit=False)


class MonitoringBase(DeclarativeBase):
    pass


async def init_monitoring_db() -> None:
    async with _monitor_engine.begin() as conn:
        await conn.run_sync(MonitoringBase.metadata.create_all)
