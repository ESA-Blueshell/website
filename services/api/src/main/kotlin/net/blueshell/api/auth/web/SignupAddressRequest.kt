package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// No userId: the account comes from the signup token, so there is no parameter to
// tamper with and no equality check to forget (ADR-024).
@Schema(name = "SignupAddressRequest")
data class SignupAddressRequest(
    @field:NotBlank var country: String,
    @field:NotBlank var city: String,
    @field:NotBlank var street: String,
    @field:NotBlank var houseNumber: String,
    @field:NotBlank var zipCode: String
)
