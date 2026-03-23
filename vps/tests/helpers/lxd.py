"""LXD VM management for cloud-init testing."""

from __future__ import annotations

import json
import subprocess
import sys
import threading
import time
from pathlib import Path


class LxdVm:
    """Manages an LXD virtual machine for cloud-init testing.

    Uses the ``lxc`` CLI via subprocess so it works identically with local
    LXD (Linux/CI) and remote LXD (macOS via Multipass).
    """

    def __init__(
        self,
        name: str,
        image: str,
        cloud_config_path: Path,
        *,
        remote: str | None = None,
        cpus: int = 2,
        memory: str = "2GiB",
    ) -> None:
        self.name = name
        self.image = image
        self.cloud_config_path = cloud_config_path
        self.remote = remote
        self.cpus = cpus
        self.memory = memory
        self._log_thread: threading.Thread | None = None
        self._log_proc: subprocess.Popen | None = None

    @property
    def ssh_port(self) -> int:
        """SSH port — 2222 inside the LXD VM, accessible directly via VM IP."""
        return 2222

    @property
    def _instance(self) -> str:
        """Instance name, prefixed with remote if set."""
        if self.remote:
            return f"{self.remote}:{self.name}"
        return self.name

    def _lxc(self, *args: str, check: bool = True, **kwargs) -> subprocess.CompletedProcess:
        """Run an lxc command."""
        cmd = ["lxc", *args]
        return subprocess.run(cmd, check=check, capture_output=True, text=True, **kwargs)

    def launch(self) -> None:
        """Launch the VM with cloud-init user-data."""
        cloud_config = self.cloud_config_path.read_text()

        # Delete any leftover instance from a previous run
        self._lxc("delete", "--force", self._instance, check=False)

        print(f"Launching LXD VM '{self.name}' ({self.image}, {self.cpus} CPUs, {self.memory})...")
        self._lxc(
            "launch",
            self.image,
            self._instance,
            "--vm",
            "-c", f"limits.cpu={self.cpus}",
            "-c", f"limits.memory={self.memory}",
            "-c", f"user.user-data={cloud_config}",
        )
        print(f"VM '{self.name}' launched.")

    def exec(
        self,
        cmd: list[str],
        *,
        check: bool = True,
        capture: bool = True,
    ) -> subprocess.CompletedProcess:
        """Execute a command inside the VM via lxc exec."""
        full_cmd = ["lxc", "exec", self._instance, "--"]
        full_cmd.extend(cmd)
        if capture:
            return subprocess.run(full_cmd, check=check, capture_output=True, text=True)
        return subprocess.run(full_cmd, check=check, text=True)

    def wait_for_agent(self, timeout: int = 180) -> None:
        """Wait for the LXD agent inside the VM to become ready."""
        print(f"Waiting for LXD agent (up to {timeout}s)...")
        deadline = time.time() + timeout
        while time.time() < deadline:
            result = self.exec(["true"], check=False)
            if result.returncode == 0:
                print("LXD agent is ready.")
                return
            time.sleep(3)
        raise TimeoutError(f"LXD agent not ready after {timeout}s")

    def wait_for_cloud_init(self, timeout: int = 1200) -> None:
        """Wait for cloud-init to complete by polling for result.json."""
        print(f"Waiting for cloud-init to complete (up to {timeout}s)...")
        deadline = time.time() + timeout
        while time.time() < deadline:
            result = self.exec(
                ["test", "-f", "/run/cloud-init/result.json"],
                check=False,
            )
            if result.returncode == 0:
                print("cloud-init completed.")
                return
            time.sleep(15)
        raise TimeoutError(f"cloud-init did not complete within {timeout}s")

    def get_cloud_init_errors(self) -> list[str]:
        """Parse /run/cloud-init/result.json and return any stage errors."""
        result = self.exec(["cat", "/run/cloud-init/result.json"], check=False)
        if result.returncode != 0:
            return [f"Could not read result.json: {result.stderr.strip()}"]
        try:
            data = json.loads(result.stdout)
            return data.get("v1", {}).get("errors", [])
        except json.JSONDecodeError as e:
            return [f"Invalid JSON in result.json: {e}"]

    def stream_log(self) -> None:
        """Start streaming cloud-init-output.log to stdout in a background thread.

        Uses lxc exec + tail -f, which goes through the LXD agent websocket
        (unbuffered, no SSH/PTY issues).
        """

        def _stream() -> None:
            # Wait for the agent first
            for _ in range(60):
                result = self.exec(["true"], check=False)
                if result.returncode == 0:
                    break
                time.sleep(3)
            else:
                print("[log-stream] LXD agent not available, cannot stream logs.", flush=True)
                return

            # Wait for the log file to appear
            for _ in range(30):
                result = self.exec(
                    ["test", "-f", "/var/log/cloud-init-output.log"],
                    check=False,
                )
                if result.returncode == 0:
                    break
                time.sleep(2)

            cmd = ["lxc", "exec", self._instance, "--", "tail", "-n", "+1", "-f",
                   "/var/log/cloud-init-output.log"]
            self._log_proc = subprocess.Popen(
                cmd,
                stdout=sys.stdout,
                stderr=subprocess.DEVNULL,
            )
            self._log_proc.wait()

        self._log_thread = threading.Thread(target=_stream, daemon=True, name="log-stream")
        self._log_thread.start()

    def stop_log_stream(self) -> None:
        """Stop the background log stream."""
        if self._log_proc and self._log_proc.poll() is None:
            self._log_proc.terminate()
            try:
                self._log_proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._log_proc.kill()

    def get_ip(self) -> str:
        """Get the VM's IPv4 address from the LXD network."""
        result = self._lxc("list", self._instance, "--format", "json")
        instances = json.loads(result.stdout)
        if not instances:
            raise RuntimeError(f"Instance '{self.name}' not found")

        network = instances[0].get("state", {}).get("network", {})
        for iface_name, iface in network.items():
            if iface_name == "lo":
                continue
            for addr in iface.get("addresses", []):
                if addr.get("family") == "inet" and addr.get("scope") == "global":
                    return addr["address"]
        raise RuntimeError(f"No IPv4 address found for '{self.name}'")

    def collect_logs(self, dest: Path) -> None:
        """Pull cloud-init log files from the VM."""
        dest.mkdir(parents=True, exist_ok=True)
        log_files = [
            "/var/log/cloud-init.log",
            "/var/log/cloud-init-output.log",
            "/run/cloud-init/result.json",
        ]
        for remote_path in log_files:
            filename = Path(remote_path).name
            local_path = dest / filename
            result = self._lxc(
                "file", "pull", f"{self._instance}{remote_path}", str(local_path),
                check=False,
            )
            if result.returncode != 0:
                # File might not exist yet
                local_path.unlink(missing_ok=True)

        print(f"Logs saved to {dest}/")

    def delete(self) -> None:
        """Force-delete the VM."""
        self.stop_log_stream()
        print(f"Deleting VM '{self.name}'...")
        self._lxc("delete", "--force", self._instance, check=False)
        print(f"VM '{self.name}' deleted.")
