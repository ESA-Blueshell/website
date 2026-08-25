package net.blueshell.api.domain.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.auth.web.dto.request.MemberActivationRequest
import net.blueshell.api.domain.auth.web.dto.request.PasswordResetRequest
import net.blueshell.api.domain.auth.web.dto.request.UserActivationRequest
import net.blueshell.api.domain.auth.web.mapping.request.asCommand
import net.blueshell.api.domain.auth.web.dto.response.ActivationResponse
import net.blueshell.api.domain.auth.web.dto.response.PendingActivation
import net.blueshell.api.domain.auth.web.dto.response.PendingActivationsResponse
import net.blueshell.api.domain.auth.web.dto.response.RecoveryEmailPreviewResponse
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.domain.telemetry.web.dto.response.RedirectResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Recovery")
@RequestMapping("/recovery")
class RecoveryController(
    private val commandBus: CommandBus
) {
    @PostMapping("/password/reset/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun resetPassword(@PathVariable username: String) {
        commandBus.dispatch(ResetPasswordCommand(username))
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun setPassword(@Valid @RequestBody request: PasswordResetRequest) {
        commandBus.dispatch(request.asCommand())
    }

    @PostMapping("/user/activate")
    @PermitAll
    fun userActivate(@Valid @RequestBody request: UserActivationRequest): ActivationResponse {
        val outcome = commandBus.dispatch(request.asCommand())
        return ActivationResponse(outcome.membershipStarted)
    }

    @PostMapping("/member/activate")
    @PermitAll
    fun memberActivate(@Valid @RequestBody request: MemberActivationRequest): RedirectResponse {
        commandBus.dispatch(request.asCommand())
        return RedirectResponse("/")
    }

    @PostMapping("/user/activate/resend/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun resendUserActivation(@PathVariable username: String) {
        commandBus.dispatch(ResendUserActivationCommand(username))
    }

    /**
     * Resends a recovery email. `purpose` names which one to send; omitted, the outstanding
     * activation decides, which does nothing when none is outstanding.
     */
    @PostMapping("/users/{userId}/resend/recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(#userId, 'User', 'email')")
    fun resendRecoveryEmail(
        @PathVariable userId: Long,
        @RequestParam(required = false) purpose: TokenPurpose?,
    ) {
        // The purpose is client input here, so a value this endpoint cannot send is a bad
        // request rather than a fault; the domain guard behind it stays an invariant.
        if (purpose != null && !purpose.isActivation) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$purpose is not an activation")
        }
        commandBus.dispatch(ResendRecoveryEmailCommand(userId, purpose))
    }

    /**
     * Which activation email each account that has not been activated takes, so a caller
     * offers the one that applies rather than both and a guess.
     */
    @GetMapping("/pending-activations")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'User', 'read')")
    fun pendingActivations(): PendingActivationsResponse =
        PendingActivationsResponse(
            commandBus.dispatch(FindPendingActivationsCommand())
                .map { (userId, purpose) -> PendingActivation(userId, purpose) }
                .sortedBy { it.userId },
        )

    /**
     * Renders what a recovery email would look like for this user, without issuing the
     * token that would make its link work. Gated on the same permission as sending one:
     * the rendered email carries the recipient's name and address.
     */
    @GetMapping("/users/{userId}/email-preview")
    @PreAuthorize("hasPermission(#userId, 'User', 'email')")
    fun previewRecoveryEmail(
        @PathVariable userId: Long,
        @RequestParam purpose: TokenPurpose,
    ): RecoveryEmailPreviewResponse {
        if (!purpose.isMailable) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A $purpose token is never emailed")
        }
        val preview = commandBus.dispatch(PreviewRecoveryEmailCommand(userId, purpose))
        return RecoveryEmailPreviewResponse(
            purpose = preview.purpose,
            subject = preview.subject,
            html = preview.html,
            recipientEmail = preview.recipientEmail,
            recipientName = preview.recipientName,
            linkPlaceholder = preview.linkPlaceholder,
        )
    }
}
