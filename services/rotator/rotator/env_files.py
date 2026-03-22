import logging
import os
import re

log = logging.getLogger(__name__)


def update_env_file(file_path: str, key: str, value: str) -> None:
    """Update a KEY=VALUE in an env file (or append if not present)."""
    if not os.path.isfile(file_path):
        log.warning("env file %s not found — skipping local update for %s", file_path, key)
        return

    with open(file_path) as f:
        lines = f.readlines()

    pattern = re.compile(rf"^{re.escape(key)}=")
    found = False
    new_lines = []
    for line in lines:
        if pattern.match(line):
            new_lines.append(f"{key}={value}\n")
            found = True
        else:
            new_lines.append(line)

    if not found:
        new_lines.append(f"{key}={value}\n")

    with open(file_path, "w") as f:
        f.writelines(new_lines)

    log.info("Updated %s in %s", key, file_path)
