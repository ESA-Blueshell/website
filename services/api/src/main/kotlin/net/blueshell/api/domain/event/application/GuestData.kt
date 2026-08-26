package net.blueshell.api.domain.event.application

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * Guest information for event sign-ups.
 * Command-layer data structure (not a web DTO).
 */
data class GuestData(
    @field:NotBlank(message = "Guest name is required")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Discord username is required")
    val discord: String,

    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,

    val accessToken: String? = null,
    val version: Long? = null
)
