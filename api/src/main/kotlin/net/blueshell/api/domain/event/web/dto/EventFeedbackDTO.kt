package net.blueshell.api.domain.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "EventFeedback")
data class EventFeedbackDTO(
    @field:NotBlank
    var feedback: String? = null,

    @field:NotNull
    var eventId: Long? = null
) : AuditedAutoIdDTO()
