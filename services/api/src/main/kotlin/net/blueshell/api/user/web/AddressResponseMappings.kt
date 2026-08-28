package net.blueshell.api.user.web

import net.blueshell.api.user.persistence.Address

fun Address.asResponse(): AddressResponse =
    AddressResponse(
        country = this.country,
        city = this.city,
        street = this.street,
        houseNumber = this.houseNumber,
        zipCode = this.zipCode,
        version = this.version,
        id = this.id!!,
        userId = this.user.id,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
