#!/bin/sh
set -e

# Ensure cert directory exists
mkdir -p /etc/nginx/ssl

# Generate certs only if missing
if [ ! -f /etc/nginx/ssl/fullchain.pem ] || [ ! -f /etc/nginx/ssl/privkey.pem ]; then
  openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/privkey.pem \
    -out /etc/nginx/ssl/fullchain.pem \
    -days 365 \
    -subj "/CN=esa-blueshell"
fi

# Finally, launch nginx in the foreground
exec nginx -g 'daemon off;'