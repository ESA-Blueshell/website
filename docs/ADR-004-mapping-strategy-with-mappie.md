# ADR-004: Mapping Strategy with Mappie

## Status
Accepted

## Context
Spring Boot applications need to map between different object representations: Request DTOs, Commands, Entities, Response DTOs. Common approaches include:
- Manual mapping in controllers/services
- MapStruct (annotation-based code generation)
- ModelMapper (reflection-based runtime mapping)
- Custom mapper classes

Challenges:
- MapStruct requires annotation processors and can be slow
- ModelMapper is runtime-based with poor type safety
- Manual mapping is verbose and error-prone
- Inconsistent mapping approaches across codebase
- Hidden business logic in mappers

## Decision
We adopt **Mappie** for object mapping at API boundaries, with **manual mapping** for command-to-entity transformations.

### Mapping Strategy

**API Boundaries (Use Mappie):**
- Request DTO → Command
- Entity → Response DTO

**Internal Transformations (Manual):**
- Command → Entity (in handlers)
- Complex entity creation (factories)
- Aggregate updates (domain methods)

### Mappie Pattern

**Structure:**
```kotlin
// Mapper object (singleton)
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::roles fromProperty from::inheritedRoles
    }
}

// Extension function for convenience
fun User.asDetailResponse(): UserDetailResponse =
    UserToDetailResponseMapper.map(this)
```

**Usage in Controller:**
```kotlin
@GetMapping("/users/{id}")
fun getUser(@PathVariable id: Long): UserDetailResponse {
    val user = commandBus.dispatch(FindUserByIdCommand(id))
    return user.asDetailResponse()  // Clean API
}
```

### Mapping Patterns

**1. Simple Property Mapping**
```kotlin
// Automatic mapping by name and type
object UserToSummaryResponseMapper : ObjectMappie<User, UserSummaryResponse>()

fun User.asSummaryResponse(): UserSummaryResponse =
    UserToSummaryResponseMapper.map(this)
```

**2. Explicit Property Mapping**
```kotlin
// Different property names
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::roles fromProperty from::inheritedRoles
        UserDetailResponse::fullName fromValue { "${from.firstName} ${from.lastName}" }
    }
}
```

**3. Mapping with Context (Wrapper Pattern)**
```kotlin
// When additional context is needed
private data class BlogResponseSource(
    val blog: Blog,
    val frontendUrl: String
)

object BlogResponseSourceToBlogResponseMapper : ObjectMappie<BlogResponseSource, BlogResponse>() {
    override fun map(from: BlogResponseSource) = mapping {
        BlogResponse::url fromValue { "${from.frontendUrl}/blogs/${from.blog.id}" }
        BlogResponse::title fromProperty from.blog::title
    }
}

fun Blog.asResponse(frontendUrl: String): BlogResponse =
    BlogResponseSourceToBlogResponseMapper.map(BlogResponseSource(this, frontendUrl))
```

**4. Request → Command with Additional Context**
```kotlin
// Wrapper for additional parameters
internal data class CreateUserCommandRequest(
    val isBoard: Boolean,
    val request: CreateUserRequest
)

internal object CreateUserCommandRequestToCommandMapper :
    ObjectMappie<CreateUserCommandRequest, CreateUserCommand>() {
    override fun map(from: CreateUserCommandRequest) = mapping {
        CreateUserCommand::isBoard fromValue { from.isBoard }
        CreateUserCommand::username fromValue { from.request.username }
        // ... other fields
    }
}

fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserCommandRequestToCommandMapper.map(
        CreateUserCommandRequest(isBoard, this)
    )
```

### Manual Mapping for Commands → Entities

**Why Manual:**
- Entity creation involves business logic
- Association resolution requires service calls
- Password encoding, default values, etc.
- Factories provide better encapsulation

**Handler Pattern (Current):**
```kotlin
@Component
class CreateUserHandler(
    private val userService: UserService,
    private val addressService: AddressService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {

    override fun handle(command: CreateUserCommand): User {
        val user = User()
        user.username = command.username
        user.email = command.email
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber

        // Resolve associations
        command.addressId?.let {
            user.address = addressService.findById(it)
        }

        // Business logic
        user.password = passwordEncoder.encode(command.password)

        return userService.create(user)
    }
}
```

**Factory Pattern (Recommended for Complex Cases):**
```kotlin
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        val selector = randomUrlSafe(16)
        val verifier = randomUrlSafe(32)

        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = selector
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)

        repository.save(token)
        return "$selector.$verifier"
    }
}
```

## Consequences

### Positive
- **Type-safe**: Compile-time checking of mappings
- **Clear boundaries**: Mappie at API, manual internally
- **Performance**: No reflection, generated Kotlin code
- **IDE support**: Auto-completion and navigation
- **Explicit**: No hidden magic, clear what's being mapped
- **Testable**: Mappers are simple objects
- **Consistent**: Uniform pattern across codebase
- **Extension functions**: Clean, idiomatic Kotlin API

### Negative
- **More classes**: Mapper objects for each mapping
- **Verbosity**: Explicit mappings can be verbose
- **Duplication**: Similar mapping logic across mappers
- **Manual maintenance**: Changes require updating mappers
- **Learning curve**: Developers must learn Mappie API

### Trade-offs
- **Explicitness vs Brevity**: Clear mappings vs automatic magic
- **Type safety vs Flexibility**: Compile-time safety vs runtime flexibility

## Guidelines

### Mapper Object Naming
- Pattern: `{Source}To{Destination}Mapper`
- Examples: `UserToDetailResponseMapper`, `JwtRequestToCommandMapper`
- Use `internal` visibility for wrapper-based mappers

### Extension Function Naming
- Request DTOs: `.asCommand()`
- Entities: `.asResponse()`, `.asDetailResponse()`, `.asSummaryResponse()`
- Keep names short and idiomatic

### Package Organization
All mappers in `domain/{domain}/web/mapping/` package:
```
web/mapping/
├── AuthCommandMappings.kt    # Request → Command
├── AuthMappings.kt            # Entity → Response
├── UserCommandMappings.kt
└── UserMappings.kt
```

### Mapper Visibility
```kotlin
// Public mapper for reuse
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>()

// Internal wrapper for context
internal data class CreateUserCommandRequest(...)
internal object CreateUserCommandRequestToCommandMapper : ObjectMappie<...>()

// Public extension function
fun User.asDetailResponse(): UserDetailResponse = ...
```

### When to Use Manual Mapping
Use manual mapping when:
- Creating entities from commands (in handlers)
- Business logic is involved (encoding, calculations)
- Service dependencies are needed (association resolution)
- Using factory pattern for complex creation
- Aggregate methods for entity updates

### When to Use Mappie
Use Mappie when:
- Mapping at API boundaries
- Pure data transformations
- No business logic involved
- No service dependencies needed
- Consistency is important

## Best Practices

### DO:
- ✅ Use Mappie for Request → Command mappings
- ✅ Use Mappie for Entity → Response mappings
- ✅ Create extension functions for clean API
- ✅ Use object singletons for mappers (stateless)
- ✅ Use internal visibility for wrapper classes
- ✅ Use factories for complex entity creation
- ✅ Use aggregate methods for entity updates
- ✅ Keep mapping logic in `web/mapping/` package

### DON'T:
- ❌ Put business logic in Mappie mappers
- ❌ Access services from mappers
- ❌ Mix Mappie and manual mapping inconsistently
- ❌ Put mapping logic in controllers or services
- ❌ Create ad-hoc mappers inline
- ❌ Use reflection-based mapping
- ❌ Skip extension functions (call mappers directly)

## Anti-Patterns

### ❌ Business Logic in Mapper
```kotlin
// WRONG
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::status fromValue {
            if (from.enabled && from.roles.contains(Role.ADMIN)) {
                "ACTIVE_ADMIN"
            } else "INACTIVE"
        }
    }
}
```

**Solution**: Move logic to domain model or service

### ❌ Service Dependency in Mapper
```kotlin
// WRONG
object EventToResponseMapper(
    private val committeeService: CommitteeService  // ❌
) : ObjectMappie<Event, EventResponse>()
```

**Solution**: Resolve associations before mapping

### ❌ Inconsistent Patterns
```kotlin
// WRONG: Domain A uses Mappie, Domain B uses manual
// User domain
fun User.asResponse() = UserToResponseMapper.map(this)  // Mappie

// Event domain
fun Event.asResponse() = EventResponse(  // Manual
    id = this.id,
    title = this.title,
    ...
)
```

**Solution**: Use Mappie consistently at API boundaries

## Examples

### Complete Mapping Flow

**Request → Command:**
```kotlin
// DTO
data class JwtRequest(
    @field:NotBlank
    var username: String?,

    @field:NotBlank
    var password: String?
)

// Mapper
object JwtRequestToCommandMapper : ObjectMappie<JwtRequest, AuthenticateCommand>() {
    override fun map(from: JwtRequest) = mapping {
        AuthenticateCommand::username fromValue { from.username!! }
        AuthenticateCommand::password fromValue { from.password!! }
    }
}

// Extension
fun JwtRequest.asCommand(): AuthenticateCommand =
    JwtRequestToCommandMapper.map(this)

// Usage in controller
@PostMapping("/auth")
fun authenticate(@Valid @RequestBody request: JwtRequest): AuthenticationResponse {
    val session = commandBus.dispatch(request.asCommand())
    return session.asResponse()
}
```

**Entity → Response:**
```kotlin
// Entity
class User {
    var id: Long? = null
    var username: String = ""
    var email: String = ""
    val inheritedRoles: Set<Role> get() = ...
}

// Response DTO
data class UserDetailResponse(
    val id: Long,
    val username: String,
    val email: String,
    val roles: Set<Role>
)

// Mapper
object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::roles fromProperty from::inheritedRoles
    }
}

// Extension
fun User.asDetailResponse(): UserDetailResponse =
    UserToDetailResponseMapper.map(this)

// Usage
@GetMapping("/users/{id}")
fun getUser(@PathVariable id: Long): UserDetailResponse {
    val user = userService.findById(id)
    return user.asDetailResponse()
}
```

## References
- Mappie Documentation: https://mappie.reckon.dev/
- Kotlin Coding Conventions
- Clean Architecture - Adapter Pattern
- Hexagonal Architecture - Ports and Adapters
