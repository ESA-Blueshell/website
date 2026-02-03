package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.BaseDTO
@Schema(name = "EventFeedback")
class EventFeedbackDTO : BaseDTO() {
    val feedback: String? = null
    val eventId: Long = 0
}
