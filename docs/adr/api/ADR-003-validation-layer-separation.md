# ADR-003: Validation Layer Separation

## Status
Accepted


> **Amended** by [architecture ADR-005](../architecture/ADR-005-validation-placement.md):
> rules requiring a database query move out of bean validation into the use case,
> guarded by a unique index. Field-level constraints are unaffected.

## Context
In Spring Boot applications, validation can occur at multiple levels: web layer (DTOs), domain layer (entities), service layer, or database constraints. Without clear guidelines, validation becomes scattered, duplicated, and inconsistent.

Common problems:
- Business logic validation in DTOs (web layer knowing about database)
- Database queries in validators attached to web DTOs
- Duplicate validation across layers
- Unclear which layer validates what
- Difficult to test validation logic independently

## Decision
We adopt **layered validation** with clear responsibilities at each layer, following the principle: **validate at the appropriate level based on dependencies**.

### Validation Layers

**Web Layer** (`web/validation/`)
- **What**: Structural validation only
- **Dependencies**: None (no database, no services)
- **Examples**: Format, presence, size, type checking
- **Annotations**: `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Email`

**Command Layer** (`command/`)
- **What**: Field-level validation
- **Dependencies**: None for field validation
- **Purpose**: Defensive programming, clear contracts
- **Annotations**: Same as web layer

**Application Layer** (`application/validation/`)
- **What**: Business rules requiring external dependencies
- **Dependencies**: Services, repositories (database access allowed)
- **Examples**: Uniqueness checks, cross-aggregate rules
- **Annotations**: Custom validators (e.g., `@UniqueUsername`)

**Domain Layer** (`domain/service/`)
- **What**: Complex business logic and invariants
- **Dependencies**: Domain models, repositories
- **Examples**: Token validation, state transitions
- **Implementation**: Domain services with explicit validation methods

### Validation Flow

```
HTTP Request
    ↓
Controller validates Request DTO (@Valid)
    ↓
Web Layer Validators (structural only)
    ↓
Mapper: Request → Command
    ↓
CommandBus.dispatch()
    ↓
CommandBus validates Command (Jakarta Bean Validation)
    ↓
Application Layer Validators (business rules)
    ↓
Command Handler (no validation, only orchestration)
    ↓
Domain Service (optional defensive validation)
    ↓
Repository/Database
```

## Layer-Specific Guidelines

### Web Layer Validation

**Purpose**: Validate structure before entering application logic

**Valid Use Cases:**
```kotlin
// ✅ Format validation
@field:Pattern(regexp = "^[A-Z]{2}$")
var countryCode: String?

// ✅ Length constraints
@field:Size(min = 8, max = 100)
var password: String?

// ✅ Presence checks
@field:NotBlank
var username: String?

// ✅ Type validation
@field:Email
var email: String?

// ✅ Simple cross-field rules (no external dependencies)
@ValidQuestion  // Checks if question type matches available options
var question: QuestionDTO?
```

**Invalid Use Cases:**
```kotlin
// ❌ WRONG: Database access in web validator
class UniqueUsernameValidator : ConstraintValidator<UniqueUsername, String> {
    @Autowired
    lateinit var userService: UserService  // ❌ Service dependency in web layer

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        return !userService.existsByUsername(value)  // ❌ Database query
    }
}
```

### Command Layer Validation

**Purpose**: Defensive validation, clear contracts

**Valid Use Cases:**
```kotlin
data class CreateUserCommand(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank
    @field:Size(min = 8, max = 100)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain uppercase, lowercase, digit, and special character"
    )
    val password: String,

    @field:Email
    val email: String
) : Command<User>
```

**Benefits:**
- Commands self-document requirements
- Fail fast if DTO validation missed something
- Clear contracts for command handlers
- Independent of web layer

### Application Layer Validation

**Purpose**: Business rules requiring database or service access

**Valid Use Cases:**
```kotlin
// Uniqueness validation
@Component
class UniqueUsernameValidator @Autowired constructor(
    private val userService: UserService
) : ConstraintValidator<UniqueUsername, String?> {

    override fun isValid(username: String?, context: ConstraintValidatorContext): Boolean {
        if (username.isNullOrBlank()) return true
        return !userService.existsByUsername(username)
    }
}

// Multi-field business validation
@Component
class UniqueUserCommandValidator @Autowired constructor(
    private val users: UserService
) : ConstraintValidator<UniqueUserCommand, UserUniquenessCandidate> {

    override fun isValid(candidate: UserUniquenessCandidate?, context: ConstraintValidatorContext): Boolean {
        if (candidate == null) return true

        var valid = true
        val currentUserId = candidate.subjectId

        // Check username uniqueness
        if (!candidate.username.isNullOrBlank()) {
            val taken = if (currentUserId == null)
                users.existsByUsername(candidate.username)
            else
                users.existsByUsernameAndIdNot(candidate.username, currentUserId)

            if (taken) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate("Username is taken")
                    .addPropertyNode("username")
                    .addConstraintViolation()
                valid = false
            }
        }

        return valid
    }
}

// Cross-aggregate validation
@Component
class ValidEventSignUpCommandValidator @Autowired constructor(
    private val events: EventService
) : ConstraintValidator<ValidEventSignUpCommand, EventSignUpCandidate> {

    override fun isValid(candidate: EventSignUpCandidate?, ctx: ConstraintValidatorContext): Boolean {
        if (candidate == null) return true

        val event = events.findById(candidate.eventId)
        val form = event.signUpForm ?: return violation(ctx, "Event has no sign-up form")

        // Validate answers match form questions
        val requiredQuestionIds = form.questions
            .filter { it.type != QuestionType.DESCRIPTION }
            .mapNotNull { it.id }
            .toSet()

        val providedQuestionIds = candidate.answers
            .mapNotNull { it.questionId }
            .toSet()

        if (requiredQuestionIds != providedQuestionIds) {
            return violation(ctx, "Answers don't match required questions")
        }

        return true
    }
}
```

### Domain Layer Validation

**Purpose**: Complex business logic, invariant enforcement

**Valid Use Cases:**
```kotlin
@Component
class RecoveryTokenValidator(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {

    fun verify(rawToken: String, expectedType: ResetType): RecoveryToken {
        // Parse and validate format
        val validation = try {
            RecoveryTokenValidation.fromRawToken(rawToken, expectedType)
        } catch (e: IllegalArgumentException) {
            throw MalformedRecoveryTokenException(e.message ?: "Invalid token format")
        }

        // Find token
        val token = repository.findBySelector(validation.selector)
            .orElseThrow { InvalidRecoveryTokenException("Token not found") }

        // Validate type
        if (token.type != expectedType) {
            throw InvalidTokenTypeException("Token type mismatch")
        }

        // Check expiry
        if (token.isExpired) {
            throw ExpiredRecoveryTokenException("Token expired")
        }

        // Check consumption
        if (token.isConsumed) {
            throw ConsumedRecoveryTokenException("Token already used")
        }

        // Verify verifier
        if (!encoder.matches(validation.verifier, token.verifierHash)) {
            throw TokenVerificationFailedException("Token verification failed")
        }

        return token
    }
}
```

## Consequences

### Positive
- **Clear responsibilities**: Each layer validates what it should
- **No coupling**: Web layer doesn't know about database
- **Testable**: Each validation layer testable independently
- **Fail fast**: Multiple validation checkpoints
- **Explicit business rules**: Application validators document rules
- **Reusable**: Validators can be shared across commands
- **Type-safe**: Validator interfaces enforce contracts

### Negative
- **Multiple validation points**: Can feel redundant
- **Learning curve**: Developers must know which layer to validate in
- **Verbose**: More validator classes
- **Coordination**: Must ensure consistency across layers

### Trade-offs
- **Safety vs Simplicity**: Multiple layers for robustness
- **Coupling vs Convenience**: Strict separation vs easy database access

## Best Practices

### DO:
- ✅ Validate structure in web DTOs
- ✅ Add defensive validation to commands
- ✅ Put business rules in application validators
- ✅ Use domain services for complex validation
- ✅ Return specific exceptions from domain validators
- ✅ Use validator interfaces (e.g., `UserUniquenessCandidate`)

### DON'T:
- ❌ Access database from web validators
- ❌ Put business logic in command handlers
- ❌ Skip command validation (assuming DTO is valid)
- ❌ Perform validation in constructors
- ❌ Make application validators depend on DTO types
- ❌ Use generic exceptions (be specific)

## Validation Type Reference

| Validation Type | Layer | Dependency | Example |
|----------------|-------|------------|---------|
| Format (regex, length) | Web/Command | None | `@Pattern`, `@Size`, `@Email` |
| Presence/nullability | Web/Command | None | `@NotNull`, `@NotBlank` |
| Type safety | Web | None | `@CountryCode`, `@ValidMobilePhoneNumber` |
| File constraints | Web | None | `@FileSize`, `@AllowedContentTypes` |
| Simple business logic | Web | None | `@GuestOrUserRequired` (either/or) |
| Uniqueness checks | Application | UserService | `@UniqueUsername` |
| Multi-field uniqueness | Application | Services | `@UniqueUserCommand` |
| Cross-aggregate rules | Application | Multiple Services | `@ValidEventSignUpCommand` |
| Domain invariants | Domain Service | Repositories | Token expiry, state transitions |

## Migration Strategy

For existing code:
1. Identify validators with service dependencies in web layer
2. Move to application layer validation
3. Create command-level validators
4. Extract complex validation to domain services
5. Add ArchUnit tests to prevent regression

## References
- Jakarta Bean Validation Specification
- Spring Validation Documentation
- Domain-Driven Design - Invariants
- Hexagonal Architecture - Validation at boundaries
