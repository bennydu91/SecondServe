import time
from app.features.coaching import mistral_client
from app.features.monitoring.events import emit_event
from app.shared.exceptions import SecondServeException


async def analyze(prompt: str, api_key: str) -> str:
    if not api_key or not api_key.strip():
        raise SecondServeException(
            error_code="MISTRAL_NOT_CONFIGURED",
            message="Mistral API key not configured",
            status_code=503
        )
    t0 = time.monotonic()
    result = await mistral_client.generate(prompt, api_key)
    latency_ms = int((time.monotonic() - t0) * 1000)
    emit_event("ai.call", {"provider": "mistral", "latency_ms": latency_ms})
    return result
