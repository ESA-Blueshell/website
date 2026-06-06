package net.blueshell.api.platform.integration.cohort.persistence

/**
 * The state of a [CohortMember] ledger row, computed from its nullable
 * fields so call sites stop re-deriving it by hand. Lives in the
 * persistence package next to the entity, so the entity stays free of
 * application imports.
 *
 * - `DESIRED`  — the rule engine wants this user here; not pushed yet.
 * - `SYNCED`   — pushed to the external system (`syncedAt`), not yet
 *   confirmed against a live snapshot.
 * - `VERIFIED` — confirmed present in a live remote snapshot (`verifiedAt`,
 *   which implies `syncedAt`).
 * - `STRANGER` — present externally but not desired locally (no `userId`).
 * - `INVALID`  — a field combination no normal transition produces (e.g.
 *   a stranger with no external id, or `verifiedAt` set without `syncedAt`).
 *   The ledger upholds the invariants that keep this empty; it exists so
 *   an impossible row surfaces instead of silently classifying.
 */
enum class CohortMemberState { DESIRED, SYNCED, VERIFIED, STRANGER, INVALID }
