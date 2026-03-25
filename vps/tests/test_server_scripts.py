"""Unit tests for setup and server scripts.

Tests run without a VPS by mocking external commands (docker, git, ufw, etc.)
via PATH-prepend: tiny mock scripts log their invocations to a file, then the
test reads the log to verify correct behavior.

Run with: pytest tests/test_server_scripts.py -v
"""

from __future__ import annotations

import os
import stat
import subprocess
import textwrap
from pathlib import Path

import pytest

VPS_DIR = Path(__file__).resolve().parent.parent
SETUP_DIR = VPS_DIR / "cloud-init" / "scripts"  # one-time setup scripts
UTIL_DIR = VPS_DIR / "scripts" / "server"        # persistent utilities


# ── Helpers ─────────────────────────────────────────────────────────────────


def _create_mock(mock_dir: Path, name: str, *, exit_code: int = 0, stdout: str = "") -> Path:
    """Create a mock script that logs its invocation and exits with a given code."""
    mock = mock_dir / name
    mock.write_text(
        f"#!/usr/bin/env bash\n"
        f'echo "{name} $*" >> "${{MOCK_LOG}}"\n'
        f"{f'echo {stdout!r}' if stdout else ''}\n"
        f"exit {exit_code}\n"
    )
    mock.chmod(mock.stat().st_mode | stat.S_IEXEC)
    return mock


def _run_script(
    script: Path,
    mock_dir: Path,
    log_file: Path,
    *,
    env_extra: dict[str, str] | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """Run a server script with mocked PATH."""
    env = {
        **os.environ,
        "PATH": f"{mock_dir}:{os.environ['PATH']}",
        "MOCK_LOG": str(log_file),
    }
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        ["bash", str(script)],
        capture_output=True,
        text=True,
        env=env,
        check=check,
    )


# ── setup-swarm.sh ─────────────────────────────────────────────────────────


class TestSetupSwarm:
    def test_creates_overlay_networks(self, tmp_path: Path) -> None:
        mock_dir = tmp_path / "mocks"
        mock_dir.mkdir()
        log = tmp_path / "log"
        log.touch()

        _create_mock(mock_dir, "docker")
        _run_script(SETUP_DIR / "setup-swarm.sh", mock_dir, log)

        calls = log.read_text().strip().splitlines()
        assert any("swarm init" in c for c in calls)
        assert any("traefik-public" in c for c in calls)
        assert any("monitoring" in c for c in calls)

    def test_swarm_init_advertise_localhost(self, tmp_path: Path) -> None:
        mock_dir = tmp_path / "mocks"
        mock_dir.mkdir()
        log = tmp_path / "log"
        log.touch()

        _create_mock(mock_dir, "docker")
        _run_script(SETUP_DIR / "setup-swarm.sh", mock_dir, log)

        calls = log.read_text()
        assert "--advertise-addr 127.0.0.1" in calls


# ── setup-ghcr.sh ──────────────────────────────────────────────────────────


class TestSetupGhcr:
    def test_calls_docker_login(self, tmp_path: Path) -> None:
        mock_dir = tmp_path / "mocks"
        mock_dir.mkdir()
        log = tmp_path / "log"
        log.touch()

        _create_mock(mock_dir, "docker")
        _create_mock(mock_dir, "su", stdout="")

        # The script has __GHCR_TOKEN__ and __GHCR_USERNAME__ placeholders.
        # In production these are substituted by render.py before embedding.
        # For testing, substitute them ourselves.
        script = SETUP_DIR / "setup-ghcr.sh"
        patched = tmp_path / "setup-ghcr.sh"
        content = script.read_text()
        content = content.replace("__GHCR_TOKEN__", "test-token")
        content = content.replace("__GHCR_USERNAME__", "test-user")
        patched.write_text(content)

        # Since the script calls `su -m -s /bin/bash website -c "..."`,
        # we mock `su` to just execute the command argument directly
        su_mock = mock_dir / "su"
        su_mock.write_text(
            '#!/usr/bin/env bash\n'
            'echo "su $*" >> "${MOCK_LOG}"\n'
            '# Extract the command from -c argument and run it\n'
            'shift; shift; shift; shift; shift  # skip -m -s /bin/bash website -c\n'
            'eval "$1"\n'
        )
        su_mock.chmod(su_mock.stat().st_mode | stat.S_IEXEC)

        _run_script(patched, mock_dir, log)
        calls = log.read_text()
        assert "docker login ghcr.io" in calls


# ── setup-deploy-keys.sh ──────────────────────────────────────────────────


class TestSetupDeployKeys:
    def test_script_references_deploy_key_path(self) -> None:
        """Verify the script reads from /etc/deploy-keys/github-deploy-key."""
        content = (SETUP_DIR / "setup-deploy-keys.sh").read_text()
        assert '/etc/deploy-keys/github-deploy-key' in content

    def test_script_configures_github_over_443(self) -> None:
        """Verify SSH config routes github.com through ssh.github.com:443."""
        content = (SETUP_DIR / "setup-deploy-keys.sh").read_text()
        assert "HostName ssh.github.com" in content
        assert "Port 443" in content

    def test_script_handles_both_users(self) -> None:
        """Verify both website and admin users get the deploy key."""
        content = (SETUP_DIR / "setup-deploy-keys.sh").read_text()
        assert "for user in website admin" in content


# ── setup-repo.sh ──────────────────────────────────────────────────────────


class TestSetupRepo:
    def test_script_tries_https_first(self) -> None:
        """Verify HTTPS clone is attempted before SSH fallback."""
        content = (SETUP_DIR / "setup-repo.sh").read_text()
        # The script defines clone_https() and clone_ssh() functions
        # and calls: if ! clone_https; then clone_ssh; fi
        assert "clone_https" in content
        assert "clone_ssh" in content
        # HTTPS is attempted first (the if ! clone_https pattern)
        assert "if ! clone_https" in content

    def test_script_restores_ssh_origin(self) -> None:
        """After cloning, origin must be set back to SSH URL."""
        content = (SETUP_DIR / "setup-repo.sh").read_text()
        assert "remote set-url origin" in content
        assert "git@github.com:" in content

    def test_script_skips_if_already_cloned(self) -> None:
        """Script should exit early if .git directory already exists."""
        content = (SETUP_DIR / "setup-repo.sh").read_text()
        assert '-d "${REPO_DIR}/.git"' in content

    def test_script_contains_placeholders(self) -> None:
        """Unrendered script must contain placeholders for render.py to substitute."""
        content = (SETUP_DIR / "setup-repo.sh").read_text()
        assert "__GHCR_TOKEN__" in content
        assert "__GIT_BRANCH__" in content


# ── setup-firewall.sh ─────────────────────────────────────────────────────


class TestSetupFirewall:
    def test_allows_correct_ports(self) -> None:
        content = (SETUP_DIR / "setup-firewall.sh").read_text()
        assert "ufw allow 2222/tcp" in content
        assert "ufw allow 80/tcp" in content
        assert "ufw allow 443/tcp" in content

    def test_default_deny_incoming(self) -> None:
        content = (SETUP_DIR / "setup-firewall.sh").read_text()
        assert "ufw default deny incoming" in content

    def test_default_allow_outgoing(self) -> None:
        content = (SETUP_DIR / "setup-firewall.sh").read_text()
        assert "ufw default allow outgoing" in content

    def test_force_enables_ufw(self) -> None:
        content = (SETUP_DIR / "setup-firewall.sh").read_text()
        assert "ufw --force enable" in content

    def test_executes_correctly(self, tmp_path: Path) -> None:
        """Run with mocked ufw and verify all rules are applied."""
        mock_dir = tmp_path / "mocks"
        mock_dir.mkdir()
        log = tmp_path / "log"
        log.touch()
        _create_mock(mock_dir, "ufw")
        _run_script(SETUP_DIR / "setup-firewall.sh", mock_dir, log)

        calls = log.read_text().strip().splitlines()
        assert len(calls) == 6  # 2 defaults + 3 allows + 1 enable
        assert "ufw default deny incoming" in calls[0]
        assert "ufw default allow outgoing" in calls[1]
        assert "ufw --force enable" in calls[-1]


# ── setup-directories.sh ──────────────────────────────────────────────────


class TestSetupDirectories:
    def test_creates_website_root(self) -> None:
        content = (SETUP_DIR / "setup-directories.sh").read_text()
        assert "/src/website" in content

    def test_creates_backup_structure(self) -> None:
        content = (SETUP_DIR / "setup-directories.sh").read_text()
        assert "/src/backups/db" in content
        assert "/src/backups/env" in content
        assert "/src/backups/storage" in content
        assert "/src/backups/mailserver/mail-data" in content
        assert "/src/backups/mailserver/config" in content

    def test_backup_dirs_owned_by_root_backup(self) -> None:
        content = (SETUP_DIR / "setup-directories.sh").read_text()
        assert "root:backup" in content

    def test_backup_dirs_use_sgid(self) -> None:
        content = (SETUP_DIR / "setup-directories.sh").read_text()
        assert "2770" in content

    def test_creates_backup_log(self) -> None:
        content = (SETUP_DIR / "setup-directories.sh").read_text()
        assert "/var/log/db-backup.log" in content


# ── setup-docker.sh ───────────────────────────────────────────────────────


class TestSetupDocker:
    def test_enables_docker_service(self) -> None:
        content = (SETUP_DIR / "setup-docker.sh").read_text()
        assert "systemctl enable --now docker.service" in content

    def test_daemon_reload_before_enable(self) -> None:
        content = (SETUP_DIR / "setup-docker.sh").read_text()
        # daemon-reload must come before enable
        reload_pos = content.index("systemctl daemon-reload")
        enable_pos = content.index("systemctl enable --now docker.service")
        assert reload_pos < enable_pos

    def test_proxy_config_writes_drop_in(self) -> None:
        content = (SETUP_DIR / "setup-docker.sh").read_text()
        assert "/etc/systemd/system/docker.service.d/proxy.conf" in content

    def test_executes_without_proxy(self, tmp_path: Path) -> None:
        """Run with mocked systemctl and no proxy — should not create proxy.conf."""
        mock_dir = tmp_path / "mocks"
        mock_dir.mkdir()
        log = tmp_path / "log"
        log.touch()
        _create_mock(mock_dir, "systemctl")
        _create_mock(mock_dir, "install")

        # Ensure no proxy vars are set
        env = {
            "http_proxy": "", "https_proxy": "", "HTTP_PROXY": "",
            "HTTPS_PROXY": "", "no_proxy": "", "NO_PROXY": "",
        }
        _run_script(SETUP_DIR / "setup-docker.sh", mock_dir, log, env_extra=env)

        calls = log.read_text()
        assert "systemctl daemon-reload" in calls
        assert "systemctl enable --now docker.service" in calls


# ── setup-infisical.sh ────────────────────────────────────────────────────


class TestSetupInfisical:
    def test_downloads_from_cloudsmith(self) -> None:
        content = (SETUP_DIR / "setup-infisical.sh").read_text()
        assert "dl.cloudsmith.io" in content

    def test_continues_on_failure(self) -> None:
        """Script must not abort the entire provisioning if install fails."""
        content = (SETUP_DIR / "setup-infisical.sh").read_text()
        assert "WARNING" in content
        assert "continuing without it" in content
