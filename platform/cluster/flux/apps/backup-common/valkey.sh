#!/bin/sh
# Has Valkey write a fresh save and streams it here, so no volume of Valkey's is touched.
umask 077
mkdir -p /work/status /work/valkey
if ! valkey-cli -h "$VALKEY_HOST" --rdb /work/valkey/dump.rdb; then
  echo "Valkey did not hand over a save." | tee /work/status/valkey.failed >&2
fi
exit 0
