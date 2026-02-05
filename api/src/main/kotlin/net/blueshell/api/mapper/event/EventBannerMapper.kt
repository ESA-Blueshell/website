package net.blueshell.api.mapper.event

import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.dto.event.EventBannerDTO
import net.blueshell.api.mapper.FileMapper
import net.blueshell.api.model.event.EventBanner
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring", uses = [FileMapper::class])
abstract class EventBannerMapper : BaseMapper<EventBanner, EventBannerDTO>() {
    @Mapping(target = "file")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: EventBannerDTO, @MappingTarget banner: EventBanner): EventBanner

    @Mapping(target = "file")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(banner: EventBanner): EventBannerDTO
}
