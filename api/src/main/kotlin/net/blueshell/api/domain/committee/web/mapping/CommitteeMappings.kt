package net.blueshell.api.domain.committee.web.mapping

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.dto.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.CommitteeMemberResponse
import net.blueshell.api.domain.committee.web.dto.CommitteeSummaryResponse
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberResponseMapper : ObjectMappie<CommitteeMember, CommitteeMemberResponse>()

object CommitteeToCommitteeDetailResponseMapper : ObjectMappie<Committee, CommitteeDetailResponse>()

object CommitteeToCommitteeSummaryResponseMapper : ObjectMappie<Committee, CommitteeSummaryResponse>()

fun CommitteeMember.asDto(): CommitteeMemberResponse = CommitteeMemberToCommitteeMemberResponseMapper.map(this)

fun Committee.asDetailResponse(): CommitteeDetailResponse = CommitteeToCommitteeDetailResponseMapper.map(this)

fun Committee.asSummaryResponse(): CommitteeSummaryResponse = CommitteeToCommitteeSummaryResponseMapper.map(this)
