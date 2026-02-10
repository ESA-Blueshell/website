package net.blueshell.api.user.web.mapping

import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.web.dto.AddressDTO
import tech.mappie.api.ObjectMappie

object AddressToAddressDTOMapper : ObjectMappie<Address, AddressDTO>()

object AddressDTOToAddressMapper : ObjectMappie<AddressDTO, Address>()

fun AddressDTO.asEntity(existing: Address? = null): Address {
    val mapped = AddressDTOToAddressMapper.map(this)
    existing?.id?.let { mapped.assignIdForRef(it) }
    return mapped
}

fun Address.asDto(): AddressDTO = AddressToAddressDTOMapper.map(this)
