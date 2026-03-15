#!/bin/sh
# service shim for nginx status check
if [ "$1" = "nginx" ] && [ "$2" = "status" ]; then
  # exit with 0 if nginx master process is running, else 1
  ps aux | grep -q '[n]ginx: master process'
else
  echo "Usage: service nginx status" >&2
  exit 1
fi