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
        EventDTO dto = new EventDTO();
        dto.setCommitteeId(nextId());
        dto.setTitle(unique("Event"));
        dto.setDescription("Test event description");
        dto.setLocation("Test Location");
        dto.setStartTime(now().plusSeconds(3600));
        dto.setEndTime(dto.getStartTime().plusSeconds(3600));
        dto.setMemberPrice(0.0);
        dto.setPublicPrice(10.0);
        dto.setApproved(true);
        dto.setMembersOnly(false);
        dto.setSignUp(true);
        dto.setBanner(bannerFactory.createBasic());
        dto.setSignUpForm(surveyFactory.createBasic());
        dto.setSignUpCount(0L);
        return dto;
    }
}
