package net.blueshell.api.domain.committee.web.mapping

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.dto.*
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberResponseMapper : ObjectMappie<CommitteeMember, CommitteeMemberResponse>()

object CommitteeToCommitteeDetailResponseMapper : ObjectMappie<Committee, CommitteeDetailResponse>()

object CommitteeToCommitteeSummaryResponseMapper : ObjectMappie<Committee, CommitteeSummaryResponse>()

fun CommitteeMemberRequest.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    member.id.userId = userId!!
    member.role = role
    return member
}

fun CreateCommitteeRequest.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!

    val existingByUserId = committee.members.associateBy { it.userId }

    val updatedMembers = requireNotNull(members).map { dto ->
        val member = existingByUserId[dto.userId] ?: CommitteeMember()
        dto.asEntity(member)
    }

    committee.replaceMembers(updatedMembers)
    return committee
}

fun UpdateCommitteeRequest.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!

    val existingByUserId = committee.members.associateBy { it.userId }

    val updatedMembers = requireNotNull(members).map { dto ->
        val member = existingByUserId[dto.userId] ?: CommitteeMember()
        dto.asEntity(member)
    }

    committee.replaceMembers(updatedMembers)

    version?.let { committee.version = it }
    return committee
}

fun CommitteeMember.asDto(): CommitteeMemberResponse = CommitteeMemberToCommitteeMemberResponseMapper.map(this)

fun Committee.asDetailResponse(): CommitteeDetailResponse = CommitteeToCommitteeDetailResponseMapper.map(this)

fun Committee.asSummaryResponse(): CommitteeSummaryResponse = CommitteeToCommitteeSummaryResponseMapper.map(this)
