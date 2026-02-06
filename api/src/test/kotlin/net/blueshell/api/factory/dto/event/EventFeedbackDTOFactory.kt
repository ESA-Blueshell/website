package net.blueshell.api.factory.dto.event

import net.blueshell.api.dto.event.EventFeedbackDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for EventFeedbackDTO test instances.
 */
@Component
class EventFeedbackDTOFactory : BaseDtoFactory<EventFeedbackDTO>() {

    override fun targetType(): Class<EventFeedbackDTO> = EventFeedbackDTO::class.java

    override fun createBasic(): EventFeedbackDTO {
        val dto = EventFeedbackDTO()
        dto.feedback = "Great event!"
        dto.eventId = nextId()
        return dto
    }
}
