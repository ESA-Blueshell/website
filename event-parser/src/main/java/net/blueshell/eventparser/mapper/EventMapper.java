package net.blueshell.eventparser.mapper;

import net.blueshell.common.dto.event.EventDTO;
import net.blueshell.common.dto.SocialDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class EventMapper {

    @Mapping(source = "description", target = "text")
    @Mapping(target = "url", ignore = true)
    public abstract SocialDTO toSocialDto(EventDTO dto);
}
