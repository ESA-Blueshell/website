# ADR-014: Permission Evaluation Strategy

## Status
Accepted

## Context
Applications need fine-grained authorization beyond role-based access control (RBAC). Requirements include:
- Resource-level permissions (e.g., "user can only edit their own profile")
- Domain-specific authorization logic
- Type-safe permission checks
- Integration with Spring Security's @PreAuthorize
- Extensibility for new domains
- Testability of authorization logic

Traditional approaches:
- **Hard-coded checks in controllers**: Scattered logic, hard to test
- **Service-layer authorization**: Mixes business logic with security concerns
- **Global PermissionEvaluator**: Becomes bloated with all domain logic

## Decision
We adopt a **domain-specific permission evaluator pattern** using Spring Security's `PermissionEvaluator` interface with domain-specific implementations.

### Architecture

**Composite Pattern:**
```
CompositePermissionEvaluator (platform/config/permission/)
    ├─> UserPermission (domain/user/web/permission/)
    ├─> AddressPermission (domain/user/web/permission/)
    ├─> MembershipPermission (domain/membership/web/permission/)
    └─> [Other domain evaluators]
```

### Base Implementation

**BasePermissionEvaluator:**
```kotlin
abstract class BasePermissionEvaluator<T : Identifiable<ID>, ID, S : BaseModelService<T, ID, *>>(
    protected val service: S
) {
    val domainType: Class<T>

    init {
        this.domainType = determineDomainType()
    }

    private fun determineDomainType(): Class<T> {
        val resolvedTypes = GenericTypeResolver.resolveTypeArguments(
            javaClass,
            BasePermissionEvaluator::class.java
        )
        check(!(resolvedTypes == null || resolvedTypes.size < 1)) {
            "Unable to determine domain type for ${javaClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return resolvedTypes[0] as Class<T>
    }

    fun supports(domainClass: Class<*>): Boolean {
        return domainType.isAssignableFrom(domainClass)
    }

    abstract fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean

    abstract fun hasPermissionId(
        authentication: Authentication?,
        id: Any?,
        permission: String?
    ): Boolean
}
```

**CompositePermissionEvaluator:**
```kotlin
@Component
class CompositePermissionEvaluator @Autowired constructor(
    private val evaluators: MutableList<BasePermissionEvaluator<*, *, *>?>
) : PermissionEvaluator {

    override fun hasPermission(
        auth: Authentication?,
        target: Any?,
        perm: Any?
    ): Boolean {
        if (target == null || perm == null) return false
        val domainClass = ClassUtils.getUserClass(target.javaClass)
        return evaluators.stream()
            .filter { e -> e!!.supports(domainClass) }
            .findFirst()
            .map { e -> e!!.hasPermission(auth, target, perm.toString()) }
            .orElse(false)
    }

    override fun hasPermission(
        auth: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        perm: Any?
    ): Boolean {
        if (targetId == null || targetType == null || perm == null) return false

        return evaluators.stream()
            .filter { e ->
                val dt = e!!.domainType
                dt.simpleName == targetType || dt.name == targetType
            }
            .findFirst()
            .map { e -> e!!.hasPermissionId(auth, targetId, perm.toString()) }
            .orElse(false)
    }
}
```

### Domain-Specific Evaluator

**Example: UserPermission**
```kotlin
@Component
class UserPermission @Autowired constructor(service: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(service) {

    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val user = entity as User
        val principal = SecurityUtils.principalFrom(authentication)
        return when (permission) {
            "read", "write" -> (principal?.id == user.id)
            else -> false
        }
    }

    override fun hasPermissionId(
        authentication: Authentication?,
        id: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        val targetUser = service.findById(id as Long)
        return hasPermission(authentication, targetUser, permission)
    }
}
```

### Usage in Controllers

**@PreAuthorize with hasPermission:**
```kotlin
@RestController
@Tag(name = "User", description = "User management")
class UserController(
    private val commandBus: CommandBus,
    private val userService: UserService
) {

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'User', 'write')")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest
    ): UserResponse {
        val command = request.asCommand(id)
        val user = commandBus.dispatch(command)
        return user.asResponse()
    }

    @GetMapping("/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'read')")
    fun getAddresses(
        @PathVariable userId: Long
    ): List<AddressResponse> {
        return userService.findById(userId).addresses.map { it.asResponse() }
    }
}
```

**hasPermission Parameters:**
1. `#id` - SpEL expression for method parameter
2. `'User'` - Domain type (simple class name)
3. `'write'` - Permission string

### Package Structure

**Location:**
```
domain/{domain-name}/
└── web/
    └── permission/
        └── {Entity}Permission.kt
```

**Example:**
```
domain/user/web/permission/
├── UserPermission.kt
└── AddressPermission.kt
```

## Consequences

### Positive
- **Separation of concerns**: Authorization logic isolated from business logic
- **Domain-specific**: Each domain owns its authorization rules
- **Type-safe**: Compile-time checking of entity types
- **Testable**: Can unit test permission evaluators independently
- **Composable**: Auto-discovery via Spring DI
- **Reusable**: Same evaluator used in controllers, services, or tests
- **IDE support**: @PreAuthorize expressions benefit from code completion
- **Standard Spring Security**: Leverages built-in framework support

### Negative
- **Learning curve**: Developers must understand Spring Security SpEL
- **Runtime evaluation**: @PreAuthorize expressions evaluated at runtime (typos not caught at compile time)
- **Debuggability**: Can be hard to trace which evaluator is invoked
- **Performance**: Database lookups in `hasPermissionId` (mitigated by caching)
- **No parameter validation**: Invalid permission strings silently return false

### Trade-offs
- **Flexibility vs Performance**: Dynamic evaluation vs static checks
- **Centralization vs Distribution**: Composite pattern vs monolithic evaluator
- **Entity loading**: hasPermissionId requires fetching entity (vs ID-only checks)

## Guidelines

### Permission Naming
- Use simple, consistent verbs: `read`, `write`, `delete`
- Avoid domain-specific jargon unless necessary
- Document available permissions in evaluator comments

### Evaluator Design

**DO:**
- ✅ Place evaluators in `domain/{domain}/web/permission/`
- ✅ Extend `BasePermissionEvaluator<Entity, ID, Service>`
- ✅ Use switch/when for permission strings
- ✅ Return `false` for unknown permissions
- ✅ Check for null authentication/entity/permission
- ✅ Use `SecurityUtils.principalFrom()` to extract user
- ✅ Keep logic simple (ownership, role checks)
- ✅ Document permission strings and their meaning

**DON'T:**
- ❌ Put complex business logic in evaluators
- ❌ Access multiple services (keep it focused)
- ❌ Throw exceptions (return false instead)
- ❌ Perform expensive computations
- ❌ Use mutable state

### Controller Usage

**DO:**
- ✅ Combine with role checks: `hasAuthority('BOARD') || hasPermission(...)`
- ✅ Use method parameters with SpEL: `#id`, `#userId`
- ✅ Use simple class names for domain type: `'User'`, not full package
- ✅ Apply to endpoints that need resource-level checks

**DON'T:**
- ❌ Skip null checks in evaluators (controllers may pass nulls)
- ❌ Use entity objects directly in hasPermission (use IDs instead)
- ❌ Rely on permission checks alone (validate inputs too)

## Testing

**Unit Test:**
```kotlin
class UserPermissionTest {

    private lateinit var userPermission: UserPermission
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userService = mockk()
        userPermission = UserPermission(userService)
    }

    @Test
    fun `should allow user to read own profile`() {
        val user = User().apply { id = 1L }
        val auth = createAuthenticationForUser(1L)

        val result = userPermission.hasPermission(auth, user, "read")

        assertThat(result).isTrue()
    }

    @Test
    fun `should deny user from reading other profile`() {
        val user = User().apply { id = 2L }
        val auth = createAuthenticationForUser(1L)

        val result = userPermission.hasPermission(auth, user, "read")

        assertThat(result).isFalse()
    }

    @Test
    fun `should deny unknown permission`() {
        val user = User().apply { id = 1L }
        val auth = createAuthenticationForUser(1L)

        val result = userPermission.hasPermission(auth, user, "unknown")

        assertThat(result).isFalse()
    }
}
```

**Integration Test:**
```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class UserControllerSecurityTest {

    @Test
    fun `should allow user to update own profile`() {
        val token = authenticateAs(userId = 1L)

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $token")
            .body(UpdateUserRequest(...))
        .`when`()
            .put("/users/1")
        .then()
            .statusCode(200)
    }

    @Test
    fun `should deny user from updating other profile`() {
        val token = authenticateAs(userId = 1L)

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer $token")
            .body(UpdateUserRequest(...))
        .`when`()
            .put("/users/2")
        .then()
            .statusCode(403)
    }
}
```

## Best Practices

### DO:
- ✅ Create one evaluator per entity type
- ✅ Support both entity and ID-based checks
- ✅ Extract principal using SecurityUtils
- ✅ Use when/switch for permission strings
- ✅ Document available permissions
- ✅ Keep evaluator logic simple and focused
- ✅ Test both positive and negative cases
- ✅ Combine with role-based checks when appropriate

### DON'T:
- ❌ Mix authorization and business logic
- ❌ Access multiple services from evaluator
- ❌ Return null (return false instead)
- ❌ Throw exceptions from evaluators
- ❌ Use complex permission strings
- ❌ Hard-code user IDs or roles
- ❌ Skip null checks

## Related ADRs
- [ADR-001: Multi-Layered Domain-Driven Architecture](ADR-001-multi-layered-domain-driven-architecture.md) - Package structure
- [ADR-009: JWT Authentication Strategy](ADR-009-jwt-authentication-strategy.md) - Authentication foundation
- [ADR-011: Testing Strategy](ADR-011-testing-strategy.md) - Testing approach

## References
- Spring Security PermissionEvaluator Documentation
- Spring Expression Language (SpEL) Reference
- Method Security with @PreAuthorize
- Domain-Driven Design: Authorization Patterns
