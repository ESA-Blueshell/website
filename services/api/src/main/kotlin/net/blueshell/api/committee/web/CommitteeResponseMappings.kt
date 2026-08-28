package net.blueshell.api.committee.web

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember

fun CommitteeMember.asDto(): CommitteeMemberResponse =
    CommitteeMemberResponse(
        userId = this.userId,
        committeeId = this.committeeId,
        role = this.role!!,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Committee.asDetailResponse(): CommitteeDetailResponse =
    CommitteeDetailResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        members = this.members.map { it.asDto() }.toMutableList(),
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Committee.asSummaryResponse(): CommitteeSummaryResponse =
    CommitteeSummaryResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
