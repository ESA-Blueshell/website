# ADR-016: Layer Dependency Rules and Clean Architecture

## Status
Superseded by
[architecture ADR-001](../architecture/ADR-001-application-modules-replace-layers.md).

The seven-layer rules enforced by `LayeredArchitectureTest` are replaced by
Spring Modulith module verification. The layering held while feature coupling
decayed unchecked — 103 imports cross a feature boundary and seven pairs of
features form cycles, none of which a layer rule can see. Retained for history.


## Context
Multi-layered architecture requires clear dependency rules to prevent coupling, maintain testability, and enable independent evolution. Without explicit rules, developers may create circular dependencies, tight coupling between layers, and violations of the dependency inversion principle.

Common issues in layered architectures:
- Inner layers depending on outer layers (dependency inversion violation)
- Commands depending on application or web layers (tight coupling)
- Persistence layer accessing web DTOs (architectural violation)
- Infrastructure concerns mixed with business logic
- Circular dependencies between domains

Requirements:
- Clear, enforceable dependency rules
- Support for hexagonal/clean architecture principles
- Independence of business logic from frameworks
- Testability without infrastructure
- Prevent circular dependencies

## Decision
We adopt **strict layer dependency rules** based on clean architecture and hexagonal architecture principles, with automated enforcement via ArchUnit.

### Dependency Rule: The Dependency Rule

**Core Principle:** Dependencies point inward. Inner layers know nothing about outer layers.

```
┌─────────────────────────────────────┐
│   Web Layer (Controllers, DTOs)     │  ← Outermost
├─────────────────────────────────────┤
│   Command Layer (Command Objects)   │
├─────────────────────────────────────┤
│   Application Layer (Services)      │
├─────────────────────────────────────┤
│   Domain Layer (Business Logic)     │  ← Core
├─────────────────────────────────────┤
│   Persistence Layer (Entities)      │
└─────────────────────────────────────┘

Infrastructure Layer (Security, Email, etc.) ← Adapters, can depend on any layer
```

### Allowed Dependencies

**Web Layer** can import:
- ✅ Command layer (command objects)
- ✅ Application layer (services, query objects)
- ✅ Persistence layer (entities for mapping)
- ✅ Shared/common utilities
- ❌ Nothing imports from web

**Command Layer** can import:
- ✅ Shared/common utilities only
- ✅ Domain primitives (value objects, enums)
- ❌ NOT application layer
- ❌ NOT web layer
- ❌ NOT persistence layer

**Application Layer** can import:
- ✅ Command layer (commands)
- ✅ Domain layer (domain services, models)
- ✅ Persistence layer (entities, repositories)
- ✅ Other domain's application layer (for cross-domain calls)
- ✅ Shared/common utilities
- ❌ NOT web layer

**Domain Layer** can import:
- ✅ Shared/common utilities only
- ❌ NOT application layer
- ❌ NOT persistence layer
- ❌ NOT web layer
- ❌ No framework dependencies

**Persistence Layer** can import:
- ✅ Domain layer (if exists)
- ✅ Other domain's persistence entities
- ✅ Shared/common utilities
- ❌ NOT application layer (except query objects in application/query/)
- ❌ NOT web layer
- ❌ NOT command layer

**Infrastructure Layer** can import:
- ✅ Any layer (adapter pattern)
- ✅ Framework dependencies
- ✅ External libraries

## Layer-Specific Rules

### Rule 1: Commands Must Be Independent

**Principle:** Commands are pure data structures representing use cases. They must be usable from any interface (web, CLI, messaging, tests) without dependencies.

**Allowed:**
```kotlin
// ✅ GOOD: Independent command
data class CreateUserCommand(
    @field:NotBlank
    val username: String,

    @field:Email
    val email: String
) : Command<User>
```

**Prohibited:**
```kotlin
// ❌ BAD: Command imports from application
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand

@UniqueUserCommand  // Application layer dependency!
data class CreateUserCommand(...)

// ❌ BAD: Command imports web DTO
import net.blueshell.api.domain.event.web.dto.SurveyRequest

data class CreateEventCommand(
    val survey: SurveyRequest  // Web layer dependency!
)
```

**Solution:** Place validation annotations in command layer or shared package:
```kotlin
// ✅ GOOD: Validation in command layer
// domain/user/command/validation/UniqueUserCommand.kt
@Constraint(validatedBy = [UniqueUserCommandValidator::class])
annotation class UniqueUserCommand

// Command can now use it
@UniqueUserCommand
data class CreateUserCommand(...)
```

### Rule 2: Persistence Must Not Depend on Interface Layers

**Principle:** Persistence layer should not know about web, CLI, or any interface concerns. It represents data access, not presentation.

**Prohibited:**
```kotlin
// ❌ BAD: Query filter in persistence layer
// domain/user/persistence/filter/UserFilter.kt
class UserFilter {
    var username: String?  // Represents HTTP query param - web concern!
}

// Specification depends on it
object UserSpecifications {
    fun fromFilter(filter: UserFilter): Specification<User> {
        // Persistence → Web dependency violation
    }
}
```

**Solution:** Query objects in application layer:
```kotlin
// ✅ GOOD: Query object in application layer
// domain/user/application/query/UserQuery.kt
data class UserQuery(
    val username: String?,
    val role: Role?
)

// Web layer maps to it
// domain/user/web/mapping/QueryMappings.kt
fun UserSearchParams.toQuery() = UserQuery(...)

// Specification uses application query object
object UserSpecifications {
    fun fromQuery(query: UserQuery): Specification<User> {
        // Persistence ← Application (allowed)
    }
}
```

### Rule 3: Infrastructure Concerns Belong in Infrastructure Layer

**Principle:** Technical concerns (security, email, jobs, external APIs) should not be mixed with business logic or presentation.

**Examples of Infrastructure Concerns:**
- Spring Security components (filters, authentication providers)
- Permission evaluators
- Email/SMS services
- Job dispatchers
- External API clients
- Message queue producers/consumers
- File storage services

**Prohibited:**
```kotlin
// ❌ BAD: Permission evaluator in web layer
// domain/user/web/permission/UserPermission.kt
@Component
class UserPermission : BasePermissionEvaluator<...> {
    // This is infrastructure, not web presentation!
}

// ❌ BAD: Email templates in application layer
// domain/auth/application/email/PasswordResetEmail.kt
class PasswordResetEmail {
    // Email formatting is infrastructure concern
}
```

**Solution:**
```kotlin
// ✅ GOOD: Permission evaluator in infrastructure
// infrastructure/security/permission/UserPermission.kt
@Component
class UserPermission(userService: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(userService) {
    // Spring Security infrastructure
}

// ✅ GOOD: Email templates in platform integration
// platform/integration/email/templates/PasswordResetEmail.kt
class PasswordResetEmail {
    // Infrastructure concern
}
```

### Rule 4: Cross-Domain Dependencies

**Allowed Cross-Domain Dependencies:**
```kotlin
// ✅ Application → Other Application
// auth/application/AuthenticationService.kt
class AuthenticationService(
    private val userService: UserService  // Cross-domain service call
)

// ✅ Application → Other Persistence
// event/application/EventService.kt
class EventService(
    private val userRepository: UserRepository  // Cross-domain entity access
)

// ✅ Persistence → Other Persistence
// event/persistence/Event.kt
@Entity
class Event {
    @ManyToOne
    var organizer: User  // Cross-domain entity reference
}

// ✅ Application Listener → Other Domain Event
// auth/application/listener/RecoveryEventListener.kt
@EventListener
fun onUserCreated(event: UserCreated) {
    // Cross-domain event listening
}
```

**Prohibited Cross-Domain Dependencies:**
```kotlin
// ❌ Application → Other Web
// auth/application/AuthenticationService.kt
import net.blueshell.api.domain.user.web.dto.UserRequest  // ❌ Wrong!

// ❌ Command → Other Application
// user/command/UserCommands.kt
import net.blueshell.api.domain.auth.application.AuthService  // ❌ Wrong!

// ❌ Persistence → Other Web
// event/persistence/spec/EventSpecifications.kt
import net.blueshell.api.domain.user.web.dto.UserFilter  // ❌ Wrong!
```

### Rule 5: Minimize Circular Dependencies

**Problem:** Domain A depends on Domain B, and Domain B depends on Domain A.

**Detection:**
```kotlin
// auth → user
class AuthenticationService(userService: UserService)

// user → auth
class UserService(authService: AuthenticationService)  // ❌ Circular!
```

**Solutions:**

**A. Use Events (Preferred):**
```kotlin
// Auth publishes event
class AuthenticationService {
    fun authenticate(...) {
        events.publish(UserAuthenticated(userId))
    }
}

// User listens to event
@EventListener
fun onUserAuthenticated(event: UserAuthenticated) {
    // React without direct dependency
}
```

**B. Dependency Inversion:**
```kotlin
// Define interface in auth
interface UserProvider {
    fun findByUsername(username: String): User
}

// Auth depends on interface
class AuthenticationService(
    private val userProvider: UserProvider
)

// User implements interface
@Service
class UserService : UserProvider {
    override fun findByUsername(username: String) = ...
}
```

**C. Extract Shared Domain:**
```kotlin
// Create shared/core domain
api/src/main/kotlin/net/blueshell/api/shared/user/
└── UserIdentity.kt

// Both domains depend on shared
```

## ArchUnit Enforcement

### Layer Tests

```kotlin
@AnalyzeClasses(packages = ["net.blueshell.api"])
class LayerDependencyTest {

    @ArchTest
    fun `commands must not depend on application layer`(classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage("..command..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..")
            .check(classes)
    }

    @ArchTest
    fun `commands must not depend on web layer`(classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage("..command..")
            .should().dependOnClassesThat()
            .resideInAPackage("..web..")
            .check(classes)
    }

    @ArchTest
    fun `persistence must not depend on web layer`(classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage("..persistence..")
            .should().dependOnClassesThat()
            .resideInAPackage("..web..")
            .check(classes)
    }

    @ArchTest
    fun `domain layer must have no framework dependencies`(classes: JavaClasses) {
        classes()
            .that().resideInAPackage("..domain.model..")
            .or().resideInAPackage("..domain.service..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..domain..",
                "..shared..",
                "java..",
                "kotlin.."
            )
            .check(classes)
    }
}
```

### Circular Dependency Detection

```kotlin
@ArchTest
fun `no circular dependencies between domains`(classes: JavaClasses) {
    slices()
        .matching("net.blueshell.api.domain.(*)..")
        .should().beFreeOfCycles()
        .check(classes)
}
```

## Consequences

### Positive
- **Clear boundaries**: Developers know what can depend on what
- **Testability**: Inner layers testable without outer layers
- **Independence**: Business logic independent of frameworks
- **Flexibility**: Can swap implementations (web → CLI, MySQL → PostgreSQL)
- **Maintainability**: Changes to outer layers don't affect inner layers
- **Enforcement**: ArchUnit tests prevent violations
- **Documentation**: Dependency rules serve as architectural documentation
- **Scalability**: Domains can evolve independently

### Negative
- **More structure**: Requires more packages and interfaces
- **Learning curve**: Developers must understand clean architecture
- **Mapping overhead**: More mapping between layers (web DTO → command, etc.)
- **Verbosity**: Sometimes requires additional abstractions
- **Initial cost**: More upfront design needed

### Trade-offs
- **Flexibility vs Simplicity**: More flexible but more complex
- **Decoupling vs Directness**: Loose coupling requires more indirection
- **Testability vs Speed**: Better tests but slower initial development

## Guidelines

### DO:
- ✅ Place commands in independent command layer
- ✅ Place query objects in application layer
- ✅ Place permission evaluators in infrastructure layer
- ✅ Place email templates in platform/integration
- ✅ Use events for cross-domain communication
- ✅ Use dependency inversion for cross-domain service calls
- ✅ Write ArchUnit tests to enforce rules
- ✅ Map web DTOs → commands in web layer
- ✅ Map query params → query objects in web layer
- ✅ Keep domain layer pure (no framework dependencies)

### DON'T:
- ❌ Import application layer from commands
- ❌ Import web layer from commands
- ❌ Import web layer from persistence
- ❌ Place query filters in persistence layer
- ❌ Place permission evaluators in web layer
- ❌ Place email logic in application layer
- ❌ Create circular dependencies between domains
- ❌ Skip ArchUnit tests
- ❌ Pass web DTOs to services
- ❌ Mix infrastructure concerns with business logic

## Migration Path

For existing violations:

1. **Commands → Application dependencies:**
   - Move validation annotations to `command/validation/` or `shared/validation/`
   - Update imports in command objects

2. **Persistence → Web dependencies (filters):**
   - Create `application/query/` package
   - Move filter objects → query objects
   - Update specifications to use `fromQuery()` instead of `fromFilter()`
   - Add mapping in web layer: search params → query objects

3. **Permission evaluators in web layer:**
   - Move from `domain/{domain}/web/permission/` to `infrastructure/security/permission/`
   - Update Spring configuration if needed

4. **Email templates in application layer:**
   - Move to `platform/integration/email/templates/`
   - Have application publish events
   - Platform layer listens to events and sends emails

5. **Add ArchUnit tests:**
   - Create `LayerDependencyTest.kt`
   - Add tests for each rule
   - Fix violations before merging

## Related ADRs
- [ADR-001: Multi-Layered Domain-Driven Architecture](ADR-001-multi-layered-domain-driven-architecture.md) - Layer structure
- [ADR-002: Command Pattern with CommandBus](ADR-002-command-pattern-with-command-bus.md) - Command independence
- [ADR-006: Event-Driven Architecture](ADR-006-event-driven-architecture.md) - Cross-domain communication
- [ADR-014: Permission Evaluation Strategy](ADR-014-permission-evaluation-strategy.md) - Infrastructure layer placement
- [ADR-015: JPA Specifications and Dynamic Queries](ADR-015-jpa-specifications-dynamic-queries.md) - Query object pattern

## References
- Clean Architecture (Robert C. Martin)
- Hexagonal Architecture (Alistair Cockburn)
- Domain-Driven Design (Eric Evans)
- ArchUnit Documentation
- Dependency Inversion Principle
