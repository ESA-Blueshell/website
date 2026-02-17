# ADR-005: Factory Pattern for Entity Creation

## Status
Accepted

## Context
Entity creation in Spring Boot applications can involve:
- Complex initialization logic
- Multiple dependencies (password encoders, token generators, etc.)
- Business rule enforcement
- Association setup
- Default value calculation

Without a pattern, entity creation logic ends up scattered across:
- Command handlers (most common)
- Services
- Helper methods
- Static factory methods on entities

This leads to:
- Duplicated initialization logic
- Inconsistent entity state
- Difficult to test creation logic
- Command handlers becoming bloated
- Business logic mixed with orchestration

## Decision
We adopt the **Factory Pattern** for complex entity creation, while allowing **direct construction** for simple entities.

### When to Use Factories

Use factories when entity creation involves:
- **Complex initialization**: Multiple steps, calculations
- **Dependencies**: Password encoding, token generation, etc.
- **Business rules**: Default values based on logic
- **Security**: Token generation, hashing
- **Consistency**: Same creation logic used in multiple places

### When to Use Direct Construction

Use direct construction when:
- Entity is simple data container
- No complex initialization needed
- No dependencies required
- One-time creation logic
- Handler can manage creation inline

### Factory Structure

```kotlin
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    private val random = SecureRandom()

    /**
     * Issue a new recovery token for a user.
     * Deletes any existing unconsumed tokens of the same type for the user.
     */
    @Transactional
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        // Delete existing tokens
        repository.findAllUnconsumedByTypeAndUserId(user.id!!, type)
            .forEach { repository.delete(it) }

        // Generate secure random values
        val selector = randomUrlSafe(16)
        val verifier = randomUrlSafe(32)

        // Create token
        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = selector
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)

        // Persist
        repository.save(token)

        // Return raw token (selector.verifier)
        return "$selector.$verifier"
    }

    fun consume(token: RecoveryToken) {
        token.consumedAt = Instant.now()
        repository.save(token)
    }

    private fun randomUrlSafe(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
```

**Key Characteristics:**
- Spring `@Component` for dependency injection
- Named `{Entity}Factory`
- Located in `application/factory/` package
- Encapsulates all creation logic
- Returns fully initialized entities
- May persist entities directly (if needed)
- `@Transactional` when persistence is involved

## Factory Patterns

### 1. Simple Factory (Create and Return)

```kotlin
@Component
class UserFactory(
    private val passwordEncoder: PasswordEncoder
) {
    fun createFromCommand(command: CreateUserCommand): User {
        return User().apply {
            username = command.username
            email = command.email
            discord = command.discord
            phoneNumber = command.phoneNumber
            password = passwordEncoder.encode(command.password)
            enabled = false
            roles = mutableSetOf(Role.USER)
        }
    }
}

// Usage in handler
@Component
class CreateUserHandler(
    private val factory: UserFactory,
    private val userService: UserService
) : CommandHandler<CreateUserCommand, User> {

    override fun handle(command: CreateUserCommand): User {
        val user = factory.createFromCommand(command)
        return userService.create(user)
    }
}
```

### 2. Factory with Persistence

```kotlin
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    @Transactional
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        // Cleanup
        repository.findAllUnconsumedByTypeAndUserId(user.id!!, type)
            .forEach { repository.delete(it) }

        // Create
        val token = createToken(user, type, ttl)

        // Persist
        repository.save(token)

        return buildRawToken(token.selector, token.verifier)
    }

    private fun createToken(user: User, type: ResetType, ttl: Duration): TokenData {
        val selector = randomUrlSafe(16)
        val verifier = randomUrlSafe(32)

        return TokenData(
            entity = RecoveryToken().apply {
                this.user = user
                this.type = type
                this.selector = selector
                this.verifierHash = encoder.encode(verifier)
                this.expiresAt = Instant.now().plus(ttl)
            },
            selector = selector,
            verifier = verifier
        )
    }

    private data class TokenData(
        val entity: RecoveryToken,
        val selector: String,
        val verifier: String
    )
}
```

### 3. Factory with Multiple Creation Methods

```kotlin
@Component
class EventFactory(
    private val committeeService: CommitteeService
) {
    fun createFromCommand(command: CreateEventCommand): Event {
        return Event().apply {
            title = command.title
            description = command.description
            startTime = command.startTime
            endTime = command.endTime
            committee = committeeService.getReferenceById(command.committeeId)
        }
    }

    fun createDraft(committeeId: Long): Event {
        return Event().apply {
            title = "Draft Event"
            description = ""
            committee = committeeService.getReferenceById(committeeId)
            published = false
        }
    }

    fun createRecurring(template: Event, date: LocalDate): Event {
        return Event().apply {
            title = template.title
            description = template.description
            committee = template.committee
            startTime = date.atTime(template.startTime.toLocalTime())
            endTime = date.atTime(template.endTime.toLocalTime())
        }
    }
}
```

### 4. Factory with Builder Pattern

```kotlin
@Component
class SurveyFactory {
    fun builder(): SurveyBuilder = SurveyBuilder()

    class SurveyBuilder {
        private var title: String = ""
        private var description: String = ""
        private val questions: MutableList<Question> = mutableListOf()

        fun title(title: String) = apply { this.title = title }

        fun description(description: String) = apply { this.description = description }

        fun addQuestion(question: Question) = apply {
            questions.add(question)
        }

        fun build(): Survey {
            require(title.isNotBlank()) { "Survey title is required" }

            return Survey().apply {
                this.title = this@SurveyBuilder.title
                this.description = this@SurveyBuilder.description
                this@SurveyBuilder.questions.forEach { this.addQuestion(it) }
            }
        }
    }
}

// Usage
val survey = surveyFactory.builder()
    .title("Event Feedback")
    .description("Please rate your experience")
    .addQuestion(ratingQuestion)
    .addQuestion(commentQuestion)
    .build()
```

## Consequences

### Positive
- **Encapsulation**: Creation logic in one place
- **Reusability**: Same logic for all creation scenarios
- **Testability**: Factory testable in isolation
- **Consistency**: Entities always created correctly
- **Dependencies**: Clear what's needed for creation
- **Documentation**: Factory methods document how to create entities
- **Type safety**: Compile-time validation of creation logic
- **Clean handlers**: Handlers delegate creation, focus on orchestration

### Negative
- **More classes**: Factory class per complex entity
- **Indirection**: Extra layer between handler and entity
- **Overhead**: Overkill for simple entities
- **Learning curve**: Developers must know when to use factories

### Trade-offs
- **Simplicity vs Consistency**: Direct construction vs factory
- **Flexibility vs Convention**: Inline logic vs factory method

## Guidelines

### Naming Conventions
- Class: `{Entity}Factory`
- Location: `application/factory/` package
- Methods: `create*`, `issue*`, `build*`, `make*`

### Factory Method Naming
- `create*`: General creation (createFromCommand, createDraft)
- `issue`: When something is issued (issueToken, issueTicket)
- `build`: When builder pattern is used
- `make`: Alternative to create
- Be specific: `createDraft`, `createRecurring`, not just `create`

### Factory Dependencies
Factories can depend on:
- ✅ Services (for association resolution)
- ✅ Repositories (for persistence or lookups)
- ✅ Encoders/generators (PasswordEncoder, TokenGenerator)
- ✅ Other factories (for composite creation)
- ❌ Controllers (violation of layers)
- ❌ Command handlers (wrong direction)

### Testing Factories
```kotlin
@SpringBootTest
class RecoveryTokenFactoryTest {

    @Autowired
    lateinit var factory: RecoveryTokenFactory

    @Autowired
    lateinit var repository: RecoveryTokenRepository

    @Test
    fun `should create valid recovery token`() {
        val user = createTestUser()
        val rawToken = factory.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))

        assertThat(rawToken).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")

        val tokens = repository.findAllUnconsumedByTypeAndUserId(user.id!!, ResetType.PASSWORD_RESET)
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0].type).isEqualTo(ResetType.PASSWORD_RESET)
    }

    @Test
    fun `should delete old tokens when issuing new one`() {
        val user = createTestUser()

        factory.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))
        factory.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))

        val tokens = repository.findAllUnconsumedByTypeAndUserId(user.id!!, ResetType.PASSWORD_RESET)
        assertThat(tokens).hasSize(1)  // Only latest token remains
    }
}
```

## Best Practices

### DO:
- ✅ Use factories for complex entity creation
- ✅ Encapsulate all creation logic in factory
- ✅ Include documentation on factory methods
- ✅ Use dependency injection for factory dependencies
- ✅ Make factory methods specific (not generic "create")
- ✅ Test factory methods thoroughly
- ✅ Use `@Transactional` when factory persists entities

### DON'T:
- ❌ Use factories for simple entities (overkill)
- ❌ Put business logic in factories (use domain services)
- ❌ Make factories stateful (should be singletons)
- ❌ Bypass factories (always use them if they exist)
- ❌ Create entities differently in different places
- ❌ Mix factory and direct construction for same entity

## Anti-Patterns

### ❌ Bloated Command Handler
```kotlin
// WRONG: All creation logic in handler
@Component
class CreateUserHandler(...) : CommandHandler<CreateUserCommand, User> {
    override fun handle(command: CreateUserCommand): User {
        val user = User()
        user.username = command.username
        user.email = command.email
        user.password = passwordEncoder.encode(command.password)
        user.enabled = false
        user.roles = mutableSetOf(Role.USER)
        user.createdAt = Instant.now()
        // ... 20 more lines of initialization
        return userService.create(user)
    }
}
```

**Solution**: Extract to UserFactory

### ❌ Stateful Factory
```kotlin
// WRONG: Factory with state
@Component
class UserFactory {
    private var lastCreatedUser: User? = null  // ❌ State

    fun create(command: CreateUserCommand): User {
        val user = ...
        lastCreatedUser = user  // ❌
        return user
    }
}
```

**Solution**: Make factories stateless

### ❌ Factory with Business Logic
```kotlin
// WRONG: Business logic in factory
@Component
class UserFactory {
    fun create(command: CreateUserCommand): User {
        if (command.age < 18) {  // ❌ Business rule
            throw BusinessException("Must be 18+")
        }
        // ...
    }
}
```

**Solution**: Put business rules in validators or domain services

## References
- Gang of Four: Factory Method Pattern
- Domain-Driven Design: Factories
- Spring Framework: Component Model
- Effective Java: Static Factory Methods
