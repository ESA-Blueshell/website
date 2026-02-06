package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.dto.FileDTO
import net.blueshell.api.dto.base.AuditedSoftDeleteDTO

@Schema(name = "EventBanner")
data class EventBannerDTO(
    @field:NotNull
    var file: FileDTO? = null
) : AuditedSoftDeleteDTO()
