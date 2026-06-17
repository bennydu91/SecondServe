import pytest
from unittest.mock import AsyncMock, MagicMock

from app.features.sessions.schemas import SessionCreateRequest, SessionResponse
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
