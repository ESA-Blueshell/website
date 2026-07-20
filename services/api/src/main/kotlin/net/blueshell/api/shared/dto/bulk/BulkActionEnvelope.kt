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
@Schema(name = "BulkFeeType")
enum class BulkFeeType {
    /** Full-year fee — for REGULAR members who started before the half-year cutoff. */
    FULL_YEAR_FEE,

    /** Half-year fee — for REGULAR members who started on or after the half-year cutoff. */
    HALF_YEAR_FEE,

    /** Alumni fee — for ALUMNI members. */
    ALUMNI_FEE,
}

/** How a selected user will be treated by a bulk action. */
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
@Schema(name = "BulkRowReason")
enum class BulkRowReason {
    ALREADY_PAID,
    NOT_PAID,
    HONORARY,
    INCASSO_MISMATCH,
    NO_ACTIVE_MEMBERSHIP,
    STARTED_TODAY,

    /**
     * Email actions (reminder/incasso): the user has no email address on file, so
     * nothing can be sent. Previously the execute handler skipped these silently and
     * the preview never surfaced it — now it is a first-class SKIPPED reason visible
     * in the preview. See docs/proposals/bulk-actions/REDESIGN.md §3.
     */
    NO_EMAIL,
    /** Resume/start-new: the user already has an active (endDate=null) membership. */
    ALREADY_ACTIVE,
    /** Resume/start-new: no contribution period exists at all. */
    NO_CONTRIBUTION_PERIOD,
    /** Preview outcome for INCLUDED rows: the most-recent membership will be resumed. */
    WILL_RESUME,
    /** Preview outcome for INCLUDED rows: a new membership will be inserted starting today. */
    WILL_START_NEW,
}

/** Type of bulk action being performed. */
@Schema(name = "BulkActionType")
enum class BulkActionType {
    MARK_PAID,
    MARK_UNPAID,
    CONTRIBUTION_REMINDER,
    INCASSO_NOTIFICATION,
    END_MEMBERSHIP,
    RESUME_MEMBERSHIP,
}

@Schema(name = "BulkActionCounts")
data class BulkActionCounts(
    val selected: Int,
    val willApply: Int,
    val skipped: Int,
    val excluded: Int,
    val warned: Int,
)

@Schema(name = "BulkActionResult")
data class BulkActionResult(
    /** Rows changed (contributions created/deleted, memberships ended). */
    val applied: Int,
    /** Rows deliberately not changed (no-ops / excluded). */
    val skipped: Int,
    /** Emails queued (0 for non-email actions). */
    val queued: Int = 0,
)
