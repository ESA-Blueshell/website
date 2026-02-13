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

## Guidelines

### DO:
- ✅ Use entity references in business logic
- ✅ Make ID properties computed (no @Column)
- ✅ Use `private set` on associations
- ✅ Use aggregate methods for bidirectional updates
- ✅ Use `getReferenceById()` for lazy loading
- ✅ Use underscore only for collection backing fields

### DON'T:
- ❌ Set ID fields directly
- ❌ Add @Column to computed ID properties
- ❌ Clear/add to collections directly (use aggregate methods)
- ❌ Use dual fields (entity + ID)
- ❌ Use underscore for regular properties

## Consequences
- **Positive**: Single source of truth, type-safe, JPA-aligned
- **Negative**: Must resolve entities, not just IDs

## References
- JPA Best Practices
- Domain-Driven Design: Aggregates
- Kotlin Property Patterns
