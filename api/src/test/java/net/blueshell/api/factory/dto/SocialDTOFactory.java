package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.SocialDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for SocialDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class SocialDTOFactory extends BaseDtoFactory<SocialDTO> {

    @Override
    public Class<SocialDTO> targetType() {
        return SocialDTO.class;
    }

    @Override
    public SocialDTO createBasic() {
        SocialDTO dto = new SocialDTO();
        dto.setTitle("Hello world");
        dto.setText("Body");
        dto.setUrl("https://example.com/" + nextId());
        dto.setPlatforms(new PlatformType[]{PlatformType.FACEBOOK});
        return dto;
    }
}
