package net.blueshell.api.event.mapper

import net.blueshell.api.event.dto.EventBannerDTO
import net.blueshell.api.file.mapper.FileMapper
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.event.model.EventBanner
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
    abstract override fun toDTO(entity: EventBanner): EventBannerDTO
}
