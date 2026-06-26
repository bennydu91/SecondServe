# backend/tests/unit/monitoring/test_models.py
import pytest
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.pool import StaticPool

from app.features.monitoring.database import MonitoringBase
from app.features.monitoring.models import RequestLog, ErrorLog, BusinessEvent
from datetime import datetime


@pytest.fixture
async def monitor_session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(MonitoringBase.metadata.create_all)
    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    async with session_factory() as s:
        yield s
    await engine.dispose()


async def test_request_log_persisted(monitor_session):
    log = RequestLog(
        timestamp=datetime.utcnow(),
        method="GET",
        path="/api/v1/health",
        status_code=200,
        response_time=42,
        ip="1.2.3.4",
    )
    monitor_session.add(log)
    await monitor_session.commit()
    await monitor_session.refresh(log)
    assert log.id is not None
    assert log.response_time == 42


async def test_error_log_persisted(monitor_session):
    err = ErrorLog(
        timestamp=datetime.utcnow(),
        level="ERROR",
        logger="app.features.sessions",
        message="Session not found",
        traceback="Traceback (most recent call last):\n  ...",
    )
    monitor_session.add(err)
    await monitor_session.commit()
    await monitor_session.refresh(err)
    assert err.id is not None


async def test_business_event_persisted(monitor_session):
    evt = BusinessEvent(
        timestamp=datetime.utcnow(),
        event_type="match.started",
        payload='{"session_id": 1}',
        source="backend",
    )
    monitor_session.add(evt)
    await monitor_session.commit()
    await monitor_session.refresh(evt)
    assert evt.source == "backend"
