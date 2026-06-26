# backend/app/features/monitoring/middleware.py
import asyncio
import time
from datetime import datetime

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring.models import RequestLog

_background_tasks: set = set()


async def _write_request_log(
    method: str, path: str, status_code: int, response_time: int, ip: str
) -> None:
    try:
        async with MonitoringSessionLocal() as session:
            session.add(RequestLog(
                timestamp=datetime.utcnow(),
                method=method,
                path=path,
                status_code=status_code,
                response_time=response_time,
                ip=ip,
            ))
            await session.commit()
    except Exception:
        pass  # monitoring ne doit jamais faire crasher l'app


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if request.url.path.startswith("/monitor"):
            return await call_next(request)

        start = time.monotonic()
        response = await call_next(request)
        elapsed_ms = int((time.monotonic() - start) * 1000)

        ip = request.headers.get("X-Forwarded-For", request.client.host if request.client else "unknown")
        task = asyncio.create_task(_write_request_log(
            method=request.method,
            path=request.url.path,
            status_code=response.status_code,
            response_time=elapsed_ms,
            ip=ip.split(",")[0].strip(),
        ))
        _background_tasks.add(task)
        task.add_done_callback(_background_tasks.discard)
        return response
