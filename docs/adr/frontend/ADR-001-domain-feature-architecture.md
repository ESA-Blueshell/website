# ADR-001: Domain Feature Architecture

## Status
Accepted

## Context
The frontend has historically been organized by technical folders (`pages`, `components`, `services`, `utils`). This works for small change sets, but API contract shifts now happen per domain and can cause broad refactors across unrelated UI files.

We need a structure that:
- keeps feature ownership clear
- reduces coupling to transport shapes
- scales with API domains
- remains practical for incremental migration

## Decision
Adopt a domain-first frontend structure with strict boundaries and incremental migration.

### Structure

```text
src/
├── app/                        # App bootstrap, router composition, plugin wiring
├── domains/                    # Business domains (auth, user, event, sponsor, ...)
│   └── <domain>/
│       ├── api/                # Domain adapters around generated clients
│       ├── model/              # Domain model/types used by UI
│       ├── features/           # Use-case UI + composables
│       ├── state/              # Domain state abstractions
│       └── index.ts            # Domain public API
├── pages/                      # Route-level composition only
├── components/                 # Shared UI components (no domain business logic)
└── shared/                     # Cross-domain utilities/config/contracts
```

### Dependency Rules
- `app` may depend on `pages`, `domains`, `components`, `shared`
- `pages` may depend on `domains`, `components`, `shared`
- `domains/<a>` may depend on `shared` and its own internals
- `components` and `shared` must not depend on domain internals
- cross-domain access must go through each domain's public API (`index.ts`)

### Migration Rules
- New feature work goes to `domains/<domain>` first.
- Existing technical folders may remain during migration, but migrated features must stop depending on legacy internals.
- Pages stay thin and should not call generated API clients directly.

## Consequences

### Positive
- Clear ownership per business capability
- Smaller refactor blast radius when contracts change
- Better testability by isolating feature logic
- Easier onboarding through predictable boundaries

### Negative
- Migration overhead while old and new structures coexist
- Requires discipline around public APIs and import boundaries

## Guidelines

### DO
- Keep domain logic in domain packages
- Expose stable domain APIs through `domains/<domain>/index.ts`
- Keep `pages` focused on route composition and layout

### DO NOT
- Put business workflows directly in shared components
- Deep-import another domain's internal files
- Treat `shared` as a catch-all for domain-specific helpers

## References
- Vue Style Guide: https://vuejs.org/style-guide/
- Vue application scaling guidance: https://vuejs.org/guide/scaling-up/
