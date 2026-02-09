package net.blueshell.api.feature.user.mapper

import net.blueshell.api.feature.user.dto.AddressDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.feature.user.model.Address
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