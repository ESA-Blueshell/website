package net.blueshell.api.domain.committee.web.mapping.response

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.dto.response.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeMemberResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeSummaryResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeRole

fun CommitteeMember.asDto(): CommitteeMemberResponse =
    CommitteeMemberResponse(
        userId = this.userId,
        committeeId = this.committeeId,
        role = CommitteeRole.valueOf(this.role!!),
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
