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
