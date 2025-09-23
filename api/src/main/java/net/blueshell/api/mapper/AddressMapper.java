package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.model.Address;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.User;
import net.blueshell.api.service.AddressService;
import net.blueshell.api.service.ContributionPeriodService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class AddressMapper extends BaseMapper<Address, AddressDTO> {

    @Autowired
    private AddressService addresses;

    @ObjectFactory
    protected Address address(@TargetType Class<Address> type, AddressDTO dto) {
        if (dto.getId() != null) {
            return addresses.findById(dto.getId());
        }
        return new Address();
    }

    @Mapping(target = "createdAt", dateFormat = "yyyy-MM-dd HH:mm:ss")
    public abstract AddressDTO toDTO(Address address);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    public abstract Address fromDTO(AddressDTO dto);
}