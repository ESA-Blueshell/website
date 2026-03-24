"""LXD container management for cloud-init testing."""

from __future__ import annotations

import json
import os
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
        """SSH port — 2222 inside the container, accessible directly via container IP."""
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

    def _build_cloud_config(self) -> str:
        """Return cloud-init user-data with any CI-specific bootstrapping merged in."""
        cloud_config = self.cloud_config_path.read_text()
        apt_proxy = os.environ.get("LXD_APT_PROXY")
        if not apt_proxy:
            return cloud_config

        # Keep proxy setup in the instance user-data so it cannot be dropped by
        # vendor-data merge behavior when the main cloud-config also defines bootcmd.
        #
        # Strip the scheme (http://) to get host:port for SSH ProxyCommand (nc -X connect).
        proxy_hostport = apt_proxy.split("://", 1)[-1].rstrip("/")
        proxy_bootcmd = "\n".join([
            "  - mkdir -p /etc/apt/apt.conf.d",
            f"  - echo 'Acquire::http::Proxy \"{apt_proxy}\";' > /etc/apt/apt.conf.d/95proxy.conf",
            f"  - echo 'Acquire::https::Proxy \"{apt_proxy}\";' >> /etc/apt/apt.conf.d/95proxy.conf",
            # Write proxy to system config files so tools use it regardless of env-var
            # propagation. cloud-init may not pass systemd Environment= vars down to runcmd
            # subprocesses. Use printf directly — tools may not be installed yet in bootcmd.
            f"  - printf '[http]\\n\\tproxy = {apt_proxy}\\n' > /etc/gitconfig",
            # /etc/curlrc is the system-wide curl config — covers curl calls in provision.sh
            # (e.g. Infisical CLI download from dl.cloudsmith.io).
            f"  - printf 'proxy = {apt_proxy}\\n' >> /etc/curlrc",
            # /etc/environment is read by PAM (pam_env) for all `su` sessions.
            # This covers Docker CLI (docker login) and other tools that use env vars
            # but have no equivalent of /etc/gitconfig or /etc/curlrc.
            f"  - echo 'http_proxy={apt_proxy}' >> /etc/environment",
            f"  - echo 'https_proxy={apt_proxy}' >> /etc/environment",
            f"  - echo 'HTTP_PROXY={apt_proxy}' >> /etc/environment",
            f"  - echo 'HTTPS_PROXY={apt_proxy}' >> /etc/environment",
            # Pre-create the Docker daemon proxy drop-in so Docker uses the proxy
            # for image pulls even if provision.sh's configure_docker_proxy() doesn't
            # run (which requires env vars to be present in provision.sh's shell).
            "  - mkdir -p /etc/systemd/system/docker.service.d",
            (
                "  - printf '[Service]\\n"
                f"Environment=\"http_proxy={apt_proxy}\"\\n"
                f"Environment=\"https_proxy={apt_proxy}\"\\n"
                f"Environment=\"HTTP_PROXY={apt_proxy}\"\\n"
                f"Environment=\"HTTPS_PROXY={apt_proxy}\"\\n' "
                "> /etc/systemd/system/docker.service.d/proxy.conf"
            ),
            # Configure SSH to tunnel github.com through the HTTP proxy via CONNECT so the
            # SSH fallback in the clone script can also reach ssh.github.com:443.
            # nc (netcat-openbsd) is pre-installed in Debian 13 cloud images.
            # %%h/%%p: printf interprets %% as literal %, producing %h/%p in the output.
            (
                "  - printf 'Host github.com\\n"
                "  HostName ssh.github.com\\n"
                "  Port 443\\n"
                f"  ProxyCommand nc -X connect -x {proxy_hostport} %%h %%p\\n'"
                " >> /etc/ssh/ssh_config"
            ),
            "  - mkdir -p /etc/systemd/system/cloud-final.service.d",
            (
                "  - printf '[Service]\\n"
                f"Environment=\"http_proxy={apt_proxy}\"\\n"
                f"Environment=\"https_proxy={apt_proxy}\"\\n"
                f"Environment=\"HTTP_PROXY={apt_proxy}\"\\n"
                f"Environment=\"HTTPS_PROXY={apt_proxy}\"\\n' "
                "> /etc/systemd/system/cloud-final.service.d/proxy.conf"
            ),
            "  - systemctl daemon-reload",
        ])

        bootcmd_marker = "bootcmd:\n"
        if bootcmd_marker in cloud_config:
            return cloud_config.replace(bootcmd_marker, f"{bootcmd_marker}{proxy_bootcmd}\n", 1)

        header = "#cloud-config\n"
        if cloud_config.startswith(header):
            return f"{header}\nbootcmd:\n{proxy_bootcmd}\n{cloud_config[len(header):].lstrip()}"

        raise ValueError("Rendered cloud-config must start with #cloud-config")

    def launch(self) -> None:
        """Launch the container with cloud-init user-data."""
        cloud_config = self._build_cloud_config()

        # Delete any leftover instance from a previous run
        self._lxc("delete", "--force", self._instance, check=False)

        init_args = [
            "init",
            self.image,
            self._instance,
            "-c", f"limits.cpu={self.cpus}",
            "-c", f"limits.memory={self.memory}",
            "-c", f"user.user-data={cloud_config}",
        ]

        print(f"Launching LXD container '{self.name}' ({self.image}, {self.cpus} CPUs, {self.memory})...")
        self._lxc(*init_args)

        network = os.environ.get("LXD_NETWORK")
        if network:
            # Override the default profile NIC before the first start so CI can
            # attach the instance to a runner-provided host-backed LXD network.
            self._lxc(
                "config", "device", "override", self._instance, "eth0",
                f"network={network}",
            )

        self._lxc("start", self._instance)
        print(f"Container '{self.name}' launched.")

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

    def push_file(self, local_path: Path, remote_path: str) -> None:
        """Push a local file into the VM via lxc file push."""
        self._lxc("file", "push", str(local_path), f"{self._instance}{remote_path}")

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

        def global_ipv4s(iface: dict) -> list[str]:
            return [
                addr["address"]
                for addr in iface.get("addresses", [])
                if addr.get("family") == "inet" and addr.get("scope") == "global"
            ]

        preferred_names = ("eth0", "enp0s3", "ens3", "ens5")
        for iface_name in preferred_names:
            iface = network.get(iface_name)
            if not iface:
                continue
            addresses = global_ipv4s(iface)
            if addresses:
                return addresses[0]

        skipped_prefixes = ("docker", "br-", "veth", "virbr", "lo")
        for iface_name, iface in network.items():
            if iface_name.startswith(skipped_prefixes):
                continue
            addresses = global_ipv4s(iface)
            if addresses:
                return addresses[0]

        for iface_name, iface in network.items():
            if iface_name == "lo":
                continue
            addresses = global_ipv4s(iface)
            if addresses:
                return addresses[0]
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
