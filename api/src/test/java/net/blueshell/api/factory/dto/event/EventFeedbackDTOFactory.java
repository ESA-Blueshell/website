package net.blueshell.api.factory.dto.event;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.event.EventFeedbackDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for EventFeedbackDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class EventFeedbackDTOFactory extends BaseDtoFactory<EventFeedbackDTO> {

    @Override
    public Class<EventFeedbackDTO> targetType() {
        return EventFeedbackDTO.class;
    }

    @Override
    public EventFeedbackDTO createBasic() {
        EventFeedbackDTO dto = new EventFeedbackDTO();
        dto.setFeedback("Great event!");
        dto.setEventId(nextId());
        return dto;
    }
}
