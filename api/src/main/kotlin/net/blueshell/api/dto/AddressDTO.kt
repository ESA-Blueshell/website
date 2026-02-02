package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.address.ValidCountryCode

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Data
@Schema(name = "Address")
class AddressDTO : BaseDTO() {
    private val id: Long? = null

    @NotEmpty
    @ValidCountryCode
    private val country: @NotEmpty String? = null

    @NotEmpty
    private val city: @NotEmpty String? = null

    @NotEmpty
    private val street: @NotEmpty String? = null

    @NotEmpty
    private val houseNumber: @NotEmpty String? = null

    @NotEmpty
    private val zipCode: @NotEmpty String? = null
}