package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.Role

/** Who signed in and what they may do now. Never the token: the cookie carries that (api ADR-030). */
@Schema(name = "LoginResponse")
data class AuthenticationResponse(
    @field:NotNull
    val userId: Long,
    @field:NotBlank
    val username: String,
    /** The roles in force, sorted: a dormant role is not among them. */
    @field:NotNull
    val roles: List<Role>,
    val addressId: Long? = null,
)
