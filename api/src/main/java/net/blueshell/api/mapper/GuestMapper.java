package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.model.event.Guest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class GuestMapper extends BaseMapper<Guest, GuestDTO> {

    @BeanMapping(ignoreByDefault = true)
    public abstract Guest fromDTO(EventSignUpDTO dto, @MappingTarget Guest guest);

    @BeanMapping(ignoreByDefault = true)
    public abstract GuestDTO toDTO(Guest guest);
}
