# Blueshell Website

Full-stack web application for ESA Blueshell — a student association platform
for managing members, events, payments, and communications.

Built with **Spring Boot 4 (Kotlin)** backend and **Vue.js 3 (TypeScript)** frontend.

---

## Architecture

Domain-Driven Design with a clean layered architecture:

| Layer | Technology |
|-------|-----------|
| **Backend API** | Spring Boot 4 (Kotlin), Spring Security, Spring Data JPA |
| **Frontend** | Vue.js 3, TypeScript, Vuetify 3, Vite |
| **Database** | MariaDB 10.11 |
| **Email** | Stalwart SMTP relay (transactional) |
| **Secrets** | HashiCorp Vault + Vault Secrets Operator |
| **Auth / OIDC** | API issues tokens (Spring Authorization Server) for Headlamp, Vault |
| **Reverse proxy** | Traefik v3 in k3s (Let's Encrypt DNS-01 via Cloudflare) |
| **Orchestration** | k3s (single node) + FluxCD GitOps |
| **OS** | NixOS on Contabo VPS |

**Architecture decisions:** [docs/adr/ADR-INDEX.md](docs/adr/ADR-INDEX.md)

---

## Development setup

### Prerequisites

- Docker + Docker Compose v2
- Java 21 (optional — for running the API outside Docker)
- Node.js + Yarn Berry (optional — for running the frontend outside Docker)
- A GitHub token with `read:packages` — both images pull the Brevo and Discord
  clients from GitHub Packages, which authenticates even for public packages

### A token for the image builds

The builds read the token from `.secrets/` as a BuildKit secret, so it never
lands in the image history. Write the files once — they are gitignored, and the
compose files expect exactly these three names:

```bash
mkdir -p .secrets && chmod 700 .secrets
gh auth token          > .secrets/github_token      # Gradle → maven.pkg.github.com
gh auth token          > .secrets/node_auth_token   # Yarn   → npm.pkg.github.com
gh api user -q .login  > .secrets/github_actor      # username the maven registry wants
chmod 600 .secrets/*
```

A classic PAT with `read:packages` works just as well as the `gh` token — paste
it into the two token files instead. Compose refuses to start when a secret
file is missing, so create all three even if you leave one empty.

The tokens are read at **image build** time only. The api image bakes Gradle's
dependency cache in, so the running container starts `bootRun --offline` and
never reaches for the registry. Rebuild the image (`docker compose build api`)
when a dependency version changes; source edits hot-reload off the bind mount
as before.

### Start the dev environment

```bash
docker compose up -d
```

This starts:

| Service | URL | Notes |
|---------|-----|-------|
| API | http://localhost:8080 | Hot-reload via Gradle |
| Frontend | http://localhost:3000 | Hot-reload via Vite |
| Swagger UI | http://localhost:8080/swagger-ui | Set `SPRINGDOC_API_DOCS_ENABLED=true` |
| MariaDB | localhost:3307 | |
| Stalwart | http://localhost:8085 | Dev MTA admin UI (SMTP :1025, IMAP :1143, admin `admin`/`admin`) |

### Reaching the api, and trying the site on a phone

The frontend reaches the api at the page's own origin under `/api`, the same shape
production serves: `http://localhost:3000/api` from this machine, and
`http://<your-lan-ip>:3000/api` from anything else on the network. Vite proxies
`/api` and strips the prefix, mirroring the `strip-api-prefix` Traefik middleware,
so no address is configured anywhere and a phone needs nothing but the URL:

```bash
ipconfig getifaddr en0    # then open http://<that>:3000 on the phone
```

The api's own port stays published, so `http://localhost:8080` still answers
directly for Swagger, `curl` and the debugger.

Two things stay laptop-only, and are supposed to: activation and password-reset
links, and the email tracking pixel. Those are absolute URLs the api builds from
`FRONTEND_URL` and `APP_URL`, and dev mail is read on the laptop anyway.

`VITE_APP_URL` still overrides the origin if you point the frontend at a deployed
api — set a matching entry in `security.cors.allowed-origins`
(`services/api/src/main/resources/application-dev.yaml`) when you do, since that
request is cross-origin again.

### Environment files

The compose files include sensible defaults. For production-like secrets,
copy the examples:

```bash
cp services/api/.db.example.env             services/api/.db.env
```

### Run tests

```bash
./gradlew :services:api:test
./gradlew :services:api:integrationTest
```

### Generate OpenAPI TypeScript client

```bash
./scripts/generate_openapi.sh
```

### Remote debugging

The dev API container exposes JDWP on `localhost:5005`.
IntelliJ: **Remote JVM Debug → host: localhost, port: 5005**.

---

## Production deployment

Production runs on a single-node NixOS + k3s + FluxCD stack. Flux reconciles
manifests from `platform/cluster/flux/` against `main`; Keel polls
`ghcr.io/esa-blueshell/*` for new `:latest` tags and rolls the matching
Deployments. There is no CI deploy step — pushing to `main` is the deploy.

Runbook: [`platform/docs/runbook.md`](platform/docs/runbook.md).

`build-push.yml` publishes `api` and `frontend` images to GHCR.

---

## Services at a glance

| Service | Image | Internal port | Description |
|---------|-------|---------------|-------------|
| `api` | `ghcr.io/esa-blueshell/api` | 8080 | Spring Boot REST API |
| `frontend` | `ghcr.io/esa-blueshell/frontend` | 3000 | Vue.js SPA |
| `db` | `mariadb:10.11` | 3306 | Application database |

---

## Security

- JWT authentication (Spring Security)
- SQL injection prevention (JPA parameterized queries)
- XSS protection (Vue template escaping + CSP headers)
- CORS restricted to blueshell domains
- TLS 1.2+ with Let's Encrypt certificates (auto-renewed)

---

## API documentation

- **Development:** http://localhost:8080/swagger-ui (set `SPRINGDOC_API_DOCS_ENABLED=true`)
- **Production:** disabled (OpenAPI docs off in the prod profile)
- **OpenAPI spec:** `/api/v3/api-docs`

---

## Contributing

1. Create a feature branch from `main`
2. Make changes with hot reload in the dev environment
3. Run tests: `./gradlew :services:api:test`
4. If API endpoints changed: `./scripts/generate_openapi.sh`
5. Open a pull request

---

## Support

Questions or issues: **board@blueshell.utwente.nl**
