package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(name = "CreateAddressRequest")
data class CreateAddressRequest(
    var userId: Long,

    @field:NotEmpty
    @field:ValidCountryCode
    var country: String,

    @field:NotEmpty
    var city: String,

    @field:NotEmpty
    var street: String,

    @field:NotEmpty
    var houseNumber: String,

    @field:NotEmpty
    var zipCode: String
)
