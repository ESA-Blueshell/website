"""Pre-flight cloud-config schema validation — no VM required.

Validates the rendered cloud-config against the cloud-init JSON schema using
the local ``cloud-init schema`` command.  Tests are skipped when cloud-init is
not installed (e.g. on macOS dev machines); they will always run in CI where
the LXD host runs Ubuntu.

Run alone with:
    pytest tests/test_schema.py -v
"""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest


pytestmark = pytest.mark.schema

_CLOUD_INIT = shutil.which("cloud-init")


def _skip_if_unavailable() -> None:
    if not _CLOUD_INIT:
        pytest.skip("cloud-init not installed — schema check requires Linux/CI")


class TestCloudConfigSchema:
    """Schema validation without a running VM."""

    def test_schema_valid(self, rendered_config: Path) -> None:
        """rendered config must pass cloud-init schema validation with no errors."""
        _skip_if_unavailable()
        result = subprocess.run(
            [_CLOUD_INIT, "schema", "--config-file", str(rendered_config)],
            capture_output=True,
            text=True,
        )
        combined = result.stdout + result.stderr
        assert result.returncode == 0, (
            f"cloud-init schema validation failed (exit {result.returncode}):\n{combined}"
        )

    def test_schema_no_warnings(self, rendered_config: Path) -> None:
        """rendered config must not produce any schema warnings."""
        _skip_if_unavailable()
        result = subprocess.run(
            [_CLOUD_INIT, "schema", "--config-file", str(rendered_config)],
            capture_output=True,
            text=True,
        )
        combined = result.stdout + result.stderr
        # These two warnings are runner infrastructure artefacts, not schema issues:
        # - cloud-init can't read its own pickle state when run as non-root
        # - schema check without prior cloud-init run has no datasource state
        _BENIGN = (
            "failed loading pickle",       # non-root permission denied on obj.pkl
            "datasource not detected",     # no cloud-init state on runner
        )
        warning_lines = [
            line for line in combined.splitlines()
            if ("warning" in line.lower() or "failed schema validation" in line.lower())
            and not any(b in line for b in _BENIGN)
        ]
        assert warning_lines == [], (
            "cloud-init schema produced warnings:\n" + "\n".join(warning_lines)
        )

    def test_config_is_valid_yaml(self, rendered_config: Path) -> None:
        """rendered config must be parseable YAML (catches template substitution gaps)."""
        import yaml

        try:
            yaml.safe_load(rendered_config.read_text())
        except yaml.YAMLError as exc:
            pytest.fail(f"cloud-config.yaml is not valid YAML: {exc}")

    def test_no_unsubstituted_placeholders(self, rendered_config: Path) -> None:
        """rendered config must not contain any __PLACEHOLDER__ tokens."""
        content = rendered_config.read_text()
        import re

        placeholders = re.findall(r"__[A-Z0-9_]+__", content)
        assert placeholders == [], (
            f"Unsubstituted placeholders found in rendered config: {placeholders}"
        )


class TestScriptEmbedding:
    """Verify all scripts are embedded in the cloud-config."""

    VPS_DIR = Path(__file__).resolve().parent.parent
    SETUP_DIR = VPS_DIR / "cloud-init" / "scripts"
    UTIL_DIR = VPS_DIR / "scripts" / "server"

    # Map from source filename to deployed path (when they differ)
    _DEPLOY_NAME_MAP = {
        "website-cli.sh": "website",
        "db-backup.sh": "db-backup",
    }

    def test_all_scripts_are_embedded(self, rendered_config: Path) -> None:
        """Every .sh in cloud-init/scripts/ and scripts/server/ must have a write_files entry."""
        import yaml

        config = yaml.safe_load(rendered_config.read_text())
        embedded_paths = {entry["path"] for entry in config.get("write_files", [])}

        all_scripts = sorted(self.SETUP_DIR.glob("*.sh")) + sorted(self.UTIL_DIR.glob("*.sh"))
        assert all_scripts, "No .sh files found"

        missing = []
        for script in all_scripts:
            deploy_name = self._DEPLOY_NAME_MAP.get(script.name, script.name)
            expected = f"/usr/local/bin/{deploy_name}"
            if expected not in embedded_paths:
                missing.append(f"{script.name} -> {expected}")

        assert missing == [], (
            "Scripts not found in write_files:\n" + "\n".join(missing)
        )

    def test_runcmd_calls_all_setup_scripts(self, rendered_config: Path) -> None:
        """Each setup script should be called directly from runcmd."""
        import yaml

        config = yaml.safe_load(rendered_config.read_text())
        runcmd = config.get("runcmd", [])
        runcmd_text = "\n".join(str(cmd) for cmd in runcmd)

        expected_calls = [
            "/usr/local/bin/setup-infisical.sh",
            "/usr/local/bin/setup-firewall.sh",
            "/usr/local/bin/setup-docker.sh",
            "/usr/local/bin/setup-directories.sh",
            "/usr/local/bin/setup-deploy-keys.sh",
            "/usr/local/bin/setup-swarm.sh",
            "/usr/local/bin/setup-repo.sh",
            "/usr/local/bin/setup-ghcr.sh",
        ]
        missing = [call for call in expected_calls if call not in runcmd_text]
        assert missing == [], (
            "Setup scripts not called from runcmd:\n" + "\n".join(missing)
        )