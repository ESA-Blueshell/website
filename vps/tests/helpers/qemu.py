"""QEMU VM backend for cloud-init testing on macOS.

Boots a Debian 13 cloud image with cloud-init user-data via a NoCloud
seed ISO. Uses SSH (port 2222 forwarded to host) for exec commands
after cloud-init reconfigures sshd.
"""

from __future__ import annotations

import json
import os
import platform
import shutil
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path


DEBIAN_IMAGE_URL = (
    "https://cloud.debian.org/images/cloud/trixie/latest/debian-13-generic-amd64.qcow2"
)
HOST_SSH_PORT = 55022
BOOT_TIMEOUT = 900  # 15 min for SSH to appear on port 2222


class QemuVm:
    """Manages a QEMU virtual machine for cloud-init testing.

    Provides the same interface as LxdVm so the tests work unchanged.
    Commands are executed via SSH after cloud-init configures sshd on port 2222.
    """

    def __init__(
        self,
        name: str,
        cloud_config_path: Path,
        *,
        cache_dir: Path,
        ssh_admin_key: Path | None = None,
        admin_password: str | None = None,
    ) -> None:
        self.name = name
        self.cloud_config_path = cloud_config_path
        self.cache_dir = cache_dir
        self.ssh_admin_key = ssh_admin_key or Path.home() / ".ssh" / "blueshell-admin"
        self.admin_password = admin_password or os.environ.get("ADMIN_PASSWORD", "")
        self._work_dir: Path | None = None
        self._qemu_pid: int | None = None
        self._log_thread: threading.Thread | None = None
        self._log_stop = threading.Event()

    @property
    def ssh_port(self) -> int:
        """SSH port — forwarded from host to guest 2222."""
        return HOST_SSH_PORT

    def launch(self) -> None:
        """Download image, create overlay + seed ISO, boot QEMU."""
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._work_dir = self.cache_dir / f"run-{os.getpid()}"
        self._work_dir.mkdir(parents=True, exist_ok=True)

        # Download Debian 13 cloud image (cached)
        image = self.cache_dir / "debian-13-generic-amd64.qcow2"
        if not image.is_file():
            print(f"Downloading Debian 13 cloud image (~300 MB)...")
            subprocess.run(
                ["curl", "-L", "--progress-bar", "-o", str(image) + ".tmp", DEBIAN_IMAGE_URL],
                check=True,
            )
            (image.parent / (image.name + ".tmp")).rename(image)

        # Create thin overlay
        overlay = self._work_dir / "disk.qcow2"
        subprocess.run(
            ["qemu-img", "create", "-q", "-f", "qcow2", "-F", "qcow2",
             "-b", str(image), str(overlay), "20G"],
            check=True,
        )

        # Create NoCloud seed ISO
        meta_data = self._work_dir / "meta-data"
        meta_data.write_text(f"instance-id: test-{os.getpid()}\nlocal-hostname: cloud-config-test\n")

        seed_iso = self._work_dir / "seed.iso"
        seed_tool = _find_seed_tool()

        if seed_tool == "cloud-localds":
            subprocess.run(
                ["cloud-localds", str(seed_iso), str(self.cloud_config_path), str(meta_data)],
                check=True,
            )
        else:
            user_data = self._work_dir / "user-data"
            shutil.copy2(self.cloud_config_path, user_data)
            subprocess.run(
                [seed_tool, "-output", str(seed_iso), "-volid", "cidata",
                 "-joliet", "-rock", str(user_data), str(meta_data)],
                check=True,
            )

        # Select accelerator
        accel = []
        system = platform.system()
        if system == "Darwin":
            accel = ["-accel", "hvf"]
        elif system == "Linux":
            accel = ["-enable-kvm"]

        # Boot VM
        console_log = self._work_dir / "console.log"
        print(f"Launching QEMU VM (host:{HOST_SSH_PORT} → guest:2222)...")
        with open(console_log, "w") as log_f:
            proc = subprocess.Popen(
                [
                    "qemu-system-x86_64",
                    *accel,
                    "-m", "2048",
                    "-cpu", "host",
                    "-smp", "2",
                    "-nographic",
                    "-drive", f"file={overlay},format=qcow2,if=virtio",
                    "-drive", f"file={seed_iso},format=raw,media=cdrom",
                    "-device", "virtio-net-pci,netdev=net0",
                    "-netdev", f"user,id=net0,hostfwd=tcp::{HOST_SSH_PORT}-:2222",
                ],
                stdout=log_f,
                stderr=subprocess.STDOUT,
            )
        self._qemu_pid = proc.pid
        print(f"QEMU started (PID {proc.pid}).")

        # Wait for SSH on port 2222
        print(f"Waiting for SSH on port {HOST_SSH_PORT} (up to {BOOT_TIMEOUT}s)...")
        deadline = time.time() + BOOT_TIMEOUT
        ssh_opts = ["-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=5",
                    "-o", "BatchMode=yes", "-o", "LogLevel=ERROR"]
        while time.time() < deadline:
            result = subprocess.run(
                ["ssh", *ssh_opts, "-p", str(HOST_SSH_PORT), "-i", str(self.ssh_admin_key),
                 "admin@127.0.0.1", "true"],
                capture_output=True,
            )
            if result.returncode == 0:
                print(f"SSH is up on port {HOST_SSH_PORT}.")
                return
            time.sleep(15)

        raise TimeoutError(f"SSH not available on port {HOST_SSH_PORT} after {BOOT_TIMEOUT}s")

    def exec(
        self,
        cmd: list[str],
        *,
        check: bool = True,
        capture: bool = True,
    ) -> subprocess.CompletedProcess:
        """Execute a command inside the VM via SSH as root (using sudo)."""
        remote_cmd = " ".join(_shell_escape(c) for c in cmd)
        # Pipe admin password to sudo -S for root execution
        full_cmd = f"printf '%s\\n' '{self.admin_password}' | sudo -S {remote_cmd} 2>/dev/null"

        ssh_opts = ["-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=5",
                    "-o", "BatchMode=yes", "-o", "LogLevel=ERROR"]
        ssh_cmd = [
            "ssh", *ssh_opts, "-p", str(HOST_SSH_PORT),
            "-i", str(self.ssh_admin_key), "admin@127.0.0.1", full_cmd,
        ]
        if capture:
            return subprocess.run(ssh_cmd, check=check, capture_output=True, text=True)
        return subprocess.run(ssh_cmd, check=check, text=True)

    def push_file(self, local_path: Path, remote_path: str) -> None:
        """Push a local file into the VM via scp."""
        ssh_opts = ["-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=5",
                    "-o", "BatchMode=yes", "-o", "LogLevel=ERROR"]
        subprocess.run(
            ["scp", *ssh_opts, "-P", str(HOST_SSH_PORT), "-i", str(self.ssh_admin_key),
             str(local_path), f"admin@127.0.0.1:{remote_path}"],
            check=True,
        )

    def wait_for_cloud_init(self, timeout: int = 1200) -> None:
        """Wait for cloud-init to complete by polling for result.json."""
        print(f"Waiting for cloud-init to complete (up to {timeout}s)...")
        deadline = time.time() + timeout
        while time.time() < deadline:
            result = self.exec(["test", "-f", "/run/cloud-init/result.json"], check=False)
            if result.returncode == 0:
                print("cloud-init completed.")
                return
            time.sleep(15)
        raise TimeoutError(f"cloud-init did not complete within {timeout}s")

    def get_cloud_init_errors(self) -> list[str]:
        """Parse /run/cloud-init/result.json for errors."""
        result = self.exec(["cat", "/run/cloud-init/result.json"], check=False)
        if result.returncode != 0:
            return [f"Could not read result.json: {result.stderr.strip()}"]
        try:
            data = json.loads(result.stdout)
            return data.get("v1", {}).get("errors", [])
        except json.JSONDecodeError as e:
            return [f"Invalid JSON in result.json: {e}"]

    def stream_log(self) -> None:
        """Stream console.log to stdout in a background thread."""
        console_log = self._work_dir / "console.log" if self._work_dir else None
        if not console_log or not console_log.exists():
            return

        def _stream() -> None:
            with open(console_log) as f:
                while not self._log_stop.is_set():
                    line = f.readline()
                    if line:
                        sys.stdout.write(line)
                        sys.stdout.flush()
                    else:
                        time.sleep(0.5)

        self._log_thread = threading.Thread(target=_stream, daemon=True, name="console-log")
        self._log_thread.start()

    def stop_log_stream(self) -> None:
        """Stop the background log stream."""
        self._log_stop.set()

    def get_ip(self) -> str:
        """Return localhost since QEMU uses port forwarding."""
        return "127.0.0.1"

    def collect_logs(self, dest: Path) -> None:
        """Pull cloud-init logs from the VM via SSH."""
        dest.mkdir(parents=True, exist_ok=True)

        # Copy local console log
        if self._work_dir:
            console = self._work_dir / "console.log"
            if console.is_file():
                shutil.copy2(console, dest / "console.log")

        # Pull VM logs via SSH
        log_files = [
            "/var/log/cloud-init.log",
            "/var/log/cloud-init-output.log",
            "/run/cloud-init/result.json",
        ]
        for remote_path in log_files:
            filename = Path(remote_path).name
            result = self.exec(["cat", remote_path], check=False)
            if result.returncode == 0 and result.stdout.strip():
                (dest / filename).write_text(result.stdout)

        print(f"Logs saved to {dest}/")

    def delete(self) -> None:
        """Kill QEMU and clean up work directory."""
        self.stop_log_stream()
        if self._qemu_pid:
            print(f"Stopping QEMU (PID {self._qemu_pid})...")
            try:
                os.kill(self._qemu_pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        if self._work_dir and self._work_dir.exists():
            shutil.rmtree(self._work_dir, ignore_errors=True)
        print("QEMU cleaned up.")


def _find_seed_tool() -> str:
    """Find a tool to create the NoCloud seed ISO."""
    for tool in ("cloud-localds", "genisoimage", "mkisofs"):
        if shutil.which(tool):
            return tool
    raise RuntimeError(
        "No ISO tool found. "
        "macOS: brew install cdrtools. "
        "Linux: apt install cloud-image-utils."
    )


def _shell_escape(s: str) -> str:
    """Escape a string for use in a remote shell command."""
    if not s:
        return "''"
    # If it's safe, return as-is
    safe = all(c.isalnum() or c in "-_./=:+" for c in s)
    if safe:
        return s
    # Otherwise single-quote it (escaping any existing single quotes)
    return "'" + s.replace("'", "'\\''") + "'"
