package net.blueshell.api.domain.committee.web.mapping.response

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.dto.response.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeMemberResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeSummaryResponse
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberResponseMapper : ObjectMappie<CommitteeMember, CommitteeMemberResponse>() {
    override fun map(from: CommitteeMember) = mapping {
        CommitteeMemberResponse::role fromValue from.role!!
    }
}

object CommitteeToCommitteeDetailResponseMapper : ObjectMappie<Committee, CommitteeDetailResponse>() {
    override fun map(from: Committee) = mapping {
        CommitteeDetailResponse::id fromValue from.id!!
    }
}

object CommitteeToCommitteeSummaryResponseMapper : ObjectMappie<Committee, CommitteeSummaryResponse>() {
    override fun map(from: Committee) = mapping {
        CommitteeSummaryResponse::id fromValue from.id!!
    }
}

fun CommitteeMember.asDto(): CommitteeMemberResponse = CommitteeMemberToCommitteeMemberResponseMapper.map(this)

fun Committee.asDetailResponse(): CommitteeDetailResponse = CommitteeToCommitteeDetailResponseMapper.map(this)

fun Committee.asSummaryResponse(): CommitteeSummaryResponse = CommitteeToCommitteeSummaryResponseMapper.map(this)
