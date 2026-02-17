package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.CreateAddressCommand
import net.blueshell.api.domain.user.command.UpdateAddressCommand
import net.blueshell.api.domain.user.web.dto.request.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateAddressRequest

fun CreateAddressRequest.asCommand(): CreateAddressCommand =
    CreateAddressCommand(
        userId = this.userId!!,
        country = this.country!!,
        city = this.city!!,
        street = this.street!!,
        houseNumber = this.houseNumber!!,
        zipCode = this.zipCode!!
    )

fun UpdateAddressRequest.asCommand(id: Long) =
    UpdateAddressCommand(
        id = id,
        country = this.country!!,
        city = this.city!!,
        street = this.street!!,
        houseNumber = this.houseNumber!!,
        zipCode = this.zipCode!!,
        version = this.version!!
    )
