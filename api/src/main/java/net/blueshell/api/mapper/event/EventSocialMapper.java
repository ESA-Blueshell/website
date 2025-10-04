package net.blueshell.api.mapper.event;

import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.SocialDTO;
import net.blueshell.api.dto.event.EventDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class EventSocialMapper {

    @Mapping(source = "description", target = "text")
    @BeanMapping(ignoreByDefault = true)
    public abstract SocialDTO toSocialDto(EventDTO dto);

    @AfterMapping
    public void afterToSocialDTO(EventDTO dto, @MappingTarget SocialDTO socialDTO) {
        PlatformType[] platforms = {PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM};
        socialDTO.setPlatforms(platforms);
    }
}
