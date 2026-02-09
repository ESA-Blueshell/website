package net.blueshell.api.event.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.file.dto.FileDTO
import net.blueshell.api.shared.dto.AuditedSoftDeleteDTO

@Schema(name = "EventBanner")
data class EventBannerDTO(
    @field:NotNull
    var file: FileDTO? = null
) : AuditedSoftDeleteDTO()
