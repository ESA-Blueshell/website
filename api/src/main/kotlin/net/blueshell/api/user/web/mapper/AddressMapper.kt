package net.blueshell.api.user.web.mapper

import net.blueshell.api.user.web.dto.AddressDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.user.persistence.Address
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class AddressMapper : BaseMapper<Address, AddressDTO>() {
    @Mapping(target = "country")
    @Mapping(target = "city")
    @Mapping(target = "street")
    @Mapping(target = "houseNumber")
    @Mapping(target = "zipCode")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: AddressDTO, @MappingTarget address: Address): Address

    @Mapping(target = "id")
    @Mapping(target = "country")
    @Mapping(target = "city")
    @Mapping(target = "street")
    @Mapping(target = "houseNumber")
    @Mapping(target = "zipCode")
    @Mapping(target = "createdAt")
    @Mapping(target = "version")
    abstract override fun toDTO(address: Address): AddressDTO
}