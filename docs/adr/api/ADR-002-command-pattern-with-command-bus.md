# ADR-002: Command Pattern with CommandBus

## Status
Superseded by
[architecture ADR-002](../architecture/ADR-002-use-case-services-replace-the-command-bus.md).

The `CommandBus`, the `CommandHandler` interface and the one-handler-per-command
rule are withdrawn. Measured against the codebase this pattern produced 110
`handle()` methods of which 53 were a single line, and the async half
(`AsyncCommandDispatcher`, `AbstractCommandJobHandler`) never acquired a
production caller. The query/command distinction this ADR drew remains correct
and is carried forward. Retained for history.


## Context
Spring Boot controllers typically call service methods directly, leading to:
- Controllers tightly coupled to specific service implementations
- Difficult to add cross-cutting concerns (logging, authorization, validation)
- No unified way to handle commands across the application
- Business operations not discoverable from code structure
- Validation scattered across controllers and services
- Difficult to test business logic without Spring context

## Decision
We adopt the **Command Pattern** with a **CommandBus** to handle all write operations.

### Command Structure

Commands are immutable data classes representing business operations:

```kotlin
data class CreateUserCommand(
    @field:NotBlank
    val username: String,

    @field:Email
    val email: String,

    val roles: Set<Role>
) : Command<User>
```

**Key Characteristics:**
- Immutable (`data class` with `val` properties)
- Include Jakarta Bean Validation annotations
- Implement `Command<R>` interface where `R` is the return type
- Named after business operations (CreateUser, not SaveUser)
- Located in `domain/{domain}/command/` package

### Command Handler Structure

Each command has one handler:

```kotlin
@Component
class CreateUserHandler(
    private val userService: UserService,
    private val addressService: AddressService,
    private val passwordEncoder: PasswordEncoder
) : CommandHandler<CreateUserCommand, User> {

    override val commandType = CreateUserCommand::class

    override fun handle(command: CreateUserCommand): User {
        val user = User()
        user.username = command.username
        user.email = command.email
        user.password = passwordEncoder.encode(command.password)
        return userService.create(user)
    }
}
```

**Key Characteristics:**
- One handler per command (Single Responsibility)
- Stateless Spring components
- Orchestrate services, don't contain business logic
- Named {Operation}Handler (e.g., CreateUserHandler)
- Located in `domain/{domain}/application/command/` package

### CommandBus

The CommandBus validates and dispatches commands:

```kotlin
@Component
class CommandBus(
    handlers: List<CommandHandler<*, *>>,
    private val validator: Validator
) {
    private val handlersByType = handlers.associateBy { it.commandType }

    fun <R, C : Command<R>> dispatch(command: C): R {
        // 1. Validate command
        val violations = validator.validate(command)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }

        // 2. Find handler
        val handler = handlersByType[command::class]
            ?: throw IllegalArgumentException("No handler registered")

        // 3. Execute
        return (handler as CommandHandler<C, R>).handle(command)
    }
}
```

**Responsibilities:**
- Command validation (Jakarta Bean Validation)
- Handler lookup and dispatch
- Single point for cross-cutting concerns
- Type-safe command execution

### Controller Integration

Controllers map requests to commands:

```kotlin
@RestController
class UserController(
    private val commandBus: CommandBus
) {

    @PostMapping("/users")
    fun createUser(@Valid @RequestBody request: CreateUserRequest): UserResponse {
        val command = request.asCommand()
        val user = commandBus.dispatch(command)
        return user.asResponse()
    }
}
```

**Flow:**
1. Request DTO validated by Spring (`@Valid`)
2. Request mapped to Command
3. CommandBus validates Command
4. CommandBus dispatches to Handler
5. Handler orchestrates services
6. Result mapped to Response DTO

## Consequences

### Positive
- **Decoupling**: Controllers don't depend on specific services
- **Unified validation**: All commands validated in CommandBus
- **Discoverability**: Commands make operations explicit
- **Testability**: Commands and handlers testable without Spring
- **Cross-cutting concerns**: Easy to add logging, metrics, authorization
- **Type safety**: Compile-time command/handler matching
- **Single Responsibility**: Each handler does one thing
- **Explicit use cases**: Commands document what the system can do
- **Audit trail**: Easy to log all commands for auditing
- **Command replay**: Commands can be stored and replayed

### Negative
- **More classes**: Each operation needs Command + Handler
- **Indirection**: Extra layer between controller and service
- **Learning curve**: Developers must understand pattern
- **Boilerplate**: Similar structure across handlers
- **No direct return types**: Controllers dispatch, don't call methods directly

### Trade-offs
- **Simplicity vs Flexibility**: More classes for better architecture
- **Verbosity vs Clarity**: More explicit but more code

## Implementation Guidelines

### Command Naming
- Use imperative verbs: `CreateUser`, `UpdateEvent`, `DeleteCommittee`
- Include entity name: `CreateUser` not just `Create`
- Specific operations: `ActivateUser`, `DeactivateUser` (not `UpdateUserStatus`)

### Handler Responsibilities
Handlers should:
- ✅ Orchestrate multiple services
- ✅ Map commands to entities
- ✅ Resolve entity associations
- ✅ Call services for persistence
- ✅ Publish domain events

Handlers should NOT:
- ❌ Contain business logic (delegate to services/domain)
- ❌ Perform validation (done by CommandBus)
- ❌ Direct database access (use repositories via services)
- ❌ Handle HTTP concerns (that's controller responsibility)

### Command Validation

**Structural Validation (on Command):**
```kotlin
data class CreateUserCommand(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:Email
    val email: String
) : Command<User>
```

**Business Validation (custom validators):**
```kotlin
@UniqueUserCommand
data class CreateUserCommand(
    @field:NotBlank
    val username: String,

    @field:Email
    val email: String
) : Command<User>, UserUniquenessCandidate
```

### Query vs Command
- **Commands**: Modify state (POST, PUT, DELETE)
  - Use CommandBus
  - Have handlers
  - Return entities or Unit

- **Queries**: Read-only (GET)
  - Call services directly from controllers
  - No CommandBus
  - Return read models/DTOs

### Transactional Boundaries
Handlers define transaction boundaries:
```kotlin
@Component
class CreateUserHandler(...) : CommandHandler<CreateUserCommand, User> {

    @Transactional
    override fun handle(command: CreateUserCommand): User {
        // All or nothing
    }
}
```

## Examples

### Simple Command (No Business Validation)
```kotlin
// Command
data class DeleteUserCommand(
    val userId: Long
) : Command<Unit>

// Handler
@Component
class DeleteUserHandler(
    private val userService: UserService
) : CommandHandler<DeleteUserCommand, Unit> {
    override val commandType = DeleteUserCommand::class

    @Transactional
    override fun handle(command: DeleteUserCommand) {
        userService.deleteById(command.userId)
    }
}
```

### Complex Command (With Business Validation)
```kotlin
// Command with validation
@UniqueUserCommand
data class CreateUserCommand(
    @field:NotBlank
    override val username: String,

    @field:NotBlank
    @field:Email
    override val email: String,

    override val discord: String?,
    override val phoneNumber: String?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
}

// Handler orchestrating multiple services
@Component
class CreateUserHandler(
    private val userService: UserService,
    private val addressService: AddressService,
    private val passwordEncoder: PasswordEncoder,
    private val events: EventPublisher
) : CommandHandler<CreateUserCommand, User> {
    override val commandType = CreateUserCommand::class

    @Transactional
    override fun handle(command: CreateUserCommand): User {
        val user = User()
        user.username = command.username
        user.email = command.email
        user.discord = command.discord
        user.phoneNumber = command.phoneNumber

        command.addressId?.let {
            user.address = addressService.findById(it)
        }

        user.password = passwordEncoder.encode(command.password)

        val saved = userService.create(user)
        events.publish(UserCreated(saved.id!!))

        return saved
    }
}
```

## Anti-Patterns to Avoid

### ❌ Business Logic in Handler
```kotlin
// WRONG: Handler contains business logic
@Component
class CreateUserHandler(...) : CommandHandler<CreateUserCommand, User> {
    override fun handle(command: CreateUserCommand): User {
        if (command.age < 18) {
            throw BusinessException("User must be 18+")
        }
        // ...
    }
}
```

**Solution**: Move to domain service or validator

### ❌ Handler Calling Another Handler
```kotlin
// WRONG: Handler dispatching commands
@Component
class CreateEventHandler(
    private val commandBus: CommandBus
) : CommandHandler<CreateEventCommand, Event> {
    override fun handle(command: CreateEventCommand): Event {
        commandBus.dispatch(CreateCommitteeCommand(...))
        // ...
    }
}
```

**Solution**: Extract to service or use events

### ❌ Command with Behavior
```kotlin
// WRONG: Command with methods
data class CreateUserCommand(...) : Command<User> {
    fun validate() { ... }  // ❌
    fun toEntity(): User { ... }  // ❌
}
```

**Solution**: Keep commands as pure data

## References
- Command Pattern (Gang of Four)
- CQRS (Command Query Responsibility Segregation)
- Hexagonal Architecture
- Spring Framework Validation
- Jakarta Bean Validation
