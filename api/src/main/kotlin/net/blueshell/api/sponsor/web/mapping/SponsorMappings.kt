package net.blueshell.api.sponsor.web.mapping

import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.web.dto.SponsorDTO
import tech.mappie.api.ObjectMappie

object SponsorToSponsorDTOMapper : ObjectMappie<Sponsor, SponsorDTO>()

fun SponsorDTO.asEntity(sponsor: Sponsor = Sponsor()): Sponsor {
    sponsor.name = name!!
    sponsor.description = description!!
    sponsor.version = version!!
    return sponsor
}

fun Sponsor.asDto(): SponsorDTO = SponsorToSponsorDTOMapper.map(this)
