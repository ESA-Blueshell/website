package net.blueshell.api.domain.sponsor.web.mapping.response

import net.blueshell.api.domain.sponsor.command.result.SponsorResult
import net.blueshell.api.domain.sponsor.web.dto.response.SponsorResponse

fun SponsorResult.asResponse(): SponsorResponse =
    SponsorResponse(
        id = this.id,
        name = this.name,
        description = this.description,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
