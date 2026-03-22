import logging
import os
import secrets
from typing import Optional

from rotator import docker_ops
from rotator.config import Config
from rotator.env_files import update_env_file
from rotator.infisical import InfisicalClient, try_infisical_set
from rotator.rotators.base import Rotator

log = logging.getLogger(__name__)


class JwtRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        log.info("[jwt] rotating JWT_SECRET for stack=%s", stack)

        new_secret = secrets.token_urlsafe(64)

        api_env = os.path.join(config.repo_root, "services/api/.api.env")
        update_env_file(api_env, "JWT_SECRET", new_secret)
        try_infisical_set(infisical, "JWT_SECRET", new_secret, env)

        docker_ops.stack_deploy(config.repo_root, stack)
        log.info("[jwt] done — all active sessions invalidated")
