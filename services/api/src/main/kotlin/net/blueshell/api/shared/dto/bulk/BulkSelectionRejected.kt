package net.blueshell.api.shared.dto.bulk

/**
 * A bulk request naming rows the action cannot be applied to.
 *
 * Bulk actions are driven from a table the client rendered earlier, so a selection
 * can reference users that have since been deleted, or users a rule excludes. Such a
 * request is refused whole rather than partly applied: the operator chose a set, and
 * quietly acting on a subset of it leaves them believing something happened that did
 * not. The offending ids travel back per reason so the client can say which rows are
 * at fault and reload the ones it can no longer trust.
 */
class BulkSelectionRejected(
    val objectName: String,
    val violations: List<Violation>,
) : RuntimeException(
    "Bulk selection rejected: " + violations.joinToString("; ") { "${it.code}=${it.values}" },
) {
    /**
     * One reason a selection was refused.
     *
     * @param field the request field at fault, so a form can attach the message to it
     * @param code stable identifier the client branches on; never a display string
     * @param values the offending ids, so the client can name and reload those rows
     */
    data class Violation(
        val field: String,
        val code: String,
        val values: List<Long>,
        val message: String,
    )

    companion object {
        /** The selection names users that no longer exist; the client's list is stale. */
        const val UNKNOWN_USERS: String = "UnknownUserIds"

        /**
         * The selection names users that have been deleted. Deletion anonymises the
         * account and keeps the row for a restore window, so such a user still resolves
         * by id while no longer being someone a contribution can be recorded against.
         */
        const val DELETED_USERS: String = "DeletedUserIds"

        /** The selection names honorary members, who owe no contribution. */
        const val HONORARY_USERS: String = "HonoraryUserIds"

        /** The selection names a contribution period that no longer exists. */
        const val UNKNOWN_PERIOD: String = "UnknownContributionPeriodId"
    }
}
