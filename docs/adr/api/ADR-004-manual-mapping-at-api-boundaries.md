# ADR-004: Manual Mapping at API Boundaries

## Status
Accepted

## Context
The API maps between request models, commands, domain entities, and response models. The project previously used a mapping library for part of this flow, but this added abstraction and tooling overhead that no longer matches current architecture goals.

The codebase has moved toward:
- explicit command contracts and stricter nullability
- constructor-based entity invariants
- domain-owned mapping files in `web/mapping`
- direct, readable transformations without framework-specific mapping DSLs

## Decision
Use **manual mapping only** in the API module.

### Scope
Manual mapping is the standard for:
- Request DTO -> Command
- Entity / projection -> Response DTO
- Transport DTO -> domain-specific API model

Command -> entity mapping remains manual in handlers/factories, where business rules and association resolution belong.

### Mapping Rules
- Mapping code lives in domain `web/mapping` packages.
- Request and response mappings are separated by file purpose (for example `*CommandMappings.kt` and `*Mappings.kt`).
- Mapping functions are pure and side-effect free.
- Mappers must not call repositories/services.
- Business logic stays in handlers, services, factories, or domain methods.

### Library Policy
The API does not use mapping libraries for application mapping concerns.
- Do not introduce Mappie, MapStruct, ModelMapper, or similar mapping frameworks for domain/web mappings.

## Consequences

### Positive
- Fully explicit mappings with straightforward Kotlin code
- Easier nullability and required-field reasoning per endpoint
- No mapping code generation/runtime reflection dependency
- Faster onboarding: mapping behavior is visible in plain functions
- Simpler debugging and code review at API boundaries

### Negative
- More handwritten mapping code
- Potential duplication for similar shapes across responses
- Requires discipline to keep mapper files focused and consistent

## Guidelines

### DO
- Keep mapping functions close to the web boundary
- Map every field intentionally, especially nullable -> non-null conversions
- Use dedicated helper functions for repeated normalization
- Keep naming consistent with domain mapping conventions

### DO NOT
- Introduce mapping-library abstractions for API/domain mappings
- Put validation/business decisions in mappers
- Reach into persistence/application services from mapping code
- Perform ad-hoc mapping inline in controllers when reusable mappers already exist

## Examples

### Request -> Command
```kotlin
fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserCommand(
        isBoard = isBoard,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        initials = initials,
        password = password,
        nationality = nationality,
        phoneNumber = phoneNumber,
        discord = discord,
        birthDate = birthDate,
        gender = gender,
        address = address,
        accountNumber = accountNumber,
        receiveNewsletter = receiveNewsletter,
        emergencyContact = emergencyContact,
        emergencyNumber = emergencyNumber,
        study = study,
        startStudy = startStudy,
        expectedGraduation = expectedGraduation,
        canShowEmail = canShowEmail,
        canShowPhoneNumber = canShowPhoneNumber,
        canShowBirthDate = canShowBirthDate,
        canShowAddress = canShowAddress,
    )
```

### Entity -> Response
```kotlin
fun User.asDetailResponse(): UserDetailResponse =
    UserDetailResponse(
        id = id,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        roles = inheritedRoles,
    )
```

## References
- Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
- Clean Architecture (boundary explicitness): https://8thlight.com/insights/uncle-bob/2012/08/13/the-clean-architecture.html
