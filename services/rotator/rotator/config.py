import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    infisical_token: str
    infisical_project_id: str
    infisical_base_url: str
    listmonk_url: str


def load() -> Config:
    return Config(
        infisical_token=_require("INFISICAL_TOKEN"),
        infisical_project_id=_require("INFISICAL_PROJECT_ID"),
        infisical_base_url=os.getenv("INFISICAL_BASE_URL", "https://app.infisical.com"),
        listmonk_url=os.getenv("LISTMONK_URL", "http://listmonk:9000"),
    )


def _require(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Required environment variable {name!r} is not set")
    return value
