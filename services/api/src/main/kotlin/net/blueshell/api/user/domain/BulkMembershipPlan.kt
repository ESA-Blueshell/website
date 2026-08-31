package net.blueshell.api.user.domain

import java.time.LocalDate

/**
 * What a bulk membership action would do to a whole selection, and the day it would do it.
 *
 * The preview endpoint answers with this, mapped to a response at the web boundary. Rows
 * keep the order they were selected in.
 */
data class BulkMembershipPlan(
    val effectiveDate: LocalDate,
    val rows: List<Row>,
) {
    data class Row(val userId: Long, val decision: BulkMembershipDecision)
}
