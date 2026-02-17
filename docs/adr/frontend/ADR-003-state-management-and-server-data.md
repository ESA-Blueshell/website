# ADR-003: State Management and Server Data Ownership

## Status
Accepted

## Context
The codebase currently uses Vuex. Vue best practices for Vue 3 emphasize composables for feature logic and smaller, explicit state ownership. Global stores are often overused for server data, which causes stale cache issues and duplicated loading/error state.

## Decision
Use explicit state ownership with a transitional strategy from legacy Vuex usage.

### Ownership Model
- Keep Vuex for existing global concerns (session/auth/app-wide flags) during migration.
- Do not add new Vuex modules for domain server collections.
- Use domain query composables for server data fetch/mutate flows.
- Keep feature-local UI state inside feature composables/components.

### Migration Direction
- New global state should be designed with Pinia-compatible patterns (domain-scoped stores and typed actions/getters).
- Introduce Pinia through a dedicated migration ADR when the first migrated domain store is ready.

## Consequences

### Positive
- Less duplicated server-state logic in global stores
- Clearer ownership of data lifecycle and side effects
- Lower risk migration path from current Vuex setup

### Negative
- Temporary coexistence of patterns during migration
- Requires team discipline to avoid defaulting back to global store usage

## Guidelines

### DO
- Keep server data orchestration in domain composables
- Keep Vuex focused on session and true app-global concerns
- Isolate cache invalidation logic near the domain adapter/composable

### DO NOT
- Mirror the same server data in both Vuex and feature state
- Create new Vuex modules for per-page or per-feature data
- Put request lifecycle flags in unrelated global stores

## References
- Vue state management: https://vuejs.org/guide/scaling-up/state-management.html
- Pinia: https://pinia.vuejs.org/
- Vuex: https://vuex.vuejs.org/
