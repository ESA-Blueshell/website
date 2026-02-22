# ADR-022: Platform, Infrastructure, and Shared Organization

## Status
Accepted

## Context
The codebase follows Domain-Driven Design with multi-layered architecture, using three cross-cutting layers: `shared/`, `infrastructure/`, and `platform/`. Without clear documentation distinguishing these layers, developers may:
- Place code in the wrong layer, violating dependency rules
- Create unwanted dependencies (e.g., domains importing from platform)
- Confuse security infrastructure with platform integrations
- Misunderstand the purpose of the shared kernel

Common ambiguities:
- **"Where do job definitions go?"** - They're contracts between domains and platform
- **"Is permission evaluation web or infrastructure?"** - It's Spring Security infrastructure
- **"Should email templates be in domain or platform?"** - Platform (Anti-Corruption Layer)
- **"Can shared/ depend on domain entities?"** - Only User for audit fields (documented exception)

Requirements:
- Clear, actionable guidelines for code placement
- Enforcement of layer independence via architecture tests
- Alignment with Clean Architecture and DDD principles
- Support for Anti-Corruption Layers (ADR-019)

## Decision
We establish **clear definitions and decision trees** for the three cross-cutting layers, with automated enforcement via ArchUnit.

### Layer Definitions

#### 1. Shared Kernel (`shared/`)

**Purpose:** Cross-cutting contracts and abstractions used by all domains

**Characteristics:**
- **Innermost layer** - depends on nothing except Java/Kotlin std libs
- Defines interfaces and contracts, NOT implementations
- Framework-agnostic (no Spring, no JPA annotations except in base entities)
- Minimal and stable (changes affect all domains)

**Contents:**
```
shared/
├── command/              # Command infrastructure (Command, CommandHandler, CommandBus)
├── dto/                  # Shared DTOs (BaseDTO)
├── email/                # Email content DTOs (Anti-Corruption Layer)
├── enums/                # Cross-domain enums (Role, etc.)
├── event/                # Event publishing infrastructure
├── exception/            # Base exception classes
├── job/                  # Job definitions and payloads (contract between domains and platform)
├── model/                # Base entity classes (AuditedEntity, etc.)
├── security/             # Security abstractions (UserPrincipal, UserPrincipalMapper)
└── service/              # Base service interfaces
```

**Examples:**
- ✅ `Command<R>` interface - all domains implement this
- ✅ `EmailContent` DTO - domains build, platform delivers
- ✅ `CalendarJobs` object - defines job contracts
- ✅ `AuditedEntity` - base entity with audit fields
- ❌ `EmailService` implementation - belongs in platform
- ❌ `JwtTokenGenerator` - belongs in infrastructure

**Dependency Exception:**
- `shared/` imports `domain.user.persistence.User` for audit fields (ADR-016)
- This creates a documented cycle: `domain → shared → domain.user`
- Trade-off accepted for audit field convenience

---

#### 2. Infrastructure (`infrastructure/`)

**Purpose:** Spring Security and framework-specific infrastructure

**Characteristics:**
- Framework-specific (Spring Security, JWT libraries)
- Domain-aware (permission evaluators know about User, Event, etc.)
- Can depend on any layer (adapter pattern)
- Technical infrastructure, not business logic

**Contents:**
```
infrastructure/
└── security/
    ├── JwtAuthFilter.kt           # JWT authentication filter
    ├── JwtTokenGenerator.kt       # Token generation/validation
    ├── JwtAuthenticationEntryPoint.kt
    ├── SecurityUtils.kt           # Security helper utilities
    └── permission/                # Spring Security permission evaluators
        ├── BasePermissionEvaluator.kt
        ├── CompositePermissionEvaluator.kt
        ├── UserPermission.kt
        ├── EventPermission.kt
        └── ...
```

**Examples:**
- ✅ `UserPermission` - Spring Security PermissionEvaluator
- ✅ `JwtAuthFilter` - Spring Security filter
- ✅ `@PreAuthorize` helpers - Spring Security infrastructure
- ❌ Email sending - belongs in platform (external integration)
- ❌ Payment processing - belongs in platform (external integration)

---

#### 3. Platform (`platform/`)

**Purpose:** External integrations and application configuration

**Characteristics:**
- **Anti-Corruption Layers** for external systems (ADR-019)
- Implements `shared/` interfaces
- Isolates domains from external API changes
- Spring configuration (`@Configuration`, `@Bean`)

**Contents:**
```
platform/
├── config/                        # Spring configuration
│   ├── SecurityConfig.kt          # Security filter chain, CORS
│   ├── JpaConfig.kt               # JPA/Hibernate configuration
│   ├── RabbitMqConfig.kt          # Message queue configuration
│   └── WebConfig.kt               # Web MVC configuration
├── advice/                        # Global exception handling
│   └── GlobalExceptionHandler.kt
└── integration/                   # External system adapters (ACL)
    ├── email/                     # Email delivery (Brevo)
    │   ├── service/
    │   │   ├── EmailService.kt    # Sends EmailContent via Brevo
    │   │   └── EmailTemplateService.kt
    │   └── job/
    │       └── RecoveryEmailJob.kt
    ├── calendar/                  # Google Calendar sync
    │   ├── service/
    │   │   └── GoogleCalendarAdapter.kt
    │   └── job/
    │       ├── AddEventToCalendarJob.kt
    │       └── SyncEventToCalendarJob.kt
    ├── contact/                   # Contact management (Brevo)
    │   ├── service/
    │   │   └── BrevoContactAdapter.kt
    │   └── job/
    │       └── SyncContactJob.kt
    ├── payment/                   # Payment processing (Mollie)
    │   └── service/
    │       └── MolliePaymentAdapter.kt
    └── queue/                     # Job queue implementation
        ├── JobDispatcher.kt       # Dispatches jobs to RabbitMQ
        ├── JobConsumer.kt         # Consumes jobs from RabbitMQ
        └── JobHandler.kt          # Job execution infrastructure
```

**Examples:**
- ✅ `EmailService` - delivers `EmailContent` via Brevo API
- ✅ `GoogleCalendarAdapter` - syncs events to Google Calendar
- ✅ `SecurityConfig` - configures Spring Security filter chain
- ✅ `JobDispatcher` - implements job queue with RabbitMQ
- ❌ Job definitions (`CalendarJobs`) - belong in shared (contracts)
- ❌ Permission evaluators - belong in infrastructure (Spring Security)

---

### Decision Tree: Where Does X Go?

Use this flowchart to determine the correct layer:

```
┌─────────────────────────────────────────────────┐
│ Is it a contract/interface used by multiple    │
│ domains without implementation details?         │
└─────────────┬───────────────────────────────────┘
              │ YES
              ↓
         ┌─────────┐
         │ shared/ │
         └─────────┘
              │
              │ NO
              ↓
┌─────────────────────────────────────────────────┐
│ Is it Spring Security infrastructure?           │
│ (JWT, filters, permission evaluators)           │
└─────────────┬───────────────────────────────────┘
              │ YES
              ↓
      ┌──────────────────┐
      │ infrastructure/  │
      │    security/     │
      └──────────────────┘
              │
              │ NO
              ↓
┌─────────────────────────────────────────────────┐
│ Is it an external system adapter or             │
│ Spring @Configuration?                          │
└─────────────┬───────────────────────────────────┘
              │ YES
              ↓
         ┌──────────┐
         │platform/ │
         └──────────┘
              │
              │ NO
              ↓
┌─────────────────────────────────────────────────┐
│ It belongs in a domain layer!                   │
│ (web, command, application, persistence)        │
└─────────────────────────────────────────────────┘
```

**Specific Examples:**

| Component | Question | Location | Rationale |
|-----------|----------|----------|-----------|
| `EmailContent` DTO | Used by domains + platform? | `shared/email/` | Contract (ACL) |
| `EmailService` (send) | External API? | `platform/integration/email/` | Brevo adapter |
| `PasswordResetEmail` builder | Domain logic? | `domain/auth/application/email/` | Auth domain concern |
| `UserPermission` | Spring Security? | `infrastructure/security/permission/` | PermissionEvaluator |
| `JwtTokenGenerator` | Spring Security? | `infrastructure/security/` | JWT infrastructure |
| `CalendarJobs` definitions | Contract? | `shared/job/` | Job payload contracts |
| `AddEventToCalendarJob` handler | External API? | `platform/integration/calendar/` | Google Calendar adapter |
| `GoogleCalendarAdapter` | External API? | `platform/integration/calendar/` | Anti-Corruption Layer |
| `SecurityConfig` | Spring config? | `platform/config/` | Spring configuration |
| `UserActivationEmail` builder | Domain logic? | `domain/auth/application/email/` | Auth domain concern |
| `RecoveryEmailJob` handler | External API? | `platform/integration/email/` | Email delivery job |

---

## Consequences

### Positive
- **Clear placement rules**: Developers know where to put new code
- **Enforced independence**: Domains cannot import platform (ArchUnit)
- **Anti-Corruption Layers**: External APIs isolated from domains
- **Testability**: Shared kernel has no external dependencies
- **Maintainability**: Easy to find and update cross-cutting code
- **Documentation**: Decision tree provides instant answers

### Negative
- **Learning curve**: Developers must understand the three layers
- **Initial confusion**: Distinction between infrastructure and platform not obvious
- **More structure**: Additional package hierarchy to navigate

### Trade-offs
- **Clarity vs Simplicity**: More organized but more folders
- **Enforcement vs Flexibility**: Rules prevent mistakes but require discipline
- **Shared cycle**: Accepts `shared → User` dependency for audit convenience

---

## Migration Examples

### Example 1: Moving Job Definitions
**Problem:** `platform/integration/queue/JobDefinitions.kt` violates dependency rule (domains import platform)

**Solution:**
```kotlin
// BEFORE: platform/integration/queue/JobDefinitions.kt
object CalendarJobs {
    data class AddEvent(val eventId: Long)
}

// AFTER: shared/job/JobDefinitions.kt
object CalendarJobs {
    data class AddEvent(val eventId: Long)
}

// Domain listener now imports from shared, not platform ✅
import net.blueshell.api.shared.job.CalendarJobs
```

---

### Example 2: Fixing Email Inheritance Violation
**Problem:** Domain `RecoveryEmail` extends `platform.integration.email.BaseEmail` (ACL violation)

**Solution:**
```kotlin
// BEFORE: Domain extends platform class ❌
// domain/auth/application/email/PasswordResetEmail.kt
class PasswordResetEmail(user: User, token: String) : BaseEmail() {
    // Inherits platform implementation
}

// AFTER: Domain builds EmailContent DTO ✅
// domain/auth/application/email/RecoveryEmailBuilders.kt
fun createPasswordResetEmail(user: User, token: String, frontendUrl: String): EmailContent {
    return EmailContent(
        recipientEmail = user.email,
        recipientName = user.fullName,
        subject = "Reset Your Password",
        markdownContent = "Click here: $frontendUrl/reset?token=$token"
    )
}

// Platform delivers EmailContent ✅
// platform/integration/email/service/EmailService.kt
fun sendEmail(emailContent: EmailContent) {
    brevoClient.send(emailContent.toBrevoRequest())
}
```

---

### Example 3: Moving Permission Evaluators
**Problem:** `UserPermission` in `web/permission/` should be in `infrastructure/security/permission/`

**Solution:**
```kotlin
// BEFORE: platform/config/permission/UserPermission.kt ❌
// AFTER: infrastructure/security/permission/UserPermission.kt ✅
@Component
class UserPermission(userService: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(userService) {
    // Spring Security PermissionEvaluator
}
```

---

## ArchUnit Enforcement

### Test 1: Domains Must Not Import Platform Integration
```kotlin
@Test
fun `domains must not depend on platform integration`(): Unit =
    arch("Domains use shared contracts, not platform implementation") {
        noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..platform.integration..")
            .because("ADR-019/ADR-022: Domains use shared/ contracts, not platform/ internals")
    }
```

### Test 2: Shared Kernel Must Not Depend on Platform
```kotlin
@Test
fun `shared kernel must not depend on platform`(): Unit =
    arch("Shared kernel is innermost layer") {
        noClasses()
            .that().resideInAnyPackage("..shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..platform..")
            .because("ADR-022: Shared kernel cannot depend on platform")
    }
```

### Test 3: Permission Evaluators in Infrastructure
```kotlin
@Test
fun `permission evaluators in infrastructure layer`(): Unit =
    arch("Permission evaluators must be in infrastructure layer") {
        classes()
            .that().haveSimpleNameEndingWith("Permission")
            .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..")
            .should().resideInAnyPackage("..infrastructure.security.permission..")
            .because("ADR-014/ADR-022: Permission evaluators are infrastructure adapters")
    }
```

---

## Guidelines

### DO:
- ✅ Place job definitions (contracts) in `shared/job/`
- ✅ Place job handlers (implementations) in `platform/integration/{system}/job/`
- ✅ Place permission evaluators in `infrastructure/security/permission/`
- ✅ Place email builders in domain `application/email/`
- ✅ Place email delivery in `platform/integration/email/`
- ✅ Use `EmailContent` DTO as Anti-Corruption Layer
- ✅ Consult decision tree when uncertain
- ✅ Write ArchUnit tests to enforce rules

### DON'T:
- ❌ Import `platform.integration.*` from domains
- ❌ Place Spring Security code in platform (use infrastructure)
- ❌ Place job definitions in platform (use shared)
- ❌ Extend platform classes from domains (use composition)
- ❌ Place permission evaluators in web layer
- ❌ Place Spring `@Configuration` in infrastructure (use platform/config)

---

## Related ADRs
- [ADR-001: Multi-Layered Domain-Driven Architecture](ADR-001-multi-layered-domain-driven-architecture.md) - Layer structure
- [ADR-016: Layer Dependency Rules](ADR-016-layer-dependency-rules.md) - Dependency constraints
- [ADR-019: Anti-Corruption Layers for External Integration](ADR-019-anti-corruption-layers-for-external-integration.md) - Platform isolation
- [ADR-020: Shared Kernel Governance](ADR-020-shared-kernel-governance.md) - Shared kernel principles

## References
- Clean Architecture (Robert C. Martin) - Dependency Rule
- Domain-Driven Design (Eric Evans) - Shared Kernel, Anti-Corruption Layer
- Hexagonal Architecture (Alistair Cockburn) - Adapters and ports
