package net.blueshell.api.mapper.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.event.EventBannerDTO;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.mapper.survey.SurveyMapper;
import net.blueshell.api.model.event.EventBanner;
import org.mapstruct.*;

@Slf4j
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, uses = {FileMapper.class})
public abstract class EventBannerMapper extends BaseMapper<EventBanner, EventBannerDTO> {
    @Mapping(target = "id")
    @Mapping(target = "fileId")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventBanner fromDTO(EventBannerDTO dto, @MappingTarget EventBanner banner);

    @Mapping(target = "id")
    @Mapping(target = "fileId")
    @Mapping(target = "file")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventBannerDTO toDTO(EventBanner banner);
}
