# ADR-006: Component and Composable Standards

## Status
Accepted

## Context
As the frontend grows, components can become hard to maintain when rendering, side effects, and domain orchestration are mixed. Consistent Composition API patterns improve readability, reuse, and testability.

## Decision
Standardize on Vue 3 Composition API patterns with clear component/composable responsibilities.

### Standards
- Use `<script setup lang="ts">` for new components.
- Keep templates declarative; move branching and orchestration into composables/computed state.
- Keep domain-specific composables inside the owning domain package.
- Shared components (`components/base`, `components/common`, `components/form`) remain domain-agnostic.

### Responsibility Split
- Pages: route-level composition
- Domain feature components: use-case UI and orchestration
- Shared components: reusable presentation primitives
- Composables: reusable state/behavior with explicit inputs/outputs

## Consequences

### Positive
- Better separation of concerns and file readability
- Easier unit testing of logic via composables
- Lower coupling between UI primitives and business workflows

### Negative
- Legacy components need incremental refactoring
- Can increase number of files in complex features

## Guidelines

### DO
- Type props/emits explicitly
- Keep composables side-effect aware and predictable
- Add explicit loading/empty/error states for async views

### DO NOT
- Call generated API clients directly in presentational components
- Hide domain behavior in generic utility folders
- Put unrelated side effects in shared composables

## References
- `<script setup>`: https://vuejs.org/api/sfc-script-setup.html
- Composition API FAQ: https://vuejs.org/guide/extras/composition-api-faq.html
- Vue accessibility best practices: https://vuejs.org/guide/best-practices/accessibility.html
