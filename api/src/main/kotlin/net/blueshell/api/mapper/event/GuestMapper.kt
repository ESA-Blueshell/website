package net.blueshell.api.mapper.event

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.common.util.Util
import net.blueshell.api.dto.GuestDTO
import net.blueshell.api.model.event.Guest
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
    abstract fun fromDTO(dto: GuestDTO, @MappingTarget guest: Guest)

    @AfterMapping
    protected fun afterFromDTO(dto: GuestDTO, @MappingTarget guest: Guest) {
        if (guest.accessToken == null) {
            guest.accessToken = Util.getRandomCapitalString(30)
        }
    }

    @Mapping(target = "name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @Mapping(target = "accessToken")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(guest: Guest): GuestDTO
}
