# backend/app/features/monitoring/log_handler.py
import asyncio
import logging
import traceback
from datetime import datetime

from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring.models import ErrorLog

_background_tasks: set = set()


async def _write_error_log(
    level: str, logger_name: str, message: str, tb: str | None
) -> None:
    try:
        async with MonitoringSessionLocal() as session:
            session.add(ErrorLog(
                timestamp=datetime.utcnow(),
                level=level,
                logger=logger_name,
                message=message[:2000],
                traceback=tb[:4000] if tb else None,
            ))
            await session.commit()
    except Exception:
        pass


class MonitoringLogHandler(logging.Handler):
    """Branché sur le root logger — capture WARNING et supérieurs dans monitor.db."""

    def __init__(self):
        super().__init__(level=logging.WARNING)

    def emit(self, record: logging.LogRecord) -> None:
        tb = None
        if record.exc_info:
            tb = "".join(traceback.format_exception(*record.exc_info))

        try:
            loop = asyncio.get_running_loop()
            task = loop.create_task(_write_error_log(
                level=record.levelname,
                logger_name=record.name,
                message=record.getMessage(),
                tb=tb,
            ))
            _background_tasks.add(task)
            task.add_done_callback(_background_tasks.discard)
        except RuntimeError:
            pass  # pas de boucle en cours (ex: startup) — on ignore
