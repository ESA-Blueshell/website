package net.blueshell.api.domain.sponsor.web.mapping.response

import net.blueshell.api.domain.sponsor.command.result.SponsorResult
import net.blueshell.api.domain.sponsor.web.dto.response.SponsorResponse
import tech.mappie.api.ObjectMappie

object SponsorResultToSponsorResponseMapper : ObjectMappie<SponsorResult, SponsorResponse>()

fun SponsorResult.asResponse(): SponsorResponse = SponsorResultToSponsorResponseMapper.map(this)
