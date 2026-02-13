# ADR-013: Entity Association Pattern

## Status
Accepted

## Context
JPA entity associations can be managed in multiple ways, leading to inconsistency without clear patterns.

## Decision
We use **direct entity references** as the single source of truth, with **computed ID properties** for convenience.

### Pattern by Association Type

**Many-to-One (Required):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "committee_id", nullable = false)
var committee: Committee = committee
    private set

val committeeId: Long
    get() = committee.id  // Computed, no @Column
```

**Many-to-One (Optional):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
var user: User? = null
    private set

val userId: Long?
    get() = user?.id  // Computed, no @Column
```

**One-to-Many:**
```kotlin
@OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true)
private val _questions: MutableSet<Question> = linkedSetOf()

val questions: Set<Question>
    get() = _questions
```

**Aggregate Methods:**
```kotlin
fun replaceMembers(newMembers: List<CommitteeMember>) {
    _members.clear()
    newMembers.forEach { it.committee = this }
    _members.addAll(newMembers)
}
```

## Entity Assignment in Command Handlers

### Pattern
When command handlers need to set entity associations, **fetch the actual entity using a service** and assign the entity reference, not the ID.

**CORRECT - Fetch entity via service:**
```kotlin
@Component
class CreateEventHandler(
    private val eventService: EventService,
    private val committeeService: CommitteeService
) : CommandHandler<CreateEventCommand, Event> {
    override fun handle(command: CreateEventCommand): Event {
        val committee = committeeService.findById(command.committeeId)
        val event = Event()
        event.committee = committee  // ✅ Assign entity
        // ... other fields
        return eventService.create(event)
    }
}
```

**INCORRECT - Don't use repository getReferenceById:**
```kotlin
// ❌ WRONG: Don't inject repositories into handlers
@Component
class CreateEventHandler(
    private val eventService: EventService,
    private val committeeRepository: CommitteeRepository  // ❌ Don't do this
) : CommandHandler<CreateEventCommand, Event> {
    override fun handle(command: CreateEventCommand): Event {
        val event = Event()
        event.committee = committeeRepository.getReferenceById(id)  // ❌ Lazy proxy
        return eventService.create(event)
    }
}
```

**INCORRECT - Don't assign IDs directly:**
```kotlin
// ❌ WRONG: Don't set ID fields
val event = Event()
event.committeeId = command.committeeId  // ❌ ID assignment prohibited
```

### Rationale
- **Services provide business logic**: Fetching via service ensures business rules (permissions, validation) are applied
- **Explicit loading**: Forces deliberate entity loading rather than hidden lazy loading
- **Testability**: Service methods can be mocked; repository references cannot
- **Transaction management**: Services handle transactions properly
- **Error handling**: Services provide domain-specific exceptions (e.g., `CommitteeNotFoundException`)

### Mapping Functions
For complex entity graphs, compute entity references before mapping:

```kotlin
@Component
class CreateEventHandler(
    private val eventService: EventService,
    private val committeeService: CommitteeService,
    private val fileService: FileService
) : CommandHandler<CreateEventCommand, Event> {
    override fun handle(command: CreateEventCommand): Event {
        // Fetch all required entities first
        val committee = committeeService.findById(command.committeeId)
        val banner = command.bannerId?.let { fileService.findById(it) }

        // Then create and populate
        val event = Event()
        event.committee = committee
        event.banner = banner
        // ... other fields

        return eventService.create(event)
    }
}
```

## Guidelines

### DO:
- ✅ Use entity references in business logic
- ✅ Make ID properties computed (no @Column)
- ✅ Use `private set` on associations
- ✅ Use aggregate methods for bidirectional updates
- ✅ **Fetch entities via services in command handlers**
- ✅ **Assign entity references, not IDs**
- ✅ Use underscore only for collection backing fields

### DON'T:
- ❌ Set ID fields directly
- ❌ Add @Column to computed ID properties
- ❌ Clear/add to collections directly (use aggregate methods)
- ❌ Use dual fields (entity + ID)
- ❌ Use underscore for regular properties
- ❌ **Inject repositories into command handlers**
- ❌ **Use `getReferenceById()` in command handlers**
- ❌ **Pass repositories to mapping functions**

## Consequences
- **Positive**: Single source of truth, type-safe, JPA-aligned
- **Negative**: Must resolve entities, not just IDs

## References
- JPA Best Practices
- Domain-Driven Design: Aggregates
- Kotlin Property Patterns
