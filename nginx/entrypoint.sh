#!/bin/sh
set -e

CERT_DIR="/etc/letsencrypt/live/esa-blueshell.nl"
HTTP_ONLY_CONF="/etc/nginx/conf.d/nginx-http-only.conf"
SSL_CONF="/etc/nginx/conf.d/nginx-ssl.conf"

# Ensure directories exist
mkdir -p "$CERT_DIR"
mkdir -p /var/www/certbot
mkdir -p /etc/nginx/conf.d

# Determine which config to use based on cert existence
if [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then
  echo "✓ SSL certificates found. Starting nginx with HTTPS enabled..."
  cp /etc/nginx/nginx-ssl.conf "$SSL_CONF"
else
  echo "⚠ No SSL certificates found. Starting nginx in HTTP-only mode..."
  echo "→ Certbot will obtain certificates, then nginx will reload with HTTPS."
  cp /etc/nginx/nginx.conf "$HTTP_ONLY_CONF"
fi

# Remove default nginx config to avoid conflicts
rm -f /etc/nginx/conf.d/default.conf

# Handle reload signals
trap 'echo "Received reload signal, checking for certificates..."; \
      if [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then \
        echo "✓ Certificates now available, switching to SSL config..."; \
        rm -f "$HTTP_ONLY_CONF"; \
        cp /etc/nginx/nginx-ssl.conf "$SSL_CONF"; \
        nginx -s reload; \
        echo "✓ Nginx reloaded with HTTPS enabled"; \
      else \
        nginx -s reload; \
      fi' HUP

# Launch nginx in the foreground
exec nginx -g 'daemon off;'
