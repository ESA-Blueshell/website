"""VM backend — LXD only (Linux/CI)."""

from __future__ import annotations

from pathlib import Path

from .lxd import LxdVm


def create_vm(name: str, cloud_config_path: Path) -> LxdVm:
    return LxdVm(
        name=name,
        image="images:debian/13/cloud",
        cloud_config_path=cloud_config_path,
    )
