package net.blueshell.api.sponsor.web.mapper

import net.blueshell.api.sponsor.web.dto.SponsorDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.sponsor.persistence.Sponsor
import org.springframework.stereotype.Component

@Component
class SponsorMapper : BaseMapper<Sponsor, SponsorDTO>() {
    override fun fromDTO(dto: SponsorDTO): Sponsor = fromDTO(dto, Sponsor())

    fun fromDTO(dto: SponsorDTO, sponsor: Sponsor): Sponsor {
        sponsor.name = requireNotNull(dto.name)
        sponsor.description = requireNotNull(dto.description)
        dto.version?.let { sponsor.version = it }
        return sponsor
    }

    override fun toDTO(sponsor: Sponsor): SponsorDTO {
        return SponsorDTO(
            name = sponsor.name,
            description = sponsor.description
        ).also { dto ->
            dto.id = sponsor.id
            dto.version = sponsor.version
        }
    }
}

fun Sponsor.asDTO(mapper: SponsorMapper): SponsorDTO = mapper.toDTO(this)

fun SponsorDTO.asEntity(mapper: SponsorMapper): Sponsor = mapper.fromDTO(this)
