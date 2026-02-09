package net.blueshell.api.feature.event.mapper

import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import net.blueshell.api.feature.event.dto.GuestDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.feature.event.model.Guest
import org.mapstruct.*

@Mapper(componentModel = "spring")
abstract class GuestMapper : BaseMapper<Guest, GuestDTO>() {
    @Mapping(target = "name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: GuestDTO, @MappingTarget entity: Guest)

    @AfterMapping
    protected fun afterFromDTO(dto: GuestDTO, @MappingTarget entity: Guest) {
        if (entity.accessToken != null) return

        entity.accessToken = randomCapitalString(30)
    }

    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "accessToken")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(entity: Guest): GuestDTO
}
