import time
import pytest
from app.features.live_sharing.broadcast import broadcaster
from app.features.live_sharing.models import MatchShareModel
from app.features.live_sharing.repository import ABANDONED_SHARE_TTL_MS, MatchShareRepository
from app.features.live_sharing.schemas import CreateShareRequest, LiveScoreUpdateRequest
from app.features.live_sharing.service import LiveSharingService
from app.shared.exceptions import SecondServeException


def make_score_update(is_match_over: bool = False) -> LiveScoreUpdateRequest:
    return LiveScoreUpdateRequest(
        completed_sets=[],
        current_set_games_a=1,
        current_set_games_b=0,
        current_set_point_log=["A"],
        current_game_points_a="THIRTY",
        current_game_points_b="FIFTEEN",
        tie_break_points_a=0,
        tie_break_points_b=0,
        is_tie_break=False,
        is_super_tie_break=False,
        is_match_over=is_match_over,
        match_winner="A" if is_match_over else None,
        player_a_name="Benjamin",
        player_b_name="Marceau",
        surface="CLAY",
        tournament="Tournoi du club",
        competition_type="CLUB",
        started_at=1000,
    )


@pytest.mark.asyncio
async def test_create_share_is_idempotent(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    first = await service.create_share(CreateShareRequest(session_id=1))
    second = await service.create_share(CreateShareRequest(session_id=1))
    assert first.token == second.token
    assert first.url.endswith(f"/live/{first.token}")


@pytest.mark.asyncio
async def test_get_snapshot_unknown_token_raises_404(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    with pytest.raises(SecondServeException) as exc_info:
        await service.get_snapshot("unknown-token")
    assert exc_info.value.status_code == 404


@pytest.mark.asyncio
async def test_get_snapshot_before_first_push_is_waiting(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=2))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "WAITING"


@pytest.mark.asyncio
async def test_push_score_then_get_snapshot_is_live(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=3))
    await service.push_score(3, make_score_update(is_match_over=False))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "LIVE"
    assert snapshot.current_set_games_a == 1
    assert snapshot.player_a_name == "Benjamin"


@pytest.mark.asyncio
async def test_push_score_match_over_sets_expiry_and_ended_status(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=4))
    await service.push_score(4, make_score_update(is_match_over=True))
    snapshot = await service.get_snapshot(share.token)
    assert snapshot.status == "ENDED"
    assert snapshot.match_winner == "A"


@pytest.mark.asyncio
async def test_push_score_for_unshared_session_is_noop(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    await service.push_score(999, make_score_update())  # ne doit pas lever d'exception
    repo = MatchShareRepository(db_session)
    assert await repo.get_by_session(999) is None


@pytest.mark.asyncio
async def test_get_snapshot_expired_raises_410(db_session):
    repo = MatchShareRepository(db_session)
    service = LiveSharingService(repo)
    share = await service.create_share(CreateShareRequest(session_id=5))
    await service.push_score(5, make_score_update(is_match_over=True))
    stored = await repo.get_by_token(share.token)
    stored.expires_at = int(time.time() * 1000) - 1000  # forcer l'expiration
    await db_session.flush()
    with pytest.raises(SecondServeException) as exc_info:
        await service.get_snapshot(share.token)
    assert exc_info.value.status_code == 410


@pytest.mark.asyncio
async def test_push_score_broadcasts_live_status_not_raw_flag(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=6))
    queue = broadcaster.subscribe(share.token)
    try:
        await service.push_score(6, make_score_update(is_match_over=False))
        published = queue.get_nowait()
        assert published["status"] == "LIVE"
        assert "is_match_over" not in published
    finally:
        broadcaster.unsubscribe(share.token, queue)


@pytest.mark.asyncio
async def test_push_score_broadcasts_ended_status_when_match_over(db_session):
    service = LiveSharingService(MatchShareRepository(db_session))
    share = await service.create_share(CreateShareRequest(session_id=7))
    queue = broadcaster.subscribe(share.token)
    try:
        await service.push_score(7, make_score_update(is_match_over=True))
        published = queue.get_nowait()
        assert published["status"] == "ENDED"
        assert "is_match_over" not in published
    finally:
        broadcaster.unsubscribe(share.token, queue)


@pytest.mark.asyncio
async def test_repository_delete_expired_removes_only_past_shares(db_session):
    repo = MatchShareRepository(db_session)
    now = int(time.time() * 1000)

    expired = await repo.create(100)
    await repo.update_snapshot(expired, "{}", expires_at=now - 1000)

    active = await repo.create(101)
    await repo.update_snapshot(active, "{}", expires_at=now + 1_000_000)

    still_live = await repo.create(102)  # expires_at=None : match en cours

    deleted_count = await repo.delete_expired(now)

    assert deleted_count == 1
    assert await repo.get_by_session(100) is None
    assert await repo.get_by_session(101) is not None
    assert await repo.get_by_session(102) is not None


@pytest.mark.asyncio
async def test_repository_delete_expired_removes_abandoned_shares_past_ttl(db_session):
    """Un partage jamais clôturé (expires_at=None) doit tout de même être purgé
    une fois trop ancien, pour éviter une fuite de contexte d'adversaire à durée
    de vie infinie (app tuée, session laissée ouverte, etc.)."""
    repo = MatchShareRepository(db_session)
    now = int(time.time() * 1000)

    abandoned = MatchShareModel(
        token="abandoned-token",
        session_id=200,
        created_at=now - ABANDONED_SHARE_TTL_MS - 1000,
        expires_at=None,
        score_snapshot=None,
    )
    db_session.add(abandoned)

    still_in_progress = MatchShareModel(
        token="recent-token",
        session_id=201,
        created_at=now - 1000,
        expires_at=None,
        score_snapshot=None,
    )
    db_session.add(still_in_progress)
    await db_session.flush()

    deleted_count = await repo.delete_expired(now)

    assert deleted_count == 1
    assert await repo.get_by_session(200) is None
    assert await repo.get_by_session(201) is not None
