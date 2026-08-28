package net.blueshell.api.committee.web

import net.blueshell.api.committee.domain.CommitteeMemberData

fun CommitteeMemberRequest.asData(): CommitteeMemberData =
    CommitteeMemberData(
        userId = this.userId,
        // Blank and absent are the same thing, and the column already holds null for it,
        // so they collapse to one representation here rather than two in the data.
        role = this.role?.takeIf { it.isNotBlank() },
    )
