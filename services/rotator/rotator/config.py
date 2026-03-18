import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    infisical_token: str
    infisical_project_id: str
    infisical_base_url: str
    listmonk_url: str
    repo_root: str

    @property
    def infisical_configured(self) -> bool:
        return bool(self.infisical_token and self.infisical_project_id)


def load() -> Config:
    return Config(
        infisical_token=os.getenv("INFISICAL_TOKEN", ""),
        infisical_project_id=os.getenv("INFISICAL_PROJECT_ID", ""),
        infisical_base_url=os.getenv("INFISICAL_BASE_URL", "https://vault.esa-blueshell.nl"),
        listmonk_url=os.getenv("LISTMONK_URL", "http://listmonk:9000"),
        repo_root=os.getenv("REPO_ROOT", "/src/website"),
    )
