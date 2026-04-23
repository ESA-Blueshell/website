# Blueshell Website

Full-stack web application for ESA Blueshell — a student association platform
for managing members, events, payments, and communications.

Built with **Spring Boot 4 (Kotlin)** backend and **Vue.js 3 (TypeScript)** frontend.

> **Platform migration in progress.** The repository is moving from a Docker Swarm
> deployment on a Contabo VPS to a single-node **NixOS + k3s + FluxCD + Kustomize + Helm**
> stack. Swarm-era assets (`infra/`, `vps/`,
> `docker-stack.yml`, `deploy.sh`, `rotator`, `mailserver`) have been removed in this
> branch. The new `platform/` tree will land in follow-up PRs.

---

## Architecture (target)

Domain-Driven Design with a clean layered architecture:

| Layer | Technology |
|-------|-----------|
| **Backend API** | Spring Boot 4 (Kotlin), Spring Security, Spring Data JPA |
| **Frontend** | Vue.js 3, TypeScript, Vuetify 3, Vite |
| **Database** | MariaDB 10.11 (application), PostgreSQL 17 (Listmonk) |
| **Email (marketing)** | Listmonk v4 |
| **Email (MTA)** | Stalwart (replaces docker-mailserver) |
| **Secrets** | HashiCorp Vault + Vault Secrets Operator |
| **Auth / OIDC** | API issues tokens (Spring Authorization Server) for Headlamp, Vault |
| **Reverse proxy** | Traefik v3 in k3s (Let's Encrypt DNS-01 via Cloudflare) |
| **Orchestration** | k3s (single node) + FluxCD GitOps |
| **OS** | NixOS on Contabo VPS |

**Architecture decisions:** [docs/adr/ADR-INDEX.md](docs/adr/ADR-INDEX.md)

---

## Development Setup

### Prerequisites

- Docker + Docker Compose v2
- Java 21 (optional — for running API outside Docker)
- Node.js + Yarn Berry (optional — for running frontend outside Docker)

### Start the dev environment

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts:

| Service | URL | Notes |
|---------|-----|-------|
| API | http://localhost:8080 | Hot-reload via Gradle |
| Frontend | http://localhost:3000 | Hot-reload via Vite |
| Swagger UI | http://localhost:8080/swagger-ui | Set `SPRINGDOC_API_DOCS_ENABLED=true` |
| MariaDB | localhost:3307 | |
| Listmonk | http://localhost:9000 | Email management UI |
| Stalwart | http://localhost:8085 | Dev MTA admin UI (SMTP :1025, IMAP :1143, admin `admin`/`admin`) |

### Environment files

The dev compose files include sensible defaults. For production-like secrets,
copy the examples:

```bash
cp services/api/.db.example.env       services/api/.db.env
cp services/listmonk/.listmonk.example.env  services/listmonk/.listmonk.env
```

### Run tests

```bash
# API unit + integration tests (will be split in PR3)
docker compose -f docker-compose.dev.yml run --rm api ./gradlew test
```

### Generate OpenAPI TypeScript client

```bash
./scripts/generate_openapi.sh
```

### Remote debugging

The dev API container exposes JDWP on `localhost:5005`.
IntelliJ: **Remote JVM Debug → host: localhost, port: 5005**.

---

## Production Deployment

The previous Docker Swarm deployment is being replaced. See
[`platform/docs/runbook.md`](platform/docs/runbook.md) for the current state of the migration.

The `build-push.yml` workflow still publishes `api` and `frontend` images to GHCR
(`ghcr.io/esa-blueshell/*`). The `deploy.yml` workflow keeps deploying to the old
Swarm VPS until the k3s cutover lands.

---

## Services at a Glance

| Service | Image | Internal port | Description |
|---------|-------|---------------|-------------|
| `api` | `ghcr.io/esa-blueshell/api` | 8080 | Spring Boot REST API |
| `frontend` | `ghcr.io/esa-blueshell/frontend` | 3000 | Vue.js SPA |
| `db` | `mariadb:10.11` | 3306 | Application database |
| `listmonk` | `listmonk/listmonk:v4.1.0` | 9000 | Email + contact management |
| `listmonk-db` | `postgres:17-alpine` | 5432 | Listmonk database |
| `listmonk-setup` | `python:3.12-alpine` | — | One-time Listmonk setup job |

---

## Security

- JWT authentication (Spring Security)
- SQL injection prevention (JPA parameterized queries)
- XSS protection (Vue template escaping + CSP headers)
- CORS restricted to blueshell domains
- TLS 1.2+ with Let's Encrypt certificates (auto-renewed)

---

## API Documentation

- **Development:** http://localhost:8080/swagger-ui (set `SPRINGDOC_API_DOCS_ENABLED=true`)
- **Production:** disabled (OpenAPI docs off in the prod profile)
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
