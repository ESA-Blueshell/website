# 2026 1.1.x Modernization

Period: 2026-02 onward (current active cycle)
Versioning status: `1.1.0` → `1.1.1`

## User account lifecycle improvements (2026-02)
- Improved error responses for user deletion and restoration with clearer status codes and messages.
- User deletion and restoration now publish domain events, enabling better audit trails and decoupled side-effects.
- Simplified the deleted user snapshot by removing redundant flags in favour of query-based detection.
- Removed the unused GDPR consent field.
- The recovery manager now shows how many days remain before a deleted user can no longer be restored.

## 1.1.1 — Privacy and consent policy updates (2026-02-25)
- Updated the privacy policy and reworked the consent fields shown during registration and profile editing.
- Photo consent is now a user-level setting that can be managed independently by both the user and the board, rather than being tied to the member profile.
- Photo consent is preserved when a user account is deleted and included in the deleted user snapshot for restoration.
- Simplified the privacy consent layout in the registration and profile forms.

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
- `1.1.x` is the modernization release line following `1.0.0`, centered on architecture quality, operability, and reliability at scale.
