package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.auth.JWTAuthBase
import net.blueshell.api.dto.recovery.MemberActivationRequest
import net.blueshell.api.dto.recovery.PasswordResetRequest
import net.blueshell.api.dto.recovery.UserActivationRequest
import net.blueshell.api.dto.response.RedirectResponseDTO
import net.blueshell.api.service.RecoveryService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Recovery")
@RequestMapping("/recovery")
class RecoveryController(
    private val recoveryService: RecoveryService
) : JWTAuthBase() {
    @PostMapping("/password/reset/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun resetPassword(@PathVariable("username") username: String?) {
        recoveryService.resetPassword(username)
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun setPassword(@Valid @RequestBody request: @Valid PasswordResetRequest) {
        recoveryService.setPassword(request.getToken(), request.getPassword())
    }

    @PostMapping("/user/activate")
    @PermitAll
    fun userActivate(@Valid @RequestBody request: @Valid UserActivationRequest): RedirectResponseDTO {
        val user = recoveryService.activateUser(request.getToken())
        if (user.getDateOfBirth() != null) {
            return RedirectResponseDTO("/membership/signUp?step=2")
        } else {
            return RedirectResponseDTO("/")
        }
    }

    @PostMapping("/member/activate")
    @PermitAll
    fun memberActivate(@Valid @RequestBody request: @Valid MemberActivationRequest): RedirectResponseDTO {
        recoveryService.activateMember(request.getToken(), request.getUsername(), request.getPassword())
        return RedirectResponseDTO("/")
    }

    @PostMapping("/user/activate/resend/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PermitAll
    fun resendUserActivation(@PathVariable("username") username: String?) {
        recoveryService.resendActivation(username)
    }

    @PostMapping("/users/{userId}/resend/recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('BOARD')")
    fun resendMemberActivationEmail(@PathVariable("userId") userId: Long?) {
        recoveryService.resendActivationEmail(userId)
    }
}
