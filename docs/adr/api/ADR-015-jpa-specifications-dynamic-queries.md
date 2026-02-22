# ADR-015: JPA Specifications and Dynamic Queries

## Status
Accepted

## Context
Applications need to support dynamic queries with optional filters, sorting, and complex criteria. Requirements include:
- Dynamic WHERE clauses based on user input
- Reusable query fragments
- Type-safe query construction
- Complex joins and predicates
- Support for search/filter endpoints
- Maintainable query logic

Traditional approaches:
- **String-based queries (JPQL/SQL)**: Not type-safe, hard to compose dynamically
- **Query methods with @Query**: Verbose, not reusable, limited composability
- **Criteria API directly**: Low-level, boilerplate-heavy
- **QueryDSL**: External dependency, code generation required

## Decision
We adopt **JPA Specifications** (Spring Data JPA) for dynamic queries with **query objects** in the application layer for search criteria.

### Architecture

**Package Structure:**
```
domain/{domain-name}/
├── web/
│   ├── dto/
│   │   └── request/
│   │       └── {Entity}SearchParams.kt  # HTTP query parameters
│   └── mapping/
│       └── QueryMappings.kt             # Maps params → query objects
├── application/
│   └── query/
│       └── {Entity}Query.kt             # Domain query object
└── persistence/
    ├── {Entity}.kt
    ├── repository/
    │   └── {Entity}Repository.kt
    └── spec/
        └── {Entity}Specifications.kt
```

**Components:**
1. **Specification Objects** (persistence/spec/): Reusable JPA query fragments
2. **Query Objects** (application/query/): Domain-level search criteria
3. **Search Params** (web/dto/): HTTP query parameters from controllers
4. **JpaSpecificationExecutor**: Repository interface for specifications

**Data Flow:**
```
HTTP Query Params (web)
    ↓ mapping
Query Object (application)
    ↓ fromQuery()
Specification (persistence)
    ↓ findAll()
Entities
```

### Implementation Pattern

**1. Query Object (Application Layer - Domain Search Criteria):**
```kotlin
// domain/user/application/query/UserQuery.kt
data class UserQuery(
    val isMember: Boolean? = null,
    val username: String? = null,
    val email: String? = null,
    val roles: Set<Role>? = null,
    val enabled: Boolean? = null
) {
    companion object {
        fun empty() = UserQuery()
    }
}
```

**1b. Search Parameters (Web Layer - HTTP Query Params):**
```kotlin
// domain/user/web/dto/request/UserSearchParams.kt
data class UserSearchParams(
    val isMember: Boolean?,
    val username: String?,
    val email: String?
)

// domain/user/web/mapping/QueryMappings.kt
fun UserSearchParams.toQuery() = UserQuery(
    isMember = this.isMember,
    username = this.username,
    email = this.email
)
```

**2. Specifications Object:**
```kotlin
// domain/user/persistence/spec/UserSpecifications.kt
object UserSpecifications {

    // Simple predicate
    fun hasMemberAuthority(): Specification<User> {
        return hasAuthorityAtLeast(Role.MEMBER)
    }

    // Predicate with parameter
    fun hasAuthorityAtLeast(base: Role): Specification<User> {
        val allowed: MutableSet<Role> = Role.allThatInherit(base)

        return Specification { root, query, _ ->
            query.distinct(true)
            val rolesJoin = root.join<User, Role>("roles", JoinType.INNER)
            rolesJoin.`in`(allowed)
        }
    }

    // Boolean filter
    fun hasMemberRole(isMember: Boolean): Specification<User> {
        return Specification { root, query, cb ->
            query.distinct(true)
            val rolesJoin = root.join<Any, Any>("roles", JoinType.INNER)
            if (isMember) {
                rolesJoin.`in`(EnumSet.of(Role.MEMBER))
            } else {
                cb.not(rolesJoin.`in`(EnumSet.of(Role.MEMBER)))
            }
        }
    }

    // Composite specification from query object
    fun fromQuery(query: UserQuery, user: CurrentUser?): Specification<User> {
        var spec = Specification<User> { _, _, cb -> cb.conjunction() }

        query.isMember?.let {
            spec = spec.and(hasMemberRole(it))
        }

        query.username?.let {
            spec = spec.and(usernameContains(it))
        }

        query.enabled?.let {
            spec = spec.and(isEnabled(it))
        }

        return spec
    }
}
```

**3. Repository Interface:**
```kotlin
// domain/user/persistence/repository/UserRepository.kt
interface UserRepository :
    JpaRepository<User, Long>,
    JpaSpecificationExecutor<User> {

    // Standard query methods
    fun findByUsername(username: String): User?
    fun existsByEmail(email: String): Boolean
}
```

**4. Service Usage:**
```kotlin
@Service
class UserService(
    private val repository: UserRepository
) {

    // Simple specification
    fun findAllMembers(): List<User> {
        return repository.findAll(UserSpecifications.hasMemberAuthority())
    }

    // Composite specification from query object
    fun findAllByQuery(query: UserQuery, user: CurrentUser?): List<User> {
        val spec = UserSpecifications.fromQuery(query, user)
        return repository.findAll(spec)
    }

    // With pagination
    fun findAllByQueryPaginated(
        query: UserQuery,
        pageable: Pageable
    ): Page<User> {
        val spec = UserSpecifications.fromQuery(query, null)
        return repository.findAll(spec, pageable)
    }

    // Combining specifications
    fun findEnabledMembers(): List<User> {
        val spec = UserSpecifications.hasMemberAuthority()
            .and(UserSpecifications.isEnabled())
        return repository.findAll(spec)
    }
}
```

**5. Controller Usage:**
```kotlin
@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) isMember: Boolean?,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) email: String?,
        pageable: Pageable
    ): Page<UserResponse> {
        // Map query params to query object
        val query = UserQuery(
            isMember = isMember,
            username = username,
            email = email
        )
        return userService.findAllByQueryPaginated(query, pageable)
            .map { it.asResponse() }
    }

    // OR use search params DTO:
    @GetMapping("/search-v2")
    fun searchV2(
        params: UserSearchParams,
        pageable: Pageable
    ): Page<UserResponse> {
        val query = params.toQuery()  // Web DTO → Query object
        return userService.findAllByQueryPaginated(query, pageable)
            .map { it.asResponse() }
    }
}
```

## Specification Patterns

### Basic Predicates

**Equality:**
```kotlin
fun hasUsername(username: String): Specification<User> {
    return Specification { root, _, cb ->
        cb.equal(root.get<String>("username"), username)
    }
}
```

**Like (String matching):**
```kotlin
fun usernameContains(substring: String): Specification<User> {
    return Specification { root, _, cb ->
        cb.like(
            cb.lower(root.get("username")),
            "%${substring.lowercase()}%"
        )
    }
}
```

**Boolean:**
```kotlin
fun isEnabled(): Specification<User> {
    return Specification { root, _, cb ->
        cb.isTrue(root.get("enabled"))
    }
}
```

**Null checks:**
```kotlin
fun hasNoEmail(): Specification<User> {
    return Specification { root, _, cb ->
        cb.isNull(root.get<String>("email"))
    }
}
```

**Date ranges:**
```kotlin
fun createdAfter(date: LocalDateTime): Specification<User> {
    return Specification { root, _, cb ->
        cb.greaterThan(root.get("createdAt"), date)
    }
}

fun createdBetween(start: LocalDateTime, end: LocalDateTime): Specification<User> {
    return Specification { root, _, cb ->
        cb.between(root.get("createdAt"), start, end)
    }
}
```

### Joins

**Inner Join:**
```kotlin
fun hasRole(role: Role): Specification<User> {
    return Specification { root, query, cb ->
        query.distinct(true)
        val rolesJoin = root.join<User, Role>("roles", JoinType.INNER)
        cb.equal(rolesJoin, role)
    }
}
```

**Left Join:**
```kotlin
fun hasAddress(): Specification<User> {
    return Specification { root, query, _ ->
        query.distinct(true)
        root.join<User, Address>("addresses", JoinType.LEFT)
        // Just joining is enough to filter for existence
    }
}
```

### Combining Specifications

**AND:**
```kotlin
fun findEnabledMembers(): List<User> {
    val spec = isEnabled().and(hasMemberAuthority())
    return repository.findAll(spec)
}
```

**OR:**
```kotlin
fun findByUsernameOrEmail(value: String): List<User> {
    val spec = hasUsername(value).or(hasEmail(value))
    return repository.findAll(spec)
}
```

**NOT:**
```kotlin
fun findNonMembers(): List<User> {
    val spec = Specification.not(hasMemberAuthority())
    return repository.findAll(spec)
}
```

## Consequences

### Positive
- **Type-safe**: Compile-time checking of entity properties
- **Composable**: Specifications can be combined with and/or/not
- **Reusable**: Specifications are reusable across services
- **Testable**: Can unit test specifications independently
- **Spring Data integration**: Works seamlessly with pagination/sorting
- **Dynamic**: Build queries at runtime based on user input
- **Maintainable**: Query logic centralized in specification objects
- **No code generation**: Pure JPA Criteria API (unlike QueryDSL)
- **Proper layering**: Query objects in application layer, not persistence
- **Interface-agnostic**: Query objects can be used from REST, GraphQL, CLI
- **Clean architecture**: Persistence doesn't depend on interface concerns

### Negative
- **Learning curve**: Developers must understand JPA Criteria API
- **Verbosity**: More code than simple @Query methods
- **Debugging**: Harder to see generated SQL during development
- **Performance**: Can generate suboptimal queries (N+1, Cartesian products)
- **IDE support**: Limited autocomplete for criteria paths
- **Extra mapping**: Web params → Query object adds a mapping step

### Trade-offs
- **Type-safety vs Simplicity**: Specifications vs string-based JPQL
- **Reusability vs Directness**: Specification objects vs inline queries
- **Dynamic vs Static**: Runtime composition vs compile-time methods
- **Layer separation vs Convenience**: Query objects vs direct filters

## Guidelines

### Specification Organization

**DO:**
- ✅ Create `{Entity}Specifications` object in `persistence/spec/`
- ✅ Make specifications `object` (singleton) with factory methods
- ✅ Name methods clearly: `hasMemberRole()`, `usernameContains()`
- ✅ Use `fromQuery()` pattern for composite specifications
- ✅ Call `query.distinct(true)` when joining collections
- ✅ Use lowercase for case-insensitive string matching
- ✅ Return `Specification<Entity>` from factory methods

**DON'T:**
- ❌ Put specifications in service or repository layer
- ❌ Create mutable specification builders (use immutable composition)
- ❌ Mix business logic with query logic
- ❌ Forget `distinct(true)` when joining ElementCollections
- ❌ Hardcode values (accept parameters instead)

### Query Objects (Application Layer)

**DO:**
- ✅ Create query objects in `application/query/`
- ✅ Use data classes with nullable properties (null = not filtering)
- ✅ Keep queries simple (primitive types, enums, IDs, value objects)
- ✅ Make query objects immutable
- ✅ Provide companion object with `empty()` factory
- ✅ Name clearly: `UserQuery`, `EventQuery`

**DON'T:**
- ❌ Put query objects in persistence layer (dependency violation)
- ❌ Include JPA-specific concerns (FetchType, joins)
- ❌ Add validation logic (query objects are search criteria, not commands)
- ❌ Use mutable properties
- ❌ Include entity references (use IDs instead)

### Search Parameters (Web Layer)

**DO:**
- ✅ Create search params DTOs in `web/dto/request/`
- ✅ Map search params → query objects in `web/mapping/`
- ✅ Use extension functions: `fun SearchParams.toQuery()`
- ✅ Validate web input before mapping
- ✅ Document HTTP query parameter behavior

**DON'T:**
- ❌ Pass web DTOs directly to services
- ❌ Skip mapping step (web → application)
- ❌ Put business logic in search params
- ❌ Use search params in application layer

### Repository Usage

**DO:**
- ✅ Extend `JpaSpecificationExecutor<Entity>`
- ✅ Use `findAll(spec)` for lists
- ✅ Use `findAll(spec, pageable)` for pagination
- ✅ Combine specifications with `.and()` and `.or()`
- ✅ Use standard query methods for simple queries

**DON'T:**
- ❌ Create custom methods when specification suffices
- ❌ Return specifications from repositories
- ❌ Mix @Query and specifications for same use case

### Performance

**DO:**
- ✅ Use `query.distinct(true)` to avoid duplicates
- ✅ Use `@EntityGraph` for fetch optimization
- ✅ Profile queries with `show-sql: true`
- ✅ Index filtered columns
- ✅ Use pagination for large result sets

**DON'T:**
- ❌ Join multiple collections without distinct
- ❌ Fetch entire object graphs unnecessarily
- ❌ Ignore N+1 query warnings
- ❌ Skip query profiling

## Testing

**Unit Test Specifications:**
```kotlin
@DataJpaTest
class UserSpecificationsTest {

    @Autowired
    lateinit var repository: UserRepository

    @Test
    fun `hasMemberRole should find users with MEMBER role`() {
        // Given
        val member = User().apply {
            username = "member"
            roles = mutableSetOf(Role.MEMBER)
        }
        val user = User().apply {
            username = "user"
            roles = mutableSetOf(Role.USER)
        }
        repository.saveAll(listOf(member, user))

        // When
        val spec = UserSpecifications.hasMemberRole(true)
        val result = repository.findAll(spec)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].username).isEqualTo("member")
    }

    @Test
    fun `fromQuery should apply multiple filters`() {
        // Given
        val query = UserQuery(
            isMember = true,
            username = "john"
        )

        // When
        val spec = UserSpecifications.fromQuery(query, null)
        val result = repository.findAll(spec)

        // Then
        assertThat(result).allMatch {
            it.username.contains("john") && it.roles.contains(Role.MEMBER)
        }
    }
}
```

## When to Use Specifications

**Use Specifications When:**
- ✅ Query has optional filters (search endpoints)
- ✅ Need to combine multiple predicates dynamically
- ✅ Query logic is reused across services
- ✅ Building complex joins or subqueries
- ✅ Need type-safe query construction

**Use Query Methods When:**
- ✅ Simple, static query (single condition)
- ✅ Not reused elsewhere
- ✅ Query DSL is clearer than Criteria API
- ✅ No dynamic composition needed

**Use @Query When:**
- ✅ Complex SQL that's hard to express with Criteria
- ✅ Performance-critical native SQL
- ✅ Using database-specific features

## Architectural Benefits

### Proper Layer Separation
```
Web Layer: HTTP params → Search params DTO
    ↓ mapping (web/mapping/)
Application Layer: Query objects
    ↓ fromQuery()
Persistence Layer: Specifications → JPA Criteria
    ↓
Database
```

**Why This Matters:**
1. **Persistence independence**: Application layer doesn't know about HTTP
2. **Interface flexibility**: Can add GraphQL, CLI without changing specifications
3. **Clean architecture**: Inner layers (persistence) don't depend on outer layers (web)
4. **Testability**: Can test query logic without web infrastructure
5. **Reusability**: Query objects can be constructed from any interface

## Best Practices

### DO:
- ✅ Use specifications for dynamic search queries
- ✅ Extract reusable predicates as separate methods
- ✅ Use `fromQuery()` pattern for composite queries
- ✅ Call `distinct(true)` when joining collections
- ✅ Test specifications with real database
- ✅ Combine specifications with `.and()` and `.or()`
- ✅ Place query objects in `application/query/`
- ✅ Map web params → query objects in web layer
- ✅ Profile generated SQL queries

### DON'T:
- ❌ Overuse specifications for simple queries
- ❌ Put business logic in specifications
- ❌ Put query objects in persistence layer (dependency violation)
- ❌ Pass web DTOs to specifications
- ❌ Return null from specifications
- ❌ Create god-object specifications with all predicates
- ❌ Ignore Cartesian products from multiple joins
- ❌ Mix specification and @Query approaches
- ❌ Skip pagination for large result sets

## Related ADRs
- [ADR-001: Multi-Layered Domain-Driven Architecture](ADR-001-multi-layered-domain-driven-architecture.md) - Package structure
- [ADR-007: Repository Pattern and JPA](ADR-007-repository-pattern-and-jpa.md) - Repository interface
- [ADR-011: Testing Strategy](ADR-011-testing-strategy.md) - Testing approach

## References
- Spring Data JPA Specifications Documentation
- JPA Criteria API Reference
- Domain-Driven Design: Repository Pattern
- Effective JPA Query Design
