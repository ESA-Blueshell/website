package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.dto.AuditedAutoIdDTO

@Schema(name = "EventFeedback")
data class EventFeedbackDTO(
    var feedback: String? = null,
    var eventId: Long = 0
) : AuditedAutoIdDTO()
