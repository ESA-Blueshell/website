package net.blueshell.api.mapper;

import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.dto.SocialDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class EventSocialMapper {

    @Mapping(source = "description", target = "text")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "platforms", ignore = true)
    public abstract SocialDTO toSocialDto(EventDTO dto);


    @AfterMapping
    public void afterToSocialDTO(EventDTO dto, @MappingTarget SocialDTO socialDTO) {
        PlatformType[] platforms = {PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.INSTAGRAM};
        socialDTO.setPlatforms(platforms);
    }
}
