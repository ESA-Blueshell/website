# Frontend Architecture Decision Records Index

This index tracks frontend architecture decisions for the Vue application.

## ADR List

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-domain-feature-architecture.md) | Domain Feature Architecture | Accepted | Domain-first package boundaries with incremental migration from technical folders |
| [002](ADR-002-api-client-boundary-and-domain-mapping.md) | API Client Boundary and Domain Mapping | Accepted | Generated clients are infrastructure; domains expose stable adapters and mappings |
| [003](ADR-003-state-management-and-server-data.md) | State Management and Server Data Ownership | Accepted | Explicit ownership model for Vuex transition, composables, and server data lifecycle |
| [004](ADR-004-form-validation-and-command-mapping.md) | Form Validation and Command Mapping | Accepted | Three-stage validation and explicit mapping from form models to command payloads |
| [005](ADR-005-routing-and-authorization.md) | Routing and Authorization | Accepted | Modular routes with centralized guard evaluation and route metadata policy |
| [006](ADR-006-component-and-composable-standards.md) | Component and Composable Standards | Accepted | Vue 3 Composition API standards and clear responsibility split |
| [007](ADR-007-testing-and-quality-gates.md) | Testing and Quality Gates | Superseded | Replaced by the [testing ADR set](../testing/ADR-INDEX.md); contract-safety rules carried forward |

## Related Documentation
- [ADR umbrella index](../ADR-INDEX.md)
- [API ADR index](../api/ADR-INDEX.md)
- [Testing ADR index](../testing/ADR-INDEX.md)
- [CLAUDE.md](../../../CLAUDE.md)
