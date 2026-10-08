#!/bin/sh
# Copies Gatus's history with SQLite's online backup, which is consistent while Gatus writes.
# Root writes here and nobody's Kopia reads it, and the history holds no secret.
umask 022
mkdir -p /work/status /work/gatus
if ! python3 - <<'PY'
import sqlite3
source = sqlite3.connect("/gatus/data.db", timeout=60)
target = sqlite3.connect("/work/gatus/data.db")
source.backup(target)
# Gatus runs in WAL mode; one self-contained file opens anywhere, read-only included.
target.execute("pragma journal_mode=delete")
target.close()
source.close()
PY
then
  echo "SQLite could not back up Gatus's database." | tee /work/status/gatus.failed >&2
fi
exit 0
