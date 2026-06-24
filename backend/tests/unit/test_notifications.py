import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi import HTTPException

from app.api.v1.notifications import get_pending_notification


@pytest.mark.asyncio
async def test_get_pending_notification_returns_content_when_present():
    db = AsyncMock()
    result_mock = MagicMock()
    result_mock.scalar_one_or_none.return_value = MagicMock(
        content="Travaille ton premier service sur cette surface rapide."
    )
    db.execute = AsyncMock(return_value=result_mock)

    response = await get_pending_notification(session_id=42, db=db)

    assert response.content == "Travaille ton premier service sur cette surface rapide."


@pytest.mark.asyncio
async def test_get_pending_notification_raises_404_when_absent():
    db = AsyncMock()
    result_mock = MagicMock()
    result_mock.scalar_one_or_none.return_value = None
    db.execute = AsyncMock(return_value=result_mock)

    with pytest.raises(HTTPException) as exc_info:
        await get_pending_notification(session_id=999, db=db)

    assert exc_info.value.status_code == 404
    assert "pré-match" in exc_info.value.detail
