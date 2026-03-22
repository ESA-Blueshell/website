import logging
from typing import Optional

import httpx

log = logging.getLogger(__name__)


class InfisicalClient:
    def __init__(self, base_url: str, token: str, project_id: str) -> None:
        self._base_url = base_url.rstrip("/")
        self._project_id = project_id
        self._http = httpx.Client(
            headers={"Authorization": f"Bearer {token}"},
            timeout=30.0,
        )

    def get(self, name: str, environment: str) -> str:
        response = self._http.get(
            f"{self._base_url}/api/v3/secrets/raw/{name}",
            params={
                "workspaceId": self._project_id,
                "environment": environment,
                "secretPath": "/",
            },
        )
        response.raise_for_status()
        return response.json()["secret"]["secretValue"]

    def set(self, name: str, value: str, environment: str) -> None:
        response = self._http.patch(
            f"{self._base_url}/api/v3/secrets/raw/{name}",
            json={
                "workspaceId": self._project_id,
                "environment": environment,
                "secretPath": "/",
                "secretValue": value,
            },
        )
        response.raise_for_status()
        log.debug("Updated secret %s in env=%s", name, environment)

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> "InfisicalClient":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()


def try_infisical_set(
    infisical: Optional[InfisicalClient], name: str, value: str, env: str
) -> None:
    """Update a secret in Infisical if the client is available, log warning otherwise."""
    if infisical is None:
        log.info("Infisical not configured — skipping remote update for %s", name)
        return
    try:
        infisical.set(name, value, env)
    except Exception:
        log.warning("Failed to update %s in Infisical — local env file updated", name, exc_info=True)
