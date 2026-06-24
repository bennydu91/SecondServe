import logging
import time
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.notifications.models import PendingNotificationModel
from app.features.sessions.models import SessionModel
from app.features.coaching import mistral_client

logger = logging.getLogger(__name__)

LOOKAHEAD_MIN_SEC = 90 * 60
LOOKAHEAD_MAX_SEC = 4 * 60 * 60
CONTENT_TTL_SEC = 3 * 60 * 60


async def generate_pending_for_upcoming(db: AsyncSession, api_key: str) -> int:
    """APScheduler job : génère le contenu coaching pré-match pour les sessions
    planifiées dans la fenêtre [now+1h30, now+4h]."""
    now_ms = int(time.time() * 1000)
    window_min = now_ms + LOOKAHEAD_MIN_SEC * 1000
    window_max = now_ms + LOOKAHEAD_MAX_SEC * 1000

    result = await db.execute(
        select(SessionModel).where(
            SessionModel.scheduled_at.isnot(None),
            SessionModel.scheduled_at >= window_min,
            SessionModel.scheduled_at <= window_max,
            SessionModel.status == "PLANNED",
        )
    )
    sessions = result.scalars().all()

    generated = 0
    for session in sessions:
        existing = await db.execute(
            select(PendingNotificationModel).where(
                PendingNotificationModel.session_id == session.id
            )
        )
        if existing.scalar_one_or_none() is not None:
            continue

        try:
            prompt = _build_prompt(session)
            content = await mistral_client.generate(prompt, api_key)
            pending = PendingNotificationModel(
                session_id=session.id,
                content=content,
                generated_at=now_ms,
                expires_at=now_ms + CONTENT_TTL_SEC * 1000,
            )
            db.add(pending)
            generated += 1
            logger.info("APScheduler: contenu pré-match généré pour session_id=%d", session.id)
        except Exception as exc:
            logger.error("APScheduler: erreur génération session %d: %s", session.id, exc)

    await db.flush()
    return generated


def _build_prompt(session: SessionModel) -> str:
    parts = [
        f"Surface : {session.surface}",
        f"Format : {session.match_format}",
    ]
    if session.opponent:
        parts.append(f"Adversaire : {session.opponent}")
    context = ", ".join(parts)
    return (
        f"En tant que coach tennis IA, génère un conseil de préparation mentale et tactique "
        f"avant le match ({context}). "
        f"Sois concis (2-3 phrases), actionnable, et personnalisé selon le contexte fourni. "
        f"Pas de formule de politesse."
    )
