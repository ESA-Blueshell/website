package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.address.ValidCountryCode
@Schema(name = "Address")
class AddressDTO : BaseDTO() {
    @NotEmpty
    @ValidCountryCode
    val country: @NotEmpty String? = null

    @NotEmpty
    val city: @NotEmpty String? = null

    @NotEmpty
    val street: @NotEmpty String? = null

    @NotEmpty
    val houseNumber: @NotEmpty String? = null

    @NotEmpty
    val zipCode: @NotEmpty String? = null
}