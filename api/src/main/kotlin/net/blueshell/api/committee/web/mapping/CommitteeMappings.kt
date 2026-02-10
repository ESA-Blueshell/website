package net.blueshell.api.committee.web.mapping

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberDTOMapper : ObjectMappie<CommitteeMember, CommitteeMemberDTO>()

object CommitteeToAdvancedCommitteeDTOMapper : ObjectMappie<Committee, AdvancedCommitteeDTO>()

object CommitteeToSimpleCommitteeDTOMapper : ObjectMappie<Committee, SimpleCommitteeDTO>()

fun CommitteeMemberDTO.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    member.userId = userId!!
    member.committeeId = committeeId!!
    member.role = role
    member.version = version!!
    return member
}

fun AdvancedCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!
    committee.members = members!!.map { it.asEntity() }
    committee.version = version!!
    return committee
}

fun SimpleCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!
    committee.version = version!!
    return committee
}

fun CommitteeMember.asDto(): CommitteeMemberDTO = CommitteeMemberToCommitteeMemberDTOMapper.map(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO = CommitteeToAdvancedCommitteeDTOMapper.map(this)

fun Committee.asSimpleDto(): SimpleCommitteeDTO = CommitteeToSimpleCommitteeDTOMapper.map(this)
