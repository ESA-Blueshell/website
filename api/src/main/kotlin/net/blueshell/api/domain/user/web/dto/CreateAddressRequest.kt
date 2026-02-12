package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.domain.user.web.validation.ValidCountryCode

@Schema(name = "CreateAddressRequest")
data class CreateAddressRequest(
    @field:NotEmpty
    @field:ValidCountryCode
    var country: String? = null,

    @field:NotEmpty
    var city: String? = null,

    @field:NotEmpty
    var street: String? = null,

    @field:NotEmpty
    var houseNumber: String? = null,

    @field:NotEmpty
    var zipCode: String? = null
)
