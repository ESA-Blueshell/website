"""QEMU VM management for cloud-init testing.

Uses QEMU with SLIRP (userspace) networking — no iptables/nftables/kernel
NAT rules required, so it works reliably in all CI environments.

Command execution uses the QEMU Guest Agent over a Unix socket, which runs
commands as root inside the VM without any SSH authentication.
"""

from __future__ import annotations

import base64
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import types
from pathlib import Path


# ── QEMU Guest Agent ─────────────────────────────────────────────────────────


class QemuGuestAgent:
    """Minimal QEMU Guest Agent client over a Unix socket.

    The QGA protocol is newline-delimited JSON-RPC. There is no initial
    greeting from the server (unlike QMP). We synchronise once on connect
    using ``guest-sync-delimited`` so stale data in the socket buffer is
    discarded.
    """

    SYNC_ID = 0x5A5A5A5A  # arbitrary fixed sync cookie

    def __init__(self, sock_path: str) -> None:
        self._sock_path = sock_path
        self._sock: socket.socket | None = None
        self._buf = b""

    # ── connection ────────────────────────────────────────────────────────────

    def connect(self) -> None:
        """Open the Unix socket connection to the guest agent."""
        self._sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._sock.connect(self._sock_path)
        self._sock.settimeout(60)
        self._buf = b""

    def close(self) -> None:
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def wait_ready(self, timeout: int = 180) -> None:
        """Wait until the guest agent socket is connectable and responsive."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                self.connect()
                self._sync()
                return
            except (OSError, TimeoutError):
                self.close()
                time.sleep(2)
        raise TimeoutError(f"QEMU guest agent not ready after {timeout}s")

    # ── low-level protocol ────────────────────────────────────────────────────

    def _send(self, obj: dict) -> None:
        if self._sock is None:
            raise ConnectionError("QGA socket not connected")
        self._sock.sendall((json.dumps(obj) + "\n").encode())

    def _recv_line(self) -> dict:
        """Read one newline-terminated JSON object from the socket."""
        if self._sock is None:
            raise ConnectionError("QGA socket not connected")
        while b"\n" not in self._buf:
            chunk = self._sock.recv(65536)
            if not chunk:
                raise EOFError("Guest agent socket closed")
            self._buf += chunk
        line, self._buf = self._buf.split(b"\n", 1)
        return json.loads(line.strip())

    def _call(self, execute: str, arguments: dict | None = None) -> dict:
        req: dict = {"execute": execute}
        if arguments is not None:
            req["arguments"] = arguments
        self._send(req)
        return self._recv_line()

    def _sync(self) -> None:
        """Synchronise the QGA channel using guest-sync-delimited.

        This discards any stale data that may be buffered in the socket and
        ensures the agent is responsive before we start sending real commands.
        """
        self._send({"execute": "guest-sync-delimited", "arguments": {"id": self.SYNC_ID}})
        # The agent responds with 0xFF followed by the JSON response on one line.
        # We keep reading until we see our sync cookie.
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                resp = self._recv_line()
                if resp.get("return") == self.SYNC_ID:
                    return
            except (json.JSONDecodeError, EOFError):
                pass
        raise TimeoutError("guest-sync-delimited timed out")

    # ── high-level API ────────────────────────────────────────────────────────

    def exec(
        self,
        path: str,
        args: list[str] | None = None,
        env: list[str] | None = None,
    ) -> tuple[int, str, str]:
        """Execute a command inside the VM. Returns (returncode, stdout, stderr)."""
        arguments: dict = {
            "path": path,
            "arg": args or [],
            "capture-output": True,
        }
        if env:
            arguments["env"] = env

        resp = self._call("guest-exec", arguments)
        if "error" in resp:
            raise RuntimeError(f"guest-exec error: {resp['error']}")
        pid = resp["return"]["pid"]

        # Poll until the process exits
        while True:
            status = self._call("guest-exec-status", {"pid": pid})
            if "error" in status:
                raise RuntimeError(f"guest-exec-status error: {status['error']}")
            data = status["return"]
            if data.get("exited"):
                rc = data.get("exitcode", 0)
                stdout = base64.b64decode(data.get("out-data", "")).decode("utf-8", errors="replace")
                stderr = base64.b64decode(data.get("err-data", "")).decode("utf-8", errors="replace")
                return rc, stdout, stderr
            time.sleep(0.2)

    def write_file(self, remote_path: str, content: bytes) -> None:
        """Write bytes to a file inside the VM."""
        resp = self._call("guest-file-open", {"path": remote_path, "mode": "wb"})
        if "error" in resp:
            raise RuntimeError(f"guest-file-open error: {resp['error']}")
        handle = resp["return"]
        try:
            chunk_size = 32768
            for i in range(0, len(content), chunk_size):
                chunk = content[i : i + chunk_size]
                wr = self._call("guest-file-write", {
                    "handle": handle,
                    "buf-b64": base64.b64encode(chunk).decode(),
                    "count": len(chunk),
                })
                if "error" in wr:
                    raise RuntimeError(f"guest-file-write error: {wr['error']}")
        finally:
            self._call("guest-file-close", {"handle": handle})

    def read_file(self, remote_path: str) -> str:
        """Read a text file from inside the VM."""
        resp = self._call("guest-file-open", {"path": remote_path, "mode": "rb"})
        if "error" in resp:
            raise RuntimeError(f"guest-file-open error: {resp['error']}")
        handle = resp["return"]
        data = b""
        try:
            while True:
                rd = self._call("guest-file-read", {"handle": handle, "count": 65536})
                if "error" in rd:
                    raise RuntimeError(f"guest-file-read error: {rd['error']}")
                chunk = base64.b64decode(rd["return"].get("buf-b64", ""))
                data += chunk
                if rd["return"].get("eof"):
                    break
        finally:
            self._call("guest-file-close", {"handle": handle})
        return data.decode("utf-8", errors="replace")


# ── QEMU VM ──────────────────────────────────────────────────────────────────


class QemuVm:
    """Manages a QEMU virtual machine for cloud-init testing.

    Uses SLIRP (userspace) networking so no kernel NAT rules are required.
    Executes commands via the QEMU Guest Agent (runs as root, no SSH needed).
    """

    # Local port forwarded to VM port 2222 (the hardened SSH port)
    _SSH_HOST_PORT = 12222

    def __init__(
        self,
        name: str,
        cloud_config_path: Path,
        base_image_path: Path,
        *,
        cpus: int = 2,
        memory_mb: int = 2048,
    ) -> None:
        self.name = name
        self.cloud_config_path = cloud_config_path
        self.base_image_path = base_image_path
        self.cpus = cpus
        self.memory_mb = memory_mb

        self._tmpdir: tempfile.TemporaryDirectory | None = None
        self._work_image: Path | None = None
        self._seed_image: Path | None = None
        self._qga_sock: str | None = None
        self._qga: QemuGuestAgent | None = None
        self._proc: subprocess.Popen | None = None
        self._log_thread: threading.Thread | None = None
        self._stop_log = threading.Event()

    @property
    def ssh_port(self) -> int:
        """Local port that forwards to the VM's SSH port 2222."""
        return self._SSH_HOST_PORT

    # ── lifecycle ─────────────────────────────────────────────────────────────

    def launch(self) -> None:
        """Prepare disk images and start the QEMU VM."""
        self._tmpdir = tempfile.TemporaryDirectory(prefix=f"qemu-{self.name}-")
        tmp = Path(self._tmpdir.name)

        self._work_image = tmp / "disk.qcow2"
        self._seed_image = tmp / "seed.img"
        self._qga_sock = str(tmp / "qga.sock")

        # Create a copy-on-write overlay so the base image is never modified
        subprocess.run(
            ["qemu-img", "create", "-f", "qcow2",
             "-F", "qcow2", "-b", str(self.base_image_path),
             str(self._work_image)],
            check=True, capture_output=True,
        )
        # Expand the overlay to 10 GiB (the base image is typically 2 GiB)
        subprocess.run(
            ["qemu-img", "resize", str(self._work_image), "10G"],
            check=True, capture_output=True,
        )
        # Build the cloud-init seed ISO
        subprocess.run(
            ["cloud-localds", str(self._seed_image), str(self.cloud_config_path)],
            check=True, capture_output=True,
        )

        cmd = [
            "qemu-system-x86_64",
            "-machine", "q35,accel=kvm",
            "-cpu", "host",
            "-m", str(self.memory_mb),
            "-smp", str(self.cpus),
            "-nographic",
            "-nodefaults",
            "-serial", "none",
            "-monitor", "none",
            # Boot disk (copy-on-write overlay)
            "-drive", f"file={self._work_image},format=qcow2,if=virtio",
            # Cloud-init seed ISO
            "-drive", f"file={self._seed_image},format=raw,if=virtio,readonly=on",
            # SLIRP (userspace) networking — no kernel NAT needed
            "-netdev", f"user,id=net0,hostfwd=tcp::{self._SSH_HOST_PORT}-:2222",
            "-device", "virtio-net-pci,netdev=net0",
            # QEMU Guest Agent socket
            "-chardev", f"socket,path={self._qga_sock},server=on,wait=off,id=qga0",
            "-device", "virtio-serial",
            "-device", "virtserialport,chardev=qga0,name=org.qemu.guest_agent.0",
        ]

        print(f"Launching QEMU VM '{self.name}' ({self.cpus} vCPUs, {self.memory_mb} MiB)...")
        # pylint: disable=consider-using-with
        self._proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        print(f"VM '{self.name}' launched (pid {self._proc.pid}).")

        self._qga = QemuGuestAgent(self._qga_sock)

    def exec(
        self,
        cmd: list[str],
        *,
        check: bool = True,
        capture: bool = True,  # kept for interface compatibility
    ) -> types.SimpleNamespace:
        """Execute a command inside the VM via the QEMU Guest Agent (runs as root)."""
        assert self._qga is not None, "VM not launched"
        path, *args = cmd
        rc, stdout, stderr = self._qga.exec(path, args)
        result = types.SimpleNamespace(
            returncode=rc,
            stdout=stdout,
            stderr=stderr,
            args=cmd,
        )
        if check and rc != 0:
            raise subprocess.CalledProcessError(rc, cmd, stdout, stderr)
        return result

    def push_file(self, local_path: Path, remote_path: str) -> None:
        """Write a local file into the VM via the QEMU Guest Agent."""
        assert self._qga is not None, "VM not launched"
        content = Path(local_path).read_bytes()
        self._qga.write_file(remote_path, content)

    def wait_for_agent(self, timeout: int = 180) -> None:
        """Wait for the QEMU Guest Agent to become ready."""
        assert self._qga is not None, "VM not launched"
        print(f"Waiting for QEMU guest agent (up to {timeout}s)...")
        self._qga.wait_ready(timeout=timeout)
        print("QEMU guest agent is ready.")

    def wait_for_cloud_init(self, timeout: int = 1200) -> None:
        """Wait for cloud-init to complete by polling for result.json."""
        print(f"Waiting for cloud-init to complete (up to {timeout}s)...")
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                rc, _, _ = self._qga.exec("test", ["-f", "/run/cloud-init/result.json"])
                if rc == 0:
                    print("cloud-init completed.")
                    return
            except (OSError, RuntimeError):
                # Guest agent may not be ready yet; reconnect
                try:
                    self._qga.close()
                    self._qga.connect()
                except OSError:
                    pass
            time.sleep(15)
        raise TimeoutError(f"cloud-init did not complete within {timeout}s")

    def get_cloud_init_errors(self) -> list[str]:
        """Parse /run/cloud-init/result.json and return any stage errors."""
        assert self._qga is not None, "VM not launched"
        try:
            content = self._qga.read_file("/run/cloud-init/result.json")
            data = json.loads(content)
            return data.get("v1", {}).get("errors", [])
        except Exception as e:
            return [f"Could not read result.json: {e}"]

    def stream_log(self) -> None:
        """Start printing cloud-init-output.log to stdout in a background thread."""
        self._stop_log.clear()

        def _poll() -> None:
            offset = 0
            # Wait until the agent and the log file are available
            for _ in range(60):
                if self._stop_log.is_set():
                    return
                try:
                    assert self._qga is not None
                    content = self._qga.read_file("/var/log/cloud-init-output.log")
                    if len(content) > offset:
                        print(content[offset:], end="", flush=True)
                        offset = len(content)
                except (OSError, RuntimeError, AssertionError):
                    pass
                time.sleep(5)

        self._log_thread = threading.Thread(target=_poll, daemon=True, name="log-stream")
        self._log_thread.start()

    def stop_log_stream(self) -> None:
        """Stop the background log polling thread."""
        self._stop_log.set()
        if self._log_thread:
            self._log_thread.join(timeout=10)

    def get_ip(self) -> str:
        """Return the host-side address for SSH (SLIRP forwards via localhost)."""
        return "127.0.0.1"

    def collect_logs(self, dest: Path) -> None:
        """Pull cloud-init log files from the VM to a local directory."""
        dest.mkdir(parents=True, exist_ok=True)
        log_files = [
            "/var/log/cloud-init.log",
            "/var/log/cloud-init-output.log",
            "/run/cloud-init/result.json",
        ]
        for remote_path in log_files:
            filename = Path(remote_path).name
            local_path = dest / filename
            try:
                assert self._qga is not None
                content = self._qga.read_file(remote_path)
                local_path.write_text(content)
            except Exception:
                local_path.unlink(missing_ok=True)
        print(f"Logs saved to {dest}/")

    def delete(self) -> None:
        """Terminate the QEMU process and clean up temporary files."""
        self.stop_log_stream()
        if self._qga:
            self._qga.close()
            self._qga = None
        if self._proc and self._proc.poll() is None:
            print(f"Terminating VM '{self.name}' (pid {self._proc.pid})...")
            self._proc.terminate()
            try:
                self._proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self._proc.kill()
                self._proc.wait()
            print(f"VM '{self.name}' terminated.")
        if self._tmpdir:
            self._tmpdir.cleanup()
            self._tmpdir = None
