package net.blueshell.api.domain.sponsor.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "SponsorResponse")
data class SponsorResponse(
    var name: String? = null,
    var description: String? = null
) : AuditedAutoIdDTO()
