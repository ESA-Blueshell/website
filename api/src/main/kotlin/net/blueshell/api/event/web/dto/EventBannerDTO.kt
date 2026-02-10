package net.blueshell.api.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.shared.dto.AuditedSoftDeleteDTO

@Schema(name = "EventBanner")
data class EventBannerDTO(
    @field:NotNull
    var file: FileDTO
) : AuditedSoftDeleteDTO()
