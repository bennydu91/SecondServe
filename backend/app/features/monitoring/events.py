# backend/app/features/monitoring/events.py
import asyncio
import json
from datetime import datetime

from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring.models import BusinessEvent

_background_tasks: set = set()


async def _persist_event(event_type: str, payload: dict, source: str) -> None:
    try:
        async with MonitoringSessionLocal() as session:
            session.add(BusinessEvent(
                timestamp=datetime.utcnow(),
                event_type=event_type,
                payload=json.dumps(payload),
                source=source,
            ))
            await session.commit()
    except Exception:
        pass


def emit_event(event_type: str, payload: dict, source: str = "backend") -> None:
    """Fire-and-forget — ne pas awaiter."""
    try:
        loop = asyncio.get_running_loop()
        task = loop.create_task(_persist_event(event_type, payload, source))
        _background_tasks.add(task)
        task.add_done_callback(_background_tasks.discard)
    except RuntimeError:
        pass
