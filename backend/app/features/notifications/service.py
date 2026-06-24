import logging
import time
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.notifications.models import PendingNotificationModel
from app.features.sessions.models import SessionModel
from app.features.profile.models import PlayerProfile
from app.features.work_axes.models import WorkAxis
from app.features.coaching import mistral_client

logger = logging.getLogger(__name__)

LOOKAHEAD_MIN_SEC = 30 * 60        # fenêtre élargie : 30 min avant le match (était 90 min)
LOOKAHEAD_MAX_SEC = 4 * 60 * 60    # 4h avant le match
CONTENT_TTL_SEC = 3 * 60 * 60      # contenu valide 3h


async def generate_pending_for_upcoming(db: AsyncSession, api_key: str) -> int:
    """APScheduler job : génère le contenu coaching pré-match pour les sessions
    planifiées dans la fenêtre [now+30min, now+4h]."""
    if not api_key or not api_key.strip():
        logger.warning("APScheduler: MISTRAL_API_KEY non configurée — génération pré-match ignorée")
        return 0
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

    profile_result = await db.execute(select(PlayerProfile).where(PlayerProfile.id == 1))
    profile = profile_result.scalar_one_or_none()

    axes_result = await db.execute(select(WorkAxis).order_by(WorkAxis.created_at.desc()).limit(3))
    axes = axes_result.scalars().all()

    recent_result = await db.execute(
        select(SessionModel)
        .where(SessionModel.status == "COMPLETED")
        .order_by(SessionModel.created_at.desc())
        .limit(5)
    )
    recent_sessions = recent_result.scalars().all()

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
            prompt = _build_prompt(session, profile, list(axes), list(recent_sessions))
            content = await mistral_client.generate(prompt, api_key)
            if not content or not content.strip():
                logger.warning("APScheduler: Mistral retourne contenu vide pour session_id=%d", session.id)
                continue
            pending = PendingNotificationModel(
                session_id=session.id,
                content=content.strip(),
                generated_at=now_ms,
                expires_at=now_ms + CONTENT_TTL_SEC * 1000,
            )
            db.add(pending)
            generated += 1
            logger.info("APScheduler: contenu pré-match généré pour session_id=%d", session.id)
        except Exception as exc:
            logger.error("APScheduler: erreur génération session %d: %s", session.id, exc)

    return generated


def _build_prompt(
    session: SessionModel,
    profile: PlayerProfile | None,
    axes: list,
    recent_sessions: list,
) -> str:
    parts = [
        f"Surface : {session.surface}",
        f"Format : {session.match_format}",
    ]
    if session.opponent:
        parts.append(f"Adversaire : {session.opponent}")
    if profile:
        if profile.current_series:
            parts.append(f"Classement : {profile.current_series}" + (f" ({profile.current_points} pts)" if profile.current_points else ""))
        if profile.play_style:
            parts.append(f"Style de jeu : {profile.play_style}")
    if axes:
        parts.append("Axes de travail : " + ", ".join(a.title for a in axes))
    if recent_sessions:
        recent_results = [s.result for s in recent_sessions if s.result]
        if recent_results:
            parts.append(f"Derniers résultats : {', '.join(recent_results[:3])}")

    context = " | ".join(parts)
    return (
        f"En tant que coach tennis IA, génère un conseil de préparation mentale et tactique "
        f"avant le match. Contexte joueur : {context}. "
        f"Sois concis (2-3 phrases), actionnable, et utilise le contexte fourni pour personnaliser le conseil. "
        f"Pas de formule de politesse."
    )
