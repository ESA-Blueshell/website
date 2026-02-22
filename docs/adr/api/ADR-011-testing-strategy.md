# ADR-011: Testing Strategy

## Status
Accepted

## Context
Comprehensive testing strategy needed to ensure code quality, architectural compliance, and functionality.

## Decision
We use a **multi-layered testing approach** with specialized tools for each concern.

### Test Types

**1. Architecture Tests (ArchUnit)**
```kotlin
@AnalyzeClasses(packages = ["net.blueshell.api"])
class LayeredArchitectureTest {
    @ArchTest
    fun `enforce layering`(importedClasses: JavaClasses) {
        layeredArchitecture()
            .layer("Controllers").definedBy("..web..")
            .layer("Services").definedBy("..application..")
            .layer("Repositories").definedBy("..persistence.repository..")

            .whereLayer("Controllers")
                .mayOnlyAccessLayers("Services", "DTO", "Model", "Common")
            .whereLayer("Services")
                .mayOnlyAccessLayers("Repositories", "Model", "Common")
            .check(importedClasses)
    }
}
```

**2. Unit Tests**
```kotlin
class RecoveryTokenFactoryTest {
    @Test
    fun `should generate valid token`() {
        val factory = RecoveryTokenFactory(mockRepository, mockEncoder)
        val token = factory.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))

        assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
    }
}
```

**3. Integration Tests (Testcontainers)**
```kotlin
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {
    @Container
    val mariaDB = MariaDBContainer<Nothing>("mariadb:10.11.10")

    @Test
    fun `should create user and publish event`() {
        val user = userService.create(User().apply {
            username = "test"
            email = "test@example.com"
        })

        assertThat(user.id).isNotNull()
        verify(eventPublisher).publish(any<UserCreated>())
    }
}
```

**4. API Tests (REST Assured)**
```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class AuthenticationControllerTest {
    @Test
    fun `should authenticate valid user`() {
        given()
            .contentType(ContentType.JSON)
            .body(JwtRequest("user", "password"))
        .`when`()
            .post("/auth")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
    }
}
```

## Test Organization
```
src/test/kotlin/
├── architecture/           # ArchUnit tests
├── domain/
│   └── user/
│       ├── application/   # Unit tests
│       ├── web/           # API tests
│       └── integration/   # Integration tests
└── testsupport/           # Test utilities
```

## Guidelines

### DO:
- ✅ Write ArchUnit tests for architecture rules
- ✅ Use Testcontainers for integration tests
- ✅ Mock external dependencies in unit tests
- ✅ Test command handlers independently
- ✅ Test validators with edge cases
- ✅ Use meaningful test names
- ✅ Keep tests fast and focused

### DON'T:
- ❌ Skip architecture tests
- ❌ Test implementation details
- ❌ Create interdependent tests
- ❌ Use production database for tests
- ❌ Ignore flaky tests
- ❌ Test framework code

## Consequences
- **Positive**: High confidence, enforced architecture, fast feedback
- **Negative**: More test code, requires discipline

## References
- ArchUnit Documentation
- Testcontainers Documentation
- Spring Boot Testing Guide
