package net.blueshell.api.domain.committee.web.mapping

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.domain.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.domain.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.domain.committee.web.mapping.asEntity
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.domain.user.persistence.User
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberDTOMapper : ObjectMappie<CommitteeMember, CommitteeMemberDTO>()

object CommitteeToAdvancedCommitteeDTOMapper : ObjectMappie<Committee, AdvancedCommitteeDTO>()

object CommitteeToSimpleCommitteeDTOMapper : ObjectMappie<Committee, SimpleCommitteeDTO>()

fun CommitteeMemberDTO.asEntity(member: CommitteeMember = CommitteeMember()): CommitteeMember {
    member.id.userId = userId!!
    member.role = role
    version?.let { member.version = it }
    return member
}

fun AdvancedCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
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

fun SimpleCommitteeDTO.asEntity(committee: Committee = Committee()): Committee {
    committee.name = name!!
    committee.description = description!!
    version?.let { committee.version = it }
    return committee
}

fun CommitteeMember.asDto(): CommitteeMemberDTO = CommitteeMemberToCommitteeMemberDTOMapper.map(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO = CommitteeToAdvancedCommitteeDTOMapper.map(this)

fun Committee.asSimpleDto(): SimpleCommitteeDTO = CommitteeToSimpleCommitteeDTOMapper.map(this)
