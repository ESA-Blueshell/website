package net.blueshell.api.mapper.activation;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.dto.request.PasswordResetRequest;
import net.blueshell.api.model.Address;
import net.blueshell.api.model.User;
import net.blueshell.api.service.AddressService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class PasswordResetRequestMapper extends BaseMapper<User, PasswordResetRequest> {

    @Mapping(target = "createdAt", dateFormat = "yyyy-MM-dd HH:mm:ss")
    public abstract AddressDTO toDTO(Address address);

    @BeanMapping(ignoreByDefault = true)
    public abstract Address fromDTO(AddressDTO dto, @MappingTarget Address address);
}