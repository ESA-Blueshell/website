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
        warning_lines = [
            line for line in combined.splitlines()
            if "warning" in line.lower() or "failed schema validation" in line.lower()
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