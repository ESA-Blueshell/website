#!/bin/sh
set -e

CERT_DIR=/etc/nginx/ssl
KEY_FILE=$CERT_DIR/localhost.key
CERT_FILE=$CERT_DIR/localhost.crt

if [ ! -f "$CERT_FILE" ] || [ ! -f "$KEY_FILE" ]; then
  mkdir -p "$CERT_DIR"
  echo "Generating self-signed certificate for localhost..."
  openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout "$KEY_FILE" \
    -out "$CERT_FILE" \
    -days 3650 \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
  echo "Self-signed certificate generated."
else
  echo "Using existing certificate."
fi

exec nginx -g "daemon off;"
