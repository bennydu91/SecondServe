import os
os.environ.setdefault("JWT_SECRET", "test-only-secret-do-not-use-in-production")
os.environ.setdefault("MONITOR_USER", "admin")
os.environ.setdefault("MONITOR_PASSWORD", "testpass")

import pytest
from httpx import AsyncClient, ASGITransport, BasicAuth
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.pool import StaticPool

from app.main import app
from app.core.security import JWTManager
from app.features.monitoring.database import MonitoringBase
from app.features.monitoring.router import get_monitor_db

TEST_MONITOR_URL = "sqlite+aiosqlite:///:memory:"

_test_engine = create_async_engine(
    TEST_MONITOR_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
_test_session_factory = async_sessionmaker(_test_engine, expire_on_commit=False)

_TEST_JWT = JWTManager("test-only-secret-do-not-use-in-production").create_token()


@pytest.fixture(autouse=True)
async def setup_monitor_db():
    async with _test_engine.begin() as conn:
        await conn.run_sync(MonitoringBase.metadata.create_all)
    yield
    async with _test_engine.begin() as conn:
        await conn.run_sync(MonitoringBase.metadata.drop_all)


@pytest.fixture
async def client():
    async def override_monitor_db():
        async with _test_session_factory() as s:
            yield s

    app.dependency_overrides[get_monitor_db] = override_monitor_db
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.pop(get_monitor_db, None)


async def test_dashboard_requires_auth(client):
    r = await client.get("/monitor")
    assert r.status_code == 401


async def test_dashboard_returns_html_with_auth(client):
    r = await client.get("/monitor", auth=BasicAuth("admin", "testpass"))
    assert r.status_code == 200
    assert "text/html" in r.headers["content-type"]


async def test_stats_returns_zero_on_empty_db(client):
    r = await client.get("/monitor/api/stats?window=1", auth=BasicAuth("admin", "testpass"))
    assert r.status_code == 200
    data = r.json()
    assert data["total_requests"] == 0
    assert data["uptime_pct"] == 100.0


async def test_post_event_requires_jwt(client):
    r = await client.post(
        "/monitor/api/events",
        json={"event_type": "test", "payload": {}, "source": "android"},
    )
    assert r.status_code == 401


async def test_post_event_and_retrieve(client):
    r = await client.post(
        "/monitor/api/events",
        json={"event_type": "match.started", "payload": {"session_id": 1}, "source": "android"},
        headers={"Authorization": f"Bearer {_TEST_JWT}"},
    )
    assert r.status_code == 201

    r2 = await client.get("/monitor/api/events?window=1", auth=BasicAuth("admin", "testpass"))
    events = r2.json()
    assert any(e["event_type"] == "match.started" for e in events)


async def test_post_batch_events(client):
    payload = [
        {"event_type": "wear.score.updated", "payload": {}, "source": "wear"},
        {"event_type": "wear.score.updated", "payload": {}, "source": "wear"},
    ]
    r = await client.post(
        "/monitor/api/events/batch",
        json=payload,
        headers={"Authorization": f"Bearer {_TEST_JWT}"},
    )
    assert r.status_code == 201
    assert r.json()["count"] == 2
