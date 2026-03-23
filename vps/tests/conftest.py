"""Pytest fixtures for cloud-init testing."""

from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

import pytest

from lib.credentials import Credentials, load_credentials
from lib.render import render
from tests.helpers.lxd import LxdVm
from tests.helpers.vm import create_vm


VPS_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture(scope="session")
def credentials() -> Credentials:
    """Load credentials from vps/.env or environment variables."""
    return load_credentials(VPS_DIR)


@pytest.fixture(scope="session")
def rendered_config(credentials: Credentials) -> Path:
    """Render the cloud-config template. Returns path to cloud-config.yaml."""
    return render(credentials, VPS_DIR)


@pytest.fixture(scope="session")
def vm(rendered_config: Path, credentials: Credentials) -> Generator[LxdVm, None, None]:
    """Launch an LXD VM with cloud-init, wait for completion, then clean up.

    Session-scoped: the VM boots once and all tests run against it.
    """
    instance = create_vm(name="cloud-config-test", cloud_config_path=rendered_config)

    instance.launch()
    instance.stream_log()
    instance.wait_for_cloud_init(timeout=1200)
    instance.stop_log_stream()

    yield instance

    instance.collect_logs(VPS_DIR / "vm-logs")
    instance.delete()
