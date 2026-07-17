package net.blueshell.api.shared.dto.bulk

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/**
 * Shared preview/execute envelope for member-manager bulk actions.
 *
 * Lives in the shared kernel so every domain's bulk endpoints (contributions,
 * memberships, and — later — reminders/incasso) return the same shape and the
 * frontend can drive one confirmation dialog. All business logic that produces
 * these values stays in the per-domain command handlers; this is pure data.
 */

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

@Schema(name = "BulkPreviewRow")
data class BulkPreviewRow(
    val userId: Long,
    val name: String,
    val memberType: MemberType? = null,
    val memberSince: LocalDate? = null,
    val disposition: BulkRowDisposition,
    /** Machine-readable reason code for a non-INCLUDED disposition (e.g. "ALREADY_PAID"). */
    val reason: String? = null,
    /** Resolved fee for the email actions (null for non-email actions). */
    val amount: Double? = null,
    /** When this user was last sent this email for the period (email actions only). */
    val lastSentOn: LocalDate? = null,
)

@Schema(name = "BulkActionCounts")
data class BulkActionCounts(
    val selected: Int,
    val willApply: Int,
    val skipped: Int,
    val excluded: Int,
    val warned: Int,
) {
    companion object {
        fun of(rows: List<BulkPreviewRow>): BulkActionCounts = BulkActionCounts(
            selected = rows.size,
            willApply = rows.count { it.disposition == BulkRowDisposition.INCLUDED },
            skipped = rows.count { it.disposition == BulkRowDisposition.SKIPPED },
            excluded = rows.count { it.disposition == BulkRowDisposition.EXCLUDED },
            warned = rows.count { it.disposition == BulkRowDisposition.WARNING },
        )
    }
}

@Schema(name = "BulkPreviewResult")
data class BulkPreviewResult(
    val action: String,
    val contributionPeriodId: Long? = null,
    val counts: BulkActionCounts,
    val rows: List<BulkPreviewRow>,
) {
    companion object {
        fun of(action: String, contributionPeriodId: Long?, rows: List<BulkPreviewRow>): BulkPreviewResult =
            BulkPreviewResult(action, contributionPeriodId, BulkActionCounts.of(rows), rows)
    }
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
