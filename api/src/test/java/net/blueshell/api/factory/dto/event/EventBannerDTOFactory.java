package net.blueshell.api.factory.dto.event;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.event.EventBannerDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import net.blueshell.api.factory.dto.FileDTOFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for EventBannerDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class EventBannerDTOFactory extends BaseDtoFactory<EventBannerDTO> {

    private final FileDTOFactory fileFactory;

    @Override
    public Class<EventBannerDTO> targetType() {
        return EventBannerDTO.class;
    }

    @Override
    public EventBannerDTO createBasic() {
        EventBannerDTO dto = new EventBannerDTO();
        dto.setFile(fileFactory.createBasic());
        return dto;
    }
}
