import logging
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.core.database import AsyncSessionLocal
from app.core.config import settings
from app.features.notifications.service import generate_pending_for_upcoming
from app.features.monitoring.database import MonitoringSessionLocal
from app.features.monitoring import service as monitoring_svc

logger = logging.getLogger(__name__)

_scheduler: AsyncIOScheduler | None = None


async def _run_job() -> None:
    async with AsyncSessionLocal() as db:
        try:
            count = await generate_pending_for_upcoming(db, settings.mistral_api_key)
            await db.commit()
            if count:
                logger.info("APScheduler job: %d notifications pré-match générées", count)
        except Exception as exc:
            await db.rollback()
            logger.error("APScheduler job: erreur: %s", exc)


async def _run_purge_job() -> None:
    async with MonitoringSessionLocal() as db:
        try:
            await monitoring_svc.purge_old_records(db, days=30)
            logger.info("APScheduler: purge monitor.db — entrées > 30 jours supprimées")
        except Exception as exc:
            logger.error("APScheduler: erreur purge monitoring: %s", exc)


def start_scheduler() -> None:
    global _scheduler
    _scheduler = AsyncIOScheduler()
    _scheduler.add_job(_run_job, "interval", minutes=30, id="pre_match_reminder")
    _scheduler.add_job(_run_purge_job, "cron", hour=3, minute=0, id="monitoring_purge")
    _scheduler.start()
    logger.info("APScheduler démarré (intervalle 30 min)")


def stop_scheduler() -> None:
    global _scheduler
    if _scheduler and _scheduler.running:
        _scheduler.shutdown(wait=False)
        logger.info("APScheduler arrêté")
