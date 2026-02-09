package net.blueshell.api.committee.web.mapper

import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.Committee
import org.springframework.stereotype.Component

@Component
class SimpleCommitteeMapper : BaseMapper<Committee, SimpleCommitteeDTO>() {
    override fun fromDTO(dto: SimpleCommitteeDTO): Committee {
        return Committee().also { committee ->
            committee.name = requireNotNull(dto.name)
            committee.description = requireNotNull(dto.description)
            dto.version?.let { committee.version = it }
        }
    }

    override fun toDTO(committee: Committee): SimpleCommitteeDTO {
        return SimpleCommitteeDTO(
            name = committee.name,
            description = committee.description
        ).also { dto ->
            dto.id = committee.id
            dto.version = committee.version
        }
    }
}

fun Committee.asDTO(mapper: SimpleCommitteeMapper): SimpleCommitteeDTO = mapper.toDTO(this)

fun SimpleCommitteeDTO.asEntity(mapper: SimpleCommitteeMapper): Committee = mapper.fromDTO(this)
