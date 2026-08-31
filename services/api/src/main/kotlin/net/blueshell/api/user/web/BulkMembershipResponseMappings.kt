package net.blueshell.api.user.web

import net.blueshell.api.user.domain.BulkMembershipPlan

fun BulkMembershipPlan.asResponse(): BulkMembershipPreviewResponse =
    BulkMembershipPreviewResponse(
        effectiveDate = this.effectiveDate,
        rows = this.rows.map {
            BulkMembershipPreviewRowResponse(
                userId = it.userId,
                disposition = it.decision.disposition,
                reason = it.decision.reason,
            )
        },
    )
