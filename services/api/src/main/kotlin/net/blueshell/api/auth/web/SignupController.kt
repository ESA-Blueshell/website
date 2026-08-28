package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.SignupUseCases
import net.blueshell.api.domain.user.application.UserUseCases
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestHeader
import net.blueshell.api.domain.user.web.dto.response.SignupOutcomeResponse
import net.blueshell.api.shared.model.SignupSession
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.application.SignupDetailsData
import net.blueshell.api.domain.user.web.mapping.request.asData
import net.blueshell.api.domain.user.web.mapping.request.asCommandData
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
    private val userUseCases: UserUseCases,
    private val signupUseCases: SignupUseCases,
) {

    @PostMapping
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: CreateUserRequest): SignupSessionResponse {
        val user = userUseCases.create(request.asData(), isBoard = false)
        val session = signupUseCases.issueSession(user.id!!)
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
        signupUseCases.saveAddress(
            signupToken = signupToken,
            country = request.country,
            city = request.city,
            street = request.street,
            houseNumber = request.houseNumber,
            zipCode = request.zipCode,
        )
    }

    @PostMapping("/apply")
    @PermitAll
    fun apply(
        @RequestHeader(SIGNUP_TOKEN_HEADER) signupToken: String,
        @Valid @RequestBody request: SignupApplicationRequest
    ): SignupOutcomeResponse {
        val outcome = signupUseCases.submitApplication(signupToken)
        return SignupOutcomeResponse(outcome.emailConfirmed, outcome.membershipStarted)
    }

    @PatchMapping("/details")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateDetails(
        @RequestHeader(SIGNUP_TOKEN_HEADER) signupToken: String,
        @Valid @RequestBody request: SignupDetailsRequest
    ) {
        signupUseCases.updateDetails(
            signupToken = signupToken,
            data = SignupDetailsData(
                username = request.username,
                initials = request.initials,
                firstName = request.firstName,
                prefix = request.prefix,
                lastName = request.lastName,
                discord = request.discord,
                phoneNumber = request.phoneNumber,
                newsletter = request.newsletter,
                photoConsent = request.photoConsent == true,
                memberProfile = request.memberProfile?.asCommandData(),
            ),
        )
    }

    @PatchMapping("/email")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun correctEmail(
        @RequestHeader(SIGNUP_TOKEN_HEADER) signupToken: String,
        @Valid @RequestBody request: SignupEmailRequest
    ) {
        signupUseCases.correctEmail(signupToken = signupToken, email = request.email!!)
    }

    companion object {
        const val SIGNUP_TOKEN_HEADER = "X-Signup-Token"
    }
}
