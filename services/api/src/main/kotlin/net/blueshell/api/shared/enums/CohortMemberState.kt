package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/**
 * The state of a cohort ledger row, computed from its nullable fields so call sites stop
 * re-deriving it. Lives in `shared.enums` rather than beside the entity because it crosses the
 * wire, and an enum that reaches a response carries a schema.
 */
@Schema(description = "Whether a cohort ledger row is in step with the external system")
enum class CohortMemberState {
    /** The rule engine wants this user here, and nothing has been pushed yet. */
    DESIRED,

    /** Pushed to the external system, not yet confirmed against a live snapshot. */
    SYNCED,

    /** Confirmed present in a live remote snapshot, which implies it was synced. */
    VERIFIED,

    /** Present externally but not desired locally, so it carries no `userId`. */
    STRANGER,

    /**
     * A field combination no normal transition produces: a stranger with no external id, or a
     * row verified without being synced. The ledger's invariants keep this empty; it exists so
     * an impossible row surfaces instead of passing as something healthy.
     */
    INVALID,
}
