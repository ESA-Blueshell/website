"""SSH helpers for testing SSH-specific functionality."""

from __future__ import annotations

import shutil
import subprocess


SSH_OPTS = [
    "-o", "StrictHostKeyChecking=no",
    "-o", "ConnectTimeout=5",
    "-o", "BatchMode=yes",
    "-o", "LogLevel=ERROR",
]


def ssh_key_login(host: str, port: int, user: str, key_path: str, command: str) -> str:
    """SSH into the VM with a key and run a command. Returns stdout."""
    result = subprocess.run(
        ["ssh", *SSH_OPTS, "-p", str(port), "-i", key_path,
         f"{user}@{host}", command],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"SSH key login failed for {user}@{host}:{port}: {result.stderr.strip()}"
        )
    return result.stdout.strip()


def ssh_password_login(host: str, port: int, user: str, password: str, command: str) -> str:
    """SSH into the VM with a password (via sshpass) and run a command. Returns stdout."""
    if not shutil.which("sshpass"):
        raise RuntimeError("sshpass not installed. Install with: apt install sshpass / brew install hudochenkov/sshpass/sshpass")

    sshpass_opts = [
        "-o", "StrictHostKeyChecking=no",
        "-o", "ConnectTimeout=5",
        "-o", "PasswordAuthentication=yes",
        "-o", "PubkeyAuthentication=no",
        "-o", "LogLevel=ERROR",
    ]
    result = subprocess.run(
        ["sshpass", "-p", password, "ssh", *sshpass_opts,
         "-p", str(port), f"{user}@{host}", command],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"SSH password login failed for {user}@{host}:{port}: {result.stderr.strip()}"
        )
    return result.stdout.strip()
