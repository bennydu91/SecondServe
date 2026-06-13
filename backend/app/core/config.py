from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    jwt_secret: str = "changeme-in-production"
    mistral_api_key: str = ""
    database_url: str = "sqlite+aiosqlite:///./secondserve.db"
    debug: bool = False


settings = Settings()
