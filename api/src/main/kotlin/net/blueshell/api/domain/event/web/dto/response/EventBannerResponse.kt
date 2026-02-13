package net.blueshell.api.domain.event.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedSoftDeleteDTO

@Schema(name = "EventBannerResponse")
data class EventBannerResponse(
    @field:NotNull
    var fileId: Long? = null
) : AuditedSoftDeleteDTO()
