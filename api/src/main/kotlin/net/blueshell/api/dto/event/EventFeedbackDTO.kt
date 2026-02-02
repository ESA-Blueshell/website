package net.blueshell.api.dto.event

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "EventFeedback")
class EventFeedbackDTO : BaseDTO() {
    private val id: Long? = null
    private val feedback: String? = null
    private val eventId: Long = 0
}
