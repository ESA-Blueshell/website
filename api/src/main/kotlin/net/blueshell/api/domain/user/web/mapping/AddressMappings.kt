package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.AddressResponse
import tech.mappie.api.ObjectMappie

object AddressToAddressResponseMapper : ObjectMappie<Address, AddressResponse>()

fun Address.asResponse(): AddressResponse = AddressToAddressResponseMapper.map(this)
