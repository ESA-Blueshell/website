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

**Every integration follows a canonical 4-sub-package structure:**

```
integration/{system}/
├── adapter/                   # ACL adapters, low-level HTTP clients, initializers
│   ├── {System}Adapter.kt     # Production ACL adapter (@Profile("!test"))
│   ├── {System}Client.kt      # Low-level API client (HTTP, SDK)
│   └── {System}Initializer.kt # Startup configuration (e.g. bounce settings)
├── application/               # Business orchestration (services, schedulers, job handlers)
│   ├── service/               # e.g. EmailSenderService, ContactSyncService
│   ├── query/                 # Query objects for this module
│   ├── job/                   # Job handlers for async work
│   │   └── {Operation}Job.kt  # Extends AbstractJsonJobHandler
│   └── {System}Scheduler.kt   # Cron-triggered orchestration
├── web/                       # REST controllers and DTOs (same rules as domain web/)
│   ├── {System}ManagementController.kt
│   └── dto/                   # *DTO classes for this module's endpoints
└── persistence/               # Entities and repositories (if the module owns tables)
    ├── {Entity}.kt
    ├── repository/            # Spring Data repositories
    └── spec/                  # JPA Specifications
```

Simple integrations (e.g. `calendar/`) may omit sub-packages that don't apply
(no web, no persistence), but must still use `adapter/` and `application/job/` for
their adapter and job handler files.

**Hexagonal ports (`port/in` and `port/out`) — optional refinement.**

An integration with non-trivial inbound use cases and a vendor-facing
Anti-Corruption Layer may name its ports explicitly under `port/`. The cohort
module is the canonical example:

```
integration/cohort/
├── port/in/                    # Inbound (driving) ports — one per use-case group
│   ├── CohortMembershipSync.kt # ADD/REMOVE one (user, target) pair
│   ├── CohortReconciliation.kt # "kick the engine" admin operations
│   ├── CohortRemediation.kt    # link / verify / remove-external-member
│   ├── CohortTargeting.kt      # link / create / switch / delete external target
│   └── CohortDrift.kt          # compute a drift report
├── port/out/                   # Outbound (driven) ACL ports
│   ├── CohortPort.kt           # vendor-neutral target operations
│   └── CohortPortRegistry.kt   # selects a CohortPort by TargetSystem
├── adapter/                    # driving adapters (job handlers, web) + the ACL implementation (brevo/)
├── application/                # port/in implementations: *Service, evaluator, ledger, schedulers
└── persistence/                # entities + repositories
```

Conventions:

- **Inbound use-case ports live under `port/in`.** One port per use-case group;
  do **not** split one use case across several ports, and do **not** add a port
  just to read or write a single column. A port with one implementation is fine —
  its value is a published contract for driving adapters (job handlers, controllers),
  a stable test seam, and consistency with hexagonal architecture, not caller count.
- **Outbound ACL ports live under `port/out`.** These are the ADR-019
  Anti-Corruption Layer interfaces (`CohortPort` is a vendor-neutral facade selected
  at runtime by `TargetSystem`); their vendor implementations live under `adapter/`
  (e.g. `adapter/brevo/BrevoCohortAdapter`).
- **`application/` holds the `port/in` implementations** — the `*Service` beans,
  the rule evaluator, the ledger writer, schedulers — exactly as the 4-sub-package
  rule already requires for `@Service` beans.
- **Driving adapters stay under `adapter/`** (job handlers under
  `adapter/job/`, controllers under `adapter/web/` or `web/`).

This is a refinement of the 4-sub-package structure, not a replacement: most
integrations have a single trivial inbound path and do not need named ports. The
architecture tests already permit it — `LayeredArchitectureTest` treats `platform/`
as infrastructure and lets `shared/job` payloads reference
`platform.integration..port.in..`, and `PlatformConsistencyArchitectureTest` has no
rule against port packages.

**Mock adapters** live in a shared sub-package, easily excludable by ArchUnit and profiles:
```
integration/mock/
├── MockContactAdapter.kt      # @Primary @Profile("test | dev")
├── MockCalendarAdapter.kt     # @Primary @Profile("test | dev")
└── MockListmonkEmailClient.kt # @Primary @Profile("test")
```

**Contents:**
```
platform/
├── config/                        # Spring configuration
│   ├── SecurityConfig.kt
│   ├── JpaConfig.kt
│   ├── AsyncConfig.kt
│   └── WebConfig.kt
├── advice/                        # Global exception handling
│   └── GlobalExceptionHandler.kt
└── integration/                   # External system adapters (ACL)
    ├── email/                     # Email delivery (Listmonk)
    │   ├── adapter/               # ListmonkEmailClient, EmailTransportClient, ListmonkBouncePollingService
    │   ├── application/service/   # EmailSenderService, EmailTemplateService
    │   ├── application/job/       # RecoveryEmailJob, EventSignupEmailJob, ContributionReminderEmailJob
    │   ├── persistence/           # Email entity, EmailRepository, EmailSpecifications
    │   └── web/                   # EmailManagementController, EmailTrackingController
    │       └── dto/               # EmailDTO, EmailStatsDTO
    ├── calendar/                  # Google Calendar sync
    │   ├── adapter/               # GoogleCalendarAdapter, GoogleCalendarClient
    │   └── application/job/       # SyncEventToCalendarJob
    ├── contact/                   # Contact sync (Listmonk + Brevo)
    │   ├── adapter/               # ListmonkContactAdapter, BrevoContactAdapter, BrevoContactClient
    │   ├── application/           # ContactSyncService, ContactListService, ContactSyncScheduler
    │   ├── application/job/       # SyncContactJob, DeleteContactJob, SyncListMembershipJob
    │   └── persistence/           # Contact entity, ContactRepository, etc.
    ├── job/                       # Job execution management
    │   ├── application/service/   # JobExecutionService
    │   ├── application/query/     # JobExecutionQuery
    │   ├── persistence/           # JobExecution entity, JobExecutionSpecifications
    │   ├── persistence/repository/ # JobExecutionRepository
    │   └── web/                   # JobManagementController
    │       ├── service/           # JobExecutionViewService (builds DTOs — web concern)
    │       └── dto/               # JobExecutionDTO, JobStatsDTO
    ├── mock/                      # Test/dev mock adapters
    │   ├── MockContactAdapter.kt  # @Primary @Profile("test | dev")
    │   ├── MockCalendarAdapter.kt # @Primary @Profile("test | dev")
    │   └── MockListmonkEmailClient.kt  # @Primary @Profile("test")
    └── queue/                     # Job execution infrastructure
        ├── AbstractJsonJobHandler.kt
        ├── AbstractMailJobHandler.kt
        ├── JobDispatcher.kt       # Enqueue: DB write + async dispatch
        └── JobExecutor.kt         # @Async execution + RetryTemplate
```

**Profile conventions for integration adapters:**
- Production adapters (`@Profile("!test")` or `@Profile("!test & !dev")`): prevent test environment pollution
- Mock adapters (`@Primary @Profile("test | dev")` or `@Profile("test")`): override production beans in safe environments

**Examples:**
- ✅ `ListmonkEmailClient` — delivers `EmailContent` via Listmonk transactional API, `@Profile("!test")`
- ✅ `GoogleCalendarAdapter` — syncs events to Google Calendar, `@Profile("!test & !dev")`
- ✅ `MockContactAdapter` — in-memory contact management for tests, `@Primary @Profile("test | dev")`
- ✅ `SecurityConfig` — configures Spring Security filter chain
- ✅ `JobDispatcher` — enqueues jobs (DB write + @Async dispatch, see ADR-023)
- ❌ Job definitions (`CalendarJobs`) — belong in shared (contracts)
- ❌ Permission evaluators — belong in infrastructure (Spring Security)

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
| `EmailService` (send) | External API? | `platform/integration/email/application/service/` | Listmonk adapter |
| `PasswordResetEmail` builder | Domain logic? | `domain/auth/application/email/` | Auth domain concern |
| `UserPermission` | Spring Security? | `infrastructure/security/permission/` | PermissionEvaluator |
| `JwtTokenGenerator` | Spring Security? | `infrastructure/security/` | JWT infrastructure |
| `CalendarJobs` definitions | Contract? | `shared/job/` | Job payload contracts |
| `SyncEventToCalendarJob` handler | External API? | `platform/integration/calendar/application/job/` | Google Calendar adapter |
| `GoogleCalendarAdapter` | External API? | `platform/integration/calendar/adapter/` | Anti-Corruption Layer |
| `SecurityConfig` | Spring config? | `platform/config/` | Spring configuration |
| `UserActivationEmail` builder | Domain logic? | `domain/auth/application/email/` | Auth domain concern |
| `RecoveryEmailJob` handler | External API? | `platform/integration/email/application/job/` | Email delivery job |
| `EmailRepository` | Owns state (email outbox)? | `platform/integration/email/persistence/repository/` | Structured integration |
| `JobManagementController` | Exposes endpoints? | `platform/integration/job/web/` | Structured integration web layer |
| `MockContactAdapter` | Test/dev mock? | `platform/integration/mock/` | Mock adapter (never production) |

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

### Tests 4–10: Integration Module Consistency (PlatformConsistencyArchitectureTest)

These tests enforce the canonical 4-sub-package structure within every integration module.

| Group | Rule | Package constant |
|-------|------|-----------------|
| E1 | `*Adapter` classes (outside mock) must reside in `..adapter..` | `PLATFORM_ADAPTER` |
| E2 | `*Client` classes (outside mock) must reside in `..adapter..` | `PLATFORM_ADAPTER` |
| F1 | `@Service` beans (outside adapter/mock/queue) must reside in `..application..` | `PLATFORM_APPLICATION` |
| G1 | `*DTO` classes must reside in `..web.dto..` | `PLATFORM_WEB_DTO` |
| H1 | Spring bean `*Scheduler` classes must reside in `..application..` | `PLATFORM_APPLICATION` |
| I1 | Concrete `*Job` classes must reside in `..application.job..` | `APPLICATION_JOB` |
| J1 | `queue/` classes must not access platform repositories directly | `PLATFORM_ANY_REPOSITORY` |

---

## Guidelines

### DO:
- ✅ Place job definitions (contracts) in `shared/job/`
- ✅ Place job handlers (implementations) in `platform/integration/{system}/application/job/`
- ✅ Place ACL adapters and HTTP clients in `platform/integration/{system}/adapter/`
- ✅ Place `@Service` beans in `platform/integration/{system}/application/`
- ✅ Place schedulers in `platform/integration/{system}/application/`
- ✅ Place inbound use-case ports in `platform/integration/{system}/port/in/` (one per use-case group) and their implementations in `application/`
- ✅ Place outbound ACL ports in `platform/integration/{system}/port/out/` and their vendor implementations in `adapter/`
- ✅ Place DTOs in `platform/integration/{system}/web/dto/`
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
- ❌ Place `*Adapter` or `*Client` classes at the module root (use `adapter/` sub-package)
- ❌ Access repositories directly from `queue/` classes (use the service layer)
- ❌ Split one inbound use case across multiple `port/in` interfaces, or add a port just to read/write a single column

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
