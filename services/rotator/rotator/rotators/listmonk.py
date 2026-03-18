import logging
import secrets

import httpx

from rotator import docker_ops
from rotator.config import Config
from rotator.infisical import InfisicalClient
from rotator.rotators.base import Rotator

log = logging.getLogger(__name__)


class ListmonkRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: InfisicalClient, config: Config) -> None:
        log.info("[listmonk] rotating credentials for stack=%s", stack)

        old_user = infisical.get("LISTMONK_ADMIN_USERNAME", env)
        old_pw = infisical.get("LISTMONK_ADMIN_PASSWORD", env)
        new_pw = secrets.token_urlsafe(32)

        self._update_admin_password(config.listmonk_url, old_user, old_pw, new_pw)
        infisical.set("LISTMONK_ADMIN_PASSWORD", new_pw, env)

        # Delete the API token user so listmonk-setup regenerates it on next deploy
        docker_ops.exec_in_service(f"{stack}_listmonk-db", [
            "psql", "-U", "listmonk", "-c",
            "DELETE FROM users WHERE username='api' AND type='api';",
        ])
        docker_ops.remove_volume(f"{stack}_listmonk-secrets")

        docker_ops.force_update(f"{stack}_listmonk-setup")
        docker_ops.force_update(f"{stack}_api")
        log.info("[listmonk] credentials rotated")

    def _update_admin_password(
        self, listmonk_url: str, username: str, old_pw: str, new_pw: str
    ) -> None:
        with httpx.Client() as client:
            client.post(
                f"{listmonk_url}/admin/login",
                data={"username": username, "password": old_pw},
                follow_redirects=True,
            ).raise_for_status()

            client.put(
                f"{listmonk_url}/api/profile",
                json={"password": new_pw, "password_confirm": new_pw},
            ).raise_for_status()
