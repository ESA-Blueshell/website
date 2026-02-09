package net.blueshell.api.committee.web.mapper

import net.blueshell.api.committee.web.dto.CommitteeMemberDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.CommitteeMember
import org.springframework.stereotype.Component

@Component
class CommitteeMemberMapper : BaseMapper<CommitteeMember, CommitteeMemberDTO>() {
    override fun fromDTO(dto: CommitteeMemberDTO): CommitteeMember = fromDTO(dto, CommitteeMember())

    fun fromDTO(dto: CommitteeMemberDTO, member: CommitteeMember): CommitteeMember {
        member.committeeId = requireNotNull(dto.committeeId)
        member.userId = requireNotNull(dto.userId)
        member.role = dto.role
        dto.version?.let { member.version = it }
        return member
    }

    override fun toDTO(member: CommitteeMember): CommitteeMemberDTO {
        return CommitteeMemberDTO(
            userId = member.userId,
            committeeId = member.committeeId,
            role = member.role
        ).also { dto ->
            dto.version = member.version
        }
    }
}

fun CommitteeMember.asDTO(mapper: CommitteeMemberMapper): CommitteeMemberDTO = mapper.toDTO(this)

fun CommitteeMemberDTO.asEntity(mapper: CommitteeMemberMapper): CommitteeMember = mapper.fromDTO(this)
