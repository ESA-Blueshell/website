package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.response.AddressResponse
import tech.mappie.api.ObjectMappie

object AddressToAddressResponseMapper : ObjectMappie<Address, AddressResponse>() {
    override fun map(from: Address) = mapping {
        AddressResponse::id fromValue from.id!!
    }
}

fun Address.asResponse(): AddressResponse = AddressToAddressResponseMapper.map(this)
