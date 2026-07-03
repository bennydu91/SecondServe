import pytest
from unittest.mock import AsyncMock, MagicMock

from app.features.sessions.schemas import SessionCreateRequest, SessionResponse, SessionsResponse
from app.features.sessions.service import SessionService


def make_service(created_session=None):
    repo = MagicMock()
    if created_session:
        repo.create = AsyncMock(return_value=created_session)
    else:
        repo.create = AsyncMock(return_value=None)
    return SessionService(repo)


def session_model(
    id=1,
    surface="CLAY",
    match_format="BEST_OF_3",
    third_set_rule="FULL_ADVANTAGE",
    opponent=None,
    competition_type=None,
    tournament=None,
    status="ACTIVE",
    session_type="MATCH",
    result=None,
    score_text=None,
    score_seed_json=None,
    created_at=1_000_000,
    updated_at=1_000_000
):
    m = MagicMock()
    m.id = id
    m.surface = surface
    m.match_format = match_format
    m.third_set_rule = third_set_rule
    m.opponent = opponent
    m.competition_type = competition_type
    m.tournament = tournament
    m.status = status
    m.session_type = session_type
    m.result = result
    m.score_text = score_text
    m.score_seed_json = score_seed_json
    m.created_at = created_at
    m.updated_at = updated_at
    return m


@pytest.mark.asyncio
async def test_create_session_returns_session_response():
    model = session_model(id=42, surface="HARD", match_format="BEST_OF_1")
    service = make_service(created_session=model)

    request = SessionCreateRequest(
        surface="HARD",
        match_format="BEST_OF_1",
        third_set_rule="FULL_ADVANTAGE",
        created_at=1_000_000
    )
    response = await service.create_session(request)

    assert isinstance(response, SessionResponse)
    assert response.id == 42
    assert response.surface == "HARD"
    assert response.match_format == "BEST_OF_1"
    assert response.status == "ACTIVE"
    assert response.session_type == "MATCH"


@pytest.mark.asyncio
async def test_create_session_with_optional_fields():
    model = session_model(
        id=1,
        surface="GRASS",
        match_format="BEST_OF_3",
        third_set_rule="SUPER_TIE_BREAK_10",
        opponent="Dupont",
        competition_type="Tournoi open",
        tournament="Roland Garros"
    )
    service = make_service(created_session=model)

    request = SessionCreateRequest(
        surface="GRASS",
        match_format="BEST_OF_3",
        third_set_rule="SUPER_TIE_BREAK_10",
        opponent="Dupont",
        competition_type="Tournoi open",
        tournament="Roland Garros",
        created_at=1_000_000
    )
    response = await service.create_session(request)

    assert response.opponent == "Dupont"
    assert response.competition_type == "Tournoi open"
    assert response.tournament == "Roland Garros"
    assert response.third_set_rule == "SUPER_TIE_BREAK_10"


@pytest.mark.asyncio
async def test_create_session_repository_called_once():
    model = session_model()
    repo = MagicMock()
    repo.create = AsyncMock(return_value=model)
    service = SessionService(repo)

    request = SessionCreateRequest(
        surface="CLAY",
        match_format="BEST_OF_3",
        third_set_rule="FULL_ADVANTAGE",
        created_at=1_000_000
    )
    await service.create_session(request)

    repo.create.assert_called_once_with(request)


@pytest.mark.asyncio
async def test_list_sessions_returns_all_from_repository():
    model_a = session_model(id=1, created_at=2_000_000, score_text="6-4 · 6-3")
    model_b = session_model(id=2, created_at=1_000_000)
    repo = MagicMock()
    repo.get_all = AsyncMock(return_value=[model_a, model_b])
    service = SessionService(repo)

    result = await service.list_sessions()

    assert isinstance(result, SessionsResponse)
    assert result.total == 2
    assert [item.id for item in result.items] == [1, 2]
    assert result.items[0].score_text == "6-4 · 6-3"


@pytest.mark.asyncio
async def test_list_sessions_empty():
    repo = MagicMock()
    repo.get_all = AsyncMock(return_value=[])
    service = SessionService(repo)

    result = await service.list_sessions()

    assert result.total == 0
    assert result.items == []


import json
from app.features.sessions.schemas import ScoreSeedRequest
from app.shared.exceptions import SecondServeException


@pytest.mark.asyncio
async def test_update_score_seed_returns_session_response():
    model = session_model(id=5, score_seed_json='{"current_set_games_a": 3}')
    repo = MagicMock()
    repo.update_score_seed = AsyncMock(return_value=model)
    service = SessionService(repo)

    request = ScoreSeedRequest(current_set_games_a=3)
    response = await service.update_score_seed(5, request)

    assert response.id == 5
    assert response.score_seed_json == '{"current_set_games_a": 3}'
    repo.update_score_seed.assert_called_once_with(5, json.dumps(request.model_dump()))


@pytest.mark.asyncio
async def test_update_score_seed_raises_when_session_not_found():
    repo = MagicMock()
    repo.update_score_seed = AsyncMock(return_value=None)
    service = SessionService(repo)

    with pytest.raises(SecondServeException) as exc_info:
        await service.update_score_seed(999, ScoreSeedRequest())

    assert exc_info.value.error_code == "SESSION_NOT_FOUND"
    assert exc_info.value.status_code == 404
