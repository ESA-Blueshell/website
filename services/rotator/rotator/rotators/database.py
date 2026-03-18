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


class DatabaseRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        self._rotate_mysql(env, stack, infisical, config)
        self._rotate_listmonk_db(env, stack, infisical, config)

    def _rotate_mysql(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        log.info("[database] rotating MYSQL_PASSWORD for stack=%s", stack)

        db_env = os.path.join(config.repo_root, "services/api/.db.env")
        root_pw = self._get_root_password(env, infisical, db_env)
        new_pw = secrets.token_urlsafe(32)

        docker_ops.exec_in_service(f"{stack}_db", [
            "mysql", f"-uroot", f"-p{root_pw}", "-e",
            f"ALTER USER 'blueshell'@'%' IDENTIFIED BY '{new_pw}'; FLUSH PRIVILEGES;",
        ])

        update_env_file(db_env, "MYSQL_PASSWORD", new_pw)
        try_infisical_set(infisical, "MYSQL_PASSWORD", new_pw, env)
        docker_ops.stack_deploy(config.repo_root, stack)
        log.info("[database] MYSQL_PASSWORD rotated")

    def _rotate_listmonk_db(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        log.info("[database] rotating LISTMONK_DB_PASSWORD for stack=%s", stack)

        new_pw = secrets.token_urlsafe(32)

        docker_ops.exec_in_service(f"{stack}_listmonk-db", [
            "psql", "-U", "listmonk", "-c",
            f"ALTER USER listmonk WITH PASSWORD '{new_pw}';",
        ])

        listmonk_env = os.path.join(config.repo_root, "services/listmonk/.listmonk.env")
        update_env_file(listmonk_env, "LISTMONK_DB_PASSWORD", new_pw)
        try_infisical_set(infisical, "LISTMONK_DB_PASSWORD", new_pw, env)
        docker_ops.stack_deploy(config.repo_root, stack)
        log.info("[database] LISTMONK_DB_PASSWORD rotated")

    @staticmethod
    def _get_root_password(env: str, infisical: Optional[InfisicalClient], db_env: str) -> str:
        """Get root password from Infisical or fall back to local env file."""
        if infisical is not None:
            try:
                return infisical.get("MYSQL_ROOT_PASSWORD", env)
            except Exception:
                log.warning("Could not fetch MYSQL_ROOT_PASSWORD from Infisical, trying local env")
        # Fall back to reading the local env file
        if os.path.isfile(db_env):
            with open(db_env) as f:
                for line in f:
                    if line.startswith("MYSQL_ROOT_PASSWORD="):
                        return line.split("=", 1)[1].strip()
        raise RuntimeError("MYSQL_ROOT_PASSWORD not found in Infisical or local env file")
