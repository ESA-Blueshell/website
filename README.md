# Blueshell Website

Full-stack web application for ESA Blueshell — a student association platform
for managing members, events, payments, and communications.

Built with **Spring Boot 4 (Kotlin)** backend and **Vue.js 3 (TypeScript)** frontend,
deployed as a **Docker Swarm stack** on a Contabo VPS.

---

## Architecture

Domain-Driven Design (DDD) with a clean layered architecture:

| Layer | Technology |
|-------|-----------|
| **Backend API** | Spring Boot 4.0 (Kotlin), Spring Security, Spring Data JPA |
| **Frontend** | Vue.js 3, TypeScript, Vuetify 3, Vite |
| **Database** | MariaDB 10.11 (application), PostgreSQL 17 (Listmonk) |
| **Email** | Listmonk v4 (transactional + marketing) |
| **Reverse proxy** | Nginx with Let's Encrypt (Certbot) |
| **Containerization** | Docker Swarm (single-node, rootless) |
| **CI/CD** | GitHub Actions → GHCR → `website pull` on server |

**Architecture decisions:** [docs/adr/ADR-INDEX.md](docs/adr/ADR-INDEX.md)

---

## Repository Layout

```
website/
├── services/
│   ├── api/                 Spring Boot backend
│   │   ├── src/             Kotlin source
│   │   ├── Dockerfile       Production image
│   │   ├── docker-compose.yml       Production compose fragment
│   │   ├── docker-compose.dev.yml   Development compose fragment
│   │   ├── .db.env          MariaDB credentials (not committed)
│   │   └── .api.env         Application secrets (not committed)
│   ├── frontend/            Vue.js 3 frontend
│   │   ├── src/             TypeScript/Vue source
│   │   ├── Dockerfile
│   │   ├── docker-compose.yml
│   │   └── docker-compose.dev.yml
│   ├── nginx/               Reverse proxy + SSL
│   │   ├── nginx.conf           HTTP-only (initial cert acquisition)
│   │   ├── nginx-ssl.conf       Full HTTPS + redirect
│   │   ├── entrypoint.sh        Picks config based on cert presence
│   │   ├── Dockerfile
│   │   └── README.md
│   ├── listmonk/            Email & contact management (Listmonk + PostgreSQL)
│   │   ├── setup.py         Idempotent first-run setup
│   │   ├── docker-compose.yml
│   │   ├── docker-compose.dev.yml
│   │   └── .listmonk.env    Listmonk credentials (not committed)
│   └── mailserver/          docker-mailserver (development only)
│       └── docker-compose.dev.yaml
├── deployment/              Docker Swarm stack & deployment scripts
│   ├── docker-stack.yml     Swarm stack definition (all services, flat)
│   ├── deploy.sh            Deploy script (sources env files + stack deploy)
│   └── README.md            Full deployment guide
├── image/                   VPS provisioning (Contabo cloud-init + ops)
│   ├── scripts/provision.sh System setup baked into the image
│   ├── cloud-init/          Cloud-init templates + render script
│   ├── ops/                 backup.sh, restore.sh, create-vps.sh
│   ├── packer/              Optional QEMU pre-baked image
│   └── README.md            Provisioning guide
├── docs/
│   ├── adr/                 Architecture Decision Records
│   └── policies/            Privacy & Cookie policies (EN/NL)
├── scripts/                 Developer scripts (OpenAPI gen, coverage, etc.)
├── docker-compose.yml       Production compose (uses include:)
└── docker-compose.dev.yml   Development compose
```

---

## Development Setup

### Prerequisites

- Docker + Docker Compose v2 (for dev environment)
- Java 24 (optional — for running API outside Docker)
- Node.js + Yarn Berry (optional — for running frontend outside Docker)

### Start the dev environment

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts:

| Service | URL | Notes |
|---------|-----|-------|
| API | https://localhost/api | Hot-reload via Gradle |
| Frontend | https://localhost | Hot-reload via Vite |
| Swagger UI | https://localhost/api/swagger-ui | |
| MariaDB | localhost:3307 | |
| Listmonk | http://localhost:9000 | Email management UI |
| Mailserver | localhost:587 (SMTP) | Bounce testing |

### Environment files

The dev compose files include sensible defaults. For production-like secrets,
copy the examples:

```bash
cp services/api/.db.example.env       services/api/.db.env
cp services/listmonk/.listmonk.example.env  services/listmonk/.listmonk.env
```

### Run tests

```bash
# All tests (API unit + system + frontend unit + e2e + coverage)
docker compose -f docker-compose.dev.yml run --rm api ./gradlew test

# Full test suite with merged coverage report
./scripts/test-all-compose-coverage.sh
```

### Generate OpenAPI TypeScript client

Run after changing API endpoints:

```bash
./scripts/generate_openapi.sh
```

The generated client lands in `services/frontend/src/services/api/blueshell/`.

### Remote debugging

The dev API container exposes a JDWP debug port at `localhost:5005`.
Configure IntelliJ: **Remote JVM Debug → host: localhost, port: 5005**.

---

## Production Deployment

Production runs as a Docker Swarm stack on a Contabo VPS.

### Quick reference

```bash
# Provision a new VPS
cd image && ./cloud-init/render.sh --standalone && ./ops/create-vps.sh

# Pull a backup from the server
cd image && ./ops/backup.sh

# Push a backup (e.g. when migrating)
cd image && ./ops/restore.sh

# On the server — deploy / redeploy
sudo website up

# On the server — update to latest images (also triggered by CI)
sudo website pull
```

**Full docs:** [deployment/README.md](deployment/README.md)
**VPS provisioning:** [image/README.md](image/README.md)

### CI/CD flow

Every push to `main`:
1. Runs tests (API unit, system, frontend unit, e2e, coverage check)
2. Builds and pushes Docker images to GHCR (`api`, `frontend`, `nginx`)
3. SSHes to the production server and runs `website pull`

`website pull` = `git pull` + `bash deployment/deploy.sh` (Docker Swarm stack deploy).

---

## Services at a Glance

| Service | Image | Internal port | Description |
|---------|-------|---------------|-------------|
| `nginx` | `ghcr.io/esa-blueshell/nginx` | 80, 443 | Reverse proxy + SSL |
| `api` | `ghcr.io/esa-blueshell/api` | 8080 | Spring Boot REST API |
| `frontend` | `ghcr.io/esa-blueshell/frontend` | 3000 | Vue.js SPA |
| `db` | `mariadb:10.11` | 3306 | Application database |
| `listmonk` | `listmonk/listmonk:v4.1.0` | 9000 | Email + contact management |
| `listmonk-db` | `postgres:17-alpine` | 5432 | Listmonk database |
| `listmonk-setup` | `python:3.12-alpine` | — | One-time Listmonk setup job |

---

## Security

- JWT authentication (Spring Security)
- SQL injection prevention (JPA/Hibernate parameterized queries)
- XSS protection (Vue.js template escaping + CSP headers via Nginx)
- CORS restricted to `esa-blueshell.nl`
- TLS 1.2+ with Let's Encrypt certificates (auto-renewed)
- SSH on port 2222, keys-only, no password auth
- Rootless Docker on the VPS (no root daemon)
- Secrets stored in per-service `.env` files (not committed to git)

---

## API Documentation

- **Development:** https://localhost/api/swagger-ui
- **Production:** https://esa-blueshell.nl/api/swagger-ui
- **OpenAPI spec:** `/api/v3/api-docs`

---

## Contributing

1. Create a feature branch from `main`
2. Make changes with hot reload in the dev environment
3. Run tests: `docker compose -f docker-compose.dev.yml run --rm api ./gradlew test`
4. If API endpoints changed: `./scripts/generate_openapi.sh`
5. Open a pull request

---

## Support

Questions or issues: **board@blueshell.utwente.nl**
