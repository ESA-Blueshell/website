#!/bin/sh
set -e

# === CONFIGURATION ===
DOMAIN="esa-blueshell.nl"
EMAIL="board@blueshell.utwente.nl"
CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
HTTP_ONLY_CONF="/etc/nginx/conf.d/nginx-http-only.conf"
SSL_CONF="/etc/nginx/conf.d/nginx-ssl.conf"

# === INITIALIZATION ===
echo "🚀 Nginx + Certbot SSL Manager Starting..."
echo "=========================================="

# Ensure required directories exist
mkdir -p "$CERT_DIR"
mkdir -p /var/www/certbot
mkdir -p /etc/nginx/conf.d

# Remove default nginx config to avoid conflicts
rm -f /etc/nginx/conf.d/default.conf

# === CERTIFICATE CHECK ===
if [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then
  echo "✅ SSL certificates found at: $CERT_DIR"
  echo "🔒 Starting nginx in HTTPS mode..."
  cp /tmp/nginx/nginx-ssl.conf "$SSL_CONF"
  HTTPS_MODE=true
else
  echo "⚠️  No SSL certificates found"
  echo "🌐 Starting nginx in HTTP-only mode..."
  cp /tmp/nginx/nginx.conf "$HTTP_ONLY_CONF"
  HTTPS_MODE=false
fi

# === START NGINX ===
echo "▶️  Launching nginx..."
nginx -g 'daemon off;' &
NGINX_PID=$!

# Wait for nginx to be ready
sleep 3

if ! kill -0 $NGINX_PID 2>/dev/null; then
  echo "❌ Nginx failed to start"
  exit 1
fi

echo "✅ Nginx is running (PID: $NGINX_PID)"

# === CERTIFICATE ACQUISITION (if needed) ===
if [ "$HTTPS_MODE" = false ]; then
  echo ""
  echo "📜 Obtaining SSL certificate..."
  echo "   Domain: $DOMAIN, www.$DOMAIN"
  echo "   Email: $EMAIL"
  echo ""

  # Wait a bit more to ensure nginx is fully ready
  sleep 2

  # Attempt to obtain certificate
  if certbot certonly --webroot -w /var/www/certbot \
    -d "$DOMAIN" -d "www.$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --non-interactive \
    --no-eff-email; then

    echo ""
    echo "✅ Certificate obtained successfully!"
    echo "🔄 Switching to HTTPS configuration..."

    # Switch from HTTP-only to HTTPS config
    rm -f "$HTTP_ONLY_CONF"
    cp /tmp/nginx/nginx-ssl.conf "$SSL_CONF"

    # Reload nginx to apply HTTPS config
    nginx -s reload

    echo "✅ Nginx reloaded with HTTPS enabled"
    HTTPS_MODE=true
  else
    echo ""
    echo "❌ Failed to obtain certificate"
    echo "⚠️  Continuing in HTTP-only mode"
    echo "   Certificate acquisition will be retried in 6 hours"
  fi
fi

# === CERTIFICATE RENEWAL LOOP ===
echo ""
echo "🔄 Starting certificate renewal service..."
echo "   Checks run twice daily (every 12 hours)"
echo ""

while true; do
  sleep 12h

  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Running certificate renewal check..."

  if [ "$HTTPS_MODE" = false ]; then
    # Retry acquisition if we don't have certs yet
    echo "   Retrying certificate acquisition..."

    if certbot certonly --webroot -w /var/www/certbot \
      -d "$DOMAIN" -d "www.$DOMAIN" \
      --email "$EMAIL" \
      --agree-tos \
      --non-interactive \
      --no-eff-email; then

      echo "   ✅ Certificate obtained!"
      echo "   🔄 Switching to HTTPS configuration..."

      rm -f "$HTTP_ONLY_CONF"
      cp /tmp/nginx/nginx-ssl.conf "$SSL_CONF"
      nginx -s reload

      echo "   ✅ Nginx reloaded with HTTPS enabled"
      HTTPS_MODE=true
    else
      echo "   ⚠️  Acquisition failed, will retry in 12 hours"
    fi
  else
    # Normal renewal for existing certificates
    if certbot renew --webroot -w /var/www/certbot --quiet --deploy-hook "nginx -s reload"; then
      echo "   ✅ Renewal check complete"
    else
      echo "   ⚠️  Renewal check encountered issues"
    fi
  fi
done
