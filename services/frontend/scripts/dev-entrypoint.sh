#!/bin/sh
set -e

yarn install --immutable

exec "$@"
