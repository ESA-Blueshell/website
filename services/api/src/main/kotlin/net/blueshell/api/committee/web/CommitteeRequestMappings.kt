package net.blueshell.api.committee.web

import net.blueshell.api.committee.api.CommitteePage
import net.blueshell.api.committee.domain.CommitteeMemberData

fun CommitteeMemberRequest.asData(): CommitteeMemberData =
    CommitteeMemberData(
        userId = this.userId,
        // Blank and absent are the same thing, and the column already holds null for it,
        // so they collapse to one representation here rather than two in the data.
        role = this.role?.takeIf { it.isNotBlank() },
    )

fun CreateCommitteeRequest.page(): CommitteePage = CommitteePage(address = slug, listed = listed, banner = banner, gameCodes = gameCodes)

fun UpdateCommitteeRequest.page(): CommitteePage = CommitteePage(address = slug, listed = listed, banner = banner, gameCodes = gameCodes)
