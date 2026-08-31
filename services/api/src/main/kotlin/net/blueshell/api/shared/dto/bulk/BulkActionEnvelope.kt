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

/**
 * Which side of the fee cycle a member is on.
 *
 * The `incasso` flag on the member's membership decides it, so this is not a choice the
 * operator makes: one group is told what will be debited, the other is asked to transfer.
 */
@Schema(name = "FeeCycleGroup", enumAsRef = true)
enum class FeeCycleGroup {
    /** Pays by direct debit, and is told what will be taken and when. */
    DIRECT_DEBIT,

    /** Pays by transfer, and is asked to pay what is owed by when. */
    TRANSFER,
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

    /**
     * Dead: nothing sets it. It existed because the payment request and the pre-notification
     * were two independent sends, so a member could be selected for the wrong one. Under the
     * fee cycle the `incasso` flag decides which side of the partition a member is on, so
     * there is no wrong one and not having the flag is not a warning. Kept for one release so
     * a client reading a stored value still recognises it.
     */
    @Deprecated("Nothing populates this. The fee-cycle partition replaced it.")
    INCASSO_MISMATCH,
    NO_ACTIVE_MEMBERSHIP,
    STARTED_TODAY,

    /**
     * Email actions (reminder/incasso): the user has no email address on file, so
     * nothing can be sent. Previously the execute handler skipped these silently and
     * the preview never surfaced it — now it is a first-class SKIPPED reason visible
     * in the preview. See docs/flows/fee-cycle/README.md.
     */
    NO_EMAIL,
    /**
     * The account has been deleted. Deletion anonymises the address to a placeholder and
     * keeps the row for a restore window, and it does not end the memberships — so a deleted
     * account still looks like a member of a period, and an action that reads memberships has
     * to say so rather than write to it.
     */
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
