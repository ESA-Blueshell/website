#!/usr/bin/env python3
"""Render the cloud-init config template with real values.

Python port of vps/cloud-init/render.sh. Can be run standalone or imported.

Usage:
    python -m tests.helpers.render          # from vps/
    ./cloud-init/render.sh                  # via bash wrapper

Reads all values from vps/.env (or exported env vars).

Generates (or reuses) ed25519 SSH keypairs in ~/.ssh:
    ~/.ssh/blueshell-website              — website application user
    ~/.ssh/blueshell-admin                — admin user
    ~/.ssh/blueshell-website-github-deploy-key — GitHub deploy key

Output:
    cloud-init/cloud-config.yaml
    cloud-init/rendered/.db.env
    cloud-init/rendered/.api.env
    cloud-init/rendered/.listmonk.env
    cloud-init/rendered/.infra.env
"""

from __future__ import annotations

import base64
import socket
import subprocess
import sys
from pathlib import Path

from .credentials import Credentials, load_credentials


def _vps_dir() -> Path:
    """Resolve the vps/ directory (parent of lib/)."""
    return Path(__file__).resolve().parent.parent


def hash_password(password: str) -> str:
    """Hash a password with SHA-512 crypt. Tries openssl, falls back to Python crypt."""
    result = subprocess.run(
        ["openssl", "passwd", "-6", password],
        capture_output=True,
        text=True,
    )
    if result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()

    # Fallback: Python crypt module (available on Python < 3.13)
    try:
        import crypt

        return crypt.crypt(password, crypt.mksalt(crypt.METHOD_SHA512))
    except (ImportError, AttributeError):
        pass

    raise RuntimeError(
        "Cannot generate SHA-512 password hash. "
        "Install OpenSSL 3.x (brew install openssl@3) or use Python < 3.13."
    )


def ensure_ssh_key(key_path: Path, comment: str) -> None:
    """Generate an ed25519 SSH keypair if it doesn't exist."""
    key_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if key_path.exists() and key_path.with_suffix(".pub").exists():
        print(f"Reusing existing key: {key_path}")
    else:
        print(f"Generating key: {key_path}")
        subprocess.run(
            ["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", comment, "-f", str(key_path)],
            check=True,
        )
    key_path.chmod(0o600)
    key_path.with_suffix(".pub").chmod(0o644)


def read_pub_key(key_path: Path) -> str:
    """Read a public key file, stripping newlines."""
    return key_path.with_suffix(".pub").read_text().strip()


def b64_encode_file(path: Path) -> str:
    """Base64-encode a file's contents (single line, no newlines)."""
    return base64.b64encode(path.read_bytes()).decode("ascii")


def b64_encode_template(path: Path, substitutions: dict[str, str]) -> str:
    """Read a file as text, apply placeholder substitutions, then base64-encode."""
    content = path.read_text()
    for placeholder, value in substitutions.items():
        content = content.replace(placeholder, value)
    return base64.b64encode(content.encode()).decode("ascii")


def write_env_file(path: Path, content: str) -> None:
    """Write an env file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)


def render(creds: Credentials, vps_dir: Path | None = None) -> Path:
    """Render the cloud-config template and env files. Returns path to rendered config."""
    if vps_dir is None:
        vps_dir = _vps_dir()

    cloud_init_dir = vps_dir / "cloud-init"
    template_path = cloud_init_dir / "cloud-config.template.yaml"
    output_path = cloud_init_dir / "cloud-config.yaml"
    rendered_dir = cloud_init_dir / "rendered"
    setup_scripts_dir = cloud_init_dir / "scripts"   # one-time setup scripts
    util_scripts_dir = vps_dir / "scripts" / "server"  # persistent utilities

    if not template_path.is_file():
        raise FileNotFoundError(f"Template not found: {template_path}")

    # ── TransIP private key ──────────────────────────────────────────────
    transip_key_path = Path(creds.transip_private_key_file) if creds.transip_private_key_file else Path()
    if not transip_key_path.is_file():
        # No real key available — write a stub for CI/schema/hash testing.
        # The stub is never used for actual deployment; production always sets
        # TRANSIP_PRIVATE_KEY_FILE to a real PEM before calling render().
        rendered_dir.mkdir(parents=True, exist_ok=True)
        transip_key_path = rendered_dir / ".transip_key_stub.pem"
        transip_key_path.write_text(
            "-----BEGIN RSA PRIVATE KEY-----\n"
            "c3R1Yi1rZXktZm9yLUNJLXRlc3Rpbmctb25seQ==\n"
            "-----END RSA PRIVATE KEY-----\n"
        )
        print("  WARNING: TRANSIP_PRIVATE_KEY_FILE not set — using stub PEM (CI/test mode only)")

    # ── SSH keypairs ─────────────────────────────────────────────────────
    hostname = socket.getfqdn()
    ensure_ssh_key(creds.website_key, f"website@{hostname}")
    ensure_ssh_key(creds.admin_key, f"admin@{hostname}")
    ensure_ssh_key(creds.github_deploy_key, "github-deploy@esa-blueshell")

    website_pub = read_pub_key(creds.website_key)
    admin_pub = read_pub_key(creds.admin_key)
    github_deploy_pub = read_pub_key(creds.github_deploy_key)

    print()
    print("  GitHub deploy key (add as read-only Deploy Key to the repository):")
    print("  Repository: Settings → Deploy keys → Add deploy key → Allow read access")
    print("  Key:")
    print(f"  {github_deploy_pub}")
    print()

    # ── Hash passwords ───────────────────────────────────────────────────
    print("==> Hashing passwords...")
    hashed_admin = hash_password(creds.admin_password)
    hashed_root = hash_password(creds.root_password)
    hashed_website = hash_password(creds.website_password)

    # ── Render env files ─────────────────────────────────────────────────
    write_env_file(
        rendered_dir / ".db.env",
        f"MYSQL_ROOT_PASSWORD={creds.mysql_root_password}\n"
        f"MYSQL_DATABASE={creds.mysql_database}\n"
        f"MYSQL_USER={creds.mysql_user}\n"
        f"MYSQL_PASSWORD={creds.mysql_password}\n",
    )
    write_env_file(
        rendered_dir / ".api.env",
        f"JWT_SECRET={creds.jwt_secret}\n"
        f"SMTP_HOST={creds.smtp_host}\n"
        f"SMTP_PORT={creds.smtp_port}\n"
        f"SMTP_USERNAME={creds.smtp_username}\n"
        f"SMTP_PASSWORD={creds.smtp_password}\n"
        f"SMTP_USE_SSL={creds.smtp_use_ssl}\n"
        f"SMTP_USE_TLS={creds.smtp_use_tls}\n"
        f"BREVO_API_KEY={creds.brevo_api_key}\n"
        f"GOOGLE_CALENDAR_ID={creds.google_calendar_id}\n"
        f"GOOGLE_CALENDAR_SA_JSON={creds.google_calendar_sa_json}\n",
    )
    write_env_file(
        rendered_dir / ".listmonk.env",
        f"LISTMONK_DB_PASSWORD={creds.listmonk_db_password}\n"
        f"LISTMONK_ADMIN_USERNAME={creds.listmonk_admin_username}\n"
        f"LISTMONK_ADMIN_PASSWORD={creds.listmonk_admin_password}\n"
        f"LISTMONK_ADMIN_EMAIL={creds.listmonk_admin_email}\n"
        f"LISTMONK_SMTP_HOST={creds.listmonk_smtp_host}\n"
        f"LISTMONK_SMTP_PORT={creds.listmonk_smtp_port}\n"
        f"LISTMONK_SMTP_USERNAME={creds.listmonk_smtp_username}\n"
        f"LISTMONK_SMTP_PASSWORD={creds.listmonk_smtp_password}\n",
    )
    write_env_file(
        rendered_dir / ".infra.env",
        f"INFRA_DOMAIN={creds.infra_domain}\n"
        f"TRANSIP_ACCOUNT_NAME={creds.transip_account_name}\n"
        f"TRANSIP_PRIVATE_KEY_FILE=/etc/traefik/transip_key.pem\n"
        f"GRAFANA_ADMIN_PASSWORD={creds.grafana_admin_password}\n"
        f"GRAFANA_DISCORD_WEBHOOK_URL={creds.grafana_discord_webhook_url}\n"
        f"INFISICAL_DB_PASSWORD={creds.infisical_db_password}\n"
        f"INFISICAL_REDIS_PASSWORD={creds.infisical_redis_password}\n"
        f"INFISICAL_ENCRYPTION_KEY={creds.infisical_encryption_key}\n"
        f"INFISICAL_AUTH_SECRET={creds.infisical_auth_secret}\n",
    )
    print(f"Rendered env files in {rendered_dir}/")

    # ── Render cloud-config template ─────────────────────────────────────
    # Python str.replace() is safe against $, &, / — no escaping needed.
    content = template_path.read_text()

    # Passwords (SHA-512 hashes)
    content = content.replace("__ADMIN_PASSWORD__", hashed_admin)
    content = content.replace("__ROOT_PASSWORD__", hashed_root)
    content = content.replace("__WEBSITE_PASSWORD__", hashed_website)

    # SSH keys, GHCR, Infisical, git branch
    content = content.replace("__GHCR_TOKEN__", creds.ghcr_token)
    content = content.replace("__GHCR_USERNAME__", creds.ghcr_username)
    content = content.replace("__WEBSITE_SSH_PUB__", website_pub)
    content = content.replace("__ADMIN_SSH_PUB__", admin_pub)
    content = content.replace("__INFISICAL_ADMIN_EMAIL__", creds.infisical_admin_email)
    content = content.replace("__INFISICAL_ADMIN_PASSWORD__", creds.infisical_admin_password)
    content = content.replace("__GITHUB_DEPLOY_PUB__", github_deploy_pub)
    content = content.replace("__GIT_BRANCH__", creds.git_branch)

    # Base64-encoded files (verbatim — no placeholder substitution)
    b64_files = {
        # One-time setup scripts (cloud-init/scripts/)
        "__SETUP_INFISICAL_SH_B64__": setup_scripts_dir / "setup-infisical.sh",
        "__SETUP_FIREWALL_SH_B64__": setup_scripts_dir / "setup-firewall.sh",
        "__SETUP_DOCKER_SH_B64__": setup_scripts_dir / "setup-docker.sh",
        "__SETUP_DIRECTORIES_SH_B64__": setup_scripts_dir / "setup-directories.sh",
        "__SETUP_DEPLOY_KEYS_SH_B64__": setup_scripts_dir / "setup-deploy-keys.sh",
        "__SETUP_SWARM_SH_B64__": setup_scripts_dir / "setup-swarm.sh",
        # Persistent utilities (scripts/server/)
        "__DB_BACKUP_SH_B64__": util_scripts_dir / "db-backup.sh",
        "__WEBSITE_CLI_SH_B64__": util_scripts_dir / "website-cli.sh",
        # Env files + keys
        "__DB_ENV_B64__": rendered_dir / ".db.env",
        "__API_ENV_B64__": rendered_dir / ".api.env",
        "__LISTMONK_ENV_B64__": rendered_dir / ".listmonk.env",
        "__INFRA_ENV_B64__": rendered_dir / ".infra.env",
        "__GITHUB_DEPLOY_KEY_B64__": creds.github_deploy_key,
        "__TRANSIP_KEY_B64__": transip_key_path,
    }
    for placeholder, source_file in b64_files.items():
        if not source_file.is_file():
            raise FileNotFoundError(f"{source_file} not found (needed for {placeholder})")
        content = content.replace(placeholder, b64_encode_file(source_file))

    # Base64-encoded templates (scripts containing __PLACEHOLDER__ tokens)
    script_subs = {
        "__GHCR_TOKEN__": creds.ghcr_token,
        "__GHCR_USERNAME__": creds.ghcr_username,
        "__GIT_BRANCH__": creds.git_branch,
    }
    b64_templates = {
        "__SETUP_REPO_SH_B64__": setup_scripts_dir / "setup-repo.sh",
        "__SETUP_GHCR_SH_B64__": setup_scripts_dir / "setup-ghcr.sh",
    }
    for placeholder, source_file in b64_templates.items():
        if not source_file.is_file():
            raise FileNotFoundError(f"{source_file} not found (needed for {placeholder})")
        content = content.replace(placeholder, b64_encode_template(source_file, script_subs))

    output_path.write_text(content)

    print()
    print(f"==> Wrote {output_path}")
    print()
    print("Keys:")
    print(f"  {creds.website_key} (.pub)        -> injected for website user SSH login")
    print(f"  {creds.admin_key} (.pub)          -> injected for admin user SSH login")
    print(
        f"  {creds.github_deploy_key} (.pub)  -> injected as GitHub deploy key (read-only repo access)"
    )
    print()
    print("Env files (also embedded in cloud-config):")
    print(f"  {rendered_dir}/.db.env")
    print(f"  {rendered_dir}/.api.env")
    print(f"  {rendered_dir}/.listmonk.env")
    print(f"  {rendered_dir}/.infra.env")

    return output_path


def main() -> None:
    vps_dir = _vps_dir()
    print("==> Generating secrets...")
    creds = load_credentials(vps_dir)
    render(creds, vps_dir)


if __name__ == "__main__":
    main()
