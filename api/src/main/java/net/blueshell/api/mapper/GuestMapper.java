package net.blueshell.api.mapper;

import net.blueshell.api.dto.EventSignUpDTO;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.model.Guest;
import net.blueshell.api.base.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class GuestMapper extends BaseMapper<Guest, GuestDTO> {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventSignUp", ignore = true)
    public abstract Guest fromDTO(EventSignUpDTO dto);

    public abstract GuestDTO toDTO(Guest guest);
}
