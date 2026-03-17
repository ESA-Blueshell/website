# Nginx — Local Development Reverse Proxy

> **Production note:** In production, [Traefik](../../infra/) handles TLS
> termination, HTTP→HTTPS redirect, and routing to all environments. Nginx is
> only used for local development.

Nginx acts as the local reverse proxy for development, handling:

- HTTP → HTTPS redirect
- TLS termination (self-signed cert for local dev)
- Reverse-proxying to the `api` (`:8080`) and `frontend` (`:3000`) services

---

## Files

| File | Purpose |
|------|---------|
| `nginx.conf` | HTTP-only config (ACME challenge + plain proxy) |
| `nginx-ssl.conf` | Full HTTPS config (TLS termination, HTTP redirect) |
| `nginx-dev.conf` | Local dev config |
| `entrypoint.sh` | Picks config based on cert presence |
| `Dockerfile` | Builds custom nginx image with both configs |

---

## Production: Traefik

In production, Traefik (in `infra/docker-compose.yml`) replaces nginx:

- **TLS**: Traefik obtains Let's Encrypt certificates automatically via ACME HTTP-01
- **Routing**: done via Docker container labels (`traefik.http.routers.*`)
- **Multi-env**: each environment (`website`, `website-staging`, `website-dev`) has
  its own subdomain — all routed by the single Traefik instance

See [infra/](../../infra/) and [deployment/README.md](../../deployment/README.md).
