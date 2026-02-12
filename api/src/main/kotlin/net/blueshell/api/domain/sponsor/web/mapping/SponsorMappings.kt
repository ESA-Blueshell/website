package net.blueshell.api.domain.sponsor.web.mapping

import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.sponsor.web.dto.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.SponsorResponse
import net.blueshell.api.domain.sponsor.web.dto.UpdateSponsorRequest
import tech.mappie.api.ObjectMappie

object SponsorToSponsorResponseMapper : ObjectMappie<Sponsor, SponsorResponse>()

fun CreateSponsorRequest.asEntity(sponsor: Sponsor = Sponsor()): Sponsor {
    sponsor.name = name!!
    sponsor.description = description!!
    return sponsor
}

fun UpdateSponsorRequest.asEntity(sponsor: Sponsor = Sponsor()): Sponsor {
    sponsor.name = name!!
    sponsor.description = description!!
    version?.let { sponsor.version = it }
    return sponsor
}

fun Sponsor.asResponse(): SponsorResponse = SponsorToSponsorResponseMapper.map(this)
