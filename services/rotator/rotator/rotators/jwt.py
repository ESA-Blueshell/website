import logging
import secrets

from rotator import docker_ops
from rotator.config import Config
from rotator.infisical import InfisicalClient
from rotator.rotators.base import Rotator

log = logging.getLogger(__name__)


class JwtRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: InfisicalClient, config: Config) -> None:
        log.info("[jwt] rotating JWT_SECRET for stack=%s", stack)

        new_secret = secrets.token_urlsafe(64)
        infisical.set("JWT_SECRET", new_secret, env)

        docker_ops.force_update(f"{stack}_api")
        log.info("[jwt] done — all active sessions invalidated")
