# ADR-020: Shared Kernel Governance

## Status
Accepted

## Context

The `shared/` package contains code used across multiple bounded contexts: command infrastructure, event publishing, base entities, validators, DTOs. Evans warns that **Shared Kernel** is an "intimate interdependency" requiring explicit governance and continuous integration ([Domain Language][1]).

Without governance:
- Shared code becomes dumping ground
- Breaking changes affect multiple domains
- Unclear ownership and responsibility
- Testing becomes complex

## Decision

We govern the **Shared Kernel** (`shared/` package) with strict rules:

### Shared Kernel Scope
```
shared/
├── command/        # Command pattern infrastructure
├── event/          # Event publishing (AfterCommitEventPublisher)
├── validation/     # Reusable validators (UniqueUserCommand, etc.)
├── dto/            # Base DTOs (AuditedAutoIdDTO, VersionedDTO)
├── model/          # Base entities (Auditable, Identifiable)
├── enums/          # Cross-domain enums (Role, MemberType)
└── security/       # Security primitives (CurrentUser, etc.)
```

### Governance Rules

1. **Joint Ownership** - All domains have input on changes
2. **Versioning** - Changes require version bump if breaking
3. **Testing** - 100% test coverage required
4. **Documentation** - All public APIs documented
5. **Review** - Changes require approval from 2+ domain maintainers
6. **Compatibility** - Prefer additive changes over breaking changes

## Guidelines

### What Belongs in Shared Kernel

**✅ Include:**
- Infrastructure code (Command, Event patterns)
- Base classes with stable contracts (Auditable)
- True cross-cutting concerns (Security, Validation framework)
- Stable enums used by 3+ domains (Role)

**❌ Exclude:**
- Domain-specific business logic
- Volatile domain concepts
- Code used by only 1-2 domains (duplicate instead)
- External API clients (use ACLs in platform/)

### DO:
- ✅ Keep Shared Kernel minimal
- ✅ Version changes (SemVer)
- ✅ Require 2+ approvals for changes
- ✅ Test exhaustively
- ✅ Document breaking changes
- ✅ Prefer duplication over premature sharing

### DON'T:
- ❌ Add domain logic to shared/
- ❌ Break compatibility without major version bump
- ❌ Skip tests for shared code
- ❌ Allow single-domain ownership

## References
- Eric Evans, "Shared Kernel" in DDD Reference ([Domain Language][1])
- DevIQ, Shared Kernel in DDD ([DevIQ][2])

[1]: https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf
[2]: https://deviq.com/domain-driven-design/shared-kernel
