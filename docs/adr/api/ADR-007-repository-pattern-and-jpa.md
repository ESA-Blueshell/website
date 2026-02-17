# ADR-007: Repository Pattern and JPA

## Status
Accepted

## Context
Data access in Spring Boot applications requires a strategy for querying and persisting entities while maintaining clean architecture and testability.

## Decision
We use **Spring Data JPA** with the **Repository pattern** for data access, keeping repositories in the persistence layer.

### Repository Structure
```
persistence/
├── User.kt              # JPA entity
├── Address.kt
└── repository/
    ├── UserRepository.kt
    └── AddressRepository.kt
```

### Repository Interface
```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
```

## Guidelines

### DO:
- ✅ Extend `JpaRepository<T, ID>` for CRUD operations
- ✅ Use Spring Data query methods for simple queries
- ✅ Use `@Query` for complex queries
- ✅ Return `Optional<T>` for single results
- ✅ Use `getReferenceById()` for lazy loading
- ✅ Keep repositories in `persistence/repository/` package

### DON'T:
- ❌ Put business logic in repositories
- ❌ Make repositories depend on services
- ❌ Use repositories directly from controllers
- ❌ Return entities from web layer
- ❌ Expose repositories to other domains (use services)

## Consequences
- **Positive**: Clean separation, testable, leverages Spring Data
- **Negative**: Limited to JPA capabilities, potential N+1 queries

## References
- Spring Data JPA Documentation
- Repository Pattern (Domain-Driven Design)
