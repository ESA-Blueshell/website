# 2026 1.1.0 Modernization

Period: 2026-02 onward (current active cycle)  
Versioning status: upcoming `1.1.0`

## Privacy lifecycle simplification (2026-02-25)
- Replaced all `ResponseStatusException` in `UserLifecycleService` with domain-specific exceptions (`DeletedUserNotFoundException`, `RestoreWindowExpiredException`, `RestoreConflictException`) and a `@RestControllerAdvice` mapping them to RFC 9457 Problem Details responses (ADR-008).
- Added `UserDeleted` and `UserRestored` domain events published after lifecycle transitions. Moved contact deletion job dispatch from the lifecycle service to `UserLifecycleEventListener` (ADR-006).
- Removed redundant boolean snapshot fields `hadMemberProfile` and `hadAddress` from `DeletedUser`. Restore and finalization now use query-based approaches instead of flag checks.
- Cleared `consentPrivacy` on user deletion (privacy correctness: consent flags are personal data).
- Removed dead code: `consentGdpr` field from `User` and `consent_gdpr` column from `users` table.
- Added `restoreUntilAt` field to `UserDetailResponse` (previously not exposed in API). Frontend `RecoveryUserRow.vue` now shows a chip with remaining restore days (amber when < 7 days).
- Expanded `UserControllerIT` with 10 new integration tests covering: delete/restore without member profile, delete/restore without address, consent cleared on deletion, consent not restored on restoration, 410 Gone on expired window, finalization without profile/address, idempotent finalization, `restoreUntilAt` in response, `UserDeleted` and `UserRestored` domain events.
- Migration V48: drops `had_member_profile`, `had_address` from `deleted_users`; drops `consent_gdpr` from `users`.

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
