"""Load and validate credentials from vps/.env or environment variables."""

from __future__ import annotations

import os
import re
import secrets
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Credentials:
    # Required
    admin_password: str
    root_password: str
    website_password: str
    ghcr_username: str
    ghcr_token: str

    # SSH key paths
    admin_key: Path = field(default_factory=lambda: Path.home() / ".ssh" / "blueshell-admin")
    website_key: Path = field(default_factory=lambda: Path.home() / ".ssh" / "blueshell-website")
    github_deploy_key: Path = field(
        default_factory=lambda: Path.home() / ".ssh" / "blueshell-website-github-deploy-key"
    )

    # Auto-generated secrets (filled by render if blank)
    mysql_root_password: str = ""
    mysql_database: str = "blueshell"
    mysql_user: str = "blueshell"
    mysql_password: str = ""
    jwt_secret: str = ""
    smtp_host: str = ""
    smtp_port: str = "587"
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_use_ssl: str = "false"
    smtp_use_tls: str = "true"
    brevo_api_key: str = ""
    google_calendar_id: str = ""
    google_calendar_sa_json: str = ""
    listmonk_db_password: str = ""
    listmonk_admin_username: str = "admin"
    listmonk_admin_password: str = ""
    listmonk_admin_email: str = ""
    listmonk_smtp_host: str = ""
    listmonk_smtp_port: str = "587"
    listmonk_smtp_username: str = ""
    listmonk_smtp_password: str = ""
    transip_account_name: str = ""
    transip_private_key_file: str = ""  # local path to TransIP API private key PEM
    grafana_admin_password: str = ""
    grafana_discord_webhook_url: str = ""
    infisical_db_password: str = ""
    infisical_redis_password: str = ""
    infisical_encryption_key: str = ""
    infisical_auth_secret: str = ""
    infisical_admin_email: str = ""
    infisical_admin_password: str = ""
    base_domain: str = "v2.esa-blueshell.nl"
    infra_domain: str = ""
    git_branch: str = "main"


def load_dotenv(path: Path) -> dict[str, str]:
    """Parse a KEY=VALUE .env file, ignoring comments and blank lines."""
    env: dict[str, str] = {}
    if not path.is_file():
        return env
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        value = value.strip()
        if not value or value.startswith("#"):
            env[key.strip()] = ""
            continue
        if value[0] in {"'", '"'} and len(value) >= 2 and value[-1] == value[0]:
            env[key.strip()] = value[1:-1]
            continue
        env[key.strip()] = re.sub(r"\s+#.*$", "", value).rstrip()
    return env


def auto_secret(length: int = 32) -> str:
    """Generate a random base64 secret (matches render.sh auto_secret)."""
    return secrets.token_urlsafe(length)


def auto_hex(nbytes: int = 16) -> str:
    """Generate a random hex string (matches render.sh auto_hex)."""
    return secrets.token_hex(nbytes)


def _get(env: dict[str, str], key: str, default: str = "") -> str:
    """Get from os.environ first, then from .env dict, then default."""
    return os.environ.get(key, env.get(key, default))


def load_credentials(vps_dir: Path) -> Credentials:
    """Load credentials from vps/.env and/or environment variables.

    Raises ValueError if required variables are missing.
    """
    env = load_dotenv(vps_dir / ".env")

    admin_password = _get(env, "ADMIN_PASSWORD")
    root_password = _get(env, "ROOT_PASSWORD")
    website_password = _get(env, "WEBSITE_PASSWORD")
    ghcr_username = _get(env, "GHCR_USERNAME")
    ghcr_token = _get(env, "GHCR_TOKEN")

    transip_account_name = _get(env, "TRANSIP_ACCOUNT_NAME")
    transip_private_key_file = _get(env, "TRANSIP_PRIVATE_KEY_FILE")

    missing = []
    if not admin_password:
        missing.append("ADMIN_PASSWORD")
    if not root_password:
        missing.append("ROOT_PASSWORD")
    if not website_password:
        missing.append("WEBSITE_PASSWORD")
    if not ghcr_username:
        missing.append("GHCR_USERNAME")
    if not ghcr_token:
        missing.append("GHCR_TOKEN")
    if not transip_account_name:
        missing.append("TRANSIP_ACCOUNT_NAME")
    if not transip_private_key_file:
        missing.append("TRANSIP_PRIVATE_KEY_FILE")
    if missing:
        raise ValueError(
            f"Missing required credentials: {', '.join(missing)}. "
            f"Set them in vps/.env or export to the environment."
        )

    git_branch = _get(env, "GIT_BRANCH", "main")
    base_domain = _get(env, "BASE_DOMAIN", "v2.esa-blueshell.nl")

    return Credentials(
        admin_password=admin_password,
        root_password=root_password,
        website_password=website_password,
        ghcr_username=ghcr_username,
        ghcr_token=ghcr_token,
        mysql_root_password=_get(env, "MYSQL_ROOT_PASSWORD") or auto_secret(),
        mysql_database=_get(env, "MYSQL_DATABASE", "blueshell"),
        mysql_user=_get(env, "MYSQL_USER", "blueshell"),
        mysql_password=_get(env, "MYSQL_PASSWORD") or auto_secret(),
        jwt_secret=_get(env, "JWT_SECRET") or auto_secret(48),
        smtp_host=_get(env, "SMTP_HOST"),
        smtp_port=_get(env, "SMTP_PORT", "587"),
        smtp_username=_get(env, "SMTP_USERNAME"),
        smtp_password=_get(env, "SMTP_PASSWORD"),
        smtp_use_ssl=_get(env, "SMTP_USE_SSL", "false"),
        smtp_use_tls=_get(env, "SMTP_USE_TLS", "true"),
        brevo_api_key=_get(env, "BREVO_API_KEY"),
        google_calendar_id=_get(env, "GOOGLE_CALENDAR_ID"),
        google_calendar_sa_json=_get(env, "GOOGLE_CALENDAR_SA_JSON"),
        listmonk_db_password=_get(env, "LISTMONK_DB_PASSWORD") or auto_secret(),
        listmonk_admin_username=_get(env, "LISTMONK_ADMIN_USERNAME", "admin"),
        listmonk_admin_password=_get(env, "LISTMONK_ADMIN_PASSWORD") or auto_secret(),
        listmonk_admin_email=_get(env, "LISTMONK_ADMIN_EMAIL"),
        listmonk_smtp_host=_get(env, "LISTMONK_SMTP_HOST"),
        listmonk_smtp_port=_get(env, "LISTMONK_SMTP_PORT", "587"),
        listmonk_smtp_username=_get(env, "LISTMONK_SMTP_USERNAME"),
        listmonk_smtp_password=_get(env, "LISTMONK_SMTP_PASSWORD"),
        transip_account_name=transip_account_name,
        transip_private_key_file=transip_private_key_file,
        grafana_admin_password=_get(env, "GRAFANA_ADMIN_PASSWORD") or auto_secret(),
        grafana_discord_webhook_url=_get(env, "GRAFANA_DISCORD_WEBHOOK_URL"),
        infisical_db_password=_get(env, "INFISICAL_DB_PASSWORD") or auto_secret(),
        infisical_redis_password=_get(env, "INFISICAL_REDIS_PASSWORD") or auto_secret(),
        infisical_encryption_key=_get(env, "INFISICAL_ENCRYPTION_KEY") or auto_hex(16),
        infisical_auth_secret=_get(env, "INFISICAL_AUTH_SECRET") or auto_secret(48),
        infisical_admin_email=_get(env, "INFISICAL_ADMIN_EMAIL"),
        infisical_admin_password=_get(env, "INFISICAL_ADMIN_PASSWORD") or auto_secret(),
        base_domain=base_domain,
        infra_domain=_get(env, "INFRA_DOMAIN", base_domain),
        git_branch=git_branch,
    )
