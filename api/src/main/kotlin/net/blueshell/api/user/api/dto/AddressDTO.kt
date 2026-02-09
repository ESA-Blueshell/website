package net.blueshell.api.user.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "Address")
data class AddressDTO(
    @field:NotEmpty
    @field:net.blueshell.api.user.api.validation.ValidCountryCode
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
