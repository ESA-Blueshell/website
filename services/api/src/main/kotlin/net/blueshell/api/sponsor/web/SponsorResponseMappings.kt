package net.blueshell.api.sponsor.web

import net.blueshell.api.sponsor.domain.SponsorResult

fun SponsorResult.asResponse(): SponsorResponse =
    SponsorResponse(
        id = this.id,
        name = this.name,
        description = this.description,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
