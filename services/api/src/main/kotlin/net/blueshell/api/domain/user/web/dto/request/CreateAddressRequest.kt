package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.domain.user.web.validation.ValidCountryCode

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
