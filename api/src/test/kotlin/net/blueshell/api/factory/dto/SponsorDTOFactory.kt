package net.blueshell.api.factory.dto

import net.blueshell.api.sponsor.dto.SponsorDTO
import org.springframework.stereotype.Component

/**
 * Factory for SponsorDTO test instances.
 */
@Component
class SponsorDTOFactory : BaseDtoFactory<SponsorDTO>() {

    override fun targetType(): Class<SponsorDTO> = SponsorDTO::class.java

    override fun createBasic(): SponsorDTO {
        val dto = SponsorDTO()
        dto.name = unique("Sponsor")
        dto.description = "Test sponsor description"
        return dto
    }
}
