package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.io.Serial

@Schema(name = "JwtRequest")
data class JwtRequest(
    @field:NotBlank(message = "Username required.")
    var username: String,
    @field:NotBlank(message = "Password required.")
    var password: String
) {
    companion object {
        @Serial
        private const val serialVersionUID = 5926468583005150707L
    }
}
