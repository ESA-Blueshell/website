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
    @field:NotNull
    val roles: List<Role>,
    val addressId: Long?,
    @field:NotNull
    val twoFactor: TwoFactorStandingResponse,
)

@Schema(name = "TwoFactorStanding")
data class TwoFactorStandingResponse(
    val on: Boolean,
    val backupCodesLeft: Int,
    val required: Boolean,
    val offered: Boolean,
    val mayTurnOff: Boolean,
)

@Schema(enumAsRef = true)
enum class SignInStatus { SIGNED_IN, TWO_FACTOR_REQUIRED }

/** What `POST /auth` answers: a sign-in, or the code step to come. */
@Schema(name = "SignInAnswer")
data class SignInAnswer(
    val status: SignInStatus,
    val login: AuthenticationResponse? = null,
)

@Schema(name = "TwoFactorCodeRequest")
data class TwoFactorCodeRequest(
    @field:NotBlank
    val code: String,
    val trustThisBrowser: Boolean = false,
)

@Schema(name = "ReenrolRequest")
data class ReenrolRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
)
