"""VM backend abstraction — picks LXD (Linux/CI) or QEMU (macOS)."""

from __future__ import annotations

import platform
import shutil
from pathlib import Path
from typing import Protocol

from subprocess import CompletedProcess


class Vm(Protocol):
    """Interface that both LXD and QEMU backends implement."""

    @property
    def ssh_port(self) -> int:
        """SSH port to connect to (2222 for LXD, forwarded port for QEMU)."""
        ...

    def launch(self) -> None: ...
    def exec(self, cmd: list[str], *, check: bool = True, capture: bool = True) -> CompletedProcess: ...
    def wait_for_cloud_init(self, timeout: int = 1200) -> None: ...
    def get_cloud_init_errors(self) -> list[str]: ...
    def stream_log(self) -> None: ...
    def stop_log_stream(self) -> None: ...
    def get_ip(self) -> str: ...
    def collect_logs(self, dest: Path) -> None: ...
    def delete(self) -> None: ...


def create_vm(
    name: str,
    cloud_config_path: Path,
    *,
    cache_dir: Path | None = None,
) -> Vm:
    """Create the appropriate VM backend for the current platform.

    - Linux: LXD (native, fast)
    - macOS: QEMU (direct, no Multipass dependency)
    """
    if platform.system() == "Linux" and shutil.which("lxc"):
        from .lxd import LxdVm
        return LxdVm(
            name=name,
            image="images:debian/13/cloud",
            cloud_config_path=cloud_config_path,
        )

    if shutil.which("qemu-system-x86_64"):
        from .qemu import QemuVm
        return QemuVm(
            name=name,
            cloud_config_path=cloud_config_path,
            cache_dir=cache_dir or Path(".test-cache"),
        )

    raise RuntimeError(
        "No VM backend available. "
        "Linux: install LXD (snap install lxd). "
        "macOS: install QEMU (brew install qemu cdrtools)."
    )
