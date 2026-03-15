# ADR-012: API Documentation with OpenAPI

## Status
Accepted

## Context
REST APIs need comprehensive documentation for frontend developers, API consumers, and automated client generation.

## Decision
We use **SpringDoc OpenAPI 3** (Swagger UI) for API documentation with code-first approach.

### Configuration

**Controller Documentation:**
```kotlin
@RestController
@Tag(name = "Authentication", description = "Authentication operations")
class AuthenticationController(
    private val commandBus: CommandBus
) {

    @Operation(
        summary = "Authenticate user",
        description = "Authenticate with username and password to receive JWT token"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        ApiResponse(responseCode = "401", description = "Invalid credentials"),
        ApiResponse(responseCode = "400", description = "Validation error")
    )
    @PostMapping("/auth")
    @PermitAll
    fun authenticate(
        @Valid @RequestBody request: JwtRequest
    ): AuthenticationResponse {
        val session = commandBus.dispatch(request.asCommand())
        return session.asResponse()
    }
}
```

**DTO Documentation:**
```kotlin
@Schema(name = "JwtRequest", description = "Authentication request")
data class JwtRequest(
    @field:NotBlank(message = "Username required")
    @Schema(description = "Username", example = "john.doe", required = true)
    var username: String?,

    @field:NotBlank(message = "Password required")
    @Schema(description = "Password", example = "SecureP@ss123", required = true)
    var password: String?
)
```

### Client Generation

**Frontend TypeScript Client:**
```bash
./scripts/generate_openapi.sh
```

This generates:
- `openapi/blueshell.json` - OpenAPI specification
- `frontend/src/lib/` - TypeScript client

## Guidelines

### DO:
- ✅ Document all public endpoints
- ✅ Include examples in schemas
- ✅ Document error responses
- ✅ Use meaningful operation summaries
- ✅ Group endpoints with @Tag
- ✅ Generate clients after API changes

### DON'T:
- ❌ Skip documentation on public APIs
- ❌ Use generic descriptions
- ❌ Forget to update after changes
- ❌ Document internal endpoints
- ❌ Include sensitive data in examples

## Consequences
- **Positive**: Always up-to-date, client generation, interactive testing
- **Negative**: Annotation overhead, need to maintain

## References
- OpenAPI Specification 3.0
- SpringDoc Documentation
- Swagger UI Guide
