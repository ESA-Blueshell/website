package net.blueshell.api.domain.file.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Picture")
data class PictureDTO(
    var name: String? = null,
    var url: String? = null,

    @field:NotNull
    var uploaderId: Long? = null,

    @field:NotNull
    var eventId: Long? = null
) : AuditedAutoIdDTO()
