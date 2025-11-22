#!/bin/sh
set -e

CERT_DIR="/etc/letsencrypt/live/esa-blueshell.nl"

# Ensure cert and ACME directories exist
mkdir -p "$CERT_DIR"
mkdir -p /var/www/certbot

# Generate mock certs only if missing
if [ ! -f "$CERT_DIR/fullchain.pem" ] || [ ! -f "$CERT_DIR/privkey.pem" ]; then
  echo "Generating self-signed certificate in $CERT_DIR ..."
  openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout "$CERT_DIR/privkey.pem" \
    -out "$CERT_DIR/fullchain.pem" \
    -days 365 \
    -subj "/CN=esa-blueshell.nl"
fi

# Finally, launch nginx in the foreground
exec nginx -g 'daemon off;'
