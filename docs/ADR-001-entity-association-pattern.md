# ADR-001: Entity Association Pattern

## Status
Accepted

## Context
The API codebase had inconsistent patterns for handling JPA entity associations. There were multiple ways to set relationships:
1. Setting ID fields directly (e.g., `event.committeeId = 5`)
2. Setting entity references (e.g., `event.committee = committeeRef`)
3. Both approaches coexisting with complex synchronization logic

This dual-access pattern created:
- Maintenance burden with bidirectional sync between ID and entity fields
- Ambiguity in which approach to use when
- Inconsistent mapper implementations across the codebase
- Complex service layer logic to handle both patterns

## Decision
We adopt a **single source of truth** pattern for entity associations:

### Core Principle
**Associations are managed exclusively through entity references in mappers and services. ID properties are read-only and computed from entity references.**

### Pattern by Association Type

#### Many-to-One (e.g., Event → Committee)
**Entity Layer:**
```kotlin
// Required association
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "committee_id", nullable = false)
private var _committee: Committee? = null
var committee: Committee
    get() = requireNotNull(_committee) { "Committee is required" }
    set(value) { _committee = value }

// Read-only ID with assertion - NO @Column annotation (no backing field)
val committeeId: Long 
    get() = requireNotNull(_committee?.id) { "committeeId is required" }

// Optional association
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private var _user: User? = null
var user: User?
    get() = _user
    set(value) { _user = value }

// Read-only ID, nullable - NO @Column annotation (no backing field)
val userId: Long? 
    get() = _user?.id
```

**Key Points:**
- **No `@Column` annotation on ID properties**: They are computed from the entity reference, not database-backed fields
- **Required associations**: Use `requireNotNull()` in both entity getter and ID getter for fail-fast behavior
- **Optional associations**: Return nullable types, simple delegation to entity ID

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
private var _signUpForm: Survey? = null
var signUpForm: Survey?
    get() = _signUpForm
    set(value) { _signUpForm = value }

// Read-only ID, nullable - NO @Column annotation (no backing field)
val signUpFormId: Long? get() = _signUpForm?.id
```

**Key Points:**
- **No `@Column` annotation on ID property**: Computed from entity reference only
- Optional one-to-one relationships typically use nullable types throughout

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
@OneToMany(mappedBy = "_survey", cascade = [CascadeType.ALL], orphanRemoval = true)
private val _questions: MutableSet<Question> = linkedSetOf()
val questions: MutableSet<Question>
    get() = _questions
```

**Mapper:**
```kotlin
fun SurveyDTO.asEntity(survey: Survey = Survey()): Survey {
    survey.questions.addAll(questions!!.map { it.asEntity() })
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
    get() = requireNotNull(_committee?.id) { "committeeId is required" }
```

**Correct implementation:**
```kotlin
// ✅ CORRECT - No @Column annotation
val committeeId: Long
get() = requireNotNull(_committee?.id) { "committeeId is required" }
```

## Consequences

### Positive
- **Predictable**: Always use entities in business logic, never IDs
- **Observable**: Clear ownership and lifecycle management
- **Maintainable**: Single pattern across entire API
- **JPA-aligned**: Leverages entity graph navigation
- **Type-safe**: Compiler enforces entity references
- **Reduced complexity**: No synchronization logic between IDs and entities
- **No backing fields**: ID properties are computed, eliminating potential for drift
- **Fail-fast behavior**: Required associations use assertions, catching issues early
- **Cleaner code**: Single-line computed properties instead of complex getter/setter logic

### Negative
- **Breaking change**: Requires updating all mappers and some services
- **Learning curve**: Developers must understand entity reference pattern
- **Read-only IDs**: Cannot set associations via ID anymore (feature, not bug)

### Migration Impact
Files updated:
- **Entities**: Event, Question, CommitteeMember, User, Membership, EventSignUp, EventBanner, Answer
- **Mappers**: EventMappings, SurveyMappings, CommitteeMappings, MembershipMappings
- **Services**: EventService, SurveyService, CommitteeService, EventSignUpService

## Examples

### Before (Inconsistent)
```kotlin
// Mapper setting IDs
event.committeeId = committeeId!!

// Entity with dual access and backing field
@Column(name = "committee_id", nullable = false, updatable = false, insertable = false)
var committeeId: Long = 0
    get() = _committee?.id ?: field
    set(value) {
        field = value
        if (value != 0L && value != _committee?.id) {
            _committee = Committee::class.asRef(value)
        }
    }
```

### After (Consistent)
```kotlin
// Mapper setting entity reference
event.committee = Committee::class.asRef(committeeId!!)

// Entity with computed read-only ID (no backing field, no @Column)
val committeeId: Long 
    get() = requireNotNull(_committee?.id) { "committeeId is required" }
```

**Key Differences:**
1. **No `@Column` annotation**: ID property has no backing field
2. **No setter**: ID is truly read-only
3. **Assertion on access**: For required associations, fail fast if entity/ID is not available
4. **Single line**: Much simpler implementation

## References
- JPA Best Practices: Entity relationships should use object references
- Domain-Driven Design: Aggregates manage their own consistency
- Original implementation: Event/Survey/EventSignUp entities
