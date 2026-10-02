package net.blueshell.api.user.web

import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.persistence.Address

/** The address with [fields] opened, or with none where it is not opened: in a list, or with the key away. */
fun Address.asResponse(fields: AddressFields?): AddressResponse =
    AddressResponse(
        country = fields?.country,
        city = fields?.city,
        street = fields?.street,
        houseNumber = fields?.houseNumber,
        zipCode = fields?.zipCode,
        version = this.version,
        id = this.id!!,
        userId = this.user.id,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        opened = fields != null,
    )
