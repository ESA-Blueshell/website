"""Phase 2: Full container tests — validates cloud-init provisioning via LXD.

Requires a running LXD container (provided by the ``vm`` session fixture).
Most checks use ``lxc exec`` (runs as root, no SSH needed).
SSH is only used when testing SSH functionality itself.

Run with: pytest tests/test_cloud_init.py -v -s
The -s flag enables live log streaming during VM boot.
"""

from __future__ import annotations

import time
from pathlib import Path

import pytest

from lib.credentials import Credentials
from tests.helpers.lxd import LxdVm
from tests.helpers.ssh import ssh_key_login, ssh_password_login


pytestmark = pytest.mark.vm


# ── Cloud-init completion ────────────────────────────────────────────────────


class TestCloudInitCompletion:
    def test_no_stage_errors(self, vm: LxdVm) -> None:
        errors = vm.get_cloud_init_errors()
        assert errors == [], f"cloud-init stage errors: {errors}"

    def test_schema_valid(self, vm: LxdVm, rendered_config: Path) -> None:
        """cloud-init schema --config-file must exit 0 with no warnings."""
        remote_path = "/tmp/cloud-config-validate.yaml"
        vm.push_file(rendered_config, remote_path)
        result = vm.exec(["cloud-init", "schema", "--config-file", remote_path], check=False)
        combined = result.stdout + result.stderr
        assert result.returncode == 0, (
            f"cloud-init schema validation failed (exit {result.returncode}):\n{combined}"
        )
        warning_lines = [
            line for line in combined.splitlines()
            if "warning" in line.lower() or "failed schema validation" in line.lower()
        ]
        assert warning_lines == [], (
            "cloud-init schema produced warnings:\n" + "\n".join(warning_lines)
        )

    def test_no_schema_warnings_in_boot_log(self, vm: LxdVm) -> None:
        """cloud-init.log must not contain schema validation warnings from boot."""
        result = vm.exec(["cat", "/var/log/cloud-init.log"], check=False)
        if result.returncode != 0:
            pytest.skip("cloud-init.log not readable")
        schema_warning_lines = [
            line for line in result.stdout.splitlines()
            if "schema" in line.lower() and (
                "warning" in line.lower() or "failed" in line.lower()
            )
        ]
        assert schema_warning_lines == [], (
            "cloud-init.log contains schema warnings from boot:\n"
            + "\n".join(schema_warning_lines)
        )


# ── SSH key login ────────────────────────────────────────────────────────────


class TestSSHKeyLogin:
    def test_admin_key_login(self, vm: LxdVm, credentials: Credentials) -> None:
        ip = vm.get_ip()
        result = ssh_key_login(ip, vm.ssh_port, "admin", str(credentials.admin_key), "whoami")
        assert result == "admin"

    def test_website_key_login(self, vm: LxdVm, credentials: Credentials) -> None:
        ip = vm.get_ip()
        result = ssh_key_login(ip, vm.ssh_port, "website", str(credentials.website_key), "whoami")
        assert result == "website"

    def test_website_in_website_group(self, vm: LxdVm) -> None:
        result = vm.exec(["id", "-nG", "website"])
        groups = result.stdout.strip().split()
        assert "website" in groups


# ── SSH password login ───────────────────────────────────────────────────────


class TestSSHPasswordLogin:
    """Temporarily enables password auth, tests password login, then restores.

    Uses lxc exec (not SSH) to toggle the sshd config — avoids the
    chicken-and-egg problem of needing SSH to configure SSH.
    """

    @pytest.fixture(autouse=True, scope="class")
    def _enable_password_auth(self, vm: LxdVm) -> None:
        """Enable password auth for the duration of this test class."""
        dropin = (
            "AuthenticationMethods any\n"
            "KbdInteractiveAuthentication yes\n"
            "PasswordAuthentication yes\n"
            "PermitRootLogin yes\n"
        )
        vm.exec(["bash", "-c", f"printf '{dropin}' > /etc/ssh/sshd_config.d/00-test-pwauth.conf"])
        vm.exec(["systemctl", "reload", "ssh"])
        time.sleep(2)

        yield

        vm.exec(["rm", "-f", "/etc/ssh/sshd_config.d/00-test-pwauth.conf"])
        vm.exec(["systemctl", "reload", "ssh"])
        time.sleep(1)

    def test_admin_password_login(self, vm: LxdVm, credentials: Credentials) -> None:
        ip = vm.get_ip()
        result = ssh_password_login(ip, vm.ssh_port, "admin", credentials.admin_password, "whoami")
        assert result == "admin"

    def test_website_password_login(self, vm: LxdVm, credentials: Credentials) -> None:
        ip = vm.get_ip()
        result = ssh_password_login(ip, vm.ssh_port, "website", credentials.website_password, "whoami")
        assert result == "website"

    def test_root_password_login(self, vm: LxdVm, credentials: Credentials) -> None:
        ip = vm.get_ip()
        result = ssh_password_login(ip, vm.ssh_port, "root", credentials.root_password, "whoami")
        assert result == "root"


# ── Security checks ──────────────────────────────────────────────────────────


class TestSecurity:
    def test_password_auth_disabled(self, vm: LxdVm) -> None:
        result = vm.exec(
            ["grep", "-Eriq", "^PasswordAuthentication\\s+no",
             "/etc/ssh/sshd_config", "/etc/ssh/sshd_config.d/"],
            check=False,
        )
        assert result.returncode == 0, "PasswordAuthentication is not set to 'no'"

    def test_sshd_port_2222(self, vm: LxdVm) -> None:
        result = vm.exec(
            ["grep", "-Eriq", "^Port\\s+2222",
             "/etc/ssh/sshd_config", "/etc/ssh/sshd_config.d/"],
            check=False,
        )
        assert result.returncode == 0, "Port is not set to 2222"

    def test_listening_on_2222(self, vm: LxdVm) -> None:
        result = vm.exec(["ss", "-tlnp"])
        assert ":2222" in result.stdout, "sshd is not listening on port 2222"

    def test_not_listening_on_22(self, vm: LxdVm) -> None:
        result = vm.exec(["ss", "-tlnp"])
        for line in result.stdout.splitlines():
            # Match :22 but not :2222
            if ":22 " in line or ":22\t" in line or line.rstrip().endswith(":22"):
                pytest.fail("sshd is still listening on port 22")

    def test_ufw_active(self, vm: LxdVm) -> None:
        result = vm.exec(["ufw", "status"])
        assert "Status: active" in result.stdout, "UFW is not active"

    def test_ufw_allows_2222(self, vm: LxdVm) -> None:
        result = vm.exec(["ufw", "status"])
        lines = result.stdout.splitlines()
        assert any(line.startswith("2222") for line in lines), (
            "Port 2222 not found in ufw status"
        )

    def test_ufw_blocks_22(self, vm: LxdVm) -> None:
        result = vm.exec(["ufw", "status"])
        lines = result.stdout.splitlines()
        # Check no line starts with "22" that isn't "2222"
        for line in lines:
            if line.startswith("22") and not line.startswith("2222"):
                pytest.fail(f"Port 22 is explicitly allowed in UFW: {line}")


# ── Provisioning checks ─────────────────────────────────────────────────────


class TestProvisioning:
    def test_docker_running(self, vm: LxdVm) -> None:
        result = vm.exec(["docker", "info"], check=False)
        assert result.returncode == 0, "Docker daemon is not running"

    def test_admin_in_docker_group(self, vm: LxdVm) -> None:
        result = vm.exec(["id", "-nG", "admin"])
        assert "docker" in result.stdout.strip().split()

    def test_website_in_docker_group(self, vm: LxdVm) -> None:
        result = vm.exec(["id", "-nG", "website"])
        assert "docker" in result.stdout.strip().split()

    def test_git_repo_cloned(self, vm: LxdVm) -> None:
        result = vm.exec(["test", "-d", "/src/website/.git"], check=False)
        assert result.returncode == 0, "/src/website/.git not found — clone failed"

    @pytest.mark.parametrize(
        "dir_path",
        [
            "/src/backups/db",
            "/src/backups/env",
            "/src/backups/storage",
            "/src/backups/mailserver/mail-data",
            "/src/backups/mailserver/config",
        ],
    )
    def test_backup_directory_exists(self, vm: LxdVm, dir_path: str) -> None:
        result = vm.exec(["test", "-d", dir_path], check=False)
        assert result.returncode == 0, f"Directory {dir_path} does not exist"

    def test_website_deploy_service_enabled(self, vm: LxdVm) -> None:
        result = vm.exec(["systemctl", "is-enabled", "website-deploy.service"])
        assert result.stdout.strip() == "enabled"
