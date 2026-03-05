# API - Spring Boot Backend

A Spring Boot 4.0.3 REST API for the Blueshell student association management system, built with Kotlin and following Domain-Driven Design (DDD) principles with clean architecture.

## Quick Start

### Prerequisites

- **Java 24** (toolchain in `build.gradle.kts`)
- **Docker** (for containerized development)

### Development

```bash
# Run with Docker
docker compose -f docker-compose.dev.yml up api

# Or run locally
./gradlew :api:bootRun
```

Access Swagger UI: `http://localhost:8080/swagger-ui`

### Testing

```bash
# Run all tests
./gradlew :api:test

# Run specific domain tests
./gradlew :api:test --tests "net.blueshell.api.domain.auth.*"

# Run architecture tests (ArchUnit)
./gradlew :api:test --tests "net.blueshell.api.architecture.*"

# Run all tests with system tests
./gradlew :api:test systemTest

# Full coverage (Docker + API-driven system tests)
./scripts/test-all-compose-coverage.sh
```

## Architecture Overview

The backend follows **multi-layered Domain-Driven Design** with strict layer separation and ArchUnit-enforced boundaries.

```
domain/{domain-name}/
├── web/              # REST endpoints & DTOs
├── command/          # Use case commands
├── application/      # Business logic & services
├── domain/           # Rich domain models (optional)
└── persistence/      # JPA entities & repositories
```

**Key Principles:**
- **Bounded Contexts**: Each domain is self-contained
- **Command Pattern**: Independent commands dispatch to handlers
- **Event-Driven**: Cross-domain communication via domain events
- **Layer Independence**: Dependencies point inward (clean architecture)

**For comprehensive architecture guidance, see:**
- **[CLAUDE.md](../CLAUDE.md)** - Complete developer guide
- **[docs/adr/api/ADR-INDEX.md](../docs/adr/api/ADR-INDEX.md)** - All architecture decisions
- **Key ADRs**:
  - [ADR-001: Multi-Layered DDD Architecture](../docs/adr/api/ADR-001-multi-layered-domain-driven-architecture.md)
  - [ADR-002: Command Pattern with CommandBus](../docs/adr/api/ADR-002-command-pattern-with-command-bus.md)
  - [ADR-016: Layer Dependency Rules](../docs/adr/api/ADR-016-layer-dependency-rules.md)
  - [ADR-022: Platform, Infrastructure, and Shared Organization](../docs/adr/api/ADR-022-platform-infrastructure-shared-organization.md)

## Project Structure

```
api/src/main/kotlin/net/blueshell/api/
├── domain/                 # Domain-driven design contexts
│   ├── auth/              # Authentication & recovery
│   ├── user/              # User management
│   ├── membership/        # Member lifecycle
│   ├── event/             # Event management
│   ├── committee/         # Committee operations
│   └── ...
├── shared/                # Shared kernel contracts
│   ├── command/           # Command infrastructure
│   ├── event/             # Domain event base types
│   ├── email/             # Email content DTOs
│   └── job/               # Job definitions
├── infrastructure/        # Spring Security & framework
│   └── security/          # JWT, permission evaluators
└── platform/              # External integrations
    ├── config/            # Spring configuration
    ├── integration/       # Anti-corruption layers
    │   ├── email/        # Brevo email service
    │   ├── calendar/     # Google Calendar API
    │   ├── payment/      # Mollie payment processor
    │   └── ...
    └── jobs/             # Job dispatching (@Async + RetryTemplate)
```

## Building & Deployment

### Generate OpenAPI Spec and TypeScript Client

```bash
# From project root
./generate_openapi.sh
```

This:
1. Generates OpenAPI spec from Spring Boot backend
2. Generates TypeScript client for frontend
3. Updates Discord and other external API clients

### Build Production Image

```bash
# Build locally
./gradlew :api:build

# Build Docker image
docker build -f api/Dockerfile -t blueshell-api:latest api/
```

## Database

- **Engine**: MariaDB 10.11.10
- **Migrations**: Flyway (`api/src/main/resources/db/migration/`)
- **Pattern**: `V{version}__{description}.sql`
- **Timezone**: Europe/Amsterdam
- **Charset**: UTF-8 (utf8mb4)

## API Documentation

- **Development**: `http://localhost:8080/swagger-ui`
- **Production**: `https://esa-blueshell.nl/api/swagger-ui`
- **OpenAPI Spec**: `/api/v3/api-docs`

Auto-generated from `@Tag`, `@Operation`, and parameter annotations.

## External Integrations

The API integrates with several external services via anti-corruption layers in `platform/integration/`:

- **Google Calendar API**: Event synchronization
- **Mollie**: Payment processing
- **Brevo**: Email campaigns (contact sync fallback)
- **Listmonk**: Transactional email delivery and contact/list management
- **Job Dispatch**: @Async thread pool + RetryTemplate (no external broker)

See [ADR-019: Anti-Corruption Layers](../docs/adr/api/ADR-019-anti-corruption-layers-for-external-integration.md) for integration patterns.

## Validation Strategy

Validation is distributed across layers:

| Layer | Responsibility | Database Access |
|-------|----------------|-----------------|
| **Web** | Format, structure, presence | ❌ No |
| **Command** | Field-level validation | ❌ No |
| **Application** | Business rules (unique, constraints) | ✅ Yes |
| **Domain** | Invariant enforcement | ✅ Yes |

See [ADR-003: Validation Layer Separation](../docs/adr/api/ADR-003-validation-layer-separation.md).

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

See [ADR-006: Event-Driven Architecture](../docs/adr/api/ADR-006-event-driven-architecture.md).

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
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up
```

### Gradle dependency issues

```bash
# Clear gradle cache
rm -rf .gradle
./gradlew clean build
```

### OpenAPI client generation fails

Ensure the API is running and accessible:
```bash
curl http://localhost:8080/api/v3/api-docs
```

## Policies & Compliance

User-facing policies are documented in `docs/policies/`:
- Cookie Policy
- Privacy Policy

These are referenced in signup flows and user consent workflows.

## Contributing

1. Follow the architecture patterns in CLAUDE.md
2. Reference ADRs when making design decisions
3. Run tests and architecture checks before committing
4. Update OpenAPI spec when API changes (`./generate_openapi.sh`)
5. Keep changes within bounded contexts

See [CLAUDE.md](../CLAUDE.md) for detailed development guidelines.

---

**Note**: The API is part of the Blueshell website project. See the root [README.md](../README.md) for full project setup.
