package net.blueshell.api.committee.web.mapper

import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.Committee
import org.springframework.stereotype.Component

@Component
class AdvancedCommitteeMapper(
    private val committeeMemberMapper: CommitteeMemberMapper
) : BaseMapper<Committee, AdvancedCommitteeDTO>() {
    override fun fromDTO(dto: AdvancedCommitteeDTO): Committee = fromDTO(dto, Committee())

    fun fromDTO(dto: AdvancedCommitteeDTO, committee: Committee): Committee {
        committee.name = requireNotNull(dto.name)
        committee.description = requireNotNull(dto.description)
        committee.members = dto.members.map { committeeMemberMapper.fromDTO(it) }
        dto.version?.let { committee.version = it }
        return committee
    }

    override fun toDTO(committee: Committee): AdvancedCommitteeDTO {
        return AdvancedCommitteeDTO(
            name = committee.name,
            description = committee.description,
            members = committee.members.map { committeeMemberMapper.toDTO(it) }.toMutableList()
        ).also { dto ->
            dto.id = committee.id
            dto.version = committee.version
        }
    }
}

fun Committee.asDTO(mapper: AdvancedCommitteeMapper): AdvancedCommitteeDTO = mapper.toDTO(this)

fun AdvancedCommitteeDTO.asEntity(mapper: AdvancedCommitteeMapper): Committee = mapper.fromDTO(this)
