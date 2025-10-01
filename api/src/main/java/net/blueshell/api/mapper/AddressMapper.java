package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.model.Address;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class AddressMapper extends BaseMapper<Address, AddressDTO> {

    @Mapping(target = "createdAt", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @BeanMapping(ignoreByDefault = true)
    public abstract AddressDTO toDTO(Address address);

    @BeanMapping(ignoreByDefault = true)
    public abstract Address fromDTO(AddressDTO dto, @MappingTarget Address address);
}