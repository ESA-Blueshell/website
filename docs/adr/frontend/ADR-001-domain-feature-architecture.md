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
├── domains/                    # Business domains, named for the API module
│   └── <domain>/               # board, cohort, esports, user, event, ...
│       ├── adapters/           # Wrappers around the generated client
│       ├── components/         # Domain UI
│       ├── composables/        # Domain logic
│       └── index.ts            # Domain public API
├── pages/                      # Route-level composition only
├── components/                 # Shared UI components (no domain business logic)
├── composables/                # Cross-domain composables
└── utils/, config/, types/     # Cross-domain utilities/config/contracts
```

This is the shape three migrated domains converged on, not the shape originally
recorded here. `api/`, `model/`, `features/` and `state/` were specified and then
never used once: what `board`, `cohort` and `esports` actually grew was
`adapters/`, `components/` and `composables/`. The folder names now follow the
code.

`index.ts` is the exception — it was specified, it is the only way the
cross-domain rule below can be satisfied, and no domain has one. It is required.

**Domain names are singular and match the API module** — `board`, not `boards` —
so one word names the same capability on both sides of the wire.

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

### Enforcement

The rules above were convention-only, and the result is measurable: **63 of 109
files under `pages` and `components` import the generated client directly** — 26
of 47 pages and 37 of 62 components — against a prohibition written here. No
domain exposes an `index.ts`, so the cross-domain rule has never had an
implementation to route through.

An eslint `no-restricted-paths` rule closes both, and lands **before** the
migration rather than after it, because an unenforced rule is what produced the
63:

- `pages/**` and `components/**` may not import `@/services/api`.
- `domains/a/**` may not import `domains/b/**` except `domains/b/index.ts`.

Report-only first, then `error`.

## Consequences

### Positive
- Clear ownership per business capability
- Smaller refactor blast radius when contracts change
- Better testability by isolating feature logic
- Easier onboarding through predictable boundaries

### Negative
- Migration overhead while old and new structures coexist
- Renaming the three existing domains to singular churns their imports once
- A lint rule can enforce the import boundary but not the judgement about what
  belongs in a domain

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
