package net.blueshell.api.domain.sponsor.web.mapping

import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.sponsor.web.dto.response.SponsorResponse
import tech.mappie.api.ObjectMappie

object SponsorToSponsorResponseMapper : ObjectMappie<Sponsor, SponsorResponse>()

fun Sponsor.asResponse(): SponsorResponse = SponsorToSponsorResponseMapper.map(this)
