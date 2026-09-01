package net.blueshell.api.shared.dto.bulk

/**
 * A field on a bulk request refused by a rule that needed the database, and so could not be
 * a bean constraint.
 *
 * 400 rather than the 409 [BulkSelectionRejected] carries: the request is wrong rather than
 * stale, so the caller corrects the field instead of re-reading the selection. The reasons
 * travel in the same `errors` shape bean validation produces, so one client-side handler
 * covers every refusal in a flow.
 */
class BulkFieldRejected(
    val objectName: String,
    val violations: List<Violation>,
) : RuntimeException(
    "Bulk request rejected: " + violations.joinToString("; ") { "${it.field}=${it.code}" },
) {
    /**
     * @param field the request field at fault, so a form can attach the message to it
     * @param code stable identifier the client branches on; never a display string
     * @param message fixed per code — it interpolates nothing, so it carries no presentation
     *        decision and stays readable in a log
     */
    data class Violation(
        val field: String,
        val code: String,
        val message: String,
    )

    companion object {
        /** Somebody in the batch gets an email that states this date, and it is absent. */
        const val DATE_REQUIRED: String = "DateRequired"

        /** The date falls before the contribution period starts, or too long after it ends. */
        const val DATE_OUTSIDE_PERIOD: String = "DateOutsideContributionPeriod"
    }
}
