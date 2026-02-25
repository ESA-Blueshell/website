# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Full-stack web application for managing student association activities (ESA Blueshell). Spring Boot 3.x backend (Kotlin) with Vue.js 3 frontend (TypeScript), containerized with Docker.

**Architecture:** Multi-layered Domain-Driven Design (DDD) with clean architecture principles, enforced by ArchUnit tests.

## Quick Start Commands

### Backend (API)

```bash
# Run application with Docker
docker compose -f docker-compose.dev.yml up api

# Run all tests
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test

# Run specific domain tests
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test --tests "net.blueshell.api.domain.auth.*"

# Run architecture tests (ArchUnit)
docker compose -f docker-compose.dev.yml run api ./gradlew :api:test --tests "net.blueshell.api.architecture.*"

# Build locally
./gradlew :api:build

# Generate OpenAPI spec and TypeScript client
./generate_openapi.sh
```

### Frontend

```bash
cd frontend

# Install dependencies
yarn install

# Development server (from project root)
docker compose -f docker-compose.dev.yml up frontend

# Build for production
yarn build

# Lint and typecheck
yarn lint
yarn typecheck

# Generate API clients
yarn gen:blueshell    # Generate from backend OpenAPI spec
yarn gen:all          # Generate all clients
```

### Docker

```bash
# Development environment (hot reload)
./run-dev.sh
# or
docker compose -f docker-compose.dev.yml up --build

# Production environment
./run.sh
# or
docker compose -f docker-compose.yml up --build -d
```

## Architecture Overview

The backend follows **clean architecture** and **domain-driven design** principles with strict layer separation. Each domain (e.g., `auth`, `user`, `event`, `committee`) is a self-contained bounded context.

**Core Principles:**
- **Package by Feature**: Each domain is independent with clear boundaries
- **Layer Independence**: Commands are independent; persistence doesn't depend on web
- **Dependency Rule**: Dependencies point inward (web → command → application → domain → persistence)
- **ArchUnit Enforcement**: Automated tests prevent architectural violations

**📚 For detailed architecture documentation, see:** [`docs/adr/ADR-INDEX.md`](docs/adr/ADR-INDEX.md)

## Layer Structure

Each domain follows this standardized structure:

```
domain/{domain-name}/
├── web/                    # Presentation Layer
│   ├── *Controller.kt      # REST endpoints
│   ├── dto/                # Request/Response DTOs
│   │   ├── request/        # Inbound DTOs
│   │   └── response/       # Outbound DTOs
│   ├── mapping/            # Mappie mappers (DTO ↔ Command/Entity)
│   └── validation/         # Web-layer validators (format, structure)
├── command/                # Command Layer
│   ├── *Commands.kt        # Command objects (immutable use case DTOs)
│   └── validation/         # Command-specific constraint annotations
├── application/            # Application Layer
│   ├── command/            # Command handlers (business logic)
│   │   └── *CommandHandlers.kt
│   ├── *Service.kt         # Application services (orchestration)
│   ├── event/              # Domain events (UserCreated, etc.)
│   ├── listener/           # Event listeners (cross-domain reactions)
│   ├── query/              # Query objects for dynamic searches
│   ├── factory/            # Entity factories (complex creation)
│   ├── validation/         # Business rule validators (DB access)
│   └── exception/          # Application exceptions
├── domain/                 # Domain Layer (optional - for complex domains)
│   ├── model/              # Rich domain models (business behavior)
│   └── service/            # Domain services (pure business logic)
└── persistence/            # Persistence Layer
    ├── *Entity.kt          # JPA entities
    ├── repository/         # Spring Data repositories
    └── spec/               # JPA Specifications (dynamic queries)
```

**Infrastructure Layer** (cross-cutting concerns):
```
infrastructure/
└── security/
    └── permission/         # Permission evaluators (Spring Security)
        ├── UserPermission.kt
        └── EventPermission.kt

platform/
└── integration/
    ├── email/              # Email templates and services
    ├── calendar/           # Google Calendar integration
    └── jobs/               # Job dispatching (RabbitMQ)
```

**📚 ADR References:**
- **[ADR-001](docs/adr/api/ADR-001-multi-layered-domain-driven-architecture.md)**: Complete layer structure and responsibilities
- **[ADR-016](docs/adr/api/ADR-016-layer-dependency-rules.md)**: Layer dependency rules and violations
- **[ADR-022](docs/adr/api/ADR-022-platform-infrastructure-shared-organization.md)**: Platform, infrastructure, and shared organization

## Platform, Infrastructure, and Shared Organization

The API module distinguishes three cross-cutting layers with specific responsibilities:

### Shared Kernel (`shared/`)
**Purpose**: Cross-cutting contracts and abstractions used by all domains
**Contents**: Command/Event infrastructure, base entities, job definitions, email content DTOs
**Dependencies**: None (innermost layer - only Java/Kotlin stdlib)
**Key Principle**: Defines interfaces and contracts, NOT implementations

**Example:**
```kotlin
// shared/email/EmailContent.kt - Contract between domains and platform
data class EmailContent(
    val recipientEmail: String,
    val subject: String,
    val markdownContent: String
)

// shared/job/JobDefinitions.kt - Job contracts
object CalendarJobs {
    data class AddEvent(val eventId: Long)
}
```

### Infrastructure (`infrastructure/`)
**Purpose**: Spring Security and framework-specific infrastructure
**Contents**: JWT authentication, permission evaluators, security filters
**Dependencies**: Can depend on any layer (adapter pattern)
**Key Principle**: Framework-specific, domain-aware implementations

**Example:**
```kotlin
// infrastructure/security/permission/UserPermission.kt
@Component
class UserPermission(userService: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(userService) {
    // Spring Security PermissionEvaluator
}
```

### Platform (`platform/`)
**Purpose**: External integrations and application configuration
**Contents**: Anti-Corruption Layers (email, calendar, payments), Spring configuration, job queue
**Dependencies**: Implements shared/ interfaces, adapts external APIs
**Key Principle**: Isolates domains from external system details

**Example:**
```kotlin
// platform/integration/email/service/EmailService.kt
@Service
class EmailService(private val brevoClient: BrevoClient) {
    fun sendEmail(emailContent: EmailContent) {
        // Adapts EmailContent to Brevo API
        brevoClient.send(emailContent.toBrevoRequest())
    }
}
```

### Decision Tree: Where Does X Go?

1. **Is it a contract between domains?** → `shared/`
   - Examples: JobDefinition, EmailContent, Command, Event

2. **Is it Spring Security infrastructure?** → `infrastructure/security/`
   - Examples: JWT utilities, permission evaluators, auth filters

3. **Is it an external system adapter?** → `platform/integration/{system}/`
   - Examples: GoogleCalendarAdapter, BrevoEmailService, MolliePaymentClient

4. **Is it Spring configuration?** → `platform/config/`
   - Examples: SecurityConfig, JpaConfig, RabbitMqConfig

**📚 See**: [ADR-022](docs/adr/api/ADR-022-platform-infrastructure-shared-organization.md) for detailed guidance and examples

---

## Key Architectural Patterns

### 1. Command Pattern with CommandBus

**Commands** are immutable data classes representing use cases. They must be **independent** of application and web layers.

**Pattern:**
```kotlin
// domain/user/command/UserCommands.kt
@UniqueUserCommand  // Business rule validator (application layer)
data class CreateUserCommand(
    @field:NotBlank(message = "Username is required")
    val username: String?,

    @field:Email
    val email: String?
) : Command<User>
```

**Flow:**
```
Controller → Mapper → Command → CommandBus → Validator → Handler → Service
```

**Rules:**
- ✅ Commands are in `command/` package
- ✅ Commands have field-level validation (`@NotNull`, `@NotBlank`, `@Size`)
- ✅ CommandBus validates all commands automatically
- ❌ Commands MUST NOT import from `application/` or `web/` layers
- ❌ Commands MUST NOT import web DTOs

**📚 See:** [ADR-002: Command Pattern](docs/adr/api/ADR-002-command-pattern-with-command-bus.md)

---

### 2. Validation Layer Separation

Validation is distributed across layers with specific responsibilities:

| Layer | Responsibility | Examples | DB Access |
|-------|----------------|----------|-----------|
| **Web** | Format, structure, presence | `@Email`, `@Size`, `@CountryCode` | ❌ No |
| **Command** | Field-level validation | `@NotNull`, `@NotBlank` | ❌ No |
| **Application** | Business rules | `@UniqueUsername`, `@ValidEventSignUpCommand` | ✅ Yes |
| **Domain** | Invariant enforcement | Token expiry, state transitions | ✅ Yes |

**Flow:**
```
HTTP Request
    ↓
Controller (@Valid on DTO) → Web validators (format)
    ↓
Mapper (DTO → Command)
    ↓
CommandBus → Jakarta validation (field-level)
    ↓
Application validators (business rules)
    ↓
Handler → Domain service (invariants)
    ↓
Repository
```

**Rules:**
- ✅ Web validators: NO database access, structural only
- ✅ Application validators: Can access services and repositories
- ✅ Add field validation to commands (defensive programming)
- ❌ Don't put business logic in web layer validators
- ❌ Don't skip validation by calling services directly

**📚 See:** [ADR-003: Validation Layer Separation](docs/adr/api/ADR-003-validation-layer-separation.md)

---

### 3. Manual Object Mapping with Extension Functions

**Pattern:** Manual mapping with extension functions for all DTO ↔ Command and Entity ↔ Response conversions.

**Request to Command Mapping:**
```kotlin
// domain/user/web/mapping/request/UserRequestMappings.kt
fun CreateUserRequest.asCommand(): CreateUserCommand =
    CreateUserCommand(
        username = this.username,
        email = this.email
    )

// Usage in controller
@PostMapping
fun createUser(@Valid @RequestBody request: CreateUserRequest): UserResponse {
    val user = commandBus.dispatch(request.asCommand())
    return user.asResponse()
}
```

**Entity to Response Mapping:**
```kotlin
// domain/committee/web/mapping/response/CommitteeResponseMappings.kt
fun Committee.asDetailResponse(): CommitteeDetailResponse =
    CommitteeDetailResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        members = this.members.map { it.asDto() }.toMutableList(),
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun CommitteeMember.asDto(): CommitteeMemberResponse =
    CommitteeMemberResponse(
        userId = this.userId,
        committeeId = this.committeeId,
        role = this.role!!,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
```

**Mapping Responsibilities:**
- **Web → Command**: Extension functions in `web/mapping/request/`
- **Command → Entity**: Manual in handlers or factories
- **Entity → Response**: Extension functions in `web/mapping/response/`

**Rules:**
- ✅ Use extension functions (`.asCommand()`, `.asResponse()`, `.asDto()`)
- ✅ Keep mapping logic in `web/mapping/` package (separate request/response subdirectories)
- ✅ Map at API boundaries (controllers map requests to commands, responses to DTOs)
- ✅ Use straightforward property assignment
- ❌ Don't put mapping logic in controllers or services
- ❌ Don't hide complex transformations in mappings

**📚 See:** [ADR-004: Manual Mapping at API Boundaries](docs/adr/api/ADR-004-manual-mapping-at-api-boundaries.md)

---

### 4. Entity Association Management

**Pattern:** Entity references are the single source of truth, with computed ID properties.

**Many-to-One (Required):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "committee_id", nullable = false)
var committee: Committee = committee
    private set

val committeeId: Long
    get() = committee.id  // Computed, no @Column
```

**Aggregate Methods:**
```kotlin
fun replaceMembers(newMembers: List<CommitteeMember>) {
    _members.clear()
    newMembers.forEach { it.committee = this }
    _members.addAll(newMembers)
}
```

**Rules:**
- ✅ Use entity references in business logic
- ✅ Make ID properties computed (no `@Column`)
- ✅ Use `getReferenceById()` for lazy loading
- ✅ Use aggregate methods for bidirectional updates
- ❌ Don't set ID fields directly
- ❌ Don't add `@Column` to computed ID properties
- ❌ Don't clear/add collections directly (use aggregate methods)

**📚 See:** [ADR-013: Entity Association Pattern](docs/adr/api/ADR-013-entity-association-pattern.md)

---

### 5. Event-Driven Architecture

**Domain events** enable loose coupling between domains.

**Publishing:**
```kotlin
@Service
class UserService(
    private val repository: UserRepository,
    private val events: AfterCommitEventPublisher
) {
    @Transactional
    override fun create(entity: User): User {
        val saved = repository.save(entity)
        events.publish(UserCreated(saved.id!!, createdByBoard = isBoardUser()))
        return saved
    }
}
```

**Listening:**
```kotlin
@Component
class RecoveryEventListener(
    private val activationService: UserActivationService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        activationService.issueActivationForNewUser(event.userId)
    }
}
```

**Event Location:**
- **Events**: `domain/{domain}/application/event/`
- **Listeners**: In the domain that **reacts** to events

**Rules:**
- ✅ Use past tense: `UserCreated`, `PasswordChanged`
- ✅ Events are immutable data classes
- ✅ Use `AfterCommitEventPublisher` for transactional safety
- ✅ Place listeners in the domain that reacts
- ❌ Don't include full entities in events (IDs only)
- ❌ Don't depend on listener execution order

**📚 See:** [ADR-006: Event-Driven Architecture](docs/adr/api/ADR-006-event-driven-architecture.md)

---

### 6. JPA Specifications and Query Objects

**Pattern:** Dynamic queries use query objects in application layer, not filters in persistence.

**Query Object (Application Layer):**
```kotlin
// domain/user/application/query/UserQuery.kt
data class UserQuery(
    val username: String? = null,
    val isMember: Boolean? = null,
    val enabled: Boolean? = null
)
```

**Specification (Persistence Layer):**
```kotlin
// domain/user/persistence/spec/UserSpecifications.kt
object UserSpecifications {
    fun fromQuery(query: UserQuery): Specification<User> {
        var spec = Specification<User> { _, _, cb -> cb.conjunction() }

        query.username?.let {
            spec = spec.and(usernameContains(it))
        }

        query.isMember?.let {
            spec = spec.and(hasMemberRole(it))
        }

        return spec
    }
}
```

**Web Layer Mapping:**
```kotlin
// Controller receives HTTP params, maps to query object
@GetMapping("/search")
fun search(
    @RequestParam username: String?,
    @RequestParam isMember: Boolean?,
    pageable: Pageable
): Page<UserResponse> {
    val query = UserQuery(username = username, isMember = isMember)
    return userService.findAllByQuery(query, pageable)
        .map { it.asResponse() }
}
```

**Data Flow:**
```
HTTP Params → Query Object (application) → Specification (persistence) → Database
```

**Rules:**
- ✅ Query objects in `application/query/`
- ✅ Specifications in `persistence/spec/`
- ✅ Web layer maps params → query objects
- ❌ Don't put query objects in persistence layer
- ❌ Don't pass web DTOs to specifications

**📚 See:** [ADR-015: JPA Specifications and Dynamic Queries](docs/adr/api/ADR-015-jpa-specifications-dynamic-queries.md)

---

### 7. Permission Evaluation (Infrastructure Layer)

**Permission evaluators** are Spring Security infrastructure components, NOT web layer concerns.

**Location:** `infrastructure/security/permission/`

**Pattern:**
```kotlin
// infrastructure/security/permission/UserPermission.kt
@Component
class UserPermission(userService: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(userService) {

    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        val user = entity as User
        val principal = SecurityUtils.principalFrom(authentication)
        return when (permission) {
            "read", "write" -> (principal?.id == user.id)
            else -> false
        }
    }
}
```

**Usage in Controllers:**
```kotlin
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'User', 'write')")
fun update(@PathVariable id: Long, @RequestBody request: UpdateUserRequest): UserResponse {
    val user = commandBus.dispatch(request.asCommand(id))
    return user.asResponse()
}
```

**Rules:**
- ✅ Place in `infrastructure/security/permission/`
- ✅ Extend `BasePermissionEvaluator`
- ✅ Use with `@PreAuthorize` annotations
- ❌ Don't put in web layer (not a presentation concern)
- ❌ Don't put complex business logic in evaluators

**📚 See:** [ADR-014: Permission Evaluation Strategy](docs/adr/api/ADR-014-permission-evaluation-strategy.md)

---

### 8. Factory Pattern for Entity Creation

**Use factories** for complex entity creation with security concerns or multi-step logic.

**Pattern:**
```kotlin
// domain/auth/application/factory/RecoveryTokenFactory.kt
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    fun issue(user: User, type: ResetType, ttl: Duration): RecoveryDispatch {
        val selector = generateSelector()
        val verifier = generateVerifier()

        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = selector
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)

        repository.save(token)

        return RecoveryDispatch(
            rawToken = "$selector.$verifier",
            userId = user.id!!,
            type = type
        )
    }
}
```

**When to Use:**
- ✅ Complex creation logic
- ✅ Security concerns (hashing, encoding)
- ✅ Multi-step creation process
- ✅ Entity requires validation/generation

**When NOT to Use:**
- ❌ Simple CRUD operations
- ❌ Direct property assignment suffices

**📚 See:** [ADR-005: Factory Pattern for Entity Creation](docs/adr/api/ADR-005-factory-pattern-for-entity-creation.md)

---

## Layer Dependency Rules

**The Dependency Rule:** Dependencies point inward. Inner layers know nothing about outer layers.

```
┌─────────────────────────────────────┐
│   Web Layer (Controllers, DTOs)     │  ← Outermost
├─────────────────────────────────────┤
│   Command Layer (Independent!)      │
├─────────────────────────────────────┤
│   Application Layer (Services)      │
├─────────────────────────────────────┤
│   Domain Layer (Business Logic)     │  ← Core
├─────────────────────────────────────┤
│   Persistence Layer (Entities)      │
└─────────────────────────────────────┘

Infrastructure (Security, Email, Jobs) ← Adapters, can depend on any layer
```

**Critical Rules:**
- ❌ **Commands MUST NOT import from application or web layers**
- ❌ **Persistence MUST NOT import from web layer**
- ❌ **Commands MUST NOT import web DTOs**
- ✅ Web can import: command, application, persistence
- ✅ Application can import: command, persistence, domain
- ✅ Infrastructure can import: any layer (adapter pattern)

**ArchUnit Enforcement:**
```kotlin
@ArchTest
fun `commands must not depend on application layer`(classes: JavaClasses) {
    noClasses()
        .that().resideInAPackage("..command..")
        .should().dependOnClassesThat()
        .resideInAPackage("..application..")
        .check(classes)
}
```

**📚 See:** [ADR-016: Layer Dependency Rules](docs/adr/api/ADR-016-layer-dependency-rules.md)

---

## Common Pitfalls and Anti-Patterns

### ❌ Commands Importing Application Layer

**WRONG:**
```kotlin
// domain/user/command/UserCommands.kt
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand

@UniqueUserCommand  // ❌ Application layer dependency!
data class CreateUserCommand(...)
```

**CORRECT:**
```kotlin
// Move annotation to command layer or shared
// shared/validation/UniqueUserCommand.kt
@Constraint(validatedBy = [UniqueUserCommandValidator::class])
annotation class UniqueUserCommand
```

---

### ❌ Commands Importing Web DTOs

**WRONG:**
```kotlin
// domain/event/command/EventCommands.kt
import net.blueshell.api.domain.event.web.dto.request.SurveyRequest

data class CreateEventCommand(
    val survey: SurveyRequest  // ❌ Web DTO in command!
)
```

**CORRECT:**
```kotlin
// Create domain object in command layer
data class SurveyData(val questions: List<QuestionData>)

data class CreateEventCommand(
    val survey: SurveyData  // ✅ Domain object
)

// Web layer maps SurveyRequest → SurveyData
```

---

### ❌ Query Filters in Persistence Layer

**WRONG:**
```kotlin
// domain/user/persistence/filter/UserFilter.kt  ❌ Wrong location!
class UserFilter {
    var username: String?  // HTTP query param - web concern!
}
```

**CORRECT:**
```kotlin
// domain/user/application/query/UserQuery.kt  ✅ Application layer
data class UserQuery(
    val username: String?
)

// Web layer maps HTTP params → UserQuery
```

---

### ❌ Permission Evaluators in Web Layer

**WRONG:**
```kotlin
// domain/user/web/permission/UserPermission.kt  ❌ Wrong location!
@Component
class UserPermission : BasePermissionEvaluator<...> { }
```

**CORRECT:**
```kotlin
// infrastructure/security/permission/UserPermission.kt  ✅ Infrastructure
@Component
class UserPermission : BasePermissionEvaluator<...> { }
```

---

### ❌ Business Validation in Web Layer

**WRONG:**
```kotlin
// Web DTO with business rule validator
@ValidEventSignUpCommand  // ❌ Accesses EventService - business logic!
data class EventSignUpRequest(...)
```

**CORRECT:**
```kotlin
// Validation on command, not DTO
@ValidEventSignUpCommand  // ✅ Application layer validator
data class CreateEventSignUpCommand(...) : Command<EventSignUp>
```

---

### ❌ Email Templates in Application Layer

**WRONG:**
```kotlin
// domain/auth/application/email/PasswordResetEmail.kt  ❌ Infrastructure leak
class PasswordResetEmail { }
```

**CORRECT:**
```kotlin
// platform/integration/email/templates/PasswordResetEmail.kt  ✅ Infrastructure
class PasswordResetEmail { }

// Application publishes event, platform listens and sends email
```

---

### ❌ Direct Entity Mutation Without Aggregate Methods

**WRONG:**
```kotlin
// Direct mutation bypasses domain logic
committee.members.clear()
committee.members.addAll(newMembers)  // ❌ No bidirectional consistency!
```

**CORRECT:**
```kotlin
// Aggregate method ensures invariants
committee.replaceMembers(newMembers)  // ✅ Maintains consistency
```

---

### ❌ No Field Validation on Commands

**WRONG:**
```kotlin
data class CreateUserCommand(
    val username: String?,  // ❌ Could be null or blank!
    val email: String?
) : Command<User>
```

**CORRECT:**
```kotlin
data class CreateUserCommand(
    @field:NotBlank(message = "Username is required")
    val username: String?,

    @field:Email
    @field:NotBlank
    val email: String?
) : Command<User>
```

---

## Development Workflow

### Making Changes

1. **Backend Changes:**
   ```bash
   # Make changes to domain
   # Run tests
   ./gradlew :api:test

   # Run architecture tests
   ./gradlew :api:test --tests "net.blueshell.api.architecture.*"
   ```

2. **API Contract Changes:**
   ```bash
   # Regenerate OpenAPI spec and TypeScript client
   ./generate_openapi.sh

   # Frontend will now have updated API client
   ```

3. **Database Schema Changes:**
   ```bash
   # Create new Flyway migration
   # api/src/main/resources/db/migration/V{n}__description.sql

   # Restart backend - Flyway runs automatically
   docker compose -f docker-compose.dev.yml restart api
   ```

### Creating New Features

1. **Create domain package structure:**
   ```
   domain/mynewdomain/
   ├── web/
   ├── command/
   ├── application/
   └── persistence/
   ```

2. **Define commands:**
   ```kotlin
   // command/MyDomainCommands.kt
   data class CreateMyEntityCommand(...) : Command<MyEntity>
   ```

3. **Create handlers:**
   ```kotlin
   // application/command/MyDomainCommandHandlers.kt
   @Component
   class CreateMyEntityHandler(...) : CommandHandler<CreateMyEntityCommand, MyEntity>
   ```

4. **Create controller:**
   ```kotlin
   // web/MyDomainController.kt
   @RestController
   @RequestMapping("/mydomain")
   class MyDomainController(private val commandBus: CommandBus)
   ```

5. **Add ArchUnit tests** to verify architectural compliance

### Committing Changes

Follow the git commit guidelines in CLAUDE.md:
- Create feature branch
- Make atomic commits
- Run tests before committing
- Use descriptive commit messages
- Reference ADRs when making architectural decisions

---

## Technology Stack

### Backend
- **Language**: Kotlin 2.3.10 with Java 24 toolchain
- **Framework**: Spring Boot 4.0.3 (Web, Security, Data JPA, AMQP)
- **Database**: MariaDB 10.11.10 with Flyway migrations
- **Security**: Spring Security with JWT (nimbus-jose-jwt, jjwt)
- **Mapping**: Mappie 2.3.10 for object mapping
- **API Docs**: SpringDoc OpenAPI 3 (Swagger UI)
- **Testing**: JUnit 5, Mockito Kotlin, MockK, ArchUnit, Testcontainers, REST Assured
- **Integrations**: Google Calendar API, Mollie (payments), Brevo (email campaigns)

### Frontend
- **Framework**: Vue.js 3.5.28 with TypeScript 5.9.3
- **UI**: Vuetify 3.12.0 (Material Design)
- **State**: Vuex 4.1.0
- **Routing**: Vue Router 5.0.3
- **HTTP**: Axios 1.13.5 with OpenAPI-generated client
- **Build**: Vite 7.3.1
- **Validation**: VeeValidate 4.15.1
- **Testing**: Playwright 1.58.2, Vitest 4.0.18
- **Utilities**: Luxon (dates), Marked (Markdown), DOMPurify (XSS protection)

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Database**: MariaDB 10.11.10 (utf8mb4, Europe/Amsterdam timezone)
- **Message Queue**: RabbitMQ (job dispatching)
- **Reverse Proxy**: Nginx (SSL termination)

---

## Database

- **Engine**: MariaDB 10.11.10
- **Charset**: utf8mb4 with utf8mb4_unicode_ci collation
- **Timezone**: Europe/Amsterdam
- **Migrations**: Flyway (`api/src/main/resources/db/migration/`)
- **Connection (dev)**: `localhost:3307` (Docker) or `localhost:3306` (local)

**Migration Pattern:** `V{version}__{description}.sql`
```sql
-- V5__add_user_roles.sql
ALTER TABLE users ADD COLUMN roles JSON NOT NULL DEFAULT '[]';
```

---

## API Documentation

- **Swagger UI (dev)**: https://localhost/api/swagger-ui
- **Swagger UI (prod)**: https://esa-blueshell.nl/api/swagger-ui
- **OpenAPI Spec**: `/api/v3/api-docs`
- **Client Generation**: Run `./generate_openapi.sh` after API changes

---

## Environment Configuration

Required environment files in `env/`:
- **`.app.env`**: JWT secret, SMTP, Brevo, Google Calendar, Mollie, social APIs
- **`.db.env`**: Database credentials

---

## Architecture Decision Records (ADRs)

Architectural documentation is split by platform in `docs/adr/`:
- **API ADRs:** `docs/adr/api/ADR-INDEX.md`
- **Frontend ADRs:** `docs/adr/frontend/ADR-INDEX.md`
- **Umbrella index:** `docs/adr/ADR-INDEX.md`

### API ADR Highlights

- **[ADR-001](docs/adr/api/ADR-001-multi-layered-domain-driven-architecture.md)**: Multi-Layered DDD Architecture
- **[ADR-002](docs/adr/api/ADR-002-command-pattern-with-command-bus.md)**: Command Pattern with CommandBus
- **[ADR-013](docs/adr/api/ADR-013-entity-association-pattern.md)**: Entity Association Pattern
- **[ADR-016](docs/adr/api/ADR-016-layer-dependency-rules.md)**: Layer Dependency Rules

### Strategic Domain-Driven Design
- **[ADR-017](docs/adr/api/ADR-017-bounded-context-relationships-and-context-map.md)**: Bounded Context Relationships and Context Map
- **[ADR-018](docs/adr/api/ADR-018-data-ownership-in-modular-monolith.md)**: Data Ownership in Modular Monolith
- **[ADR-019](docs/adr/api/ADR-019-anti-corruption-layers-for-external-integration.md)**: Anti-Corruption Layers for External Integration
- **[ADR-020](docs/adr/api/ADR-020-shared-kernel-governance.md)**: Shared Kernel Governance
- **[ADR-021](docs/adr/api/ADR-021-observability-and-distributed-tracing.md)**: Observability and Distributed Tracing (Proposed)

### Data & Persistence
- **[ADR-007](docs/adr/api/ADR-007-repository-pattern-and-jpa.md)**: Repository Pattern and JPA
- **[ADR-010](docs/adr/api/ADR-010-database-migrations-with-flyway.md)**: Database Migrations with Flyway
- **[ADR-015](docs/adr/api/ADR-015-jpa-specifications-dynamic-queries.md)**: JPA Specifications and Dynamic Queries

### Validation & Mapping
- **[ADR-003](docs/adr/api/ADR-003-validation-layer-separation.md)**: Validation Layer Separation
- **[ADR-004](docs/adr/api/ADR-004-manual-mapping-at-api-boundaries.md)**: Manual Mapping at API Boundaries

### Patterns
- **[ADR-005](docs/adr/api/ADR-005-factory-pattern-for-entity-creation.md)**: Factory Pattern for Entity Creation
- **[ADR-006](docs/adr/api/ADR-006-event-driven-architecture.md)**: Event-Driven Architecture

### Security & API
- **[ADR-008](docs/adr/api/ADR-008-exception-handling-strategy.md)**: Exception Handling Strategy
- **[ADR-009](docs/adr/api/ADR-009-jwt-authentication-strategy.md)**: JWT Authentication Strategy
- **[ADR-012](docs/adr/api/ADR-012-api-documentation-with-openapi.md)**: API Documentation with OpenAPI
- **[ADR-014](docs/adr/api/ADR-014-permission-evaluation-strategy.md)**: Permission Evaluation Strategy

### Testing
- **[ADR-011](docs/adr/api/ADR-011-testing-strategy.md)**: Testing Strategy

### Frontend ADR Highlights
- **[ADR-001](docs/adr/frontend/ADR-001-domain-feature-architecture.md)**: Domain Feature Architecture
- **[ADR-002](docs/adr/frontend/ADR-002-api-client-boundary-and-domain-mapping.md)**: API Client Boundary and Domain Mapping
- **[ADR-003](docs/adr/frontend/ADR-003-state-management-and-server-data.md)**: State Management and Server Data Ownership
- **[ADR-004](docs/adr/frontend/ADR-004-form-validation-and-command-mapping.md)**: Form Validation and Command Mapping
- **[ADR-005](docs/adr/frontend/ADR-005-routing-and-authorization.md)**: Routing and Authorization
- **[ADR-006](docs/adr/frontend/ADR-006-component-and-composable-standards.md)**: Component and Composable Standards
- **[ADR-007](docs/adr/frontend/ADR-007-testing-and-quality-gates.md)**: Testing and Quality Gates

**📚 Full Index:** [`docs/adr/ADR-INDEX.md`](docs/adr/ADR-INDEX.md)

---

## Important Files & Resources

- **`docs/adr/ADR-INDEX.md`**: Umbrella architecture documentation index
- **`docs/adr/api/ADR-INDEX.md`**: API architecture decisions
- **`docs/adr/frontend/ADR-INDEX.md`**: Frontend architecture decisions
- **`AGENTS.md`**: Agent-facing summary aligned with this guide
- **`docs/association-refactor-checklist.md`**: Entity association refactoring guide
- **`api/openapi-overrides/`**: Manual overrides for generated Brevo client
- **`openapi/`**: OpenAPI specifications (blueshell.json auto-generated, discord.json manual)

---

## Summary of Best Practices

### Architecture
- ✅ Follow clean architecture: dependencies point inward
- ✅ Keep commands independent (no application/web imports)
- ✅ Place query objects in application layer (not persistence)
- ✅ Place permission evaluators in infrastructure (not web)
- ✅ Use events for cross-domain communication
- ✅ Respect layer boundaries - ArchUnit tests will enforce
- ✅ Access cross-domain data via services, not repositories (ADR-018)
- ✅ Use Anti-Corruption Layers for all external APIs (ADR-019)
- ✅ Keep Shared Kernel minimal and well-governed (ADR-020)
- ✅ Document domain relationships in Context Map (ADR-017)

### Development
- ✅ Run ArchUnit tests before committing
- ✅ Add field validation to commands (@NotNull, @NotBlank)
- ✅ Use Mappie for all DTO ↔ Command/Entity mappings
- ✅ Use aggregate methods for entity updates
- ✅ Use factories for complex entity creation
- ✅ Reference ADRs when making architectural decisions

### Testing
- ✅ Write ArchUnit tests for new domains
- ✅ Test command handlers independently
- ✅ Use Testcontainers for integration tests
- ✅ Test validators with edge cases

---

**For any architectural questions, consult the ADRs in `docs/adr/` or ask Claude Code to explain specific patterns.**
