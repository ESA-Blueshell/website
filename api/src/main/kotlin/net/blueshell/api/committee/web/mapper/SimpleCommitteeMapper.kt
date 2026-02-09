package net.blueshell.api.committee.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.Committee
import org.springframework.stereotype.Component

@Konverter
interface SimpleCommitteeKonverter {
    fun toDTO(committee: Committee): SimpleCommitteeDTO

    fun fromDTO(dto: SimpleCommitteeDTO): Committee
}

@Component
class SimpleCommitteeMapper : BaseMapper<Committee, SimpleCommitteeDTO>() {
    private val konverter = konverter<SimpleCommitteeKonverter>()

    override fun fromDTO(dto: SimpleCommitteeDTO): Committee {
        return konverter.fromDTO(dto)
    }

    override fun toDTO(entity: Committee): SimpleCommitteeDTO = konverter.toDTO(entity)
}

fun Committee.asDTO(mapper: SimpleCommitteeMapper): SimpleCommitteeDTO = mapper.toDTO(this)

fun SimpleCommitteeDTO.asEntity(mapper: SimpleCommitteeMapper): Committee = mapper.fromDTO(this)
