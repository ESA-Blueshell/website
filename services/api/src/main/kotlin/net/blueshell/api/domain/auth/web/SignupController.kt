package net.blueshell.api.domain.auth.web

import org.springframework.web.bind.annotation.RequestHeader
import net.blueshell.api.domain.user.web.dto.response.SignupOutcomeResponse
import net.blueshell.api.domain.user.command.SubmitSignupApplicationCommand
import net.blueshell.api.domain.user.command.SaveSignupAddressCommand
import net.blueshell.api.domain.auth.web.dto.request.SignupApplicationRequest
import net.blueshell.api.domain.auth.web.dto.request.SignupAddressRequest
import net.blueshell.api.shared.model.SignupSession
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.auth.command.IssueSignupSessionCommand
import net.blueshell.api.domain.auth.web.dto.response.SignupSessionResponse
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.mapping.request.asCommand
import net.blueshell.api.shared.command.CommandBus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The one public way to register (ADR-024). Keeping it separate from
 * POST /users lets that endpoint be board-only, and keeps the signup token out of
 * UserDetailResponse, which the board's user list also renders.
 */
@RestController
@Tag(name = "Signup")
@RequestMapping("/signup")
class SignupController(
    private val commandBus: CommandBus
) {

    @PostMapping
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: CreateUserRequest): SignupSessionResponse {
        val user = commandBus.dispatch(request.asCommand(isBoard = false))
        val session = commandBus.dispatch(IssueSignupSessionCommand(user.id!!))
        return SignupSessionResponse(
            userId = session.userId,
            email = session.email,
            signupToken = session.token,
            expiresAt = session.expiresAt,
        )
    }

    @PostMapping("/address")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveAddress(
        @RequestHeader(SIGNUP_TOKEN_HEADER) signupToken: String,
        @Valid @RequestBody request: SignupAddressRequest
    ) {
        commandBus.dispatch(
            SaveSignupAddressCommand(
                signupToken = signupToken,
                country = request.country!!,
                city = request.city!!,
                street = request.street!!,
                houseNumber = request.houseNumber!!,
                zipCode = request.zipCode!!,
            )
        )
    }

    @PostMapping("/apply")
    @PermitAll
    fun apply(
        @RequestHeader(SIGNUP_TOKEN_HEADER) signupToken: String,
        @Valid @RequestBody request: SignupApplicationRequest
    ): SignupOutcomeResponse {
        val outcome = commandBus.dispatch(
            SubmitSignupApplicationCommand(
                signupToken = signupToken,
                conditionsAccepted = request.conditionsAccepted,
            )
        )
        return SignupOutcomeResponse(outcome.emailConfirmed, outcome.membershipStarted)
    }

    companion object {
        const val SIGNUP_TOKEN_HEADER = "X-Signup-Token"
    }
}
