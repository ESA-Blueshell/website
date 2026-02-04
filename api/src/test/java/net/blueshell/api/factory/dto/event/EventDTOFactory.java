package net.blueshell.api.factory.dto.event;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for EventDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class EventDTOFactory extends BaseDtoFactory<EventDTO> {

    private final EventBannerDTOFactory bannerFactory;
    private final SurveyDTOFactory surveyFactory;

    @Override
    public Class<EventDTO> targetType() {
        return EventDTO.class;
    }

    @Override
    public EventDTO createBasic() {
        var start = now().plusSeconds(3600);
        return new EventDTO(
                nextId(),
                unique("Event"),
                "Test event description",
                "Test Location",
                start,
                start.plusSeconds(3600),
                0.0,
                10.0,
                true,
                false,
                true,
                bannerFactory.createBasic(),
                0L,
                surveyFactory.createBasic()
        );
    }
}
