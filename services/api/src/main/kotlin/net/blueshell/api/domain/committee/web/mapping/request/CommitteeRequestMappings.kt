package net.blueshell.api.domain.committee.web.mapping.request

import net.blueshell.api.domain.committee.application.CommitteeMemberData
import net.blueshell.api.domain.committee.web.dto.request.CommitteeMemberRequest

fun CommitteeMemberRequest.asData(): CommitteeMemberData =
    CommitteeMemberData(
        userId = this.userId,
        // Blank and absent are the same thing, and the column already holds null for it,
        // so they collapse to one representation here rather than two in the data.
        role = this.role?.takeIf { it.isNotBlank() },
    )
