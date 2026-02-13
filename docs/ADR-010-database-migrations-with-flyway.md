# ADR-010: Database Migrations with Flyway

## Status
Accepted

## Context
Database schema needs to evolve with application code while maintaining version control and consistency across environments.

## Decision
We use **Flyway** for database migrations with versioned SQL scripts.

### Migration Structure
```
api/src/main/resources/db/migration/
├── V0__Init.sql
├── V1__add_user_table.sql
├── V2__add_event_table.sql
└── V3__add_indexes.sql
```

### Migration Naming
- Pattern: `V{version}__{description}.sql`
- Version: Sequential integers (V0, V1, V2, ...)
- Description: Snake_case describing the change

### Migration Content
```sql
-- V1__add_user_table.sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
```

## Guidelines

### DO:
- ✅ Write idempotent migrations when possible
- ✅ Test migrations on copy of production data
- ✅ Include rollback scripts (separate files)
- ✅ Use transactions for data migrations
- ✅ Add indexes in separate migrations
- ✅ Document complex migrations

### DON'T:
- ❌ Modify existing migrations (create new ones)
- ❌ Use application code in migrations
- ❌ Skip version numbers
- ❌ Mix DDL and DML in same migration (when possible)
- ❌ Create circular dependencies

## Consequences
- **Positive**: Version controlled schema, reproducible, automatic
- **Negative**: Can't easily rollback, failed migrations block startup

## References
- Flyway Documentation
- Database Versioning Best Practices
