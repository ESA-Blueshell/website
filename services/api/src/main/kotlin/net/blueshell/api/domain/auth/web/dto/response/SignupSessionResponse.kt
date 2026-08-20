package net.blueshell.api.domain.auth.web.dto.response

import net.blueshell.api.shared.model.SignupSession
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "SignupSessionResponse")
data class SignupSessionResponse(
    val userId: Long,
    val email: String,
    // Presented in the X-Signup-Token header on the /signup/* endpoints.
    val signupToken: String,
    val expiresAt: Instant
)
