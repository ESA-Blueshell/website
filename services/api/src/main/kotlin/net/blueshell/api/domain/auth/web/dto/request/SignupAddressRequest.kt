package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// No userId: the account comes from the signup token, so there is no parameter to
// tamper with and no equality check to forget (ADR-024).
@Schema(name = "SignupAddressRequest")
data class SignupAddressRequest(
    @field:NotBlank var country: String? = null,
    @field:NotBlank var city: String? = null,
    @field:NotBlank var street: String? = null,
    @field:NotBlank var houseNumber: String? = null,
    @field:NotBlank var zipCode: String? = null
)
