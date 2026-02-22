# ADR-005: Routing and Authorization

## Status
Accepted

## Context
Route configuration and access checks become inconsistent when route guards are scattered across pages/components. As domain boundaries get stronger, route ownership and policy checks need a single, testable flow.

## Decision
Use modular route definitions with centralized guard evaluation.

### Route Model
- Each domain can provide route fragments.
- `app/router` composes domain route fragments.
- Route components are lazy-loaded by default.

### Authorization Model
- Route `meta` is the source of truth for UI access checks:
  - `requiresAuth`
  - `requiredRoles`
  - `featureFlag` (optional)
- One global guard evaluates auth/session state and route metadata.
- Frontend guards provide UX flow only; backend remains the authorization authority.

## Consequences

### Positive
- Consistent navigation and access behavior
- Single place to test route policy logic
- Better bundle loading via lazy routes

### Negative
- Requires clear naming and ownership of route metadata
- Guard logic must stay simple and deterministic

## Guidelines

### DO
- Keep route records near owning domains
- Use named routes and typed route params
- Keep guard side effects minimal and explicit

### DO NOT
- Duplicate role checks across many components
- Use component-level redirects as primary access control
- Eager-load heavy route components without need

## References
- Vue Router: https://router.vuejs.org/
- Route meta fields: https://router.vuejs.org/guide/advanced/meta.html
- Lazy loading routes: https://router.vuejs.org/guide/advanced/lazy-loading
