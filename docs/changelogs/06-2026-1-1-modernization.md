# 2026 1.1.0 Modernization

Period: 2026-02 onward (current active cycle)  
Versioning status: upcoming `1.1.0`

## Implemented/in-progress functionality
- Full backend migration track to Kotlin was executed across the codebase.
- Queueing and async processing matured around RabbitMQ-backed job handling.
- Retry-aware job handling and richer job observability/control were introduced.
- Security and permission infrastructure were strengthened and reorganized.
- Domain architecture was formalized around DDD and package-by-feature boundaries, with stronger architecture test enforcement.
- Validation and command-handling layers were restructured for clearer web/application separation.
- Testing scope expanded substantially across:
  - architecture tests,
  - unit and integration tests,
  - API security/controller tests,
  - frontend system tests.
- Coverage handling matured into merged backend+frontend reporting, CI artifact publication, and threshold gating.
- Frontend and API quality gates in CI were broadened and made more consistent.

## 1.1.0 direction
This release track is focused on:
- completing Kotlin-first backend architecture,
- solidifying RabbitMQ queue/retry behavior,
- achieving very high test/coverage confidence,
- closing security hardening gaps,
- enforcing clear cross-domain communication boundaries.

## Outcome
- `1.1.0` is the large modernization release line following `1.0.0`, centered on architecture quality, operability, and reliability at scale.
