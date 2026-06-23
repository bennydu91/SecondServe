import pytest
from unittest.mock import AsyncMock, patch
from app.features.coaching import service
from app.shared.exceptions import SecondServeException


@pytest.mark.asyncio
async def test_analyze_success():
    with patch("app.features.coaching.service.mistral_client.generate", new=AsyncMock(return_value="Conseil VPS")) as mock_gen:
        result = await service.analyze("prompt test", "api_key_test")
        assert result == "Conseil VPS"
        mock_gen.assert_awaited_once_with("prompt test", "api_key_test")


@pytest.mark.asyncio
async def test_analyze_timeout_raises():
    with patch(
        "app.features.coaching.service.mistral_client.generate",
        new=AsyncMock(side_effect=SecondServeException("MISTRAL_UNAVAILABLE", "Mistral timeout after retry", 503)),
    ):
        with pytest.raises(SecondServeException) as exc_info:
            await service.analyze("prompt test", "api_key_test")
        assert exc_info.value.error_code == "MISTRAL_UNAVAILABLE"
        assert exc_info.value.status_code == 503


@pytest.mark.asyncio
async def test_analyze_mistral_error_raises():
    with patch(
        "app.features.coaching.service.mistral_client.generate",
        new=AsyncMock(side_effect=SecondServeException("MISTRAL_ERROR", "Mistral API error: 500", 503)),
    ):
        with pytest.raises(SecondServeException) as exc_info:
            await service.analyze("prompt test", "api_key_test")
        assert exc_info.value.error_code == "MISTRAL_ERROR"
        assert exc_info.value.status_code == 503
