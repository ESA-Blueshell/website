#!/bin/sh
set -e

CERT_DIR="/etc/letsencrypt/live/esa-blueshell.nl"
DOMAIN="esa-blueshell.nl"
EMAIL="board@blueshell.utwente.nl"

echo "🔒 Certbot SSL Certificate Manager"
echo "===================================="

# Wait for nginx to be ready
echo "⏳ Waiting for nginx to be ready..."
sleep 10

# First-time certificate acquisition if missing
if [ ! -f "$CERT_DIR/fullchain.pem" ]; then
  echo "📜 No certificate found. Obtaining initial certificate..."
  echo "   Domain: $DOMAIN, www.$DOMAIN"
  echo "   Email: $EMAIL"

  certbot certonly --webroot -w /var/www/certbot \
    -d "$DOMAIN" -d "www.$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos --non-interactive

  if [ -f "$CERT_DIR/fullchain.pem" ]; then
    echo "✅ Certificate obtained successfully!"
    echo "   Location: $CERT_DIR"
    echo ""
    echo "🔄 Please restart nginx to enable HTTPS:"
    echo "   docker compose restart nginx"
  else
    echo "❌ Failed to obtain certificate. Check logs above."
  fi
else
  echo "✅ Certificate already exists at: $CERT_DIR"
fi

# Run renewal check loop
echo ""
echo "🔄 Starting automatic renewal service (checks every 12 hours)..."
while :; do
  echo "[$(date)] Checking for certificate renewal..."
  certbot renew --webroot -w /var/www/certbot --quiet
  echo "[$(date)] Renewal check complete. Sleeping for 12 hours..."
  sleep 12h
done
