package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.model.event.Guest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class GuestMapper extends BaseMapper<Guest, GuestDTO> {
    @Mapping(target = "name", source = "dto.fullName")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @BeanMapping(ignoreByDefault = true)
    public abstract Guest fromDTO(GuestDTO dto, @MappingTarget Guest guest);

    @Mapping(target = "fullName", source = "guest.name")
    @Mapping(target = "discord")
    @Mapping(target = "email")
    @Mapping(target = "phoneNumber")
    @BeanMapping(ignoreByDefault = true)
    public abstract GuestDTO toDTO(Guest guest);
}
