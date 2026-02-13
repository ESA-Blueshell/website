# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Full-stack web application for managing student association activities (ESA Blueshell). Spring Boot 3.x backend (Kotlin) with Vue.js 3 frontend (TypeScript), containerized with Docker.

## Build and Development Commands

### Backend (API)

```bash
# Run tests with Docker
docker compose -f docker-compose.dev.yml run api ./gradlew :api:bootRun

# Run tests
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test

# Run specific test
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test --tests "net.blueshell.api.domain.auth.*"

# Generate Brevo API client
docker compose -f docker-compose.dev.yml run api ./gradlew :api:generateBrevoClient

# Generate class dependency graph
docker compose -f docker-compose.dev.yml run api ./gradlew :api:classDependencyGraph

# Build (local)
./gradlew :api:build
```

### Frontend

```bash
cd frontend

# Install dependencies
yarn install

# Development server (with hot reload) (run from project root)
docker compose -f docker-compose.dev.yml up frontend

# Build for production
yarn build

# Typecheck
yarn typecheck

# Lint
yarn lint

# Generate API clients
yarn gen:blueshell    # Generate from backend OpenAPI spec
yarn gen:discord      # Generate Discord API client
yarn gen:all          # Generate all clients
```

### Docker

```bash
# Production environment
./run.sh
# or
docker compose -f docker-compose.yml up --build -d

# Development environment (with hot reload)
./run-dev.sh
# or
docker compose -f docker-compose.dev.yml up --build -d

# Generate OpenAPI specs and TypeScript clients
./generate_openapi.sh
```

## Backend Architecture

The backend follows a **multi-layered domain-driven design** with strict layer separation enforced by ArchUnit tests. Each domain (e.g., `auth`, `user`, `event`, `committee`) is organized into these layers:

### Layer Structure

```
domain/
├── {domain-name}/
    ├── web/                    # Web/API layer
    │   ├── *Controller.kt      # REST controllers
    │   ├── dto/                # Request/Response DTOs
    │   │   ├── request/
    │   │   └── response/
    │   ├── mapping/            # Mappie mappings (request -> command)
    │   └── validation/         # Custom validators
    ├── command/                # Command definitions
    │   └── *Commands.kt        # Command DTOs with validation
    ├── application/            # Application layer
    │   ├── command/            # Command handlers
    │   │   └── *CommandHandlers.kt
    │   ├── *Service.kt         # Application services (orchestration)
    │   ├── listener/           # Event listeners
    │   ├── factory/            # Domain object factories
    │   ├── email/              # Email templates/builders
    │   └── exception/          # Application exceptions
    ├── domain/                 # Domain layer (business logic)
    │   ├── model/              # Domain models (pure business logic)
    │   └── service/            # Domain services
    └── persistence/            # Persistence layer
        ├── *Entity.kt          # JPA entities
        └── repository/         # Spring Data repositories
```

### Key Patterns

**Command Pattern with CommandBus**
- Commands are immutable data classes in `command/` package
- Commands include Jakarta validation annotations
- Command handlers in `application/command/` implement business logic
- `CommandBus` validates and dispatches commands to handlers
- Controllers map web requests to commands using Mappie and dispatch via CommandBus

**Service Responsibilities**
- Application services orchestrate business operations (multiple repositories, domain services, events)
- Domain services contain pure business logic without infrastructure concerns
- Services do NOT handle request/response mapping (that's in web layer mappers)

**Entity Association Management**
- Use explicit ID references for existing entities in command DTOs
- Only use nested objects for owned children (cascade operations)
- Services resolve entity associations using `repository.getReferenceById(id)` or `findById(id)`
- Entities define explicit association helper methods (e.g., `setBanner()`, `addMember()`) for bidirectional consistency

**Mappie for Object Mapping**
- Replaces MapStruct for mapping DTOs
- Read-only mappings: entity → response DTO (in mappers)
- Write mappings: request DTO → command (in web layer) and command → entity updates (in services)
- No `@AfterMapping` or hidden business logic in mappers

**Testing**
- ArchUnit tests enforce layering and architectural rules (`architecture/` package)
- Unit tests for validators, factories, and DTOs
- Service tests verify association wiring and error handling
- Testcontainers for integration tests with MariaDB

### Architectural Rules (Enforced by ArchUnit)

- Controllers may access: Services, Validation, DTOs, Model, Common
- Services may access: Repositories, Model, Common, Validation
- Repositories may access: Model, Common
- Model/DTO may access: Common
- No circular dependencies between layers

## Technology Stack

### Backend
- Kotlin 2.3.10 with Java 24 toolchain
- Spring Boot 3.5.7 (Web, Security, Data JPA, AMQP)
- Spring Security with JWT (nimbus-jose-jwt, jjwt)
- MariaDB 10.11.10 with Flyway migrations
- Mappie 2.3.10 for object mapping
- SpringDoc OpenAPI 3 for API documentation
- Google Calendar API, Mollie (payments), Brevo (email campaigns)
- Testing: JUnit 5, Mockito Kotlin, MockK, ArchUnit, Testcontainers, REST Assured

### Frontend
- Vue.js 3.5.24 with TypeScript 5.7.2
- Vuetify 3.10.2 (UI framework)
- Vuex 4.1.0 (state management)
- Vue Router 4.5.1
- Axios 1.8.4 with auto-generated OpenAPI client (@hey-api/openapi-ts)
- Vite 6.2.0 build tool
- VeeValidate 4.15.1 (form validation)
- Luxon (date handling), Marked (Markdown), DOMPurify (XSS protection)

## Database

- Engine: MariaDB 10.11.10
- Charset: utf8mb4 with utf8mb4_unicode_ci collation
- Timezone: Europe/Amsterdam
- Migrations: Flyway (`api/src/main/resources/db/migration/`)
- Connection in dev: `localhost:3307` (Docker) or `localhost:3306` (local MariaDB)

## API Documentation

- Swagger UI: `https://localhost/api/swagger-ui` (dev) or `https://esa-blueshell.nl/api/swagger-ui` (prod)
- OpenAPI spec: `/api/v3/api-docs`
- TypeScript client generation: Run `./generate_openapi.sh` after API changes

## Project Structure Notes

### Shared Infrastructure (`api/src/main/kotlin/net/blueshell/api/`)

- `shared/command/`: CommandBus implementation, Command and CommandHandler interfaces
- `shared/dto/`: Common DTOs and base classes
- `shared/event/`: Event publishing infrastructure
- `shared/model/`: Base entity classes and common domain models
- `shared/security/`: JWT handling, authentication utilities
- `shared/validation/`: Custom validation annotations
- `infrastructure/security/`: Spring Security configuration, filters, authentication providers
- `platform/`: Application configuration, error handling, OpenAPI config

### Environment Configuration

Required environment files in `env/`:
- `.app.env`: Application config (JWT secret, SMTP, Brevo, Google Calendar, Mollie, social media APIs)
- `.db.env`: Database credentials

### Important Files

- `docs/association-refactor-checklist.md`: Guidelines for refactoring entity associations (follow this pattern)
- `api/openapi-overrides/`: Manual overrides for generated Brevo client
- `openapi/`: OpenAPI specifications (blueshell.json auto-generated, discord.json manual)

## Development Workflow

1. Make backend changes
2. Run tests: `./gradlew :api:test`
3. If API contracts changed, regenerate OpenAPI client: `./generate_openapi.sh`
4. Test frontend integration
5. Commit changes with descriptive message

## Common Pitfalls

- Don't put business logic in mappers or validators - keep it in services/domain
- Don't use `*_id` shadow fields when JPA manages the association
- Always enforce bidirectional consistency when updating associations
- Commands should be immutable data classes with validation, no behavior
- Services resolve associations explicitly, never use `asRef()` patterns
- ArchUnit tests will fail if layering is violated - respect the architecture