package net.blueshell.api.shared.dto.bulk

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Shared preview/execute envelope for member-manager bulk actions.
 *
 * Lives in the shared kernel so every domain's bulk endpoints (contributions,
 * memberships, and — later — reminders/incasso) return the same shape and the
 * frontend can drive one confirmation dialog. All business logic that produces
 * these values stays in the per-domain command handlers; this is pure data.
 */

/**
 * Fee type used for contribution-reminder and incasso-notification bulk actions.
 * The server resolves the € amount from the selected period's fee for the chosen type.
 */
@Schema(name = "BulkFeeType", enumAsRef = true)
enum class BulkFeeType {
    /** Full-year fee — for REGULAR members who started on or before the half-year cutoff. */
    FULL_YEAR_FEE,

    /** Half-year fee — for REGULAR members who started after the half-year cutoff. */
    HALF_YEAR_FEE,

    /** Alumni fee — for ALUMNI members. */
    ALUMNI_FEE,
}

/** How a selected user will be treated by a bulk action. */
@Schema(name = "BulkRowDisposition", enumAsRef = true)
enum class BulkRowDisposition {
    /** Will be acted on / emailed. */
    INCLUDED,

    /** No-op for this action (e.g. already paid, no active membership). */
    SKIPPED,

    /** Hard-excluded by a business rule and NOT overridable (e.g. honorary). */
    EXCLUDED,

    /** Excluded by default but the operator may opt the user back in (e.g. already-paid / no incasso). */
    WARNING,
}

/** Machine-readable reason code for a non-INCLUDED disposition. */
@Schema(name = "BulkRowReason", enumAsRef = true)
enum class BulkRowReason {
    ALREADY_PAID,
    NOT_PAID,
    HONORARY,

    /** Dead: routing by the direct-debit flag replaced it. Kept one release for stored values. */
    @Deprecated("Nothing populates this.")
    INCASSO_MISMATCH,

    /** No membership running right now. Read by the membership actions. */
    NO_ACTIVE_MEMBERSHIP,

    /**
     * Held no membership overlapping the period being billed. Distinct from
     * [NO_ACTIVE_MEMBERSHIP]: a current member can still not have been here in 2024.
     */
    NOT_MEMBER_IN_PERIOD,
    STARTED_TODAY,

    /** No address on file, so there is nothing to send to. */
    NO_EMAIL,

    /** Deletion anonymises the address and leaves the memberships running, so it must be asked. */
    DELETED,
    /** Resume/start-new: the user already has an active (endDate=null) membership. */
    ALREADY_ACTIVE,
    /** Resume/start-new: no contribution period exists at all. */
    NO_CONTRIBUTION_PERIOD,
    /**
     * Preview outcome for INCLUDED rows: the most-recent membership will be reopened
     * rather than a new one started.
     *
     * Nothing produces this today. Starting a membership in bulk always opens a fresh
     * spell, so that a member who left and came back reads as two stays rather than one
     * long one. Kept because the distinction is a real one the vocabulary should be able
     * to make, and reopening remains available one membership at a time.
     */
    WILL_RESUME,
    /** Preview outcome for INCLUDED rows: a new membership will be inserted starting today. */
    WILL_START_NEW,
}

@Schema(name = "BulkActionResult")
data class BulkActionResult(
    /** Rows changed (contributions created/deleted, memberships ended). */
    val applied: Int,
    /** Rows deliberately not changed (no-ops / excluded). */
    val skipped: Int,
    /** Emails queued (0 for non-email actions). */
    val queued: Int = 0,
)
