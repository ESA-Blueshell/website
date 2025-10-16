package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.model.Address;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class AddressMapper extends BaseMapper<Address, AddressDTO> {

    @Mapping(target = "id")
    @Mapping(target = "country")
    @Mapping(target = "city")
    @Mapping(target = "street")
    @Mapping(target = "houseNumber")
    @Mapping(target = "zipCode")
    @Mapping(target = "createdAt")
    public abstract AddressDTO toDTO(Address address);

    @Mapping(target = "id")
    @Mapping(target = "country")
    @Mapping(target = "city")
    @Mapping(target = "street")
    @Mapping(target = "houseNumber")
    @Mapping(target = "zipCode")
    @Mapping(target = "createdAt")
    public abstract Address fromDTO(AddressDTO dto, @MappingTarget Address address);
}