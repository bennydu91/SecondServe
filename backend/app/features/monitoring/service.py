# backend/app/features/monitoring/service.py
import json
from datetime import datetime, timedelta

from sqlalchemy import delete, func, case, text, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.monitoring.models import RequestLog, ErrorLog, BusinessEvent


async def get_stats(session: AsyncSession, window_hours: int) -> dict:
    since = datetime.utcnow() - timedelta(hours=window_hours)
    row = (await session.execute(
        select(
            func.count(RequestLog.id),
            func.avg(RequestLog.response_time),
            func.sum(case((RequestLog.status_code >= 500, 1), else_=0)),
        ).where(RequestLog.timestamp >= since)
    )).one()
    total, avg_rt, errors = row
    total = total or 0
    errors = errors or 0
    return {
        "total_requests": total,
        "error_rate": round(errors / total * 100, 1) if total > 0 else 0.0,
        "avg_response_time_ms": round(avg_rt or 0),
        "uptime_pct": round((1 - errors / total) * 100, 1) if total > 0 else 100.0,
    }


async def get_requests_by_hour(session: AsyncSession, window_hours: int) -> list[dict]:
    since = datetime.utcnow() - timedelta(hours=window_hours)
    rows = (await session.execute(
        select(
            func.strftime("%Y-%m-%dT%H:00:00", RequestLog.timestamp).label("hour"),
            func.count(RequestLog.id).label("count"),
        ).where(RequestLog.timestamp >= since)
        .group_by(text("hour"))
        .order_by(text("hour"))
    )).all()
    return [{"hour": r.hour, "count": r.count} for r in rows]


async def get_top_endpoints(session: AsyncSession, window_hours: int, limit: int = 10) -> list[dict]:
    since = datetime.utcnow() - timedelta(hours=window_hours)
    rows = (await session.execute(
        select(
            RequestLog.path,
            func.count(RequestLog.id).label("count"),
            func.avg(RequestLog.response_time).label("avg_ms"),
        ).where(RequestLog.timestamp >= since)
        .group_by(RequestLog.path)
        .order_by(func.count(RequestLog.id).desc())
        .limit(limit)
    )).all()
    return [{"path": r.path, "count": r.count, "avg_ms": round(r.avg_ms or 0)} for r in rows]


async def get_recent_errors(session: AsyncSession, limit: int = 50) -> list[dict]:
    rows = (await session.execute(
        select(ErrorLog).order_by(ErrorLog.timestamp.desc()).limit(limit)
    )).scalars().all()
    return [
        {
            "id": e.id,
            "timestamp": e.timestamp.isoformat(),
            "level": e.level,
            "logger": e.logger,
            "message": e.message,
            "traceback": e.traceback,
        }
        for e in rows
    ]


async def get_events_summary(session: AsyncSession, window_hours: int) -> list[dict]:
    since = datetime.utcnow() - timedelta(hours=window_hours)
    rows = (await session.execute(
        select(
            BusinessEvent.event_type,
            BusinessEvent.source,
            func.count(BusinessEvent.id).label("count"),
        ).where(BusinessEvent.timestamp >= since)
        .group_by(BusinessEvent.event_type, BusinessEvent.source)
        .order_by(func.count(BusinessEvent.id).desc())
    )).all()
    return [{"event_type": r.event_type, "source": r.source, "count": r.count} for r in rows]


async def insert_business_event(
    session: AsyncSession, event_type: str, payload: dict, source: str = "backend"
) -> None:
    session.add(BusinessEvent(
        timestamp=datetime.utcnow(),
        event_type=event_type,
        payload=json.dumps(payload),
        source=source,
    ))
    await session.commit()


async def purge_old_records(session: AsyncSession, days: int = 30) -> None:
    cutoff = datetime.utcnow() - timedelta(days=days)
    await session.execute(delete(RequestLog).where(RequestLog.timestamp < cutoff))
    await session.execute(delete(ErrorLog).where(ErrorLog.timestamp < cutoff))
    await session.execute(delete(BusinessEvent).where(BusinessEvent.timestamp < cutoff))
    await session.commit()
