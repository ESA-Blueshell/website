# ADR-008: Exception Handling Strategy

## Status
Accepted

## Context
Applications need consistent error handling across all endpoints for client reliability and debugging.

## Decision
We use **Spring's Problem Details (RFC 7807)** with **domain-specific exception hierarchies** and **@RestControllerAdvice** for global handling.

### Exception Hierarchy

**Domain Exceptions:**
```kotlin
// Base exception
sealed class RecoveryTokenException(message: String) : RuntimeException(message)

// Specific exceptions
class ExpiredRecoveryTokenException(message: String) : RecoveryTokenException(message)
class ConsumedRecoveryTokenException(message: String) : RecoveryTokenException(message)
class MalformedRecoveryTokenException(message: String) : RecoveryTokenException(message)
```

**Business Exceptions:**
```kotlin
class UserNotFoundException(message: String) : RuntimeException(message)
class InvalidCredentialsException(message: String) : RuntimeException(message)
```

### Global Exception Handler

```kotlin
@RestControllerAdvice
class ValidationProblemDetailsAdvice {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed")

        val errors = ex.bindingResult.fieldErrors.map { fe ->
            mapOf(
                "field" to fe.field,
                "message" to fe.defaultMessage,
                "rejectedValue" to fe.rejectedValue
            )
        }

        pd.setProperty("errors", errors)
        return pd
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleNotFound(ex: UserNotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
    }
}
```

## Guidelines

### DO:
- ✅ Use sealed classes for related exceptions
- ✅ Include specific error messages
- ✅ Use appropriate HTTP status codes
- ✅ Return Problem Details format
- ✅ Log exceptions at appropriate levels
- ✅ Create domain-specific exception hierarchies

### DON'T:
- ❌ Catch exceptions in controllers
- ❌ Return generic error messages
- ❌ Expose stack traces to clients (in production)
- ❌ Use exceptions for flow control
- ❌ Create overly generic exceptions

## Consequences
- **Positive**: Consistent errors, better debugging, client-friendly
- **Negative**: More exception classes, need to maintain hierarchy

## References
- RFC 7807: Problem Details for HTTP APIs
- Spring Boot Error Handling
