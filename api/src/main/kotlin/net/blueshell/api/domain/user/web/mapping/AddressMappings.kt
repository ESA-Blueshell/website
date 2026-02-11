package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.AddressDTO
import tech.mappie.api.ObjectMappie

object AddressToAddressDTOMapper : ObjectMappie<Address, AddressDTO>()

fun AddressDTO.asEntity(address: Address = Address()): Address {
    address.country = country
    address.city = city
    address.street = street
    address.houseNumber = houseNumber
    address.zipCode = zipCode
    version?.let { address.version = it }
    return address
}

fun Address.asDto(): AddressDTO = AddressToAddressDTOMapper.map(this)
