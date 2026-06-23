from app.features.coaching import mistral_client


async def analyze(prompt: str, api_key: str) -> str:
    return await mistral_client.generate(prompt, api_key)
