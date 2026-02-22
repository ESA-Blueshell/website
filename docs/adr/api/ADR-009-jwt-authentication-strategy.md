# ADR-009: JWT Authentication Strategy

## Status
Accepted

## Context
The application needs stateless authentication for a REST API with Spring Security.

## Decision
We use **JWT (JSON Web Tokens)** with Spring Security for stateless authentication.

### Architecture

**Token Generator Interface (Domain Layer):**
```kotlin
interface TokenGenerator {
    val expirationMs: Long
    fun generateToken(username: String): String
}
```

**Implementation (Infrastructure Layer):**
```kotlin
@Component
class JwtTokenGenerator(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") override val expirationMs: Long
) : TokenGenerator {

    override fun generateToken(username: String): String {
        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact()
    }

    private fun getSigningKey(): Key {
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }
}
```

**Authentication Service (Application Layer):**
```kotlin
@Service
class AuthenticationService(
    private val authenticationManager: AuthenticationManager,
    private val tokenGenerator: TokenGenerator,
    private val users: UserService
) {
    fun authenticate(username: String, password: String): AuthenticationSession {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(username, password)
        )

        val user = users.findByUsername(username)
        val token = tokenGenerator.generateToken(user.username)
        val expirationTime = System.currentTimeMillis() + tokenGenerator.expirationMs

        return AuthenticationSession(
            token = token,
            userId = user.id!!,
            username = user.username,
            expiresAtEpochMs = expirationTime,
            roles = user.inheritedRoles,
            addressId = user.addressId
        )
    }
}
```

## Guidelines

### DO:
- ✅ Use strong secrets (min 256 bits)
- ✅ Set appropriate expiration times
- ✅ Store secrets in environment variables
- ✅ Use domain abstraction (TokenGenerator interface)
- ✅ Return domain model (AuthenticationSession)
- ✅ Include necessary claims only

### DON'T:
- ❌ Store sensitive data in JWT
- ❌ Use JWT for long-lived sessions
- ❌ Hardcode secrets
- ❌ Skip signature verification
- ❌ Return JWT details directly to presentation layer

## Consequences
- **Positive**: Stateless, scalable, no session storage
- **Negative**: Can't revoke tokens before expiry, token size

## References
- RFC 7519: JSON Web Token
- Spring Security JWT Documentation
