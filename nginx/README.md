# Nginx + Certbot SSL Setup

This directory contains the nginx configuration with automatic Let's Encrypt SSL certificate management.

## How It Works

### Initial Deployment (No Certificates)

1. **Nginx starts in HTTP-only mode** using `nginx.conf`
   - Serves ACME challenges at `/.well-known/acme-challenge/`
   - Proxies all traffic to frontend/api (no HTTPS redirect yet)

2. **Certbot obtains certificates**
   - Waits 10 seconds for nginx to be ready
   - Requests certificates from Let's Encrypt using webroot method
   - Certificates are stored in `/etc/letsencrypt/live/esa-blueshell.nl/`

3. **Nginx restarts with SSL enabled**
   - After certbot succeeds, restart nginx: `docker compose restart nginx`
   - Nginx detects certificates exist and loads `nginx-ssl.conf`
   - HTTPS is now enabled with automatic HTTP → HTTPS redirect

### Subsequent Deployments (Certificates Exist)

- Nginx automatically detects existing certificates and starts with SSL enabled
- No manual intervention needed

### Automatic Renewal

- Certbot checks for renewal every 12 hours
- Let's Encrypt certificates are valid for 90 days
- Certbot renews certificates when they have 30 days or less remaining
- After renewal, restart nginx: `docker compose restart nginx`

## Files

- **`nginx.conf`**: HTTP-only configuration (used during initial cert acquisition)
- **`nginx-ssl.conf`**: Full HTTPS configuration with SSL certificates
- **`Dockerfile`**: Builds custom nginx image with both configs
- **`entrypoint.sh`**: Smart entrypoint that chooses config based on cert availability

## Deployment Steps

### First-Time Deployment

```bash
# 1. Start all services
docker compose up -d

# 2. Monitor certbot logs to see when certificates are obtained
docker compose logs -f certbot

# 3. Once you see "Certificate obtained successfully", restart nginx
docker compose restart nginx

# 4. Verify HTTPS is working
curl -I https://esa-blueshell.nl
```

### Updating Configuration

If you modify `nginx.conf` or `nginx-ssl.conf`:

```bash
# Rebuild nginx image
docker compose build nginx

# Restart nginx
docker compose restart nginx
```

### Manual Certificate Renewal

```bash
# Force renewal (for testing)
docker compose exec certbot certbot renew --force-renewal

# Restart nginx to load new certificates
docker compose restart nginx
```

### Troubleshooting

**nginx won't start:**
- Check logs: `docker compose logs nginx`
- Verify frontend and api services are healthy
- Check if port 80/443 are already in use on host

**Certbot can't obtain certificate:**
- Ensure DNS points to your server
- Check nginx is serving ACME challenges: `curl http://esa-blueshell.nl/.well-known/acme-challenge/test`
- Check certbot logs: `docker compose logs certbot`
- Verify firewall allows port 80

**Certificate renewal fails:**
- Check certbot logs: `docker compose logs certbot`
- Manually run: `docker compose exec certbot certbot renew --dry-run`
- Ensure webroot `/var/www/certbot` is properly mounted

## Certificate Locations

- **Certificates**: `/etc/letsencrypt/live/esa-blueshell.nl/`
  - `fullchain.pem`: Full certificate chain
  - `privkey.pem`: Private key
- **ACME challenges**: `/var/www/certbot`

Both directories are shared between nginx and certbot containers via Docker volumes.
