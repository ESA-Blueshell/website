#!/bin/bash
set -e

echo "🔒 ESA Blueshell - SSL Certificate Setup"
echo "========================================"
echo ""

# Check if docker compose is available
if ! command -v docker &> /dev/null; then
    echo "❌ Error: docker is not installed or not in PATH"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ Error: docker-compose.yml not found. Please run this script from the project root."
    exit 1
fi

echo "📦 Building and starting services..."
docker compose up -d --build

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 15

echo ""
echo "🔍 Checking nginx status..."
if docker compose ps nginx | grep -q "Up"; then
    echo "✅ Nginx is running"
else
    echo "❌ Nginx failed to start. Check logs with: docker compose logs nginx"
    exit 1
fi

echo ""
echo "🔍 Checking certbot status..."
if docker compose ps certbot | grep -q "Up"; then
    echo "✅ Certbot is running"
else
    echo "❌ Certbot failed to start. Check logs with: docker compose logs certbot"
    exit 1
fi

echo ""
echo "📜 Following certbot logs (waiting for certificate acquisition)..."
echo "   Press Ctrl+C once you see 'Certificate obtained successfully'"
echo ""

# Follow certbot logs
docker compose logs -f certbot &
LOGS_PID=$!

# Wait for certificates to appear (check every 5 seconds for up to 5 minutes)
SECONDS=0
MAX_WAIT=300

while [ $SECONDS -lt $MAX_WAIT ]; do
    if docker compose exec -T certbot test -f /etc/letsencrypt/live/esa-blueshell.nl/fullchain.pem; then
        echo ""
        echo "✅ Certificate obtained successfully!"
        kill $LOGS_PID 2>/dev/null || true
        break
    fi
    sleep 5
done

if [ $SECONDS -ge $MAX_WAIT ]; then
    echo ""
    echo "⚠️  Timeout waiting for certificate. Please check certbot logs:"
    echo "   docker compose logs certbot"
    kill $LOGS_PID 2>/dev/null || true
    exit 1
fi

echo ""
echo "🔄 Restarting nginx to enable HTTPS..."
docker compose restart nginx

echo ""
echo "⏳ Waiting for nginx to restart..."
sleep 5

echo ""
echo "🔍 Verifying HTTPS is working..."
if curl -sf -I https://esa-blueshell.nl > /dev/null 2>&1; then
    echo "✅ HTTPS is working!"
else
    echo "⚠️  HTTPS verification failed. This might be normal if DNS isn't fully propagated."
    echo "   Try accessing https://esa-blueshell.nl manually."
fi

echo ""
echo "✅ Setup complete!"
echo ""
echo "📊 Service status:"
docker compose ps

echo ""
echo "📝 Next steps:"
echo "  - Verify HTTPS: https://esa-blueshell.nl"
echo "  - Monitor logs: docker compose logs -f"
echo "  - Check certbot: docker compose logs certbot"
echo ""
echo "🔄 Certificate renewal:"
echo "  - Automatic renewal runs every 12 hours"
echo "  - Manual renewal: docker compose exec certbot certbot renew"
echo "  - After renewal: docker compose restart nginx"
