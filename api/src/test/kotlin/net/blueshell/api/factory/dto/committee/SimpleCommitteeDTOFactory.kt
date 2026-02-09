package net.blueshell.api.factory.dto.committee

import net.blueshell.api.committee.api.dto.SimpleCommitteeDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for SimpleCommitteeDTO test instances.
 */
@Component
class SimpleCommitteeDTOFactory : BaseDtoFactory<SimpleCommitteeDTO>() {

    override fun targetType(): Class<SimpleCommitteeDTO> = SimpleCommitteeDTO::class.java

    override fun createBasic(): SimpleCommitteeDTO {
        val dto = SimpleCommitteeDTO()
        dto.name = unique("Committee")
        dto.description = "Test committee description"
        return dto
    }

    fun createWithName(name: String): SimpleCommitteeDTO {
        val dto = createBasic()
        dto.name = name
        return dto
    }

    fun createWithDetails(name: String, description: String): SimpleCommitteeDTO {
        val dto = createBasic()
        dto.name = name
        dto.description = description
        return dto
    }
}
