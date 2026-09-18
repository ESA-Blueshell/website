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
cross-domain rule below can be satisfied, and every domain has one. It is
required: `association`, `auth`, `boards`, `cohorts`, `committees`,
`contribution`, `emails`, `esports`, `jobs`, `recovery` and `user` each name
what they offer through a door, and nothing outside a domain may reach past it.

**A domain is named for the API module it wraps.** Whether that name is singular
was specified here and never held to: `boards`, `cohorts`, `committees`, `emails`
and `jobs` are plural, the other six read as singular, and nothing enforces
either. #1314 settles which way it goes — until then, follow the directory that
is already there rather than this sentence.

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

Both rules are enforced. They are one `no-restricted-imports` entry in
`services/frontend/eslint.config.mjs` with two patterns, at `error`:

- `pages/**` and `components/**` may not import `@/services/api`.
- Nothing may import `@/domains/<a>/<anything>` except the door, `@/domains/<a>`.

One entry rather than two blocks, because a second block naming the same rule
replaces the first rather than adding to it — which is how the first draft of
this let a client import through.

The rules were convention-only until then, and the cost was measurable: **63 of
109 files under `pages` and `components` imported the generated client
directly**. The files that predate the rule are named one by one in a
`CROSSES_THE_BOUNDARY` allowlist, which is the debt count — a new violation is a
red build, and the count cannot drift from what the build actually permits. It
stood at 70 when the rule landed and stands at **42** today: 7 pages and 35
components. It is deleted when it empties.

There is one exception, and it is a decision rather than a gap: **a `.vue`
component may be imported at its own path**. A component is imported where it is
drawn, and routing components through a barrel loads a domain's whole surface
into anything that renders one — which broke two unit suites and, measured,
saved nothing in the bundle. The regex carries that exception, and the rule
message says so.

The second rule is scoped to `pages/**` and `components/**`. A domain deep-importing
another domain is not yet checked.

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
