# ADR-018: Data Ownership in Modular Monolith

## Status
Accepted

## Context

The application is a modular monolith with multiple bounded contexts sharing a single MariaDB database. While microservices typically enforce "Database per Service" with physical separation ([microservices.io][1]), a monolith requires **logical** boundaries within the shared database.

Martin Fowler warns that sharing a database across applications (or bounded contexts) creates coupling through the schema and makes changes risky and expensive ([martinfowler.com][2]). Even within a monolith, unrestricted cross-domain database access leads to:

- **Coupling**: Domains depend on each other's table structure
- **Change resistance**: Schema changes affect multiple domains
- **Unclear ownership**: Multiple domains modifying the same tables
- **Testing complexity**: Tests must account for cross-domain dependencies
- **Migration risk**: Hard to extract domains into services later

## Decision

We establish **clear data ownership boundaries** within our shared database:

### Ownership Rules

1. **Each bounded context owns its tables**
   - Owns: auth_*, user_*, event_*, committee_*, membership_*, etc.
   - Only the owning domain's persistence layer writes to these tables

2. **Cross-domain data access via services, not direct queries**
   - Other domains call application services (e.g., `UserService`)
   - Or subscribe to domain events for eventual consistency
   - No direct repository access across domains

3. **Foreign key references are allowed but constrained**
   - FK references to stable entities (User, Committee) are acceptable
   - Referenced via entity relationships, not raw IDs
   - Changes to referenced entities communicated via events

4. **Read projections for complex cross-domain queries**
   - Build denormalized views for cross-domain reporting
   - Subscribe to domain events to maintain projections
   - Eventual consistency is acceptable

### Database Organization

```
MariaDB Schema: blueshell
├── auth domain tables
│   ├── recovery_tokens
│   └── activation_tokens
│
├── user domain tables
│   ├── users (canonical)
│   └── addresses
│
├── event domain tables
│   ├── events
│   ├── event_signups
│   ├── event_banners
│   └── surveys
│
├── committee domain tables
│   ├── committees
│   └── committee_members
│
├── membership domain tables
│   └── memberships
│       └── FK → users.id
│
├── contribution domain tables
│   ├── contributions
│   │   └── FK → users.id
│   └── contribution_periods
│
├── blog domain tables
│   └── blog_posts
│
└── sponsor domain tables
    └── sponsors

Cross-Cutting:
├── flyway_schema_history (migrations)
└── spring_session (if used)
```

### Data Access Patterns

**Pattern 1: Service-to-Service Calls (Synchronous)**
```kotlin
// Membership domain needs user data
@Service
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val userService: UserService  // Cross-domain service call
) {
    fun createMembership(userId: Long): Membership {
        val user = userService.findById(userId)  // Validate user exists
        val membership = Membership()
        membership.user = user  // FK reference
        return membershipRepository.save(membership)
    }
}
```

**Pattern 2: Event-Driven Updates (Asynchronous)**
```kotlin
// Contribution domain reacts to user changes
@Component
class ContributionEventListener(
    private val contributionService: ContributionService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserActivated(event: UserActivated) {
        // Update contribution records when user is activated
        contributionService.processActivation(event.userId)
    }
}
```

**Pattern 3: Read Projections (Reporting)**
```kotlin
// Reporting service builds denormalized view
@Service
class ReportingService(
    private val jdbcTemplate: JdbcTemplate
) {
    // Acceptable: Read-only cross-domain query for reporting
    fun getMembershipReport(): List<MembershipReportDTO> {
        return jdbcTemplate.query("""
            SELECT u.username, m.start_date, m.member_type
            FROM users u
            JOIN memberships m ON u.id = m.user_id
            WHERE m.end_date IS NULL
        """) { rs, _ ->
            MembershipReportDTO(
                username = rs.getString("username"),
                startDate = rs.getDate("start_date").toLocalDate(),
                memberType = MemberType.valueOf(rs.getString("member_type"))
            )
        }
    }
}
```

**Anti-Pattern: Direct Repository Access**
```kotlin
// ❌ BAD: Membership domain directly querying user repository
@Service
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val userRepository: UserRepository  // ❌ Wrong layer crossing!
) {
    fun createMembership(userId: Long): Membership {
        val user = userRepository.findById(userId)  // ❌ Direct repository access
        // ...
    }
}
```

## Consequences

### Positive
- **Clear ownership**: Each table has a single writer
- **Maintainability**: Schema changes localized to owning domain
- **Testability**: Domains can be tested with focused data setup
- **Future-proof**: Easier to extract domains into services
- **Explicit integration**: Cross-domain access is visible and reviewable
- **Event-driven benefits**: Loose coupling via events

### Negative
- **Eventual consistency**: Event-driven updates may lag
- **Service calls overhead**: Cross-domain lookups require service calls
- **Complexity**: More code than direct table joins
- **Developer discipline**: Easy to violate without enforcement

### Trade-offs
- **Coupling vs Convenience**: Less coupling, more indirection
- **Consistency vs Autonomy**: Immediate consistency via services, eventual consistency via events

## Guidelines

### DO:
- ✅ Access cross-domain data via application services
- ✅ Use domain events for cross-domain state changes
- ✅ Use FK references to stable entities (User, Committee)
- ✅ Build read projections for complex cross-domain queries
- ✅ Use `@Transactional` for service-to-service calls
- ✅ Prefix tables with domain name (e.g., `event_signups`)
- ✅ Document cross-domain dependencies in ADR-017 Context Map

### DON'T:
- ❌ Access another domain's repository directly
- ❌ Write to tables owned by another domain
- ❌ Couple domains through shared mutable tables
- ❌ Use direct SQL joins across domains (except read projections)
- ❌ Skip service layer for cross-domain data access
- ❌ Reference unstable entities across domains

### Foreign Key Guidelines

**Allowed FK References:**
```kotlin
// ✅ GOOD: Reference to stable, canonical entity
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
var user: User = user
    private set
```

**Avoid FK References:**
```kotlin
// ⚠️ CAUTION: Reference to volatile entity
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "event_id", nullable = false)
var event: Event = event  // Event structure changes frequently
    private set

// Alternative: Use event ID + service lookup
val eventId: Long
fun loadEvent(eventService: EventService): Event {
    return eventService.findById(eventId)
}
```

### Cross-Domain Query Patterns

**Pattern 1: Service Call (Strong Consistency)**
```kotlin
// When: Immediate consistency required
// Cost: N+1 query potential
val user = userService.findById(membership.userId)
```

**Pattern 2: Event Subscription (Eventual Consistency)**
```kotlin
// When: Updates can be asynchronous
// Benefit: Loose coupling
@EventListener
fun onUserUpdated(event: UserUpdated) {
    // Update local cache/projection
}
```

**Pattern 3: Entity Reference (Lazy Loading)**
```kotlin
// When: Related entity needed occasionally
// Benefit: JPA handles loading
val user = membership.user  // Lazy-loaded via FK
```

**Pattern 4: Read Projection (Reporting)**
```kotlin
// When: Complex cross-domain reporting
// Benefit: Optimized queries
// Cost: Maintain projection
jdbcTemplate.query("SELECT ... FROM users u JOIN memberships m ...")
```

## Data Ownership Matrix

| Domain | Owns Tables | References (FK) | Subscribes To Events |
|--------|-------------|-----------------|----------------------|
| **Auth** | recovery_tokens, activation_tokens | users | UserCreated |
| **User** | users, addresses | - | - |
| **Event** | events, event_signups, event_banners, surveys | users, committees | UserUpdated, CommitteeUpdated |
| **Committee** | committees, committee_members | users | UserUpdated |
| **Membership** | memberships | users | UserCreated, UserActivated |
| **Contribution** | contributions, contribution_periods | users | UserUpdated, MembershipChanged |
| **Blog** | blog_posts | users | UserUpdated |
| **Sponsor** | sponsors | - | - |

## Enforcement

### ArchUnit Rules (Recommended Addition)

```kotlin
@ArchTest
fun `repositories should only be accessed within their domain`(classes: JavaClasses) {
    classes()
        .that().resideInAPackage("..domain.membership..")
        .should().onlyAccessClassesThat(
            resideOutsideOf("..domain.user.persistence.repository..")
                .or(resideInAPackage("..domain.membership.."))
        )
        .check(classes)
}
```

### Code Review Checklist

When reviewing cross-domain data access:
- [ ] Cross-domain access goes through application service (not repository)
- [ ] FK references documented in ADR-017 Context Map
- [ ] Event subscriptions documented
- [ ] Read projections justified (reporting use case)
- [ ] No writes to other domain's tables

## Migration Path

For existing violations:

1. **Identify direct repository access**
   ```kotlin
   // Find: userRepository used outside user domain
   grep -r "userRepository" domain/*/application
   ```

2. **Replace with service calls**
   ```kotlin
   // Before:
   val user = userRepository.findById(userId)

   // After:
   val user = userService.findById(userId)
   ```

3. **Consider event-driven alternatives**
   ```kotlin
   // If immediate consistency not needed, use events
   @EventListener
   fun onUserCreated(event: UserCreated) { ... }
   ```

4. **Document in Context Map**
   - Update ADR-017 with new service dependencies

## Examples

### Example 1: Membership Domain Accessing User

**Correct (Service Call):**
```kotlin
@Service
class MembershipService(
    private val membershipRepository: MemberRepository,
    private val userService: UserService  // ✅ Service dependency
) {
    fun createMembership(userId: Long): Membership {
        val user = userService.findById(userId)  // ✅ Service call
        val membership = Membership()
        membership.user = user  // ✅ Entity reference
        return membershipRepository.save(membership)
    }
}
```

**Incorrect (Direct Repository):**
```kotlin
@Service
class MembershipService(
    private val membershipRepository: MemberRepository,
    private val userRepository: UserRepository  // ❌ Repository dependency
) {
    fun createMembership(userId: Long): Membership {
        val user = userRepository.findById(userId)  // ❌ Direct repository access
        // ...
    }
}
```

### Example 2: Event Domain Reacting to User Changes

**Event-Driven Approach:**
```kotlin
// Event domain listens to User domain events
@Component
class EventUserListener(
    private val eventService: EventService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserDeactivated(event: UserDeactivated) {
        // Cancel event sign-ups for deactivated user
        eventService.cancelSignUpsForUser(event.userId)
    }
}
```

### Example 3: Reporting with Read Projections

**Acceptable Cross-Domain Join:**
```kotlin
@Service
class ReportingService(
    private val jdbcTemplate: JdbcTemplate
) {
    // Read-only reporting query (acceptable)
    fun getActiveContributors(): List<ContributorReportDTO> {
        return jdbcTemplate.query("""
            SELECT
                u.username,
                u.email,
                c.contribution_period_id,
                c.created_at
            FROM users u
            JOIN contributions c ON u.id = c.user_id
            WHERE u.enabled = true
            ORDER BY c.created_at DESC
        """) { rs, _ ->
            ContributorReportDTO(
                username = rs.getString("username"),
                email = rs.getString("email"),
                periodId = rs.getLong("contribution_period_id"),
                contributedAt = rs.getTimestamp("created_at").toInstant()
            )
        }
    }
}
```

## References

- Martin Fowler, "Integration Database": https://martinfowler.com/bliki/IntegrationDatabase.html
- Microservices.io, "Database per service": https://microservices.io/patterns/data/database-per-service.html
- Sam Newman, "Building Microservices" (Chapter on Data Management)
- Vaughn Vernon, "Implementing Domain-Driven Design" (Strategic design patterns)

[1]: https://microservices.io/patterns/data/database-per-service.html "Pattern: Database per service"
[2]: https://martinfowler.com/bliki/IntegrationDatabase.html "Integration Database - Martin Fowler"
