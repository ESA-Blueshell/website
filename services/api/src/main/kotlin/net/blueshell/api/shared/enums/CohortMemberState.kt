package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * The state of a cohort ledger row, computed from its nullable fields so call sites stop
 * re-deriving it by hand.
 *
 * Lives here rather than beside the entity because it crosses the wire: the cohort page reads
 * it per row to say whether a member is in step with the external system, and the enums that
 * reach a response live in `shared.enums` with a schema.
 *
 * - `DESIRED`  — the rule engine wants this user here; not pushed yet.
 * - `SYNCED`   — pushed to the external system (`syncedAt`), not yet confirmed against a live
 *   snapshot.
 * - `VERIFIED` — confirmed present in a live remote snapshot (`verifiedAt`, which implies
 *   `syncedAt`).
 * - `STRANGER` — present externally but not desired locally (no `userId`).
 * - `INVALID`  — a field combination no normal transition produces (a stranger with no
 *   external id, or `verifiedAt` without `syncedAt`). The ledger upholds the invariants that
 *   keep this empty; it exists so an impossible row surfaces instead of being classified as
 *   something healthy.
 */
@Schema(description = "Whether a cohort ledger row is in step with the external system")
enum class CohortMemberState { DESIRED, SYNCED, VERIFIED, STRANGER, INVALID }
