import logging

from fastapi import FastAPI
from app.api.v1.router import api_router
from app.core.config import settings

logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="SecondServe Backend",
    version="1.0.0",
    docs_url="/docs" if settings.debug else None,
)

app.include_router(api_router, prefix="/api/v1")
