import httpx
from app.shared.exceptions import SecondServeException


async def generate(prompt: str, api_key: str) -> str:
    url = "https://api.mistral.ai/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": "mistral-small-latest",
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 200,
        "temperature": 0.7,
    }

    async with httpx.AsyncClient(timeout=15.0) as client:
        for attempt in range(2):
            try:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()
                return response.json()["choices"][0]["message"]["content"]
            except httpx.TimeoutException:
                if attempt == 1:
                    raise SecondServeException(
                        "MISTRAL_UNAVAILABLE", "Mistral timeout after retry", 503
                    )
                continue
            except httpx.HTTPStatusError as e:
                raise SecondServeException(
                    "MISTRAL_ERROR",
                    f"Mistral API error: {e.response.status_code}",
                    503,
                )

    raise SecondServeException("MISTRAL_UNAVAILABLE", "Mistral unreachable", 503)
