# ADR-001: Entity Association Pattern

## Status
Accepted

## Context
The API codebase had inconsistent patterns for handling JPA entity associations. There were multiple approaches:
1. Setting ID fields directly (e.g., `event.committeeId = 5`)
2. Setting entity references (e.g., `event.committee = committeeRef`)
3. Using private backing fields with underscore prefixes (`_committee`)
4. Complex synchronization logic between IDs and entities

This created:
- Maintenance burden with bidirectional sync between ID and entity fields
- Ambiguity in which approach to use when
- Inconsistent mapper implementations across the codebase
- Complex service layer logic to handle multiple patterns
- Unfamiliar naming conventions (underscore prefixes) that don't align with standard Kotlin/JPA practices

## Decision
We adopt a **single source of truth** pattern for entity associations using **direct field access with protection**.

### Core Principle
**Associations are managed exclusively through entity references. ID properties are read-only and computed from entity references. Kotlin's visibility modifiers control access, eliminating the need for underscore prefixes.**

### Pattern by Association Type

#### Many-to-One (e.g., Event → Committee)
**Entity Layer:**
```kotlin
// Required association
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "committee_id", nullable = false)
var committee: Committee = committee
    private set

// Read-only ID computed from entity - NO @Column annotation
val committeeId: Long
    get() = committee.id

// Optional association
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
var user: User? = null
    private set

// Read-only ID, nullable - NO @Column annotation
val userId: Long?
    get() = user?.id
```

**Key Points:**
- **Direct field access**: No underscore prefixes, cleaner naming
- **`private set`**: Prevents external mutation while allowing internal updates
- **No `@field:` prefix needed**: JPA annotations apply directly to properties
- **No `@Column` annotation on ID properties**: They are computed from the entity reference
- **Required associations**: Non-nullable types with non-null initialization
- **Optional associations**: Nullable types with null-safe access to IDs

**Mapper:**
```kotlin
fun EventDTO.asEntity(event: Event = Event()): Event {
    event.committee = Committee::class.asRef(committeeId!!)
    // ... other fields
}
```

**Service:** No merge needed (already handled by reference)

#### One-to-One (e.g., Event → Survey)
**Entity Layer:**
```kotlin
// Optional one-to-one with cascade
@OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
@JoinColumn(name = "survey_id")
var signUpForm: Survey? = null
    private set

// Read-only ID, nullable - NO @Column annotation
val signUpFormId: Long?
    get() = signUpForm?.id
```

**Key Points:**
- **Direct property access**: `signUpForm` instead of `_signUpForm`
- **`private set`**: Controlled mutation from outside the entity
- **No `@Column` annotation on ID property**: Computed from entity reference only

**Mapper:**
```kotlin
fun EventDTO.asEntity(event: Event = Event()): Event {
    event.signUpForm = signUpForm?.asEntity()
    // ... other fields
}
```

**Service:** Set bidirectional ref if needed:
```kotlin
private fun mergeAssociations(event: Event) {
    event.signUpForm?.let { survey ->
        survey.questions.forEach { it.survey = survey }
    }
}
```

#### One-to-Many (e.g., Survey → Questions)
**Entity Layer:**
```kotlin
@OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
private val _questions: MutableSet<Question> = linkedSetOf()
val questions: Set<Question>
    get() = _questions
```

**Key Points:**
- **Private mutable collection**: Use underscore prefix ONLY for internal mutable collections
- **Public immutable getter**: Returns immutable type (`Set<T>`) to prevent external mutation
- **`mappedBy` references the property name**: Points to `survey` on the child entity (not `_survey`)
- **This is the ONLY case where underscore is used**: Collections need this pattern to prevent external mutation

**Mapper:**
```kotlin
fun SurveyDTO.asEntity(survey: Survey = Survey()): Survey {
    survey._questions.clear()
    survey._questions.addAll(questions!!.map { it.asEntity() })
    return survey
}
```

**Service:** Set parent ref on each child:
```kotlin
private fun mergeAssociations(survey: Survey) {
    survey.questions.forEach { question ->
        question.survey = survey
    }
}
```

#### Composite Key Entities (e.g., CommitteeMember, EventBanner)
**Entity Layer:**
```kotlin
@Entity
@Table(name = "committee_members")
class CommitteeMember(
    @EmbeddedId
    @AttributeOverrides(AttributeOverride(name = "userId", column = Column(name = "user_id", nullable = false)))
    override var id: Id = Id(),
) : AuditedSoftDeleteEntity(), Identifiable<CommitteeMember.Id> {

    @MapsId("committeeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    var committee: Committee = committee
        private set

    // ID property reads from embedded ID
    val committeeId: Long
        get() = id.committeeId ?: 0

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        private set

    val userId: Long
        get() = id.userId ?: 0

    @Embeddable
    data class Id(
        var committeeId: Long? = null,
        var userId: Long? = null
    ) : Serializable {
        @get:Transient
        val isComplete: Boolean
            get() = committeeId != null && userId != null
    }

    // Internal setter to update both entity and embedded ID
    internal fun setCommittee(value: Committee) {
        committee = value
        id.committeeId = value.id
    }

    internal fun setUser(value: User) {
        user = value
        id.userId = value.id
    }
}
```

**Key Points:**
- **`@EmbeddedId` with `@MapsId`**: Associates entity references with composite key components
- **Internal setters**: Handle bidirectional sync between entity and embedded ID
- **ID properties read from embedded ID**: `val committeeId: Long get() = id.committeeId ?: 0`
- **`isComplete` helper**: Validates that all ID components are present
- **`private set` on properties**: Prevents external direct mutation
- **Package-private internal setters**: Used by mappers to set associations

**Mapper:**
```kotlin
fun CommitteeMemberDTO.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    member.setCommittee(Committee::class.asRef(committeeId!!))
    member.setUser(User::class.asRef(userId!!))
    // ... other fields
}
```

### Kotlin `allOpen` Plugin Configuration

The `kotlin-allopen` plugin makes classes and methods `open` for JPA proxying. Current configuration:

```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

**This configuration is sufficient** because:
1. **It already makes entity classes `open`**: All methods and properties are `open` by default
2. **Properties with `private set` remain accessible to JPA**: The getter is `open`, setter is private
3. **JPA uses reflection**: Private setters don't prevent Hibernate from setting values during entity loading
4. **No additional configuration needed**: The existing setup works with our pattern

**Why `private set` works with JPA:**
- JPA/Hibernate uses reflection to access fields directly
- The `private set` only restricts Kotlin code access
- Database loading and persistence bypass visibility modifiers
- The public getter allows JPA to create proxies for lazy loading

### Service Layer Standard
All services use a consistent `mergeAssociations()` method that:
1. Sets bidirectional parent references for owned entities
2. Replaces transient entities with references when IDs exist
3. Is called before `super.create()` and `super.update()`

### Important: Why No `@Column` Annotation on ID Properties?

The ID properties **must not** have `@Column` annotations because:

1. **No backing field**: They are computed properties derived from entity references
2. **Avoid JPA confusion**: `@Column` tells JPA this is a database-backed field, which it isn't
3. **Prevent dual-mapping**: The `@JoinColumn` on the entity field already maps the foreign key
4. **Read-only guarantee**: Without `@Column`, JPA won't try to persist changes to these properties

**Example of what NOT to do:**
```kotlin
// ❌ WRONG - Don't add @Column to computed ID properties
@Column(name = "committee_id", updatable = false, insertable = false)
val committeeId: Long
    get() = committee.id
```

**Correct implementation:**
```kotlin
// ✅ CORRECT - No @Column annotation
val committeeId: Long
    get() = committee.id
```

## Consequences

### Positive
- **Predictable**: Always use entities in business logic, never IDs
- **Observable**: Clear ownership and lifecycle management
- **Maintainable**: Single pattern across entire API
- **JPA-aligned**: Leverages entity graph navigation
- **Type-safe**: Compiler enforces entity references
- **Reduced complexity**: No synchronization logic between IDs and entities
- **No backing fields for IDs**: ID properties are computed, eliminating potential for drift
- **Idiomatic Kotlin**: Uses `private set` instead of underscore prefixes
- **Familiar pattern**: Aligns with standard Kotlin/JPA practices
- **Less verbose**: No explicit getter/setter blocks for most associations
- **Better tooling support**: IDEs understand standard naming without configuration
- **Fail-fast behavior**: Required associations use non-null types, catching issues at compile time

### Negative
- **Breaking change**: Requires updating all entities, mappers, repositories, and entity graphs
- **Learning curve**: Developers must understand entity reference pattern
- **Read-only IDs**: Cannot set associations via ID anymore (feature, not bug)
- **Collection handling**: Still requires underscore prefix for internal mutable collections
- **Composite keys need special handling**: Internal setters required for `@MapsId` synchronization

### Migration Impact
Files to update:
- **Entities**: Remove underscore prefixes, add `private set` to associations
- **Mappers**: Update property names (remove underscores), use internal setters for composite keys
- **Services**: Update any direct field access to use new property names
- **Repositories**: Update any JPQL/entity graph references to remove underscore prefixes
- **Entity Graphs**: Update attribute paths to use new naming scheme

## Examples

### Before (Underscore Pattern)
```kotlin
// Entity with underscore prefix
@field:ManyToOne(fetch = FetchType.LAZY, optional = false)
@field:JoinColumn(name = "committee_id", nullable = false)
private var _committee: Committee? = null
var committee: Committee
    get() = requireNotNull(_committee) { "Committee is required" }
    set(value) {
        _committee = value
    }

val committeeId: Long
    get() = requireNotNull(_committee?.id) { "committeeId is required" }
```

### After (Direct Access with Protection)
```kotlin
// Entity with direct access and private set
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "committee_id", nullable = false)
var committee: Committee = committee
    private set

val committeeId: Long
    get() = committee.id
```

**Key Differences:**
1. **No `@field:` prefix**: Annotations apply directly to properties
2. **No underscore prefix**: Use standard Kotlin naming (`committee` not `_committee`)
3. **No explicit getter/setter**: Kotlin's `private set` is concise and clear
4. **Non-null types for required associations**: Type system enforces requirements
5. **No `requireNotNull()` needed**: Non-null types fail at construction time
6. **Simpler, more idiomatic**: Aligns with Kotlin best practices

### Collection Pattern (Only Case for Underscore)
```kotlin
// Private mutable, public immutable
@OneToMany(mappedBy = "survey", cascade = [CascadeType.ALL], orphanRemoval = true)
private val _questions: MutableSet<Question> = linkedSetOf()
val questions: Set<Question>
    get() = _questions
```

This is the **only pattern** where underscore prefixes are used, as it's the standard Kotlin idiom for exposing immutable views of mutable collections.

## References
- JPA Best Practices: Entity relationships should use object references
- Domain-Driven Design: Aggregates manage their own consistency
- Kotlin Coding Conventions: Use `private set` for controlled mutability
- Original implementation: Event/Survey/EventSignUp entities
