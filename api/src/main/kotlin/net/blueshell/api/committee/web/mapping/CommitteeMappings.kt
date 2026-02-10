package net.blueshell.api.committee.web.mapping

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import tech.mappie.api.ObjectMappie

object CommitteeMemberToCommitteeMemberDTOMapper : ObjectMappie<CommitteeMember, CommitteeMemberDTO>()

object CommitteeMemberDTOToCommitteeMemberMapper : ObjectMappie<CommitteeMemberDTO, CommitteeMember>()

object CommitteeToAdvancedCommitteeDTOMapper : ObjectMappie<Committee, AdvancedCommitteeDTO>()

object AdvancedCommitteeDTOToCommitteeMapper : ObjectMappie<AdvancedCommitteeDTO, Committee>()

object CommitteeToSimpleCommitteeDTOMapper : ObjectMappie<Committee, SimpleCommitteeDTO>()

object SimpleCommitteeDTOToCommitteeMapper : ObjectMappie<SimpleCommitteeDTO, Committee>()

fun CommitteeMemberDTO.asEntity(): CommitteeMember = CommitteeMemberDTOToCommitteeMemberMapper.map(this)

fun AdvancedCommitteeDTO.asEntity(existing: Committee? = null): Committee {
    val mapped = AdvancedCommitteeDTOToCommitteeMapper.map(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun SimpleCommitteeDTO.asEntity(): Committee = SimpleCommitteeDTOToCommitteeMapper.map(this)

fun CommitteeMember.asDto(): CommitteeMemberDTO = CommitteeMemberToCommitteeMemberDTOMapper.map(this)

fun Committee.asAdvancedDto(): AdvancedCommitteeDTO = CommitteeToAdvancedCommitteeDTOMapper.map(this)

fun Committee.asSimpleDto(): SimpleCommitteeDTO = CommitteeToSimpleCommitteeDTOMapper.map(this)
