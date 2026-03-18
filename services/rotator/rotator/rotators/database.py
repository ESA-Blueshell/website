import logging
import secrets

from rotator import docker_ops
from rotator.config import Config
from rotator.infisical import InfisicalClient
from rotator.rotators.base import Rotator

log = logging.getLogger(__name__)


class DatabaseRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: InfisicalClient, config: Config) -> None:
        self._rotate_mysql(env, stack, infisical)
        self._rotate_listmonk_db(env, stack, infisical)

    def _rotate_mysql(self, env: str, stack: str, infisical: InfisicalClient) -> None:
        log.info("[database] rotating MYSQL_PASSWORD for stack=%s", stack)

        root_pw = infisical.get("MYSQL_ROOT_PASSWORD", env)
        new_pw = secrets.token_urlsafe(32)

        docker_ops.exec_in_service(f"{stack}_db", [
            "mysql", f"-uroot", f"-p{root_pw}", "-e",
            f"ALTER USER 'blueshell'@'%' IDENTIFIED BY '{new_pw}'; FLUSH PRIVILEGES;",
        ])

        infisical.set("MYSQL_PASSWORD", new_pw, env)
        docker_ops.force_update(f"{stack}_api")
        log.info("[database] MYSQL_PASSWORD rotated")

    def _rotate_listmonk_db(self, env: str, stack: str, infisical: InfisicalClient) -> None:
        log.info("[database] rotating LISTMONK_DB_PASSWORD for stack=%s", stack)

        new_pw = secrets.token_urlsafe(32)

        docker_ops.exec_in_service(f"{stack}_listmonk-db", [
            "psql", "-U", "listmonk", "-c",
            f"ALTER USER listmonk WITH PASSWORD '{new_pw}';",
        ])

        infisical.set("LISTMONK_DB_PASSWORD", new_pw, env)
        docker_ops.force_update(f"{stack}_listmonk")
        docker_ops.force_update(f"{stack}_api")
        log.info("[database] LISTMONK_DB_PASSWORD rotated")
