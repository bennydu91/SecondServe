from app.features.coaching import mistral_client
from app.shared.exceptions import SecondServeException


async def analyze(prompt: str, api_key: str) -> str:
    if not api_key:
        raise SecondServeException(
            error_code="MISTRAL_NOT_CONFIGURED",
            message="Mistral API key not configured",
            status_code=503
        )
    return await mistral_client.generate(prompt, api_key)
