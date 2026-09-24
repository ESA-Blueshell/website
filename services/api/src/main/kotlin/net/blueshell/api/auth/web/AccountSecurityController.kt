package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.auth.domain.AccountSecurity
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.security.SignInContext
import org.springdoc.core.annotations.ParameterObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** The security page's endpoints, the admin's help and the links a security email carries. */
@RestController
@Tag(name = "Account Security")
// One endpoint per thing the security page and the user manager do.
@Suppress("TooManyFunctions")
class AccountSecurityController(
    private val accountSecurity: AccountSecurity,
    private val twoFactor: TwoFactor,
    @param:Value($$"${app.security.contact-email:board@blueshell.utwente.nl}") private val contactEmail: String,
) {
    @PostMapping("/auth/step-up")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun stepUp(
        @RequestBody body: StepUpRequest,
    ) = accountSecurity.stepUp(me(), signInId(), body.code, body.password)

    @GetMapping("/users/me/two-factor")
    @PreAuthorize("isAuthenticated()")
    fun twoFactorStanding(): TwoFactorStandingResponse = twoFactor.standing(me()).asResponse()

    @PostMapping("/users/me/two-factor/setup")
    @PreAuthorize("isAuthenticated()")
    fun setUpTwoFactor(
        @Valid @RequestBody body: PasswordRequest,
    ): TwoFactorSetupResponse = accountSecurity.setUpTwoFactor(me(), body.password).let { TwoFactorSetupResponse(it.otpauthUri, it.key) }

    @PostMapping("/users/me/two-factor/confirm")
    @PreAuthorize("isAuthenticated()")
    fun confirmTwoFactor(
        @Valid @RequestBody body: CodeRequest,
    ): BackupCodesResponse = BackupCodesResponse(twoFactor.confirm(me(), body.code))

    @PostMapping("/users/me/two-factor/saved")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun twoFactorSaved() = twoFactor.saved(me(), signInId())

    @DeleteMapping("/users/me/two-factor")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun turnOffTwoFactor() = accountSecurity.turnOffTwoFactor(me())

    @PostMapping("/users/me/two-factor/backup-codes")
    @PreAuthorize("isAuthenticated()")
    fun regenerateBackupCodes(): BackupCodesResponse = BackupCodesResponse(accountSecurity.regenerateBackupCodes(me()))

    @PostMapping("/users/me/two-factor/offer")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun answerTwoFactorOffer() = twoFactor.answerOffer(me())

    @PutMapping("/users/me/password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @Valid @RequestBody body: PasswordChangeRequest,
    ) = accountSecurity.changePassword(me(), signInId(), body.currentPassword, body.newPassword)

    @PostMapping("/users/me/email")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun requestEmailChange(
        @Valid @RequestBody body: EmailChangeRequest,
    ) = accountSecurity.requestEmailChange(me(), body.email)

    @GetMapping("/users/me/sign-ins")
    @PreAuthorize("isAuthenticated()")
    fun signIns(): List<SignInResponse> {
        val current = signInId()
        return accountSecurity.signInsOf(me()).map { it.asResponse(current) }
    }

    @DeleteMapping("/users/me/sign-ins/{signInId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun endSignIn(
        @PathVariable signInId: String,
    ) {
        if (!accountSecurity.endSignIn(me(), signInId)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    /** Signs out everywhere, this browser included. */
    @DeleteMapping("/users/me/sign-ins")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOutEverywhere() = accountSecurity.signOutEverywhere(me())

    @GetMapping("/users/me/trusted-browsers")
    @PreAuthorize("isAuthenticated()")
    fun trustedBrowsers(): List<TrustedBrowserResponse> = accountSecurity.trustedBrowsersOf(me()).map { it.asResponse() }

    @DeleteMapping("/users/me/trusted-browsers/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgetTrustedBrowser(
        @PathVariable id: Long,
    ) {
        if (!accountSecurity.forgetTrustedBrowser(me(), id)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    @DeleteMapping("/users/me/trusted-browsers")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgetTrustedBrowsers() = accountSecurity.forgetTrustedBrowsers(me())

    @GetMapping("/users/me/security-events")
    @PreAuthorize("isAuthenticated()")
    fun mySecurityEvents(
        @ParameterObject @PageableDefault(size = 20) pageable: Pageable,
    ): SecurityEventPageResponse = accountSecurity.eventsOf(me(), pageable).asResponse()

    @GetMapping("/users/{userId}/security-events")
    @PreAuthorize("hasPermission(#userId, 'User', 'security')")
    fun securityEvents(
        @PathVariable userId: Long,
        @ParameterObject @PageableDefault(size = 20) pageable: Pageable,
    ): SecurityEventPageResponse = accountSecurity.eventsOf(userId, pageable).asResponse()

    @GetMapping("/users/{userId}/account-security")
    @PreAuthorize("hasPermission(#userId, 'User', 'security')")
    fun accountStanding(
        @PathVariable userId: Long,
    ): AccountStandingResponse = accountSecurity.standingOf(userId).asResponse()

    @PostMapping("/users/{userId}/two-factor/reset")
    @PreAuthorize("hasPermission(#userId, 'User', 'security')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetTwoFactor(
        @PathVariable userId: Long,
        @Valid @RequestBody body: ReasonRequest,
    ) = accountSecurity.resetTwoFactorFor(me(), userId, body.reason)

    @PostMapping("/users/{userId}/two-factor/reset/resend")
    @PreAuthorize("hasPermission(#userId, 'User', 'security')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resendReenrolmentLink(
        @PathVariable userId: Long,
    ) = accountSecurity.resendReenrolmentLink(me(), userId)

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasPermission(#userId, 'User', 'security')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlock(
        @PathVariable userId: Long,
        @Valid @RequestBody body: UnlockRequest,
    ) = accountSecurity.unlock(me(), userId, body.reason, body.email)

    /** Answers the same whatever the link, so it cannot be used to test links. */
    @PostMapping("/recovery/lock")
    @PermitAll
    fun lock(
        @Valid @RequestBody body: TokenRequest,
    ): LockResponse {
        accountSecurity.lockWithLink(body.token)
        return LockResponse(contactEmail)
    }

    @PostMapping("/recovery/email/confirm")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmEmailChange(
        @Valid @RequestBody body: TokenRequest,
    ) = accountSecurity.confirmEmailChange(body.token)

    private fun me(): Long = SecurityUtils.currentPrincipal()?.id ?: throw AccessDeniedException("Not signed in")

    private fun signInId(): String = SignInContext.current()?.id ?: throw AccessDeniedException("Not signed in")
}
