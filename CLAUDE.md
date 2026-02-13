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
- Commands include Jakarta validation annotations (field-level: `@NotNull`, `@NotBlank`; class-level: `@UniqueUserCommand`)
- Command handlers in `application/command/` implement business logic
- `CommandBus` automatically validates all commands before execution using Jakarta Bean Validation
- Controllers map web requests to commands using Mappie and dispatch via CommandBus
- Validation flows: Request DTO → Web validators → Command → CommandBus → Application validators → Handler

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
- Replaces MapStruct for mapping DTOs (Mappie 2.3.10)
- Used at API boundaries: Request→Command and Entity→Response mappings
- All mappers are `object` singletons extending `ObjectMappie<From, To>()`
- Extension functions provide clean API (`.asCommand()`, `.asResponse()`)
- Command→Entity mapping is manual in handlers (or via factories)
- No `@AfterMapping` or hidden business logic in mappers
- Mappers live in `web/mapping/` package, never in controllers or services

**Testing**
- ArchUnit tests enforce layering and architectural rules (`architecture/` package)
- Unit tests for validators, factories, and DTOs
- Service tests verify association wiring and error handling
- Testcontainers for integration tests with MariaDB

## Validation Architecture

Validation is strategically distributed across layers following DDD principles. Each layer has specific validation responsibilities.

### Layer Responsibilities

**Web Layer** (`web/validation/`)
- **Structural validation**: Format, presence, size, regex patterns
- **Type safety**: Enum validation, type checking
- **Simple business logic**: Single-field rules that don't require external dependencies
- **Examples**: `@CountryCode`, `@ValidMobilePhoneNumber`, `@FileSize`, `@AllowedContentTypes`

**Application Layer** (`application/validation/`)
- **Business rules**: Complex validation requiring database access
- **Cross-field validation**: Multi-property uniqueness checks
- **Cross-aggregate validation**: Rules spanning multiple entities
- **Examples**: `@UniqueUserCommand`, `@ValidEventSignUpCommand`, `@NoExistingMembershipForUser`

**Command Layer** (`command/`)
- **Field-level validation**: Jakarta Bean Validation annotations on command properties
- **Class-level validation**: Custom annotations for complex command validation
- **Validation interfaces**: Marker interfaces for validators (e.g., `UserUniquenessCandidate`)

**Domain Services** (`domain/service/`)
- **Domain invariant enforcement**: Core business rules
- **Complex business logic validation**: Multi-step validation with business semantics
- **Example**: `RecoveryTokenValidator` validates token format, expiry, consumption status

### Validation Types and Placement

| Validation Type | Layer | Dependency | Example |
|----------------|-------|------------|---------|
| Format (regex, length) | Web | None | `@Pattern`, `@Size`, `@Email` |
| Presence/nullability | Web/Command | None | `@NotNull`, `@NotBlank` |
| Type safety | Web | None | `@CountryCode`, `@ValidMobilePhoneNumber` |
| File constraints | Web | None | `@FileSize`, `@AllowedContentTypes` |
| Simple business logic | Web | None | `@GuestOrUserRequired` (either/or) |
| Uniqueness checks | Application | UserService | `@UniqueUsername` |
| Multi-field uniqueness | Application | Services | `@UniqueUserCommand` |
| Cross-aggregate rules | Application | Multiple Services | `@ValidEventSignUpCommand` |
| Domain invariants | Domain Service | Repositories | Token expiry, state transitions |

### Validation Flow

```
HTTP Request
    ↓
Controller (@Valid on Request DTO)
    ↓
Web Layer Validators
- Structural validation (format, presence, size)
- Simple business rules (no DB access)
    ↓
Mapper (Request → Command)
    ↓
CommandBus.dispatch()
    ↓
Jakarta Bean Validation on Command
- Field-level validation annotations
- Class-level custom validators
    ↓
Application Layer Validators
- Database-dependent business rules
- Cross-aggregate validation
    ↓
Command Handler
- Execute business logic (no validation)
    ↓
Domain Service (optional defensive validation)
- Domain invariant enforcement
- Complex business rule validation
    ↓
Repository/Database
```

### Best Practices

**DO:**
- ✅ Use Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`) on Request DTOs for structural validation
- ✅ Use custom validators in `web/validation/` for format validation without DB access
- ✅ Use custom validators in `application/validation/` for business rules requiring services
- ✅ Add field-level validation to commands for critical properties
- ✅ Use class-level annotations for multi-field validation on commands
- ✅ Let CommandBus handle all command validation automatically
- ✅ Implement domain services for complex business logic validation

**DON'T:**
- ❌ Put business logic requiring DB access in web layer validators
- ❌ Make application validators depend on DTO types (use command interfaces instead)
- ❌ Skip field-level validation on commands assuming DTOs are always valid
- ❌ Perform validation in command handlers (validation should happen before)
- ❌ Couple web layer to application layer through validator dependencies

### Anti-patterns to Avoid

**Anti-pattern: Business Validation in Web DTOs**
```kotlin
// ❌ WRONG: Business logic in web layer
@ValidEventSignUpCommand  // This validator accesses EventService
data class EventSignUpDTO(
    val eventId: Long?
)
```

**Better: Validation on Command**
```kotlin
// ✅ CORRECT: Business validation on command
@ValidEventSignUpCommand
data class CreateEventSignUpCommand(
    val dto: EventSignUpDTO
) : Command<EventSignUp>
```

**Anti-pattern: No Field Validation on Commands**
```kotlin
// ❌ WRONG: No validation, assumes DTO is always valid
data class CreateUserCommand(
    val username: String?,  // Could be null or blank
    val email: String?
) : Command<User>
```

**Better: Field Validation on Commands**
```kotlin
// ✅ CORRECT: Defensive validation
@UniqueUserCommand
data class CreateUserCommand(
    @field:NotBlank(message = "Username is required")
    val username: String?,

    @field:NotBlank
    @field:Email
    val email: String?
) : Command<User>
```

## Mapping Architecture

Mapping responsibilities are clearly separated by layer. Mappie is used at API boundaries, while internal domain logic uses manual mapping or factory patterns.

### Mapping Responsibilities by Layer

**Web Layer** (`web/mapping/`)
- **Request → Command**: Maps external API contracts to internal commands
- **Entity/Model → Response**: Maps domain models to external API responses
- **Tool**: Mappie 2.3.10 with extension functions

**Application Layer** (`application/command/`)
- **Command → Entity**: Creates/updates entities from commands in handlers
- **Tool**: Manual property assignment (current) or factories (recommended)

**Domain Layer** (`domain/`)
- **Aggregate methods**: Entity updates through domain logic
- **Factories**: Complex entity creation (`application/factory/`)

### Mappie Usage Guidelines

**Pattern: Request → Command**
```kotlin
// File: domain/auth/web/mapping/AuthCommandMappings.kt
object JwtRequestToCommandMapper : ObjectMappie<JwtRequest, AuthenticateCommand>() {
    override fun map(from: JwtRequest) = mapping {
        AuthenticateCommand::username fromValue { from.username!! }
        AuthenticateCommand::password fromValue { from.password!! }
    }
}

fun JwtRequest.asCommand(): AuthenticateCommand = JwtRequestToCommandMapper.map(this)
```

**Pattern: Entity → Response**
```kotlin
// File: domain/user/web/mapping/UserMappings.kt
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::roles fromProperty from::inheritedRoles
    }
}

fun User.asDetailResponse(): UserDetailResponse = UserToDetailResponseMapper.map(this)
```

**Pattern: Complex Mapping with Context**
```kotlin
// When additional context is needed
private data class BlogResponseSource(val blog: Blog, val frontendUrl: String)

object BlogResponseSourceToBlogResponseMapper : ObjectMappie<BlogResponseSource, BlogResponse>() {
    override fun map(from: BlogResponseSource) = mapping {
        BlogResponse::url fromValue { "${from.frontendUrl}/blogs/${from.blog.id}" }
    }
}

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponseSourceToBlogResponseMapper.map(BlogResponseSource(this, frontendUrl))
```

### Command → Entity Mapping

**Current Pattern: Direct Property Assignment**

Most command handlers use direct property assignment:
```kotlin
// Current implementation in most handlers
@Component
class CreateUserHandler(
    private val service: UserService,
    private val addresses: AddressService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {
    override fun handle(command: CreateUserCommand): User {
        var user = User()
        user.username = command.username
        user.email = command.email
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber
        command.addressId?.let { user.address = addresses.findById(it) }
        user.password = passwordEncoder.encode(command.password)
        return service.create(user)
    }
}
```

**Recommended: Factory Pattern**

For complex entity creation, use factories:
```kotlin
// Example: RecoveryTokenFactory (existing pattern)
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = generateSelector()
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)
        repository.save(token)
        return "$selector.$verifier"
    }
}
```

**Recommended: Aggregate Methods**

For entity updates, prefer aggregate methods over direct mutation:
```kotlin
// Example: Committee.replaceMembers()
class UpdateCommitteeHandler(...) : CommandHandler<UpdateCommitteeCommand, Committee> {
    override fun handle(command: UpdateCommitteeCommand): Committee {
        var committee = service.findById(command.id)
        committee.name = command.name
        committee.description = command.description
        committee.replaceMembers(mapMembers(command.members))  // Aggregate method
        return service.update(committee)
    }
}
```

### Best Practices

**DO:**
- ✅ Use Mappie for all Request→Command and Entity→Response mappings
- ✅ Create extension functions (`.asCommand()`, `.asResponse()`) for clean API
- ✅ Use `object` singletons for Mappie mappers (stateless)
- ✅ Use helper functions consistently across domains for complex mappings
- ✅ Consider factory pattern for complex entity creation
- ✅ Use aggregate methods for entity updates when business logic is involved
- ✅ Keep mapping logic in `web/mapping/` package

**DON'T:**
- ❌ Mix Mappie and manual mapping inconsistently
- ❌ Put mapping logic in controllers or services
- ❌ Directly mutate entities when aggregate methods should encapsulate the logic
- ❌ Create ad-hoc factories in handlers; use dedicated factory classes
- ❌ Skip helper functions when the same pattern repeats across handlers

### Anti-patterns to Avoid

**Anti-pattern: Inconsistent Entity Creation**
```kotlin
// ❌ Domain A uses direct assignment
class CreateUserHandler(...) {
    override fun handle(command: CreateUserCommand): User {
        var user = User()
        user.username = command.username
        // ... 15 lines of property assignment
    }
}

// ❌ Domain B uses helper function
class CreateEventHandler(...) {
    override fun handle(command: CreateEventCommand): Event {
        val event = Event()
        applyEventFields(event, command)  // Different pattern
    }
}
```

**Better: Consistent Factory Pattern**
```kotlin
// ✅ RECOMMENDED: Consistent factory pattern
@Component
class UserFactory(private val passwordEncoder: PasswordEncoder) {
    fun createFromCommand(command: CreateUserCommand): User {
        return User().apply {
            username = command.username
            email = command.email
            password = passwordEncoder.encode(command.password)
        }
    }
}

class CreateUserHandler(private val factory: UserFactory) {
    override fun handle(command: CreateUserCommand): User {
        val user = factory.createFromCommand(command)
        return service.create(user)
    }
}
```

**Anti-pattern: Direct Entity Mutation**
```kotlin
// ❌ WRONG: Direct mutation bypasses domain logic
class UpdateCommitteeHandler(...) {
    override fun handle(command: UpdateCommitteeCommand): Committee {
        val committee = service.findById(command.id)
        committee.members.clear()
        committee.members.addAll(newMembers)  // No bidirectional consistency!
    }
}
```

**Better: Aggregate Method**
```kotlin
// ✅ CORRECT: Aggregate method ensures invariants
class UpdateCommitteeHandler(...) {
    override fun handle(command: UpdateCommitteeCommand): Committee {
        val committee = service.findById(command.id)
        committee.replaceMembers(mapMembers(command.members))  // Maintains consistency
        return service.update(committee)
    }
}
```

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

### General Architecture
- Don't put business logic in mappers or validators - keep it in services/domain
- Don't use `*_id` shadow fields when JPA manages the association
- Always enforce bidirectional consistency when updating associations
- Commands should be immutable data classes with validation, no behavior
- Services resolve associations explicitly, never use `asRef()` patterns
- ArchUnit tests will fail if layering is violated - respect the architecture

### Validation Pitfalls
- Don't put business validation annotations on Request DTOs (web layer) - use command-level validators instead
- Don't let application validators depend on DTO types - use command interfaces (e.g., `UserUniquenessCandidate`)
- Always add `@NotNull`/`@NotBlank` to critical command fields for defensive validation
- Don't skip CommandBus validation by calling services directly
- Don't perform validation logic in command handlers - validation should happen before execution
- Don't access external services (DB, APIs) in web layer validators

### Mapping Pitfalls
- Don't mix Mappie and manual mapping inconsistently across domains
- Don't put mapping logic in controllers or services - keep it in `web/mapping/` package
- Prefer aggregate methods over direct entity property mutation
- Use factories for complex entity creation instead of inline construction
- Keep helper functions consistent across domains (e.g., `applyIdentityFields`)
- Don't create bidirectional associations manually - use entity helper methods (e.g., `replaceMembers()`)
- Always use extension functions (`.asCommand()`, `.asResponse()`) rather than calling mappers directly