import json
import time
from app.core.config import settings
from app.features.live_sharing.broadcast import broadcaster
from app.features.live_sharing.repository import MatchShareRepository
from app.features.live_sharing.schemas import (
    CreateShareRequest,
    CreateShareResponse,
    LiveScoreUpdateRequest,
    LiveSnapshotResponse,
)
from app.shared.exceptions import SecondServeException

SHARE_RETENTION_MS_AFTER_MATCH = 48 * 60 * 60 * 1000


class LiveSharingService:
    def __init__(self, repository: MatchShareRepository):
        self.repository = repository

    async def create_share(self, request: CreateShareRequest) -> CreateShareResponse:
        share = await self.repository.get_by_session(request.session_id)
        if share is None:
            share = await self.repository.create(request.session_id)
        return CreateShareResponse(
            token=share.token,
            url=f"{settings.public_web_base_url}/live/{share.token}",
        )

    async def push_score(self, session_id: int, request: LiveScoreUpdateRequest) -> None:
        share = await self.repository.get_by_session(session_id)
        if share is None:
            return  # match non partagé : ignoré silencieusement
        snapshot = request.model_dump()
        expires_at = (
            int(time.time() * 1000) + SHARE_RETENTION_MS_AFTER_MATCH
            if request.is_match_over
            else None
        )
        await self.repository.update_snapshot(share, json.dumps(snapshot), expires_at)
        broadcaster.publish(share.token, self._build_snapshot_response(snapshot).model_dump())

    async def get_snapshot(self, token: str) -> LiveSnapshotResponse:
        share = await self.repository.get_by_token(token)
        if share is None:
            raise SecondServeException(
                error_code="SHARE_NOT_FOUND", message="Lien introuvable", status_code=404
            )
        now_ms = int(time.time() * 1000)
        if share.expires_at is not None and share.expires_at < now_ms:
            raise SecondServeException(
                error_code="SHARE_EXPIRED",
                message="Ce lien n'est plus disponible",
                status_code=410,
            )
        if share.score_snapshot is None:
            return LiveSnapshotResponse(status="WAITING")
        data = json.loads(share.score_snapshot)
        return self._build_snapshot_response(data)

    @staticmethod
    def _build_snapshot_response(data: dict) -> LiveSnapshotResponse:
        """Dérive le statut à partir de is_match_over pour produire une forme
        de snapshot unique, utilisée à la fois par get_snapshot et push_score."""
        data = dict(data)
        is_match_over = data.pop("is_match_over", False)
        status = "ENDED" if is_match_over else "LIVE"
        return LiveSnapshotResponse(status=status, **data)
