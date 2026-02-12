package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.AddressResponse
import net.blueshell.api.domain.user.web.dto.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.UpdateAddressRequest
import tech.mappie.api.ObjectMappie

object AddressToAddressResponseMapper : ObjectMappie<Address, AddressResponse>()

fun CreateAddressRequest.asEntity(address: Address = Address()): Address {
    address.country = country
    address.city = city
    address.street = street
    address.houseNumber = houseNumber
    address.zipCode = zipCode
    return address
}

fun UpdateAddressRequest.asEntity(address: Address = Address()): Address {
    address.country = country
    address.city = city
    address.street = street
    address.houseNumber = houseNumber
    address.zipCode = zipCode
    version?.let { address.version = it }
    return address
}

fun Address.asResponse(): AddressResponse = AddressToAddressResponseMapper.map(this)
