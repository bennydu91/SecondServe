from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_WEAK_DEFAULT_SECRET = "changeme-in-production"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    jwt_secret: str = _WEAK_DEFAULT_SECRET
    mistral_api_key: str = ""
    database_url: str = "sqlite+aiosqlite:///./secondserve.db"
    debug: bool = False
    port: int = 8000
    google_client_id: str = ""
    public_web_base_url: str = "http://localhost:3000"
    authorized_email: str = "ben.finot@gmail.com"
    monitor_db_url: str = "sqlite+aiosqlite:///./monitor.db"
    monitor_user: str = "admin"
    monitor_password: str = "changeme"

    @model_validator(mode="after")
    def validate_jwt_secret(self) -> "Settings":
        if not self.debug:
            if self.jwt_secret == _WEAK_DEFAULT_SECRET:
                raise ValueError(
                    "JWT_SECRET must be changed from the default value. "
                    "Set a strong secret (32+ chars) in your .env file."
                )
            if len(self.jwt_secret) < 32:
                raise ValueError(
                    "JWT_SECRET must be at least 32 characters long."
                )
        return self


settings = Settings()
