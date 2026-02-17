package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import net.blueshell.api.domain.user.web.validation.ValidCountryCode
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "UpdateAddressRequest")
data class UpdateAddressRequest(
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
    var zipCode: String? = null,

    var version: Long? = null
)
