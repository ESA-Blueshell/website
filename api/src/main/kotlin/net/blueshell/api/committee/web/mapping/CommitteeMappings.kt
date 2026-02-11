package net.blueshell.api.committee.web.mapping

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.user.persistence.User
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberDTOMapper : ObjectMappie<CommitteeMember, CommitteeMemberDTO>()

object CommitteeToAdvancedCommitteeDTOMapper : ObjectMappie<Committee, AdvancedCommitteeDTO>()

object CommitteeToSimpleCommitteeDTOMapper : ObjectMappie<Committee, SimpleCommitteeDTO>()

fun CommitteeMemberDTO.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    member.user = User::class.asRef(userId!!)
    member.committee = Committee::class.asRef(committeeId!!)
    member.role = role
    version?.let { member.version = it }
    return member
}

fun AdvancedCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!
    val mappedMembers = members!!.map { it.asEntity() }
    committee.members = mappedMembers
    version?.let { committee.version = it }
    return committee
}

fun SimpleCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!
    version?.let { committee.version = it }
    return committee
}

fun CommitteeMember.asDto(): CommitteeMemberDTO = CommitteeMemberToCommitteeMemberDTOMapper.map(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO = CommitteeToAdvancedCommitteeDTOMapper.map(this)

fun Committee.asSimpleDto(): SimpleCommitteeDTO = CommitteeToSimpleCommitteeDTOMapper.map(this)
