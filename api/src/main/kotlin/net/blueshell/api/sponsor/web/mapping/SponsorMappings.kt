package net.blueshell.api.sponsor.web.mapping

import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.web.dto.SponsorDTO
import tech.mappie.api.ObjectMappie

object SponsorToSponsorDTOMapper : ObjectMappie<Sponsor, SponsorDTO>()

object SponsorDTOToSponsorMapper : ObjectMappie<SponsorDTO, Sponsor>()

fun SponsorDTO.asEntity(existing: Sponsor? = null): Sponsor {
    val mapped = SponsorDTOToSponsorMapper.map(this)
    existing?.let { current ->
        mapped.assignIdForRef(current.id!!)
        mapped.picture = current.picture
    }
    return mapped
}

fun Sponsor.asDto(): SponsorDTO = SponsorToSponsorDTOMapper.map(this)
