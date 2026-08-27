# ADR-013: Entity Association Pattern

## Status
Accepted — amended by
[architecture ADR-001](../architecture/ADR-001-application-modules-replace-layers.md)
and [architecture ADR-003](../architecture/ADR-003-package-topology-and-placement-rules.md)
for references that cross a module boundary.

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

## References That Cross a Module Boundary

The patterns above describe associations **inside** one module, where they are
unchanged. Across a module boundary they cannot all hold at once: a module graph
verified free of cycles and a bidirectional association between two modules are
mutually exclusive, because the back-reference is the reverse edge.

### The rule

**An owning-side reference may cross a module boundary. A `mappedBy`
back-reference may not.**

The owning side holds the foreign key, so it is the direction the schema already
runs in and the direction the module graph can follow. The back-reference adds
nothing the schema needs and everything the cycle needs.

```kotlin
// ALLOWED — owning side, holds the FK, crosses file -> user
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "uploader_id", nullable = false)
var uploader: User

// NOT ALLOWED — mappedBy back-reference crossing user -> auth
@OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
private val _recoveryTokens: MutableSet<RecoveryToken> = linkedSetOf()
```

Navigate the removed direction with a repository query instead:
`recoveryTokenRepository.findByUser(user)`.

Two of the back-references this removes are eagerly fetched and cause an N+1 on
list queries, so dropping them fixes a live performance defect rather than
trading one cost for another.

### Reachability

An owning-side reference has to compile, so the referenced entity has to be
reachable from the referring module. It is published through the module's
`entities` named interface and named explicitly by the modules permitted to use
it — never through `api`, which would make every entity permanently public. The
mechanism is in
[architecture ADR-003](../architecture/ADR-003-package-topology-and-placement-rules.md).

### Re-homing a cascade

A dropped back-reference takes its `cascade` with it. Where the cascade was doing
real work, the owning module publishes a participant interface and the modules
that held the collection register implementations:

```kotlin
// user/api
interface UserDeletionParticipant {
    fun onUserDeleted(userId: Long)
}

// user/domain
@Transactional
fun delete(id: Long) {
    participants.forEach { it.onUserDeleted(id) }
    userRepository.delete(user)
}
```

Spring resolves `List<UserDeletionParticipant>` by bean type, so registration is
package-independent — the same mechanism `CompositePermissionEvaluator` already
relies on. The dependency runs from each participant to `user`, which is the
direction that already existed, so no edge is added.

A database-level `ON DELETE CASCADE` is not an alternative here: `User` carries
`@SQLDelete`, so deleting it emits an `UPDATE` and no foreign-key cascade fires.

### Enforcement

An ArchUnit rule asserts that no `mappedBy` field names a type outside its own
module. Without it this rule is convention, and it is the convention that decayed
into fourteen cycles.

## Guidelines

### DO:
- ✅ Use entity references in business logic
- ✅ Make ID properties computed (no @Column)
- ✅ Use `private set` on associations
- ✅ Use aggregate methods for bidirectional updates
- ✅ Fetch entities via a service, not `getReferenceById()`
- ✅ Assign entity references, not IDs
- ✅ Use underscore only for collection backing fields
- ✅ **Let an owning-side reference cross a module boundary, and publish the
  target through `entities`**
- ✅ **Replace a boundary-crossing `mappedBy` collection with a repository query**

### DON'T:
- ❌ Set ID fields directly
- ❌ Add @Column to computed ID properties
- ❌ Clear/add to collections directly (use aggregate methods)
- ❌ Use dual fields (entity + ID)
- ❌ Use underscore for regular properties
- ❌ Inject repositories where a service belongs
- ❌ Pass repositories to mapping functions
- ❌ **Declare a `mappedBy` association whose target is in another module**
- ❌ **Publish an entity through `api` to make a cross-module reference compile**

## Consequences
- **Positive**: Single source of truth, type-safe, JPA-aligned. Across a
  boundary, the object graph now runs the same direction as the foreign keys, so
  the module graph can be acyclic without giving up entity references.
- **Negative**: Must resolve entities, not just IDs. A removed back-reference
  costs a repository query at every site that used to navigate it, and a removed
  cascade costs an explicit participant.
- **Neutral**: The command-handler guidance this record used to carry is gone
  with the handlers themselves, superseded by
  [architecture ADR-002](../architecture/ADR-002-use-case-services-replace-the-command-bus.md).

## References
- JPA Best Practices
- Domain-Driven Design: Aggregates
- Kotlin Property Patterns
