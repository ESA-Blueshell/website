package net.blueshell.api.domain.sponsor.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "SponsorResponse")
data class SponsorResponse(
    var id: Long,
    var name: String,
    var description: String,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
