package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.response.AddressResponse

fun Address.asResponse(): AddressResponse =
    AddressResponse(
        country = this.country,
        city = this.city,
        street = this.street,
        houseNumber = this.houseNumber,
        zipCode = this.zipCode,
        version = this.version,
        id = this.id!!,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
