"""Phase 1: Verify that password hashes in the rendered cloud-config are correct.

Fast (~1 second), no VM needed. Parses the rendered YAML to extract
each user's SHA-512 hash, re-derives it from the plaintext password
using the embedded salt, and asserts they match.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

import yaml

from lib.credentials import Credentials


def _extract_hash(config_path: Path, username: str) -> str:
    """Extract the password hash for a user from the rendered cloud-config."""
    config = yaml.safe_load(config_path.read_text())
    for user in config["chpasswd"]["users"]:
        if user["name"] == username:
            # type: hash format uses 'password' for the hash value
            return user.get("hashed_passwd") or user["password"]
    raise ValueError(f"User '{username}' not found in chpasswd.users")


def _verify_hash(password: str, stored_hash: str) -> bool:
    """Re-derive the hash from password + embedded salt and compare."""
    assert stored_hash.startswith("$6$"), f"Expected SHA-512 hash ($6$...), got: {stored_hash[:20]}"

    # Extract salt: $6$<salt>$<hash>
    parts = stored_hash.split("$")
    salt = parts[2]

    result = subprocess.run(
        ["openssl", "passwd", "-6", "-salt", salt, password],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"openssl passwd failed: {result.stderr}")

    return result.stdout.strip() == stored_hash


class TestHashVerification:
    """Verify SHA-512 password hashes in the rendered cloud-config."""

    def test_admin_hash_matches_password(
        self, rendered_config: Path, credentials: Credentials
    ) -> None:
        stored_hash = _extract_hash(rendered_config, "admin")
        assert _verify_hash(credentials.admin_password, stored_hash), (
            "admin: SHA-512 hash does not match plaintext password"
        )

    def test_root_hash_matches_password(
        self, rendered_config: Path, credentials: Credentials
    ) -> None:
        stored_hash = _extract_hash(rendered_config, "root")
        assert _verify_hash(credentials.root_password, stored_hash), (
            "root: SHA-512 hash does not match plaintext password"
        )

    def test_website_hash_matches_password(
        self, rendered_config: Path, credentials: Credentials
    ) -> None:
        stored_hash = _extract_hash(rendered_config, "website")
        assert _verify_hash(credentials.website_password, stored_hash), (
            "website: SHA-512 hash does not match plaintext password"
        )
