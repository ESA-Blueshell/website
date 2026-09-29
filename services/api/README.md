# API - Spring Boot Backend

A Spring Boot 4.0.3 REST API for the Blueshell student association management system, built with Kotlin and following Domain-Driven Design (DDD) principles with clean architecture.

## Quick Start

### Prerequisites

- **Java 25** (toolchain in `build-logic/src/main/kotlin/kotlin-conventions.gradle.kts`)
- **Docker** (for containerized development)

### Development

```bash
# Run with Docker, from the repository root
docker compose up api

# Or run locally
./gradlew :services:api:bootRun
```

Access Swagger UI: `http://localhost:8080/swagger-ui`

### Testing

```bash
# Run all tests
./gradlew :api:test

# Run specific domain tests
./gradlew :api:test --tests "net.blueshell.api.auth.*"

# Run architecture tests (ArchUnit)
./gradlew :api:test --tests "net.blueshell.api.architecture.*"

# Run all tests with system tests
./gradlew :api:test systemTest

# Full coverage (Docker + API-driven system tests)
./scripts/test-all-compose-coverage.sh
```

## Architecture Overview

The backend is a modular monolith. Each top-level package under `net.blueshell.api` is a
Spring Modulith application module, and a build-time verification refuses a reach into another
module that its `ModuleMetadata` does not allow.

```
net/blueshell/api/{module}/
├── ModuleMetadata.kt  # the module and what it may depend on
├── api/               # what other modules may call
├── domain/            # use-case services, job handlers, listeners
├── persistence/       # JPA entities and repositories
└── web/               # controllers, requests, responses and their mappers
```

**Key principles:**
- **Modules, not layers**: a module owns its entities, and another module reaches them only
  through its `api` package
- **Use-case services**: a controller calls a service method; there is no command bus
- **Events and jobs**: a module reacts to another through Spring events, and deferred work runs
  on the durable job queue

**For architecture guidance, see:**
- **[AGENTS.md](../../AGENTS.md)**: the developer guide
- **[docs/adr/architecture/ADR-INDEX.md](../../docs/adr/architecture/ADR-INDEX.md)** and
  **[docs/adr/api/ADR-INDEX.md](../../docs/adr/api/ADR-INDEX.md)**: the decisions
- **Key ADRs**:
  - [Architecture ADR-001: Application Modules Replace Layers](../../docs/adr/architecture/ADR-001-application-modules-replace-layers.md)
  - [Architecture ADR-002: Use-Case Services Replace the Command Bus](../../docs/adr/architecture/ADR-002-use-case-services-replace-the-command-bus.md)
  - [Architecture ADR-003: Package Topology and Placement Rules](../../docs/adr/architecture/ADR-003-package-topology-and-placement-rules.md)

## Project Structure

```
services/api/src/main/kotlin/net/blueshell/api/
├── auth/, user/, event/, committee/, contribution/, ...   # one package per module
├── platform/          # Spring configuration and the in-memory integration fakes
├── security/          # authentication and the permission evaluator
└── shared/            # the kernel several modules share
```

## Building & Deployment

### Generate OpenAPI Spec and TypeScript Client

```bash
# From project root
./gradlew :services:api:dumpOpenApiSpec
yarn --cwd services/frontend gen:blueshell
```

This:
1. Generates OpenAPI spec from Spring Boot backend
2. Generates TypeScript client for frontend

### Build Production Image

```bash
# Build locally
./gradlew :services:api:build

# Build Docker image, from the repository root
docker build -f services/api/Dockerfile -t blueshell-api:latest .
```

## Database

- **Engine**: MariaDB 10.11.10
- **Migrations**: Liquibase (`src/main/resources/db/changelog/`): a baseline plus dated YAML
  changesets, each with a rollback. See [api ADR-034](../../docs/adr/api/ADR-034-the-schema-starts-from-a-baseline.md)
- **Timezone**: Europe/Amsterdam
- **Charset**: UTF-8 (utf8mb4)

## API Documentation

- **Development**: `http://localhost:8080/swagger-ui`
- **Production**: `https://esa-blueshell.nl/api/swagger-ui`
- **OpenAPI Spec**: `/api/v3/api-docs`

Auto-generated from `@Tag`, `@Operation`, and parameter annotations.

## External Integrations

The API reaches each external service through an adapter in the module that uses it. Where the
service's credentials are not set, an in-memory stand-in beside the port takes its place:

- **Google Calendar**: approved events, pushed by the `sync` module
- **Brevo**: contacts and cohort lists, through the `contact` and `cohort` modules
- **Discord**: the bot's event posts and Discord events, through the `discord` module
- **Stalwart** (SMTP relay): transactional email delivery via JavaMailSender
- **Job queue**: a durable `job_executions` table and an `@Async` pool, with no external broker

See [ADR-019: Anti-Corruption Layers](../../docs/adr/api/ADR-019-anti-corruption-layers-for-external-integration.md) for integration patterns.

## Validation Strategy

Validation is distributed across layers:

| Layer | Responsibility | Database Access |
|-------|----------------|-----------------|
| **Web** | Format, structure, presence, field-level constraints | ❌ No |
| **Use case** | Business rules (unique, constraints) | ✅ Yes |
| **Entity** | Invariant enforcement | ✅ Yes |

See [ADR-003: Validation Layer Separation](../../docs/adr/api/ADR-003-validation-layer-separation.md) and
[architecture ADR-005: Validation Placement](../../docs/adr/architecture/ADR-005-validation-placement.md).

## Event-Driven Architecture

Domain events enable loose coupling between bounded contexts:

```kotlin
// Events published in domain service
events.publish(UserCreated(userId, createdByBoard = true))

// Listened by other domains
@Component
class RecoveryEventListener(val activationService: UserActivationService) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        activationService.issueActivationForNewUser(event.userId)
    }
}
```

See [ADR-006: Event-Driven Architecture](../../docs/adr/api/ADR-006-event-driven-architecture.md).

## Debugging

Remote JVM debugging available in development:

```bash
# Configure IntelliJ Remote JVM Debug
# Host: localhost
# Port: 5005
# Attach to running API container
```

## Troubleshooting

### Tests failing with database issues

```bash
# Rebuild containers with clean volumes
docker compose down -v
docker compose up
```

### Gradle dependency issues

```bash
# Clear gradle cache
rm -rf .gradle
./gradlew clean build
```

### OpenAPI client generation fails

`dumpOpenApiSpec` starts the api on an in-memory H2 database, so it needs no running stack. Where it fails, the api failed to start: its output names why.

## Policies & Compliance

User-facing policies are written in `docs/policies/` (the Cookie Policy and the Privacy Policy, each
in English and Dutch). `scripts/generate-policy-pdfs.sh` renders them into
`services/frontend/src/assets/documents/`, the one copy the site serves.

These are referenced in signup flows and user consent workflows.

## Contributing

1. Follow the architecture patterns in AGENTS.md and the ADRs
2. Reference ADRs when making design decisions
3. Run tests and architecture checks before committing
4. Update OpenAPI spec when API changes (`./gradlew :services:api:dumpOpenApiSpec`, then `yarn --cwd services/frontend gen:blueshell`)
5. Keep changes within bounded contexts

See [AGENTS.md](../../AGENTS.md) for detailed development guidelines.

---

**Note**: The API is part of the Blueshell website project. See the root [README.md](../../README.md) for full project setup.
