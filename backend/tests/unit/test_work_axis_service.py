import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi import HTTPException
from pydantic import ValidationError

from app.features.work_axes.schemas import WorkAxisRequest, MAX_WORK_AXES
from app.features.work_axes.service import WorkAxisService
from app.shared.exceptions import SecondServeException


def make_service(count: int = 0, created_axis=None):
    repo = MagicMock()
    repo.count = AsyncMock(return_value=count)
    if created_axis:
        repo.create = AsyncMock(return_value=created_axis)
    repo.update = AsyncMock(return_value=None)
    repo.delete = AsyncMock(return_value=False)
    return WorkAxisService(repo)


@pytest.mark.asyncio
async def test_create_raises_409_when_at_max():
    service = make_service(count=MAX_WORK_AXES)
    request = WorkAxisRequest(title="Axe 4", created_at=1000)
    with pytest.raises(SecondServeException) as exc_info:
        await service.create(request)
    assert exc_info.value.status_code == 409
    assert exc_info.value.error_code == "MAX_WORK_AXES_REACHED"


@pytest.mark.asyncio
async def test_create_raises_409_when_already_at_max_exactly():
    service = make_service(count=3)
    request = WorkAxisRequest(title="Un axe de plus", created_at=1000)
    with pytest.raises(SecondServeException) as exc_info:
        await service.create(request)
    assert exc_info.value.status_code == 409


@pytest.mark.asyncio
async def test_update_raises_404_when_not_found():
    service = make_service()
    service.repository.update = AsyncMock(return_value=None)
    request = WorkAxisRequest(title="Titre", created_at=1000)
    with pytest.raises(HTTPException) as exc_info:
        await service.update(99999, request)
    assert exc_info.value.status_code == 404
    assert exc_info.value.detail["error_code"] == "WORK_AXIS_NOT_FOUND"


@pytest.mark.asyncio
async def test_delete_raises_404_when_not_found():
    service = make_service()
    service.repository.delete = AsyncMock(return_value=False)
    with pytest.raises(HTTPException) as exc_info:
        await service.delete(99999)
    assert exc_info.value.status_code == 404
    assert exc_info.value.detail["error_code"] == "WORK_AXIS_NOT_FOUND"


def test_work_axis_request_strips_whitespace():
    req = WorkAxisRequest(title="  Revers  ", created_at=1000)
    assert req.title == "Revers"


def test_work_axis_request_rejects_empty_title():
    with pytest.raises(ValidationError):
        WorkAxisRequest(title="   ", created_at=1000)


def test_work_axis_request_rejects_title_over_200_chars():
    with pytest.raises(ValidationError):
        WorkAxisRequest(title="x" * 201, created_at=1000)


def test_work_axis_request_accepts_title_of_200_chars():
    req = WorkAxisRequest(title="x" * 200, created_at=1000)
    assert len(req.title) == 200


def test_max_work_axes_constant_is_3():
    assert MAX_WORK_AXES == 3
