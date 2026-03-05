# ADR-001: Multi-Layered Domain-Driven Architecture

## Status
Accepted

## Context
Spring Boot applications can be organized in various ways: traditional layered architecture (controller/service/repository), package-by-layer, package-by-feature, hexagonal architecture, or domain-driven design (DDD).

The application needs to:
- Scale as business complexity grows
- Maintain clear separation of concerns
- Support multiple bounded contexts (auth, user, event, committee, etc.)
- Enable parallel development by multiple teams
- Enforce architectural boundaries automatically
- Make business logic explicit and testable

Traditional layered architecture with package-by-layer leads to:
- Large, monolithic service classes handling multiple concerns
- Business logic scattered across services and controllers
- Difficulty understanding domain boundaries
- Tight coupling between unrelated features
- Hard to test business logic in isolation

## Decision
We adopt a **multi-layered domain-driven design (DDD)** architecture with **package-by-feature** organization, enforced by ArchUnit tests.

### Layer Structure

Each domain (bounded context) is organized into these layers:

```
domain/{domain-name}/
├── web/                    # Presentation Layer
│   ├── *Controller.kt      # REST endpoints
│   ├── dto/                # Data Transfer Objects
│   │   ├── request/        # Inbound DTOs
│   │   └── response/       # Outbound DTOs
│   ├── mapping/            # Request/Response mappers
│   └── validation/         # Web-layer validators (format, structure)
├── command/                # Command Layer
│   ├── *Commands.kt        # Command objects with field validation
│   └── validation/         # Command-specific constraint annotations
├── application/            # Application Layer
│   ├── command/            # Command handlers
│   │   └── *CommandHandlers.kt
│   ├── *Service.kt         # Application services
│   ├── event/              # Domain events
│   ├── listener/           # Event listeners
│   ├── query/              # Query objects for dynamic searches
│   ├── factory/            # Entity factories
│   ├── validation/         # Business rule validators
│   └── exception/          # Application exceptions
├── domain/                 # Domain Layer (optional)
│   ├── model/              # Domain models
│   └── service/            # Domain services
└── persistence/            # Persistence Layer
    ├── *Entity.kt          # JPA entities
    ├── repository/         # Spring Data repositories
    └── spec/               # JPA Specifications for dynamic queries
```

### Layer Responsibilities

**Web Layer**
- HTTP request/response handling
- Input validation (structural: format, required fields, size limits)
- Request → Command mapping
- Entity → Response mapping
- OpenAPI documentation
- DTOs for external API contracts

**Command Layer**
- Command objects representing use cases
- Field-level validation (@NotNull, @Size, @Pattern)
- Command-specific constraint annotations
- Type-safe operation contracts
- Independent of application/web layers

**Application Layer**
- Use case orchestration
- Command handling
- Transaction management
- Business rule validation (with DB access)
- Domain event definition and publishing
- Event listening and reaction
- Query objects for dynamic searches
- Cross-aggregate coordination
- Business logic orchestration

**Domain Layer** (Optional)
- Core business logic
- Domain models (rich objects with behavior)
- Domain services (business operations on multiple entities)
- Domain invariant enforcement
- Value objects and domain primitives
- No infrastructure dependencies

**Persistence Layer**
- Data access
- Entity definitions (JPA entities)
- Repository interfaces
- Database queries
- JPA Specifications for dynamic queries
- Database schema concerns

**Infrastructure Layer** (Cross-Cutting)
- Spring Security configuration
- Permission evaluators (authorization logic)
- Email templates and services
- External API integrations
- Job scheduling and dispatching
- Technical services (SMS, storage, etc.)

**Note on Platform Sub-Layers:**
Complex platform integration modules (`email/`, `contact/`, `job/`) mirror the domain layer
structure with their own `application/`, `persistence/`, `web/`, and `job/` sub-packages.
The same layer rules apply: controllers in `web/`, services in `application/`, repositories
in `persistence/repository/`, specifications in `persistence/spec/`. See ADR-022 for details.

### Package Organization: Package-by-Feature

Each domain is a self-contained module:
```
api/src/main/kotlin/net/blueshell/api/domain/
├── auth/           # Authentication & authorization
├── user/           # User management
├── event/          # Event management
├── committee/      # Committee management
├── membership/     # Membership management
├── contribution/   # Contribution management
├── blog/           # Blog management
├── sponsor/        # Sponsor management
└── survey/         # Survey management
```

### Layer Dependency Rules

**Dependency Flow (Clean Architecture):**
```
Web Layer
   ↓ can import
Command Layer
   ↓ can import
Application Layer
   ↓ can import
Domain Layer
   ↓ can import
Persistence Layer

Infrastructure ← can import from any layer (adapter pattern)
```

**Prohibited Dependencies:**
- ❌ Command → Application (commands must be independent)
- ❌ Command → Web (commands must be reusable across interfaces)
- ❌ Persistence → Web (persistence shouldn't know about presentation)
- ❌ Persistence → Application (except query objects in application/query/)
- ❌ Domain → Application (domain must be pure)
- ❌ Domain → Persistence (domain shouldn't know about JPA)

**Allowed Cross-Domain Dependencies:**
- ✅ Application → Other domain's Application services
- ✅ Application → Other domain's Persistence entities
- ✅ Persistence → Other domain's Persistence entities
- ✅ Application listeners → Other domain's Application events

### Architectural Boundaries (Enforced by ArchUnit)

```kotlin
layeredArchitecture()
    .layer("Controllers").definedBy("..web..")
    .layer("Commands").definedBy("..command..")
    .layer("Services").definedBy("..application..")
    .layer("Repositories").definedBy("..persistence.repository..")
    .layer("Model").definedBy("..persistence..", "..domain.model..")

    .whereLayer("Controllers")
        .mayOnlyAccessLayers("Commands", "Services", "DTO", "Model", "Common")
    .whereLayer("Commands")
        .mayOnlyAccessLayers("Common")  // Commands are independent
    .whereLayer("Services")
        .mayOnlyAccessLayers("Commands", "Repositories", "Model", "Common")
    .whereLayer("Repositories")
        .mayOnlyAccessLayers("Model", "Common")
    .whereLayer("Model")
        .mayOnlyAccessLayers("Common")
```

### Shared Infrastructure

Common functionality lives in shared packages:
```
api/src/main/kotlin/net/blueshell/api/
├── shared/             # Shared across domains
│   ├── command/        # Command pattern infrastructure
│   ├── event/          # Event publishing
│   ├── validation/     # Reusable validators
│   ├── dto/            # Base DTOs
│   └── model/          # Base entities
├── infrastructure/     # Infrastructure concerns
│   └── security/       # Spring Security config
│       ├── JwtAuthFilter.kt
│       ├── JwtTokenUtil.kt
│       └── permission/ # Permission evaluators (domain-specific)
│           ├── UserPermission.kt
│           ├── EventPermission.kt
│           └── ...
└── platform/           # Platform concerns
    ├── config/         # Application configuration
    └── integration/    # External integrations
        ├── email/      # Email templates and services
        ├── calendar/   # Calendar integration
        └── jobs/       # Job dispatching
```

## Consequences

### Positive
- **Clear boundaries**: Each domain is self-contained with explicit dependencies
- **Screaming architecture**: Project structure reveals business concepts
- **Independent evolution**: Domains can evolve independently
- **Testable**: Business logic is isolated and testable
- **Parallel development**: Teams can work on different domains without conflicts
- **Scalability**: Can extract domains into microservices if needed
- **Explicit use cases**: Commands make operations discoverable
- **Enforced architecture**: ArchUnit prevents violations
- **IDE-friendly**: Easy navigation within feature boundaries
- **Reduced coupling**: Features don't accidentally depend on each other

### Negative
- **More directories**: More nesting than flat package structure
- **Learning curve**: Developers must understand DDD concepts
- **Initial setup overhead**: More structure to create initially
- **Potential duplication**: Shared concepts might be duplicated across domains
- **Requires discipline**: Easy to violate if not enforced by tests

### Trade-offs
- **Complexity vs Maintainability**: More structure upfront for long-term maintainability
- **Flexibility vs Convention**: Strong conventions reduce flexibility but improve consistency

## Implementation Notes

### When to Create a Domain Layer
Create `domain/` layer when:
- Business logic is complex and needs separation from application orchestration
- Domain models should have behavior beyond simple data holders
- Business rules need to be enforced by domain services
- Multiple aggregates need coordination through domain services

Skip `domain/` layer when:
- Application services suffice for orchestration
- Entities are primarily data structures
- Business logic is minimal

### Cross-Domain Dependencies
- Domains may depend on other domains' **persistence layer** (entities/repositories)
- Domains may depend on other domains' **application services**
- Domains should NOT depend on other domains' **web layer**
- Use events for loosely-coupled cross-domain communication

## Common Pitfalls and Anti-Patterns

### ❌ Command Layer Violations

**DON'T: Import from Application Layer**
```kotlin
// ❌ BAD: Command imports application validator
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand

@UniqueUserCommand  // Application layer dependency
data class CreateUserCommand(...)
```

**DO: Keep Commands Independent**
```kotlin
// ✅ GOOD: Validation annotation in command layer or shared
import net.blueshell.api.shared.validation.UniqueUserCommand

@UniqueUserCommand
data class CreateUserCommand(...)
```

**DON'T: Import Web DTOs in Commands**
```kotlin
// ❌ BAD: Command depends on web layer
import net.blueshell.api.domain.event.web.dto.request.SurveyRequest

data class CreateEventCommand(
    val survey: SurveyRequest  // Web DTO!
)
```

**DO: Use Domain Objects**
```kotlin
// ✅ GOOD: Command uses domain object
data class CreateEventCommand(
    val survey: SurveyData  // Domain object in command layer
)
```

### ❌ Persistence Layer Violations

**DON'T: Place Query Parameters in Persistence**
```kotlin
// ❌ BAD: HTTP query params in persistence layer
// domain/user/persistence/filter/UserFilter.kt
class UserFilter {
    var username: String?  // This represents HTTP query param
}
```

**DO: Place Query Objects in Application**
```kotlin
// ✅ GOOD: Query objects in application layer
// domain/user/application/query/UserQuery.kt
data class UserQuery(
    val username: String?,
    val hasRole: Role?
)

// Web layer maps HTTP params → Query objects
```

### ❌ Infrastructure Concerns in Wrong Layer

**DON'T: Permission Evaluators in Web Layer**
```kotlin
// ❌ BAD: domain/user/web/permission/UserPermission.kt
// Permission evaluation is infrastructure, not web presentation
```

**DO: Permission Evaluators in Infrastructure**
```kotlin
// ✅ GOOD: infrastructure/security/permission/UserPermission.kt
// Spring Security infrastructure concern
```

**DON'T: Email Templates in Application Layer**
```kotlin
// ❌ BAD: domain/auth/application/email/PasswordResetEmail.kt
// Email formatting is infrastructure, not business logic
```

**DO: Email Templates in Platform Integration**
```kotlin
// ✅ GOOD: platform/integration/email/templates/PasswordResetEmail.kt
// Application publishes events, platform handles email
```

### ❌ Circular Dependencies

**DON'T: Direct Service-to-Service Calls Across Domains**
```kotlin
// ❌ BAD: Tight coupling between domains
@Service
class AuthenticationService(
    private val userService: UserService  // Direct dependency
)
```

**DO: Use Dependency Inversion or Events**
```kotlin
// ✅ GOOD: Interface for loose coupling
interface UserProvider {
    fun findByUsername(username: String): User
}

@Service
class AuthenticationService(
    private val userProvider: UserProvider  // Depends on interface
)

// OR use events for cross-domain communication
```

### Migration Path
For new features:
1. Create domain package structure
2. Start with web → application → persistence layers
3. Add domain layer only if complexity warrants it
4. Add ArchUnit tests to enforce boundaries

For existing features:
1. Refactor one domain at a time
2. Start with highest business value domains
3. Extract domain logic from services to domain models
4. Add tests to verify boundaries

## Examples

### Simple Domain (No Domain Layer)
```
domain/sponsor/
├── web/
│   ├── SponsorController.kt
│   ├── dto/
│   └── mapping/
├── command/
│   └── SponsorCommands.kt
├── application/
│   ├── command/
│   │   └── SponsorCommandHandlers.kt
│   └── SponsorService.kt
└── persistence/
    ├── Sponsor.kt
    └── repository/
```

### Complex Domain (With Domain Layer)
```
domain/auth/
├── web/
│   ├── AuthenticationController.kt
│   ├── RecoveryController.kt
│   ├── dto/
│   └── mapping/
├── command/
│   ├── AuthenticationCommands.kt
│   └── RecoveryCommands.kt
├── application/
│   ├── command/
│   ├── listener/
│   ├── factory/
│   ├── AuthenticationService.kt
│   ├── PasswordRecoveryService.kt
│   └── UserActivationService.kt
├── domain/
│   ├── model/
│   │   ├── AuthenticationSession.kt
│   │   └── RecoveryTokenValidation.kt
│   └── service/
│       ├── RecoveryTokenValidator.kt
│       └── TokenGenerator.kt
└── persistence/
    ├── RecoveryToken.kt
    └── repository/
```

## References
- Domain-Driven Design (Eric Evans)
- Implementing Domain-Driven Design (Vaughn Vernon)
- Hexagonal Architecture (Alistair Cockburn)
- Spring Boot Best Practices
- ArchUnit for Architecture Testing
