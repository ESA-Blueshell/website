package net.blueshell.api.domain.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.auth.security.JWTAuthBase
import net.blueshell.api.domain.auth.web.dto.request.MemberActivationRequest
import net.blueshell.api.domain.auth.web.dto.request.PasswordResetRequest
import net.blueshell.api.domain.auth.web.dto.request.UserActivationRequest
import net.blueshell.api.domain.auth.web.mapping.asCommand
import net.blueshell.api.domain.telemetry.web.dto.RedirectResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Recovery")
@RequestMapping("/recovery")
class RecoveryController(
    private val commandBus: CommandBus
) : JWTAuthBase() {
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
    fun userActivate(@Valid @RequestBody request: UserActivationRequest): RedirectResponse {
        val user = commandBus.dispatch(request.asCommand())
        return if (user.dateOfBirth != null) {
            RedirectResponse("/membership/signUp?step=2")
        } else {
            RedirectResponse("/")
        }
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

    @PostMapping("/users/{userId}/resend/recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BOARD')")
    fun resendMemberActivationEmail(@PathVariable userId: Long) {
        commandBus.dispatch(ResendMemberActivationEmailCommand(userId))
    }
}
