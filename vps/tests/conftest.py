"""Pytest fixtures for cloud-init testing."""

from __future__ import annotations

from collections.abc import Generator
from pathlib import Path
from typing import Any

import pytest

from lib.credentials import Credentials, load_credentials
from lib.render import render
from tests.helpers.vm import Vm, create_vm


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
def vm(rendered_config: Path, credentials: Credentials) -> Generator[Any, None, None]:
    """Launch a VM with cloud-init, wait for completion, then clean up.

    Picks the right backend automatically:
    - Linux: LXD (native, fast, used in CI)
    - macOS: QEMU (direct, no Multipass dependency)

    Session-scoped: the VM boots once and all tests run against it.
    """
    instance = create_vm(
        name="cloud-config-test",
        cloud_config_path=rendered_config,
        cache_dir=VPS_DIR / ".test-cache",
    )

    # QEMU backend needs credentials for SSH sudo
    if hasattr(instance, "admin_password") and not instance.admin_password:
        instance.admin_password = credentials.admin_password

    instance.launch()
    instance.stream_log()
    instance.wait_for_cloud_init(timeout=1200)
    instance.stop_log_stream()

    yield instance

    log_dir = VPS_DIR / "vm-logs"
    instance.collect_logs(log_dir)
    instance.delete()
