package net.blueshell.api.mapper.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.event.EventBannerDTO;
import net.blueshell.api.mapper.FileMapper;
import net.blueshell.api.model.event.EventBanner;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Slf4j
@Mapper(componentModel = "spring", uses = {FileMapper.class})
public abstract class EventBannerMapper extends BaseMapper<EventBanner, EventBannerDTO> {
    @Mapping(target = "id")
    @Mapping(target = "file")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventBanner fromDTO(EventBannerDTO dto, @MappingTarget EventBanner banner);

    @Mapping(target = "id")
    @Mapping(target = "file")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract EventBannerDTO toDTO(EventBanner banner);
}
