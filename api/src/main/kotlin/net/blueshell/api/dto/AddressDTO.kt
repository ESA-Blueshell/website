package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.dto.base.AuditedAutoIdDTO
import net.blueshell.api.validation.address.ValidCountryCode

@Schema(name = "Address")
data class AddressDTO(
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
) : AuditedAutoIdDTO()
