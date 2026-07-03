import pytest
from unittest.mock import AsyncMock, MagicMock

from app.features.points.schemas import PointCreateRequest, PointResponse, PointsResponse
from app.features.points.service import PointService


def point_model(id=1, session_id=1, scorer="A", context="ACE", sequence_num=1, recorded_at=1_000_000):
    m = MagicMock()
    m.id = id
    m.session_id = session_id
    m.scorer = scorer
    m.context = context
    m.sequence_num = sequence_num
    m.recorded_at = recorded_at
    return m


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "context,expected_scorer",
    [
        ("ACE", "A"),
        ("WINNER", "A"),
        ("FORCED_ERROR", "A"),
        ("UNFORCED_ERROR_OPPONENT", "A"),
        ("ACE_OPPONENT", "B"),
        ("WINNER_OPPONENT", "B"),
        ("UNFORCED_ERROR_SELF", "B"),
        ("DOUBLE_FAULT", "B"),
    ],
)
async def test_create_point_derives_scorer_from_context(context, expected_scorer):
    model = point_model(scorer=expected_scorer, context=context)
    repo = MagicMock()
    repo.create = AsyncMock(return_value=model)
    service = PointService(repo)

    response = await service.create_point(7, PointCreateRequest(context=context))

    assert isinstance(response, PointResponse)
    repo.create.assert_called_once_with(7, expected_scorer, context)
    assert response.scorer == expected_scorer


@pytest.mark.asyncio
async def test_list_points_returns_all_from_repository():
    model_a = point_model(id=1, sequence_num=1)
    model_b = point_model(id=2, sequence_num=2)
    repo = MagicMock()
    repo.get_all_for_session = AsyncMock(return_value=[model_a, model_b])
    service = PointService(repo)

    result = await service.list_points(7)

    assert isinstance(result, PointsResponse)
    assert [item.id for item in result.items] == [1, 2]


@pytest.mark.asyncio
async def test_delete_last_point_calls_repository():
    repo = MagicMock()
    repo.delete_last = AsyncMock(return_value=True)
    service = PointService(repo)

    await service.delete_last_point(7)

    repo.delete_last.assert_called_once_with(7)
