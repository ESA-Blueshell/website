#!/bin/sh
# Stops or starts Stalwart through the Kubernetes API, as the backup's own service account.
# RocksDB must not be copied while Stalwart has it open, so the snapshot runs in between.
#   stalwart.sh stop   scales to 0 and waits for the pod to be gone; on failure, starts it again
#   stalwart.sh start  scales back to 1
# It always exits 0: a failure is written to /work/status/stalwart.failed.
api=https://kubernetes.default.svc
sa=/var/run/secrets/kubernetes.io/serviceaccount
ns=$(cat "$sa/namespace")

call() {
  curl -sS --fail-with-body --cacert "$sa/ca.crt" -H "Authorization: Bearer $(cat "$sa/token")" "$@"
}

scale() {
  call -X PATCH -H 'Content-Type: application/merge-patch+json' \
    -d "{\"spec\":{\"replicas\":$1}}" "$api/apis/apps/v1/namespaces/$ns/deployments/stalwart/scale" >/dev/null
}

failed() {
  mkdir -p /work/status
  echo "$1" | tee -a /work/status/stalwart.failed >&2
}

case "$1" in
  stop)
    if [ -s /work/status/failed ]; then
      failed "Stalwart stays up: there are no credentials to back it up with."
      exit 0
    fi
    scale 0 || { failed "Stalwart could not be scaled down."; exit 0; }
    i=0
    while [ $i -lt 60 ]; do
      # One "phase" per pod, a terminating one included. A failed call proves nothing stopped.
      if pods=$(call "$api/api/v1/namespaces/$ns/pods?labelSelector=app.kubernetes.io/name%3Dstalwart"); then
        left=$(printf '%s' "$pods" | grep -o '"phase" *:' | wc -l | tr -d ' ')
        [ "$left" = 0 ] && { echo "Stalwart is stopped."; exit 0; }
      fi
      i=$((i + 1))
      sleep 2
    done
    failed "Stalwart's pod did not stop within two minutes."
    scale 1 || failed "Stalwart could not be started again; scale it to 1 by hand."
    ;;
  start)
    if scale 1; then echo "Stalwart is starting again."; else failed "Stalwart could not be started again; scale it to 1 by hand."; fi
    ;;
esac
exit 0
