# backend/tests/unit/monitoring/test_service.py
import pytest
from datetime import datetime, timedelta
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.pool import StaticPool

from app.features.monitoring.database import MonitoringBase
from app.features.monitoring.models import RequestLog, ErrorLog, BusinessEvent
from app.features.monitoring import service


@pytest.fixture
async def session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(MonitoringBase.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False)
    async with factory() as s:
        yield s
    await engine.dispose()


async def _seed_requests(session, count=10, error_count=2):
    now = datetime.utcnow()
    logs = [
        RequestLog(
            timestamp=now - timedelta(minutes=i * 5),
            method="GET",
            path=f"/api/v1/sessions" if i % 2 == 0 else "/api/v1/health",
            status_code=500 if i < error_count else 200,
            response_time=100 + i * 10,
            ip="1.2.3.4",
        )
        for i in range(count)
    ]
    session.add_all(logs)
    await session.commit()


async def test_get_stats_returns_correct_totals(session):
    await _seed_requests(session, count=10, error_count=2)
    stats = await service.get_stats(session, window_hours=24)
    assert stats["total_requests"] == 10
    assert stats["error_rate"] == 20.0
    assert stats["avg_response_time_ms"] > 0
    assert stats["uptime_pct"] == 80.0


async def test_get_stats_empty_db(session):
    stats = await service.get_stats(session, window_hours=1)
    assert stats["total_requests"] == 0
    assert stats["error_rate"] == 0.0
    assert stats["uptime_pct"] == 100.0


async def test_get_top_endpoints(session):
    await _seed_requests(session, count=10)
    top = await service.get_top_endpoints(session, window_hours=24, limit=5)
    assert len(top) <= 5
    assert "path" in top[0]
    assert "count" in top[0]
    assert "avg_ms" in top[0]
    # Le plus appelé est en premier
    assert top[0]["count"] >= top[-1]["count"]


async def test_get_recent_errors(session):
    now = datetime.utcnow()
    session.add(ErrorLog(timestamp=now, level="ERROR", logger="test", message="boom", traceback=None))
    await session.commit()
    errors = await service.get_recent_errors(session, limit=10)
    assert len(errors) == 1
    assert errors[0]["message"] == "boom"


async def test_get_events_summary(session):
    now = datetime.utcnow()
    for _ in range(3):
        session.add(BusinessEvent(timestamp=now, event_type="match.started", payload="{}", source="backend"))
    session.add(BusinessEvent(timestamp=now, event_type="ai.call", payload="{}", source="backend"))
    await session.commit()
    summary = await service.get_events_summary(session, window_hours=24)
    types = {e["event_type"]: e["count"] for e in summary}
    assert types["match.started"] == 3
    assert types["ai.call"] == 1


async def test_purge_removes_old_records(session):
    old_ts = datetime.utcnow() - timedelta(days=31)
    session.add(RequestLog(timestamp=old_ts, method="GET", path="/x", status_code=200, response_time=1, ip="0.0.0.0"))
    await session.commit()
    await service.purge_old_records(session, days=30)
    stats = await service.get_stats(session, window_hours=24 * 40)
    assert stats["total_requests"] == 0
