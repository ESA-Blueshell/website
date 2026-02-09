package net.blueshell.api.committee.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.Committee
import org.springframework.stereotype.Component

@Konverter
interface AdvancedCommitteeKonverter {
    @Konvert(mappings = [Mapping(target = "members", ignore = true)])
    fun toDTO(committee: Committee): AdvancedCommitteeDTO

    @Konvert(mappings = [Mapping(target = "members", ignore = true)])
    fun fromDTO(dto: AdvancedCommitteeDTO): Committee
}

@Component
class AdvancedCommitteeMapper(
    private val committeeMemberMapper: CommitteeMemberMapper
) : BaseMapper<Committee, AdvancedCommitteeDTO>() {
    private val konverter = konverter<AdvancedCommitteeKonverter>()

    override fun fromDTO(dto: AdvancedCommitteeDTO): Committee = fromDTO(dto, Committee())

    fun fromDTO(dto: AdvancedCommitteeDTO, committee: Committee): Committee {
        val mapped = konverter.fromDTO(dto)
        committee.name = mapped.name
        committee.description = mapped.description
        committee.members = dto.members.map { committeeMemberMapper.fromDTO(it) }
        committee.version = dto.version
        return committee
    }

    override fun toDTO(entity: Committee): AdvancedCommitteeDTO {
        val dto = konverter.toDTO(entity)
        dto.members = entity.members.map { committeeMemberMapper.toDTO(it) }.toMutableList()
        return dto
    }
}

fun Committee.asDTO(mapper: AdvancedCommitteeMapper): AdvancedCommitteeDTO = mapper.toDTO(this)

fun AdvancedCommitteeDTO.asEntity(mapper: AdvancedCommitteeMapper): Committee = mapper.fromDTO(this)
